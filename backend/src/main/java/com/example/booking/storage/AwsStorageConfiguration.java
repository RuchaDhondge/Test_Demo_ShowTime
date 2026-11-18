package com.example.booking.storage;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsStorageConfiguration {

	@Bean
	S3Client s3Client(
			@Value("${aws.region}") String region,
			@Value("${aws.endpoint-url}") URI endpointUrl) {
		return S3Client.builder()
				.region(Region.of(region))
				.endpointOverride(endpointUrl)
				.forcePathStyle(true)
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create("emulator", "emulator")))
				.build();
	}
}
