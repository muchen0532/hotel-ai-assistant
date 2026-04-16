package com.hotel.ai.service.agent;

import com.hotel.ai.model.dto.*;
import com.hotel.ai.model.entity.*;
import com.hotel.ai.repository.*;
import com.hotel.ai.service.tools.QdrantSearchService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AgentPipelineTest {

    @Autowired AgentPipeline pipeline;
    @Autowired FaqRepository faqRepo;

    @MockBean ChatLanguageModel llm;
    @MockBean QdrantSearchService qdrantService;

    @Test
    void wifiQuery_populatesInfoGrid() throws Exception {
        // Arrange: mock LLM intro response
        when(llm.generate(anyString()))
                .thenReturn("Here are your WiFi credentials:");

        // Mock Qdrant (not used for FAQ path, but must not throw)
        when(qdrantService.searchRestaurantNames(anyString(), anyString(), any()))
                .thenReturn(List.of());

        AgentState state = AgentState.builder()
                .hotelId("grandplc")
                .sessionId("test-session")
                .userMessage("What is the WiFi password?")
                .hotelName("Grand Palace Hotel")
                .hotelLocation("Taipei, Taiwan")
                .guestName("Test Guest")
                .roomNumber("1204")
                .hotelWelcomeChips(List.of())
                .history(List.of())
                .build();

        // Use a no-op emitter to capture results
        SseEmitter emitter = new SseEmitter();
        pipeline.run(state, emitter);

        // Assert state was populated
        assertThat(state.getIntent()).isEqualTo("faq");
        assertThat(state.getResponseMeta()).isNotNull();
        assertThat(state.getResponseMeta().getInfoGrid()).isNotEmpty();
        assertThat(state.getResponseText()).isNotBlank();
    }

    // Mockito any() helper for int
    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
