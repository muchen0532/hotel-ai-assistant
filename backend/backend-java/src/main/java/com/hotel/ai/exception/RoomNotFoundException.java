package com.hotel.ai.exception;

public class RoomNotFoundException extends RuntimeException {
    public RoomNotFoundException(String message) { super(message); }
}
