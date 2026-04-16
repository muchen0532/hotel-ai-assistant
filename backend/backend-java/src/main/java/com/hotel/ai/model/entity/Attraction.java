package com.hotel.ai.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "attractions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    private String emoji;

    @Column(name = "distance_text")
    private String distanceText;

    private java.math.BigDecimal rating;

    @Column(name = "open_time")
    private java.time.LocalTime openTime;

    @Column(name = "close_time")
    private java.time.LocalTime closeTime;

    @Column(name = "open_24h")
    private boolean open24h;

    @Column(name = "entry_fee")
    private String entryFee;

    @Column(nullable = false)
    private String tag;

    @Column(name = "tag_text", nullable = false)
    private String tagText;

    @Column(name = "is_active")
    private boolean active = true;
}