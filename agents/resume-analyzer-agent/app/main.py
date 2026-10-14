"""
Resume Analyzer Agent - FastAPI service
ATS-style resume scoring with 7 dimensions + improvement suggestions.
"""
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from .config import settings
from .analyzer import analyze_resume, AnalysisResult

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(f"{settings.APP_NAME} starting on port {settings.APP_PORT}")
    yield
    logger.info("Shutting down")


app = FastAPI(
    title="CareerPilot Resume Analyzer Agent",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class AnalyzeRequest(BaseModel):
    resume_text: str
    resume_id: str = ""


class AnalyzeResponse(BaseModel):
    resume_id: str = ""
    result: AnalysisResult


@app.get("/health")
async def health():
    return {"status": "ok", "service": settings.APP_NAME}


@app.post("/api/analyze")
async def analyze_resume_endpoint(req: AnalyzeRequest):
    """Analyze a resume and return ATS-style scoring"""
    if not req.resume_text or not req.resume_text.strip():
        raise HTTPException(status_code=400, detail="resume_text cannot be empty")

    try:
        result = analyze_resume(req.resume_text)
        result.resume_id = req.resume_id
        return {
            "resume_id": req.resume_id,
            "result": result.model_dump(),
        }
    except Exception as e:
        logger.error(f"Resume analysis failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))
