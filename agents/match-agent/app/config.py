from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """应用配置"""
    APP_NAME: str = "match-agent"
    APP_PORT: int = 8004

    # LLM（为空时纯规则引擎，不发起任何外部调用）
    OPENAI_API_KEY: str = ""
    OPENAI_API_BASE: str = "https://api.openai.com/v1"
    OPENAI_MODEL: str = "gpt-4o"
    OPENAI_TEMPERATURE: float = 0.2
    LLM_TIMEOUT_SECONDS: float = 30.0

    class Config:
        env_file = ".env"


settings = Settings()
