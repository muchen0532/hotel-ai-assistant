package com.hotel.ai.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

@Entity
@Table(name = "faqs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Faq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String question;

    @Column(nullable = false, columnDefinition = "text")
    private String answer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_grid", columnDefinition = "jsonb")
    private List<Map<String, String>> answerGrid;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "follow_up_chips", columnDefinition = "jsonb")
    private List<String> followUpChips;

    private Integer priority = 0;

    @Column(name = "is_active")
    private boolean active = true;
}