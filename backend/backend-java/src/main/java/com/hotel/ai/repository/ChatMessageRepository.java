package com.hotel.ai.repository;

import com.hotel.ai.model.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.sessionId = :sessionId
            ORDER BY m.createdAt DESC
            LIMIT :limit
            """)
    List<ChatMessage> findRecentBySession(@Param("sessionId") UUID sessionId,
                                          @Param("limit") int limit);
}
