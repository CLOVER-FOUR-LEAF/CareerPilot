from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    APP_NAME: str = "resume-analyzer-agent"
    APP_PORT: int = 8003

    OPENAI_API_KEY: str = ""
    OPENAI_API_BASE: str = "https://api.openai.com/v1"
    OPENAI_MODEL: str = "gpt-4o"
    OPENAI_TEMPERATURE: float = 0.3

    class Config:
        env_file = ".env"


settings = Settings()
