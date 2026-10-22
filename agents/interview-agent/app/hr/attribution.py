"""
归因层：gap → 能力维度（L2，计划书 §4.2）。

输入：gap（来自 L1 匹配层）+ jd_text + resume_text + jd_skills
输出：每个 gap 附带 dimension / required_level / evidence_level / explanation / evidence_ref

实现分工（计划书 §4.2 表格）
--------------------------
| 部分 | 谁做 | 理由 |
|---|---|---|
| dimension 归类 | 规则 | 确定性、可测试、可审计 |
| required_level 推断 | 规则 | 同上 |
| explanation 文案 | LLM | 需要自然语言归纳 |
| evidence_ref 抽取 | LLM 产出 + 代码校验 | LLM 抽，原文校验 |

**遵守 C3**：分类和分级永远由规则决定，LLM 只写文案。
LLM 不可用时，explanation 退化为规则拼装的确定性文案 ——
功能完整，只是没那么流畅（对齐 match-agent 的 _polish_suggestions 范式）。
"""
from __future__ import annotations

import logging
from typing import Optional

from pydantic import BaseModel, Field

from .. import llm
from .capability import (
    DIMENSION_NAMES,
    classify_dimension,
    dimension_gap_kind,
    infer_evidence_level,
    infer_required_level,
)
from .compliance import (
    DISCLAIMER_TEXT,
    EVIDENCE_MIN_LENGTH,
    extract_evidence_ref,
    verify_evidence_ref,
)

logger = logging.getLogger(__name__)


# ── 输出模型 ──────────────────────────────────────────────────────

class GapAttribution(BaseModel):
    """
    单个 gap 的归因结果。

    对应计划书 §4.2 的归因输出结构。
    """

    gap_ref: str = ""
    gap_type: str = ""
    gap_content: str = ""
    severity: int = 0

    dimension: str = ""
    dimension_name: str = ""
    required_level: int = 1
    evidence_level: int = 0
    gap_kind: str = ""

    explanation: str = ""
    evidence_ref: str = ""

    # 该条归因是否因为引用校验失败而被丢弃了文案（规则结论仍然保留）
    llm_used: bool = False


class CapabilityCoverage(BaseModel):
    """一个能力维度的覆盖情况（HR 视图 §② 用）。"""

    dimension: str = ""
    dimension_name: str = ""
    required_level: int = 1
    evidence_level: int = 0
    gap_refs: list[str] = Field(default_factory=list)


# ── LLM 文案（只写文案，不碰判定）─────────────────────────────────

_ATTRIBUTION_SYSTEM_PROMPT = (
    "你是资深招聘顾问，正在分析一份简历相对于岗位要求的证据缺口。\n"
    "对给定的每一条缺口，用 40-80 字说明：\n"
    "1) 这个缺口背后的真正能力要求是什么；\n"
    "2) 简历现有内容与该要求差在哪。\n"
    "只描述能力差距本身，不要给出录用、淘汰、是否胜任一类的结论。\n"
    "如果简历里有能说明现状的原文，把原文片段用「」包起来附在末尾。\n"
    "严格按 JSON 输出，不要任何解释性文字。"
)

_ATTRIBUTION_SCHEMA = {
    "type": "object",
    "properties": {
        "explanations": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "gap_ref": {"type": "string"},
                    "explanation": {"type": "string"},
                    "evidence_ref": {"type": "string"},
                },
                "required": ["gap_ref", "explanation"],
                "additionalProperties": False,
            },
        }
    },
    "required": ["explanations"],
    "additionalProperties": False,
}


def _build_attribution_prompt(
    gaps: list[dict],
    jd_text: str,
    resume_text: str,
) -> str:
    """
    构造 prompt。

    **只喂 gap 涉及的片段**，不喂简历全文 —— 降低幻觉与成本
    （计划书 §5.3 的 Prompt 设计原则 1）。
    """
    lines = []
    for gap in gaps:
        lines.append(
            f"- {gap['gap_ref']}（{gap['gap_type']}，严重度 {gap['severity']}）："
            f"{gap['gap_content']}"
        )

    jd_excerpt = (jd_text or "")[:1500]
    resume_excerpt = (resume_text or "")[:1500]

    return (
        f"岗位要求（节选）：\n{jd_excerpt}\n\n"
        f"候选人简历（节选）：\n{resume_excerpt}\n\n"
        f"需要分析的证据缺口：\n" + "\n".join(lines) + "\n\n"
        f"请为每一条缺口输出 explanation，gap_ref 原样填回。"
    )


def _fallback_explanation(gap: dict, dimension: str, required: int, evidence: int) -> str:
    """
    LLM 不可用时的确定性文案。

    不追求文采，但信息完整 —— 与规则结论一致，可审计。
    """
    dim_name = DIMENSION_NAMES.get(dimension, dimension)
    if evidence <= 0:
        return (
            f"该岗位要求{dim_name}方向的能力（层级 {required}），"
            f"简历中未见该方向的直接证据；简历未写明不等于不具备，建议在面试中针对性提问确认。"
        )
    if evidence < required:
        return (
            f"该岗位要求{dim_name}方向的能力（层级 {required}），"
            f"简历体现约在层级 {evidence}，属于相邻经验；"
            f"简历未写明不等于不具备，建议在面试中确认深度。"
        )
    return (
        f"该岗位要求{dim_name}方向的能力（层级 {required}），"
        f"简历体现层级 {evidence}，该条缺口的描述与已有证据存在偏差，建议在面试中核对具体场景。"
    )


# ── 主入口 ────────────────────────────────────────────────────────

async def attribute_gaps(
    gaps: list[dict],
    jd_text: str = "",
    resume_text: str = "",
    jd_skills: Optional[list[str]] = None,
) -> list[GapAttribution]:
    """
    把 L1 的 gap 列表归因到能力维度。

    :param gaps: [{"gap_ref","gap_type","gap_content","severity"}...]
    :param jd_text: JD 全文（用于推断要求层级）
    :param resume_text: 简历全文（用于推断已有证据层级）
    :param jd_skills: JD 硬技能（供文案参考）

    返回的每条归因里，dimension / required_level / evidence_level / gap_kind
    **由规则决定**，同输入必然同输出。
    """
    if not gaps:
        return []

    # ── 1. 规则层：分类与分级 ──
    results: list[GapAttribution] = []
    for gap in gaps:
        dimension, _detail = classify_dimension(
            gap.get("gap_content", ""),
            gap.get("gap_type", ""),
            jd_text,
            resume_text,
        )
        required = infer_required_level(jd_text, gap.get("gap_content", ""))
        evidence = infer_evidence_level(resume_text, dimension)
        results.append(GapAttribution(
            gap_ref=gap.get("gap_ref", ""),
            gap_type=gap.get("gap_type", ""),
            gap_content=gap.get("gap_content", ""),
            severity=int(gap.get("severity") or 0),
            dimension=dimension,
            dimension_name=DIMENSION_NAMES.get(dimension, dimension),
            required_level=required,
            evidence_level=evidence,
            gap_kind=dimension_gap_kind(required, evidence),
            explanation=_fallback_explanation(gap, dimension, required, evidence),
        ))

    # ── 2. LLM 层：只写文案 ──
    llm_map = await _enrich_with_llm(results, jd_text, resume_text, jd_skills)

    for item in results:
        enriched = llm_map.get(item.gap_ref)
        if not enriched:
            continue

        explanation = (enriched.get("explanation") or "").strip()
        if not explanation:
            continue

        # R3：引用必须能在原文里定位，否则丢弃这段文案（保留规则结论）
        raw_ref = (enriched.get("evidence_ref") or "").strip()
        ref = extract_evidence_ref(raw_ref) or raw_ref
        if ref:
            verified = verify_evidence_ref(ref, resume_text, EVIDENCE_MIN_LENGTH)
            if not verified:
                logger.info(
                    "归因引用校验失败，丢弃该条文案（gap_ref=%s）", item.gap_ref
                )
                continue
            item.evidence_ref = ref
        else:
            # 没有引用也可以保留文案，只是不带证据引用
            pass

        item.explanation = explanation
        item.llm_used = True

    return results


async def _enrich_with_llm(
    results: list[GapAttribution],
    jd_text: str,
    resume_text: str,
    jd_skills: Optional[list[str]],
) -> dict[str, dict]:
    """
    调用 LLM 生成文案。失败返回空 dict（调用方保留规则文案）。

    绝不抛异常 —— LLM 挂了不影响规则结论（M1 的降级要求）。
    """
    if not llm.llm_enabled() or not results:
        return {}

    gaps = [
        {
            "gap_ref": item.gap_ref,
            "gap_type": item.gap_type,
            "gap_content": item.gap_content,
            "severity": item.severity,
        }
        for item in results
    ]

    prompt = _build_attribution_prompt(gaps, jd_text, resume_text)
    if jd_skills:
        prompt += f"\n\nJD 硬技能：{'、'.join(jd_skills[:12])}"

    try:
        payload = await llm.complete_json(
            _ATTRIBUTION_SYSTEM_PROMPT,
            prompt,
            _ATTRIBUTION_SCHEMA,
        )
    except Exception as exc:  # LLM 层永不冒泡
        logger.warning("归因文案生成失败，保留规则文案: %s", exc)
        return {}

    if not payload:
        return {}

    mapping: dict[str, dict] = {}
    for item in payload.get("explanations") or []:
        if isinstance(item, dict) and item.get("gap_ref"):
            mapping[str(item["gap_ref"])] = item
    return mapping


def build_capability_coverage(
    attributions: list[GapAttribution],
) -> list[CapabilityCoverage]:
    """
    汇总成「能力维度覆盖」列表（HR 视图 §②）。

    同一维度可能对应多个 gap，合并它们的 gap_ref；
    required_level 取最高，evidence_level 取最高（该维度上最好的证据）。
    """
    buckets: dict[str, CapabilityCoverage] = {}
    for item in attributions:
        coverage = buckets.get(item.dimension)
        if coverage is None:
            coverage = CapabilityCoverage(
                dimension=item.dimension,
                dimension_name=item.dimension_name,
                required_level=item.required_level,
                evidence_level=item.evidence_level,
            )
            buckets[item.dimension] = coverage
        coverage.required_level = max(coverage.required_level, item.required_level)
        coverage.evidence_level = max(coverage.evidence_level, item.evidence_level)
        if item.gap_ref:
            coverage.gap_refs.append(item.gap_ref)

    # 按要求层级倒序：要求越高的越先看
    return sorted(
        buckets.values(),
        key=lambda c: (c.required_level, c.dimension),
        reverse=True,
    )
