package com.hotel.ai.repository;

import com.hotel.ai.model.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

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