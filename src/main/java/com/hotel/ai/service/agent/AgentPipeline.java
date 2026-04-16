package com.hotel.ai.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.ai.model.dto.*;
import com.hotel.ai.model.entity.*;
import com.hotel.ai.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AgentPipeline — the top-level orchestrator.
 *
 * Wires the 3 nodes together and drives SSE emission:
 *
 *   [IntentClassifier] → [ToolExecutor] → [ResponseGenerator]
 *
 * SSE emission order:
 *   1. Trace updates (live, during ToolExecutor)
 *   2. Text tokens (word-by-word during ResponseGenerator)
 *   3. Final meta chunk (cards / infoGrid / chips)
 *   4. done
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentPipeline {

    private final IntentClassifier  intentClassifier;
    private final ToolExecutor      toolExecutor;
    private final ResponseGenerator responseGenerator;

    private final GuestSessionRepository sessionRepo;
    private final ChatMessageRepository  chatMessageRepo;
    private final ObjectMapper           objectMapper;

    // ── Public entry ──────────────────────────────────────────────────────────

    public void run(AgentState state, SseEmitter emitter) {
        try {
            // ── Node 1: Intent ────────────────────────────────────────────────
            intentClassifier.classify(state);
            log.info("[Pipeline] session={} hotel={} intent={}",
                    state.getSessionId(), state.getHotelId(), state.getIntent());

            // ── Node 2: Tools (with live trace streaming) ─────────────────────
            toolExecutor.execute(state, trace -> {
                // Emit intermediate trace update to SSE
                try {
                    MessageMeta traceMeta = MessageMeta.builder().agentTrace(trace).build();
                    emitter.send(SseEmitter.event()
                            .data(toJson(new SseChunk.Meta(traceMeta))));
                } catch (IOException e) {
                    log.debug("[Pipeline] SSE write during trace: {}", e.getMessage());
                }
            });

            // ── Node 3: Response (stream text word by word) ───────────────────
            responseGenerator.generate(state);

            streamText(state.getResponseText(), emitter);

            // ── Send final meta (cards / infoGrid / chips / agentTrace) ───────
            MessageMeta meta = state.getResponseMeta();
            if (meta != null) {
                emitter.send(SseEmitter.event()
                        .data(toJson(new SseChunk.Meta(meta))));
            }

            // ── Done ──────────────────────────────────────────────────────────
            emitter.send(SseEmitter.event().data(toJson(new SseChunk.Done())));
            emitter.complete();

            // ── Persist chat messages asynchronously ──────────────────────────
            persistAsync(state);

        } catch (Exception e) {
            log.error("[Pipeline] Fatal error: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event()
                        .data(toJson(new SseChunk.Error("Agent error: " + e.getMessage()))));
                emitter.complete();
            } catch (IOException ignored) {
                emitter.completeWithError(e);
            }
        }
    }

    // ── Text streaming ────────────────────────────────────────────────────────

    private void streamText(String text, SseEmitter emitter) throws IOException, InterruptedException {
        if (text == null || text.isBlank()) return;

        String[] words = text.split(" ");
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            buf.append(words[i]).append(" ");

            // Send every 3 words (balances latency vs chunk count)
            if ((i + 1) % 3 == 0 || i == words.length - 1) {
                emitter.send(SseEmitter.event()
                        .data(toJson(SseChunk.TextDelta.builder()
                                .delta(buf.toString())
                                .build())));
                buf.setLength(0);
                Thread.sleep(20); // ~50 wpm realistic pacing
            }
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void persistAsync(AgentState state) {
        Thread.ofVirtual().start(() -> {
            try {
                // User message
                chatMessageRepo.save(ChatMessage.builder()
                        .sessionId(state.getSessionId())
                        .hotelId(state.getHotelId())
                        .role("user")
                        .content(state.getUserMessage())
                        .build());

                // Assistant message
                chatMessageRepo.save(ChatMessage.builder()
                        .sessionId(state.getSessionId())
                        .hotelId(state.getHotelId())
                        .role("assistant")
                        .content(state.getResponseText())
                        .intent(state.getIntent())
                        .build());

            } catch (Exception e) {
                log.warn("[Pipeline] Failed to persist messages: {}", e.getMessage());
            }
        });
    }

    // ── JSON helper ───────────────────────────────────────────────────────────

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
