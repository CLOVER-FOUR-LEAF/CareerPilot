"""
Interview Agent - FastAPI service
面试出题与回答评价，契约 §7.5。

另承载 HR 侧能力（recruiter 模式）：
- /api/hr/insight   归因 + 验证计划（L2 + L3）
- /api/hr/calibrate JD 校准（F4）
- /api/hr/verify    回答覆盖判定（F3）

放在这个 Agent 而不是新建进程，是因为计划书的硬约束 C1：
本期不新增 Java 微服务、不新增 Python Agent 进程。
"""
import logging
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from . import llm
from .config import settings
from .hr.attribution import (
    GapAttribution,
    attribute_gaps,
    build_capability_coverage,
)
from .hr.calibration import JdCalibration, calibrate_jd
from .hr.report import HrInsightReport, build_report
from .hr.verdict import CoverageVerdict, judge_answer
from .hr.verification import VerificationQuestion, build_verification_plan
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


# ─── HR 侧（recruiter 模式）────────────────────────────────────────

class HrInsightRequest(BaseModel):
    """
    HR 洞察报告的输入。

    gaps 由上游（编排层）跑 match-agent 得到后传入 ——
    归因层需要的是「已经算好的差距」，不是原始文本。
    """

    jd_text: str = ""
    resume_text: str = ""
    jd_skills: list[str] = Field(default_factory=list)
    gaps: list[dict] = Field(default_factory=list)
    match_result: Optional[dict] = None
    max_questions: int = 12


class HrCalibrateRequest(BaseModel):
    """JD 校准输入。"""

    jd_text: str
    job_title: str = ""


class HrVerifyRequest(BaseModel):
    """
    回答判定输入。

    question 带完整的 signals 与 probes，判定才有依据。
    """

    question: VerificationQuestion
    answer: str
    round_no: int = 1


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


# ─── HR 侧端点 ─────────────────────────────────────────────────

@app.post("/api/hr/insight", response_model=HrInsightReport)
async def hr_insight_endpoint(req: HrInsightRequest):
    """
    生成 HR 洞察报告：归因（L2）+ 验证计划（L3）。

    规则层负责所有判定（维度、层级、问题结构），
    LLM 只用于润色文案，失败自动降级到规则版本。
    """
    if not req.gaps and not req.jd_text:
        raise HTTPException(status_code=400, detail="gaps 与 jd_text 不能同时为空")

    try:
        attributions = await attribute_gaps(
            req.gaps,
            jd_text=req.jd_text,
            resume_text=req.resume_text,
            jd_skills=req.jd_skills,
        )
        coverage = build_capability_coverage(attributions)
        plan = await build_verification_plan(
            attributions,
            jd_text=req.jd_text,
            resume_text=req.resume_text,
            max_questions=max(1, min(req.max_questions, 30)),
        )
    except Exception as exc:
        logger.error(f"HR insight generation failed: {exc}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    report = build_report(
        match_result=req.match_result,
        attributions=attributions,
        verification_plan=plan,
        capability_coverage=coverage,
    )
    logger.info(
        f"hr_insight gaps={report.gap_count} dimensions={report.dimension_count} "
        f"questions={report.question_count}"
    )
    return report


@app.post("/api/hr/calibrate", response_model=JdCalibration)
async def hr_calibrate_endpoint(req: HrCalibrateRequest):
    """JD 反向校准：能力模型 / 硬门槛 / 过度筛选 / 模糊表述"""
    if not req.jd_text or not req.jd_text.strip():
        raise HTTPException(status_code=400, detail="jd_text cannot be empty")

    try:
        result = await calibrate_jd(req.jd_text, req.job_title)
    except Exception as exc:
        logger.error(f"JD calibration failed: {exc}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    logger.info(
        f"jd_calibrate gates={len(result.hard_gates)} "
        f"over_filtering={len(result.over_filtering_signals)} "
        f"vague={len(result.vague_phrases)}"
    )
    return result


@app.post("/api/hr/verify", response_model=CoverageVerdict)
async def hr_verify_endpoint(req: HrVerifyRequest):
    """
    对一段回答给出覆盖判定（F3）。

    规则优先，LLM 兜底；LLM 返回的引用必须能在回答里定位，
    否则整条结论不采信（R3）。
    """
    if req.answer is None or not req.answer.strip():
        raise HTTPException(status_code=400, detail="answer cannot be empty")

    try:
        result = await judge_answer(req.question, req.answer, req.round_no)
    except Exception as exc:
        logger.error(f"HR verify failed: {exc}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    logger.info(
        f"hr_verify {result.vq_id} coverage={result.coverage} "
        f"confidence={result.confidence} probe={result.should_probe}"
    )
    return result
