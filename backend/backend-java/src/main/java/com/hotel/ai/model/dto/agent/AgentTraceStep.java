package com.hotel.ai.model.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentTraceStep {
    private String label;
    private String status;  // "pending" | "running" | "done" | "error"
}
