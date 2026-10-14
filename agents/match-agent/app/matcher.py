"""
Match Agent - 岗位匹配打分引擎

核心口径（契约 §7.3，不可更改）：

    final_score = round(0.40 * semantic_score + 0.35 * keyword_score + 0.25 * business_score, 1)

三个分项均为 0-100。全部为纯 Python 实现，不依赖 numpy / scikit-learn，
也不依赖任何 LLM。
"""
import logging
import math
import re
from typing import Optional

from pydantic import BaseModel, Field

from .knowledge_base import (
    ALIAS_INDEX,
    CERT_ALIASES,
    DATE_RANGE_PATTERN,
    EDUCATION_ALIASES,
    EDUCATION_LEVELS,
    METRIC_KEYWORDS,
    SKILL_LEXICON,
    SUBSUMED_BY,
    all_aliases,
    normalize,
)

logger = logging.getLogger(__name__)

# 契约 §7.3 锁定的权重
W_SEMANTIC = 0.40
W_KEYWORD = 0.35
W_BUSINESS = 0.25


# ─── 输出模型 ──────────────────────────────────────────────────

class MatchGap(BaseModel):
    gap_type: str          # missing_skill / weak_metric / missing_cert
    gap_content: str
    severity: int          # 1-5，硬性条件不满足固定为 5
    suggestion: str


class MatchResult(BaseModel):
    """匹配结果，对应契约 §7.3"""
    semantic_score: float = 0.0
    keyword_score: float = 0.0
    business_score: float = 0.0
    final_score: float = 0.0
    matched_skills: list[str] = Field(default_factory=list)
    gap_list: list[MatchGap] = Field(default_factory=list)


# ─── 语义相似度：TF-IDF 余弦 ───────────────────────────────────

# 中文停用词（单字 + 常见双字），避免"的了和是在"这类噪声主导向量
CHINESE_STOPWORDS: frozenset[str] = frozenset({
    "的", "了", "和", "是", "在", "有", "与", "等", "及", "对", "为", "以",
    "并", "或", "被", "把", "从", "到", "该", "其", "之", "上", "下", "中",
    "工作", "负责", "进行", "相关", "公司", "岗位", "职位", "要求", "能力",
    "以上", "优先", "熟悉", "熟练", "具备", "具有", "能够", "良好", "以及",
})

_ASCII_TOKEN = re.compile(r"[a-z][a-z0-9+#.]*")
_CJK_RUN = re.compile(r"[\u4e00-\u9fff]+")
_NUMBER_PATTERN = re.compile(r"\d+(?:\.\d+)?")


def _tokenize(text: str) -> list[str]:
    """
    中英混合分词。

    - 英文/数字按词切分
    - 中文取单字 + 二元组（不需要分词器，对简历/JD 这种短文本效果足够）
    """
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


def _term_freq(tokens: list[str]) -> dict[str, int]:
    tf: dict[str, int] = {}
    for token in tokens:
        tf[token] = tf.get(token, 0) + 1
    return tf


def _build_idf(docs: list[list[str]]) -> dict[str, float]:
    """平滑 IDF（sklearn smooth_idf 同口径）：idf = ln((1+N)/(1+df)) + 1"""
    n_docs = len(docs)
    df: dict[str, int] = {}
    for tokens in docs:
        for token in set(tokens):
            df[token] = df.get(token, 0) + 1
    return {term: math.log((1 + n_docs) / (1 + freq)) + 1 for term, freq in df.items()}


def _weighted_vector(tf: dict[str, int], idf: dict[str, float]) -> dict[str, float]:
    """TF-IDF 向量并做 L2 归一化"""
    vec = {term: count * idf.get(term, 1.0) for term, count in tf.items()}
    norm = math.sqrt(sum(v * v for v in vec.values()))
    if norm == 0.0:
        return {}
    return {term: v / norm for term, v in vec.items()}


def _cosine(a: dict[str, float], b: dict[str, float]) -> float:
    if not a or not b:
        return 0.0
    if len(a) > len(b):
        a, b = b, a
    return max(0.0, min(1.0, sum(weight * b.get(term, 0.0) for term, weight in a.items())))


def compute_semantic_score(jd_text: str, resume_text: str) -> float:
    """
    JD 全文与简历全文的 TF-IDF 余弦相似度，缩放到 0-100。
    任一侧为空或无有效 token 时返回 0。
    """
    if not jd_text.strip() or not resume_text.strip():
        return 0.0

    jd_tokens = _tokenize(jd_text)
    resume_tokens = _tokenize(resume_text)
    if not jd_tokens or not resume_tokens:
        return 0.0

    idf = _build_idf([jd_tokens, resume_tokens])
    jd_vec = _weighted_vector(_term_freq(jd_tokens), idf)
    resume_vec = _weighted_vector(_term_freq(resume_tokens), idf)

    return round(_cosine(jd_vec, resume_vec) * 100, 1)


# ─── 技能抽取与关键词覆盖 ──────────────────────────────────────

def extract_skills_from_text(text: str) -> list[str]:
    """
    用词表从任意文本中抽取规范技能名（已命中的别名去重）。

    命中包含关系的技能（如 Spring ⊂ Spring Boot）只保留更具体的那一个，
    避免同一处文字被重复计入 JD 硬技能分母。
    """
    if not text.strip():
        return []

    lowered = normalize(text)
    found: list[str] = []
    seen: set[str] = set()

    for alias, canonical in all_aliases():
        if canonical in seen:
            continue
        if _contains_token(alias, lowered):
            seen.add(canonical)
            found.append(canonical)

    # 丢弃被更具体技能覆盖的项（Spring Boot 命中后不再单独计 Spring）
    result = [
        skill for skill in found
        if not (skill in SUBSUMED_BY and any(s in seen for s in SUBSUMED_BY[skill]))
    ]
    return result


def _contains_token(needle: str, haystack: str) -> bool:
    """
    带边界判断的子串匹配，避免 "go" 命中 "google"、"ts" 命中 "tsource"。
    中文字符两侧不需要边界，英文/数字则要求非字母数字边界。
    """
    start = haystack.find(needle)
    while start != -1:
        end = start + len(needle)
        before = haystack[start - 1] if start > 0 else " "
        after = haystack[end] if end < len(haystack) else " "

        needle_is_cjk = any("\u4e00" <= c <= "\u9fff" for c in needle)
        if needle_is_cjk:
            return True

        before_ok = not before.isalnum()
        after_ok = not after.isalnum()
        if before_ok and after_ok:
            return True

        start = haystack.find(needle, start + 1)
    return False


def _canonicalize(skill: str) -> str:
    """把用户传入的技能名映射到词表里的规范名；命中不了就原样小写返回"""
    key = normalize(skill)
    if not key:
        return ""
    if key in ALIAS_INDEX:
        return ALIAS_INDEX[key]
    for canonical, (_category, aliases) in SKILL_LEXICON.items():
        if any(normalize(alias) == key for alias in aliases):
            return canonical
    return key


def resolve_jd_skills(jd_text: str, provided: Optional[list[str]]) -> list[str]:
    """
    决定用于打分的 JD 硬技能列表。
    调用方显式传入 jd_skills 时以传入为准（并去重），否则用词表从 JD 全文抽取。
    """
    if provided:
        resolved: list[str] = []
        for item in provided:
            canonical = _canonicalize(item)
            if canonical and canonical not in resolved:
                resolved.append(canonical)
        return resolved
    return extract_skills_from_text(jd_text)


def compute_keyword_score(
    jd_skills: list[str], resume_text: str,
) -> tuple[float, list[str], list[str]]:
    """
    JD 硬技能在简历中的覆盖率，分母 = JD 硬技能数。
    返回 (score, matched_skills, missing_skills)。JD 技能为 0 时返回 0。
    """
    if not jd_skills:
        logger.info("keyword_score: JD 未识别到硬技能，分母为 0，记 0 分")
        return 0.0, [], []

    lowered = normalize(resume_text)
    matched: list[str] = []
    missing: list[str] = []

    for skill in jd_skills:
        aliases = SKILL_LEXICON.get(skill, (None, (skill,)))[1]
        if any(_contains_token(normalize(alias), lowered) for alias in aliases):
            matched.append(skill)
        else:
            missing.append(skill)

    score = round(len(matched) / len(jd_skills) * 100, 1)
    return score, matched, missing


# ─── 业务对齐：年限 + 学历 ─────────────────────────────────────

_EXPERIENCE_PATTERNS: tuple[tuple[re.Pattern[str], int], ...] = (
    (re.compile(r"(\d+)\s*[-~至到—]\s*\d+\s*年"), 1),
    (re.compile(r"(\d+)\s*年\s*(?:以上|及以上|\+)"), 1),
    (re.compile(r"(?:工作)?经验\s*(\d+)\s*年"), 1),
    (re.compile(r"(\d+)\s*\+?\s*years?", re.IGNORECASE), 1),
)

_RESUME_EXPERIENCE_PATTERNS: tuple[re.Pattern[str], ...] = (
    re.compile(r"(\d+)\s*年\s*(?:以上|及以上|\+)?\s*(?:相关|工作|开发|从业)?\s*经验"),
    re.compile(r"(\d+)\s*年\s*(?:相关)?\s*(?:开发|工作)经[验历]"),
    re.compile(r"(\d+)\s*\+?\s*years?\s*(?:of\s*)?experience", re.IGNORECASE),
    re.compile(r"经验\s*(\d+)\s*年"),
)


def extract_required_years(jd_text: str) -> Optional[float]:
    """抽取 JD 要求的最低工作年限；未写明返回 None"""
    if not jd_text.strip():
        return None
    if re.search(r"经验\s*(?:不限|不限年限)|应届|无经验限制", jd_text):
        return 0.0

    best: Optional[float] = None
    for pattern, group in _EXPERIENCE_PATTERNS:
        match = pattern.search(jd_text)
        if match:
            value = float(match.group(group))
            best = value if best is None else min(best, value)
    return best


def extract_resume_years(resume_text: str, current_year: int) -> float:
    """
    估计简历反映的工作年限。优先级：
    显式年限表述 > 工作经历时间区间求和 > 毕业年份推算。
    """
    if not resume_text.strip():
        return 0.0

    for pattern in _RESUME_EXPERIENCE_PATTERNS:
        match = pattern.search(resume_text)
        if match:
            return float(match.group(1))

    total_months = 0
    for start_year, start_month, end_token in re.findall(DATE_RANGE_PATTERN, resume_text):
        if not end_token.isdigit():
            end_year, end_month = current_year, 12
        else:
            end_year = int(end_token)
            end_month = 12
        months = (end_year - int(start_year)) * 12 + (end_month - (int(start_month) if start_month else 1))
        if 0 < months <= 12 * 40:   # 过滤异常区间
            total_months += months

    if total_months > 0:
        return round(total_months / 12, 1)

    grad_match = re.search(r"(19|20)\d{2}\s*年?\s*毕业|毕业时间[:：]?\s*((?:19|20)\d{2})", resume_text)
    if grad_match:
        raw = grad_match.group(2) or grad_match.group(0)[:4]
        years = re.search(r"((?:19|20)\d{2})", raw)
        if years:
            delta = current_year - int(years.group(1))
            if 0 <= delta <= 40:
                return float(delta)
    return 0.0


def extract_required_education(jd_text: str) -> str:
    """抽取 JD 要求的学历门槛（取出现过的最低层级，如"本科及以上"→本科）"""
    best = ""
    best_level = 99
    for alias in EDUCATION_ALIASES:
        if alias in jd_text:
            level = EDUCATION_LEVELS.get(alias, 0)
            if level < best_level:
                best_level = level
                best = alias
    return best


def extract_resume_education(resume_text: str) -> str:
    """抽取简历中的最高学历"""
    best = ""
    best_level = -1
    for alias in EDUCATION_ALIASES:
        if alias in resume_text:
            level = EDUCATION_LEVELS.get(alias, 0)
            if level > best_level:
                best_level = level
                best = alias
    return best


def compute_business_score(
    jd_text: str, resume_text: str, current_year: int,
) -> tuple[float, dict[str, object]]:
    """
    业务对齐度：年限占 60%，学历占 40%。

    JD 未提出某项硬要求时，该项不扣分（记满分），因为无从违反。
    返回 (score, 明细)。
    """
    detail: dict[str, object] = {}

    # ── 年限 ──
    required_years = extract_required_years(jd_text)
    resume_years = extract_resume_years(resume_text, current_year)

    if required_years is None:
        years_score = 100.0
    elif required_years <= 0:
        years_score = 100.0
    else:
        if resume_years >= required_years:
            years_score = 100.0
        else:
            ratio = resume_years / required_years
            years_score = round(max(0.0, ratio) * 100, 1)

    detail["required_years"] = required_years
    detail["resume_years"] = resume_years
    detail["years_score"] = years_score

    # ── 学历 ──
    required_edu = extract_required_education(jd_text)
    resume_edu = extract_resume_education(resume_text)

    if not required_edu:
        education_score = 100.0
    else:
        required_level = EDUCATION_LEVELS.get(required_edu, 0)
        resume_level = EDUCATION_LEVELS.get(resume_edu, 0)
        if resume_level >= required_level:
            education_score = 100.0
        else:
            # 差一级约扣 60 分，差两级及以上记 0
            gap = required_level - resume_level
            education_score = max(0.0, 100.0 - gap * 60.0)

    detail["required_education"] = required_edu
    detail["resume_education"] = resume_edu
    detail["education_score"] = education_score

    score = round(0.6 * years_score + 0.4 * education_score, 1)
    detail["business_score"] = score
    return score, detail


# ─── 差距清单 ──────────────────────────────────────────────────

def detect_missing_certs(jd_text: str, resume_text: str) -> list[str]:
    """JD 里点名的证书，简历里查不到的"""
    jd_lower = normalize(jd_text)
    resume_lower = normalize(resume_text)
    missing = []
    for cert_name, aliases in CERT_ALIASES.items():
        in_jd = any(_contains_token(normalize(a), jd_lower) for a in aliases)
        in_resume = any(_contains_token(normalize(a), resume_lower) for a in aliases)
        if in_jd and not in_resume:
            missing.append(cert_name)
    return missing


def has_quantified_metrics(text: str) -> bool:
    """文本中是否存在"数字 + 业务指标"的表达"""
    numbers = _NUMBER_PATTERN.findall(text)
    if not numbers:
        return False
    return any(keyword in text.lower() for keyword in METRIC_KEYWORDS)


def build_gap_list(
    jd_skills: list[str],
    missing_skills: list[str],
    business_detail: dict[str, object],
    jd_text: str,
    resume_text: str,
) -> list[MatchGap]:
    """
    构建差距清单，按 severity 倒序。
    硬性条件不满足（学历 / 年限）severity 固定 5，排在最前。
    """
    gaps: list[MatchGap] = []

    # ── 硬性：学历 ──
    required_edu = str(business_detail.get("required_education") or "")
    resume_edu = str(business_detail.get("resume_education") or "")
    required_level = EDUCATION_LEVELS.get(required_edu, 0)
    resume_level = EDUCATION_LEVELS.get(resume_edu, 0)
    if required_edu and resume_level < required_level:
        gaps.append(MatchGap(
            gap_type="weak_metric",
            gap_content=f"学历不满足硬性要求：岗位要求 {required_edu} 及以上，简历最高学历为 {resume_edu or '未识别到'}",
            severity=5,
            suggestion=(
                f"该学历要求属于一票否决项。若确有相关工作成果，可在简历中强化项目复杂度与业务影响力弥补；"
                f"否则建议优先投递学历要求匹配的岗位，或通过成人在职学历提升后再行投递。"
            ),
        ))

    # ── 硬性：年限 ──
    required_years = business_detail.get("required_years")
    resume_years = float(business_detail.get("resume_years") or 0.0)
    if isinstance(required_years, (int, float)) and required_years > 0 and resume_years < required_years:
        gaps.append(MatchGap(
            gap_type="weak_metric",
            gap_content=(
                f"工作年限不足：岗位要求 {required_years:g} 年以上，简历可识别年限约 {resume_years:g} 年"
            ),
            severity=5,
            suggestion=(
                "年限为硬性门槛。建议补充与岗位相关的项目密度、实习或兼职产出，"
                "并在简历中用时间线清晰呈现累计年限；确认是否可放宽该要求。"
            ),
        ))

    # ── 必备技能缺失 ──
    for skill in missing_skills:
        category = SKILL_LEXICON.get(skill, ("technical", ()))[0]
        if category == "soft_skill":
            severity, verb = 2, "在自我评价或项目协作描述中体现该软技能"
        elif category == "domain":
            severity, verb = 4, "补充该业务领域的具体案例与行业术语"
        elif category == "certification":
            severity, verb = 3, "考取或补充该资质证明"
        else:
            severity, verb = 4, "在项目经历中加入该技术的实际使用案例"
        gaps.append(MatchGap(
            gap_type="missing_skill" if category != "certification" else "missing_cert",
            gap_content=skill,
            severity=severity,
            suggestion=f"{verb}（{skill}），并在技能列表中明确标注使用年限与熟练度",
        ))

    # ── 必备证书缺失 ──
    for cert in detect_missing_certs(jd_text, resume_text):
        gaps.append(MatchGap(
            gap_type="missing_cert",
            gap_content=cert,
            severity=3,
            suggestion=f"JD 明确要求 {cert}，简历未体现；建议尽快备考或在技能栏注明备考计划",
        ))

    # ── 量化不足 ──
    if jd_text and any(keyword in jd_text.lower() for keyword in METRIC_KEYWORDS):
        if not has_quantified_metrics(resume_text):
            gaps.append(MatchGap(
                gap_type="weak_metric",
                gap_content="简历缺少可量化的业务指标，而 JD 明确强调结果导向",
                severity=2,
                suggestion=(
                    "把每段经历改写为「动作 + 方法 + 量化结果」结构，"
                    "补齐转化率、营收、效率、良率等具体数字"
                ),
            ))

    # severity 倒序；同 severity 保持生成顺序
    gaps.sort(key=lambda g: g.severity, reverse=True)
    return gaps


# ─── 主入口 ────────────────────────────────────────────────────

def match_resume_jd(
    jd_text: str,
    resume_text: str,
    jd_skills: Optional[list[str]] = None,
    current_year: int = 2026,
) -> MatchResult:
    """
    岗位匹配打分主入口。

    注意契约要求的权重公式是硬约束，不可调整。
    """
    # 两侧文本都必须非空：JD 或简历缺失时无从匹配，直接返回全零，
    # 避免"缺失 JD 反而拿满分业务分"这种反直觉结果。
    if not jd_text.strip() or not resume_text.strip():
        logger.warning("jd_text 或 resume_text 为空，返回全零匹配结果")
        return MatchResult()

    semantic_score = compute_semantic_score(jd_text, resume_text)

    resolved_skills = resolve_jd_skills(jd_text, jd_skills)
    keyword_score, matched_skills, missing_skills = compute_keyword_score(resolved_skills, resume_text)

    business_score, business_detail = compute_business_score(jd_text, resume_text, current_year)

    gap_list = build_gap_list(
        jd_skills=resolved_skills,
        missing_skills=missing_skills,
        business_detail=business_detail,
        jd_text=jd_text,
        resume_text=resume_text,
    )

    final_score = round(
        W_SEMANTIC * semantic_score
        + W_KEYWORD * keyword_score
        + W_BUSINESS * business_score,
        1,
    )

    return MatchResult(
        semantic_score=semantic_score,
        keyword_score=keyword_score,
        business_score=business_score,
        final_score=final_score,
        matched_skills=matched_skills,
        gap_list=gap_list,
    )
