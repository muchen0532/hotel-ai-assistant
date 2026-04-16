package com.hotel.ai.repository;

import com.hotel.ai.model.entity.Faq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

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