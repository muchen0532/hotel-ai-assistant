package com.hotel.ai.service;

import com.hotel.ai.model.dto.*;
import com.hotel.ai.model.entity.*;
import com.hotel.ai.repository.*;
import com.hotel.ai.service.agent.AgentPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final SessionService        sessionService;
    private final HotelRepository       hotelRepo;
    private final ChatMessageRepository chatMessageRepo;
    private final AgentPipeline         agentPipeline;

    @Value("${agent.history-window:10}")
    private int historyWindow;

    // ── Public entry ──────────────────────────────────────────────────────────

    /**
     * Validates session, loads hotel + history, builds AgentState,
     * then hands off to AgentPipeline which streams SSE via the emitter.
     */
    public void handleStream(ChatDto.StreamRequest req, SseEmitter emitter) {
        // 1. Validate session + cross-hotel security check
        GuestSession session = sessionService.validateSession(req.getSessionId(), req.getHotelId());

        // 2. Load hotel config
        Hotel hotel = hotelRepo.findByIdAndActiveTrue(req.getHotelId())
                .orElseThrow(() -> new IllegalArgumentException("Hotel not found: " + req.getHotelId()));

        // 3. Load recent history (prefer client-provided, fall back to DB)
        List<ChatDto.HistoryMessage> history = req.getHistory() != null && !req.getHistory().isEmpty()
                ? req.getHistory()
                : loadHistory(req.getSessionId());

        // 4. Build initial agent state
        AgentState state = AgentState.builder()
                .hotelId(hotel.getId())
                .sessionId(req.getSessionId())
                .userMessage(req.getMessage())
                .hotelName(hotel.getName())
                .hotelLocation(hotel.getLocation())
                .hotelLocale(hotel.getLocale())
                .hotelWelcomeChips(hotel.getWelcomeChips())
                .guestName(session.getGuestName())
                .roomNumber(session.getRoom().getRoomNumber())
                .history(history)
                .toolResults(Collections.emptyList())
                .build();

        // 5. Run pipeline (blocks until stream complete)
        agentPipeline.run(state, emitter);
    }

    // ── History loading ───────────────────────────────────────────────────────

    private List<ChatDto.HistoryMessage> loadHistory(String sessionId) {
        try {
            List<ChatMessage> msgs = chatMessageRepo
                    .findRecentBySession(sessionId, historyWindow);
            return msgs.stream()
                    .map(m -> ChatDto.HistoryMessage.builder()
                            .role(m.getRole())
                            .content(m.getContent())
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("[Chat] Failed to load history for session={}: {}", sessionId, e.getMessage());
            return List.of();
        }
    }
}
