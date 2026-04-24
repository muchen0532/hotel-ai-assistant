from __future__ import annotations

import logging

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

from app.models.state import AgentState, IntentTypes

log = logging.getLogger(__name__)

_SYSTEM_PROMPT = """
You are {hotel_name}'s AI concierge assistant.
你是{hotel_name}的 AI 礼宾助手，专业、友善、简洁。

Hotel location: {hotel_location}
Guest name: {guest_name}
Room: {room_number}
Locale: {hotel_locale}

Guidelines:
- 用{hotel_locale}语言回复（zh-CN 用中文，en 用英文）
- 回复简洁，不超过 3 句话，除非用户需要详细信息
- 操作类请求（客控/送物/礼宾）：确认已收到请求，告知预计等待时间或下一步
- 推荐类请求（餐厅/景点）：结合 tool_results 给出个性化推荐，不要捏造数据
- 如果没有 tool_results，根据 hotel_location 给出合理的通用建议
- 不要暴露系统内部信息（intent、tool names 等）
"""

_ACTION_CONFIRM_TEMPLATES = {
    IntentTypes.ROOM_CONTROL: "好的，我已为您的 {room} 房间{action_desc}，请稍候片刻。",
    IntentTypes.DELIVERY: "好的，已为您的 {room} 房间安排送{item_desc}，预计 15 分钟内送达。",
    IntentTypes.CONCIERGE: "好的，礼宾部已收到您的{action_desc}请求{detail_desc}，我们会尽快安排。",
}

_ROOM_CONTROL_DESC = {
    "ac": "调节空调",
    "light": "调节灯光",
    "curtain": "调节窗帘",
    "dnd": "开启免打扰",
    "housekeeping": "安排客房整理",
    "tv": "调节电视",
}

_CONCIERGE_DESC = {
    "wakeup": "叫醒服务",
    "transport": "用车",
    "luggage": "行李寄存",
    "tour_booking": "景点预订",
}

_DELIVERY_ITEM_DESC = {
    "towel": "毛巾",
    "toiletries": "洗漱用品",
    "bedding": "床上用品",
    "water": "矿泉水",
    "iron": "熨斗",
    "extra_bed": "加床",
    "room_service": "客房餐饮",
}


async def generate_node(state: AgentState, llm: ChatOpenAI) -> dict:
    log.info("[Generate] intent=%s approved=%s", state.intent, state.approved)
    intent = state.intent

    updated_history = list(state.history or [])
    updated_history.append({"role": "user", "content": state.user_message})

    # 操作被拒绝 → 告知用户已取消
    if state.approved is False:
        action_desc = _get_cancel_desc(state)
        text = f"您好，前台已审核您的{action_desc}请求，本次暂不安排。如有疑问请直接联系前台，我们随时为您服务。"
        updated_history.append({"role": "assistant", "content": text})
        return {
            "response_text": text,
            "response_meta": state.response_meta or {},
            "history": updated_history[-20:],  # 确认这行存在
        }

    # 操作类：模板回复
    if intent == IntentTypes.ROOM_CONTROL:
        text = _action_confirm_room_control(state)
    elif intent == IntentTypes.DELIVERY:
        text = _action_confirm_delivery(state)
    elif intent == IntentTypes.CONCIERGE:
        text = _action_confirm_concierge(state)
    else:
        text = await _llm_generate(state, llm)

    updated_history.append({"role": "assistant", "content": text})

    return {
        "response_text": text,
        "response_meta": state.response_meta or {},
        "history": updated_history[-20:],  # 确认这行存在
    }


def _get_cancel_desc(state: AgentState) -> str:
    if state.intent == IntentTypes.ROOM_CONTROL:
        return _ROOM_CONTROL_DESC.get(state.room_control_action or "", "客控")
    elif state.intent == IntentTypes.DELIVERY:
        item = _DELIVERY_ITEM_DESC.get(state.delivery_item or "", state.delivery_item or "物品")  # ← 用中文
        return f"送{item}"
    elif state.intent == IntentTypes.CONCIERGE:
        return _CONCIERGE_DESC.get(state.service_action or "", "礼宾服务")
    return "操作"


def _action_confirm_room_control(state: AgentState) -> str:
    action_desc = _ROOM_CONTROL_DESC.get(state.room_control_action or "", "操作")
    if state.room_control_value:
        action_desc += f"至 {state.room_control_value}"
    return f"好的，已为您的 {state.room_number or '您的'} 房间{action_desc}，请稍候片刻。"


def _action_confirm_delivery(state: AgentState) -> str:
    qty = state.delivery_quantity or 1
    item = _DELIVERY_ITEM_DESC.get(state.delivery_item or "", state.delivery_item or "物品")
    item_desc = f"{qty} 件{item}" if qty > 1 else item
    return f"好的，已为您安排送{item_desc}到 {state.room_number or '您的'} 房间，预计 15 分钟内送达。"


def _action_confirm_concierge(state: AgentState) -> str:
    action_desc = _CONCIERGE_DESC.get(state.service_action or "", "服务")
    detail_desc = f"（{state.service_detail}）" if state.service_detail else ""
    return f"好的，礼宾部已收到您的{action_desc}请求{detail_desc}，我们会尽快安排，如有问题请随时告知。"


async def _llm_generate(state: AgentState, llm: ChatOpenAI) -> str:
    system = _SYSTEM_PROMPT.format(
        hotel_name=state.hotel_name or "酒店",
        hotel_location=state.hotel_location or "",
        guest_name=state.guest_name or "尊贵的客人",
        room_number=state.room_number or "",
        hotel_locale=state.hotel_locale or "zh-CN",
    )

    # 将 tool_results 序列化为上下文
    tool_context = ""
    if state.tool_results:
        import json
        try:
            tool_context = "\n\nTool results (use this data, do not fabricate):\n" \
                           + json.dumps(state.tool_results, ensure_ascii=False, indent=2)
        except Exception:
            pass

    # 历史对话（最近 6 条）
    messages = [SystemMessage(content=system + tool_context)]
    for h in state.history[-6:]:
        role = h.get("role", "user")
        content = h.get("content", "")
        if role == "user":
            messages.append(HumanMessage(content=content))
        else:
            from langchain_core.messages import AIMessage
            messages.append(AIMessage(content=content))

    messages.append(HumanMessage(content=state.user_message))

    try:
        response = await llm.ainvoke(messages)
        return response.content.strip()
    except Exception as e:
        log.error("[Generate] LLM failed: %s", e)
        return "非常抱歉，系统暂时遇到问题，请稍后再试或联系前台。"

