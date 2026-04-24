from __future__ import annotations

import logging
from functools import lru_cache
from typing import AsyncContextManager, Optional

from fastapi import Depends
from langchain_openai import ChatOpenAI
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from qdrant_client import AsyncQdrantClient

from app.config import get_settings
from app.graph.pipeline import build_graph

log = logging.getLogger(__name__)


@lru_cache
def get_llm() -> ChatOpenAI:
    s = get_settings()
    return ChatOpenAI(
        model=s.deepseek_model,
        base_url=s.deepseek_base_url,
        api_key=s.deepseek_api_key,
        temperature=0.3,
        streaming=True,
    )


@lru_cache
def get_qdrant() -> AsyncQdrantClient:
    s = get_settings()
    return AsyncQdrantClient(host=s.qdrant_host, port=s.qdrant_port)


_graph_instance = None
_checkpointer_cm: Optional[AsyncContextManager] = None


async def init_graph():
    global _graph_instance, _checkpointer_cm
    s = get_settings()
    _checkpointer_cm = AsyncPostgresSaver.from_conn_string(s.langgraph_checkpointer_url)
    checkpointer = await _checkpointer_cm.__aenter__()
    await checkpointer.setup()
    _graph_instance = build_graph(get_llm(), get_qdrant(), checkpointer)
    log.info("[Graph] Initialized with PostgresSaver")


async def cleanup_graph():
    if _checkpointer_cm:
        await _checkpointer_cm.__aexit__(None, None, None)


def get_graph():
    if _graph_instance is None:
        raise RuntimeError("Graph not initialized, check startup logs")
    return _graph_instance


async def get_session_state(session_id: str, graph=Depends(get_graph)):
    config = {"configurable": {"thread_id": session_id}}
    return await graph.aget_state(config)
