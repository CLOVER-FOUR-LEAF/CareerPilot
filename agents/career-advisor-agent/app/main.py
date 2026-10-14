"""
Career Advisor Agent - FastAPI service
基于 BM25 检索的职业发展规划，契约 §7.7。
"""
import logging
import sys
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Optional

# 以仓库根目录（agents/career-advisor-agent）为导入基准，
# 让 `data.knowledge_base` 这个顶层包在 uvicorn / 直接运行两种方式下都能被找到。
_AGENT_ROOT = str(Path(__file__).resolve().parent.parent)
if _AGENT_ROOT not in sys.path:
    sys.path.insert(0, _AGENT_ROOT)

from fastapi import FastAPI, HTTPException           # noqa: E402
from fastapi.middleware.cors import CORSMiddleware   # noqa: E402
from pydantic import BaseModel, Field                # noqa: E402

from . import llm                                    # noqa: E402
from .advisor import CareerPlan, build_career_plan    # noqa: E402
from .config import settings                          # noqa: E402

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
    title="CareerPilot Career Advisor Agent",
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

class PlanRequest(BaseModel):
    current_title: str = ""
    target_industry: str = ""
    target_position: str = ""
    years_of_experience: int = 0
    skills: list[str] = Field(default_factory=list)
    question: Optional[str] = None


# ─── 端点 ─────────────────────────────────────────────────────

@app.get("/health")
async def health():
    return {"status": "ok", "service": settings.APP_NAME}


@app.post("/api/plan", response_model=CareerPlan)
async def plan_endpoint(req: PlanRequest):
    """生成职业发展规划"""
    if not req.target_industry or not req.target_industry.strip():
        raise HTTPException(status_code=400, detail="target_industry cannot be empty")
    if not req.target_position or not req.target_position.strip():
        raise HTTPException(status_code=400, detail="target_position cannot be empty")
    if req.years_of_experience < 0 or req.years_of_experience > 50:
        raise HTTPException(
            status_code=400,
            detail="years_of_experience must be between 0 and 50",
        )

    try:
        plan = build_career_plan(
            current_title=req.current_title,
            target_industry=req.target_industry,
            target_position=req.target_position,
            years_of_experience=req.years_of_experience,
            skills=req.skills,
            question=req.question,
            top_k=settings.RETRIEVE_TOP_K,
        )
    except Exception as exc:
        logger.error(f"Career plan generation failed: {exc}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    # 不做 LLM 改写：路线图的 doc_ids 是可校验的硬证据，
    # 一旦交给模型改写就可能出现引用与内容对不上的情况。LLM 仅保留给未来扩展。
    logger.info(
        f"career plan phases={len(plan.roadmap)} "
        f"citations={len(plan.citations)} skill_gap={len(plan.skill_gap)}"
    )
    return plan
