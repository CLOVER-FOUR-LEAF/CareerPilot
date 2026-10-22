from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """应用配置"""
    APP_NAME: str = "career-advisor-agent"
    APP_PORT: int = 8008

    # LLM（为空时纯规则引擎，不发起任何外部调用）
    OPENAI_API_KEY: str = ""
    OPENAI_API_BASE: str = "https://api.openai.com/v1"
    OPENAI_MODEL: str = "gpt-4o"
    OPENAI_TEMPERATURE: float = 0.6
    LLM_TIMEOUT_SECONDS: float = 30.0
    # Prompt 版本号：改动 system prompt 时递增，便于事后对照线上输出
    LLM_PROMPT_VERSION: str = "v1"

    # 检索条数（契约要求 top-8 进入上下文）
    RETRIEVE_TOP_K: int = 8

    class Config:
        env_file = ".env"


settings = Settings()
