package com.hotel.ai.model.dto.sseChunk;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SseChunkError {
    private final String type = "error";
    private String error;
}
