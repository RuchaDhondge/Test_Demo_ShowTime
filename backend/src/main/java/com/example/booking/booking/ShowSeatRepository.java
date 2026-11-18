package com.example.booking.booking;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

	List<ShowSeat> findByEventIdOrderBySeatLabel(Long eventId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select seat from ShowSeat seat
			where seat.event.id = :eventId and seat.id in :seatIds
			order by seat.id
			""")
	List<ShowSeat> findForUpdate(
			@Param("eventId") Long eventId,
			@Param("seatIds") Collection<Long> seatIds);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update ShowSeat seat
			set seat.status = com.example.booking.booking.SeatStatus.AVAILABLE,
				seat.holdId = null,
				seat.heldBy = null,
				seat.holdExpiresAt = null
			where seat.status = com.example.booking.booking.SeatStatus.HELD
				and seat.holdExpiresAt <= :now
			""")
	int releaseExpired(@Param("now") OffsetDateTime now);
}
