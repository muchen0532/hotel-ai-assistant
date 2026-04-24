from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # LLM
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com"
    deepseek_model: str = "deepseek-chat"

    # Embedding
    jina_api_key: str = ""
    jina_model: str = "jina-embeddings-v3"

    # PostgreSQL
    database_url: str = "postgresql+asyncpg://postgres:postgres@localhost:5432/hotel_ai"

    # Qdrant
    qdrant_host: str = "localhost"
    qdrant_port: int = 6334
    qdrant_score_cutoff: float = 0.3

    langgraph_checkpointer_url: str = "postgresql://postgres:postgres@localhost:5432/hotel_ai"

    port: int = 8080


@lru_cache
def get_settings() -> Settings:
    return Settings()
