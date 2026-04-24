"""
Unit tests for classify_node — 规则匹配部分
"""
from unittest.mock import MagicMock

from app.models.state import AgentState, IntentTypes
from app.nodes.classify import classify_node


def _state(msg: str) -> AgentState:
    return AgentState(user_message=msg, hotel_id="nexstay", session_id="test")


def _classify(msg: str) -> dict:
    return classify_node(_state(msg), llm=MagicMock())


# ── FAQ ───────────────────────────────────────────────────────────────────────

def test_wifi():
    r = _classify("WiFi密码是多少？")
    assert r["intent"] == IntentTypes.FAQ
    assert r["faq_category"] == "wifi"


def test_breakfast():
    r = _classify("早餐几点供应？")
    assert r["intent"] == IntentTypes.FAQ
    assert r["faq_category"] == "breakfast"


def test_checkout():
    r = _classify("退房时间是几点？")
    assert r["intent"] == IntentTypes.FAQ
    assert r["faq_category"] == "checkout"


# ── Room Control ──────────────────────────────────────────────────────────────

def test_ac():
    r = _classify("空调调到22度")
    assert r["intent"] == IntentTypes.ROOM_CONTROL
    assert r["room_control_action"] == "ac"
    assert r.get("requires_approval") is True


def test_curtain():
    r = _classify("帮我关一下窗帘")
    assert r["intent"] == IntentTypes.ROOM_CONTROL
    assert r["room_control_action"] == "curtain"


def test_dnd():
    r = _classify("开启勿扰模式")
    assert r["intent"] == IntentTypes.ROOM_CONTROL
    assert r["room_control_action"] == "dnd"


# ── Delivery ──────────────────────────────────────────────────────────────────

def test_water():
    r = _classify("帮我送两瓶矿泉水")
    assert r["intent"] == IntentTypes.DELIVERY
    assert r["delivery_item"] == "water"
    assert r.get("requires_approval") is True


def test_towel():
    r = _classify("需要多一条毛巾")
    assert r["intent"] == IntentTypes.DELIVERY
    assert r["delivery_item"] == "towel"


# ── Concierge ─────────────────────────────────────────────────────────────────

def test_wakeup():
    r = _classify("明早6点叫醒我")
    assert r["intent"] == IntentTypes.CONCIERGE
    assert r["service_action"] == "wakeup"
    assert r.get("requires_approval") is True


def test_transport():
    r = _classify("帮我叫车去机场")
    assert r["intent"] == IntentTypes.CONCIERGE
    assert r["service_action"] == "transport"


# ── Restaurant ────────────────────────────────────────────────────────────────

def test_restaurant_romantic():
    r = _classify("推荐一家浪漫的餐厅")
    assert r["intent"] == IntentTypes.RESTAURANT
    assert "romantic" in r.get("ambiance", [])


def test_restaurant_price_budget():
    r = _classify("有什么便宜实惠的餐厅吗？cheap options")
    assert r["intent"] == IntentTypes.RESTAURANT
    assert r.get("max_price") == 2


# ── Attractions ───────────────────────────────────────────────────────────────

def test_attractions_culture():
    r = _classify("附近有什么文化景点")
    assert r["intent"] == IntentTypes.ATTRACTIONS
    assert r.get("attraction_category") == "culture"


def test_attractions_general():
    r = _classify("附近有什么好玩的")
    assert r["intent"] == IntentTypes.ATTRACTIONS
