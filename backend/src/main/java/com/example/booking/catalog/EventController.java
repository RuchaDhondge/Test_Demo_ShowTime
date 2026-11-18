package com.example.booking.catalog;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

	private final EventRepository eventRepository;

	public EventController(EventRepository eventRepository) {
		this.eventRepository = eventRepository;
	}

	@GetMapping
	public List<EventResponse> list(@RequestParam(defaultValue = "Pune") String city) {
		return eventRepository.findByCityIgnoreCaseOrderByStartsAt(city).stream()
				.map(EventResponse::from)
				.toList();
	}

	public record EventResponse(
			Long id,
			String name,
			String category,
			String city,
			String venue,
			OffsetDateTime startsAt) {

		static EventResponse from(Event event) {
			return new EventResponse(
					event.getId(),
					event.getName(),
					event.getCategory(),
					event.getCity(),
					event.getVenue(),
					event.getStartsAt());
		}
	}
}
