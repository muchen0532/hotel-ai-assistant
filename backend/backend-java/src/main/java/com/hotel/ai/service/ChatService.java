package com.hotel.ai.service;

import com.hotel.ai.model.dto.agent.AgentState;
import com.hotel.ai.model.dto.chat.ChatHistoryMessage;
import com.hotel.ai.model.dto.chat.ChatStreamRequest;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final SessionService sessionService;
    private final HotelRepository hotelRepo;
    private final ChatMessageRepository chatMessageRepo;
    private final AgentPipeline agentPipeline;

    @Value("${agent.history-window:10}")
    private int historyWindow;


    public void handleStream(ChatStreamRequest req, SseEmitter emitter) {
        GuestSession session = sessionService.validateSession(req.getSessionId(), req.getHotelId());

        Hotel hotel = hotelRepo.findByIdAndActiveTrue(req.getHotelId())
                .orElseThrow(() -> new IllegalArgumentException("Hotel not found: " + req.getHotelId()));

        List<ChatHistoryMessage> history = req.getHistory() != null && !req.getHistory().isEmpty()
                ? req.getHistory()
                : loadHistory(req.getSessionId());

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

        agentPipeline.run(state, emitter);
    }


    private List<ChatHistoryMessage> loadHistory(UUID sessionId) {
        try {
            List<ChatMessage> msgList = chatMessageRepo
                    .findRecentBySession(sessionId, historyWindow);
            return msgList.stream()
                    .map(m -> ChatHistoryMessage.builder()
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
