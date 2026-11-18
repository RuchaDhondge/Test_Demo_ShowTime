package com.example.booking.booking;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.example.booking.catalog.Event;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "show_seats")
public class ShowSeat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id")
	private Event event;

	private String seatLabel;

	@Enumerated(EnumType.STRING)
	private SeatStatus status;

	private UUID holdId;

	private String heldBy;

	private OffsetDateTime holdExpiresAt;

	@Version
	private long version;

	protected ShowSeat() {
	}

	public Long getId() {
		return id;
	}

	public Long getEventId() {
		return event.getId();
	}

	public String getSeatLabel() {
		return seatLabel;
	}

	public SeatStatus effectiveStatus(OffsetDateTime now) {
		if (status == SeatStatus.HELD && holdExpiresAt != null && !holdExpiresAt.isAfter(now)) {
			return SeatStatus.AVAILABLE;
		}
		return status;
	}

	public UUID getHoldId() {
		return holdId;
	}

	public OffsetDateTime getHoldExpiresAt() {
		return holdExpiresAt;
	}

	public void hold(UUID newHoldId, String userId, OffsetDateTime expiresAt, OffsetDateTime now) {
		if (effectiveStatus(now) != SeatStatus.AVAILABLE) {
			throw new SeatUnavailableException("Seat %s is no longer available".formatted(seatLabel));
		}
		status = SeatStatus.HELD;
		holdId = newHoldId;
		heldBy = userId;
		holdExpiresAt = expiresAt;
	}
}
