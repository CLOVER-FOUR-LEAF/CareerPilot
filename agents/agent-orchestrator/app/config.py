from typing import Optional

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """应用配置"""
    APP_NAME: str = "agent-orchestrator"
    APP_PORT: int = 8001

    # ── LLM（编排层本身不直接调用 LLM，保留配置以与其它 agent 保持一致）──
    OPENAI_API_KEY: str = ""
    OPENAI_API_BASE: str = "https://api.openai.com/v1"
    OPENAI_MODEL: str = "gpt-4o"
    OPENAI_TEMPERATURE: float = 0.3

    # ── 下游 Agent 地址（全部可通过环境变量覆盖）──
    JD_PARSER_URL: str = "http://localhost:8002"
    RESUME_ANALYZER_URL: str = "http://localhost:8003"
    MATCH_AGENT_URL: str = "http://localhost:8004"
    RESUME_OPTIMIZER_URL: str = "http://localhost:8005"
    INTERVIEW_AGENT_URL: str = "http://localhost:8006"
    COVER_LETTER_AGENT_URL: str = "http://localhost:8007"
    CAREER_ADVISOR_URL: str = "http://localhost:8008"

    # ── 下游调用超时与连接池 ──
    AGENT_TIMEOUT_SECONDS: float = 120.0
    AGENT_CONNECT_TIMEOUT_SECONDS: float = 10.0
    AGENT_MAX_CONNECTIONS: int = 50

    # ── 支持的任务类型 ──
    SUPPORTED_TASK_TYPES: tuple[str, ...] = (
        "jd_parse",
        "resume_analyze",
        "match_score",
        "resume_optimize",
        "interview",
        "cover_letter",
        "career_plan",
        # HR 侧（recruiter 模式，复用 interview-agent —— 约束 C1 不新增进程）
        "hr_insight",
        "jd_calibrate",
        "hr_verify",
    )

    REDIS_URL: str = "redis://localhost:6379/0"

    class Config:
        env_file = ".env"


settings = Settings()


def agent_url(task_type: str) -> Optional[str]:
    """按任务类型返回下游 Agent 基地址；不支持的类型返回 None"""
    mapping: dict[str, str] = {
        "jd_parse": settings.JD_PARSER_URL,
        "resume_analyze": settings.RESUME_ANALYZER_URL,
        "match_score": settings.MATCH_AGENT_URL,
        "resume_optimize": settings.RESUME_OPTIMIZER_URL,
        "interview": settings.INTERVIEW_AGENT_URL,
        "cover_letter": settings.COVER_LETTER_AGENT_URL,
        "career_plan": settings.CAREER_ADVISOR_URL,
    }
    return mapping.get(task_type)
