package fr.projetcompensation.gymbuddy.support;

import java.nio.file.Path;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Build the deployment database image once, then use isolated databases for every suite. */
public final class PostgresTestContainer {
    private PostgresTestContainer() {}

    private static final class Image {
        static final String NAME = new ImageFromDockerfile("gym-buddy-postgres:18.6-secure", false)
                .withFileFromPath("Dockerfile", Path.of("deploy/postgres/Dockerfile"))
                .get();
    }

    public static PostgreSQLContainer create() {
        String image = DockerClientFactory.instance().isDockerAvailable() ? Image.NAME : "postgres:unavailable";
        return new PostgreSQLContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("postgres"))
                .withEnv("POSTGRES_INITDB_ARGS", "--locale-provider=icu --icu-locale=en-US --locale=C.UTF-8")
                .withCreateContainerCmdModifier(IsolatedContainers::configure);
    }
}
