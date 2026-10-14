"""
Match Agent - FastAPI service
岗位与简历匹配打分（语义 + 关键词 + 业务三维），契约 §7.3。
"""
import logging
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from . import llm
from .config import settings
from .matcher import MatchGap, MatchResult, match_resume_jd

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
    title="CareerPilot Match Agent",
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

class MatchRequest(BaseModel):
    jd_text: str
    resume_text: str
    jd_skills: Optional[list[str]] = None


# ─── 端点 ─────────────────────────────────────────────────────

@app.get("/health")
async def health():
    return {"status": "ok", "service": settings.APP_NAME}


async def _polish_suggestions(result: MatchResult) -> MatchResult:
    """
    可选的 LLM 增强：只润色 gap 的建议文案，绝不改动任何分数。
    任何失败都静默降级到规则引擎的原始建议。
    """
    if not llm.llm_enabled() or not result.gap_list:
        return result

    listing = "\n".join(
        f"{i + 1}. [{g.gap_type}/{g.severity}] {g.gap_content}（原建议：{g.suggestion}）"
        for i, g in enumerate(result.gap_list)
    )
    system_prompt = (
        "你是求职顾问。请把每条差距的补充建议改写得更具体、可执行，"
        "每条 30-60 字，必须给出具体可落地的动作。"
        "严格按 '序号|建议内容' 每行一条输出，不要输出任何解释性文字。"
    )
    answer = await llm.complete(system_prompt, f"岗位差距如下：\n{listing}")
    if not answer:
        return result

    lines = [line.strip() for line in answer.splitlines() if "|" in line]
    if len(lines) != len(result.gap_list):
        logger.warning("LLM 建议条数不匹配（期望 %d，实际 %d），保留规则引擎结果",
                       len(result.gap_list), len(lines))
        return result

    polished = [
        MatchGap(
            gap_type=gap.gap_type,
            gap_content=gap.gap_content,
            severity=gap.severity,
            suggestion=line.split("|", 1)[1].strip(),
        )
        for gap, line in zip(result.gap_list, lines)
    ]
    result.gap_list = polished
    return result


@app.post("/api/match", response_model=MatchResult)
async def match_endpoint(req: MatchRequest):
    """对 JD 与简历进行三维匹配打分"""
    if not req.jd_text or not req.jd_text.strip():
        raise HTTPException(status_code=400, detail="jd_text cannot be empty")
    if not req.resume_text or not req.resume_text.strip():
        raise HTTPException(status_code=400, detail="resume_text cannot be empty")

    try:
        result = match_resume_jd(req.jd_text, req.resume_text, req.jd_skills)
    except Exception as exc:
        logger.error(f"Match scoring failed: {exc}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    try:
        result = await _polish_suggestions(result)
    except Exception as exc:   # 兜底：LLM 增强永远不能影响主结果
        logger.warning(f"LLM suggestion polishing skipped: {exc}")

    logger.info(
        f"final={result.final_score} (semantic={result.semantic_score}, "
        f"keyword={result.keyword_score}, business={result.business_score}) "
        f"gaps={len(result.gap_list)}"
    )
    return result
