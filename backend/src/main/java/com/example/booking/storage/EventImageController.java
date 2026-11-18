package com.example.booking.storage;

import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/events/{eventId}/image")
public class EventImageController {

	private final EventImageService eventImageService;

	public EventImageController(EventImageService eventImageService) {
		this.eventImageService = eventImageService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Map<String, String> upload(
			@PathVariable Long eventId,
			@RequestPart("file") MultipartFile file) {
		eventImageService.upload(eventId, file);
		return Map.of("status", "stored", "storage", "AWS Emulator S3");
	}

	@GetMapping
	public ResponseEntity<byte[]> download(@PathVariable Long eventId) {
		EventImageService.StoredImage image = eventImageService.download(eventId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(image.contentType()))
				.cacheControl(CacheControl.noStore())
				.body(image.bytes());
	}
}
