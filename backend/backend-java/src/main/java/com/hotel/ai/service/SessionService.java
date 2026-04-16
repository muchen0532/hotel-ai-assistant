package com.hotel.ai.service;

import com.hotel.ai.exception.HotelNotFoundException;
import com.hotel.ai.exception.RoomNotFoundException;
import com.hotel.ai.model.dto.*;
import com.hotel.ai.model.dto.session.SessionInitRequest;
import com.hotel.ai.model.dto.session.SessionInitResponse;
import com.hotel.ai.model.entity.*;
import com.hotel.ai.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final HotelRepository hotelRepo;
    private final RoomRepository roomRepo;
    private final GuestSessionRepository sessionRepo;

    @Value("${agent.history-window:10}")
    private int historyWindow;


    @Transactional
    public SessionInitResponse initSession(SessionInitRequest req) {
        String code = req.getHotelCode().toUpperCase().trim();
        String room = req.getRoomNumber().trim();
        String name = req.getGuestName().trim();

        Hotel hotel = hotelRepo.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new HotelNotFoundException("Hotel code '" + code + "' not found"));

        Room dbRoom = roomRepo.findActiveRoom(hotel.getId(), room)
                .orElseThrow(() -> new RoomNotFoundException("Room " + room + " not found. "));

        GuestSession session = GuestSession.builder().hotel(hotel).room(dbRoom).guestName(name).active(true).expiresAt(OffsetDateTime.now().plusHours(24)).build();

        session = sessionRepo.save(session);

        log.info("[Session] Created session={} hotel={} room={} guest={}", session.getId(), hotel.getId(), room, name);

        return SessionInitResponse.builder().sessionId(session.getId()).hotel(toHotelConfig(hotel)).build();
    }

    @Transactional
    public void logout(String sessionId) {
        sessionRepo.invalidate(sessionId);
        log.info("[Session] Invalidated session={}", sessionId);
    }


    public GuestSession validateSession(UUID sessionId, String hotelId) {
        GuestSession session = sessionRepo.findActiveSession(sessionId, OffsetDateTime.now()).orElseThrow(() -> new SecurityException("Session expired or not found. Please check in again."));

        if (!session.getHotel().getId().equals(hotelId)) {
            throw new SecurityException("Session does not belong to hotel " + hotelId);
        }

        return session;
    }


    private HotelConfig toHotelConfig(Hotel h) {
        return HotelConfig.builder().hotelId(h.getId()).name(h.getName()).tagline(h.getTagline()).location(h.getLocation()).locale(h.getLocale()).theme(h.getTheme()).quickActions(h.getQuickActions()).welcomeChips(h.getWelcomeChips()).build();
    }
}
