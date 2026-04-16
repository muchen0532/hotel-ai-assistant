package com.hotel.ai.model.dto.session;

import com.hotel.ai.model.dto.HotelConfig;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class SessionInitResponse {
    private UUID sessionId;
    private HotelConfig hotel;
}
