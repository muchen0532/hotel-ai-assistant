package com.hotel.ai.service;

import com.hotel.ai.exception.HotelNotFoundException;
import com.hotel.ai.exception.RoomNotFoundException;
import com.hotel.ai.model.dto.*;
import com.hotel.ai.model.entity.*;
import com.hotel.ai.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final HotelRepository        hotelRepo;
    private final RoomRepository         roomRepo;
    private final GuestSessionRepository sessionRepo;

    @Value("${agent.history-window:10}")
    private int historyWindow;

    // ── Init session ──────────────────────────────────────────────────────────

    @Transactional
    public SessionDto.InitResponse initSession(SessionDto.InitRequest req) {
        String code = req.getHotelCode().toUpperCase().trim();
        String room = req.getRoomNumber().trim();
        String name = req.getGuestName().trim();

        // 1. Find hotel
        Hotel hotel = hotelRepo.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new HotelNotFoundException(
                        "Hotel code '" + code + "' not found. Please check your key card."));

        // 2. Validate room
        Room dbRoom = roomRepo.findActiveRoom(hotel.getId(), room)
                .orElseThrow(() -> new RoomNotFoundException(
                        "Room " + room + " not found. Please verify at the front desk."));

        // 3. Create session (24h TTL)
        GuestSession session = GuestSession.builder()
                .hotel(hotel)
                .room(dbRoom)
                .guestName(name)
                .active(true)
                .expiresAt(OffsetDateTime.now().plusHours(24))
                .build();

        session = sessionRepo.save(session);

        log.info("[Session] Created session={} hotel={} room={} guest={}",
                session.getId(), hotel.getId(), room, name);

        return SessionDto.InitResponse.builder()
                .sessionId(session.getId())
                .hotel(toHotelConfig(hotel))
                .build();
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Transactional
    public void logout(String sessionId) {
        sessionRepo.invalidate(sessionId);
        log.info("[Session] Invalidated session={}", sessionId);
    }

    // ── Validate session for chat ─────────────────────────────────────────────

    /**
     * Returns the active session or throws SecurityException if expired/not found.
     * Also verifies the session belongs to the given hotelId (prevents cross-hotel access).
     */
    public GuestSession validateSession(String sessionId, String hotelId) {
        GuestSession session = sessionRepo
                .findActiveSession(sessionId, OffsetDateTime.now())
                .orElseThrow(() -> new SecurityException(
                        "Session expired or not found. Please check in again."));

        if (!session.getHotel().getId().equals(hotelId)) {
            throw new SecurityException("Session does not belong to hotel " + hotelId);
        }

        return session;
    }

    // ── Conversion ────────────────────────────────────────────────────────────

    private HotelConfig toHotelConfig(Hotel h) {
        return HotelConfig.builder()
                .hotelId(h.getId())
                .name(h.getName())
                .tagline(h.getTagline())
                .location(h.getLocation())
                .locale(h.getLocale())
                .theme(h.getTheme())
                .quickActions(h.getQuickActions())
                .welcomeChips(h.getWelcomeChips())
                .build();
    }
}
