package com.hotel.ai.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "facilities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Facility {

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

    @Column(name = "location_desc")
    private String locationDesc;

    @Column(name = "open_time")
    private java.time.LocalTime openTime;

    @Column(name = "close_time")
    private java.time.LocalTime closeTime;

    @Column(name = "open_24h")
    private boolean open24h;

    @Column(name = "booking_required")
    private boolean bookingRequired;

    @Column(name = "is_active")
    private boolean active = true;
}
