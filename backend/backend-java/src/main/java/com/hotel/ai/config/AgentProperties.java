package com.hotel.ai.config;

// Agent properties

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
record AgentProperties(
        RagProperties rag,
        int historyWindow,
        int maxSteps
) {
    record RagProperties(int topK, double scoreCutoff) {}
}
