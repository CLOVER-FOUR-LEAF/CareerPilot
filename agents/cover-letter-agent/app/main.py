"""
Cover Letter Agent - FastAPI service
中文求职信生成，契约 §7.6。
"""
import logging
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from . import llm
from .config import settings
from .writer import CoverLetter, generate_cover_letter

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(
        f"{settings.APP_NAME} starting on port {settings.APP_PORT} "
        f"(LLM {'enabled' if llm.llm_enabled() else 'disabled, rules-only'})"
    )
    yield
    logger.info("Shutting down")


app = FastAPI(
    title="CareerPilot Cover Letter Agent",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ─── 请求 / 响应模型 ───────────────────────────────────────────

class GenerateRequest(BaseModel):
    jd_skills: list[str] = Field(default_factory=list)
    resume_highlights: list[str] = Field(default_factory=list)
    company_name: str = "贵司"
    job_title: str = "该岗位"
    tone: str = "professional"
    company_info: Optional[str] = None


# ─── 端点 ─────────────────────────────────────────────────────

@app.get("/health")
async def health():
    return {"status": "ok", "service": settings.APP_NAME}


async def _polish_with_llm(letter: CoverLetter) -> CoverLetter:
    """
    可选的 LLM 增强：只润色措辞，保持结构、语气与字数区间不变。
    失败即返回模板生成的原结果。
    """
    if not llm.llm_enabled():
        return letter

    system_prompt = (
        "你是中文求职信润色专家。保持原文的分段结构、语气与事实不变，"
        "只让措辞更自然流畅，总字数保持在 300-450 字之间。"
        "直接输出润色后的全文，不要任何解释。"
    )
    answer = await llm.complete(system_prompt, letter.content)
    if not answer:
        return letter
    stripped = answer.strip()
    if len(stripped) < 100:
        return letter
    letter.content = stripped
    letter.word_count = len(stripped)
    return letter


@app.post("/api/generate", response_model=CoverLetter)
async def generate_endpoint(req: GenerateRequest):
    """生成中文求职信"""
    if not req.job_title or not req.job_title.strip():
        raise HTTPException(status_code=400, detail="job_title cannot be empty")
    if req.tone not in ("professional", "passionate", "humble"):
        raise HTTPException(
            status_code=400,
            detail="tone must be one of: professional, passionate, humble",
        )

    try:
        letter = generate_cover_letter(
            jd_skills=req.jd_skills,
            resume_highlights=req.resume_highlights,
            company_name=req.company_name,
            job_title=req.job_title,
            tone=req.tone,
            company_info=req.company_info,
        )
    except Exception as exc:
        logger.error(f"Cover letter generation failed: {exc}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    try:
        letter = await _polish_with_llm(letter)
    except Exception as exc:
        logger.warning(f"LLM polishing skipped: {exc}")

    logger.info(f"cover letter tone={letter.tone} words={letter.word_count}")
    return letter
