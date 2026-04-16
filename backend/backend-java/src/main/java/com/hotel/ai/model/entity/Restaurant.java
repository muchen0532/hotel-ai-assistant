package com.hotel.ai.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(nullable = false)
    private String name;

    @Column(name = "cuisine_type", nullable = false)
    private String cuisineType;

    private String emoji;

    @Column(name = "distance_text")
    private String distanceText;

    private java.math.BigDecimal rating;

    @Column(name = "price_range")
    private Integer priceRange;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] ambiance;

    @Column(name = "open_time")
    private java.time.LocalTime openTime;

    @Column(name = "close_time")
    private java.time.LocalTime closeTime;

    @Column(name = "open_24h")
    private boolean open24h;

    @Column(name = "reservation_required")
    private boolean reservationRequired;

    @Column(nullable = false)
    private String tag;

    @Column(name = "tag_text", nullable = false)
    private String tagText;

    @Column(name = "is_active")
    private boolean active = true;
}
