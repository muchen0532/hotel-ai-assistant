package com.hotel.ai.repository;

import com.hotel.ai.model.entity.Attraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    @Query("SELECT a FROM Attraction a WHERE a.hotel.id = :hotelId AND a.name IN :names AND a.active = true")
    List<Attraction> findByNames(@Param("hotelId") String hotelId, @Param("names") List<String> names);
}
