package com.example.booking.storage;

public class ImageNotFoundException extends RuntimeException {

	public ImageNotFoundException(Long eventId) {
		super("No image is stored for event " + eventId);
	}
}
