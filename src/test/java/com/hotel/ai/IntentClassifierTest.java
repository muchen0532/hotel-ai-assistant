package com.hotel.ai.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.ai.model.dto.AgentState;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;

class IntentClassifierTest {

    @Mock
    ChatLanguageModel llm;

    IntentClassifier classifier;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        classifier = new IntentClassifier(llm, new ObjectMapper());
    }

    @ParameterizedTest(name = "[{index}] ''{0}'' → intent={1}")
    @CsvSource({
            "What is the WiFi password?,            faq,         wifi",
            "WiFi 密碼,                              faq,         wifi",
            "What time is breakfast served?,        faq,         breakfast",
            "What time is check-out?,               faq,         checkout",
            "退房時間,                               faq,         checkout",
            "Is the pool open?,                     facilities,  pool",
            "Does the hotel have a gym?,            facilities,  gym",
            "Tell me about the spa,                 facilities,  spa",
            "Recommend a romantic restaurant,       restaurant,  null",
            "I want to find a nice place for dinner,restaurant,  null",
            "What are the nearby attractions?,      attractions, null",
            "Are there any temples nearby?,         attractions, culture",
            "Show me nature spots near the hotel,   attractions, nature",
    })
    void ruleBasedClassification(String message, String expectedIntent, String expectedSub) {
        AgentState state = AgentState.builder()
                .userMessage(message)
                .hotelId("grandplc")
                .build();

        classifier.classify(state);

        assertThat(state.getIntent()).isEqualTo(expectedIntent);

        if (!"null".equals(expectedSub)) {
            if (IntentClassifier.INTENT_FAQ.equals(expectedIntent)) {
                assertThat(state.getFaqCategory()).isEqualTo(expectedSub);
            } else if (IntentClassifier.INTENT_ATTRACTIONS.equals(expectedIntent)) {
                assertThat(state.getAttractionCategory()).isEqualTo(expectedSub);
            }
        }
    }

    @ParameterizedTest(name = "[{index}] price: ''{0}'' → maxPrice={1}")
    @CsvSource({
            "recommend a cheap restaurant,    2",
            "budget dining options,           2",
            "fine dining tonight,             4",
            "luxury restaurant for anniversary, 4",
    })
    void priceFilterExtraction(String message, int expectedPrice) {
        AgentState state = AgentState.builder()
                .userMessage(message)
                .hotelId("grandplc")
                .build();

        classifier.classify(state);

        assertThat(state.getMaxPrice()).isEqualTo(expectedPrice);
    }
}
