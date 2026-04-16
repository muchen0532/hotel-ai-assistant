package com.hotel.ai.model.dto.agent;

import com.hotel.ai.model.dto.chat.ChatHistoryMessage;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentState {

    // ── Input ───────────
    private String hotelId;
    private UUID sessionId;
    private String userMessage;
    private String hotelName;
    private String hotelLocation;
    private String hotelLocale;
    private List<String> hotelWelcomeChips;
    private String guestName;
    private String roomNumber;
    private List<ChatHistoryMessage> history;

    // ── Intent Classification ───────────────────────────────────——───
    private String intent;

    // faq / facilities
    private String faqCategory;
    // restaurant
    private List<String> ambiance;
    private Integer maxPrice;
    private Double minRating;

    // attractions
    private String attractionCategory;

    // room_control
    private String roomControlAction;
    private String roomControlValue;

    // delivery
    private String deliveryItem;
    private Integer deliveryQuantity;

    // concierge
    private String serviceAction;
    private String serviceDetail;

    // ── Tool Execution ───────────────────────────────────────────────
    private List<Object> toolResults;
    private List<AgentTraceStep> toolTrace;

    // ── Response Generation ──────────────────────────────────────────
    private String responseText;
    private AgentMessageMeta responseMeta;
}