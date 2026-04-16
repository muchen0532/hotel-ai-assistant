package com.hotel.ai.model.dto.agent;

import com.hotel.ai.model.dto.MessageMeta;
import com.hotel.ai.model.dto.TraceStep;
import com.hotel.ai.model.dto.chat.ChatHistoryMessage;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentState {
    // Input
    private String hotelId;
    private String sessionId;
    private String userMessage;
    private String hotelName;
    private String hotelLocation;
    private String hotelLocale;
    private List<String> hotelWelcomeChips;
    private String guestName;
    private String roomNumber;
    private List<ChatHistoryMessage> history;

    // Node 1 output
    private String intent;
    // "faq" | "facilities" | "restaurant" | "attractions" | "general"
    private String faqCategory;
    private List<String> ambiance;
    private Integer maxPrice;
    private Double minRating;
    private String attractionCategory;

    // Node 2 output
    private List<Object> toolResults;
    private List<TraceStep> toolTrace;

    // Node 3 output
    private String responseText;
    private MessageMeta responseMeta;
}
