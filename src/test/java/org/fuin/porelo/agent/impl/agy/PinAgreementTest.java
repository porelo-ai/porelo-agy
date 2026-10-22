package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Guards the fetched CLI archive against partial or unfiltered pin updates. */
class PinAgreementTest {

    @Test
    void builtArtifactAgreesWithThePom() throws Exception {
        final String pom = Files.readString(Path.of("pom.xml"));
        final var definition = new AgyAgent().definition();
        final String version = property(pom, "agent.cli.version");
        final String sha256 = property(pom, "agent.cli.sha256");

        assertThat(definition.version())
                .as("the version actually described by the adapter")
                .isEqualTo(version);
        assertThat(definition.artifacts()).as("the complete installation reference set").hasSize(1);

        final var artifact = definition.artifacts().getFirst();
        assertThat(artifact.fileName()).as("downloaded archive file name").isEqualTo("agy.tar.gz");
        assertThat(artifact.sha256())
                .as("filtered digest for %s", artifact.target())
                .isEqualTo(sha256);
        assertThat(artifact.url())
                .as("download URL follows the pinned CLI version")
                .contains("/" + version + "/")
                .doesNotContain("${");
    }

    private static String property(String pom, String name) {
        final Matcher value =
                Pattern.compile(
                                "<"
                                        + Pattern.quote(name)
                                        + ">([^<]+)</"
                                        + Pattern.quote(name)
                                        + ">")
                        .matcher(pom);
        assertThat(value.find()).as("pom property %s", name).isTrue();
        return value.group(1);
    }
}
