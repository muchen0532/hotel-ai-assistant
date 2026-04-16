package com.hotel.ai.controller;

import com.hotel.ai.model.dto.chat.ChatStreamRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.hotel.ai.service.ChatService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
class ChatController {

    private final ChatService chatService;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @PostMapping("/stream")
    public SseEmitter stream(@Valid @RequestBody ChatStreamRequest req) {
        SseEmitter emitter = new SseEmitter(90_000L);

        executor.submit(() -> {
            try {
                chatService.handleStream(req, emitter);
            } catch (SecurityException e) {
                log.warn("[Chat] Auth error: {}", e.getMessage());
                sendError(emitter, e.getMessage());
            } catch (Exception e) {
                log.error("[Chat] Unhandled: {}", e.getMessage(), e);
                sendError(emitter, "Internal server error");
            }
        });

        return emitter;
    }

    private void sendError(SseEmitter emitter, String msg) {
        try {
            emitter.send(SseEmitter.event()
                    .data("{\"type\":\"error\",\"error\":\"" + msg + "\"}"));
            emitter.complete();
        } catch (Exception ignored) {
            emitter.completeWithError(new RuntimeException(msg));
        }
    }
}