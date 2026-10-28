package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.fuin.porelo.agent.api.Credential;
import org.fuin.porelo.agent.api.InstalledAgent;
import org.fuin.porelo.wire.Json;
import org.fuin.porelo.wire.varlink.VarlinkException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the packaged native executable using synthetic credentials only. */
class NativeProtocolCheck {
    private static final String IDS =
            "123456789012-"
                    + "a".repeat(32)
                    + ".apps.googleusercontent.com\n1987654321098-"
                    + "b".repeat(32)
                    + ".apps.googleusercontent.com";
    private static final String SECRETS =
            "GOC" + "SPX-" + "c".repeat(28) + "\nGOC" + "SPX-" + "d".repeat(28);
    @TempDir Path directory;

    @Test
    void theNativeProtocolRetainsTheSignInAndItsRenewalAttributes() throws Exception {
        Files.writeString(
                directory.resolve("antigravity-oauth-token"),
                AgyCredentialExtractorTest.login(
                        "synthetic-access-token",
                        "synthetic-refresh-token",
                        "1970-01-01T00:00:42Z"));
        Files.writeString(directory.resolve(".client-ids"), IDS + "\n");
        Files.writeString(directory.resolve(".client-secrets"), SECRETS + "\n");
        try (InstalledAgent agent =
                new InstalledAgent(
                        Path.of(System.getProperty("porelo.native.binary")),
                        directory.resolve("s"))) {
            final var result = agent.extractCredential(directory);
            assertThat(result)
                    .as("the native extractor survives protocol serialization")
                    .isPresent();
            final Credential credential = result.orElseThrow();
            assertThat(credential.type())
                    .as("the protocol preserves the OAuth type")
                    .isEqualTo("oauth");
            assertThat(credential.secret())
                    .as("the protocol preserves the synthetic access token")
                    .isEqualTo("synthetic-access-token");
            assertThat(credential.attributes())
                    .as("the renewal attributes and both candidate lists survive the wire")
                    .containsExactlyInAnyOrderEntriesOf(
                            Map.of(
                                    Credential.REFRESH_TOKEN, "synthetic-refresh-token",
                                    Credential.EXPIRES_AT, "42000",
                                    Credential.TOKEN_URL, "https://oauth2.googleapis.com/token",
                                    AgyCredentialExtractor.CLIENT_ID_CANDIDATES, IDS,
                                    AgyCredentialExtractor.CLIENT_SECRET_CANDIDATES, SECRETS));
        }
    }

    @Test
    void theNativeProtocolRefusesAMalformedCredentialWithoutEchoingSecrets() throws Exception {
        Files.writeString(
                directory.resolve("antigravity-oauth-token"),
                "{\"auth_method\":\"consumer\",\"token\":{\"access_token\":\"access-secret-marker\\n\"}}");
        Files.writeString(directory.resolve(".client-ids"), IDS + "\n");
        Files.writeString(directory.resolve(".client-secrets"), SECRETS + "\n");
        try (InstalledAgent agent =
                new InstalledAgent(
                        Path.of(System.getProperty("porelo.native.binary")),
                        directory.resolve("s-bad"))) {
            assertThatThrownBy(() -> agent.extractCredential(directory))
                    .as("malformed sign-in input fails across the native protocol")
                    .isInstanceOf(VarlinkException.class)
                    .hasMessageContaining("org.fuin.porelo.Agent1.Failed")
                    .satisfies(
                            error -> {
                                final StringWriter trace = new StringWriter();
                                error.printStackTrace(new PrintWriter(trace));
                                assertThat(trace.toString())
                                        .as("protocol errors never echo secret markers")
                                        .doesNotContain("access-secret-marker", "GOC" + "SPX-");
                            });
        }
    }

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
            assertThat(provider.get("default")).as("default provider").isEqualTo("google-sign-in");
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
            assertThat(provider.get("dialects"))
                    .as("the native definition retains Cloud Code Assist routing")
                    .isEqualTo(
                            Map.of(
                                    "cloud-code",
                                    Map.of(
                                            "baseUrlEnvironment",
                                            "CLOUD_CODE_URL",
                                            "addsPath",
                                            true)));
            assertThat(definition.get("refusedDomains"))
                    .as("the configured refusals must survive native resource inclusion")
                    .isEqualTo(
                            List.of(
                                    "antigravity-cli-auto-updater-974169037036.us-central1.run.app",
                                    "play.googleapis.com",
                                    "www.googleapis.com",
                                    "antigravity-unleash.goog"));
        } finally {
            process.destroyForcibly();
            process.waitFor();
        }
    }
}
