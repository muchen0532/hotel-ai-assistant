package com.hotel.ai.controller;

import com.hotel.ai.model.dto.session.SessionInitRequest;
import com.hotel.ai.model.dto.session.SessionInitResponse;
import com.hotel.ai.model.dto.session.SessionLogoutRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.hotel.ai.service.SessionService;

import java.util.Map;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
class SessionController {

    private final SessionService sessionService;

    @PostMapping("/init")
    public ResponseEntity<SessionInitResponse> init (@Valid @RequestBody SessionInitRequest req) {
        SessionInitResponse resp = sessionService.initSession(req);
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> logout(
            @Valid @RequestBody SessionLogoutRequest req) {
        sessionService.logout(req.getSessionId());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}