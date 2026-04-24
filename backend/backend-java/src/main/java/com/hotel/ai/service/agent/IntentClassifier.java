package com.hotel.ai.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.ai.model.dto.agent.AgentState;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.hotel.ai.constants.IntentTypes;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


@Component
@RequiredArgsConstructor
@Slf4j
public class IntentClassifier {

    private final ChatLanguageModel llm;
    private final ObjectMapper objectMapper;

    private record Rule(Pattern pattern, String intent, String subCategory) {
    }

    private static final List<Rule> RULES = List.of(

            // --- FAQ ---
            rule("wifi|WiFi|无线网|上网|网络|网速|密码|wifi密码|password|internet|network", IntentTypes.FAQ, "wifi"),
            rule("早餐|早饭|自助早餐|几点吃早餐|早餐时间|有没有早餐|breakfast|brunch|morning.meal", IntentTypes.FAQ, "breakfast"),
            rule("退房|几点退房|延迟退房|晚点退房|checkout|check.?out", IntentTypes.FAQ, "checkout"),
            rule("营业时间|开放时间|几点开|几点关|前台在哪|酒店信息", IntentTypes.FAQ, "general"),

            // --- Facilities (查询) ---
            rule("设施|有什么设施|酒店有什么|配套|facilit|amenity|amenities", IntentTypes.FACILITIES, null),
            rule("泳池|游泳|游泳池|pool|swimming", IntentTypes.FACILITIES, "pool"),
            rule("健身房|健身|锻炼|跑步机|gym|fitness|workout|exercise", IntentTypes.FACILITIES, "gym"),
            rule("按摩|温泉|桑拿|汗蒸|spa|massage|sauna|onsen", IntentTypes.FACILITIES, "spa"),

            // --- Restaurant (推荐/查询) ---
            rule("浪漫|约会|情侣|romantic|date.night", IntentTypes.RESTAURANT, "romantic"),
            rule("家庭|孩子|亲子|family.friendly|kids|children", IntentTypes.RESTAURANT, "family"),
            rule("吃什么|哪里吃|餐厅|饭店|用餐|晚餐|午餐|晚饭|restaurant|dinner|lunch|eat|food|dining|meal", IntentTypes.RESTAURANT, null),

            // --- Attractions ---
            rule("文化|博物馆|寺庙|神社|古迹|museum|temple|shrine|castle|monument|culture", IntentTypes.ATTRACTIONS, "culture"),
            rule("公园|自然|海边|山|户外|nature|park|beach|mountain|outdoor", IntentTypes.ATTRACTIONS, "nature"),
            rule("购物|商场|买东西|shop|market|mall", IntentTypes.ATTRACTIONS, "shopping"),
            rule("滑雪|冲浪|运动|探险|surf|ski|dive|kayak|adventure|sport", IntentTypes.ATTRACTIONS, "sport"),
            rule("附近有什么|附近玩|去哪玩|周边|景点|旅游|attract|nearby|sightsee|visit|tourism", IntentTypes.ATTRACTIONS, null),

			// --- 客控 Room Control ---
            rule("空调|温度|冷|热|太冷|太热|制冷|制热|air.?con|ac|hvac", IntentTypes.ROOM_CONTROL, "ac"), rule("开灯|关灯|灯光|亮一点|暗一点|调亮|调暗|light|lamp|brightness", IntentTypes.ROOM_CONTROL, "light"),
            rule("窗帘|拉开窗帘|关窗帘|打开窗帘|遮光|curtain|blind|drape", IntentTypes.ROOM_CONTROL, "curtain"),
            rule("勿扰|不要打扫|do.not.disturb|dnd", IntentTypes.ROOM_CONTROL, "dnd"), rule("打扫|清理房间|整理房间|收拾一下|clean.?up|housekeep|make.up.room", IntentTypes.ROOM_CONTROL, "housekeeping"),
            rule("电视|频道|换台|中央|cctv|音量|tv|television|channel|volume", IntentTypes.ROOM_CONTROL, "tv"),


			// --- 送物 Delivery ---
            rule("送.*毛巾|拿.*毛巾|要.*毛巾|提供.*毛巾|毛巾|浴巾|towel|bath.?towel", IntentTypes.DELIVERY, "towel"),
            rule("牙刷|牙膏|洗漱|洗漱用品|toothbrush|toothpaste|shampoo|toiletries|amenities", IntentTypes.DELIVERY, "toiletries"),
            rule("枕头|被子|毯子|毛毯|床单|被褥|pillow|blanket|quilt|bedding", IntentTypes.DELIVERY, "bedding"),
            rule("水|矿泉水|饮用水|瓶装水|mineral.?water|water", IntentTypes.DELIVERY, "water"),
            rule("熨斗|熨衣|熨衣板|熨烫|iron|ironing.?board", IntentTypes.DELIVERY, "iron"),
            rule("婴儿床|加床|儿童床|crib|baby.?bed|extra.?bed", IntentTypes.DELIVERY, "extra_bed"),
            rule("送餐|客房服务|客房餐|room.?service|deliver|send.?up", IntentTypes.DELIVERY, "room_service"),

			// --- 礼宾 ---
            rule("叫醒|闹钟|明早叫我|叫我起床|晨叫|wake.?up|alarm|call", IntentTypes.CONCIERGE, "wakeup"),
            rule("接机|送机|接送|打车|网约车|叫车|去机场|taxi|cab|car|transfer", IntentTypes.CONCIERGE, "transport"),
            rule("行李|寄存|存包|取行李|luggage|baggage", IntentTypes.CONCIERGE, "luggage"),
            rule("门票|景区票|订票|预订门票|tour|ticket|excursion", IntentTypes.CONCIERGE, "tour_booking")
    );

    private static Rule rule(String regex, String intent, String sub) {
        return new Rule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), intent, sub);
    }

    // ── 价格过滤（restaurant 场景用） ─────────────────────────────────────
    private static final Pattern PRICE_BUDGET = Pattern.compile("便宜|实惠|省钱|平价|不贵|便宜点|cheap|budget|affordable", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_MID = Pattern.compile("中档|适中|一般|还行|差不多|mid.?range|moderate", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_UPSCALE = Pattern.compile("高档|高端|奢华|贵一点|好一点|精致|luxury|fine.?dining|upscale|high.?end", Pattern.CASE_INSENSITIVE);
	

    // ── LLM 兜底 Prompt ───────────────────────────────────────────────────────
    private static final PromptTemplate CLASSIFY_PROMPT = PromptTemplate.from("""
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
            """);

    public AgentState classify(AgentState state) {
        String msg = state.getUserMessage();

        // 1. RULE
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(msg).find()) {
                applyIntent(state, rule.intent(), rule.subCategory());
                extractPriceFilter(state, msg);
                log.debug("[Intent] Rule match → intent={} sub={}", rule.intent(), rule.subCategory());
                return state;
            }
        }

        // 2. LLM
        try {
            Prompt prompt = CLASSIFY_PROMPT.apply(Map.of("message", msg));
            String raw = llm.generate(prompt.text());
            raw = raw.replaceAll("```json|```", "").trim();

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(raw, Map.class);

            state.setIntent((String) parsed.getOrDefault("intent", IntentTypes.GENERAL));
            state.setFaqCategory((String) parsed.get("faqCategory"));
            state.setAttractionCategory((String) parsed.get("attractionCategory"));
            state.setMaxPrice(parsed.get("maxPrice") instanceof Integer i ? i : null);

            state.setRoomControlAction((String) parsed.get("roomControlAction"));
            state.setRoomControlValue((String) parsed.get("roomControlValue"));
            state.setDeliveryItem((String) parsed.get("deliveryItem"));
            state.setDeliveryQuantity(parsed.get("deliveryQuantity") instanceof Integer i ? i : null);
            state.setServiceAction((String) parsed.get("serviceAction"));
            state.setServiceDetail((String) parsed.get("serviceDetail"));

            Object ambianceObj = parsed.get("ambiance");
            if (ambianceObj instanceof List<?> list) {
                state.setAmbiance(list.stream().map(Object::toString).toList());
            }

            log.debug("[Intent] LLM classified → {}", state.getIntent());
        } catch (Exception e) {
            log.warn("[Intent] LLM classification failed: {} — using general", e.getMessage());
            state.setIntent(IntentTypes.GENERAL);
        }

        return state;
    }

    private void applyIntent(AgentState state, String intent, String sub) {
        state.setIntent(intent);
        switch (intent) {
            case IntentTypes.FAQ -> state.setFaqCategory(sub != null ? sub : "general");
            case IntentTypes.FACILITIES -> state.setFaqCategory(sub);
            case IntentTypes.ATTRACTIONS -> state.setAttractionCategory(sub);
            case IntentTypes.RESTAURANT -> {
                if (sub != null) state.setAmbiance(List.of(sub));
            }
            case IntentTypes.ROOM_CONTROL -> state.setRoomControlAction(sub);
            case IntentTypes.DELIVERY -> state.setDeliveryItem(sub);
            case IntentTypes.CONCIERGE -> state.setServiceAction(sub);
        }
    }

    private void extractPriceFilter(AgentState state, String msg) {
        if (PRICE_BUDGET.matcher(msg).find()) state.setMaxPrice(2);
        else if (PRICE_MID.matcher(msg).find()) state.setMaxPrice(3);
        else if (PRICE_UPSCALE.matcher(msg).find()) state.setMaxPrice(4);
    }
}
