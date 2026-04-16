package com.hotel.ai.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.ai.model.dto.AgentState;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Node 1 — Intent Classification
 *
 * Flow:
 *   1. Fast rule-based matching (zero LLM cost for common queries)
 *   2. LLM fallback for ambiguous / multi-intent queries
 *
 * Writes: intent, faqCategory, ambiance, maxPrice, attractionCategory
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntentClassifier {

    private final ChatLanguageModel llm;
    private final ObjectMapper objectMapper;

    // ── Intent constants ──────────────────────────────────────────────────────

    public static final String INTENT_FAQ         = "faq";
    public static final String INTENT_FACILITIES  = "facilities";
    public static final String INTENT_RESTAURANT  = "restaurant";
    public static final String INTENT_ATTRACTIONS = "attractions";
    public static final String INTENT_GENERAL     = "general";

    // ── Rule patterns ─────────────────────────────────────────────────────────

    private record Rule(Pattern pattern, String intent, String subCategory) {}

    private static final List<Rule> RULES = List.of(
            rule("wifi|wi-fi|password|internet|network",           INTENT_FAQ,        "wifi"),
            rule("breakfast|brunch|morning.meal|朝食",             INTENT_FAQ,        "breakfast"),
            rule("check.?out|checkout|退房",                       INTENT_FAQ,        "checkout"),
            rule("facilit|amenity|amenities",                       INTENT_FACILITIES, null),
            rule("pool|swimming|泳池",                             INTENT_FACILITIES, "pool"),
            rule("gym|fitness|workout|exercise|健身",              INTENT_FACILITIES, "gym"),
            rule("spa|massage|sauna|溫泉|onsen",                   INTENT_FACILITIES, "spa"),
            rule("restaurant|dinner|lunch|eat|food|dining|meal|餐", INTENT_RESTAURANT, null),
            rule("romantic|date.night|浪漫",                        INTENT_RESTAURANT, "romantic"),
            rule("family.friendly|kids|children|家庭",             INTENT_RESTAURANT, "family"),
            rule("attract|nearby|sightsee|visit|tourism|景點",     INTENT_ATTRACTIONS, null),
            rule("museum|temple|shrine|castle|monument|culture",   INTENT_ATTRACTIONS, "culture"),
            rule("nature|park|beach|mountain|outdoor",             INTENT_ATTRACTIONS, "nature"),
            rule("shop|market|mall|購物",                          INTENT_ATTRACTIONS, "shopping"),
            rule("surf|ski|dive|kayak|adventure|sport",            INTENT_ATTRACTIONS, "sport")
    );

    private static Rule rule(String regex, String intent, String sub) {
        return new Rule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), intent, sub);
    }

    private static final Pattern PRICE_BUDGET  = Pattern.compile("budget|cheap|affordable", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_MID     = Pattern.compile("mid.range|moderate",      Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_UPSCALE = Pattern.compile("upscale|fine.dining|luxury|high.end", Pattern.CASE_INSENSITIVE);

    // ── LLM prompt ────────────────────────────────────────────────────────────

    private static final PromptTemplate CLASSIFY_PROMPT = PromptTemplate.from("""
            You are the intent classifier for a hotel AI concierge.

            Classify the guest message into ONE of:
              faq         - wifi, breakfast, check-out, policies
              facilities  - pool, gym, spa, business centre
              restaurant  - dining recommendations
              attractions - things to do nearby
              general     - anything else

            Also extract args when present:
              faq         → faqCategory: "wifi"|"breakfast"|"checkout"|"facilities"|"general"
              restaurant  → ambiance: ["romantic","family","casual"] (list), maxPrice: 1-4 or null
              attractions → attractionCategory: "culture"|"nature"|"shopping"|"sport" or null

            Guest message: {{message}}

            Respond ONLY with valid JSON, no markdown:
            {"intent":"...","faqCategory":null,"ambiance":[],"maxPrice":null,"attractionCategory":null}
            """);

    // ── Public entry ──────────────────────────────────────────────────────────

    public AgentState classify(AgentState state) {
        String msg = state.getUserMessage();

        // 1. Try rules first (fast + free)
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(msg).find()) {
                applyIntent(state, rule.intent(), rule.subCategory());
                extractPriceFilter(state, msg);
                log.debug("[Intent] Rule match → intent={} sub={}", rule.intent(), rule.subCategory());
                return state;
            }
        }

        // 2. LLM fallback
        try {
            Prompt prompt = CLASSIFY_PROMPT.apply(Map.of("message", msg));
            String raw    = llm.generate(prompt.text());
            // Strip potential markdown fences
            raw = raw.replaceAll("```json|```", "").trim();

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(raw, Map.class);

            state.setIntent((String) parsed.getOrDefault("intent", INTENT_GENERAL));
            state.setFaqCategory((String) parsed.get("faqCategory"));
            state.setAttractionCategory((String) parsed.get("attractionCategory"));
            state.setMaxPrice((Integer) parsed.get("maxPrice"));

            Object ambianceObj = parsed.get("ambiance");
            if (ambianceObj instanceof List<?> list) {
                state.setAmbiance(list.stream().map(Object::toString).toList());
            }

            log.debug("[Intent] LLM classified → {}", state.getIntent());
        } catch (Exception e) {
            log.warn("[Intent] LLM classification failed: {} — using general", e.getMessage());
            state.setIntent(INTENT_GENERAL);
        }

        return state;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyIntent(AgentState state, String intent, String sub) {
        state.setIntent(intent);
        if (INTENT_FAQ.equals(intent)) {
            state.setFaqCategory(sub != null ? sub : "general");
        } else if (INTENT_FACILITIES.equals(intent)) {
            // sub used as category filter in tool call
            state.setFaqCategory(sub);  // reuse field for facility category
        } else if (INTENT_ATTRACTIONS.equals(intent)) {
            state.setAttractionCategory(sub);
        } else if (INTENT_RESTAURANT.equals(intent) && sub != null) {
            state.setAmbiance(List.of(sub));
        }
    }

    private void extractPriceFilter(AgentState state, String msg) {
        if (PRICE_BUDGET.matcher(msg).find())  state.setMaxPrice(2);
        else if (PRICE_MID.matcher(msg).find()) state.setMaxPrice(3);
        else if (PRICE_UPSCALE.matcher(msg).find()) state.setMaxPrice(4);
    }
}
