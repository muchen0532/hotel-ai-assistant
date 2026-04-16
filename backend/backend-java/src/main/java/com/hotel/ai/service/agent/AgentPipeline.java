package com.hotel.ai.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.ai.model.dto.agent.AgentMessageMeta;
import com.hotel.ai.model.dto.agent.AgentState;
import com.hotel.ai.model.dto.sseChunk.SseChunkDone;
import com.hotel.ai.model.dto.sseChunk.SseChunkError;
import com.hotel.ai.model.dto.sseChunk.SseChunkMeta;
import com.hotel.ai.model.dto.sseChunk.SseChunkTextDelta;
import com.hotel.ai.model.entity.*;
import com.hotel.ai.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * AgentPipeline: Connect nodes and trigger SSE transmission:
 *
 *   [IntentClassifier] → [ToolExecutor] → [ResponseGenerator]
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentPipeline {

    private final IntentClassifier  intentClassifier;
    private final ToolExecutor      toolExecutor;
    private final ResponseGenerator responseGenerator;

    private final ChatMessageRepository  chatMessageRepo;
    private final ObjectMapper           objectMapper;

    public void run(AgentState state, SseEmitter emitter) {
        try {
            intentClassifier.classify(state);
            log.info("[Pipeline] session={} hotel={} intent={}", state.getSessionId(), state.getHotelId(), state.getIntent());

            toolExecutor.execute(state, trace -> {
                try {
                    AgentMessageMeta traceMeta = AgentMessageMeta.builder().agentTrace(trace).build();
                    emitter.send(SseEmitter.event().data(toJson(SseChunkMeta.builder().meta(traceMeta).build())));
                } catch (IOException e) {
                    log.debug("[Pipeline] SSE write during trace: {}", e.getMessage());
                }
            });

            responseGenerator.generate(state);

            streamText(state.getResponseText(), emitter);

            AgentMessageMeta meta = state.getResponseMeta();
            if (meta != null) {
                emitter.send(SseEmitter.event().data(toJson(SseChunkMeta.builder().meta(meta).build())));
            }

            emitter.send(SseEmitter.event().data(toJson(new SseChunkDone())));
            emitter.complete();

            persistAsync(state);

        } catch (Exception e) {
            log.error("[Pipeline] Fatal error: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().data(toJson(SseChunkError.builder().error("Agent error: " + e.getMessage()).build())));
                emitter.complete();
            } catch (IOException ignored) {
                emitter.completeWithError(e);
            }
        }
    }


    private void streamText(String text, SseEmitter emitter) throws IOException, InterruptedException {
        if (text == null || text.isBlank()) return;

        String[] words = text.split(" ");
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            buf.append(words[i]).append(" ");

            if ((i + 1) % 3 == 0 || i == words.length - 1) {
                emitter.send(SseEmitter.event()
                        .data(toJson(SseChunkTextDelta.builder()
                                .delta(buf.toString())
                                .build())));
                buf.setLength(0);
                Thread.sleep(20);
            }
        }
    }


    private void persistAsync(AgentState state) {
        Thread.ofVirtual().start(() -> {
            try {
                chatMessageRepo.save(ChatMessage.builder()
                        .sessionId(state.getSessionId())
                        .hotelId(state.getHotelId())
                        .role("user")
                        .content(state.getUserMessage())
                        .build());

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


    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
