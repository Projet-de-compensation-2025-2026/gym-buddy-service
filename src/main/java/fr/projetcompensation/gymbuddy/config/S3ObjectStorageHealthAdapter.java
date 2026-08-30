package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.health.ObjectStorageHealthPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Component
@Primary
@ConditionalOnBean(S3Client.class)
public class S3ObjectStorageHealthAdapter implements ObjectStorageHealthPort {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorageHealthAdapter.class);

    private final S3Client s3Client;
    private final String bucket;

    public S3ObjectStorageHealthAdapter(S3Client s3Client, @Value("${S3_BUCKET}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public boolean reachable() {
        return failure() == null;
    }

    @Override
    public String detail() {
        String failure = failure();
        return failure == null ? "ok" : failure;
    }

    private String failure() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return null;
        } catch (RuntimeException ex) {
            log.warn("Object storage readiness check failed for bucket {}: {}", bucket, ex.getMessage());
            return ex.getMessage() == null ? "unreachable" : ex.getMessage();
        }
    }
}
