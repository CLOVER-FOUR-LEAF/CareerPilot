"""
Interview Agent - 题库与 STAR 评分引擎

- 出题：从内置题库按 question_type（behavioral / technical / followup）取题
- 追问：拿到上一轮回答后，定位 STAR 中最薄弱的一环做针对性追问
- 评价：规则化检测 STAR 四要素覆盖度，输出 0-10 分与改进建议

全部规则实现，不依赖 LLM。
"""
import logging
import re
from typing import Optional

from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)


# ─── 输出模型 ──────────────────────────────────────────────────

class InterviewQuestion(BaseModel):
    """出题结果，对应契约 §7.5"""
    round_no: int = 1
    question: str = ""
    question_type: str = "behavioral"   # behavioral / technical / followup
    focus: str = ""


class StarCoverage(BaseModel):
    situation: bool = False
    task: bool = False
    action: bool = False
    result: bool = False


class InterviewEvaluation(BaseModel):
    """评价结果，对应契约 §7.5"""
    score: float = 0.0               # 0-10
    feedback: str = ""
    star_coverage: StarCoverage = Field(default_factory=StarCoverage)
    followup: str = ""


# ─── STAR 要素识别 ────────────────────────────────────────────
# 每个要素的特征词。命中越多说明该要素讲得越充分。

SITUATION_MARKERS: tuple[str, ...] = (
    "当时", "背景", "项目背景", "面临", "现状", "业务场景", "团队", "公司",
    "客户", "业务方", "需求来源", "为什么要做", "前提", "环境",
)

TASK_MARKERS: tuple[str, ...] = (
    "我的职责", "我的任务", "我负责", "职责是", "任务是", "目标是",
    "需要我", "要求我", "期望", "指标", "考核", "我被要求",
)

ACTION_MARKERS: tuple[str, ...] = (
    "我采用", "我的做法", "我们采取", "设计方案", "我设计", "我实现",
    "我推动", "我落地", "我搭建", "我优化", "我重构", "方案是",
    "第一步", "第二步", "具体做法", "技术方案", "我主导",
)

RESULT_MARKERS: tuple[str, ...] = (
    "结果", "最终", "后来", "达成", "提升", "降低", "缩短", "节省",
    "降到", "提高到", "提升至", "下降至", "改善", "收益", "上线后", "效果", "产出", "交付后",
)

# 结果段的量化信号
_QUANT_PATTERN = re.compile(
    r"\d+(?:\.\d+)?\s*(?:%|％|倍|万|亿|千|人|天|小时|ms|毫秒|秒|QPS|qps|TPS|tps)"
    r"|从\s*\d+(?:\.\d+)?\s*(?:[a-zA-Z%]*\s*)?(?:提升|下降|降低|增长|降到|提高到|提升至|下降至|改善)",
    re.IGNORECASE,
)


def _count_markers(text: str, markers: tuple[str, ...]) -> int:
    return sum(text.count(marker) for marker in markers)


def detect_star_coverage(answer: str) -> StarCoverage:
    """
    检测回答是否覆盖 STAR 四要素。

    action 命中"我"字样才算数，避免把团队功劳全部算到个人头上。
    """
    text = answer or ""
    return StarCoverage(
        situation=_count_markers(text, SITUATION_MARKERS) >= 1,
        task=_count_markers(text, TASK_MARKERS) >= 1,
        action=_count_markers(text, ACTION_MARKERS) >= 1,
        result=_count_markers(text, RESULT_MARKERS) >= 1 and bool(_QUANT_PATTERN.search(text)),
    )


# 元素缺失时的严重度排序：先补 result，再补 action，最后补 task / situation。
# 结果缺失对面试评价的杀伤最大，所以排在最前。
_ELEMENT_PRIORITY: tuple[str, ...] = ("result", "action", "task", "situation")

_ELEMENT_LABELS: dict[str, str] = {
    "situation": "情境（Situation）",
    "task": "任务（Task）",
    "action": "行动（Action）",
    "result": "结果（Result）",
}


def weakest_missing_element(coverage: StarCoverage) -> str:
    """返回 STAR 中最薄弱（缺失且最该补）的一个元素名"""
    missing = [name for name in _ELEMENT_PRIORITY if not getattr(coverage, name)]
    return missing[0] if missing else "result"


# ─── 题库 ──────────────────────────────────────────────────────
# 每条：template 中的 {skill} / {role} / {highlight} 会被替换成具体内容。

BEHAVIORAL_BANK: tuple[str, ...] = (
    "请结合你在 {highlight} 中的经历，讲述一次你在 {skill} 方向上遇到的最大挑战，"
    "你是如何定位问题并推动解决的？",
    "请描述一次你用 {skill} 改变了原有做法的项目。当时的团队目标是什么，"
    "你个人承担了哪部分职责？",
    "讲一个你主导的、结果明显优于原方案的 {skill} 相关项目。"
    "背景是什么、你做了什么、最终数据变化如何？",
    "在过往项目中，你有没有一次判断失误导致返工或线上问题？"
    "当时你是怎么发现并止损的，后来做了什么改进？",
    "请举一个你和产品、测试或业务方就需求产生分歧的例子，"
    "涉及 {skill} 的部分你最终是怎么推动落地的？",
)

TECHNICAL_BANK: tuple[str, ...] = (
    "请说明你在 {skill} 上的实际使用深度：用过哪些版本或组件、"
    "解决了什么类型的问题、你个人负责的边界在哪里？",
    "如果让你用 {skill} 重新设计一个当前能扛住峰值、但成本偏高的系统，"
    "你会从哪几个方面入手？为什么？",
    "围绕 {skill}，请对比两种主流方案的取舍，并说明你在 {highlight} 里为什么选了其中一种。",
    "线上遇到一个由 {skill} 引起的疑难问题，你按什么顺序排查？"
    "请给出具体的定位手段，而不是只说'看日志'。",
    "请讲讲你在 {skill} 上踩过的坑：问题现象、根因是什么、"
    "以及你事后做了哪些机制上的改进避免复发。",
)

ROLE_BANK: tuple[str, ...] = (
    "未来一年如果你入职 {role}，你最希望先解决什么问题？"
    "结合你在 {highlight} 中的积累，你的判断是什么？",
    "这个岗位需要 {skill} 相关能力。你认为达到这个岗位期望水平，"
    "通常需要哪些经历或项目来支撑？",
    "你过去三年最有成长感的经历是什么？"
    "它和这个岗位要求的 {skill} 能力是怎么衔接的？",
)

# 追问模板：按 STAR 中最薄弱的元素分组
FOLLOWUP_BANK: dict[str, tuple[str, ...]] = {
    "situation": (
        "你刚才提到的那段背景，我还想再确认一下：当时团队规模多大、"
        "上线时间压力如何？为什么这个问题必须在那时解决？",
        "你描述的情境里，有没有当时的备选方案？"
        "你们当时是如何判断这个方案风险可控的？",
    ),
    "task": (
        "在这件事里，你的个人职责边界具体到哪里？"
        "哪些是你独立决策的，哪些是团队或上级拍板的？",
        "你当时收到的考核指标是什么？"
        "除了业务结果，还有没有技术或协作层面的要求？",
    ),
    "action": (
        "你刚才说做了优化，能具体讲一下方案吗？"
        "为什么选这个思路，放弃了哪几种其他方案、为什么放弃？",
        "落地过程中遇到的最大阻力是什么？"
        "你是怎么说服其他团队配合的？",
    ),
    "result": (
        "这个结果是怎么度量的？基线值和最终值分别是多少，"
        "统计口径和数据来源是什么？",
        "除了你提到的这个数字变化，还有没有其他可量化的收益，"
        "比如成本、耗时或故障率？",
    ),
}

# 各类题型的 focus 文案
FOCUS_TEXT: dict[str, str] = {
    "behavioral": "考察经历真实性、STAR 完整度与个人贡献度",
    "technical": "考察技术深度、方案取舍能力与问题定位能力",
    "role": "考察岗位理解、动机匹配与自我认知",
    "followup": "针对上一轮回答中 STAR 最薄弱的一环做压力追问",
}


# ─── 出题 ──────────────────────────────────────────────────────

def _pick_subject(
    jd_skills: list[str], resume_highlights: list[str],
) -> tuple[str, str]:
    """返回 (技能主题, 经历主题)，优先用 JD 技能，其次用简历亮点"""
    skill = jd_skills[0] if jd_skills else "核心技术方案"
    highlight = resume_highlights[0] if resume_highlights else "过往项目"
    return skill, highlight


def _choose_type(round_no: int, total_rounds: int) -> str:
    """按轮次分配题型：首轮行为面、中段技术深挖、末轮动机匹配"""
    total = max(total_rounds, 1)
    if total == 1:
        return "behavioral"
    ratio = (round_no - 1) / (total - 1)
    if ratio < 0.34:
        return "behavioral"
    if ratio < 0.74:
        return "technical"
    return "role"


def generate_question(
    round_no: int = 1,
    total_rounds: int = 3,
    jd_skills: Optional[list[str]] = None,
    resume_highlights: Optional[list[str]] = None,
    previous_answer: Optional[str] = None,
    job_title: str = "该岗位",
) -> InterviewQuestion:
    """
    生成一道面试题。

    - previous_answer 为空：从题库按题型取题
    - previous_answer 非空：针对其 STAR 最薄弱元素生成真正的追问
    """
    jd_skills = [s for s in (jd_skills or []) if s]
    resume_highlights = [h for h in (resume_highlights or []) if h]
    skill, highlight = _pick_subject(jd_skills, resume_highlights)

    answer_text = (previous_answer or "").strip()

    if answer_text:
        coverage = detect_star_coverage(answer_text)
        weak = weakest_missing_element(coverage)
        templates = FOLLOWUP_BANK[weak]
        # 用回答里出现过的关键信息做锚点，让追问显得"读过他的回答"
        anchor = _extract_anchor(answer_text)
        template = templates[round_no % len(templates)]
        question = template.format(anchor=anchor) if "{anchor}" in template else template
        return InterviewQuestion(
            round_no=round_no,
            question=question,
            question_type="followup",
            focus=f"{FOCUS_TEXT['followup']}（本次针对 {_ELEMENT_LABELS[weak]}）",
        )

    question_type = _choose_type(round_no, total_rounds)
    bank = {
        "behavioral": BEHAVIORAL_BANK,
        "technical": TECHNICAL_BANK,
        "role": ROLE_BANK,
    }[question_type]
    template = bank[round_no % len(bank)]

    return InterviewQuestion(
        round_no=round_no,
        question=template.format(skill=skill, highlight=highlight, role=job_title),
        question_type=question_type,
        focus=f"{FOCUS_TEXT[question_type]}（聚焦：{skill}）",
    )


def _extract_anchor(answer: str, limit: int = 16) -> str:
    """
    从上一轮回答里抓一个短语做追问锚点。
    取最长的一个含数字或技术名词的片段，避免追问显得答非所问。
    """
    segments = re.split(r"[。！？；\n，,]", answer)
    candidates = [
        seg.strip() for seg in segments
        if seg.strip() and (re.search(r"\d", seg) or len(seg) >= 8)
    ]
    if not candidates:
        candidates = [seg.strip() for seg in segments if seg.strip()]
    if not candidates:
        return "你刚才提到的经历"
    best = max(candidates, key=len)
    return best[:limit]


# ─── 评价 ──────────────────────────────────────────────────────

def evaluate_answer(
    question: str,
    answer: str,
    jd_skills: Optional[list[str]] = None,
) -> InterviewEvaluation:
    """按 STAR 完整度 + 量化程度 + 技能命中给回答打分（0-10）"""
    jd_skills = [s for s in (jd_skills or []) if s]
    text = answer or ""
    coverage = detect_star_coverage(text)

    covered = sum(
        1 for name in ("situation", "task", "action", "result")
        if getattr(coverage, name)
    )

    if not text.strip():
        return InterviewEvaluation(
            score=0.0,
            feedback="回答为空，无法评估。建议先用一句话交代背景，再按 STAR 展开。",
            star_coverage=coverage,
            followup=FOLLOWUP_BANK["situation"][0],
        )

    score = 2.0
    score += covered * 1.4                       # STAR 四要素覆盖
    if _QUANT_PATTERN.search(text):
        score += 1.2                              # 有量化结果
    if len(text) < 40:
        score -= 1.0                              # 过短，信息量不足
    elif len(text) > 600:
        score -= 0.5                              # 过长，重点不突出

    skill_hits = [s for s in jd_skills if s.lower() in text.lower()]
    score += min(len(skill_hits), 2) * 0.5       # 命中 JD 技能关键词

    score = round(max(0.0, min(10.0, score)), 1)

    # ── 反馈文案 ──
    missing = [_ELEMENT_LABELS[n] for n in _ELEMENT_PRIORITY if not getattr(coverage, n)]
    parts: list[str] = []
    if covered == 4:
        parts.append("STAR 四要素完整，结构清晰。")
    else:
        parts.append("回答缺少：" + "、".join(missing) + "。")

    if _QUANT_PATTERN.search(text):
        parts.append("有量化结果，这是加分项。")
    else:
        parts.append("缺少可量化的结果，建议补上基线值与最终值的对比。")

    if skill_hits:
        parts.append(f"命中了目标岗位技能：{'、'.join(skill_hits)}。")
    elif jd_skills:
        parts.append("没有出现目标 JD 的关键技术词，建议自然地带出。")

    if len(text) < 40:
        parts.append("篇幅过短，面试官难以判断深度。")
    elif len(text) > 600:
        parts.append("篇幅偏长，建议按'结论先行'压缩到三分钟内。")

    weak = weakest_missing_element(coverage)
    followup_templates = FOLLOWUP_BANK[weak]
    followup = followup_templates[0]

    return InterviewEvaluation(
        score=score,
        feedback="".join(parts),
        star_coverage=coverage,
        followup=followup,
    )
