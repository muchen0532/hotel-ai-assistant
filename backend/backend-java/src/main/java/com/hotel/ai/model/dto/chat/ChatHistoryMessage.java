package com.hotel.ai.model.dto.chat;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatHistoryMessage {
    private String role;     // "user" | "assistant"
    private String content;
}
