package com.example.booking.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatHoldService {

	private static final Logger log = LoggerFactory.getLogger(SeatHoldService.class);
	private static final Duration HOLD_DURATION = Duration.ofMinutes(2);

	private final ShowSeatRepository seatRepository;
	private final StringRedisTemplate redisTemplate;
	private final Clock clock;
	private final Counter holdSuccess;
	private final Counter holdConflict;

	@Autowired
	public SeatHoldService(
			ShowSeatRepository seatRepository,
			StringRedisTemplate redisTemplate,
			MeterRegistry meterRegistry) {
		this(seatRepository, redisTemplate, meterRegistry, Clock.systemUTC());
	}

	SeatHoldService(
			ShowSeatRepository seatRepository,
			StringRedisTemplate redisTemplate,
			MeterRegistry meterRegistry,
			Clock clock) {
		this.seatRepository = seatRepository;
		this.redisTemplate = redisTemplate;
		this.clock = clock;
		this.holdSuccess = meterRegistry.counter("booking.seat.hold.success");
		this.holdConflict = meterRegistry.counter("booking.seat.hold.conflict");
	}

	@Transactional(readOnly = true)
	public List<SeatView> listSeats(Long eventId) {
		OffsetDateTime now = OffsetDateTime.now(clock);
		return seatRepository.findByEventIdOrderBySeatLabel(eventId).stream()
				.map(seat -> SeatView.from(seat, now))
				.toList();
	}

	@Transactional
	public HoldResult createHold(Long eventId, String userId, List<Long> requestedSeatIds) {
		List<Long> seatIds = requestedSeatIds.stream().distinct().sorted().toList();
		if (seatIds.isEmpty() || seatIds.size() != requestedSeatIds.size()) {
			throw new IllegalArgumentException("Choose one or more unique seats");
		}

		List<ShowSeat> seats = seatRepository.findForUpdate(eventId, seatIds);
		if (seats.size() != seatIds.size()) {
			throw new IllegalArgumentException("One or more seats do not belong to this event");
		}

		OffsetDateTime now = OffsetDateTime.now(clock);
		UUID holdId = UUID.randomUUID();
		OffsetDateTime expiresAt = now.plus(HOLD_DURATION);
		try {
			seats.forEach(seat -> seat.hold(holdId, userId, expiresAt, now));
		}
		catch (SeatUnavailableException exception) {
			holdConflict.increment();
			throw exception;
		}

		seatRepository.saveAll(seats);
		holdSuccess.increment();
		cacheHold(holdId, eventId, userId, seatIds, expiresAt);
		return new HoldResult(holdId, expiresAt, seatIds);
	}

	@Transactional
	@Scheduled(fixedDelayString = "${booking.hold-cleanup-delay:15000}")
	public void releaseExpiredHolds() {
		int released = seatRepository.releaseExpired(OffsetDateTime.now(clock));
		if (released > 0) {
			log.info("Released {} expired seat holds", released);
		}
	}

	private void cacheHold(
			UUID holdId,
			Long eventId,
			String userId,
			List<Long> seatIds,
			OffsetDateTime expiresAt) {
		String value = "%s|%s|%s".formatted(eventId, userId, new HashSet<>(seatIds));
		try {
			redisTemplate.opsForValue().set("seat-hold:" + holdId, value, HOLD_DURATION);
		}
		catch (DataAccessException exception) {
			log.warn("Seat hold {} is durable in PostgreSQL but could not be cached in Redis", holdId, exception);
		}
	}

	public record SeatView(
			Long id,
			String label,
			SeatStatus status,
			UUID holdId,
			OffsetDateTime holdExpiresAt) {

		static SeatView from(ShowSeat seat, OffsetDateTime now) {
			SeatStatus effectiveStatus = seat.effectiveStatus(now);
			return new SeatView(
					seat.getId(),
					seat.getSeatLabel(),
					effectiveStatus,
					effectiveStatus == SeatStatus.HELD ? seat.getHoldId() : null,
					effectiveStatus == SeatStatus.HELD ? seat.getHoldExpiresAt() : null);
		}
	}

	public record HoldResult(UUID holdId, OffsetDateTime expiresAt, List<Long> seatIds) {
	}
}
