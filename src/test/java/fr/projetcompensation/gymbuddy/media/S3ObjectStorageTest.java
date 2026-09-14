package fr.projetcompensation.gymbuddy.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ObjectStorageTest {
    @Test
    void rejectsOversizedResponseBeforeReadingItsBody() {
        S3Client client = mock(S3Client.class);
        var body = new ByteArrayInputStream(new byte[] {1, 2, 3});
        when(client.getObject(any(GetObjectRequest.class)))
                .thenReturn(new ResponseInputStream<>(
                        GetObjectResponse.builder()
                                .contentLength(MediaRules.MAX_FILE_BYTES + 1)
                                .build(),
                        body));
        var storage = new S3ObjectStorage(client, null, "test-bucket");
        assertThatThrownBy(() -> storage.get("test-object")).isInstanceOf(IllegalArgumentException.class);
        assertThat(body.available()).isEqualTo(3);
    }

    @Test
    void presignedUploadBindsActualContentLength() {
        try (var presigner = S3Presigner.builder()
                .endpointOverride(URI.create("https://storage.example.invalid"))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test-secret")))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            var storage = new S3ObjectStorage(null, presigner, "test-bucket");
            URI url = storage.signPut("original/test", "image/png", Duration.ofSeconds(60), 512);
            assertThat(java.net.URLDecoder.decode(url.getQuery(), java.nio.charset.StandardCharsets.UTF_8))
                    .contains("content-length");
        }
    }
}
