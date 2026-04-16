package com.hotel.ai.repository;

import com.hotel.ai.model.entity.Facility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

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
