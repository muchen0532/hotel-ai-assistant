package com.hotel.ai.model.dto.sseChunk;


import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SseChunkMeta {
    private final String type = "meta";
    private Object meta;   // AgentMessageMeta — infoGrid / cards / agentTrace / chips
}
