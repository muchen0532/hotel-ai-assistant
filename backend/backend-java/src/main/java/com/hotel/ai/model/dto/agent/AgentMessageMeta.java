package com.hotel.ai.model.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessageMeta {
    private List<AgentInfoGridItem> infoGrid;
    private List<AgentRestaurantCard> cards;
    private List<AgentTraceStep> agentTrace;
    private List<String> chips;
}