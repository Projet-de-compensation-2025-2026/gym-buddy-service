package fr.projetcompensation.gymbuddy.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReleaseSemVerWriteTest {

    @Test
    void releaseWritesComputedSemVerIntoPomBeforeTag() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/release.yml"));
        assertThat(workflow).contains("python3 .github/scripts/ci/sync_pom_version.py");
        assertThat(workflow).contains("prepare_changelog.py");
        assertThat(workflow.indexOf("prepare_changelog.py")).isLessThan(workflow.indexOf("sync_pom_version.py"));
        assertThat(workflow.indexOf("sync_pom_version.py"))
                .isLessThan(workflow.indexOf("Commit release prep on develop"));
    }

    @Test
    void autoBumpNeverChoosesAcademicShip() throws Exception {
        String nextVersion = Files.readString(Path.of(".github/scripts/ci/next_version.py"));
        assertThat(nextVersion).contains("1.0.0 is never chosen automatically");
        assertThat(nextVersion).contains("if nxt[0] >= 1 and not manual");
        assertThat(nextVersion).contains("if major == 0");
    }

    @Test
    void workingPomMatchesTaggedReleaseLine() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom).contains("<artifactId>gym-buddy-service</artifactId>");
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(pom.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
        String version = xpath.evaluate("/project/version", document);
        assertThat(version).matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)");
    }
}
