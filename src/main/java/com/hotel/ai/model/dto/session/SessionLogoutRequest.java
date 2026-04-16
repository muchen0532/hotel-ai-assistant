package com.hotel.ai.model.dto.session;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SessionLogoutRequest {
    @NotBlank
    private String sessionId;
}
