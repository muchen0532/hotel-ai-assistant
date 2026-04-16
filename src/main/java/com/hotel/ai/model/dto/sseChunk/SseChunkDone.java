package com.hotel.ai.model.dto.sseChunk;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SseChunkDone {
    private final String type = "done";
}

