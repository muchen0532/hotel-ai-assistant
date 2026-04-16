package com.hotel.ai.controller;

import com.hotel.ai.model.dto.SessionDto;
import com.hotel.ai.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
class SessionController {

    private final SessionService sessionService;

    @PostMapping("/init")
    public ResponseEntity<SessionDto.InitResponse> init(
            @Valid @RequestBody SessionDto.InitRequest req) {
        SessionDto.InitResponse resp = sessionService.initSession(req);
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> logout(
            @Valid @RequestBody SessionDto.LogoutRequest req) {
        sessionService.logout(req.getSessionId());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}