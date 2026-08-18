package fr.projetcompensation.gymbuddy.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenApiPackagePinTest {

    @Test
    void generateSourcesPinsVersionedPackageRefTree() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom).contains("<openapi.package.tag>v0.1.0</openapi.package.tag>");
        assertThat(pom).contains("<openapi.spec.file>${openapi.package.dir}/openapi/openapi.yaml</openapi.spec.file>");
        assertThat(pom).contains("<inputSpec>${openapi.spec.file}</inputSpec>");
        assertThat(pom).doesNotContain("bundled.yaml");
        assertThat(pom)
                .doesNotContain("raw.githubusercontent.com/Projet-de-compensation-2025-2026/gym-buddy-openapi/develop");
    }
}
