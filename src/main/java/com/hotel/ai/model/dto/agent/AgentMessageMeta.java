package com.hotel.ai.model.dto.agent;

import com.hotel.ai.model.dto.RestaurantCard;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AgentMessageMeta {
    private List<AgentInfoGridItem> infoGrid;
    private List<RestaurantCard> cards;
    private List<AgentTraceStep> agentTrace;
    private List<String> chips;
}