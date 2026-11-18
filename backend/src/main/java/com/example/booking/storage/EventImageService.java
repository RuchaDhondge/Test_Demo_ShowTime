package com.example.booking.storage;

import java.io.IOException;
import java.util.Set;

import com.example.booking.catalog.EventRepository;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class EventImageService {

	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
			MediaType.IMAGE_JPEG_VALUE,
			MediaType.IMAGE_PNG_VALUE,
			"image/webp");

	private final S3Client s3Client;
	private final EventRepository eventRepository;
	private final String bucket;

	public EventImageService(
			S3Client s3Client,
			EventRepository eventRepository,
			@Value("${aws.s3.bucket}") String bucket) {
		this.s3Client = s3Client;
		this.eventRepository = eventRepository;
		this.bucket = bucket;
	}

	@PostConstruct
	void createBucket() {
		try {
			s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
		}
		catch (NoSuchBucketException exception) {
			s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
		}
		catch (S3Exception exception) {
			if (exception.statusCode() == 404) {
				s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
				return;
			}
			throw new StorageException("Could not initialize the event-image bucket", exception);
		}
	}

	public void upload(Long eventId, MultipartFile file) {
		requireEvent(eventId);
		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
			throw new IllegalArgumentException("Event image must be JPEG, PNG, or WebP");
		}
		if (file.isEmpty()) {
			throw new IllegalArgumentException("Event image cannot be empty");
		}

		try {
			s3Client.putObject(
					PutObjectRequest.builder()
							.bucket(bucket)
							.key(key(eventId))
							.contentType(contentType)
							.build(),
					RequestBody.fromBytes(file.getBytes()));
		}
		catch (IOException | S3Exception exception) {
			throw new StorageException("Could not store the event image", exception);
		}
	}

	public StoredImage download(Long eventId) {
		requireEvent(eventId);
		try {
			ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
					GetObjectRequest.builder().bucket(bucket).key(key(eventId)).build());
			String contentType = object.response().contentType();
			return new StoredImage(
					object.asByteArray(),
					contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType);
		}
		catch (S3Exception exception) {
			if (exception.statusCode() == 404) {
				throw new ImageNotFoundException(eventId);
			}
			throw new StorageException("Could not read the event image", exception);
		}
	}

	private void requireEvent(Long eventId) {
		if (!eventRepository.existsById(eventId)) {
			throw new IllegalArgumentException("Event does not exist");
		}
	}

	private String key(Long eventId) {
		return "events/%d/cover".formatted(eventId);
	}

	public record StoredImage(byte[] bytes, String contentType) {
	}
}
