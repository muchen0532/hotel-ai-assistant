package com.hotel.ai.model.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentRestaurantCard {
    private String emoji;
    private String name;
    private String type;
    private String rating;
    private String hours;
    private String distance;
    private String tag;
    private String tagText;
}
