package com.hotel.ai.model.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "hotel_id", nullable = false)
    private String hotelId;

    @Column(nullable = false)
    private String role;     // "user" | "assistant"

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    private String intent;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}