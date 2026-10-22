"""
归因层与 JD 校准的单元测试（计划书 §7 M2 的验收标准）。

M2 验收：
1. 归因输出 dimension / required_level / evidence_level 差值明确，
   evidence_ref 能在简历原文中定位
2. 引用校验函数对伪造的 ref 返回 False
3. jd_calibrate 能识别出至少 2 条 vague_phrases 和 1 条 over_filtering_signals
4. dimension / required_level 由规则决定 —— 同输入跑 3 次结果完全一致
"""
import asyncio
import sys
from pathlib import Path

import pytest

# agents/ 目录挂载，让 shared 包可导入
_AGENTS_ROOT = Path(__file__).resolve().parents[2]
if str(_AGENTS_ROOT) not in sys.path:
    sys.path.insert(0, str(_AGENTS_ROOT))
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.hr.attribution import attribute_gaps, build_capability_coverage  # noqa: E402
from app.hr.calibration import calibrate_jd  # noqa: E402
from app.hr.capability import (  # noqa: E402
    build_capability_model,
    classify_dimension,
    dimension_gap_kind,
    infer_evidence_level,
    infer_required_level,
)
from app.hr.compliance import (  # noqa: E402
    ALLOWED_VERDICTS,
    assert_allowed_verdict,
    extract_evidence_ref,
    find_forbidden_terms,
    verify_evidence_ref,
)


JD_TEXT = (
    "高级后端工程师（Kubernetes / CI/CD 方向）\n"
    "负责公司核心交易系统的架构设计与服务拆分，主导 CI/CD 流水线建设与生产环境发布。\n"
    "要求 5 年以上后端开发经验，本科及以上学历，熟悉分布式系统与微服务治理。\n"
    "有高并发、高可用系统设计经验者优先，熟悉 Redis 与 MySQL 索引优化。\n"
    "具备良好的沟通能力与团队协作精神，PMP 认证者优先。\n"
)

RESUME_TEXT = (
    "2019-2023 某电商公司 高级后端工程师\n"
    "负责订单系统后端开发，参与微服务拆分，主导 CI/CD 流水线建设与日常发布。\n"
    "负责 Redis 缓存设计与 MySQL 索引优化，支撑日订单百万级。\n"
    "2023-至今 某支付公司 后端专家\n"
    "负责支付网关服务设计，独立完成限流熔断方案，参与服务治理。\n"
    "跨部门推动多个需求落地，负责技术方案评审与团队协作。\n"
)

GAPS = [
    {"gap_ref": "gap#1", "gap_type": "missing_skill", "gap_content": "Kubernetes",
     "severity": 4},
    {"gap_ref": "gap#2", "gap_type": "missing_skill", "gap_content": "Kafka",
     "severity": 4},
    {"gap_ref": "gap#3", "gap_type": "weak_metric", "gap_content": "简历缺少可量化的业务指标",
     "severity": 2},
]


# ── 维度归类（规则）──────────────────────────────────────────────

def test_classify_dimension_finds_delivery_for_ci_cd():
    dimension, detail = classify_dimension("CI/CD 流水线建设", "missing_skill", JD_TEXT, RESUME_TEXT)
    assert dimension == "delivery"
    assert detail["fallback"] is False


def test_classify_dimension_finds_architecture_for_microservices():
    dimension, _ = classify_dimension("微服务拆分", "missing_skill", JD_TEXT, RESUME_TEXT)
    assert dimension == "architecture"


def test_classify_dimension_falls_back_when_no_signal():
    dimension, detail = classify_dimension("某个完全无关的内容", "missing_skill", "", "")
    assert dimension == "delivery"
    assert detail["fallback"] is True


def test_classify_dimension_is_deterministic():
    """M2 验收 4：同输入跑多次结果一致。"""
    results = [classify_dimension("Kubernetes 集群运维", "missing_skill", JD_TEXT, RESUME_TEXT)[0]
               for _ in range(3)]
    assert len(set(results)) == 1


# ── 要求层级推断（规则）──────────────────────────────────────────

def test_infer_required_level_from_strong_wording():
    # 「主导 CI/CD 流水线建设」→ 3
    level = infer_required_level(JD_TEXT, "CI/CD 流水线建设")
    assert level == 3


def test_infer_required_level_from_weak_wording():
    level = infer_required_level("熟悉分布式系统", "分布式")
    assert level == 1


def test_infer_required_level_is_deterministic():
    levels = {infer_required_level(JD_TEXT, "Kubernetes") for _ in range(3)}
    assert len(levels) == 1


# ── 证据层级推断（规则）──────────────────────────────────────────

def test_infer_evidence_level_zero_when_absent():
    assert infer_evidence_level("简历里只有前端页面开发", "delivery") == 0


def test_infer_evidence_level_positive_when_present():
    assert infer_evidence_level(RESUME_TEXT, "delivery") >= 1


def test_infer_evidence_level_is_deterministic():
    levels = {infer_evidence_level(RESUME_TEXT, "collaboration") for _ in range(3)}
    assert len(levels) == 1


# ── 差距类型 ─────────────────────────────────────────────────────

def test_gap_kind_variants():
    assert dimension_gap_kind(3, 0) == "no_evidence"
    assert dimension_gap_kind(3, 1) == "adjacent"
    assert dimension_gap_kind(3, 2) == "partial"


# ── 能力模型 ─────────────────────────────────────────────────────

def test_capability_model_from_jd():
    model = build_capability_model(JD_TEXT, RESUME_TEXT)
    assert model, "JD 里应能识别出至少一个能力维度"
    dimensions = {m["dimension"] for m in model}
    assert "delivery" in dimensions
    for entry in model:
        assert entry["required_level"] >= 1
        assert entry["evidence_level"] >= 0
        assert entry["dimension_name"]


def test_capability_model_is_deterministic():
    a = build_capability_model(JD_TEXT, RESUME_TEXT)
    b = build_capability_model(JD_TEXT, RESUME_TEXT)
    assert a == b


# ── 归因主流程 ───────────────────────────────────────────────────

def test_attribute_gaps_produces_rule_driven_conclusions():
    results = asyncio.run(attribute_gaps(GAPS, JD_TEXT, RESUME_TEXT, ["Kubernetes", "Kafka"]))

    assert len(results) == 3
    for item in results:
        assert item.dimension
        assert item.dimension_name
        assert 1 <= item.required_level <= 3
        assert item.evidence_level >= 0
        assert item.gap_kind
        assert item.explanation, "没有 LLM 时也必须有规则拼装的文案"


def test_attribute_gaps_deterministic_without_llm():
    """M2 验收 4：没有 LLM 时结论也完全一致。"""
    a = asyncio.run(attribute_gaps(GAPS, JD_TEXT, RESUME_TEXT))
    b = asyncio.run(attribute_gaps(GAPS, JD_TEXT, RESUME_TEXT))
    assert [(x.dimension, x.required_level, x.evidence_level, x.gap_kind)
            for x in a] == [(x.dimension, x.required_level, x.evidence_level, x.gap_kind)
                            for x in b]


def test_attribute_gaps_never_produces_forbidden_terms():
    results = asyncio.run(attribute_gaps(GAPS, JD_TEXT, RESUME_TEXT))
    for item in results:
        assert not find_forbidden_terms(item.explanation)


def test_capability_coverage_merges_same_dimension():
    results = asyncio.run(attribute_gaps(GAPS, JD_TEXT, RESUME_TEXT))
    coverage = build_capability_coverage(results)
    assert coverage
    # 按要求层级倒序
    levels = [c.required_level for c in coverage]
    assert levels == sorted(levels, reverse=True)


# ── 引用校验（R3）─────────────────────────────────────────────────

def test_verify_evidence_ref_accepts_real_quote():
    assert verify_evidence_ref("主导 CI/CD 流水线建设与日常发布", RESUME_TEXT)


def test_verify_evidence_ref_rejects_fabricated_quote():
    """M2 验收 2：伪造的引用返回 False。"""
    assert not verify_evidence_ref("主导了跨国并购整合与海外市场战略", RESUME_TEXT)


def test_verify_evidence_ref_rejects_too_short():
    assert not verify_evidence_ref("CI", RESUME_TEXT)


def test_verify_evidence_ref_tolerates_punctuation_drift():
    """容忍标点微调，但不容忍改写内容。"""
    assert verify_evidence_ref("主导 CI/CD 流水线建设与日常发布。", RESUME_TEXT)
    assert not verify_evidence_ref("主导 CI/CD 流水线建设与日常维护", RESUME_TEXT)


def test_extract_evidence_ref_from_quoted_text():
    assert extract_evidence_ref("简历写了「主导 CI/CD 流水线建设」") == "主导 CI/CD 流水线建设"
    assert extract_evidence_ref("没有引用") is None


# ── 判定枚举（R1）────────────────────────────────────────────────

def test_only_three_verdicts_allowed():
    assert ALLOWED_VERDICTS == ("demonstrated", "adjacent", "not_evidenced")
    assert_allowed_verdict("adjacent")
    with pytest.raises(ValueError):
        assert_allowed_verdict("通过")
    with pytest.raises(ValueError):
        assert_allowed_verdict("淘汰")


def test_forbidden_terms_detection():
    assert find_forbidden_terms("建议录用该候选人")
    assert find_forbidden_terms("这个岗位不适合他")
    assert find_forbidden_terms("结果为 fail")
    assert not find_forbidden_terms("该维度有相邻经验，但本次未获取到证据")
    # 英文按词边界，"failed" 不该命中 "fail"
    assert not find_forbidden_terms("the request failed")


# ── JD 校准（F4）─────────────────────────────────────────────────

def test_calibrate_detects_vague_phrases():
    """M2 验收 3：至少 2 条 vague_phrases。"""
    result = asyncio.run(calibrate_jd(JD_TEXT, "高级后端工程师"))
    assert len(result.vague_phrases) >= 2
    for phrase in result.vague_phrases:
        assert phrase.phrase
        assert phrase.why_vague
        assert phrase.suggestion


def test_calibrate_detects_over_filtering():
    """M2 验收 3：至少 1 条 over_filtering_signals。"""
    result = asyncio.run(calibrate_jd(JD_TEXT, "高级后端工程师"))
    assert len(result.over_filtering_signals) >= 1
    for signal in result.over_filtering_signals:
        assert signal.signal
        assert signal.risk


def test_calibrate_detects_hard_gates():
    result = asyncio.run(calibrate_jd(JD_TEXT))
    items = {g.item for g in result.hard_gates}
    assert any("本科" in item for item in items)
    assert any("年" in item for item in items)


def test_calibrate_capability_model_present():
    result = asyncio.run(calibrate_jd(JD_TEXT))
    assert result.capability_model
    assert result.disclaimer
    assert "最终判断由招聘方作出" in result.disclaimer


def test_calibrate_is_deterministic_without_llm():
    a = asyncio.run(calibrate_jd(JD_TEXT, "高级后端工程师"))
    b = asyncio.run(calibrate_jd(JD_TEXT, "高级后端工程师"))
    assert [v.phrase for v in a.vague_phrases] == [v.phrase for v in b.vague_phrases]
    assert [s.signal for s in a.over_filtering_signals] == [s.signal for s in b.over_filtering_signals]


def test_calibrate_never_produces_forbidden_terms():
    result = asyncio.run(calibrate_jd(JD_TEXT, "高级后端工程师"))
    blob = " ".join(
        [s.signal for s in result.over_filtering_signals]
        + [v.why_vague + v.suggestion for v in result.vague_phrases]
    )
    assert not find_forbidden_terms(blob)


def test_calibrate_empty_jd_returns_empty():
    result = asyncio.run(calibrate_jd(""))
    assert result.capability_model == []
    assert result.vague_phrases == []
