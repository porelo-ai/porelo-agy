package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.fuin.porelo.wire.Json;
import org.junit.jupiter.api.Test;

/** Exercises the packaged native executable without a provider or credential. */
class NativeProtocolCheck {

    @Test
    void theNativeExecutableDescribesTheInstallArtifactAndProviderContract() throws Exception {
        final Path binary = Path.of(System.getProperty("porelo.native.binary"));
        final Process process =
                new ProcessBuilder(binary.toString(), "describe").redirectErrorStream(true).start();
        try {
            assertThat(process.waitFor(15, TimeUnit.SECONDS))
                    .as("native describe must not hang")
                    .isTrue();
            final String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as("native describe: %s", output).isZero();
            final Object parsed = Json.parse(output);
            assertThat(parsed).as("the published protocol envelope").isInstanceOf(Map.class);
            final Map<?, ?> envelope = (Map<?, ?>) parsed;
            assertThat(envelope.get("definition"))
                    .as("the embedded definition is available in a native image")
                    .isInstanceOf(Map.class);
            final Map<?, ?> definition = (Map<?, ?>) envelope.get("definition");
            assertThat(definition.get("artifacts"))
                    .as("the pinned CLI archive survives native resource inclusion")
                    .isInstanceOf(List.class);
            assertThat((List<?>) definition.get("artifacts"))
                    .as("the complete declared install set")
                    .hasSize(1);
            assertThat(definition.get("name"))
                    .as("the installed adapter identifies itself")
                    .isEqualTo("agy");
            assertThat(definition.get("provider"))
                    .as("Porelo must create a URL broker for the Gemini dialect")
                    .isInstanceOf(Map.class);
            final Map<?, ?> provider = (Map<?, ?>) definition.get("provider");
            assertThat(provider.get("default")).as("default provider").isEqualTo("google");
            assertThat(provider.get("dialect"))
                    .as("Gemini wire dialect")
                    .isEqualTo("google-generative-ai");
            assertThat(provider.get("endpoint")).as("broker endpoint shape").isEqualTo("url");
            assertThat(provider.get("baseUrlEnvironment"))
                    .as("base URL environment variable")
                    .isEqualTo("GOOGLE_GEMINI_BASE_URL");
            assertThat(provider.get("addsPath"))
                    .as("base URL omits the dialect path")
                    .isEqualTo(true);
            assertThat(definition.get("refusedDomains"))
                    .as("the configured refusals must survive native resource inclusion")
                    .isEqualTo(
                            List.of(
                                    "antigravity-cli-auto-updater-974169037036.us-central1.run.app",
                                    "play.googleapis.com"));
        } finally {
            process.destroyForcibly();
            process.waitFor();
        }
    }
}
