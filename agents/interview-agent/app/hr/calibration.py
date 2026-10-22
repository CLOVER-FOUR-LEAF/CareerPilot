"""
JD 校准（反向分析，F4，计划书 §4.5）。

输入：一份 JD 原文
输出：能力模型 / 硬门槛 / 过度筛选信号 / 模糊表述

这是纯规则 + LLM 润色的活，难点最低、演示效果最好。

为什么要做反向分析
------------------
HR 视角下，JD 本身也是有问题的。招聘方写 JD 时容易：
- 写「熟悉分布式」这种没法验证的模糊表述
- 把学历、年限、管理经验全设成硬门槛，形成过度筛选
- 能力要求堆一堆，但没有一个是真正区分度高的

这些都会让初筛变成「筛掉人」而不是「选对人」。
本模块把这些问题**显式指出来**，但不替招聘方做决定 ——
输出的是「JD 侧的可优化点」，不是「对候选人的判断」。
"""
from __future__ import annotations

import logging
import re
from typing import Optional

from pydantic import BaseModel, Field

from .. import llm
from .capability import (
    CAPABILITY_DIMENSIONS,
    DIMENSION_NAMES,
    build_capability_model,
    infer_required_level,
    normalize,
)
from .compliance import DISCLAIMER_TEXT

logger = logging.getLogger(__name__)


# ── 输出模型 ──────────────────────────────────────────────────────

class HardGate(BaseModel):
    """JD 里的硬性门槛。"""

    item: str = ""
    type: str = ""          # education / experience / certification
    one_vote_veto: bool = False


class OverFilteringSignal(BaseModel):
    """过度筛选信号。"""

    signal: str = ""
    risk: str = "screening_out"


class VaguePhrase(BaseModel):
    """模糊表述。"""

    phrase: str = ""
    why_vague: str = ""
    suggestion: str = ""


class JdCalibration(BaseModel):
    """JD 校准报告。"""

    capability_model: list[dict] = Field(default_factory=list)
    hard_gates: list[HardGate] = Field(default_factory=list)
    over_filtering_signals: list[OverFilteringSignal] = Field(default_factory=list)
    vague_phrases: list[VaguePhrase] = Field(default_factory=list)
    disclaimer: str = DISCLAIMER_TEXT


# ── 硬门槛识别（规则）────────────────────────────────────────────

_EDUCATION_GATES: tuple[tuple[str, str], ...] = (
    ("博士", "education"),
    ("硕士及以上", "education"),
    ("硕士", "education"),
    ("本科及以上", "education"),
    ("本科", "education"),
    ("大专及以上", "education"),
    ("大专", "education"),
    ("统招本科", "education"),
)

_CERT_GATES: tuple[str, ...] = (
    "PMP", "CPA", "CFA", "ACCA", "注册会计师", "法律职业资格",
    "教师资格证", "一级建造师", "二级建造师", "注册建筑师", "注册工程师",
)

# 「N 年经验」类表述。年限门槛常写成「5 年以上后端开发经验」，
# 中间夹着岗位修饰词，所以不能只匹配「N 年 + 经验」的紧邻形式。
_EXPERIENCE_YEAR_RE = re.compile(r"(\d+)\s*年\s*(?:以上|及以上|\+|～|~\s*)?")
_EXPERIENCE_TAIL_WORDS: tuple[str, ...] = ("经验", "工作经验", "相关经验", "从业")


def _find_experience_gate(jd_text: str) -> Optional[str]:
    """
    抽取年限硬门槛。

    做法：找到「N 年」之后，在其后一小段窗口内找「经验」类词，
    命中才算年限门槛 —— 避免把「有 3 年高并发系统设计经验者优先」
    这类描述误当成硬性年限要求。
    """
    for match in _EXPERIENCE_YEAR_RE.finditer(jd_text):
        tail = jd_text[match.end():match.end() + 14]
        if any(word in tail for word in _EXPERIENCE_TAIL_WORDS):
            return match.group(0).strip()
    return None


def _find_hard_gates(jd_text: str) -> list[HardGate]:
    """识别 JD 里的硬性门槛。规则实现。"""
    gates: list[HardGate] = []
    text = jd_text or ""

    for alias, kind in _EDUCATION_GATES:
        if alias in text:
            gates.append(HardGate(item=alias, type=kind, one_vote_veto=True))
            break  # 只取最严的一条学历要求

    for cert in _CERT_GATES:
        if cert in text:
            gates.append(HardGate(item=cert, type="certification", one_vote_veto=True))
            break

    experience = _find_experience_gate(text)
    if experience:
        gates.append(HardGate(item=experience, type="experience", one_vote_veto=True))

    return gates


# ── 过度筛选识别（规则）──────────────────────────────────────────

# 职位名里出现这些词，说明岗位本身带管理职责
_MANAGEMENT_TITLE_MARKERS: tuple[str, ...] = ("经理", "主管", "总监", "leader", "lead", "manager", "架构师")
# 职位名里出现这些词，说明岗位是纯技术执行岗
_IC_TITLE_MARKERS: tuple[str, ...] = ("工程师", "开发", "programmer", "scientist", "技术员")


def _find_over_filtering(jd_text: str, job_title: str = "") -> list[OverFilteringSignal]:
    """
    识别过度筛选信号。规则实现。

    典型场景（计划书给的例子）：
    「同时要求 5 年经验 + 3 年管理经验，但职位名未体现管理职责」
    """
    signals: list[OverFilteringSignal] = []
    text = jd_text or ""
    title = (job_title or "").lower()

    title_has_management = any(marker.lower() in title for marker in _MANAGEMENT_TITLE_MARKERS)
    title_is_ic = any(marker.lower() in title for marker in _IC_TITLE_MARKERS)

    mentions_management = bool(re.search(r"管理", text))
    mentions_years = bool(re.search(r"\d+\s*年", text))

    # 要求管理经验，但职位名是纯技术岗 → 过度筛选
    if mentions_management and mentions_years and title_is_ic and not title_has_management:
        signals.append(OverFilteringSignal(
            signal="JD 要求管理经验，但职位名体现的是纯技术执行岗，两者容易冲突造成过度筛除",
            risk="screening_out",
        ))

    # 同时要求管理经验与技术产出
    if mentions_management and re.search(r"(技术|开发|架构|编码)", text):
        signals.append(OverFilteringSignal(
            signal="同时要求管理职责与技术产出，简历上两类证据通常难以同时具备",
            risk="screening_out",
        ))

    # 多个独立的年限门槛
    year_hits = re.findall(r"(\d+)\s*年", text)
    distinct_years = {y for y in year_hits if int(y) > 0}
    if len(distinct_years) >= 2 and mentions_management:
        signals.append(OverFilteringSignal(
            signal="同时设置了多个年限与经验要求，且包含管理相关门槛，存在重复筛除风险",
            risk="screening_out",
        ))

    # 硬门槛过多
    gates = _find_hard_gates(text)
    if len(gates) >= 3:
        signals.append(OverFilteringSignal(
            signal="同时设置了多项一票否决式硬门槛（学历/年限/资质），简历筛选阶段会直接排除大量候选人",
            risk="screening_out",
        ))

    return signals


# ── 模糊表述识别（规则）──────────────────────────────────────────

# 「熟悉分布式」这类：动词 + 无法定位到具体层级的名词
_VAGUE_VERBS: tuple[str, ...] = ("熟悉", "精通", "掌握", "了解", "理解", "具备", "具有")
# 这些词后面接的名词过于宽泛，无法判断要的是哪一层
_VAGUE_NOUNS: tuple[str, ...] = (
    "分布式", "微服务", "架构", "大数据", "高并发", "性能优化", "系统设计",
    "缓存", "消息队列", "容器化", "云原生", "算法", "数据处理", "安全",
    "常用框架", "相关技术", "技术栈", "工程实践", "最佳实践",
)

# 「良好的沟通能力」这类：形容词 + 泛化名词
_VAGUE_ADJECTIVES: tuple[str, ...] = ("良好的", "优秀的", "出色的", "扎实的", "丰富的", "较强的")

_VAGUE_PHRASE_PATTERN = re.compile(
    r"(" + "|".join(_VAGUE_VERBS) + r")\s*(" + "|".join(_VAGUE_NOUNS) + r")"
)
_VAGUE_ABILITY_PATTERN = re.compile(
    r"(" + "|".join(_VAGUE_ADJECTIVES) + r")\s*(.{0,6}能力)"
)


def _find_vague_phrases(jd_text: str) -> list[VaguePhrase]:
    """
    识别模糊表述。规则实现。

    至少给出「为什么模糊」和「怎么改写」两条建议 —— 只说「这里很模糊」
    没有可操作性。
    """
    phrases: list[VaguePhrase] = []
    text = jd_text or ""

    seen: set[str] = set()
    for match in _VAGUE_PHRASE_PATTERN.finditer(text):
        phrase = match.group(0).strip()
        if phrase in seen:
            continue
        seen.add(phrase)
        noun = match.group(2)
        phrases.append(VaguePhrase(
            phrase=phrase,
            why_vague=f"未说明「{noun}」指的是哪一层，候选人无法判断自己是否达标，面试官也无法据此判断",
            suggestion=f"拆成具体项：例如把「{noun}」拆成具体的技术点、场景与深度要求",
        ))

    for match in _VAGUE_ABILITY_PATTERN.finditer(text):
        phrase = match.group(0).strip()
        if phrase in seen:
            continue
        seen.add(phrase)
        phrases.append(VaguePhrase(
            phrase=phrase,
            why_vague="形容词无法验证，缺少可观察的行为描述与衡量标准",
            suggestion="改写为可观察的行为：例如「能在跨部门需求冲突中主动对齐并给出结论」",
        ))

    return phrases


# ── LLM 润色 ─────────────────────────────────────────────────────

_CALIBRATION_SYSTEM_PROMPT = (
    "你是资深技术招聘顾问，正在审阅一份岗位描述（JD）的质量。\n"
    "你的任务是把下面这份机器分析结果补充得更具体、可执行。\n"
    "只做两件事：\n"
    "1) 为 over_filtering_signals 补充更具体的说明；\n"
    "2) 为 vague_phrases 补充更贴切的 why_vague 与 suggestion。\n"
    "不要新增或删除条目，不要改变条目数量。\n"
    "不要给出录用、淘汰一类的结论。严格按 JSON 输出。"
)

_CALIBRATION_SCHEMA = {
    "type": "object",
    "properties": {
        "over_filtering_signals": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "signal": {"type": "string"},
                    "risk": {"type": "string"},
                },
                "required": ["signal"],
            },
        },
        "vague_phrases": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "phrase": {"type": "string"},
                    "why_vague": {"type": "string"},
                    "suggestion": {"type": "string"},
                },
                "required": ["phrase"],
            },
        },
    },
    "required": ["over_filtering_signals", "vague_phrases"],
    "additionalProperties": False,
}


async def _polish_with_llm(calibration: JdCalibration, jd_text: str) -> JdCalibration:
    """
    用 LLM 润色信号说明与模糊表述建议。

    失败即返回规则版结果 —— 校准报告的**结构**由规则保证，
    LLM 只影响措辞，不影响条目数量与判定。
    """
    if not llm.llm_enabled():
        return calibration
    if not calibration.over_filtering_signals and not calibration.vague_phrases:
        return calibration

    payload = {
        "over_filtering_signals": [
            {"signal": s.signal, "risk": s.risk} for s in calibration.over_filtering_signals
        ],
        "vague_phrases": [
            {"phrase": v.phrase, "why_vague": v.why_vague, "suggestion": v.suggestion}
            for v in calibration.vague_phrases
        ],
    }

    try:
        result = await llm.complete_json(
            _CALIBRATION_SYSTEM_PROMPT,
            f"原始 JD：\n{(jd_text or '')[:1500]}\n\n"
            f"机器分析结果（JSON）：\n{payload}",
            _CALIBRATION_SCHEMA,
        )
    except Exception as exc:
        logger.warning("JD 校准润色失败，保留规则结果: %s", exc)
        return calibration

    if not result:
        return calibration

    # 按 phrase / signal 文本回填，保持条目数量与顺序不变
    signals_by_text = {s.signal: s for s in calibration.over_filtering_signals}
    for item in result.get("over_filtering_signals") or []:
        if not isinstance(item, dict):
            continue
        text = (item.get("signal") or "").strip()
        target = signals_by_text.get(text)
        if target is not None and item.get("signal"):
            target.signal = item["signal"]

    phrases_by_text = {v.phrase: v for v in calibration.vague_phrases}
    for item in result.get("vague_phrases") or []:
        if not isinstance(item, dict):
            continue
        text = (item.get("phrase") or "").strip()
        target = phrases_by_text.get(text)
        if target is None:
            continue
        if item.get("why_vague"):
            target.why_vague = item["why_vague"]
        if item.get("suggestion"):
            target.suggestion = item["suggestion"]

    return calibration


# ── 主入口 ────────────────────────────────────────────────────────

async def calibrate_jd(jd_text: str, job_title: str = "") -> JdCalibration:
    """
    对一份 JD 做反向校准。

    :param jd_text: JD 全文
    :param job_title: 职位名（用于识别「职位名与要求不符」的过度筛选）
    """
    if not (jd_text or "").strip():
        return JdCalibration()

    calibration = JdCalibration(
        capability_model=build_capability_model(jd_text),
        hard_gates=_find_hard_gates(jd_text),
        over_filtering_signals=_find_over_filtering(jd_text, job_title),
        vague_phrases=_find_vague_phrases(jd_text),
    )

    calibration = await _polish_with_llm(calibration, jd_text)
    return calibration
