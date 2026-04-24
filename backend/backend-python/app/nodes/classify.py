from __future__ import annotations

import json
import logging
import re
from typing import Any

from langchain_core.messages import HumanMessage
from langchain_openai import ChatOpenAI

from app.models.state import AgentState, IntentTypes

log = logging.getLogger(__name__)

# 规则表: 对齐Java IntentClassifier.RULES
_RULES: list[tuple[re.Pattern, str, str | None]] = [
    # --- FAQ ---
    (re.compile(r"wifi|WiFi|无线网|上网|网络|网速|密码|wifi密码|password|internet|network", re.I), IntentTypes.FAQ,
     "wifi"),
    (re.compile(r"早餐|早饭|自助早餐|几点吃早餐|早餐时间|有没有早餐|breakfast|brunch|morning.meal", re.I),
     IntentTypes.FAQ, "breakfast"),
    (re.compile(r"退房|几点退房|延迟退房|晚点退房|checkout|check.?out", re.I), IntentTypes.FAQ, "checkout"),
    (re.compile(r"营业时间|开放时间|几点开|几点关|前台在哪|酒店信息", re.I), IntentTypes.FAQ, "general"),
    # --- Facilities (查询) ---
    (re.compile(r"设施|有什么设施|酒店有什么|配套|facilit|amenity|amenities", re.I), IntentTypes.FACILITIES, None),
    (re.compile(r"泳池|游泳|游泳池|pool|swimming", re.I), IntentTypes.FACILITIES, "pool"),
    (re.compile(r"健身房|健身|锻炼|跑步机|gym|fitness|workout|exercise", re.I), IntentTypes.FACILITIES, "gym"),
    (re.compile(r"按摩|温泉|桑拿|汗蒸|spa|massage|sauna|onsen", re.I), IntentTypes.FACILITIES, "spa"),
    # --- Restaurant (推荐/查询) ---
    (re.compile(r"浪漫|约会|情侣|romantic|date.night", re.I), IntentTypes.RESTAURANT, "romantic"),
    (re.compile(r"家庭|孩子|亲子|family.friendly|kids|children", re.I), IntentTypes.RESTAURANT, "family"),
    (re.compile(r"吃什么|哪里吃|餐厅|饭店|用餐|晚餐|午餐|晚饭|restaurant|dinner|lunch|eat|food|dining|meal", re.I),
     IntentTypes.RESTAURANT, None),
    # --- Attractions ---
    (re.compile(r"文化|博物馆|寺庙|神社|古迹|museum|temple|shrine|castle|monument|culture", re.I),
     IntentTypes.ATTRACTIONS, "culture"),
    (re.compile(r"公园|自然|海边|山|户外|nature|park|beach|mountain|outdoor", re.I), IntentTypes.ATTRACTIONS, "nature"),
    (re.compile(r"购物|商场|买东西|shop|market|mall", re.I), IntentTypes.ATTRACTIONS, "shopping"),
    (re.compile(r"滑雪|冲浪|运动|探险|surf|ski|dive|kayak|adventure|sport", re.I), IntentTypes.ATTRACTIONS, "sport"),
    (re.compile(r"附近有什么|附近玩|去哪玩|周边|景点|旅游|attract|nearby|sightsee|visit|tourism", re.I),
     IntentTypes.ATTRACTIONS, None),
    # --- 客控 Room Control ---
    (re.compile(r"空调|温度|冷|热|太冷|太热|制冷|制热|air.?con|ac|hvac", re.I), IntentTypes.ROOM_CONTROL, "ac"),
    (re.compile(r"开灯|关灯|灯光|亮一点|暗一点|调亮|调暗|light|lamp|brightness", re.I), IntentTypes.ROOM_CONTROL,
     "light"),
    (re.compile(r"窗帘|拉开窗帘|关窗帘|打开窗帘|遮光|curtain|blind|drape", re.I), IntentTypes.ROOM_CONTROL, "curtain"),
    (re.compile(r"勿扰|不要打扫|do.not.disturb|dnd", re.I), IntentTypes.ROOM_CONTROL, "dnd"),
    (re.compile(r"打扫|清理房间|整理房间|收拾一下|clean.?up|housekeep|make.up.room", re.I), IntentTypes.ROOM_CONTROL,
     "housekeeping"),
    (re.compile(r"电视|频道|换台|中央|cctv|音量|tv|television|channel|volume", re.I), IntentTypes.ROOM_CONTROL, "tv"),
    # --- 送物 Delivery ---
    (re.compile(r"送.*毛巾|拿.*毛巾|要.*毛巾|提供.*毛巾|毛巾|浴巾|towel|bath.?towel", re.I), IntentTypes.DELIVERY,
     "towel"),
    (re.compile(r"牙刷|牙膏|洗漱|洗漱用品|toothbrush|toothpaste|shampoo|toiletries|amenities", re.I),
     IntentTypes.DELIVERY, "toiletries"),
    (re.compile(r"枕头|被子|毯子|毛毯|床单|被褥|pillow|blanket|quilt|bedding", re.I), IntentTypes.DELIVERY, "bedding"),
    (re.compile(r"水|矿泉水|饮用水|瓶装水|mineral.?water", re.I), IntentTypes.DELIVERY, "water"),
    (re.compile(r"熨斗|熨衣|熨衣板|熨烫|iron|ironing.?board", re.I), IntentTypes.DELIVERY, "iron"),
    (re.compile(r"婴儿床|加床|儿童床|crib|baby.?bed|extra.?bed", re.I), IntentTypes.DELIVERY, "extra_bed"),
    (re.compile(r"送餐|客房服务|客房餐|room.?service|deliver|send.?up", re.I), IntentTypes.DELIVERY, "room_service"),
    # --- 礼宾 ---
    (re.compile(r"叫醒|闹钟|明早叫我|叫我起床|晨叫|wake.?up|alarm|call", re.I), IntentTypes.CONCIERGE, "wakeup"),
    (re.compile(r"接机|送机|接送|打车|网约车|叫车|去机场|taxi|cab|car|transfer", re.I), IntentTypes.CONCIERGE,
     "transport"),
    (re.compile(r"行李|寄存|存包|取行李|luggage|baggage", re.I), IntentTypes.CONCIERGE, "luggage"),
    (re.compile(r"门票|景区票|订票|预订门票|tour|ticket|excursion", re.I), IntentTypes.CONCIERGE, "tour_booking"),
]

_PRICE_BUDGET = re.compile(r"便宜|实惠|省钱|平价|不贵|便宜点|cheap|budget|affordable", re.I)
_PRICE_MID = re.compile(r"中档|适中|一般|还行|差不多|mid.?range|moderate", re.I)
_PRICE_UPSCALE = re.compile(r"高档|高端|奢华|贵一点|好一点|精致|luxury|fine.?dining|upscale|high.?end", re.I)

_CLASSIFY_PROMPT = """
You are the intent classifier for a hotel AI concierge.
你是酒店 AI 礼宾助手的意图分类器，需要准确理解中英文及中英混合输入。

## Intents
- faq          - 酒店基础信息（wifi、早餐、退房、政策）
- facilities   - 酒店设施信息查询（泳池、健身房、SPA）
- restaurant   - 餐厅推荐 / 用餐建议
- attractions  - 附近景点 / 周边推荐
- room_control - 客控操作（空调、灯光、窗帘、电视、免打扰、打扫）
- delivery     - 送物 / 客房服务（毛巾、水、牙刷、送餐）
- concierge    - 礼宾服务（叫醒、叫车、行李寄存、门票预订）
- general      - 以上均不符合

## Slots
faq          → faq_category: "wifi"|"breakfast"|"checkout"|"facilities"|"general"
restaurant   → ambiance: ["romantic","family","casual"]  max_price: 1–4|null
attractions  → attraction_category: "culture"|"nature"|"shopping"|"sport"|null
room_control → room_control_action: "ac"|"light"|"curtain"|"dnd"|"housekeeping"|"tv"
               room_control_value: 温度数字字符串 | "on"|"off"|"open"|"closed" | null
delivery     → delivery_item: "towel"|"toiletries"|"bedding"|"water"|"iron"|"extra_bed"|"room_service"
               delivery_quantity: integer|null
concierge    → service_action: "wakeup"|"transport"|"luggage"|"tour_booking"
               service_detail: 具体时间/地点/数量描述 | null

## Examples
"帮我送两瓶矿泉水" → {{"intent":"delivery","delivery_item":"water","delivery_quantity":2}}
"空调太热了调到22度" → {{"intent":"room_control","room_control_action":"ac","room_control_value":"22"}}
"明早6点半叫醒我" → {{"intent":"concierge","service_action":"wakeup","service_detail":"06:30"}}
"Wi-Fi password please" → {{"intent":"faq","faq_category":"wifi"}}
"附近有什么好玩的，最好是文化景点" → {{"intent":"attractions","attraction_category":"culture"}}

Guest message: {message}

Respond ONLY with valid JSON, no markdown, include ALL keys even if null:
{{"intent":"...","faq_category":null,"ambiance":[],"max_price":null,"attraction_category":null,"room_control_action":null,"room_control_value":null,"delivery_item":null,"delivery_quantity":null,"service_action":null,"service_detail":null}}
"""


def _apply_price_filter(updates: dict, msg: str) -> None:
    if _PRICE_BUDGET.search(msg):
        updates["max_price"] = 2
    elif _PRICE_MID.search(msg):
        updates["max_price"] = 3
    elif _PRICE_UPSCALE.search(msg):
        updates["max_price"] = 4


def classify_node(state: AgentState, llm: ChatOpenAI) -> dict:
    msg = state.user_message
    updates: dict[str, Any] = {}

    # 1. RULE
    for pattern, intent, sub in _RULES:
        if pattern.search(msg):
            updates["intent"] = intent
            if intent == IntentTypes.FAQ:
                updates["faq_category"] = sub if sub is not None else "general"
            elif intent == IntentTypes.FACILITIES:
                updates["faq_category"] = sub
            elif intent == IntentTypes.ATTRACTIONS:
                updates["attraction_category"] = sub
            elif intent == IntentTypes.RESTAURANT:
                updates["ambiance"] = [sub] if sub else []
            elif intent == IntentTypes.ROOM_CONTROL:
                updates["room_control_action"] = sub
            elif intent == IntentTypes.DELIVERY:
                updates["delivery_item"] = sub
            elif intent == IntentTypes.CONCIERGE:
                updates["service_action"] = sub

            _apply_price_filter(updates, msg)

            if intent in IntentTypes.REQUIRES_APPROVAL:
                updates["requires_approval"] = True

            log.debug("[Classify] rule match intent=%s sub=%s", intent, sub)
            return updates

    # 2. LLM
    try:
        prompt = _CLASSIFY_PROMPT.format(message=msg)
        response = llm.invoke([HumanMessage(content=prompt)])
        raw = response.content.strip().replace("```json", "").replace("```", "").strip()
        parsed: dict = json.loads(raw)

        updates["intent"] = parsed.get("intent", IntentTypes.GENERAL)
        updates["faq_category"] = parsed.get("faq_category")
        updates["ambiance"] = parsed.get("ambiance") or []
        updates["max_price"] = parsed.get("max_price")
        updates["attraction_category"] = parsed.get("attraction_category")
        updates["room_control_action"] = parsed.get("room_control_action")
        updates["room_control_value"] = parsed.get("room_control_value")
        updates["delivery_item"] = parsed.get("delivery_item")
        updates["delivery_quantity"] = parsed.get("delivery_quantity")
        updates["service_action"] = parsed.get("service_action")
        updates["service_detail"] = parsed.get("service_detail")

        log.debug("[Classify] LLM intent=%s", updates["intent"])
    except Exception as e:
        log.warning("[Classify] LLM failed: %s — fallback to general", e)
        updates["intent"] = IntentTypes.GENERAL

    # requires_approval
    if updates.get("intent") in IntentTypes.REQUIRES_APPROVAL:
        updates["requires_approval"] = True

    return updates
