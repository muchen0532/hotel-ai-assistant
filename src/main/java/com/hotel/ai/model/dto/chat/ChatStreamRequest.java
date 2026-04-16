package com.hotel.ai.model.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
public class ChatStreamRequest {
    @NotBlank
    private String sessionId;

    @NotBlank
    private String hotelId;

    @NotBlank @Size(min = 1, max = 2000)
    private String message;

    // Optional: client can send last N messages for context
    private List<ChatHistoryMessage> history;
}