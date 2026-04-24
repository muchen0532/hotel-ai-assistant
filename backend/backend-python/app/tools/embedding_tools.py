from functools import lru_cache
from openai import AsyncOpenAI
from app.config import get_settings

_client = None


def get_jina_client() -> AsyncOpenAI:
    global _client
    if _client:
        return _client

    s = get_settings()
    _client = AsyncOpenAI(
        api_key=s.jina_api_key,
        base_url="https://api.jina.ai/v1"
    )
    return _client


async def embed(text: str) -> list[float]:
    client = get_jina_client()
    s = get_settings()

    resp = await client.embeddings.create(
        model=s.jina_model,
        input=text
    )
    return resp.data[0].embedding
