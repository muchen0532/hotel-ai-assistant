package com.hotel.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import okhttp3.*;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;

@Builder
@Slf4j
public class JinaEmbeddingModel implements EmbeddingModel {

    private static final MediaType JSON_TYPE = MediaType.get("application/json");

    private final String apiKey;
    private final String modelName;
    private final String url;

    @Builder.Default
    private final String task = "retrieval.query";

    @Builder.Default
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    @Builder.Default
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        log.debug("JINA EMBED HIT");
        try {
            List<String> texts = segments.stream()
                    .map(TextSegment::text)
                    .toList();

            String body = mapper.writeValueAsString(Map.of(
                    "model", modelName,
                    "input", texts,
                    "task",  task
            ));

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body, JSON_TYPE))
                    .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "unknown";
                    throw new RuntimeException("Jina API error " + response.code() + ": " + err);
                }

                String responseBody = response.body().string();

                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = mapper.readValue(responseBody, Map.class);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) parsed.get("data");

                List<Embedding> embeddings = data.stream()
                        .map(item -> {
                            @SuppressWarnings("unchecked")
                            List<Double> vector = (List<Double>) item.get("embedding");
                            float[] floats = new float[vector.size()];
                            for (int i = 0; i < vector.size(); i++) {
                                floats[i] = vector.get(i).floatValue();
                            }
                            return Embedding.from(floats);
                        })
                        .toList();

                log.debug("[Jina] Embedded {} segments via task={}", segments.size(), task);
                return Response.from(embeddings);
            }
        } catch (Exception e) {
            throw new RuntimeException("Jina embedding failed: " + e.getMessage(), e);
        }
    }
}
