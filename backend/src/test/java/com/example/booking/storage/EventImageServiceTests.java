package com.example.booking.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.booking.catalog.EventRepository;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class EventImageServiceTests {

	private final S3Client s3Client = mock(S3Client.class);
	private final EventRepository eventRepository = mock(EventRepository.class);
	private final EventImageService service =
			new EventImageService(s3Client, eventRepository, "booking-demo-assets");

	@Test
	void storesSupportedImageForExistingEvent() {
		when(eventRepository.existsById(1L)).thenReturn(true);
		MockMultipartFile file =
				new MockMultipartFile("file", "event.png", "image/png", new byte[] { 1, 2, 3 });

		service.upload(1L, file);

		verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
	}

	@Test
	void rejectsUnsupportedContentTypeBeforeCallingS3() {
		when(eventRepository.existsById(1L)).thenReturn(true);
		MockMultipartFile file =
				new MockMultipartFile("file", "event.svg", "image/svg+xml", "<svg/>".getBytes());

		assertThatThrownBy(() -> service.upload(1L, file))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("JPEG, PNG, or WebP");
		verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
	}
}
