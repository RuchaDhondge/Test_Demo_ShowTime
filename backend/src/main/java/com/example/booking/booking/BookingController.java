package com.example.booking.booking;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}")
public class BookingController {

	private final SeatHoldService seatHoldService;

	public BookingController(SeatHoldService seatHoldService) {
		this.seatHoldService = seatHoldService;
	}

	@GetMapping("/seats")
	public List<SeatHoldService.SeatView> seats(@PathVariable Long eventId) {
		return seatHoldService.listSeats(eventId);
	}

	@PostMapping("/holds")
	@ResponseStatus(HttpStatus.CREATED)
	public SeatHoldService.HoldResult hold(
			@PathVariable Long eventId,
			@Valid @RequestBody CreateHoldRequest request) {
		return seatHoldService.createHold(eventId, request.userId(), request.seatIds());
	}

	public record CreateHoldRequest(
			@NotBlank String userId,
			@NotEmpty List<Long> seatIds) {
	}
}
