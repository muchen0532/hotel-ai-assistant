from __future__ import annotations

import json
import logging
import uuid
from datetime import datetime, timezone, timedelta
from typing import AsyncIterator

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from langgraph.types import Command
from sqlalchemy import text

from app.api.db import get_session_dep
from app.api.deps import get_graph
from app.models.schemas import ChatRequest, ApprovalRequest, SessionInitRequest, SessionInitResponse

log = logging.getLogger(__name__)
router = APIRouter(prefix="/api", tags=["agent"])


@router.get("/health")
def health():
    return {"status": "ok", "service": "backend-python"}


@router.post("/session/init", response_model=SessionInitResponse)
async def session_init(req: SessionInitRequest, db=Depends(get_session_dep)):
    code = req.hotel_code.upper().strip()
    room = req.room_number.strip()
    name = req.guest_name.strip()

    hotel_row = await db.execute(
        text("SELECT * FROM hotels WHERE code=:c AND is_active=true"), {"c": code}
    )
    hotel = hotel_row.mappings().first()
    if not hotel:
        raise HTTPException(status_code=404, detail=f"Hotel code '{code}' not found")

    room_row = await db.execute(
        text("SELECT * FROM rooms WHERE hotel_id=:h AND room_number=:r AND is_active=true"),
        {"h": hotel["id"], "r": room}
    )
    db_room = room_row.mappings().first()
    if not db_room:
        raise HTTPException(status_code=404, detail=f"Room {room} not found")

    session_id = uuid.uuid4()
    expires_at = datetime.now(timezone.utc) + timedelta(hours=24)
    await db.execute(
        text("""
            INSERT INTO guest_sessions (id, hotel_id, room_id, guest_name, is_active, expires_at, checked_in_at)
            VALUES (:id, :hotel_id, :room_id, :guest_name, true, :expires_at, now())
        """),
        {"id": str(session_id), "hotel_id": hotel["id"],
         "room_id": db_room["id"], "guest_name": name, "expires_at": expires_at}
    )
    await db.commit()
    log.info("[Session] Created session=%s hotel=%s room=%s guest=%s",
             session_id, hotel["id"], room, name)

    return SessionInitResponse(session_id=session_id, hotel=_to_hotel_config(hotel))


@router.post("/session/logout")
async def session_logout(body: dict, db=Depends(get_session_dep)):
    session_id = body.get("session_id")
    if not session_id:
        raise HTTPException(status_code=400, detail="session_id required")
    await db.execute(
        text("UPDATE guest_sessions SET is_active=false WHERE id=:id"), {"id": session_id}
    )
    await db.commit()
    log.info("[Session] Invalidated session=%s", session_id)
    return {"ok": True}


@router.post("/chat/stream")
async def chat_stream(req: ChatRequest, graph=Depends(get_graph), db=Depends(get_session_dep)):
    session = await _validate_session(req.session_id, db)
    config = {"configurable": {"thread_id": req.session_id}}

    existing = await graph.aget_state(config)
    input_state = _build_input(req, existing, session)

    async def event_generator() -> AsyncIterator[str]:
        try:
            async for event in graph.astream_events(input_state, config=config, version="v2"):
                event_type = event.get("event", "")
                data = event.get("data", {})

                if event_type == "on_chain_start":
                    node = event.get("name", "")
                    if node in ("classify", "execute_tools", "generate"):
                        yield _sse({
                            "type": "meta",
                            "meta": {
                                "agent_trace": [{"label": node, "status": "running"}],
                                "info_grid": None, "cards": None, "chips": None
                            }
                        })

                elif event_type == "on_chain_end":
                    node = event.get("name", "")
                    if node in ("classify", "execute_tools", "generate"):
                        output = data.get("output", {})
                        if hasattr(output, "model_dump"):
                            output = output.model_dump()

                        yield _sse({
                            "type": "meta",
                            "meta": {
                                "agent_trace": [{"label": node, "status": "done"}],
                                "info_grid": None, "cards": None, "chips": None
                            }
                        })

                        if node == "generate" and output.get("response_text"):
                            text = output["response_text"]
                            words = text.split(" ")
                            buf = []
                            for i, w in enumerate(words):
                                buf.append(w)
                                if (i + 1) % 3 == 0 or i == len(words) - 1:
                                    yield _sse({"type": "text_delta", "delta": " ".join(buf) + " "})
                                    buf = []

                            meta = output.get("response_meta") or {}
                            if hasattr(meta, "model_dump"):
                                meta = meta.model_dump()
                            yield _sse({
                                "type": "meta",
                                "meta": {
                                    "info_grid": meta.get("info_grid"),
                                    "cards": meta.get("cards"),
                                    "agent_trace": [],
                                    "chips": meta.get("chips")
                                }
                            })

                elif event_type == "on_chain_stream":
                    chunk = data.get("chunk", {})
                    if "__interrupt__" in chunk:
                        interrupt_data = chunk["__interrupt__"]
                        yield _sse({"type": "interrupt",
                                    "data": interrupt_data[0].value
                                    if isinstance(interrupt_data, list) else interrupt_data})

            yield _sse({"type": "done"})
        except Exception as e:
            log.error("[SSE] Stream error: %s", e, exc_info=True)
            yield _sse({"type": "error", "message": str(e)})

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


# ── Human-in-the-loop 确认 ────────────────────────────────────────────────────

@router.post("/chat/approve")
async def approve(req: ApprovalRequest, graph=Depends(get_graph)):
    config = {"configurable": {"thread_id": req.thread_id}}
    state = await graph.aget_state(config)
    if not state or "human_review" not in (state.next or []):
        raise HTTPException(status_code=400, detail="No pending approval for this session")

    final_state = await graph.ainvoke(Command(resume={"approved": req.approved}), config=config)
    return {
        "session_id": req.thread_id,
        "approved": req.approved,
        "response_text": final_state.get("response_text", ""),
        "response_meta": final_state.get("response_meta", {}),
    }


@router.get("/session/pending/{session_id}")
async def get_pending_result(session_id: str, graph=Depends(get_graph)):
    config = {"configurable": {"thread_id": session_id}}
    state = await graph.aget_state(config)
    if not state:
        raise HTTPException(status_code=404, detail="Session not found")
    if "human_review" in (state.next or []):
        return {"status": "pending"}
    values = state.values
    if isinstance(values, dict) and values.get("response_text"):
        return {"status": "completed",
                "response_text": values["response_text"],
                "response_meta": values.get("response_meta", {})}
    return {"status": "pending"}


@router.get("/session/{session_id}")
async def get_session(session_id: str, graph=Depends(get_graph)):
    config = {"configurable": {"thread_id": session_id}}
    state = await graph.aget_state(config)
    if not state:
        raise HTTPException(status_code=404, detail="Session not found")
    return {"session_id": session_id, "state": state.values, "next": state.next}


async def _validate_session(session_id: str, db) -> dict:
    row = await db.execute(
        text("""
            SELECT gs.guest_name, r.room_number,
                   h.id as hotel_id, h.name as hotel_name,
                   h.location, h.locale, h.welcome_chips, h.qdrant_collection
            FROM guest_sessions gs
            JOIN rooms  r ON r.id = gs.room_id
            JOIN hotels h ON h.id = gs.hotel_id
            WHERE gs.id=:id AND gs.is_active=true AND gs.expires_at > now()
        """),
        {"id": session_id}
    )
    session = row.mappings().first()
    if not session:
        raise HTTPException(status_code=401, detail="Session expired or not found")
    return dict(session)


def _build_input(req: ChatRequest, existing_state, session: dict) -> dict:
    base = dict(existing_state.values) if existing_state else {}
    return {
        **base,
        "session_id": req.session_id,
        "user_message": req.message,
        "hotel_id": session["hotel_id"],
        "hotel_name": session["hotel_name"],
        "hotel_location": session["location"],
        "hotel_locale": session["locale"],
        "hotel_welcome_chips": session.get("welcome_chips") or [],
        "guest_name": session.get("guest_name"),
        "room_number": session["room_number"],
        # 每次新消息重置操作类状态，防止上次的 approved/intent 污染新请求
        "approved": None,
        "requires_approval": False,
        "intent": None,
        "room_control_action": None,
        "room_control_value": None,
        "delivery_item": None,
        "delivery_quantity": None,
        "service_action": None,
        "service_detail": None,
    }


def _to_hotel_config(hotel: dict) -> dict:
    return {
        "hotel_id": hotel["id"],
        "name": hotel["name"],
        "tagline": hotel.get("tagline"),
        "location": hotel.get("location"),
        "locale": hotel.get("locale"),
        "theme": hotel.get("theme"),
        "quick_actions": hotel.get("quick_actions"),
        "welcome_chips": hotel.get("welcome_chips"),
    }


def _sse(data: dict) -> str:
    return f"data: {json.dumps(data, ensure_ascii=False)}\n\n"
