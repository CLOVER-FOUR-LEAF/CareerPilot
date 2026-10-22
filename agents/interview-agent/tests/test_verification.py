"""
验证计划生成（L3）与覆盖判定（F3）的单元测试。

计划书 §7 的验收标准：
M3:
1. 每个 severity ≥ 4 的 gap 都至少产出一个验证问题（覆盖率 100%）
2. 禁用问法 linter 通过：全量报告中不含「会不会 X」「熟悉 X」「了解 X」句式
3. 每个问题都有 intent + ≥1 个 probes + 覆盖全部三个 verdict 的 signals
4. 报告中每个 evidence_ref 都能在原文中定位
M4:
1. 判定输出只可能出现 demonstrated / adjacent / not_evidenced 三种值
2. 全量输出禁用词扫描 0 命中
3. 回答含糊（"还行吧""就是用了一下"）→ not_evidenced 并自动触发追问
4. 回答命中 signals 特征词 → demonstrated 且 confidence ≥ 0.7
"""
import asyncio
import sys
from pathlib import Path

import pytest

_AGENTS_ROOT = Path(__file__).resolve().parents[2]
if str(_AGENTS_ROOT) not in sys.path:
    sys.path.insert(0, str(_AGENTS_ROOT))
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.hr.attribution import attribute_gaps  # noqa: E402
from app.hr.compliance import (  # noqa: E402
    ALLOWED_VERDICTS,
    find_forbidden_terms,
    verify_evidence_ref,
)
from app.hr.verdict import judge_answer  # noqa: E402
from app.hr.verification import (  # noqa: E402
    build_verification_plan,
    lint_question,
    validate_plan,
)

JD_TEXT = (
    "高级后端工程师（Kubernetes / CI/CD 方向）\n"
    "负责核心交易系统的架构设计与服务拆分，主导 CI/CD 流水线建设与生产环境发布。\n"
    "要求 5 年以上后端开发经验，熟悉分布式系统与微服务治理。\n"
)

RESUME_TEXT = (
    "2019-2023 某电商公司 后端工程师\n"
    "负责订单系统后端开发，参与微服务拆分，主导 CI/CD 流水线建设与日常发布。\n"
    "负责 Redis 缓存设计与 MySQL 索引优化，支撑日订单百万级。\n"
)

GAPS = [
    {"gap_ref": "gap#1", "gap_type": "missing_skill", "gap_content": "Kubernetes",
     "severity": 5},
    {"gap_ref": "gap#2", "gap_type": "missing_skill", "gap_content": "Kafka",
     "severity": 4},
    {"gap_ref": "gap#3", "gap_type": "weak_metric", "gap_content": "简历缺少可量化的业务指标",
     "severity": 2},
]


def _plan():
    attributions = asyncio.run(attribute_gaps(GAPS, JD_TEXT, RESUME_TEXT))
    return asyncio.run(build_verification_plan(attributions, JD_TEXT, RESUME_TEXT))


# ── 提问规则 linter ──────────────────────────────────────────────

@pytest.mark.parametrize("bad", [
    "你会 Kubernetes 吗？",
    "熟悉分布式系统吗？",
    "了解 Redis 索引优化吗？",
    "掌握微服务拆分吗？",
    "你没用过 K8s 对吧？那你怎么做部署的？",
    "JD 要求 Kubernetes，你简历里没有，怎么回事？",
    "你的简历缺少可量化指标，能说说吗？",
])
def test_linter_rejects_non_compliant_questions(bad):
    assert lint_question(bad), f"应被判为不合规：{bad}"


@pytest.mark.parametrize("good", [
    "你们的部署流程是怎样的？自己搭集群还是用托管服务？",
    "CI/CD 在你们那边承载了多少量级？出过什么问题？",
    "你们当初是怎么决定这么做的？微服务拆分承担什么角色？",
    "这个业务里最难的点在哪？和支付风控怎么结合的？",
])
def test_linter_accepts_compliant_questions(good):
    assert lint_question(good) == [], f"应被判为合规：{good}"


def test_linter_requires_scene_anchor():
    """未锚定场景的问题要判不合规。"""
    assert lint_question("Kubernetes")
    assert lint_question("谈谈 Kubernetes")


# ── 验证计划生成 ─────────────────────────────────────────────────

def test_plan_covers_all_severe_gaps():
    """M3 验收 1：severity ≥ 4 的 gap 都要有验证问题。"""
    attributions = asyncio.run(attribute_gaps(GAPS, JD_TEXT, RESUME_TEXT))
    plan = asyncio.run(build_verification_plan(attributions, JD_TEXT, RESUME_TEXT))

    severe_refs = {a.gap_ref for a in attributions if a.severity >= 4}
    covered: set[str] = set()
    for q in plan:
        covered |= {r for r in q.gap_ref.split(",") if r}
    assert severe_refs <= covered, f"severity≥4 的 gap 未被覆盖：{severe_refs - covered}"


def test_plan_questions_pass_linter():
    """M3 验收 2：全量报告不含违规问法。"""
    plan = _plan()
    assert plan
    for q in plan:
        assert lint_question(q.question) == [], f"{q.id} 不合规：{q.question}"


def test_plan_questions_have_intent_probes_signals():
    """M3 验收 3：每个问题都有 intent + ≥1 probes + 三档 signals。"""
    plan = _plan()
    for q in plan:
        assert q.intent
        assert len(q.probes) >= 1
        assert {s.verdict for s in q.signals} == set(ALLOWED_VERDICTS)


def test_plan_is_valid():
    """整体校验不应报出问题。"""
    assert validate_plan(_plan()) == []


def test_plan_is_deterministic():
    """同一输入跑两次结果完全一致（LLM 未配置时）。"""
    a = _plan()
    b = _plan()
    assert [(q.id, q.question, q.intent) for q in a] == \
           [(q.id, q.question, q.intent) for q in b]


def test_plan_respects_max_questions():
    attributions = asyncio.run(attribute_gaps(GAPS, JD_TEXT, RESUME_TEXT))
    plan = asyncio.run(build_verification_plan(attributions, JD_TEXT, RESUME_TEXT, max_questions=1))
    assert len(plan) <= 1


def test_plan_contains_no_forbidden_terms():
    """M3/M4 验收 2：输出禁用词 0 命中。"""
    plan = _plan()
    for q in plan:
        blob = " ".join([q.question, q.intent] + [p.question for p in q.probes]
                        + [s.evidence for s in q.signals])
        assert not find_forbidden_terms(blob), f"{q.id} 含禁用词"


# ── 覆盖判定（F3）────────────────────────────────────────────────

def _question():
    return _plan()[0]


def test_verdict_only_three_values():
    """M4 验收 1：判定值只可能是三种。"""
    q = _question()
    for answer in ["我们主导了集群的搭建与扩容排障", "就是用了一下", "还行吧", ""]:
        verdict = asyncio.run(judge_answer(q, answer))
        assert verdict.coverage in ALLOWED_VERDICTS


def test_verdict_demonstrated_on_specific_answer():
    """M4 验收 4：命中特征词 → demonstrated 且 confidence ≥ 0.7。"""
    q = _question()
    answer = "我们负责自己搭的集群，平时会做版本升级和节点排障，也做过扩容演练，发布走灰度流水线"
    verdict = asyncio.run(judge_answer(q, answer))
    assert verdict.coverage == "demonstrated"
    assert verdict.confidence >= 0.7
    assert verdict.evidence_ref
    assert verify_evidence_ref(verdict.evidence_ref, answer)


def test_verdict_not_evidenced_on_vague_answer():
    """M4 验收 3：含糊回答 → not_evidenced 并触发追问。"""
    q = _question()
    verdict = asyncio.run(judge_answer(q, "还行吧，就是用了一下"))
    assert verdict.coverage == "not_evidenced"
    assert verdict.should_probe is True
    assert verdict.suggested_probes


def test_verdict_adjacent_on_usage_level_answer():
    q = _question()
    answer = "我们用的是云厂商托管的集群服务，我主要是通过控制台配置，拉过一些 yaml"
    verdict = asyncio.run(judge_answer(q, answer))
    assert verdict.coverage in ("adjacent", "not_evidenced")
    assert verdict.coverage in ALLOWED_VERDICTS


def test_verdict_reason_has_no_forbidden_terms():
    """M4 验收 2：判定理由不得含禁用词。"""
    q = _question()
    for answer in ["我们主导了集群搭建与排障", "就是用了一下", "还行吧"]:
        verdict = asyncio.run(judge_answer(q, answer))
        assert not find_forbidden_terms(verdict.reason)
        assert not find_forbidden_terms(verdict.evidence_ref)


def test_verdict_evidence_ref_locatable():
    """M4 验收 5：evidence_ref 能在回答原文中定位。"""
    q = _question()
    answer = "我们负责自建集群的搭建、版本升级和节点排障，发布走灰度"
    verdict = asyncio.run(judge_answer(q, answer))
    if verdict.evidence_ref:
        assert verify_evidence_ref(verdict.evidence_ref, answer)


def test_verdict_is_deterministic_without_llm():
    q = _question()
    answer = "我们主导了集群搭建与扩容排障"
    a = asyncio.run(judge_answer(q, answer))
    b = asyncio.run(judge_answer(q, answer))
    assert (a.coverage, a.confidence) == (b.coverage, b.confidence)
