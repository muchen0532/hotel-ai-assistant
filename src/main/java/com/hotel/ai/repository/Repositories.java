package com.hotel.ai.repository;

import com.hotel.ai.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

// ── Hotel ─────────────────────────────────────────────────────────────────────

@Repository
public interface HotelRepository extends JpaRepository<Hotel, String> {

    Optional<Hotel> findByCodeAndActiveTrue(String code);

    Optional<Hotel> findByIdAndActiveTrue(String id);
}

// ── Room ──────────────────────────────────────────────────────────────────────

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    @Query("SELECT r FROM Room r WHERE r.hotel.id = :hotelId AND r.roomNumber = :roomNumber AND r.active = true")
    Optional<Room> findActiveRoom(@Param("hotelId") String hotelId,
                                  @Param("roomNumber") String roomNumber);
}

// ── GuestSession ──────────────────────────────────────────────────────────────

@Repository
public interface GuestSessionRepository extends JpaRepository<GuestSession, String> {

    @Query("""
            SELECT s FROM GuestSession s
            WHERE s.id = :id
              AND s.active = true
              AND s.expiresAt > :now
            """)
    Optional<GuestSession> findActiveSession(@Param("id") String id,
                                              @Param("now") OffsetDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE GuestSession s SET s.active = false WHERE s.id = :id")
    void invalidate(@Param("id") String id);
}

// ── Faq ──────────────────────────────────────────────────────────────────────

@Repository
public interface FaqRepository extends JpaRepository<Faq, Long> {

    @Query("""
            SELECT f FROM Faq f
            WHERE f.hotel.id = :hotelId
              AND f.category  = :category
              AND f.active    = true
            ORDER BY f.priority DESC
            """)
    List<Faq> findByHotelAndCategory(@Param("hotelId") String hotelId,
                                      @Param("category") String category);

    @Query("""
            SELECT f FROM Faq f
            WHERE f.hotel.id = :hotelId
              AND f.active   = true
            ORDER BY f.priority DESC
            """)
    List<Faq> findAllByHotel(@Param("hotelId") String hotelId);
}

// ── Facility ──────────────────────────────────────────────────────────────────

@Repository
public interface FacilityRepository extends JpaRepository<Facility, Long> {

    @Query("""
            SELECT f FROM Facility f
            WHERE f.hotel.id = :hotelId
              AND f.active   = true
            ORDER BY f.category, f.name
            """)
    List<Facility> findAllByHotel(@Param("hotelId") String hotelId);

    @Query("""
            SELECT f FROM Facility f
            WHERE f.hotel.id = :hotelId
              AND f.category = :category
              AND f.active   = true
            ORDER BY f.name
            """)
    List<Facility> findByHotelAndCategory(@Param("hotelId") String hotelId,
                                           @Param("category") String category);
}

// ── Restaurant ────────────────────────────────────────────────────────────────

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    @Query("""
            SELECT r FROM Restaurant r
            WHERE r.hotel.id = :hotelId
              AND r.active   = true
              AND r.rating   >= :minRating
            ORDER BY r.rating DESC
            LIMIT :limit
            """)
    List<Restaurant> findTopRated(@Param("hotelId") String hotelId,
                                   @Param("minRating") double minRating,
                                   @Param("limit") int limit);

    // Filter by ambiance using native Postgres array overlap operator
    @Query(value = """
            SELECT * FROM restaurants
            WHERE hotel_id   = :hotelId
              AND is_active   = true
              AND rating     >= :minRating
              AND (:maxPrice IS NULL OR price_range <= :maxPrice)
              AND (:ambiance IS NULL OR ambiance && CAST(:ambiance AS text[]))
            ORDER BY rating DESC
            LIMIT :lim
            """, nativeQuery = true)
    List<Restaurant> findFiltered(@Param("hotelId") String hotelId,
                                   @Param("minRating") double minRating,
                                   @Param("maxPrice") Integer maxPrice,
                                   @Param("ambiance") String ambiance,
                                   @Param("lim") int limit);
}

// ── Attraction ────────────────────────────────────────────────────────────────

@Repository
public interface AttractionRepository extends JpaRepository<Attraction, Long> {

    @Query("""
            SELECT a FROM Attraction a
            WHERE a.hotel.id = :hotelId
              AND a.active   = true
              AND a.rating   >= :minRating
            ORDER BY a.rating DESC
            LIMIT :limit
            """)
    List<Attraction> findTopRated(@Param("hotelId") String hotelId,
                                   @Param("minRating") double minRating,
                                   @Param("limit") int limit);

    @Query("""
            SELECT a FROM Attraction a
            WHERE a.hotel.id  = :hotelId
              AND a.category  = :category
              AND a.active    = true
              AND a.rating   >= :minRating
            ORDER BY a.rating DESC
            LIMIT :limit
            """)
    List<Attraction> findByCategory(@Param("hotelId") String hotelId,
                                     @Param("category") String category,
                                     @Param("minRating") double minRating,
                                     @Param("limit") int limit);
}

// ── ChatMessage ───────────────────────────────────────────────────────────────

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.sessionId = :sessionId
            ORDER BY m.createdAt DESC
            LIMIT :limit
            """)
    List<ChatMessage> findRecentBySession(@Param("sessionId") String sessionId,
                                           @Param("limit") int limit);
}
