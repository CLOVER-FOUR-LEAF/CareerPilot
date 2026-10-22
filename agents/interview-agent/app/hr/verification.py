"""
验证计划生成（L3，计划书 §4.3）。

把归因后的 gap 转成**非诱导性**的面试问题。

这是整个 HR 侧能力的落点：匹配层发现了「证据缺失」，
这里把它变成「怎么问才能把这个缺失问清楚」。

问题的四条硬规则（代码强制，不靠 prompt 祈祷）
----------------------------------------------
| 规则 | 说明 | 违反示例 |
|---|---|---|
| 禁止二元问法 | 不得出现「会不会/熟悉/了解/掌握 + 技能名」 | ❌「你会 Kubernetes 吗？」 |
| 必须锚定场景 | 必须包含「怎么做/多少/什么场景/流程」之一 | ✅「你们的部署流程是怎样的？」 |
| 不得暗示答案 | 不得预设候选人会/不会 | ❌「你没用过 K8s 对吧？」 |
| 不得泄露缺口 | 不能出现「JD 要求但你简历没有」 | ❌「JD 要求 K8s，你简历里没有，怎么回事？」 |

实现方式
--------
**规则生成问题骨架 → LLM 只做措辞润色 → 代码 linter 校验**。
LLM 润色结果通不过 linter 就丢弃，保留规则版本。
这样「禁用问法率 = 0」是结构性保证，而不是概率。
"""
from __future__ import annotations

import logging
import re
from typing import Optional

from pydantic import BaseModel, Field

from .. import llm
from .attribution import GapAttribution
from .capability import DIMENSION_NAMES
from .compliance import ALLOWED_VERDICTS, VERDICT_LABELS

logger = logging.getLogger(__name__)


# ── 输出模型 ──────────────────────────────────────────────────────

class VerificationProbe(BaseModel):
    """追问。"""

    question: str = ""
    trigger: str = ""


class VerificationSignal(BaseModel):
    """判定信号：某个判定值成立时，答案里应该出现什么证据。"""

    verdict: str = ""
    evidence: str = ""
    # 特征词：规则判定时用它在回答里做命中检测
    keywords: list[str] = Field(default_factory=list)


class VerificationQuestion(BaseModel):
    """
    一个验证问题，对应计划书 §4.3 的输出结构。
    """

    id: str = ""
    gap_ref: str = ""
    dimension: str = ""
    dimension_name: str = ""
    severity: int = 0

    question: str = ""
    intent: str = ""
    probes: list[VerificationProbe] = Field(default_factory=list)
    signals: list[VerificationSignal] = Field(default_factory=list)

    # 该问题由规则生成（True）还是 LLM 润色过（False）
    rule_generated: bool = True


# ── 提问框架（按能力维度）─────────────────────────────────────────
#
# 每个维度有自己的提问角度：
# delivery 问流程、scale 问量级、architecture 问取舍、data 问数据形态、
# domain 问业务难点、collaboration 问个人贡献边界。

QUESTION_FRAMES: dict[str, str] = {
    "delivery": "你们的 {skill} 是怎么从代码走到生产的？{probe}",
    "scale": "{skill} 在你们那边承载了多少量级？出过什么问题？{probe}",
    "architecture": "你们当初是怎么决定这么做的？{skill} 在架构里承担什么角色？{probe}",
    "data": "{skill} 当时的数据量/访问模式是什么样的？{probe}",
    "domain": "这个业务里最难的点在哪？和 {skill} 怎么结合的？{probe}",
    "collaboration": "这件事你具体怎么推进的？团队里你负责了哪部分？{probe}",
}

# 量化类 gap（简历缺少可量化的业务指标）用这一套问法
METRIC_FRAME = "你负责的{skill}，上线前后的核心指标分别是多少？当时是怎么度量的？{probe}"

# 硬性门槛（学历 / 年限）用这一套。
# 注意：这里问的是候选人的**实际情况**，不是给能力下判断 ——
# 学历门槛是岗位准入条件，不是能力评估（ADR-005 / §5.6 B6）。
HARD_GATE_FRAMES: dict[str, str] = {
    "education": "你的最高学历是什么？大概是什么时候毕业的？{probe}",
    "experience": "你到目前为止累计了多少年工作经验？大致的时间线是怎样的？{probe}",
}

# 硬性门槛的子类型
_EDUCATION_GATE_MARKERS: tuple[str, ...] = ("学历", "本科", "硕士", "博士", "大专", "统招")

# 每个维度的追问策略（探针）：第一问没问到点上时怎么追
PROBE_STRATEGIES: dict[str, tuple[str, ...]] = {
    "delivery": (
        "如果 {skill} 要回滚或升级，当时的流程是什么？",
        "你直接操作过 {skill} 的哪一层配置或参数？具体做了什么？",
    ),
    "scale": (
        "这个量级下，瓶颈出现在哪里？你们怎么定位的？",
        "出过最严重的一次故障是什么？当时怎么处理的？",
    ),
    "architecture": (
        "如果让你现在重新设计一遍，哪里会改？为什么？",
        "这个方案里你放弃过哪些做法？取舍的依据是什么？",
    ),
    "data": (
        "数据量涨上来之后，存储和查询分别做了什么调整？",
        "有没有遇到过一致性问题？是怎么保证的？",
    ),
    "domain": (
        "这个业务最难判断的一个决策是什么？依据是什么？",
        "如果换成另一个业务场景，这套做法还成立吗？",
    ),
    "collaboration": (
        "这个方案推进过程中遇到的最大阻力是什么？你怎么推动的？",
        "你负责的那部分，最终结果是怎么衡量的？",
    ),
}

# 通用探针（维度缺失时用）
DEFAULT_PROBES: tuple[str, ...] = (
    "你具体是怎么做的？可以说一下当时的操作或判断过程吗？",
    "这件事里你个人的部分是什么？有哪些是你独立决定的？",
)

# 每个维度的判定信号特征词 —— 规则判定时在回答里做命中检测
SIGNAL_KEYWORDS: dict[str, dict[str, tuple[str, ...]]] = {
    "delivery": {
        "demonstrated": ("配置", "操作", "扩容", "回滚", "排障", "演练", "发布", "流水线", "灰度", "脚本"),
        "adjacent": ("使用", "调用", "接入", "托管", "配置", "yaml", "helm", "控制台"),
        "not_evidenced": ("不太清楚", "没怎么接触", "同事负责", "没有直接"),
    },
    "scale": {
        "demonstrated": ("峰值", "qps", "压测", "扩容", "限流", "降级", "故障", "抖动", "容量", "瓶颈"),
        "adjacent": ("有一定", "偶尔", "小流量", "测试环境", "预期"),
        "not_evidenced": ("没有量级概念", "不清楚", "没关注"),
    },
    "architecture": {
        "demonstrated": ("取舍", "权衡", "边界", "拆分依据", "代价", "替代方案", "演进", "重构"),
        "adjacent": ("参考了", "沿用", "照搬", "现有方案"),
        "not_evidenced": ("没参与设计", "不知道原因"),
    },
    "data": {
        "demonstrated": ("分库", "分表", "索引", "慢查询", "缓存策略", "一致性", "数据量", "建模"),
        "adjacent": ("用过", "了解", "简单加过索引"),
        "not_evidenced": ("没碰过", "由别人负责"),
    },
    "domain": {
        "demonstrated": ("业务指标", "转化", "客单价", "风控", "策略", "取舍", "复盘", "踩坑"),
        "adjacent": ("参与过", "配合", "辅助"),
        "not_evidenced": ("不熟悉", "没接触过这块"),
    },
    "collaboration": {
        "demonstrated": ("我主导", "我推动", "我牵头", "我负责", "跨部门", "对齐", "协调", "决策"),
        "adjacent": ("我们团队", "一起", "配合", "协助"),
        "not_evidenced": ("不太清楚", "都是别人做的"),
    },
}

DEFAULT_SIGNAL_KEYWORDS: dict[str, tuple[str, ...]] = {
    "demonstrated": ("我主导", "我负责", "具体", "当时", "因为", "所以", "结果"),
    "adjacent": ("参与", "配合", "协助", "部分"),
    "not_evidenced": ("没有", "不太", "不清楚", "没怎么"),
}

# 硬性门槛（学历 / 年限）单独一套特征词 ——
# 判定的是「实际条件是否满足准入要求」，不是能力强弱。
HARD_GATE_SIGNAL_KEYWORDS: dict[str, tuple[str, ...]] = {
    "demonstrated": ("毕业", "取得", "获得", "累计", "至今", "目前", "有", "年"),
    "adjacent": ("在读", "即将", "接近", "差不多", "差不多有", "在岗"),
    "not_evidenced": ("不方便", "无法透露", "不方便说", "记不清"),
}


# ── 禁用问法 linter（代码强制）────────────────────────────────────

# 二元问法：会不会 / 熟悉 / 了解 / 掌握 / 用过 …… 后面跟一个技术名词
_BINARY_PATTERNS: tuple[re.Pattern, ...] = (
    re.compile(r"会不会"),
    re.compile(r"熟悉[^\s？?]{0,12}[？?]"),
    re.compile(r"了解[^\s？?]{0,12}[？?]"),
    re.compile(r"掌握[^\s？?]{0,12}[？?]"),
    re.compile(r"用过[^\s？?]{0,12}[？?]"),
    re.compile(r"懂不懂"),
    re.compile(r"可以吗[？?]"),
    re.compile(r"行不行[？?]"),
)

# 暗示答案：预设候选人会 / 不会
_LEADING_PATTERNS: tuple[re.Pattern, ...] = (
    re.compile(r"你(并)?没有用过"),
    re.compile(r"你(应该)?不(会|懂|熟悉)"),
    re.compile(r"你没用过.{0,8}对吧"),
    re.compile(r"既然你(不|没)"),
    re.compile(r"你肯定"),
    re.compile(r"我猜你"),
)

# 泄露缺口：把「简历没写」摆到台面上
_LEAK_PATTERNS: tuple[re.Pattern, ...] = (
    re.compile(r"简历(里|上)(没|没有|未)"),
    re.compile(r"JD\s*(要求|提到|写了)"),
    re.compile(r"你的简历"),
    re.compile(r"你(缺|缺少)"),
    re.compile(r"岗位要求"),
    re.compile(r"匹配度"),
)

# 必须锚定场景：问题里至少出现一个
_ANCHOR_MARKERS: tuple[str, ...] = (
    "怎么", "如何", "多少", "什么场景", "哪些", "流程", "时候", "当时",
    "哪个", "为什么", "怎样", "什么", "几次", "多久", "哪部分",
)


def lint_question(question: str) -> list[str]:
    """
    检查问题是否违反四条硬规则，返回违规说明列表（空 = 合规）。

    这是**代码断言**，不是 prompt 里的祈使句 —— 违反就是违反。
    """
    problems: list[str] = []
    text = (question or "").strip()
    if not text:
        return ["问题为空"]

    for pattern in _BINARY_PATTERNS:
        if pattern.search(text):
            problems.append(f"二元问法：命中 {pattern.pattern}")
    for pattern in _LEADING_PATTERNS:
        if pattern.search(text):
            problems.append(f"暗示答案：命中 {pattern.pattern}")
    for pattern in _LEAK_PATTERNS:
        if pattern.search(text):
            problems.append(f"泄露缺口：命中 {pattern.pattern}")
    if not any(marker in text for marker in _ANCHOR_MARKERS):
        problems.append("未锚定场景：缺少「怎么做/多少/什么场景」这类具体问法")

    return problems


def is_question_compliant(question: str) -> bool:
    """问题是否通过 linter。"""
    return not lint_question(question)


# ── 规则生成 ──────────────────────────────────────────────────────

def _skill_from_gap(gap_content: str) -> str:
    """
    从 gap 内容里取出要问的技能名。

    gap_content 可能是「Kubernetes」也可能是「简历缺少可量化的业务指标」，
    这里做一次清洗，取一个适合放进问句的短语。
    """
    cleaned = (gap_content or "").strip()
    # 去掉「缺少 X 相关经验」这类前缀描述
    cleaned = re.sub(r"^(缺少|缺乏|没有|未体现|不满足|关于)\s*", "", cleaned)
    cleaned = re.sub(r"(相关)?(经验|技能|能力)$", "", cleaned).strip()
    return cleaned or "这项能力"


def _hard_gate_kind(gap_content: str) -> str:
    """硬性门槛的子类型：education / experience。"""
    if any(marker in (gap_content or "") for marker in _EDUCATION_GATE_MARKERS):
        return "education"
    return "experience"


def build_rule_question(attribution: GapAttribution) -> str:
    """
    按 gap 类别生成问题（规则实现）。

    {probe} 留空 —— 探针是独立条目，不塞进主问题里。
    """
    category = attribution.gap_category

    if category == "hard_gate":
        frame = HARD_GATE_FRAMES[_hard_gate_kind(attribution.gap_content)]
        question = frame.format(probe="").strip()
        return question

    if category == "metric":
        subject = _metric_subject(attribution.gap_content)
        return METRIC_FRAME.format(skill=subject, probe="").strip().rstrip("，, ")

    frame = QUESTION_FRAMES.get(attribution.dimension, QUESTION_FRAMES["delivery"])
    skill = _skill_from_gap(attribution.gap_content)
    question = frame.format(skill=skill, probe="").strip()
    # 去掉句尾多余标点
    question = re.sub(r"[，、,]\s*$", "", question)
    return question


def _metric_subject(gap_content: str) -> str:
    """
    量化类 gap 的提问主体。

    「简历缺少可量化的业务指标，而 JD 明确强调结果导向」不能直接塞进问句，
    这里换成中性表述，避免把「简历缺少」这种说法泄露给候选人。
    """
    content = (gap_content or "").strip()
    if "指标" in content:
        return "相关工作"
    return _skill_from_gap(content)


def build_rule_probes(attribution: GapAttribution) -> list[VerificationProbe]:
    """
    生成追问（规则实现）。

    追问必须**不重复**主问题的信息量 —— 它要能把「没问到点上」的
    回答往下推进一层。
    """
    if attribution.gap_category == "hard_gate":
        kind = _hard_gate_kind(attribution.gap_content)
        if kind == "education":
            questions = (
                "你是在什么情况下完成这个学历的？期间投入了哪些课程或实践？",
                "你学的专业和现在的岗位方向有重合吗？具体重合在哪些内容上？",
            )
        else:
            questions = (
                "你刚才提到的这段时间里，主要负责哪些项目？",
                "这些年份是怎么算出来的？有没有重叠或空档期？",
            )
        triggers = (
            "回答未展开具体信息",
            "回答含糊，未说明判断依据",
        )
        return [
            VerificationProbe(question=q, trigger=t)
            for q, t in zip(questions, triggers)
        ]

    skill = _skill_from_gap(attribution.gap_content)
    strategies = PROBE_STRATEGIES.get(attribution.dimension, DEFAULT_PROBES)
    triggers = (
        "回答未展开具体操作或场景",
        "回答含糊，未说明本人职责与判断依据",
    )
    probes: list[VerificationProbe] = []
    for template, trigger in zip(strategies, triggers):
        probes.append(VerificationProbe(
            question=template.format(skill=skill),
            trigger=trigger,
        ))
    return probes


def build_rule_signals(attribution: GapAttribution) -> list[VerificationSignal]:
    """
    生成判定信号（规则实现）。

    三个判定值必须齐全 —— 面试官需要一个完整的判定刻度，
    而不是只有「够」和「不够」两档。
    """
    keywords = SIGNAL_KEYWORDS.get(attribution.dimension, DEFAULT_SIGNAL_KEYWORDS)
    dim_name = DIMENSION_NAMES.get(attribution.dimension, attribution.dimension)
    if attribution.gap_category == "hard_gate":
        keywords = HARD_GATE_SIGNAL_KEYWORDS
        dim_name = "该准入条件"

    return [
        VerificationSignal(
            verdict="demonstrated",
            evidence=f"回答中出现了{dim_name}方向的具体操作、量级或判断依据",
            keywords=list(keywords["demonstrated"]),
        ),
        VerificationSignal(
            verdict="adjacent",
            evidence=f"回答涉及{dim_name}但停留在使用层面，未展开本人负责的部分",
            keywords=list(keywords["adjacent"]),
        ),
        VerificationSignal(
            verdict="not_evidenced",
            evidence="回答未提供该维度的可核查信息",
            keywords=list(keywords["not_evidenced"]),
        ),
    ]


def build_intent(attribution: GapAttribution) -> str:
    """
    生成提问意图（规则实现）。

    意图要说明「这道题想区分什么」，而不是复述问题。
    """
    if attribution.gap_category == "hard_gate":
        kind = _hard_gate_kind(attribution.gap_content)
        if kind == "education":
            return "确认候选人的实际学历情况是否满足岗位的学历准入条件"
        return "确认候选人的实际工作年限是否满足岗位的年限准入条件"

    dim_name = DIMENSION_NAMES.get(attribution.dimension, attribution.dimension)
    if attribution.evidence_level <= 0:
        return f"确认候选人在{dim_name}方向是否具备相应经历，简历中未见相关证据"
    if attribution.evidence_level < attribution.required_level:
        return (
            f"区分「{dim_name}的相邻经验」与「达到岗位要求的{dim_name}能力」，"
            f"简历现有证据约在层级 {attribution.evidence_level}，岗位要求层级 {attribution.required_level}"
        )
    return f"核对{dim_name}方向的证据深度，确认与岗位要求层级 {attribution.required_level} 是否一致"


# ── LLM 润色 ─────────────────────────────────────────────────────

_REWRITE_SYSTEM_PROMPT = (
    "你是资深面试官，正在把一道结构化的验证问题改写得更自然、更口语化。\n"
    "硬性要求（违反其一就作废）：\n"
    "1. 不得改成「会不会/熟悉/了解/掌握 XX」这种二元问法；\n"
    "2. 不得预设候选人会或不会；\n"
    "3. 不得提及简历、JD、岗位要求、匹配度等字样；\n"
    "4. 必须保留原问题指向的具体场景锚点。\n"
    "只输出改写后的问题正文，不要任何解释。"
)

_REWRITE_SCHEMA = {
    "type": "object",
    "properties": {
        "questions": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "id": {"type": "string"},
                    "question": {"type": "string"},
                },
                "required": ["id", "question"],
                "additionalProperties": False,
            },
        }
    },
    "required": ["questions"],
    "additionalProperties": False,
}


async def _polish_questions(
    questions: list[VerificationQuestion],
    jd_text: str,
    resume_text: str,
) -> None:
    """
    让 LLM 润色问题措辞。**原地修改**，不通过 linter 的直接丢弃。

    这里体现了「确定性内核 + LLM 表现层」的分工：
    问题的结构、意图、追问、信号全由规则决定，
    LLM 只能改写措辞，而且改完必须过 linter。
    """
    if not llm.llm_enabled() or not questions:
        return

    payload = [
        {"id": q.id, "question": q.question, "intent": q.intent}
        for q in questions
    ]
    try:
        result = await llm.complete_json(
            _REWRITE_SYSTEM_PROMPT,
            f"需要改写的问题：\n{payload}\n\n"
            f"岗位要求（节选）：\n{(jd_text or '')[:800]}",
            _REWRITE_SCHEMA,
        )
    except Exception as exc:
        logger.warning("问题润色失败，保留规则版本: %s", exc)
        return

    if not result:
        return

    by_id = {q.id: q for q in questions}
    for item in result.get("questions") or []:
        if not isinstance(item, dict):
            continue
        q = by_id.get(str(item.get("id") or ""))
        if q is None:
            continue
        candidate = (item.get("question") or "").strip()
        if not candidate:
            continue

        problems = lint_question(candidate)
        if problems:
            logger.info("润色后的问题未通过 linter，保留规则版本（id=%s）：%s", q.id, problems)
            continue

        q.question = candidate
        q.rule_generated = False


# ── 主入口 ────────────────────────────────────────────────────────

async def build_verification_plan(
    attributions: list[GapAttribution],
    jd_text: str = "",
    resume_text: str = "",
    max_questions: int = 12,
) -> list[VerificationQuestion]:
    """
    把归因结果转成验证计划（L3）。

    :param attributions: 归因结果
    :param max_questions: 最多产出多少个问题

    排序：severity 倒序（硬性条件优先），同 severity 按维度聚在一起
    —— 同一维度的问题放一起，面试官问起来不散。

    注意：**不按「维度证据层级」过滤**。证据层级是维度级的，
    而 gap 是技能级的 —— 简历里 CI/CD 很强不代表会管 Kubernetes 集群。
    用维度级证据去过滤技能级 gap，会把最该问的问题全过滤掉。
    """
    if not attributions:
        return []

    # 排序：severity 倒序 → 维度分组
    ordered = sorted(attributions, key=lambda a: (-a.severity, a.dimension, a.gap_ref))

    questions: list[VerificationQuestion] = []
    # 记下每个问题对应的归因，linter 兜底时要用它重建规则版本的问题
    attribution_by_id: dict[str, GapAttribution] = {}
    for index, attribution in enumerate(ordered, 1):
        dimension = attribution.dimension
        dimension_name = attribution.dimension_name
        if attribution.gap_category == "hard_gate":
            # 硬性门槛不是能力维度，单独标记，不进能力维度覆盖
            dimension = "hard_gate"
            dimension_name = "硬性条件"

        question = VerificationQuestion(
            id=f"vq#{index}",
            gap_ref=attribution.gap_ref,
            dimension=dimension,
            dimension_name=dimension_name,
            severity=attribution.severity,
            question=build_rule_question(attribution),
            intent=build_intent(attribution),
            probes=build_rule_probes(attribution),
            signals=build_rule_signals(attribution),
            rule_generated=True,
        )
        questions.append(question)
        attribution_by_id[question.id] = attribution

    # 同维度的 gap 合并到一个问题下（避免同一个维度问三遍）
    questions = _merge_same_dimension(questions)

    if len(questions) > max_questions:
        questions = questions[:max_questions]

    await _polish_questions(questions, jd_text, resume_text)

    # 最后再过一次 linter，确保任何路径产出的问题都合规
    for q in questions:
        problems = lint_question(q.question)
        if not problems:
            continue
        logger.warning("问题 %s 未通过 linter，回落规则版本：%s", q.id, problems)
        attribution = attribution_by_id.get(q.id)
        if attribution is None:
            # 合并过的问题取不到原始归因时，用问题自身的信息重建一个
            attribution = GapAttribution(
                gap_ref=q.gap_ref,
                gap_type="missing_skill",
                gap_content=q.question,
                gap_category="hard_gate" if q.dimension == "hard_gate" else "skill",
                dimension="delivery",
            )
        q.question = build_rule_question(attribution)
        q.rule_generated = True

    return questions


def _merge_same_dimension(questions: list[VerificationQuestion]) -> list[VerificationQuestion]:
    """
    同一能力维度的多个 gap 合并成一个问题。

    面试官问一次就能覆盖该维度下所有缺口，比问三遍更有效。
    """
    merged: list[VerificationQuestion] = []
    by_dimension: dict[str, VerificationQuestion] = {}

    for q in questions:
        existing = by_dimension.get(q.dimension)
        if existing is None:
            by_dimension[q.dimension] = q
            merged.append(q)
            continue

        # 硬性门槛不合并 —— 学历与年限是两个不同的准入条件，问法也不同
        if q.dimension == "hard_gate":
            merged.append(q)
            continue

        # 合并：severity 取高，gap_ref 累积，意图合并
        existing.severity = max(existing.severity, q.severity)
        if q.gap_ref and q.gap_ref not in existing.gap_ref:
            existing.gap_ref = f"{existing.gap_ref},{q.gap_ref}"
        existing.intent = f"{existing.intent}；同时覆盖 {q.gap_ref} 的同类缺口"

    return merged


def validate_plan(questions: list[VerificationQuestion]) -> list[str]:
    """
    校验整份验证计划是否符合验收标准。

    返回问题列表（空 = 合规）。给测试和 CI 用。
    """
    problems: list[str] = []
    for q in questions:
        if not q.question:
            problems.append(f"{q.id}: 问题为空")
        else:
            for issue in lint_question(q.question):
                problems.append(f"{q.id}: {issue}")

        if not q.intent:
            problems.append(f"{q.id}: 缺少 intent")
        if not q.probes:
            problems.append(f"{q.id}: 缺少 probes")
        if len(q.probes) > 2:
            problems.append(f"{q.id}: probes 超过 2 条（max_probes=2）")

        verdicts = {s.verdict for s in q.signals}
        missing = set(ALLOWED_VERDICTS) - verdicts
        if missing:
            problems.append(f"{q.id}: signals 缺少判定 {sorted(missing)}")
        for signal in q.signals:
            if signal.verdict not in ALLOWED_VERDICTS:
                problems.append(f"{q.id}: 非法判定值 {signal.verdict!r}")
    return problems
