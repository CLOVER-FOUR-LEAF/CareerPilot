"""
Career Advisor Agent - 职业发展规划引擎

流程：档案 -> 检索（BM25 top-8）-> 规则拼装路线图 -> 每条路线图引用 1-2 个**实际检索到的** doc_id

严格不做引用编造：citations 里的 doc_id 一定来自检索结果。
"""
import logging
import re
from typing import Optional

from pydantic import BaseModel, Field

from data.knowledge_base import KnowledgeDoc

from .retriever import build_query, retrieve

logger = logging.getLogger(__name__)


# ─── 输出模型（契约 §7.7）─────────────────────────────────────

class RoadmapPhase(BaseModel):
    phase: str = ""
    goal: str = ""
    actions: list[str] = Field(default_factory=list)
    milestone: str = ""
    doc_ids: list[str] = Field(default_factory=list)


class Citation(BaseModel):
    doc_id: str
    source: str = ""
    version: str = ""


class CareerPlan(BaseModel):
    summary: str = ""
    roadmap: list[RoadmapPhase] = Field(default_factory=list)
    skill_gap: list[str] = Field(default_factory=list)
    citations: list[Citation] = Field(default_factory=list)


# ── 阶段模板 ──────────────────────────────────────────────────
# 每个阶段的行动建议都会在末尾标注其 doc_id 依据（_doc_hint 注入）。
# 阶段划分：前 3 个月补差距，中 3-6 个月做项目与验证，后 6-12 个月落地与安顿。

PHASE_TEMPLATES: list[dict] = [
    {
        "label": "第 1-3 个月",
        "goal": "定位能力差距并补齐目标岗位的硬性门槛",
        "actions": [
            "按目标岗位 JD 逐条拆解硬性要求，标注'已具备 / 可在 3 个月内补齐 / 短期无法补齐'",
            "完成一次能力盘点，把过往经历归入可迁移能力与行业专属知识两类",
            "以 10-20 家目标公司的 JD 为样本，统计核心技能出现频次，决定学习优先级",
            "同步重构简历：改用强动词开头，每条经历补齐可量化的结果",
        ],
        "milestone": "产出一份目标岗位能力清单，明确 3-5 项必须优先补齐的技能，并完成一轮简历改造",
    },
    {
        "label": "第 4-6 个月",
        "goal": "把补齐的技能转化为可验证的项目经验",
        "actions": [
            "在现有岗位或开源项目中承担一次有完整闭环的工作，刻意练习目标技能",
            "记录项目中的关键决策与取舍，整理成可复述的 STAR 素材",
            "参加目标行业的线上分享与线下交流，用小范围投递校准能力判断",
            "按 STAR 整理 6-8 个带量化结果的行为面故事，进行一轮模拟面试",
        ],
        "milestone": "产出 1-2 个能量化描述、与目标岗位强相关的项目，并获得 5 次以上真实面试反馈",
    },
    {
        "label": "第 7-12 个月",
        "goal": "集中投递拿下目标岗位并完成平稳入职",
        "actions": [
            "按匹配度排序目标公司，优先投递能力清单已满足的岗位",
            "针对每家公司微调简历与求职信，突出最相关的一到两段经历",
            "面试后当天记录被问到的问题与卡点，持续迭代表达",
            "入职前 90 天快速理解业务链路，并争取独立负责一个明确模块",
        ],
        "milestone": "获得目标岗位 offer；若未达成，则已明确下一轮需要补强的具体方向",
    },
]


# ── 工具 ──────────────────────────────────────────────────────

def _skill_gap(
    skills: list[str], docs: list[KnowledgeDoc], limit: int = 6,
) -> list[str]:
    """
    从检索到的文档里找出目标技能，用用户已有技能做差集。
    只输出**文档确实提到**且用户没有的技能，不凭空编造。
    """
    owned = {s.strip().lower() for s in skills if s and s.strip()}
    candidates: list[str] = []
    seen: set[str] = set()

    # 行业匹配的文档优先
    for doc in docs:
        for skill in doc["skills"]:
            key = skill.strip().lower()
            if key in owned or key in seen:
                continue
            seen.add(key)
            candidates.append(skill)
    return candidates[:limit]


def _cite(
    docs: list[KnowledgeDoc],
    pool_index: int,
    count: int = 2,
) -> list[str]:
    """
    从检索结果里取 count 个 doc_id。

    这是本模块最重要的约束：引用的必须来自实际检索结果，
    绝不允许构造一个检索不到的 doc_id。
    """
    if not docs:
        return []
    picked: list[str] = []
    for offset in range(min(count, len(docs))):
        picked.append(docs[(pool_index + offset) % len(docs)]["doc_id"])
    return picked


def _doc_hint(doc_id: str, docs: list[KnowledgeDoc]) -> str:
    """找到文档标题，拼成行动建议里的依据说明"""
    for doc in docs:
        if doc["doc_id"] == doc_id:
            return f"依据《{doc['title']}》"
    return f"依据 {doc_id}"


# ── 主生成函数 ────────────────────────────────────────────────

def _order_docs(docs: list[KnowledgeDoc], industry: str) -> list[KnowledgeDoc]:
    """
    按与目标行业的相关度重排检索结果。

    相关度排序：目标行业 > 通用方法论 > 其他行业。
    目的是让 skill_gap 与路线图引用都指向"目标行业"而不是"当前行业"，
    避免给制造业工艺工程师转互联网后端时，推荐补齐 CAE、工装夹具这类
    与目标岗位无关的技能。
    """
    if not industry:
        return docs

    def rank(doc: KnowledgeDoc) -> int:
        if doc["industry"] == industry:
            return 0
        if doc["industry"] == "通用":
            return 1
        return 2

    # sorted 是稳定排序，同档位保留 BM25 的原始相关度顺序
    return sorted(docs, key=rank)


def build_career_plan(
    current_title: str,
    target_industry: str,
    target_position: str,
    years_of_experience: int = 0,
    skills: Optional[list[str]] = None,
    question: Optional[str] = None,
    top_k: int = 8,
) -> CareerPlan:
    """
    生成职业发展规划。

    每条路线图条目引用 1-2 个检索到的 doc_id，citations 只列出被引用的文档。
    """
    skills = [s for s in (skills or []) if s and s.strip()]

    query = build_query(
        current_title=current_title,
        target_industry=target_industry,
        target_position=target_position,
        skills=skills,
        question=question,
    )
    docs = _order_docs(
        retrieve(query, industry=target_industry or None, top_k=top_k),
        target_industry,
    )
    logger.info(f"retrieved {len(docs)} docs for industry={target_industry}")

    gap = _skill_gap(skills, docs)
    cited_ids: list[str] = []

    def _make_phase(
        label: str, template: dict, pool_index: int,
    ) -> RoadmapPhase:
        nonlocal cited_ids
        doc_ids = _cite(docs, pool_index, count=2)
        for doc_id in doc_ids:
            if doc_id not in cited_ids:
                cited_ids.append(doc_id)
        hint = _doc_hint(doc_ids[0], docs) if doc_ids else ""
        actions = [f"{action}（{hint}）" for action in template["actions"]]
        return RoadmapPhase(
            phase=label,
            goal=template["goal"],
            actions=actions,
            milestone=template["milestone"],
            doc_ids=doc_ids,
        )

    roadmap = [
        _make_phase(template["label"], template, index)
        for index, template in enumerate(PHASE_TEMPLATES)
    ]

    summary = _build_summary(
        current_title, target_industry, target_position,
        years_of_experience, skills, gap, question, docs,
    )

    cited_docs = [d for d in docs if d["doc_id"] in cited_ids]
    citations = [
        Citation(doc_id=d["doc_id"], source=d["source"], version=d["version"])
        for d in cited_docs
    ]

    return CareerPlan(
        summary=summary,
        roadmap=roadmap,
        skill_gap=gap,
        citations=citations,
    )


def _build_summary(
    current_title: str,
    target_industry: str,
    target_position: str,
    years_of_experience: int,
    skills: list[str],
    gap: list[str],
    question: Optional[str],
    docs: list[KnowledgeDoc],
) -> str:
    """把用户问题和检索到的行业事实拼成一段总结"""
    experience_note = (
        f"{years_of_experience} 年经验" if years_of_experience > 0 else "经验年限待确认"
    )
    skill_text = "、".join(skills) if skills else "尚未明确技能标签"
    gap_text = "、".join(gap[:3]) if gap else "暂无明显技能缺口"

    summary = (
        f"你目前是{current_title or '未填写现岗位'}（{experience_note}），"
        f"目标是转向{target_industry or '目标行业'}的{target_position or '目标岗位'}。"
        f"现有技能标签为{skill_text}，对照该行业的岗位要求，主要差距集中在{gap_text}。"
    )

    if question:
        summary += f"针对你提出的问题「{question}」，"
    summary += (
        f"本次分析基于知识库中检索到的 {len(docs)} 篇行业资料，"
        f"主要依据包括：" + "、".join(d["title"] for d in docs[:3]) + "。"
        "建议按下方三阶段路线推进，并优先补齐前两项技能差距。"
    )
    return summary
