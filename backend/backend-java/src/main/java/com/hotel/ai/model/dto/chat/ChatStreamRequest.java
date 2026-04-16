package com.hotel.ai.model.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;


@Getter
@Setter
public class ChatStreamRequest {
    @NotNull
    private UUID sessionId;

    @NotBlank
    private String hotelId;

    @NotBlank @Size(min = 1, max = 2000)
    private String message;

    // Optional: client can send last N messages for context
    private List<ChatHistoryMessage> history;
}