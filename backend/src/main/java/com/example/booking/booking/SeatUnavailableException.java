package com.example.booking.booking;

public class SeatUnavailableException extends RuntimeException {

	public SeatUnavailableException(String message) {
		super(message);
	}
}
