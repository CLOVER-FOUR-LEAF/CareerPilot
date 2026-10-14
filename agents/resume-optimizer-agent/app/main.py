"""
Resume Optimizer Agent - FastAPI service
单条简历要点的 STAR 结构化改写，契约 §7.4。
"""
import logging
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from . import llm
from .config import settings
from .optimizer import ResumeOptimizeResult, optimize_bullet

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
    title="CareerPilot Resume Optimizer Agent",
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

class OptimizeRequest(BaseModel):
    bullet: str
    jd_skills: list[str] = Field(default_factory=list)
    tone: str = "professional"


# ─── 端点 ─────────────────────────────────────────────────────

@app.get("/health")
async def health():
    return {"status": "ok", "service": settings.APP_NAME}


async def _polish_with_llm(result: ResumeOptimizeResult) -> ResumeOptimizeResult:
    """
    可选的 LLM 增强：只润色改写句的表达，不改变 STAR 结构与注入技能。
    失败即返回规则引擎的原结果。
    """
    if not llm.llm_enabled():
        return result

    system_prompt = (
        "你是资深简历顾问。把下面这句话改写得更专业有力，保持单句、保持 STAR 逻辑，"
        "保留其中的【】占位符，不要新增事实，不要编造数字。只输出改写后的这一句话。"
    )
    answer = await llm.complete(system_prompt, f"原句：{result.rewritten}")
    if not answer:
        return result

    cleaned = answer.strip().splitlines()[0].strip() if answer.strip() else ""
    if len(cleaned) < 10:
        return result
    result.rewritten = cleaned
    result.change_note += "；已用 LLM 润色表达"
    return result


@app.post("/api/optimize", response_model=ResumeOptimizeResult)
async def optimize_endpoint(req: OptimizeRequest):
    """把一条简历要点改写成 STAR 结构"""
    if not req.bullet or not req.bullet.strip():
        raise HTTPException(status_code=400, detail="bullet cannot be empty")
    if req.tone not in ("professional", "passionate", "humble"):
        raise HTTPException(
            status_code=400,
            detail="tone must be one of: professional, passionate, humble",
        )

    try:
        result = optimize_bullet(req.bullet, req.jd_skills, req.tone)
    except Exception as exc:
        logger.error(f"Resume optimize failed: {exc}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    try:
        result = await _polish_with_llm(result)
    except Exception as exc:
        logger.warning(f"LLM polishing skipped: {exc}")

    return result
