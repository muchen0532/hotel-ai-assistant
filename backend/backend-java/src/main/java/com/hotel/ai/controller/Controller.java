package com.hotel.ai.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/api")
class Controller {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "com/hotel/ai/service", "hotel-ai-assistant");
    }
}



