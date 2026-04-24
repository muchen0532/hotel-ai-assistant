"""
Integration tests for the LangGraph pipeline.
使用 MemorySaver 替代 PostgresSaver，mock DB 和 Qdrant，无需真实外部依赖。
"""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from langgraph.checkpoint.memory import MemorySaver
from langgraph.types import Command

from app.models.state import AgentState, IntentTypes
from app.graph.pipeline import build_graph


# ── Fixtures ──────────────────────────────────────────────────────────────────

def _mock_llm(response_text: str = "好的，已为您处理。"):
    llm = MagicMock()
    llm.invoke.return_value = MagicMock(content=response_text)
    llm.ainvoke = AsyncMock(return_value=MagicMock(content=response_text))
    return llm


def _mock_qdrant():
    qdrant = AsyncMock()
    # searchAttractionNames / searchRestaurantNames 返回空列表
    qdrant.query_points = AsyncMock(
        return_value=MagicMock(points=[])
    )
    return qdrant


def _mock_db_session():
    """模拟 AsyncSession，execute 返回空结果集。"""
    session = AsyncMock()
    empty_result = MagicMock(
        mappings=MagicMock(return_value=MagicMock(
            first=MagicMock(return_value=None)
        )),
        fetchall=MagicMock(return_value=[]),
    )
    session.execute = AsyncMock(return_value=empty_result)
    session.commit = AsyncMock()
    return session


def _build_graph(llm=None):
    """构建测试用 graph，注入 MemorySaver 和 mock 依赖。"""
    return build_graph(
        llm=llm or _mock_llm(),
        qdrant=_mock_qdrant(),
        checkpointer=MemorySaver(),
    )


def _config(session_id: str):
    return {"configurable": {"thread_id": session_id}}


def _base_state(session_id: str, message: str, room: str = "1088") -> dict:
    return {
        "hotel_id": "nexstay",
        "session_id": session_id,
        "user_message": message,
        "hotel_name": "NEXSTAY 智宿酒店",
        "hotel_location": "中国·广州",
        "hotel_locale": "zh-CN",
        "hotel_welcome_chips": [],
        "guest_name": "测试用户",
        "room_number": room,
    }


@pytest.fixture(autouse=True)
def mock_db():
    """
    所有测试自动 mock get_db_session，
    避免 _execute_tools_wrapper 尝试连接真实 PG。
    """
    session = _mock_db_session()
    mock_cm = MagicMock()
    mock_cm.__aenter__ = AsyncMock(return_value=session)
    mock_cm.__aexit__ = AsyncMock(return_value=False)

    with patch("app.graph.pipeline.get_db_session", return_value=mock_cm):
        yield session


# ── 基础流程 ──────────────────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_faq_wifi():
    """FAQ 意图：规则命中 → execute_tools → generate，全程不 interrupt。"""
    graph = _build_graph()
    config = _config("t-faq-wifi")
    result = await graph.ainvoke(_base_state("t-faq-wifi", "WiFi密码是多少？"), config=config)

    assert result["intent"] == IntentTypes.FAQ
    assert result["faq_category"] == "wifi"
    assert result["response_text"]

    # 图已正常结束，没有挂起
    state = await graph.aget_state(config)
    assert not state.next


@pytest.mark.asyncio
async def test_general_llm_fallback():
    """general 意图：不调工具，直接 LLM 生成。"""
    llm = _mock_llm("您好，有什么可以帮助您的？")
    graph = _build_graph(llm=llm)
    config = _config("t-general")
    result = await graph.ainvoke(_base_state("t-general", "你好"), config=config)

    assert result["intent"] == IntentTypes.GENERAL
    assert result["response_text"] == "您好，有什么可以帮助您的？"


@pytest.mark.asyncio
async def test_attractions_no_interrupt():
    """attractions 意图：不需要审批，直接执行工具。"""
    graph = _build_graph()
    config = _config("t-attractions")
    result = await graph.ainvoke(
        _base_state("t-attractions", "附近有什么好玩的"), config=config
    )

    assert result["intent"] == IntentTypes.ATTRACTIONS
    assert result["response_text"]

    state = await graph.aget_state(config)
    assert not state.next


# ── Human-in-the-loop ─────────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_delivery_interrupts():
    """送物请求：classify 后应在 human_review 节点挂起。"""
    graph = _build_graph()
    config = _config("t-delivery-interrupt")

    await graph.ainvoke(_base_state("t-delivery-interrupt", "帮我送两瓶矿泉水"), config=config)

    state = await graph.aget_state(config)
    assert "human_review" in (state.next or [])
    assert state.values["intent"] == IntentTypes.DELIVERY
    assert state.values["delivery_item"] == "water"
    assert state.values.get("approved") is None


@pytest.mark.asyncio
async def test_room_control_interrupts():
    """客控请求：同样应该挂起等待审批。"""
    graph = _build_graph()
    config = _config("t-room-interrupt")

    await graph.ainvoke(_base_state("t-room-interrupt", "把空调调到22度"), config=config)

    state = await graph.aget_state(config)
    assert "human_review" in (state.next or [])
    assert state.values["intent"] == IntentTypes.ROOM_CONTROL
    assert state.values["room_control_action"] == "ac"


@pytest.mark.asyncio
async def test_delivery_approve():
    """审批通过：resume 后执行工具，生成确认回复。"""
    graph = _build_graph()
    config = _config("t-delivery-approve")

    # 第一次 invoke → interrupt
    await graph.ainvoke(_base_state("t-delivery-approve", "帮我送条毛巾"), config=config)
    assert "human_review" in (await graph.aget_state(config)).next

    # 审批通过 → resume
    result = await graph.ainvoke(Command(resume={"approved": True}), config=config)

    assert result.get("response_text")
    assert "毛巾" in result["response_text"]  # 确认回复包含物品名
    assert "送达" in result["response_text"]  # 确认是送达回复而非拒绝回复

    state = await graph.aget_state(config)
    assert not state.next


@pytest.mark.asyncio
async def test_delivery_reject():
    graph = _build_graph(_mock_llm("拒绝回复"))
    config = _config("t-delivery-reject")

    await graph.ainvoke(_base_state("t-delivery-reject", "帮我加一张床"), config=config)
    result = await graph.ainvoke(Command(resume={"approved": False}), config=config)

    assert result.get("response_text")
    assert "送达" not in result["response_text"]
    assert result["response_text"] != "好的，已为您安排送加床到 1088 房间，预计 15 分钟内送达。"

    state = await graph.aget_state(config)
    assert not state.next


@pytest.mark.asyncio
async def test_concierge_wakeup_interrupts():
    """叫醒服务：礼宾类操作也应触发 interrupt。"""
    graph = _build_graph()
    config = _config("t-wakeup")

    await graph.ainvoke(_base_state("t-wakeup", "明早6点半叫醒我"), config=config)

    state = await graph.aget_state(config)
    assert "human_review" in (state.next or [])
    assert state.values["intent"] == IntentTypes.CONCIERGE
    assert state.values["service_action"] == "wakeup"


# ── Checkpointer 持久化 ───────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_history_persisted_across_turns():
    """
    同一 session_id 连续两轮对话，
    第二轮的 state 里应该有第一轮写入的 history。
    """
    graph = _build_graph()
    config = _config("t-history")

    # 第一轮
    await graph.ainvoke(_base_state("t-history", "早餐几点供应？"), config=config)

    # 第二轮（新消息，reset intent）
    second = {**_base_state("t-history", "泳池呢？"), "approved": None, "intent": None}
    result = await graph.ainvoke(second, config=config)

    assert result["response_text"]

    saved = await graph.aget_state(config)
    history = saved.values.get("history", [])
    # 至少应该有两轮（4条：user+assistant × 2）
    assert len(history) >= 2


@pytest.mark.asyncio
async def test_session_isolation():
    """不同 session_id 的状态互相隔离。"""
    graph = _build_graph()

    await graph.ainvoke(_base_state("t-iso-a", "WiFi密码？"), config=_config("t-iso-a"))
    await graph.ainvoke(_base_state("t-iso-b", "早餐时间？"), config=_config("t-iso-b"))

    state_a = await graph.aget_state(_config("t-iso-a"))
    state_b = await graph.aget_state(_config("t-iso-b"))

    assert state_a.values["faq_category"] == "wifi"
    assert state_b.values["faq_category"] == "breakfast"
