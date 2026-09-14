package fr.projetcompensation.gymbuddy.support;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/** The same private S3 provider and disabled auxiliary services as the deployment. */
public final class S3TestContainer {
    public static final String IMAGE =
            "chrislusf/seaweedfs@sha256:ce9e796f1fe6f06968f4c04bdaf8f678dad9c8acdfef3d244133d71bfa6bf882";
    public static final int PORT = 8333;
    public static final String ACCESS_KEY = "integration-access-key";
    public static final String SECRET_KEY = "integration-secret-key-only";

    private S3TestContainer() {}

    public static GenericContainer<?> create() {
        return new GenericContainer<>(IMAGE)
                .withCreateContainerCmdModifier(IsolatedContainers::configure)
                .withExposedPorts(PORT)
                .withEnv("AWS_ACCESS_KEY_ID", ACCESS_KEY)
                .withEnv("AWS_SECRET_ACCESS_KEY", SECRET_KEY)
                .withCommand(
                        "mini",
                        "-dir=/data",
                        "-ip=127.0.0.1",
                        "-ip.bind=0.0.0.0",
                        "-webdav=false",
                        "-admin.ui=false",
                        "-s3.port.iceberg=0",
                        "-s3.port.lance=0",
                        "-s3.iam=false",
                        "-master.telemetry=false",
                        "-s3.allowedOrigins=https://projet-de-compensation-2025-2026.github.io")
                .waitingFor(Wait.forHttp("/").forPort(PORT).forStatusCode(403))
                .withStartupTimeout(Duration.ofMinutes(2));
    }
}
