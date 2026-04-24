from __future__ import annotations

import logging
from functools import partial
from typing import Literal

from langchain_openai import ChatOpenAI
from langgraph.graph import StateGraph, END
from langgraph.graph.state import CompiledStateGraph
from langgraph.types import interrupt
from qdrant_client import AsyncQdrantClient

from app.api.db import get_db_session
from app.models.state import AgentState, IntentTypes
from app.nodes.classify import classify_node
from app.nodes.execute_tools import execute_tools_node
from app.nodes.generate import generate_node


log = logging.getLogger(__name__)


def route_after_classify(state: AgentState) -> Literal["human_review", "execute_tools"]:
    if state.intent in IntentTypes.REQUIRES_APPROVAL and state.approved is None:
        log.debug("[Graph] route → human_review (intent=%s)", state.intent)
        return "human_review"
    log.debug("[Graph] route → execute_tools (intent=%s)", state.intent)
    return "execute_tools"


def route_after_review(state: AgentState) -> Literal["execute_tools", "generate"]:
    if state.approved:
        return "execute_tools"
    return "generate"


def human_review_node(state: AgentState) -> dict:
    log.info("[Graph] Interrupting for human review: intent=%s room=%s",
             state.intent, state.room_number)

    result = interrupt({
        "intent": state.intent,
        "room_number": state.room_number,
        "action": state.room_control_action or state.delivery_item or state.service_action,
        "detail": state.room_control_value or state.service_detail,
        "message": "请确认是否执行该操作",
    })

    log.info("[HumanReview] result=%s type=%s", result, type(result))
    if isinstance(result, dict):
        approved = result.get("approved")
    else:
        approved = bool(result)
    log.info("[HumanReview] approved=%s", approved)
    return {"approved": approved}


# ── 创建StateGraph ─────────────────────────────────────────────────────────────────────

def build_graph(llm: ChatOpenAI, qdrant: AsyncQdrantClient, checkpointer) -> CompiledStateGraph:
    graph_builder = StateGraph(AgentState)

    # 添加节点
    graph_builder.add_node("classify", partial(_classify_wrapper, llm=llm))
    graph_builder.add_node("human_review", human_review_node)
    graph_builder.add_node("execute_tools", partial(_execute_tools_wrapper, qdrant=qdrant))
    graph_builder.add_node("generate", partial(_generate_wrapper, llm=llm))

    # 添加入口点
    graph_builder.set_entry_point("classify")

    # 添加条件边
    graph_builder.add_conditional_edges(
        "classify",
        route_after_classify,
        {
            "human_review": "human_review",
            "execute_tools": "execute_tools",
        }
    )
    graph_builder.add_conditional_edges(
        "human_review",
        route_after_review,
        {
            "execute_tools": "execute_tools",
            "generate": "generate",
        }
    )

    # 线性边
    graph_builder.add_edge("execute_tools", "generate")
    graph_builder.add_edge("generate", END)

    return graph_builder.compile(checkpointer=checkpointer, interrupt_before=["human_review"])


# ── 节点 wrapper（同步/异步适配）─────────────────────────────────────────────

def _classify_wrapper(state: AgentState, llm: ChatOpenAI) -> dict:
    return classify_node(state, llm)


async def _execute_tools_wrapper(state: AgentState, qdrant) -> dict:
    async with get_db_session() as db:
        if "approved" not in state:
            state.approved = None
        return await execute_tools_node(state, db, qdrant)


async def _generate_wrapper(state: AgentState, llm: ChatOpenAI) -> dict:
    return await generate_node(state, llm)
