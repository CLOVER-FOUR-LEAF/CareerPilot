"""
HR 侧能力维度分类（计划书 §4.2）。

设计原则（约束 C3）
------------------
**确定性内核不交给 LLM**：dimension 与 required_level 永远由规则决定，
LLM 只负责写 explanation 文案。这样同一份输入跑 3 次，结论完全一致
（M2 验收 4），且每条结论都能解释「为什么是这个维度」。

能力维度 taxonomy 刻意**不含人口学特征**（ADR-005）：
没有性别、年龄、毕业院校、专业、籍贯这类维度 —— 它们与能力无因果关系，
却与统计歧视高度相关。学历门槛仍按 JD 硬性条件处理（那是岗位要求，
不是对候选人的能力判断）。
"""
from __future__ import annotations

import re
from typing import Optional

# ── 能力维度 taxonomy ────────────────────────────────────────────
#
# signals 是该维度在 JD / 简历文本中的**信号词**。
# 归因时用这些词做关键词匹配，命中越多越可能属于该维度。
CAPABILITY_DIMENSIONS: dict[str, dict] = {
    "delivery": {
        "name": "交付与工程实践",
        "signals": ("ci/cd", "cicd", "部署", "发布", "灰度", "流水线", "回滚",
                    "构建", "交付", "上线", "运维", "发布流程", "release",
                    "kubernetes", "k8s", "集群", "容器", "镜像", "helm"),
    },
    "scale": {
        "name": "规模与稳定性",
        "signals": ("qps", "并发", "容量", "性能", "延迟", "故障", "高可用",
                    "吞吐", "峰值", "扩缩容", "sla", "容灾", "稳定性", "压测"),
    },
    "architecture": {
        "name": "架构与治理",
        "signals": ("微服务", "服务治理", "拆分", "边界", "分布式", "限流",
                    "熔断", "架构", "重构", "分层", "领域驱动", "ddd", "中台"),
    },
    "data": {
        "name": "数据与存储",
        "signals": ("mysql", "redis", "elasticsearch", "es", "索引", "分库",
                    "分表", "缓存", "一致性", "数据建模", "数仓", "存储", "数据库"),
    },
    "domain": {
        "name": "业务领域理解",
        "signals": ("交易", "支付", "风控", "电商", "教育", "医疗", "招聘",
                    "金融", "零售", "物流", "供应链", "营销", "增长"),
    },
    "collaboration": {
        "name": "协作与影响力",
        "signals": ("主导", "跨部门", "带人", "推动", "协同", "汇报", "推动落地",
                    "团队管理", "技术方案评审", "跨团队", "对齐"),
    },
}

# 维度键 → 中文名，输出时用
DIMENSION_NAMES: dict[str, str] = {
    key: value["name"] for key, value in CAPABILITY_DIMENSIONS.items()
}

# 兜底维度：信号词一个都没命中时归到这里，而不是随便挑一个
DEFAULT_DIMENSION = "delivery"

# ── 要求层级 ─────────────────────────────────────────────────────
#
# required_level：1 了解 / 2 熟悉 / 3 能主导
# 由 JD 的措辞强度决定 —— 「主导」「独立」比「参与」「使用」要求更高。

LEVEL_3_MARKERS: tuple[str, ...] = (
    "主导", "独立", "负责", "搭建", "从0到1", "从 0 到 1", "架构", "设计并实现",
    "制定", "牵头", "推动", "落地", "优化", "重构", "解决", "攻坚",
)

LEVEL_2_MARKERS: tuple[str, ...] = (
    "熟练", "深入", "精通", "掌握", "理解", "运用",
    "优化", "改进", "排查", "定位", "分析", "设计",
)

LEVEL_1_MARKERS: tuple[str, ...] = (
    "了解", "参与", "使用", "协助", "配合", "接触", "知道", "有过",
    "辅助", "跟进", "熟悉",
)

# gap_type → 该类 gap 更可能指向的维度。
# 权重体现在匹配时的打分上：命中 gap_type 专属维度会加分。
GAP_TYPE_DIMENSION_HINTS: dict[str, tuple[str, ...]] = {
    "missing_skill": ("delivery", "scale", "architecture", "data"),
    "weak_metric": ("scale", "delivery"),
    "missing_cert": ("domain",),
}


def normalize(text: str) -> str:
    """统一成小写并压缩空白，让信号词匹配不受排版影响。"""
    if not text:
        return ""
    return re.sub(r"\s+", " ", text.lower())


def _count_signals(text: str, signals: tuple[str, ...]) -> int:
    """统计命中的信号词数量。"""
    lowered = normalize(text)
    return sum(1 for signal in signals if signal in lowered)


def classify_dimension(
    gap_content: str,
    gap_type: str = "",
    jd_text: str = "",
    resume_text: str = "",
) -> tuple[str, dict]:
    """
    把一个 gap 归类到能力维度。

    **完全由规则决定**（C3）：同样的输入必然得到同样的维度。

    判定顺序：
    1. gap 内容里的信号词命中 —— 权重最高（gap 说的是缺什么）
    2. JD 上下文里的信号词 —— 次之（岗位要求什么）
    3. gap_type 的类型倾向 —— 再次
    4. 都没有 → 兜底维度

    :return: (dimension_key, 判定明细)
    """
    scores: dict[str, int] = {key: 0 for key in CAPABILITY_DIMENSIONS}
    detail: dict[str, int] = {}
    # 只统计「内容信号」的得分。gap_type 的类型倾向是弱信号，
    # 单独靠它命中不算真的归类成功（否则任何 gap 都会落到某个维度上，
    # 兜底逻辑就永远不会触发）。
    content_score: dict[str, int] = {key: 0 for key in CAPABILITY_DIMENSIONS}

    # ── 1. gap 内容的信号词 ──
    for key, meta in CAPABILITY_DIMENSIONS.items():
        hits = _count_signals(gap_content, meta["signals"])
        if hits:
            scores[key] += hits * 3
            content_score[key] += hits * 3
            detail[key] = detail.get(key, 0) + hits * 3

    # ── 2. JD 上下文的信号词 ──
    for key, meta in CAPABILITY_DIMENSIONS.items():
        hits = _count_signals(jd_text, meta["signals"])
        if hits:
            scores[key] += hits
            content_score[key] += hits
            detail[key] = detail.get(key, 0) + hits

    # ── 3. 简历里已体现的信号词 ──
    #    已经在简历里大量出现的能力维度，通常不是「缺」的那一项，
    #    降权避免把最有把握的维度误判成缺口维度。
    for key, meta in CAPABILITY_DIMENSIONS.items():
        hits = _count_signals(resume_text, meta["signals"])
        if hits:
            scores[key] -= hits
            content_score[key] -= hits
            detail[key] = detail.get(key, 0) - hits

    # ── 4. gap_type 的类型倾向（弱信号）──
    for key in GAP_TYPE_DIMENSION_HINTS.get(gap_type, ()):  # type: ignore[arg-type]
        scores[key] += 1
        detail[key] = detail.get(key, 0) + 1

    best = max(scores, key=lambda k: scores[k])

    # 内容信号一个都没命中 → 走兜底，不硬凑维度
    if content_score[best] <= 0:
        return DEFAULT_DIMENSION, {"scores": scores, "content_scores": content_score,
                                  "fallback": True}

    return best, {"scores": scores, "content_scores": content_score, "fallback": False}


def infer_required_level(jd_text: str, gap_content: str = "") -> int:
    """
    从 JD 措辞推断该能力的要求层级（1-3）。

    **完全由规则决定**（C3）。

    判定顺序：先看 gap 相关的 JD 片段，再看整篇 JD。
    「主导/独立/负责」= 3，「熟练/深入/优化」= 2，「了解/参与/使用」= 1。
    """
    text = jd_text or ""

    # 优先用包含 gap 关键词的句子，避免整篇 JD 里别处的强措辞误导
    focused = _focus_sentences(text, gap_content)

    for level, markers in ((3, LEVEL_3_MARKERS), (2, LEVEL_2_MARKERS), (1, LEVEL_1_MARKERS)):
        if any(marker in focused for marker in markers):
            return level

    # 聚焦片段没结论，再看整篇
    for level, markers in ((3, LEVEL_3_MARKERS), (2, LEVEL_2_MARKERS), (1, LEVEL_1_MARKERS)):
        if any(marker in text for marker in markers):
            return level

    return 2


def _focus_sentences(text: str, keyword: str) -> str:
    """
    从 JD 里挑出与 gap 关键词同句的片段。

    挑不到就返回空串（调用方会退回整篇 JD）。
    """
    if not keyword or not text:
        return ""

    # gap_content 可能是「缺少 Kubernetes 相关经验」这种整句，
    # 取其中最长的一个词作为锚点
    token = _longest_token(keyword)
    if not token:
        return ""

    picked: list[str] = []
    for sentence in re.split(r"[。；;\n,，、]", text):
        if token.lower() in sentence.lower():
            picked.append(sentence)
    return " ".join(picked)


def _longest_token(text: str) -> str:
    """取文本里最长的一个「像技能名」的片段。"""
    cleaned = re.sub(r"^(缺少|缺乏|没有|未体现|不满足|关于|该岗位要求)\s*", "", text.strip())
    parts = re.split(r"[\s：:，,。、（）()\[\]【】]+", cleaned)
    parts = [p for p in parts if p]
    if not parts:
        return ""
    return max(parts, key=len)


def infer_evidence_level(resume_text: str, dimension: str) -> int:
    """
    推断候选人在该维度上已体现的层级（0-3）。

    0 = 简历里看不到该维度的任何信号。

    **完全由规则决定**（C3）。
    """
    meta = CAPABILITY_DIMENSIONS.get(dimension)
    if not meta:
        return 0

    text = resume_text or ""
    if not text.strip():
        return 0

    hits = _count_signals(text, meta["signals"])
    if hits == 0:
        return 0

    # 信号越多层级越高，但只看信号数会高估 —— 再用措辞强度校准
    base = 1 if hits <= 1 else (2 if hits <= 3 else 3)

    # 简历里出现强措辞（主导/独立）说明不止是「用过」
    if any(marker in text for marker in ("主导", "独立", "从0到1", "从 0 到 1", "牵头", "负责")):
        return min(3, base + 1)

    return base


def dimension_gap_kind(required_level: int, evidence_level: int) -> str:
    """
    差距类型。

    - no_evidence      简历里完全看不到该维度的证据
    - adjacent         有相邻经验但层级不够
    - partial          层级接近但仍低于要求
    """
    if evidence_level <= 0:
        return "no_evidence"
    if evidence_level < required_level - 1:
        return "adjacent"
    return "partial"


def build_capability_model(
    jd_text: str,
    resume_text: str = "",
    max_dimensions: int = 6,
) -> list[dict]:
    """
    从 JD 推出岗位要求的能力模型（F4 的 capability_model）。

    **完全由规则决定**（C3）：命中信号词的维度才会出现，
    按信号强度排序，最多取 max_dimensions 个。
    """
    scores: dict[str, int] = {}
    for key, meta in CAPABILITY_DIMENSIONS.items():
        hits = _count_signals(jd_text, meta["signals"])
        if hits > 0:
            scores[key] = hits

    model: list[dict] = []
    for key, hits in sorted(scores.items(), key=lambda kv: kv[1], reverse=True)[:max_dimensions]:
        required = infer_required_level(jd_text, "")
        evidence = infer_evidence_level(resume_text, key)
        entry = {
            "dimension": key,
            "dimension_name": DIMENSION_NAMES[key],
            "required_level": required,
            "evidence_level": evidence,
        }
        model.append(entry)

    return model
