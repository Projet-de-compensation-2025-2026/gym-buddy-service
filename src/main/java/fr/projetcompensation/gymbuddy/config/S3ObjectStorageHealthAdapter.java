package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.health.ObjectStorageHealthPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

@Component
@ConditionalOnBean(S3Client.class)
public class S3ObjectStorageHealthAdapter implements ObjectStorageHealthPort {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorageHealthAdapter.class);

    private final S3Client s3Client;

    public S3ObjectStorageHealthAdapter(S3Client s3Client) {
        this.s3Client = s3Client;
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
            s3Client.listBuckets();
            return null;
        } catch (RuntimeException ex) {
            log.warn("Object storage readiness check failed: {}", ex.getMessage());
            return ex.getMessage() == null ? "unreachable" : ex.getMessage();
        }
    }
}
