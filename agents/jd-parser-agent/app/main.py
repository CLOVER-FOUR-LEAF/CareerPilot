"""
JD Parser Agent - FastAPI service
Parses raw job descriptions into structured data.
"""
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from .config import settings
from .parser import parse_jd, JdChunk

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(f"{settings.APP_NAME} starting on port {settings.APP_PORT}")
    yield
    logger.info("Shutting down")


app = FastAPI(
    title="CareerPilot JD Parser Agent",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class ParseRequest(BaseModel):
    jd_text: str
    jd_id: str = ""


class ParseResponse(BaseModel):
    jd_id: str = ""
    result: JdChunk
    parse_score: float


@app.get("/health")
async def health():
    return {"status": "ok", "service": settings.APP_NAME}


@app.post("/api/parse")
async def parse_jd_endpoint(req: ParseRequest):
    """Parse a raw JD text into structured data"""
    if not req.jd_text or not req.jd_text.strip():
        raise HTTPException(status_code=400, detail="jd_text cannot be empty")

    try:
        result = parse_jd(req.jd_text)
        return {
            "jd_id": req.jd_id,
            "result": result.model_dump(),
            "parse_score": result.parse_score,
        }
    except Exception as e:
        logger.error(f"JD parse failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))
