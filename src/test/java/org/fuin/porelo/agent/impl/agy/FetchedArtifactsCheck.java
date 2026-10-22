package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.fuin.porelo.agent.api.InstallArtifact;
import org.fuin.porelo.wire.Json;
import org.junit.jupiter.api.Test;

/** Guards the actual build bill against silently omitting the fetched CLI executable. */
class FetchedArtifactsCheck {

    private static final Path TARGET = Path.of(System.getProperty("porelo.target", "target"));

    @Test
    void theBuildBillRecordsTheFetchedExecutableWithItsCheckedDigest() throws Exception {
        final var definition = new AgyAgent().definition();
        final Map<?, ?> document =
                (Map<?, ?>)
                        Json.parse(Files.readString(TARGET.resolve("porelo-agent-agy.cdx.json")));
        final List<?> components = (List<?>) document.get("components");
        final var fetched =
                components.stream()
                        .map(item -> (Map<?, ?>) item)
                        .filter(
                                item ->
                                        item.get("properties") instanceof List<?> properties
                                                && properties.contains(
                                                        Map.of(
                                                                "name", "porelo:delivery",
                                                                "value", "fetched-at-image-build")))
                        .toList();
        assertThat(fetched)
                .as("the fetched CLI must be visible in the actual supply-chain bill")
                .hasSize(1);
        final Map<?, ?> item = fetched.getFirst();
        assertThat(item.get("name"))
                .as("the complete fetched executable reference set")
                .isEqualTo("agy");
        final InstallArtifact artifact = definition.artifacts().getFirst();
        assertThat(item.get("version"))
                .as("the executable follows the built definition's version")
                .isEqualTo(definition.version());
        assertThat(item.get("hashes"))
                .as("the fetched archive's checked digest")
                .isEqualTo(List.of(Map.of("alg", "SHA-256", "content", artifact.sha256())));
        assertThat(item.get("properties"))
                .as("the executable is not claimed as shipped inside the package")
                .isEqualTo(
                        List.of(
                                Map.of(
                                        "name", "porelo:delivery",
                                        "value", "fetched-at-image-build")));
        assertThat(item.get("externalReferences"))
                .as("the digest identifies its actual download")
                .isEqualTo(List.of(Map.of("type", "distribution", "url", artifact.url())));
    }
}
