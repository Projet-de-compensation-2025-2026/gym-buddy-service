package fr.projetcompensation.gymbuddy.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class ObjectStorageConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = {"S3_ENDPOINT", "S3_BUCKET", "S3_ACCESS_KEY", "S3_SECRET_KEY"})
    S3Client s3Client(
            @Value("${S3_ENDPOINT}") String endpoint,
            @Value("${S3_ACCESS_KEY}") String accessKey,
            @Value("${S3_SECRET_KEY}") String secretKey,
            @Value("${S3_REGION:us-east-1}") String region) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = {"S3_ENDPOINT", "S3_BUCKET", "S3_ACCESS_KEY", "S3_SECRET_KEY"})
    S3Presigner s3Presigner(
            @Value("${S3_PUBLIC_ENDPOINT:${S3_ENDPOINT}}") String publicEndpoint,
            @Value("${S3_ACCESS_KEY}") String accessKey,
            @Value("${S3_SECRET_KEY}") String secretKey,
            @Value("${S3_REGION:us-east-1}") String region) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(publicEndpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
