"""
JD Parser Agent - Three-phase parsing: regex → classify → LLM
Extracts structured data from raw job descriptions.
"""
import re
import logging
from typing import Optional

from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)


# ─── Output models ──────────────────────────────────────────────

class JdSkill(BaseModel):
    name: str
    category: str = "other"      # technical, soft_skill, tool, language, certification
    importance: str = "required"  # required, preferred, nice_to_have
    years_required: Optional[float] = None


class JdChunk(BaseModel):
    """Structured JD parse result"""
    job_title: str = ""
    company_name: str = ""
    department: str = ""
    location: str = ""
    employment_type: str = ""     # full_time, part_time, contract, remote
    salary_min: Optional[float] = None
    salary_max: Optional[float] = None
    salary_currency: str = "CNY"

    experience_min_years: Optional[float] = None
    experience_max_years: Optional[float] = None
    education_required: str = ""

    skills: list[JdSkill] = Field(default_factory=list)
    responsibilities: list[str] = Field(default_factory=list)
    requirements: list[str] = Field(default_factory=list)
    benefits: list[str] = Field(default_factory=list)

    raw_text: str = ""
    parse_score: float = 0.0     # 0-1, confidence of the parse result
    parse_method: str = "regex"   # regex, classify, llm


# ─── Phase 1: Regex extraction ──────────────────────────────────

# Common patterns
SALARY_PATTERN = re.compile(
    r'(\d+)[~\-—至到](\d+)\s*[kK万]',
    re.IGNORECASE
)
EXPERIENCE_PATTERN = re.compile(
    r'(\d+)[~\-—至到](\d+)\s*年.*?(?:经验|工作)',
    re.IGNORECASE
)
EDUCATION_PATTERN = re.compile(
    r'(博士|硕士|本科|大专|大学|学士|MBA|EMBA)',
    re.IGNORECASE
)
LOCATION_PATTERN = re.compile(
    r'(?:工作地点|工作城市|坐标|地址|Location)[：:]\s*(.+?)(?:\n|$)',
    re.IGNORECASE
)
TITLE_PATTERN = re.compile(
    r'(?:职位名称|岗位名称|Title|Job Title)[：:]\s*(.+?)(?:\n|$)',
    re.IGNORECASE
)
EMPLOYMENT_TYPE_KEYWORDS = {
    "full_time": ["全职", "full-time", "full time"],
    "part_time": ["兼职", "part-time", "part time"],
    "contract": ["合同", "contract", "外包"],
    "remote": ["远程", "remote", "在家办公"],
}

# Section headers for splitting
SECTION_HEADERS = [
    "岗位职责", "工作职责", "工作内容", "主要职责", "Responsibilities",
    "任职要求", "职位要求", "岗位要求", "任职资格", "Requirements",
    "福利待遇", "福利", "我们提供", "Benefits", "Perks",
]


def regex_extract(text: str) -> JdChunk:
    """Phase 1: Extract structured fields using regex"""
    chunk = JdChunk(raw_text=text)

    # Job title
    title_match = TITLE_PATTERN.search(text)
    if title_match:
        chunk.job_title = title_match.group(1).strip()

    # Location
    loc_match = LOCATION_PATTERN.search(text)
    if loc_match:
        chunk.location = loc_match.group(1).strip()

    # Salary
    salary_match = SALARY_PATTERN.search(text)
    if salary_match:
        chunk.salary_min = float(salary_match.group(1))
        chunk.salary_max = float(salary_match.group(2))

    # Experience
    exp_match = EXPERIENCE_PATTERN.search(text)
    if exp_match:
        chunk.experience_min_years = float(exp_match.group(1))
        chunk.experience_max_years = float(exp_match.group(2))

    # Education
    edu_match = EDUCATION_PATTERN.search(text)
    if edu_match:
        chunk.education_required = edu_match.group(1)

    # Employment type
    text_lower = text.lower()
    for emp_type, keywords in EMPLOYMENT_TYPE_KEYWORDS.items():
        if any(kw in text_lower for kw in keywords):
            chunk.employment_type = emp_type
            break

    return chunk


# ─── Phase 2: Section classification ────────────────────────────

def classify_sections(text: str, chunk: JdChunk) -> JdChunk:
    """Phase 2: Split text into sections and classify"""
    lines = text.split('\n')
    current_section = None
    section_content: dict[str, list[str]] = {}

    for line in lines:
        stripped = line.strip()
        if not stripped:
            continue

        # Check if this line is a section header
        is_header = False
        for header in SECTION_HEADERS:
            if header.lower() in stripped.lower() and len(stripped) < 30:
                current_section = header
                section_content[current_section] = []
                is_header = True
                break

        if not is_header and current_section:
            section_content[current_section].append(stripped)

    # Map sections to chunk fields
    responsibility_headers = ["岗位职责", "工作职责", "工作内容", "主要职责", "Responsibilities"]
    requirement_headers = ["任职要求", "职位要求", "岗位要求", "任职资格", "Requirements"]
    benefit_headers = ["福利待遇", "福利", "我们提供", "Benefits", "Perks"]

    for header, content in section_content.items():
        if header in responsibility_headers:
            chunk.responsibilities = content
        elif header in requirement_headers:
            chunk.requirements = content
        elif header in benefit_headers:
            chunk.benefits = content

    return chunk


# ─── Phase 3: Skill extraction (keyword-based) ─────────────────

# Common tech skills for matching
TECH_SKILLS = {
    "python", "java", "javascript", "typescript", "go", "golang", "rust", "c++", "c#",
    "react", "vue", "angular", "node.js", "spring", "django", "flask", "fastapi",
    "mysql", "postgresql", "mongodb", "redis", "elasticsearch",
    "docker", "kubernetes", "aws", "azure", "gcp",
    "machine learning", "deep learning", "nlp", "computer vision",
    "sql", "nosql", "graphql", "rest", "grpc",
    "git", "ci/cd", "jenkins", "github actions",
    "agile", "scrum", "jira",
}

SOFT_SKILLS = {
    "沟通能力", "团队合作", "领导力", "问题解决", "项目管理",
    "communication", "teamwork", "leadership", "problem solving",
    "analytical", "critical thinking", "attention to detail",
}


def extract_skills(text: str) -> list[JdSkill]:
    """Phase 3: Extract skills from text"""
    text_lower = text.lower()
    skills = []
    seen = set()

    # Technical skills
    for skill_name in TECH_SKILLS:
        if skill_name in text_lower and skill_name not in seen:
            seen.add(skill_name)
            # Determine importance based on section position
            importance = "required"
            skills.append(JdSkill(
                name=skill_name,
                category="technical",
                importance=importance,
            ))

    # Soft skills
    for skill_name in SOFT_SKILLS:
        if skill_name in text_lower and skill_name not in seen:
            seen.add(skill_name)
            skills.append(JdSkill(
                name=skill_name,
                category="soft_skill",
                importance="required",
            ))

    return skills


# ─── Main parse function ────────────────────────────────────────

def parse_jd(text: str) -> JdChunk:
    """
    Three-phase JD parsing:
    1. Regex extraction for structured fields
    2. Section classification for responsibilities/requirements/benefits
    3. Skill extraction
    """
    if not text or not text.strip():
        return JdChunk(parse_score=0.0, parse_method="regex")

    # Phase 1
    chunk = regex_extract(text)

    # Phase 2
    chunk = classify_sections(text, chunk)

    # Phase 3
    chunk.skills = extract_skills(text)

    # Calculate parse score
    score = 0.0
    if chunk.job_title:
        score += 0.15
    if chunk.skills:
        score += 0.25
    if chunk.responsibilities:
        score += 0.2
    if chunk.requirements:
        score += 0.2
    if chunk.location:
        score += 0.1
    if chunk.experience_min_years is not None:
        score += 0.1
    chunk.parse_score = min(score, 1.0)
    chunk.parse_method = "regex+classify"

    # If parse score is low, mark for LLM enhancement
    if chunk.parse_score < 0.5:
        chunk.parse_method = "regex+classify+needs_llm"

    logger.info(f"JD parsed: title='{chunk.job_title}', skills={len(chunk.skills)}, score={chunk.parse_score}")
    return chunk
