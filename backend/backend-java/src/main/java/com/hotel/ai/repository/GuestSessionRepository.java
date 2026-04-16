package com.hotel.ai.repository;

import com.hotel.ai.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface GuestSessionRepository extends JpaRepository<GuestSession, String> {

    @Query("""
            SELECT gs FROM GuestSession gs
            JOIN FETCH gs.room
            JOIN FETCH gs.hotel
            WHERE gs.id = :id
              AND gs.active = true
              AND gs.expiresAt > :now
            """)
    Optional<GuestSession> findActiveSession(@Param("id") UUID id,
                                             @Param("now") OffsetDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE GuestSession s SET s.active = false WHERE s.id = :id")
    void invalidate(@Param("id") String id);
}

