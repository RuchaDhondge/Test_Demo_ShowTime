package com.example.booking.common;

import java.time.OffsetDateTime;
import java.util.Map;

import com.example.booking.booking.SeatUnavailableException;
import com.example.booking.storage.ImageNotFoundException;
import com.example.booking.storage.StorageException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(SeatUnavailableException.class)
	ResponseEntity<Map<String, Object>> seatUnavailable(SeatUnavailableException exception) {
		return error(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler({ IllegalArgumentException.class, MethodArgumentNotValidException.class })
	ResponseEntity<Map<String, Object>> badRequest(Exception exception) {
		return error(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(ImageNotFoundException.class)
	ResponseEntity<Map<String, Object>> notFound(ImageNotFoundException exception) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(StorageException.class)
	ResponseEntity<Map<String, Object>> storageUnavailable(StorageException exception) {
		return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
	}

	private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of(
				"timestamp", OffsetDateTime.now().toString(),
				"status", status.value(),
				"error", status.getReasonPhrase(),
				"message", message));
	}
}
