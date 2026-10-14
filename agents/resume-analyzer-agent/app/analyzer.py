"""
Resume Analyzer Agent - ATS scoring engine
7 dimensions: structure, keywords, quantification, duration, typos, formatting, expression
"""
import re
import logging
from typing import Optional

from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)


# ─── Output models ──────────────────────────────────────────────

class DimensionScore(BaseModel):
    name: str
    score: float          # 0-100
    weight: float = 1.0
    feedback: str = ""


class ResumeSkill(BaseModel):
    name: str
    category: str = "technical"
    proficiency: str = "unknown"  # expert, intermediate, beginner, unknown


class Suggestion(BaseModel):
    category: str          # structure, keyword, quantification, formatting, expression
    priority: str          # high, medium, low
    title: str
    detail: str


class AnalysisResult(BaseModel):
    resume_id: str = ""
    overall_score: float = 0.0
    dimensions: list[DimensionScore] = Field(default_factory=list)
    skills: list[ResumeSkill] = Field(default_factory=list)
    suggestions: list[Suggestion] = Field(default_factory=list)
    section_count: int = 0
    word_count: int = 0
    has_photo: bool = False
    has_contact: bool = False


# ─── 7 Dimension Scoring ────────────────────────────────────────

def score_structure(text: str) -> DimensionScore:
    """Dimension 1: Structure completeness"""
    expected_sections = [
        "教育", "education",
        "工作经", "work experience", "employment",
        "项目经", "project experience",
        "技能", "skills", "technical skills",
        "自我评价", "summary", "objective", "profile",
    ]
    text_lower = text.lower()
    found = sum(1 for s in expected_sections if s in text_lower)
    section_score = min(found / 3 * 100, 100)  # Need at least 3 sections

    feedback = "简历结构"
    if section_score >= 80:
        feedback = "简历结构完整，包含教育、工作、项目、技能等关键模块"
    elif section_score >= 50:
        feedback = "简历缺少部分关键模块，建议补充教育背景或项目经验"
    else:
        feedback = "简历结构不完整，建议按标准模板重新组织：教育→工作经历→项目经验→技能"

    return DimensionScore(
        name="structure",
        score=round(section_score, 1),
        weight=1.5,
        feedback=feedback,
    )


def score_keywords(text: str) -> DimensionScore:
    """Dimension 2: Industry keyword density"""
    # Common high-value keywords
    high_value = {
        "负责", "主导", "设计", "开发", "优化", "提升", "实现", "管理",
        "led", "designed", "developed", "optimized", "managed", "implemented",
        "python", "java", "sql", "aws", "docker", "kubernetes",
        "machine learning", "deep learning", "data analysis",
        "product management", "agile", "scrum",
    }
    text_lower = text.lower()
    found = sum(1 for kw in high_value if kw in text_lower)
    kw_score = min(found / 5 * 100, 100)

    feedback = ""
    if kw_score >= 80:
        feedback = "关键词丰富，行业术语使用恰当"
    elif kw_score >= 50:
        feedback = "关键词覆盖一般，建议增加行业核心术语和专业技能描述"
    else:
        feedback = "关键词匮乏，建议参考目标岗位JD，在简历中融入相关行业术语"

    return DimensionScore(
        name="keywords",
        score=round(kw_score, 1),
        weight=1.3,
        feedback=feedback,
    )


def score_quantification(text: str) -> DimensionScore:
    """Dimension 3: Quantified achievements"""
    # Look for numbers followed by metrics
    number_patterns = [
        r'\d+%?',           # percentages, raw numbers
        r'\d+[万kK]',       # large numbers
        r'\d+\.\d+',        # decimals
    ]
    count = 0
    for pattern in number_patterns:
        count += len(re.findall(pattern, text))

    # Check for quantified statements (number + action verb)
    quantified = len(re.findall(r'\d+.*?(?:提升|增长|降低|节省|完成|实现|增加)', text))
    quantified += len(re.findall(r'(?:increased|decreased|improved|reduced|achieved|completed).*?\d+', text, re.IGNORECASE))

    q_score = min((count / 10 + quantified / 5) * 50, 100)

    feedback = ""
    if q_score >= 80:
        feedback = "数据量化充分，用具体数字展示了工作成果"
    elif q_score >= 50:
        feedback = "部分经历有数据支撑，建议更多使用'提升了X%'、'管理Y人团队'等量化表达"
    else:
        feedback = "缺少数据量化，建议用具体数字描述成果，如'提升转化率30%'、'管理10人团队'"

    return DimensionScore(
        name="quantification",
        score=round(q_score, 1),
        weight=1.4,
        feedback=feedback,
    )


def score_duration(text: str) -> DimensionScore:
    """Dimension 4: Work duration consistency"""
    # Look for date ranges
    date_pattern = r'(\d{4}[./\-]\d{1,2})\s*[-~至到]\s*(\d{4}[./\-]\d{1,2}|至今|present)'
    dates = re.findall(date_pattern, text, re.IGNORECASE)

    d_score = 0.0
    if len(dates) >= 3:
        d_score = 90.0
    elif len(dates) >= 2:
        d_score = 70.0
    elif len(dates) >= 1:
        d_score = 50.0
    else:
        d_score = 20.0

    feedback = ""
    if d_score >= 80:
        feedback = "时间线清晰，工作经历时间标注完整"
    elif d_score >= 50:
        feedback = "部分经历缺少时间标注，建议为每段经历添加起止时间"
    else:
        feedback = "缺少时间线信息，建议为教育和工作经历添加明确的起止时间"

    return DimensionScore(
        name="duration",
        score=round(d_score, 1),
        weight=1.0,
        feedback=feedback,
    )


def score_typos(text: str) -> DimensionScore:
    """Dimension 5: Typo and grammar check (basic)"""
    # Basic checks
    issues = 0

    # Check for inconsistent punctuation (mixing Chinese and English)
    has_cn_comma = '，' in text
    has_en_comma_after_cn = bool(re.search(r'[\u4e00-\u9fff],[\u4e00-\u9fff]', text))
    if has_cn_comma and has_en_comma_after_cn:
        issues += 2

    # Check for multiple spaces
    if re.search(r'  +', text):
        issues += 1

    # Check for inconsistent date formats
    date_formats = set()
    if re.search(r'\d{4}\.\d{2}', text):
        date_formats.add('dot')
    if re.search(r'\d{4}/\d{2}', text):
        date_formats.add('slash')
    if re.search(r'\d{4}-\d{2}', text):
        date_formats.add('dash')
    if len(date_formats) > 1:
        issues += 2

    t_score = max(100 - issues * 20, 0)

    feedback = ""
    if t_score >= 80:
        feedback = "格式规范，标点符号和日期格式统一"
    elif t_score >= 50:
        feedback = "存在少量格式不一致，建议统一标点符号和日期格式"
    else:
        feedback = "格式问题较多，建议检查标点符号混用、日期格式不统一等问题"

    return DimensionScore(
        name="typos",
        score=round(t_score, 1),
        weight=0.8,
        feedback=feedback,
    )


def score_formatting(text: str) -> DimensionScore:
    """Dimension 6: Formatting quality"""
    lines = text.split('\n')
    total_lines = len(lines)
    if total_lines == 0:
        return DimensionScore(name="formatting", score=0, weight=1.0, feedback="内容为空")

    # Check line length consistency
    long_lines = sum(1 for l in lines if len(l) > 120)
    long_ratio = long_lines / total_lines

    # Check bullet point usage
    bullet_count = len(re.findall(r'^[\s]*[•·\-–*]\s', text, re.MULTILINE))

    f_score = 100.0
    if long_ratio > 0.3:
        f_score -= 30
    if bullet_count < 3:
        f_score -= 20
    if total_lines < 10:
        f_score -= 20

    f_score = max(f_score, 0)

    feedback = ""
    if f_score >= 80:
        feedback = "排版整洁，使用了项目符号，行长度适中"
    elif f_score >= 50:
        feedback = "排版基本合理，建议增加项目符号提升可读性"
    else:
        feedback = "排版需改善，建议使用项目符号、控制每行长度、增加段落分隔"

    return DimensionScore(
        name="formatting",
        score=round(f_score, 1),
        weight=1.0,
        feedback=feedback,
    )


def score_expression(text: str) -> DimensionScore:
    """Dimension 7: Professional expression quality"""
    # Check for weak expressions
    weak_patterns = [
        r'负责.*?工作',     # vague "responsible for work"
        r'参与.*?项目',     # vague "participated in projects"
        r'协助.*?完成',     # "assisted to complete"
    ]
    weak_count = sum(len(re.findall(p, text)) for p in weak_patterns)

    # Check for strong action verbs
    strong_verbs = [
        "主导", "设计", "搭建", "重构", "优化", "推动", "建立",
        "led", "architected", "built", "redesigned", "optimized", "drove",
    ]
    strong_count = sum(1 for v in strong_verbs if v in text.lower())

    e_score = 100.0
    e_score -= weak_count * 10
    e_score += strong_count * 5
    e_score = max(min(e_score, 100), 0)

    feedback = ""
    if e_score >= 80:
        feedback = "表达专业，使用了强有力的动词展示工作成果"
    elif e_score >= 50:
        feedback = "表达基本专业，建议用'主导'、'设计'等动词替代'负责'、'参与'"
    else:
        feedback = "表达过于平淡，建议用STAR法则重写经历：情境→任务→行动→结果"

    return DimensionScore(
        name="expression",
        score=round(e_score, 1),
        weight=1.2,
        feedback=feedback,
    )


# ─── Skill extraction ──────────────────────────────────────────

RESUME_SKILLS_MAP = {
    "python", "java", "javascript", "typescript", "go", "golang", "c++", "c#",
    "react", "vue", "angular", "node.js", "spring", "django", "flask",
    "mysql", "postgresql", "mongodb", "redis", "elasticsearch",
    "docker", "kubernetes", "aws", "azure", "gcp",
    "machine learning", "deep learning", "nlp",
    "sql", "nosql", "graphql", "rest",
    "git", "ci/cd", "linux",
    "product management", "agile", "scrum", "pmp",
}


def extract_resume_skills(text: str) -> list[ResumeSkill]:
    """Extract skills mentioned in resume"""
    text_lower = text.lower()
    skills = []
    for skill_name in RESUME_SKILLS_MAP:
        if skill_name in text_lower:
            skills.append(ResumeSkill(name=skill_name, category="technical"))
    return skills


# ─── Suggestion generation ──────────────────────────────────────

def generate_suggestions(dimensions: list[DimensionScore]) -> list[Suggestion]:
    """Generate top 10 improvement suggestions based on dimension scores"""
    suggestions = []

    for dim in dimensions:
        if dim.score < 60:
            if dim.name == "structure":
                suggestions.append(Suggestion(
                    category="structure", priority="high",
                    title="完善简历结构",
                    detail="建议按以下顺序组织：个人信息→教育背景→工作经历→项目经验→专业技能→自我评价"
                ))
            elif dim.name == "keywords":
                suggestions.append(Suggestion(
                    category="keyword", priority="high",
                    title="增加行业关键词",
                    detail="参考目标岗位JD，在技能和工作描述中融入相关行业术语"
                ))
            elif dim.name == "quantification":
                suggestions.append(Suggestion(
                    category="quantification", priority="high",
                    title="增加数据量化",
                    detail="用具体数字描述成果，如'提升转化率30%'、'管理10人团队'、'节省成本50万'"
                ))
            elif dim.name == "duration":
                suggestions.append(Suggestion(
                    category="structure", priority="medium",
                    title="补充时间线",
                    detail="为每段教育和工作经历添加明确的起止时间（如2020.06-2023.08）"
                ))
            elif dim.name == "typos":
                suggestions.append(Suggestion(
                    category="formatting", priority="medium",
                    title="统一格式规范",
                    detail="检查并统一标点符号使用（中文/英文）、日期格式（YYYY.MM）"
                ))
            elif dim.name == "formatting":
                suggestions.append(Suggestion(
                    category="formatting", priority="medium",
                    title="改善排版",
                    detail="使用项目符号列表描述工作内容，控制每行不超过100字符"
                ))
            elif dim.name == "expression":
                suggestions.append(Suggestion(
                    category="expression", priority="high",
                    title="提升表达专业性",
                    detail="使用STAR法则（情境-任务-行动-结果）重写工作经历，用强动词开头"
                ))

    # Sort by priority and limit to 10
    priority_order = {"high": 0, "medium": 1, "low": 2}
    suggestions.sort(key=lambda s: priority_order.get(s.priority, 3))
    return suggestions[:10]


# ─── Main analysis function ─────────────────────────────────────

def analyze_resume(text: str) -> AnalysisResult:
    """
    Full ATS-style resume analysis with 7 dimensions
    """
    if not text or not text.strip():
        return AnalysisResult(overall_score=0)

    # Calculate all 7 dimensions
    dimensions = [
        score_structure(text),
        score_keywords(text),
        score_quantification(text),
        score_duration(text),
        score_typos(text),
        score_formatting(text),
        score_expression(text),
    ]

    # Weighted overall score
    total_weight = sum(d.weight for d in dimensions)
    weighted_sum = sum(d.score * d.weight for d in dimensions)
    overall = round(weighted_sum / total_weight, 1) if total_weight > 0 else 0

    # Extract skills
    skills = extract_resume_skills(text)

    # Generate suggestions
    suggestions = generate_suggestions(dimensions)

    # Basic stats
    word_count = len(text)
    section_count = len(re.findall(r'\n\s*(?:教育|工作|项目|技能|自我评价|experience|education|skills|summary)', text, re.IGNORECASE))
    has_contact = bool(re.search(r'(?:电话|手机|邮箱|email|phone|tel)[：:]', text, re.IGNORECASE))

    return AnalysisResult(
        overall_score=overall,
        dimensions=dimensions,
        skills=skills,
        suggestions=suggestions,
        section_count=section_count,
        word_count=word_count,
        has_contact=has_contact,
    )
