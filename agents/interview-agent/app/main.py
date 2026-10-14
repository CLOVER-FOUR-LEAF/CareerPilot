"""
Interview Agent - FastAPI service
面试出题与回答评价，契约 §7.5。
"""
import logging
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from . import llm
from .config import settings
from .interviewer import (
    InterviewEvaluation,
    InterviewQuestion,
    evaluate_answer,
    generate_question,
)

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
    title="CareerPilot Interview Agent",
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

class QuestionRequest(BaseModel):
    round_no: int = 1
    total_rounds: int = 3
    jd_skills: list[str] = Field(default_factory=list)
    resume_highlights: list[str] = Field(default_factory=list)
    previous_answer: Optional[str] = None
    job_title: str = "该岗位"


class EvaluateRequest(BaseModel):
    question: str
    answer: str
    jd_skills: list[str] = Field(default_factory=list)


# ─── 端点 ─────────────────────────────────────────────────────

@app.get("/health")
async def health():
    return {"status": "ok", "service": settings.APP_NAME}


@app.post("/api/question", response_model=InterviewQuestion)
async def question_endpoint(req: QuestionRequest):
    """出题。带 previous_answer 时生成针对其 STAR 短板的追问"""
    if req.round_no < 1:
        raise HTTPException(status_code=400, detail="round_no must be >= 1")
    if req.total_rounds < 1:
        raise HTTPException(status_code=400, detail="total_rounds must be >= 1")
    if req.round_no > req.total_rounds:
        raise HTTPException(
            status_code=400,
            detail="round_no cannot exceed total_rounds",
        )

    try:
        result = generate_question(
            round_no=req.round_no,
            total_rounds=req.total_rounds,
            jd_skills=req.jd_skills,
            resume_highlights=req.resume_highlights,
            previous_answer=req.previous_answer,
            job_title=req.job_title,
        )
    except Exception as exc:
        logger.error(f"Question generation failed: {exc}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    logger.info(f"question r{req.round_no}/{req.total_rounds} type={result.question_type}")
    return result


@app.post("/api/evaluate", response_model=InterviewEvaluation)
async def evaluate_endpoint(req: EvaluateRequest):
    """按 STAR 完整度评价面试回答"""
    if not req.question or not req.question.strip():
        raise HTTPException(status_code=400, detail="question cannot be empty")
    if req.answer is None or not req.answer.strip():
        raise HTTPException(status_code=400, detail="answer cannot be empty")

    try:
        result = evaluate_answer(req.question, req.answer, req.jd_skills)
    except Exception as exc:
        logger.error(f"Answer evaluation failed: {exc}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    logger.info(f"evaluation score={result.score} star={result.star_coverage.model_dump()}")
    return result
