package com.example.booking.catalog;

import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "events")
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	private String category;

	private String city;

	private String venue;

	private OffsetDateTime startsAt;

	protected Event() {
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCategory() {
		return category;
	}

	public String getCity() {
		return city;
	}

	public String getVenue() {
		return venue;
	}

	public OffsetDateTime getStartsAt() {
		return startsAt;
	}
}
