"""
HR 侧合规约束（计划书 §3.3、§5.5、§5.6）。

三条硬约束必须落到**代码**里，不能只写在文档里：

R1 输出措辞
    所有输出字段的枚举值不得包含「录用/淘汰/不适合/不通过」。
    判定只用三值：demonstrated / adjacent / not_evidenced。

R2 人工决策
    报告结尾必须显式声明「本报告为辅助建议，最终判断由招聘方作出」，
    且该声明**由代码拼接**（不可由 LLM 生成，防止 LLM 漏写）。

R3 溯源
    每个结论携带 evidence_ref（原文片段），
    由代码验证该片段确实出现在输入文本中，验证不通过则丢弃该结论。

关于「not_evidenced ≠ 不适合」
--------------------------------
这是合规红线的具体体现：没拿到证据，只说明**这次提问没问到**，
不代表候选人不具备这个能力。措辞差别必须在这四处保持一致：
代码常量、prompt、UI 文案、文档。
"""
from __future__ import annotations

import re
from typing import Optional

# ── 判定枚举（R1）─────────────────────────────────────────────────

# 唯一允许的三个判定值。顺序即展示顺序（从强到弱）。
ALLOWED_VERDICTS: tuple[str, ...] = ("demonstrated", "adjacent", "not_evidenced")

# 中文展示名。UI 与文档必须用这套词，不能自己另造。
VERDICT_LABELS: dict[str, str] = {
    "demonstrated": "已体现",
    "adjacent": "有相邻经验",
    "not_evidenced": "本次未获取到证据",
}

VERDICT_DESCRIPTIONS: dict[str, str] = {
    "demonstrated": "有直接证据支持该能力达到要求层级",
    "adjacent": "有相邻经验，部分满足",
    "not_evidenced": "本次提问未获得证据（不等于不具备该能力）",
}

# ── 禁用词清单（R1）───────────────────────────────────────────────
#
# 命中即视为输出越界。检查会扫全量文本，命中就报错。
# 注意：这些词也用于 prompt 里提醒模型不要输出，所以词表本身要精确，
# 避免把「不通过」这类中性描述误杀 —— 判定值里根本不该出现这些词。
FORBIDDEN_TERMS: tuple[str, ...] = (
    "建议录用",
    "建议不录用",
    "建议淘汰",
    "淘汰",
    "不适合",
    "不合格",
    "通过筛选",
    "未通过筛选",
    "录用",
    "拒绝",
    "pass",
    "fail",
)

# 英文词要按词边界匹配，否则 "fail" 会命中 "failed" 这类无害词。
_FORBIDDEN_ASCII: tuple[str, ...] = ("pass", "fail")
_FORBIDDEN_CJK: tuple[str, ...] = tuple(
    term for term in FORBIDDEN_TERMS if term not in _FORBIDDEN_ASCII
)

_ASCII_PATTERN = re.compile(
    r"\b(" + "|".join(re.escape(t) for t in _FORBIDDEN_ASCII) + r")\b", re.IGNORECASE
)

# ── 免责声明（R2）─────────────────────────────────────────────────
#
# 由代码拼接，LLM 不参与。文案里的措辞刻意避开禁用词。

DISCLAIMER_TEXT: str = (
    "本报告为辅助建议，最终判断由招聘方作出。"
    "报告中的判定仅反映本次提问中获取到的证据，"
    "「本次未获取到证据」不代表候选人不具备该能力。"
)

# 审计声明：本次分析用到的输入来源，供追溯
AUDIT_TEXT: str = (
    "以上结论均可溯源到原始输入文本；"
    "被判定为「本次未获取到证据」的条目表示该维度尚未被提问覆盖，"
    "建议在面试中补充提问后再做判断。"
)


def is_allowed_verdict(verdict: str) -> bool:
    """判定值是否在白名单内。"""
    return verdict in ALLOWED_VERDICTS


def assert_allowed_verdict(verdict: str) -> str:
    """判定值不在白名单时抛错。用于运行期兜底。"""
    if not is_allowed_verdict(verdict):
        raise ValueError(
            f"非法判定值 {verdict!r}，只允许 {ALLOWED_VERDICTS}（合规约束 R1）"
        )
    return verdict


def find_forbidden_terms(text: str) -> list[str]:
    """
    扫描文本里的禁用词，返回命中的词列表（空 = 干净）。

    中文按子串匹配；英文按词边界匹配。
    """
    if not text:
        return []

    hits: list[str] = []
    for term in _FORBIDDEN_CJK:
        if term in text:
            hits.append(term)
    for match in _ASCII_PATTERN.finditer(text):
        hits.append(match.group(0).lower())
    return hits


def has_forbidden_terms(text: str) -> bool:
    """文本是否命中禁用词。"""
    return bool(find_forbidden_terms(text))


# ── 引用校验（R3）─────────────────────────────────────────────────

# 证据片段的最短长度。太短容易「碰巧命中」，失去校验意义。
EVIDENCE_MIN_LENGTH = 8

# 归一化时要去掉的字符：空白 + 常见中英文标点。
# 目的是容忍 LLM 微调标点，但不容忍改写内容。
_PUNCTUATION_RE = re.compile(r"[\s　，,。.；;：:、！!？?（）()【】\[\]「」『』“”\"'‘’—\-_/\\]+")


def normalize_evidence(text: str) -> str:
    """去掉空白与标点，用于引用比对。"""
    if not text:
        return ""
    return _PUNCTUATION_RE.sub("", text)


def verify_evidence_ref(ref: str, source_text: str, min_length: int = EVIDENCE_MIN_LENGTH) -> bool:
    """
    校验 LLM 返回的 evidence_ref 确实出现在原始文本中。

    做法：去掉空白与标点后做子串匹配（容忍 LLM 微调标点）。

    **不通过就丢弃该结论** —— 这把「防幻觉」从「相信模型」变成
    「可执行的代码断言」（计划书 §5.4）。

    :param ref: 模型给出的引用片段
    :param source_text: 原始输入文本
    :param min_length: 引用最短长度，过短的引用没有校验价值
    """
    if not ref or not source_text:
        return False

    normalized_ref = normalize_evidence(ref)
    if len(normalized_ref) < min_length:
        return False

    normalized_source = normalize_evidence(source_text)
    return normalized_ref in normalized_source


def extract_evidence_ref(text: str) -> Optional[str]:
    """
    从「简历第2段：「xxx」」这类带书名号的引用里抽出原文片段。

    HR 侧 prompt 要求模型用「『』」包住被引用的原文，
    这里把内容取出来，好让 verify_evidence_ref 精确比对。
    """
    if not text:
        return None

    for pattern in (r"[「『](.+?)[」』]", r"[:：]\s*[\"“](.+?)[\"”]"):
        match = re.search(pattern, text, re.DOTALL)
        if match:
            return match.group(1).strip()
    return None
