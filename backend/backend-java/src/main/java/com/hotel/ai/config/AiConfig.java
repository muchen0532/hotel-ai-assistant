package com.hotel.ai.config;

// LLM + Embedding


import java.time.Duration;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class AiConfig {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String openAiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name:gpt-4o-mini}")
    private String chatModel;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    @Value("${jina.api-key}")
    private String jinaApiKey;

    @Value("${jina.model}")
    private String jinaModel;

    @Value("${jina.url:https://api.jina.ai/v1/embeddings}")
    private String jinaUrl;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("[AI] ChatModel: {}", chatModel);
        return OpenAiChatModel.builder()
                .apiKey(openAiKey)
                .modelName(chatModel)
                .baseUrl(baseUrl)
                .temperature(0.3)
                .maxTokens(512)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("[AI] EmbeddingModel: {}", jinaModel);
        return JinaEmbeddingModel.builder()
                .apiKey(jinaApiKey)
                .modelName(jinaModel)
                .url(jinaUrl)
                .task("retrieval.query")
                .build();
    }
}
