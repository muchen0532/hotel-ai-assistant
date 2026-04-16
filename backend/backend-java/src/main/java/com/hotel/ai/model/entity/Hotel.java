package com.hotel.ai.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "hotels")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Hotel {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String tagline;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private String locale;


    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> theme;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quick_actions", columnDefinition = "jsonb")
    private List<Map<String, String>> quickActions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "welcome_chips", columnDefinition = "jsonb")
    private List<String> welcomeChips;

    @Column(name = "qdrant_collection", nullable = false)
    private String qdrantCollection;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
