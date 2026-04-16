package com.hotel.ai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

// ── LLM + Embedding ───────────────────────────────────────────────────────────

@Configuration
@Slf4j
public class AiConfig {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String openAiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name:gpt-4o-mini}")
    private String chatModel;

    @Value("${langchain4j.open-ai.embedding-model.model-name:text-embedding-3-small}")
    private String embedModel;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("[AI] ChatModel: {}", chatModel);
        return OpenAiChatModel.builder()
                .apiKey(openAiKey)
                .modelName(chatModel)
                .temperature(0.3)
                .maxTokens(512)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("[AI] EmbeddingModel: {}", embedModel);
        return OpenAiEmbeddingModel.builder()
                .apiKey(openAiKey)
                .modelName(embedModel)
                .timeout(Duration.ofSeconds(15))
                .build();
    }
}




// ── CORS ──────────────────────────────────────────────────────────────────────

@Configuration
class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}

// ── Agent properties ──────────────────────────────────────────────────────────

@ConfigurationProperties(prefix = "agent")
record AgentProperties(
        RagProperties rag,
        int historyWindow,
        int maxSteps
) {
    record RagProperties(int topK, double scoreCutoff) {}
}
