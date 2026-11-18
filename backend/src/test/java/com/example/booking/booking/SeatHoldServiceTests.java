package com.example.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

class SeatHoldServiceTests {

	private final ShowSeatRepository seatRepository = mock(ShowSeatRepository.class);
	private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
	private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T08:00:00Z"), ZoneOffset.UTC);
	private final SeatHoldService service =
			new SeatHoldService(seatRepository, redisTemplate, new SimpleMeterRegistry(), clock);

	@Test
	void holdsEveryRequestedSeatInOneDatabaseTransaction() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		ShowSeat first = seat(1L, "A1");
		ShowSeat second = seat(2L, "A2");
		when(seatRepository.findForUpdate(1L, List.of(1L, 2L))).thenReturn(List.of(first, second));

		SeatHoldService.HoldResult result = service.createHold(1L, "colleague@example.com", List.of(2L, 1L));

		assertThat(result.seatIds()).containsExactly(1L, 2L);
		assertThat(first.effectiveStatus(OffsetDateTime.now(clock))).isEqualTo(SeatStatus.HELD);
		assertThat(second.getHoldId()).isEqualTo(first.getHoldId());
		verify(seatRepository).saveAll(List.of(first, second));
	}

	@Test
	void rejectsRequestWhenAnySeatIsAlreadyHeld() {
		ShowSeat seat = seat(1L, "A1");
		seat.hold(
				java.util.UUID.randomUUID(),
				"other-user",
				OffsetDateTime.now(clock).plusMinutes(1),
				OffsetDateTime.now(clock));
		when(seatRepository.findForUpdate(1L, List.of(1L))).thenReturn(List.of(seat));

		assertThatThrownBy(() -> service.createHold(1L, "new-user", List.of(1L)))
				.isInstanceOf(SeatUnavailableException.class);
	}

	private ShowSeat seat(Long id, String label) {
		ShowSeat seat = new ShowSeat();
		ReflectionTestUtils.setField(seat, "id", id);
		ReflectionTestUtils.setField(seat, "seatLabel", label);
		ReflectionTestUtils.setField(seat, "status", SeatStatus.AVAILABLE);
		return seat;
	}
}
