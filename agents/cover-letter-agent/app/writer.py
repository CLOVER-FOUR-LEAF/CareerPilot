"""
Cover Letter Agent - 中文求职信生成器

按"称呼 → 匹配点 → 能力证据 → 双向契合 → 收尾"五段式拼装，
目标长度 300-450 字（契约 §7.6）。

三种语气使用**不同的句式模板**，而不是替换形容词：
- professional：克制、书面、公文腔
- passionate：主动、有推动力、表达动机
- humble：谦逊、务实、强调学习与融入
"""
import logging
import re
from typing import Optional

from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)

VALID_TONES = ("professional", "passionate", "humble")

# 契约要求的目标字数区间
MIN_WORDS = 300
MAX_WORDS = 450


# ─── 输出模型 ──────────────────────────────────────────────────

class CoverLetter(BaseModel):
    """求职信，对应契约 §7.6"""
    tone: str = "professional"
    content: str = ""
    highlighted_skills: list[str] = Field(default_factory=list)
    word_count: int = 0


# ─── 分段模板 ──────────────────────────────────────────────────
# 每个语气四段独立模板（称呼由称呼行单独处理）

OPENING: dict[str, str] = {
    "professional": (
        "您好，我谨正式应聘贵司{job_title}一职。基于岗位职责，我在此申请该机会，"
        "并随信附上个人简历，供您审阅。"
    ),
    "passionate": (
        "您好，我投递{job_title}这个岗位，不只是因为它符合我的履历，"
        "更因为它正在解决的问题让我兴奋。看完职位描述后，我几乎能立刻在脑子里"
        "列出可以贡献的地方。"
    ),
    "humble": (
        "您好，我想申请{job_title}这个岗位。我清楚自己还不是这个方向上最资深的人，"
        "但我愿意从基础工作做起，也相信踏实做事可以换来成长空间。"
    ),
}

MATCH: dict[str, str] = {
    "professional": (
        "贵司对该岗位的核心要求包括{skills}。我在过往工作中持续在这些方向上投入，"
        "并有可量化的产出与之对应。"
    ),
    "passionate": (
        "这个岗位需要{skills}，这几项恰好是我这几年反复打磨的部分。"
        "我愿意把它们直接用在这里，而不是只写在简历里。"
    ),
    "humble": (
        "贵司提出的{skills}等要求，我都有过实际接触，理解其基本原理与常见坑点；"
        "在更深的工程化场景上，我仍在学习中。"
    ),
}

EVIDENCE: dict[str, str] = {
    "professional": (
        "其中，{highlights}是我的代表性工作。我在其中承担了方案设计、核心链路实现"
        "与上线后的效果跟踪，对从需求评估到稳定运行的完整链路负责。"
    ),
    "passionate": (
        "最能说明问题的是{highlights}。那段时间我几乎住在需求里，"
        "从方案取舍、编码落地到上线后的每一个指标波动都跟着，"
        "最后交出了让我自己满意的结果。"
    ),
    "humble": (
        "其中{highlights}是我投入最多的一段经历。我在团队中负责其中一部分，"
        "在资深同事的指导下完成了设计与实现，也因此养成了先对齐目标再动手的习惯。"
    ),
}

FIT: dict[str, str] = {
    "professional": (
        "{company_reason}。我认为自身的经历与该岗位的职责范围能够较好衔接，"
        "可以较快承担起相应工作。"
    ),
    "passionate": (
        "{company_reason}。这正是我想继续深耕的方向，"
        "我希望能把这套方法用在这里，和更优秀的同事一起把事情做到更好。"
    ),
    "humble": (
        "{company_reason}。我知道自己还有需要补齐的地方，"
        "希望在这样的环境里继续打磨能力，同时为团队贡献力所能及的价值。"
    ),
}

CLOSING: dict[str, str] = {
    "professional": (
        "随信附上简历，期待有机会与您进一步沟通。若需补充项目材料，我可在第一时间提供。"
        "此致，敬礼。"
    ),
    "passionate": (
        "随信附上简历，我非常期待能当面聊聊这个岗位。"
        "如果我的经历符合贵司的期待，希望能给我一次深入交流的机会。"
    ),
    "humble": (
        "随信附上简历，不论结果如何，这次投递对我都是一次宝贵的尝试。"
        "期待您抽出时间阅读，也欢迎随时向我了解细节。"
    ),
}

GREETING: dict[str, str] = {
    "professional": "尊敬的{company}招聘负责人：",
    "passionate": "尊敬的{company}招聘团队：",
    "humble": "尊敬的{company}老师：",
}

# 公司契合句：优先用调用方给的公司信息，否则按语气用通用表达
COMPANY_REASON: dict[str, str] = {
    "professional": "贵司在相关领域的业务布局与我的职业方向一致",
    "passionate": "贵司正在做的事，恰好是我愿意长期投入的地方",
    "humble": "贵司的业务方向与我的能力结构有较好的衔接点",
}


# ─── 工具函数 ──────────────────────────────────────────────────

def _join(items: list[str], sep: str = "、") -> str:
    return sep.join(items) if items else ""


def _pick(items: list[str], limit: int, fallback: str) -> list[str]:
    cleaned = [item.strip() for item in items if item and item.strip()]
    if cleaned:
        return cleaned[:limit]
    return [fallback]


def _count_words(text: str) -> int:
    """中文字数：汉字逐字计，连续英文/数字串算一个词"""
    cjk = len(re.findall(r"[\u4e00-\u9fff]", text))
    latin = len(re.findall(r"[A-Za-z0-9][A-Za-z0-9+#./-]*", text))
    return cjk + latin


def _company_reason(company_info: Optional[str], tone: str, company_name: str) -> str:
    """把公司信息压成一句契合话，控制在合理长度内"""
    if company_info:
        info = company_info.strip().split("\n")[0].strip()
        if len(info) > 60:
            info = info[:60] + "…"
        return f"了解到{info}"
    default = COMPANY_REASON[tone].format(company_name=company_name)
    return default


# ─── 主生成函数 ────────────────────────────────────────────────

def generate_cover_letter(
    jd_skills: Optional[list[str]] = None,
    resume_highlights: Optional[list[str]] = None,
    company_name: str = "贵司",
    job_title: str = "该岗位",
    tone: str = "professional",
    company_info: Optional[str] = None,
) -> CoverLetter:
    """
    生成中文求职信。

    输出长度通过"补一段 / 缩一段"收敛到 300-450 字区间。
    """
    if tone not in VALID_TONES:
        logger.warning("未知语气 %r，回落到 professional", tone)
        tone = "professional"

    jd_skills = jd_skills or []
    resume_highlights = resume_highlights or []

    skills = _pick(jd_skills, 4, "核心工程能力")
    highlights = _pick(resume_highlights, 2, "过往项目经历")
    reason = _company_reason(company_info, tone, company_name)

    slots = {
        "job_title": job_title,
        "company": company_name,
        "skills": _join(skills),
        "highlights": _join(highlights),
        "company_reason": reason,
    }

    paragraphs = [
        GREETING[tone].format(**slots),
        OPENING[tone].format(**slots),
        MATCH[tone].format(**slots),
        EVIDENCE[tone].format(**slots),
        FIT[tone].format(**slots),
        CLOSING[tone].format(**slots),
    ]
    content = "\n".join(paragraphs)
    word_count = _count_words(content)

    # ── 长度收敛：偏短补一段，偏长缩掉匹配段 ──
    if word_count < MIN_WORDS:
        extra = {
            "professional": (
                "补充说明：我在{skills}上的实践并非停留在课程或文档层面，"
                "而是在真实的生产环境里反复验证过，能够独立完成从方案设计到上线运维的全过程。"
            ),
            "passionate": (
                "我还想补充一点：我不害怕从零开始啃新的东西。"
                "{skills}里的每一项，我都是先自己跑通、再拿去和团队讨论的，"
                "这种习惯让我在陌生领域也能快速进入状态。"
            ),
            "humble": (
                "我也想坦诚说明：我在{skills}上的经验更多来自实际项目，"
                "理论沉淀可能不如科班出身的同事深入，但我愿意持续补课，也请给我一个学习的机会。"
            ),
        }[tone].format(**slots)
        content = content.replace(CLOSING[tone].format(**slots), extra + "\n" + CLOSING[tone].format(**slots))
        word_count = _count_words(content)

    if word_count > MAX_WORDS:
        # 优先精简最长的匹配段，其余保持完整
        shorter_match = {
            "professional": "贵司要求的核心能力包括{skills}，我在这些方向有持续投入与可量化产出。",
            "passionate": "岗位需要{skills}，这正是我反复打磨的部分，我愿意直接用在这里。",
            "humble": "贵司提出的{skills}我都有实际接触，原理与常见坑点比较清楚。",
        }[tone].format(**slots)
        content = content.replace(MATCH[tone].format(**slots), shorter_match)
        word_count = _count_words(content)

    # 最终兜底：仍然超长则按句子截断到上限附近
    if word_count > MAX_WORDS:
        content = content[:MAX_WORDS]
        word_count = _count_words(content)

    return CoverLetter(
        tone=tone,
        content=content,
        highlighted_skills=skills,
        word_count=word_count,
    )
