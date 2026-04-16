package com.hotel.ai.repository;

import com.hotel.ai.model.entity.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, String> {

    Optional<Hotel> findByCodeAndActiveTrue(String code);

    Optional<Hotel> findByIdAndActiveTrue(String id);
}