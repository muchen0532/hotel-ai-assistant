package com.hotel.ai.service.agent;

import com.hotel.ai.model.dto.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hotel.ai.service.agent.IntentClassifier.*;

/**
 * Node 3 — Response Generation
 *
 * Decision logic:
 *   tools returned data  → LLM writes a short 1-sentence intro
 *                          (structured cards/grid shown by frontend automatically)
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

    // ── System prompts ────────────────────────────────────────────────────────

    private static final PromptTemplate INTRO_PROMPT = PromptTemplate.from("""
            You are a warm, helpful AI concierge for {{hotelName}} in {{location}}.
            Guest name: {{guestName}}, Room: {{room}}.

            Tool data was retrieved for: {{intent}}.
            Items found: {{items}}

            Write exactly ONE natural sentence to introduce this information to the guest.
            Do NOT list the data — just set context warmly.
            Use **bold** for the hotel name or key term if helpful.
            Guest asked: "{{question}}"
            """);

    private static final PromptTemplate FALLBACK_PROMPT = PromptTemplate.from("""
            You are a helpful AI concierge for {{hotelName}} in {{location}}.
            Guest: {{guestName}}, Room: {{room}}.

            Conversation history:
            {{history}}

            Guest's question: {{question}}

            Answer helpfully and concisely (under 150 words).
            Use markdown formatting where helpful (**bold**, bullet lists).
            If you don't have specific details, suggest they contact the front desk.
            Do NOT mention that you are an AI or that you lack information.
            """);

    // ── Public entry ──────────────────────────────────────────────────────────

    public AgentState generate(AgentState state) {
        boolean hasData = hasToolData(state);

        String text;
        if (hasData) {
            text = generateIntro(state);
        } else {
            text = generateFullAnswer(state);
            // Attach welcome chips as follow-up suggestions for general answers
            if (state.getResponseMeta() == null || state.getResponseMeta().getChips() == null) {
                List<String> chips = state.getHotelWelcomeChips();
                if (chips != null && !chips.isEmpty()) {
                    MessageMeta existing = state.getResponseMeta() != null
                            ? state.getResponseMeta()
                            : MessageMeta.builder().build();
                    state.setResponseMeta(MessageMeta.builder()
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

    // ── Intro (tool data found) ───────────────────────────────────────────────

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

            return llm.generate(prompt).trim();
        } catch (Exception e) {
            log.warn("[Response] Intro generation failed: {}", e.getMessage());
            return "Here's what I found for you:";
        }
    }

    // ── Full answer (no tool data) ────────────────────────────────────────────

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
            return "I'm sorry, I had trouble processing that. Please try again or contact the front desk.";
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean hasToolData(AgentState state) {
        MessageMeta meta = state.getResponseMeta();
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
                    // Extract name reflectively for any entity type
                    try {
                        return r.getClass().getMethod("getName").invoke(r).toString();
                    } catch (Exception ignored) {
                        return r.toString();
                    }
                })
                .collect(Collectors.joining(", "));
    }

    private String formatHistory(List<ChatDto.HistoryMessage> history) {
        if (history == null || history.isEmpty()) return "(no prior conversation)";
        return history.stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));
    }

    private String orEmpty(String s) {
        return s != null ? s : "";
    }
}
