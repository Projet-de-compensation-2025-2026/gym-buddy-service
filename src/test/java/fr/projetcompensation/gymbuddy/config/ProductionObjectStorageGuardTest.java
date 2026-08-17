package fr.projetcompensation.gymbuddy.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

class ProductionObjectStorageGuardTest {

    @Test
    void refusesToStartWhenS3EndpointIsMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("S3_BUCKET", "gym-buddy");
        environment.setProperty("S3_ACCESS_KEY", "key");
        environment.setProperty("S3_SECRET_KEY", "secret");

        ProductionObjectStorageGuard guard = new ProductionObjectStorageGuard(environment);

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("S3_ENDPOINT")
                .hasMessageContaining("uploads/");
    }

    @Test
    void refusesToStartWhenCredentialsAreBlank() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("S3_ENDPOINT", "http://minio:9000");
        environment.setProperty("S3_BUCKET", "gym-buddy");
        environment.setProperty("S3_ACCESS_KEY", " ");
        environment.setProperty("S3_SECRET_KEY", "secret");

        ProductionObjectStorageGuard guard = new ProductionObjectStorageGuard(environment);

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("S3_ACCESS_KEY");
    }

    @Test
    void startsWhenS3ConfigurationIsComplete() {
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .addFirst(new MapPropertySource(
                        "s3",
                        java.util.Map.of(
                                "S3_ENDPOINT",
                                "http://minio:9000",
                                "S3_BUCKET",
                                "gym-buddy",
                                "S3_ACCESS_KEY",
                                "key",
                                "S3_SECRET_KEY",
                                "secret")));

        ProductionObjectStorageGuard guard = new ProductionObjectStorageGuard(environment);

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }
}
