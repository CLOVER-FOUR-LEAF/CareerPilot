"""
HR 侧能力模块。

把同一套 AI 引擎翻转一个视角，服务招聘方（HR）——
不是做招聘平台，而是做招聘方的决策支持工具。

分层（计划书 §1.3）
------------------
- L1 证据对齐：事实差异（缺什么技能、缺多少年限）→ match-agent 的 gap_list
- L2 语义归因：这个 gap 背后真正缺的能力维度是什么 → capability / attribution
- L3 行为验证：用什么问题能把这个维度验出来 → verification / verdict

L1 在 match-agent 里，本包负责 L2 与 L3。
"""
from .calibration import calibrate_jd
from .capability import (
    CAPABILITY_DIMENSIONS,
    classify_dimension,
    infer_evidence_level,
    infer_required_level,
)
from .attribution import attribute_gaps, build_capability_coverage
from .compliance import (
    ALLOWED_VERDICTS,
    DISCLAIMER_TEXT,
    find_forbidden_terms,
    verify_evidence_ref,
)

__all__ = [
    "ALLOWED_VERDICTS",
    "CAPABILITY_DIMENSIONS",
    "DISCLAIMER_TEXT",
    "attribute_gaps",
    "build_capability_coverage",
    "calibrate_jd",
    "classify_dimension",
    "find_forbidden_terms",
    "infer_evidence_level",
    "infer_required_level",
    "verify_evidence_ref",
]
