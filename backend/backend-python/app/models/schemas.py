from typing import Optional, Any
from pydantic import BaseModel


class HotelConfig(BaseModel):
    hotel_id: str
    name: str
    tagline: Optional[str] = None
    location: Optional[str] = None
    locale: Optional[str] = None
    theme: Optional[dict] = None
    quick_actions: Optional[list] = None
    welcome_chips: Optional[list] = None


class SessionInitRequest(BaseModel):
    hotel_code: str
    room_number: str
    guest_name: str


class SessionInitResponse(BaseModel):
    session_id: Any
    hotel: HotelConfig


class ChatRequest(BaseModel):
    session_id: str
    hotel_id: str
    message: str


class TraceStep(BaseModel):
    label: str
    status: str  # running | done | error


class InfoGridItem(BaseModel):
    label: str
    value: str


class RestaurantCard(BaseModel):
    emoji: Optional[str] = None
    name: str
    type: Optional[str] = None
    rating: Optional[str] = None
    hours: Optional[str] = None
    distance: Optional[str] = None
    tag: Optional[str] = None
    tag_text: Optional[str] = None


class ResponseMeta(BaseModel):
    agent_trace: list[TraceStep] = []
    info_grid: list[InfoGridItem] = []
    cards: list[RestaurantCard] = []
    chips: list[str] = []


class ChatResponse(BaseModel):
    session_id: str
    response_text: str
    response_meta: ResponseMeta
    intent: Optional[str] = None


# Human-in-the-loop 确认请求
class ApprovalRequest(BaseModel):
    thread_id: str
    approved: bool
    reason: Optional[str] = None
