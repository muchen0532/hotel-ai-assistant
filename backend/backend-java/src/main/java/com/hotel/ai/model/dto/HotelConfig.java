package com.hotel.ai.model.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class HotelConfig {
    private String hotelId;
    private String name;
    private String tagline;
    private String location;
    private String locale;
    private Map<String, String> theme;
    private List<Map<String, String>> quickActions;
    private List<String> welcomeChips;
}