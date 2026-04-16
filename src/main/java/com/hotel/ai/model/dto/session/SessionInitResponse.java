package com.hotel.ai.model.dto.session;

import com.hotel.ai.model.dto.HotelConfig;

@Data
public class SessionInitResponse {
    private String sessionId;
    private HotelConfig hotel;
}
