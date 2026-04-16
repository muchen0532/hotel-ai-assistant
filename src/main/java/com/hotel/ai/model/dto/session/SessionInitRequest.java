package com.hotel.ai.model.dto.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SessionInitRequest {
    @NotBlank
    @Size(min = 3, max = 12)
    private String hotelCode;

    @NotBlank @Size(min = 1, max = 8)
    private String roomNumber;

    @NotBlank @Size(min = 1, max = 60)
    private String guestName;
}