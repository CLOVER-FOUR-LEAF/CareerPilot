"""
BM25 检索器。

Career Advisor 不允许凭空编造建议，因此所有路线图条目都必须引用**实际检索到**的
doc_id。这里用 BM25（k1=1.5, b=0.75）在内置知识库上打分取 top-K。

中英混合分词沿用 match-agent 的策略：英文按词、中文取单字 + 二元组。
"""
import logging
import math
import re
from typing import Optional

from data.knowledge_base import KnowledgeDoc, KNOWLEDGE_BASE

logger = logging.getLogger(__name__)

# BM25 超参
K1 = 1.5
B = 0.75

# 每次最多送进上下文的文档数
TOP_K = 8

# 通用兜底文档（检索无结果时使用，仍然是"实际检索到"的，不算编造）
FALLBACK_DOC_IDS = ("kb_cross_001", "kb_cross_002")

CHINESE_STOPWORDS: frozenset[str] = frozenset({
    "的", "了", "和", "是", "在", "有", "与", "等", "及", "对", "为", "以",
    "并", "或", "被", "把", "从", "到", "该", "其", "之", "上", "下", "中",
    "我", "你", "他", "们", "个", "这", "那", "就", "也", "还", "会", "能",
    "需要", "建议", "通常", "核心", "能力", "岗位", "行业", "转型", "要求",
})

_ASCII_TOKEN = re.compile(r"[a-z][a-z0-9+#./-]*")
_CJK_RUN = re.compile(r"[\u4e00-\u9fff]+")


def _tokenize(text: str) -> list[str]:
    tokens: list[str] = []
    for match in _ASCII_TOKEN.finditer(text.lower()):
        token = match.group(0)
        if len(token) >= 2:
            tokens.append(token)
    for run in _CJK_RUN.findall(text):
        for char in run:
            if char not in CHINESE_STOPWORDS:
                tokens.append(char)
        for i in range(len(run) - 1):
            bigram = run[i:i + 2]
            if bigram not in CHINESE_STOPWORDS:
                tokens.append(bigram)
    return tokens


# ── 语料预处理：只算一次 ──
_DOC_TOKENS: dict[str, list[str]] = {}
_DOC_FREQ: dict[str, dict[str, int]] = {}
_DOC_LEN: dict[str, int] = {}
_AVG_DL: float = 0.0
_TOTAL_DOCS: int = 0
_DF: dict[str, int] = {}


def _build_index() -> None:
    global _AVG_DL, _TOTAL_DOCS, _DF

    if _DOC_TOKENS:
        return

    for doc in KNOWLEDGE_BASE:
        # 标题与技能词重复计入，提高标题命中的权重
        text = " ".join([doc["title"], doc["title"], doc["content"], *doc["skills"]])
        tokens = _tokenize(text)
        _DOC_TOKENS[doc["doc_id"]] = tokens
        _DOC_LEN[doc["doc_id"]] = len(tokens)

        freq: dict[str, int] = {}
        for token in tokens:
            freq[token] = freq.get(token, 0) + 1
        _DOC_FREQ[doc["doc_id"]] = freq
        for token in freq:
            _DF[token] = _DF.get(token, 0) + 1

    _TOTAL_DOCS = len(KNOWLEDGE_BASE)
    _AVG_DL = (sum(_DOC_LEN.values()) / _TOTAL_DOCS) if _TOTAL_DOCS else 0.0


def _bm25_score(query_tokens: list[str], doc_id: str) -> float:
    score = 0.0
    freq = _DOC_FREQ[doc_id]
    doc_len = _DOC_LEN[doc_id]

    for token in set(query_tokens):
        tf = freq.get(token, 0)
        if tf == 0:
            continue
        df = _DF.get(token, 0)
        idf = math.log(1 + (_TOTAL_DOCS - df + 0.5) / (df + 0.5))
        denominator = tf + K1 * (1 - B + B * doc_len / _AVG_DL)
        score += idf * (tf * (K1 + 1)) / denominator

    return score


def retrieve(
    query: str,
    industry: Optional[str] = None,
    top_k: int = TOP_K,
) -> list[KnowledgeDoc]:
    """
    按 BM25 检索知识库文档。

    - 行业匹配的文档会获得额外的加成分（业务上更相关）
    - 检索不到任何文档时，回落到通用兜底文档，保证路线图始终有可引用的依据
    """
    _build_index()

    query_tokens = _tokenize(query)
    results: list[tuple[float, KnowledgeDoc]] = []

    for doc in KNOWLEDGE_BASE:
        score = _bm25_score(query_tokens, doc["doc_id"])
        if industry and doc["industry"] == industry:
            score += 2.0   # 行业加权
        if score > 0:
            results.append((score, doc))

    results.sort(key=lambda pair: pair[0], reverse=True)

    if not results:
        logger.info("BM25 检索无命中，回落到通用文档 %s", list(FALLBACK_DOC_IDS))
        by_id = {d["doc_id"]: d for d in KNOWLEDGE_BASE}
        return [by_id[doc_id] for doc_id in FALLBACK_DOC_IDS if doc_id in by_id]

    return [doc for _score, doc in results[:top_k]]


def build_query(
    current_title: str,
    target_industry: str,
    target_position: str,
    skills: list[str],
    question: Optional[str] = None,
) -> str:
    """把用户档案拼成检索 query"""
    parts = [current_title, target_industry, target_position, *skills]
    if question:
        parts.append(question)
    return " ".join(p for p in parts if p)
