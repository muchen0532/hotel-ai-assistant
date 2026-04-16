package com.hotel.ai.model.dto.sseChunk;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SseChunkTextDelta {
    private final String type = "text_delta";
    private String delta;
}