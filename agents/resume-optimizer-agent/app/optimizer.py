"""
Resume Optimizer Agent - 单条经历改写引擎

输入一条简历要点 + 目标 JD 技能，输出 STAR 结构化改写。
完全规则驱动：弱动词替换、强动词开头、缺失指标补脚手架、JD 技能注入。
"""
import logging
import re
from typing import Optional

from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)

# 契约 §7.4 的 tone 取值
VALID_TONES = ("professional", "passionate", "humble")


# ─── 输出模型 ──────────────────────────────────────────────────

class StarBreakdown(BaseModel):
    situation: str = ""
    task: str = ""
    action: str = ""
    result: str = ""


class ResumeOptimizeResult(BaseModel):
    original: str = ""
    rewritten: str = ""
    injected_skills: list[str] = Field(default_factory=list)
    star_breakdown: StarBreakdown = Field(default_factory=StarBreakdown)
    change_note: str = ""


# ─── 词表 ──────────────────────────────────────────────────────

# 弱动词 -> 强动词。改写的核心动作之一。
# 替换词刻意选得短且不与宾语重复，避免出现"协同推进交付模块交付"这类叠加病句。
WEAK_VERB_MAP: dict[str, str] = {
    "负责": "主导",
    "参与": "落地",
    "协助": "协同",
    "配合": "联动",
    "帮助": "支撑",
    "跟进": "跟踪推动",
    "完成": "达成",
    "进行": "执行",
    "做了": "实现",
    "处理": "承接优化",
    "维护": "运维演进",
    "熟悉": "掌握",
    "了解": "接触",
    "使用了": "基于",
}

# 各语气可用的强动词池（用于在输入没有明确动词时补一个）
TONE_STRONG_VERBS: dict[str, tuple[str, ...]] = {
    "professional": ("主导", "设计并落地", "搭建", "推动"),
    "passionate": ("牵头", "从 0 到 1 搭建", "主打", "突破"),
    "humble": ("承接并推进", "配合团队完成", "逐步完善", "在导师指导下独立完成"),
}

# 句末的空洞尾巴，替换掉弱动词后必须清掉，否则"主导用户增长相关工作"依然空洞
VAGUE_TAIL_PATTERN = re.compile(
    r"(?:相关|方面|等方面)?(?:的)?(?:工作|内容|部分|任务|业务)$"
)

# 主题关键词 -> 可用的方法/技术手段。用于给空洞的句子补方法论。
TOPIC_METHODS: dict[str, tuple[str, ...]] = {
    "用户增长": ("用户分层运营", "增长实验闭环", "渠道归因分析"),
    "用户运营": ("用户分层运营", "私域触达", "生命周期管理"),
    "数据分析": ("指标体系设计", "归因分析", "报表自动化"),
    "性能优化": ("性能剖析", "缓存策略优化", "并发模型调优"),
    "性能调优": ("性能剖析", "缓存策略优化", "并发模型调优"),
    "系统设计": ("领域建模", "分层架构设计", "容量规划"),
    "架构设计": ("领域建模", "分层架构设计", "容量规划"),
    "高并发": ("限流降级", "异步化改造", "多级缓存"),
    "高可用": ("故障演练", "降级预案", "多活容灾"),
    "分布式": ("一致性设计", "分片策略", "幂等与重试"),
    "微服务": ("服务拆分", "链路治理", "灰度发布"),
    "风控": ("规则引擎", "特征工程", "模型监控"),
    "财务": ("预算建模", "成本结构拆解", "资金规划"),
    "精益生产": ("价值流分析", "防错设计", "拉动式排产"),
    "六西格玛": ("DMAIC 改进", "过程能力分析", "SPC 管控"),
    "良率": ("SPC 过程能力分析", "制程巡检", "失效模式分析"),
    "自动化": ("产线节拍优化", "设备联网", "防呆设计"),
    "需求分析": ("需求评审", "原型验证", "迭代排期"),
    "项目管理": ("里程碑拆解", "风险登记册", "跨团队协同"),
    "转型": ("能力盘点", "梯度学习计划", "在岗项目历练"),
}

DEFAULT_METHODS: tuple[str, ...] = ("数据驱动分析", "跨团队协同推进", "标准化流程沉淀")

# 指标脚手架：原文完全没有数字时使用
METRIC_TEMPLATE = (
    "，将【核心指标，如注册转化率】从【基线值】提升至【目标值】，"
    "并在【周期，如 3 个月】内稳定保持"
)

_QUANT_PATTERN = re.compile(r"\d")


# ─── 工具函数 ──────────────────────────────────────────────────

def _has_number(text: str) -> bool:
    return bool(_QUANT_PATTERN.search(text))


def _detect_topics(text: str) -> list[str]:
    """找出句子涉及的主题，用于挑选合适的方法论"""
    return [topic for topic in TOPIC_METHODS if topic in text]


def _replace_weak_verbs(text: str) -> tuple[str, list[str]]:
    """
    把弱动词替换为强动词，返回 (新文本, 被替换的词列表)。
    已存在的强动词保持不动，避免二次改写退化成同义词堆砌。
    """
    strong = ("主导", "设计", "搭建", "推动", "建立", "重构", "优化",
              "打造", "牵头", "从 0 到 1", "独立负责", "负责到底")
    if any(word in text for word in strong):
        return text, []

    replaced: list[str] = []
    result = text
    for weak, replacement in WEAK_VERB_MAP.items():
        if weak in result:
            result = result.replace(weak, replacement, 1)
            replaced.append(weak)
    return result, replaced


def _pick_verb(text: str, tone: str) -> str:
    """按语气挑一个强动词开头"""
    if any(word in text for word in ("主导", "设计", "搭建", "推动", "打造", "牵头")):
        return ""
    pool = TONE_STRONG_VERBS.get(tone, TONE_STRONG_VERBS["professional"])
    return pool[0]


def _strip_vague_tail(text: str) -> str:
    """
    去掉句末的空洞尾巴。

    弱动词被替换后，"负责用户增长相关工作"会变成"主导用户增长相关工作"，
    语义上仍然等于"负责相关工作"，必须把尾巴一并去掉才是真正的改写。
    """
    previous = None
    result = text
    # 循环剥离，因为"…相关的工作"这类尾巴会叠加出现多层
    while previous != result:
        previous = result
        result = VAGUE_TAIL_PATTERN.sub("", result).strip()
    return result or text


def _apply_tone(text: str, tone: str) -> str:
    """按语气替换句首动词，让三种语气在措辞力度上真正产生差异"""
    if tone == "professional":
        return text

    replacements = {
        "主导": "牵头",
        "落地": "从 0 到 1 搭建",
        "协同": "推动",
        "联动": "协同",
        "设计并落地": "从 0 到 1 搭建",
    } if tone == "passionate" else {
        "主导": "承接并推进",
        "牵头": "承接并推进",
        "从 0 到 1 搭建": "承接并推进",
        "落地": "承接并推进",
        "推动": "配合团队完成",
        "打造": "逐步完善",
    }

    for verb, replacement in replacements.items():
        if text.startswith(verb):
            return replacement + text[len(verb):]
    return text


def _extract_focus(text: str) -> str:
    """从原文里抽出"做什么"的核心名词短语"""
    cleaned = re.sub(r"^[负责参与协助配合帮助跟进了做的]+\s*", "", text)
    cleaned = re.sub(r"[的了吧和与及、,，。；;]+$", "", cleaned.strip())
    cleaned = re.sub(r"(相关|方面等工作|工作|内容|项目|部分)$", "", cleaned).strip()
    return cleaned if cleaned else "核心工作"


# ─── 主改写函数 ────────────────────────────────────────────────

def optimize_bullet(
    bullet: str,
    jd_skills: Optional[list[str]] = None,
    tone: str = "professional",
) -> ResumeOptimizeResult:
    """
    把一条模糊的简历要点改写成 STAR 结构的一句话。

    - 弱动词（负责/参与/协助…）替换为强动词
    - 依据主题补上方法论
    - 注入 JD 要求但原文没提的技能
    - 完全没有数字时补量化脚手架
    """
    original = bullet.strip()
    if tone not in VALID_TONES:
        tone = "professional"

    if not original:
        return ResumeOptimizeResult(original="")

    jd_skills = jd_skills or []

    # ── 1. 抽主题与核心短语 ──
    topics = _detect_topics(original)
    focus = _extract_focus(original)

    # ── 2. 替换弱动词 / 补强动词，并清掉空洞句尾 ──
    core, replaced = _replace_weak_verbs(original)
    core = _strip_vague_tail(core)

    added_verb = ""
    if not replaced and not any(w in core for w in ("主导", "设计", "搭建", "推动", "打造", "牵头")):
        added_verb = _pick_verb(core, tone)
        core = f"{added_verb}{core}"

    # ── 3. 按语气调整动词措辞（三种语气必须读起来明显不同）──
    core = _apply_tone(core, tone)

    # ── 4. 补方法论 ──
    # 只有在原文本来就空洞（替换过弱动词）时补；原文已经强动词 + 量化时不再堆砌
    needs_methods = bool(replaced) or not _has_number(original)
    methods: list[str] = []
    if needs_methods:
        method_source = TOPIC_METHODS.get(topics[0], DEFAULT_METHODS) if topics else DEFAULT_METHODS
        for method in method_source:
            if method not in core:
                methods.append(method)
            if len(methods) == 2:
                break

    # ── 4. 注入 JD 技能 ──
    injected = [s for s in jd_skills if s and s not in original]
    injected = injected[:3]

    # ── 5. 结果段：补量化脚手架 ──
    if _has_number(original):
        result_part = ""
        metric_note = "沿用原有量化结果"
    else:
        result_part = METRIC_TEMPLATE
        metric_note = "原文缺少数字，已插入量化脚手架，请替换【】中的占位"

    # ── 6. 拼装改写句 ──
    pieces: list[str] = [core]
    if injected:
        pieces.append("，引入" + "、".join(injected))
    if methods:
        pieces.append("，通过" + "、".join(methods) + "落地")
    pieces.append(result_part)
    rewritten = "".join(pieces).rstrip("，") + "。"

    # ── 7. STAR 拆解 ──
    situation = f"在{focus}相关的业务场景下，团队对交付质量与效率提出了更高要求" if topics \
        else f"在{focus}的工作场景下，原有做法缺乏统一标准"
    task = f"需要独立承担{focus}，并把成果转化为可衡量的业务收益"
    action = f"{added_verb or (replaced[0] if replaced else '主导')}{focus}" \
        + (f"，引入{'、'.join(injected)}" if injected else "") \
        + (f"，通过{'、'.join(methods)}推进" if methods else "")
    breakdown = StarBreakdown(
        situation=situation,
        task=task,
        action=action,
        result=f"请补齐具体数字（现状 → 动作 → 结果），参考模板：{METRIC_TEMPLATE}"
        if not _has_number(original) else "原文已有量化数据，建议补充基线与对比口径",
    )

    # ── 8. 变更说明 ──
    notes: list[str] = []
    if replaced:
        notes.append("将弱动词「" + "、".join(replaced) + "」替换为强动词")
    elif added_verb:
        notes.append(f"补充强动词「{added_verb}」开头")
    if injected:
        notes.append("注入 JD 要求技能：" + "、".join(injected))
    if methods:
        notes.append("补充方法论：" + "、".join(methods))
    if not _has_number(original):
        notes.append("原文无量化结果，插入指标脚手架")
    if tone == "passionate":
        notes.append("按 passionate 语气强化动词力度")
    elif tone == "humble":
        notes.append("按 humble 语气收敛表述，避免过度拔高")
    if not notes:
        notes.append("原文已具备强动词与量化结果，仅做结构调整")

    return ResumeOptimizeResult(
        original=original,
        rewritten=rewritten,
        injected_skills=injected,
        star_breakdown=breakdown,
        change_note="；".join(notes),
    )
