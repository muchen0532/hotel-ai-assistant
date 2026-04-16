package com.hotel.ai.service.agent;

import com.hotel.ai.model.dto.agent.AgentMessageMeta;
import com.hotel.ai.model.dto.agent.AgentState;
import com.hotel.ai.model.dto.chat.ChatHistoryMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Response Generation
 *
 * Decision logic:
 *   tools returned data  → LLM writes a short 1-sentence intro
 *   no data found        → LLM writes a full helpful answer using conversation history
 *   general intent       → LLM free-form answer
 *
 * Writes: responseText (final state field consumed by ChatController for SSE streaming)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResponseGenerator {

    private final ChatLanguageModel llm;


    private static final PromptTemplate INTRO_PROMPT = PromptTemplate.from("""
        You are a warm, helpful AI concierge for {{hotelName}} in {{location}}.
        你是 {{hotelName}}（位于 {{location}}）的智能酒店礼宾助手。
        
        Guest name: {{guestName}}, Room: {{room}}.
        
        Tool data was retrieved for: {{intent}}.
        Items found: {{items}}
        
        ---
        任务：
        请用自然、礼貌的中文写 **一句话开场说明**，用于引出下面的信息。
        
        要求：
        - 只写一句话（非常重要）
        - 不要列举具体数据内容
        - 语气自然、像酒店服务人员
        - 可以适当使用“您”
        - 可在关键内容使用 **加粗**
        
        Guest asked: "{{question}}"
        """);

    private static final PromptTemplate FALLBACK_PROMPT = PromptTemplate.from("""
        You are a helpful AI concierge for {{hotelName}} in {{location}}.
        你是 {{hotelName}}（位于 {{location}}）的智能酒店礼宾助手。
        
        Guest: {{guestName}}, Room: {{room}}.
        
        ---
        对话历史：
        {{history}}
        
        ---
        用户问题：
        {{question}}
        
        ---
        任务：
        请用中文进行回复，要求：
        
        - 简洁清晰（不超过150字）
        - 语气礼貌自然（符合酒店服务风格）
        - 必要时使用 Markdown（如 **加粗**、列表）
        - 如果信息不确定，可以建议联系前台
        - 不要提及“AI”或“无法获取信息”等系统词
        
        直接给出最终回复，不要解释过程。
        """);


    public AgentState generate(AgentState state) {
        boolean hasData = hasToolData(state);

        String text;
        if (hasData) {
            text = generateIntro(state);
        } else {
            text = generateFullAnswer(state);
            if (state.getResponseMeta() == null || state.getResponseMeta().getChips() == null) {
                List<String> chips = state.getHotelWelcomeChips();
                if (chips != null && !chips.isEmpty()) {
                    AgentMessageMeta existing = state.getResponseMeta() != null
                            ? state.getResponseMeta()
                            : AgentMessageMeta.builder().build();
                    state.setResponseMeta(AgentMessageMeta.builder()
                            .infoGrid(existing.getInfoGrid())
                            .cards(existing.getCards())
                            .agentTrace(existing.getAgentTrace())
                            .chips(chips.stream().limit(4).toList())
                            .build());
                }
            }
        }

        state.setResponseText(text);
        return state;
    }


    private String generateIntro(AgentState state) {
        try {
            String items = summariseResults(state);
            String prompt = INTRO_PROMPT.apply(Map.of(
                    "hotelName", orEmpty(state.getHotelName()),
                    "location",  orEmpty(state.getHotelLocation()),
                    "guestName", orEmpty(state.getGuestName()),
                    "room",      orEmpty(state.getRoomNumber()),
                    "intent",    orEmpty(state.getIntent()),
                    "items",     items,
                    "question",  orEmpty(state.getUserMessage())
            )).text();

            return clean(llm.generate(prompt));
        } catch (Exception e) {
            log.warn("[Response] Intro generation failed: {}", e.getMessage(), e);
            return "以下是我找到的内容：";
        }
    }

    private String clean(String text) {
        return text == null ? "" : text
                .replaceAll("\\n+", "\n")
                .trim();
    }
    private String generateFullAnswer(AgentState state) {
        try {
            String history = formatHistory(state.getHistory());
            String prompt  = FALLBACK_PROMPT.apply(Map.of(
                    "hotelName", orEmpty(state.getHotelName()),
                    "location",  orEmpty(state.getHotelLocation()),
                    "guestName", orEmpty(state.getGuestName()),
                    "room",      orEmpty(state.getRoomNumber()),
                    "history",   history,
                    "question",  orEmpty(state.getUserMessage())
            )).text();

            return llm.generate(prompt).trim();
        } catch (Exception e) {
            log.warn("[Response] Full answer generation failed: {}", e.getMessage());
            return "抱歉，暂时无法处理您的请求，请稍后再试或联系前台。";
        }
    }


    private boolean hasToolData(AgentState state) {
        AgentMessageMeta meta = state.getResponseMeta();
        if (meta == null) return false;
        return (meta.getInfoGrid() != null && !meta.getInfoGrid().isEmpty())
                || (meta.getCards() != null && !meta.getCards().isEmpty());
    }

    private String summariseResults(AgentState state) {
        List<Object> results = state.getToolResults();
        if (results == null || results.isEmpty()) return "none";
        return results.stream()
                .limit(3)
                .map(r -> {
                    try {
                        return r.getClass().getMethod("getName").invoke(r).toString();
                    } catch (Exception ignored) {
                        return r.toString();
                    }
                })
                .collect(Collectors.joining(", "));
    }

    private String formatHistory(List<ChatHistoryMessage> history) {
        if (history == null || history.isEmpty()) return "(no prior conversation)";
        return history.stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));
    }

    private String orEmpty(String s) {
        return s != null ? s : "";
    }
}
