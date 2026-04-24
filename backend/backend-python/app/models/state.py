from __future__ import annotations

from typing import Any
from typing import Optional

from pydantic import BaseModel, Field, ConfigDict


# 对齐 Java AgentState
class AgentState(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)

    # ── Input ───────────────────────────────────——────────────——─────
    hotel_id: str = ""
    session_id: str = ""
    user_message: str = ""
    hotel_name: str = ""
    hotel_location: str = ""
    hotel_locale: str = "zh-CN"
    hotel_welcome_chips: list[str] = Field(default_factory=list)
    guest_name: Optional[str] = None
    room_number: Optional[str] = None
    history: list[dict] = Field(default_factory=list)

    # ── Intent Classification ───────────────────────────────────——───
    intent: Optional[str] = None

    # faq / facilities
    faq_category: Optional[str] = None

    # restaurant
    ambiance: list[str] = Field(default_factory=list)
    max_price: Optional[int] = None
    min_rating: Optional[float] = None

    # attractions
    attraction_category: Optional[str] = None

    # room_control
    room_control_action: Optional[str] = None
    room_control_value: Optional[str] = None

    # delivery
    delivery_item: Optional[str] = None
    delivery_quantity: Optional[int] = None

    # concierge
    service_action: Optional[str] = None
    service_detail: Optional[str] = None

    # ── Human-in-the-loop ───────────────────────────────────——───────
    requires_approval: bool = False
    approved: Optional[bool] = None

    # ── Tool Execution ───────────────────────────────────────────────
    tool_results: list[Any] = Field(default_factory=list)
    tool_trace: list[dict] = Field(default_factory=list)

    # ── Response Generation ──────────────────────────────────────────
    response_text: Optional[str] = None
    response_meta: Optional[dict] = None


# ── Intent 常量（对齐 Java IntentTypes）──────────────────────────────────────
class IntentTypes:
    FAQ = "faq"
    FACILITIES = "facilities"
    RESTAURANT = "restaurant"
    ATTRACTIONS = "attractions"
    GENERAL = "general"
    ROOM_CONTROL = "room_control"
    DELIVERY = "delivery"
    CONCIERGE = "concierge"

    # 操作类 intent 执行前需要人工确认
    REQUIRES_APPROVAL = {ROOM_CONTROL, DELIVERY, CONCIERGE}
