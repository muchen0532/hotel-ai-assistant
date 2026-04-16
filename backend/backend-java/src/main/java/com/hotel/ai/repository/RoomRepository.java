package com.hotel.ai.repository;

import com.hotel.ai.model.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    @Query("SELECT r FROM Room r WHERE r.hotel.id = :hotelId AND r.roomNumber = :roomNumber AND r.active = true")
    Optional<Room> findActiveRoom(@Param("hotelId") String hotelId,
                                  @Param("roomNumber") String roomNumber);
}
