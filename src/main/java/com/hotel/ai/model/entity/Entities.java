package com.hotel.ai.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

// ── Room ──────────────────────────────────────────────────────────────────────

@Entity
@Table(name = "rooms")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Room {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(name = "room_number", nullable = false)
    private String roomNumber;

    @Column(name = "room_type")
    private String roomType;

    private Integer floor;

    @Column(name = "max_guests")
    private Integer maxGuests;

    @Column(name = "is_active")
    private boolean active = true;
}

// ── GuestSession ──────────────────────────────────────────────────────────────

@Entity
@Table(name = "guest_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class GuestSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "guest_name", nullable = false)
    private String guestName;

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "is_active")
    private boolean active = true;

    @PrePersist
    void prePersist() {
        checkedInAt = OffsetDateTime.now();
    }
}

// ── Faq ──────────────────────────────────────────────────────────────────────

@Entity
@Table(name = "faqs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Faq {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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
    private List<java.util.Map<String, String>> answerGrid;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "follow_up_chips", columnDefinition = "jsonb")
    private List<String> followUpChips;

    private Integer priority = 0;

    @Column(name = "is_active")
    private boolean active = true;
}

// ── Facility ──────────────────────────────────────────────────────────────────

@Entity
@Table(name = "facilities")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Facility {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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

// ── Restaurant ────────────────────────────────────────────────────────────────

@Entity
@Table(name = "restaurants")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Restaurant {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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

// ── Attraction ────────────────────────────────────────────────────────────────

@Entity
@Table(name = "attractions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Attraction {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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

// ── ChatMessage ───────────────────────────────────────────────────────────────

@Entity
@Table(name = "chat_messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class ChatMessage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

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
