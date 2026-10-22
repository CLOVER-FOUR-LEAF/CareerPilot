"""
追问树与判定（F3，计划书 §4.4）。

输入：面试官录入的候选人回答
输出：coverage 判定 + 置信度 + 理由 + 原文引用

判定枚举（硬约束 R1）
----------------------
| 值 | 含义 | 禁止的近义说法 |
|---|---|---|
| `demonstrated` | 有直接证据支持该能力达到要求层级 | ~~符合/通过/胜任~~ |
| `adjacent` | 有相邻经验，部分满足 | ~~基本符合/勉强~~ |
| `not_evidenced` | 本次提问未获得证据 | ~~不符合/不合格/淘汰~~ |

**`not_evidenced` ≠ 不适合。** 这个措辞差别就是合规红线的具体体现，
在代码常量、prompt、UI 文案、文档四处必须一致。

判定怎么算
----------
1. **规则优先**：拿回答去命中 signals 里的特征词。
   命中 demonstrated 的特征词 → demonstrated，置信度高；
   只命中 adjacent 的特征词 → adjacent；
   命中 not_evidenced 的特征词（「不太清楚」「同事负责」等）→ not_evidenced。
2. **LLM 兜底**：规则判不出来时交给 LLM 做语义归纳，
   但它**必须返回回答里的原文片段**，由代码校验片段确实存在。
3. **触发追问**：首轮没命中任何 signal（或置信度低于阈值）→ 提示追问。

这样「判定」这个确定性内核不交给 LLM，LLM 只在规则无解时补一手，
且它的输出必须通过代码校验才能被采信。
"""
from __future__ import annotations

import logging
import re
from typing import Optional

from pydantic import BaseModel, Field

from .. import llm
from .compliance import (
    ALLOWED_VERDICTS,
    EVIDENCE_MIN_LENGTH,
    assert_allowed_verdict,
    extract_evidence_ref,
    verify_evidence_ref,
)
from .verification import VerificationQuestion

logger = logging.getLogger(__name__)


# ── 配置 ──────────────────────────────────────────────────────────

# 低于此置信度就认为「没问到位」，应触发追问
CONFIDENCE_THRESHOLD = 0.6
# 规则命中特征词后给出的基础置信度
RULE_CONFIDENCE_DEMONSTRATED = 0.75
RULE_CONFIDENCE_ADJACENT = 0.65
RULE_CONFIDENCE_NOT_EVIDENCED = 0.7
# 特征词命中数量带来的置信度加成
RULE_CONFIDENCE_PER_EXTRA_HIT = 0.08
RULE_CONFIDENCE_MAX = 0.95
# 单题最多追问次数
MAX_PROBES = 2


# ── 输出模型 ──────────────────────────────────────────────────────

class CoverageVerdict(BaseModel):
    """
    一次追问的判定结果，对应计划书 §4.4 的输出结构。
    """

    vq_id: str = ""
    coverage: str = ""
    confidence: float = 0.0
    matched_signal_index: int = -1
    reason: str = ""
    evidence_ref: str = ""
    # 是否建议继续追问
    should_probe: bool = False
    # 建议使用的追问（最多 2 条）
    suggested_probes: list[str] = Field(default_factory=list)


# ── 规则判定 ──────────────────────────────────────────────────────

def _normalize(text: str) -> str:
    return re.sub(r"\s+", "", (text or "").lower())


def _count_hits(answer: str, keywords: list[str]) -> int:
    """统计回答里命中了多少个特征词。"""
    normalized = _normalize(answer)
    hits = 0
    for keyword in keywords:
        if keyword and _normalize(keyword) in normalized:
            hits += 1
    return hits


def _rule_judge(question: VerificationQuestion, answer: str) -> Optional[CoverageVerdict]:
    """
    规则判定。返回 None 表示规则判不出来，需要 LLM 兜底。

    判定顺序按「证据强度」从高到低：
    demonstrated 的特征词最具体，not_evidenced 的特征词最明确（都是否定表述）。
    """
    text = (answer or "").strip()
    if not text:
        return CoverageVerdict(
            vq_id=question.id,
            coverage="not_evidenced",
            confidence=0.9,
            matched_signal_index=-1,
            reason="本次未记录回答内容，未获取到该维度的证据。",
            evidence_ref="",
        )

    # 先看明确否定的表述（「不太清楚」「同事负责」这类）——
    # 它们是明确的「没问到」，不应该被其他弱特征词盖过
    not_evidenced_index = next(
        (i for i, s in enumerate(question.signals) if s.verdict == "not_evidenced"), -1
    )
    if not_evidenced_index >= 0:
        hits = _count_hits(text, question.signals[not_evidenced_index].keywords)
        if hits:
            return CoverageVerdict(
                vq_id=question.id,
                coverage="not_evidenced",
                confidence=min(RULE_CONFIDENCE_MAX, RULE_CONFIDENCE_NOT_EVIDENCED),
                matched_signal_index=not_evidenced_index,
                reason="回答中出现明确的「未参与/不清楚」类表述，本次未获取到该能力的证据。",
                evidence_ref=_first_hit_snippet(text, question.signals[not_evidenced_index].keywords),
            )

    demonstrated_index = next(
        (i for i, s in enumerate(question.signals) if s.verdict == "demonstrated"), -1
    )
    adjacent_index = next(
        (i for i, s in enumerate(question.signals) if s.verdict == "adjacent"), -1
    )

    demonstrated_hits = (
        _count_hits(text, question.signals[demonstrated_index].keywords)
        if demonstrated_index >= 0 else 0
    )
    adjacent_hits = (
        _count_hits(text, question.signals[adjacent_index].keywords)
        if adjacent_index >= 0 else 0
    )

    if demonstrated_hits >= 2:
        confidence = min(
            RULE_CONFIDENCE_MAX,
            RULE_CONFIDENCE_DEMONSTRATED + (demonstrated_hits - 2) * RULE_CONFIDENCE_PER_EXTRA_HIT,
        )
        return CoverageVerdict(
            vq_id=question.id,
            coverage="demonstrated",
            confidence=confidence,
            matched_signal_index=demonstrated_index,
            reason=(
                f"回答中出现 {demonstrated_hits} 处具体操作/量级/判断依据类表述，"
                f"与「{question.signals[demonstrated_index].evidence}」相符。"
            ),
            evidence_ref=_first_hit_snippet(text, question.signals[demonstrated_index].keywords),
        )

    if adjacent_hits >= 1 and demonstrated_hits == 0:
        return CoverageVerdict(
            vq_id=question.id,
            coverage="adjacent",
            confidence=RULE_CONFIDENCE_ADJACENT,
            matched_signal_index=adjacent_index,
            reason=(
                "回答涉及该能力但停留在使用层面，未展开本人负责的部分，"
                "属于相邻经验。"
            ),
            evidence_ref=_first_hit_snippet(text, question.signals[adjacent_index].keywords),
        )

    if demonstrated_hits == 1:
        return CoverageVerdict(
            vq_id=question.id,
            coverage="adjacent",
            confidence=RULE_CONFIDENCE_ADJACENT,
            matched_signal_index=adjacent_index,
            reason="回答仅出现一处具体表述，尚不足以确认达到岗位要求的层级。",
            evidence_ref=_first_hit_snippet(text, question.signals[demonstrated_index].keywords),
        )

    return None  # 规则判不出来 → 交给 LLM


def _first_hit_snippet(answer: str, keywords: list[str]) -> str:
    """从回答里截出包含特征词的那一段，作为原文引用。"""
    if not answer:
        return ""
    normalized = _normalize(answer)
    for keyword in keywords:
        if not keyword:
            continue
        target = _normalize(keyword)
        if target and target in normalized:
            # 用原文（不归一化）找位置，截出上下文
            index = answer.lower().find(keyword.lower())
            if index >= 0:
                start = max(0, index - 20)
                end = min(len(answer), index + len(keyword) + 30)
                return answer[start:end].strip()
    return ""


# ── LLM 兜底 ──────────────────────────────────────────────────────

_JUDGE_SYSTEM_PROMPT = (
    "你是面试评估助手，正在判断一段回答对某个能力维度的证据强度。\n"
    "只能给出三种判定之一：\n"
    "1. demonstrated —— 回答中有直接证据支持该能力达到要求层级\n"
    "2. adjacent —— 有相邻经验，部分满足\n"
    "3. not_evidenced —— 本次提问未获得证据\n"
    "注意：not_evidenced 只表示这次没问到，不代表候选人不具备该能力。\n"
    "禁止使用「符合/不符合/通过/未通过/录用/淘汰/胜任/不合格」等表述。\n"
    "必须引用回答中的一段原文（放在「」里）。\n"
    "严格按 JSON 输出。"
)

_JUDGE_SCHEMA = {
    "type": "object",
    "properties": {
        "coverage": {"type": "string", "enum": list(ALLOWED_VERDICTS)},
        "confidence": {"type": "number", "minimum": 0, "maximum": 1},
        "reason": {"type": "string"},
        "evidence_ref": {"type": "string"},
    },
    "required": ["coverage", "confidence", "reason"],
    "additionalProperties": False,
}


async def _llm_judge(
    question: VerificationQuestion,
    answer: str,
) -> Optional[CoverageVerdict]:
    """
    LLM 兜底判定。失败或校验不过返回 None，由调用方给出保守结论。
    """
    if not llm.llm_enabled():
        return None

    signal_text = "\n".join(
        f"- {s.verdict}：{s.evidence}" for s in question.signals
    )
    try:
        result = await llm.complete_json(
            _JUDGE_SYSTEM_PROMPT,
            f"验证问题：{question.question}\n"
            f"提问意图：{question.intent}\n"
            f"判定刻度：\n{signal_text}\n\n"
            f"候选人回答：\n{answer}\n\n"
            f"请给出判定，并在 evidence_ref 里用「」包住回答中的一段原文。",
            _JUDGE_SCHEMA,
        )
    except Exception as exc:
        logger.warning("LLM 判定失败，回落保守结论: %s", exc)
        return None

    if not result:
        return None

    coverage = str(result.get("coverage") or "")
    if coverage not in ALLOWED_VERDICTS:
        logger.warning("LLM 返回非法判定值 %r，忽略", coverage)
        return None

    raw_ref = (result.get("evidence_ref") or "").strip()
    ref = extract_evidence_ref(raw_ref) or raw_ref
    if ref and not verify_evidence_ref(ref, answer, EVIDENCE_MIN_LENGTH):
        # R3：引用校验不过 → 整条结论不采信
        logger.info("LLM 引用的原文未能在回答中定位，丢弃该结论")
        return None

    try:
        confidence = float(result.get("confidence") or 0.0)
    except (TypeError, ValueError):
        confidence = 0.0

    return CoverageVerdict(
        vq_id=question.id,
        coverage=coverage,
        confidence=max(0.0, min(1.0, confidence)),
        matched_signal_index=-1,
        reason=(result.get("reason") or "").strip(),
        evidence_ref=ref,
    )


# ── 主入口 ────────────────────────────────────────────────────────

async def judge_answer(
    question: VerificationQuestion,
    answer: str,
    round_no: int = 1,
) -> CoverageVerdict:
    """
    对一段回答给出覆盖判定。

    :param question: 验证问题（含 signals 与 probes）
    :param answer: 面试官录入的候选人回答
    :param round_no: 第几轮追问
    """
    text = (answer or "").strip()

    verdict = _rule_judge(question, text)
    if verdict is None:
        verdict = await _llm_judge(question, text)

    if verdict is None:
        # 规则判不出、LLM 也不可用 → 保守结论：本次未获取到证据。
        # 注意这不是「不具备」，只是这次没问到。
        verdict = CoverageVerdict(
            vq_id=question.id,
            coverage="not_evidenced",
            confidence=0.4,
            matched_signal_index=-1,
            reason="回答中未出现可核查的具体信息，本次提问未获取到该能力的证据。",
            evidence_ref="",
        )

    assert_allowed_verdict(verdict.coverage)

    # 置信度不足或没命中任何信号 → 建议继续追问
    if verdict.confidence < CONFIDENCE_THRESHOLD or verdict.matched_signal_index < 0:
        verdict.should_probe = True
        verdict.suggested_probes = [
            probe.question for probe in question.probes[:MAX_PROBES]
        ]

    return verdict
