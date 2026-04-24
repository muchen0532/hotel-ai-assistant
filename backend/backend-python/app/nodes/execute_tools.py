from __future__ import annotations

import logging
from typing import Any

from app.models.state import AgentState, IntentTypes

log = logging.getLogger(__name__)

_ROOM_CONTROL_CHIPS: dict[str, list[str]] = {
    "ac": ["调高温度", "调低温度", "关闭空调"],
    "light": ["调亮", "调暗", "关灯"],
    "curtain": ["打开窗帘", "关闭窗帘", "半开"],
    "dnd": ["取消勿扰", "请打扫房间"],
    "housekeeping": ["明天再打扫", "需要换床单"],
    "tv": ["换台", "调音量", "关闭电视"],
}

_CONCIERGE_CHIPS: dict[str, list[str]] = {
    "wakeup": ["修改叫醒时间", "取消叫醒"],
    "transport": ["预约接机", "预约送机", "市内用车"],
    "luggage": ["寄存行李", "取回行李"],
    "tour_booking": ["推荐景点", "查询门票", "人工协助"],
}


def _trace_step(trace: list[dict], label: str, status: str) -> None:
    # 找到同 label 的步骤更新，否则追加
    for step in trace:
        if step["label"] == label:
            step["status"] = status
            return
    trace.append({"label": label, "status": status})


async def execute_tools_node(state: AgentState, db, qdrant) -> dict:
    intent = state.intent
    trace: list[dict] = []
    meta: dict[str, Any] = {"agent_trace": [], "info_grid": [], "cards": [], "chips": []}
    results: list[Any] = []

    try:
        if intent == IntentTypes.FAQ:
            await _handle_faq(state, trace, meta, results, db)
        elif intent == IntentTypes.FACILITIES:
            await _handle_facilities(state, trace, meta, results, db)
        elif intent == IntentTypes.RESTAURANT:
            await _handle_restaurant(state, trace, meta, results, db, qdrant)
        elif intent == IntentTypes.ATTRACTIONS:
            await _handle_attractions(state, trace, meta, results, db, qdrant)
        elif intent == IntentTypes.ROOM_CONTROL:
            await _handle_room_control(state, trace, meta, results)
        elif intent == IntentTypes.DELIVERY:
            await _handle_delivery(state, trace, meta, results)
        elif intent == IntentTypes.CONCIERGE:
            await _handle_concierge(state, trace, meta, results)
    except Exception as e:
        log.error("[Tools] Unexpected error intent=%s: %s", intent, e, exc_info=True)
        trace.append({"label": f"Tool error: {e}", "status": "error"})

    meta["agent_trace"] = trace
    return {
        "tool_results": results,
        "tool_trace": trace,
        "response_meta": meta,
        "approved": None,
    }


async def _handle_faq(state, trace, meta, results, db):
    category = state.faq_category or "general"
    label = f"PostgreSQL: FAQ lookup · category={category}"
    _trace_step(trace, label, "running")
    try:
        from app.tools.db_tools import faq_find_by_category, faq_find_all
        faqs = await faq_find_by_category(db, state.hotel_id, category)
        if faqs:
            faq = faqs[0]
            meta["info_grid"] = faq.get("answer_grid", [])
            meta["chips"] = faq.get("follow_up_chips", [])
            results.append(faq)
            _trace_step(trace, label, "done")
            return
        _trace_step(trace, label, "done")

        # fuzzy fallback
        fb_label = "PostgreSQL: fuzzy FAQ fallback"
        _trace_step(trace, fb_label, "running")
        all_faqs = await faq_find_all(db, state.hotel_id)
        if all_faqs:
            prefix = state.user_message[:6].lower()
            best = next((f for f in all_faqs if prefix in f["question"].lower()), all_faqs[0])
            meta["info_grid"] = best.get("answer_grid", [])
            meta["chips"] = best.get("follow_up_chips", [])
            results.append(best)
        _trace_step(trace, fb_label, "done")
    except Exception as e:
        log.warning("[Tools] FAQ step failed: %s", e)
        _trace_step(trace, label, "error")


async def _handle_facilities(state, trace, meta, results, db):
    category = state.faq_category
    label = "PostgreSQL: facilities query" + (f" · {category}" if category else "")
    _trace_step(trace, label, "running")
    try:
        from app.tools.db_tools import facility_find
        facilities = await facility_find(db, state.hotel_id, category)
        meta["info_grid"] = [
            {"label": f["name"],
             "value": _build_hours(f) + (f" · {f['location_desc']}" if f.get("location_desc") else "")}
            for f in facilities[:4]
        ]
        meta["chips"] = [c["query"] for c in state.hotel_welcome_chips[:3]]
        results.extend(facilities)
        _trace_step(trace, label, "done")
    except Exception as e:
        log.warning("[Tools] Facilities step failed: %s", e)
        _trace_step(trace, label, "error")


async def _handle_restaurant(state, trace, meta, results, db, qdrant):
    min_rating = state.min_rating or 4.0
    ambiance = state.ambiance
    max_price = state.max_price

    # Step 1: Qdrant
    qdrant_names: list[str] = []
    label1 = f"Qdrant: semantic search · hotel={state.hotel_id}"
    _trace_step(trace, label1, "running")
    try:
        from app.tools.qdrant_tools import search_names
        qdrant_names = await search_names(qdrant, state.hotel_id, state.user_message, "restaurant", 8)
        log.debug("[Restaurant] qdrantNames=%s", qdrant_names)
        _trace_step(trace, label1, "done")
    except Exception as e:
        log.warning("[Tools] Qdrant restaurant failed: %s", e)
        _trace_step(trace, label1, "error")

    # Step 2: Filter log
    label2 = f"Filter: ambiance={ambiance or 'any'}, price≤{max_price or 'any'}, rating≥{min_rating}"
    _trace_step(trace, label2, "running")
    _trace_step(trace, label2, "done")

    # Step 3: PostgreSQL
    pg_results: list[dict] = []
    label3 = "PostgreSQL: hours & availability lookup"
    _trace_step(trace, label3, "running")
    try:
        from app.tools.db_tools import restaurant_find_filtered
        pg_results = await restaurant_find_filtered(db, state.hotel_id, min_rating, max_price, ambiance, 6)
        log.debug("[Restaurant] pgResults=%s", [r["name"] for r in pg_results])
        _trace_step(trace, label3, "done")
    except Exception as e:
        log.warning("[Tools] PostgreSQL restaurant failed: %s", e)
        _trace_step(trace, label3, "error")

    # Step 4: Rerank
    label4 = "Rerank by semantic score + rating"
    _trace_step(trace, label4, "running")
    reranked = _rerank(pg_results, qdrant_names, 3)
    meta["cards"] = [_to_restaurant_card(r) for r in reranked]
    meta["chips"] = ["有素食选项吗？", "可以预订餐位吗？", "有着装要求吗？"]
    results.extend(reranked)
    log.debug("[Restaurant] pgResults=%s", [r["name"] for r in pg_results])
    log.debug("[Restaurant] qdrantNames=%s", qdrant_names)
    log.debug("[Restaurant] reranked=%s", [r["name"] for r in reranked])
    log.debug("[Restaurant] cards=%s", meta["cards"])
    _trace_step(trace, label4, "done")


async def _handle_attractions(state, trace, meta, results, db, qdrant):
    category = state.attraction_category

    # Step 1: Qdrant RAG
    qdrant_names: list[str] = []
    label1 = f"Qdrant: RAG retrieval · hotel={state.hotel_id}"
    _trace_step(trace, label1, "running")
    try:
        from app.tools.qdrant_tools import search_names
        qdrant_names = await search_names(qdrant, state.hotel_id, state.user_message, "attraction", 8)
        log.debug("[Attractions] qdrantNames=%s", qdrant_names)
        _trace_step(trace, label1, "done")
    except Exception as e:
        log.warning("[Tools] Qdrant attraction failed: %s", e)
        _trace_step(trace, label1, "error")

    # Step 2: Filter log
    label2 = f"Filter: category={category or 'all'}, rating≥4.0"
    _trace_step(trace, label2, "running")
    _trace_step(trace, label2, "done")

    # Step 3: PostgreSQL
    pg_results: list[dict] = []
    label3 = "PostgreSQL: metadata enrichment"
    _trace_step(trace, label3, "running")
    try:
        from app.tools.db_tools import attraction_find
        pg_results = await attraction_find(db, state.hotel_id, category, 4.0, 5)
        log.debug("[Attractions] pgResults=%s", [r["name"] for r in pg_results])
        _trace_step(trace, label3, "done")
    except Exception as e:
        log.warning("[Tools] PostgreSQL attraction failed: %s", e)
        _trace_step(trace, label3, "error")

    # Step 4: Rerank（含 name mismatch fallback）
    label4 = "Rerank by relevance & rating"
    _trace_step(trace, label4, "running")
    if not pg_results and qdrant_names:
        log.warning("[Attractions] pgResults empty, fallback to qdrantNames lookup")
        try:
            from app.tools.db_tools import attraction_find_by_names
            pg_results = await attraction_find_by_names(db, state.hotel_id, qdrant_names)
        except Exception as e:
            log.warning("[Tools] Attraction fallback failed: %s", e)

    reranked = _rerank(pg_results, qdrant_names, 3)
    log.debug("[Attractions] reranked=%s cards=%d", [r["name"] for r in reranked], len(reranked))
    meta["cards"] = [_to_attraction_card(r) for r in reranked]
    meta["chips"] = ["如何前往？", "门票多少钱？", "几点开放？"]
    results.extend(reranked)
    _trace_step(trace, label4, "done")


async def _handle_room_control(state, trace, meta, results):
    action = state.room_control_action
    value = state.room_control_value
    label = f"RoomControl: action={action}" + (f" · value={value}" if value else "")
    _trace_step(trace, label, "running")
    # TODO: 对接客控系统 API（INNCOM / KNX / 罗格朗等）
    log.info("[RoomControl] room=%s action=%s value=%s", state.room_number, action, value)
    meta["chips"] = _ROOM_CONTROL_CHIPS.get(action or "", [])
    results.append({"action": action or "", "value": value or "", "room": state.room_number or ""})
    _trace_step(trace, label, "done")


async def _handle_delivery(state, trace, meta, results):
    item = state.delivery_item
    quantity = state.delivery_quantity or 1
    label = f"Delivery: item={item} · qty={quantity}"
    _trace_step(trace, label, "running")
    # TODO: 对接工单系统（HotSOS / Quore / 自研 HMS）
    log.info("[Delivery] room=%s item=%s qty=%s", state.room_number, item, quantity)
    meta["chips"] = ["还需要其他物品？", "送餐服务", "叫醒服务"]
    results.append({"item": item or "", "quantity": quantity, "room": state.room_number or ""})
    _trace_step(trace, label, "done")


async def _handle_concierge(state, trace, meta, results):
    action = state.service_action
    detail = state.service_detail
    label = f"Concierge: action={action}" + (f" · detail={detail}" if detail else "")
    _trace_step(trace, label, "running")
    # TODO: wakeup→PMS / transport→滴滴企业版 / luggage→HMS / tour_booking→票务API
    log.info("[Concierge] room=%s action=%s detail=%s", state.room_number, action, detail)
    meta["chips"] = _CONCIERGE_CHIPS.get(action or "", [])
    results.append({"action": action or "", "detail": detail or "", "room": state.room_number or ""})
    _trace_step(trace, label, "done")


def _build_hours(f: dict) -> str:
    if f.get("open_24h"):
        return "24/7"
    open_t = f.get("open_time")
    close_t = f.get("close_time")
    if open_t and close_t:
        return f"{open_t}–{close_t}"
    return ""


def _to_restaurant_card(r: dict) -> dict:
    return {
        "emoji": r.get("emoji") or "🍽️",
        "name": r.get("name", ""),
        "type": r.get("cuisine_type", ""),
        "rating": f"★ {r['rating']}" if r.get("rating") else "",
        "hours": _build_hours(r),
        "distance": r.get("distance_text", ""),
        "tag": r.get("tag"),
        "tag_text": r.get("tag_text"),
    }


def _to_attraction_card(a: dict) -> dict:
    cat = a.get("category", "")
    dist = a.get("distance_text", "")
    return {
        "emoji": a.get("emoji") or "📍",
        "name": a.get("name", ""),
        "type": f"{cat} · {dist}" if dist else cat,
        "rating": f"★ {a['rating']}" if a.get("rating") else "",
        "hours": _build_hours(a),
        "distance": dist,
        "tag": a.get("tag"),
        "tag_text": a.get("tag_text"),
    }


def _rerank(items: list[dict], name_order: list[str], limit: int) -> list[dict]:
    if not name_order:
        return items[:limit]

    def rank_key(item: dict) -> int:
        try:
            return name_order.index(item.get("name", ""))
        except ValueError:
            return 999

    return sorted(items, key=rank_key)[:limit]
