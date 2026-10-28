package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.fuin.porelo.agent.api.AgentException;
import org.fuin.porelo.agent.api.Credential;
import org.fuin.porelo.wire.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Guards credential disclosure, unsafe file reads, and lost host renewal metadata. */
class AgyCredentialExtractorTest {
    static final String CLIENT_ID = "test-client-id";
    static final String CLIENT_SECRET = "test-client-secret";
    private static final Pattern BINARY_ID = Pattern.compile("test-client-id(-[0-9]+)?");
    private static final Pattern BINARY_SECRET = Pattern.compile("test-client-secret(-[0-9]+)?");
    private static final String ACCESS = "access-secret-marker";
    private static final String REFRESH = "refresh-secret-marker";
    private static final String EXPIRY = "2026-10-22T09:58:00Z";
    @TempDir Path directory;

    @Test
    void everyWholeClientIdAndEverySecretOfTheInstalledCliReachesTheLoginsPick()
            throws IOException {
        writeLogin();
        // A stale pair from an older image's wrapper must not stand in for the CLI's clients.
        Files.writeString(directory.resolve(".client-id"), CLIENT_ID);
        Files.writeString(directory.resolve(".client-secret"), CLIENT_SECRET);
        final String gcpId = "123456789012-" + "a".repeat(32) + ".apps.googleusercontent.com";
        final String consumerId = "1987654321098-" + "b".repeat(32) + ".apps.googleusercontent.com";
        final String consumerSecret = "GOC" + "SPX-" + "c".repeat(28);
        final String gcpSecret = "GOC" + "SPX-" + "d".repeat(28);
        final Path binary =
                Files.writeString(
                        directory.resolve("installed-cli"),
                        consumerSecret
                                + gcpSecret
                                + "\0text"
                                + gcpId
                                + "\0%v"
                                + consumerId
                                + consumerId
                                + "\0"
                                + gcpSecret);
        final Credential credential =
                new AgyCredentialExtractor(binary).extract(directory).orElseThrow();
        assertThat(credential.attributes())
                .as("the login tries each whole id with each secret, in the binary's order")
                .containsEntry(
                        AgyCredentialExtractor.CLIENT_ID_CANDIDATES, gcpId + "\n" + consumerId)
                .containsEntry(
                        AgyCredentialExtractor.CLIENT_SECRET_CANDIDATES,
                        consumerSecret + "\n" + gcpSecret)
                .doesNotContainKeys(Credential.CLIENT_ID, Credential.CLIENT_SECRET);
    }

    @Test
    void theWrappersCollectedListsReachTheLoginDistinctAndInOrder() throws IOException {
        writeLogin();
        Files.writeString(
                directory.resolve(".client-ids"),
                CLIENT_ID + "-2\n" + CLIENT_ID + "\n" + CLIENT_ID + "-2\n");
        Files.writeString(directory.resolve(".client-secrets"), CLIENT_SECRET + "\n");
        Files.writeString(directory.resolve("antigravity"), CLIENT_ID + "-3\0" + CLIENT_SECRET);
        assertThat(extractor().extract(directory).orElseThrow().attributes())
                .as("the collected lists stand for the CLI, without repeats, in their order")
                .containsEntry(
                        AgyCredentialExtractor.CLIENT_ID_CANDIDATES, CLIENT_ID + "-2\n" + CLIENT_ID)
                .containsEntry(AgyCredentialExtractor.CLIENT_SECRET_CANDIDATES, CLIENT_SECRET);
    }

    @Test
    void moreThanEightClientsOfOneKindAreRefusedRatherThanTriedAgainstGoogle() throws IOException {
        writeLogin();
        final StringBuilder ids = new StringBuilder();
        for (int i = 1; i <= 9; i++) {
            ids.append(CLIENT_ID).append('-').append(i).append('\n');
        }
        Files.writeString(directory.resolve(".client-ids"), ids);
        Files.writeString(directory.resolve(".client-secrets"), CLIENT_SECRET);
        refuses(extractor());
        Files.delete(directory.resolve(".client-ids"));
        Files.delete(directory.resolve(".client-secrets"));
        Files.writeString(directory.resolve("antigravity"), ids.toString().replace('\n', '\0'));
        refuses(extractor());
    }

    @Test
    void aMissingLoginLeavesNothingToImport() {
        assertThat(extractor().extract(directory))
                .as("an unfinished login has no credential")
                .isEmpty();
    }

    @Test
    void aConsumerSignInRetainsEveryHostRenewalAttribute() throws IOException {
        writeLogin();
        Files.writeString(directory.resolve(".client-ids"), "\n" + CLIENT_ID + "\n");
        Files.writeString(directory.resolve(".client-secrets"), "\n" + CLIENT_SECRET + "\n");
        assertCredential(extractor().extract(directory).orElseThrow(), EXPIRY);
    }

    @Test
    void anIdCutByTheRetainedTailIsNotTakenForAnotherClient() throws IOException {
        writeLogin();
        final String id = "1987654321098-" + "b".repeat(32) + ".apps.googleusercontent.com";
        final String secret = "GOC" + "SPX-" + "c".repeat(28);
        // The id ends inside the first read, but starts before the 112 bytes kept for the next one.
        final Path binary = directory.resolve("installed-cli");
        Files.write(binary, new byte[8075]);
        Files.writeString(binary, id + "\0" + secret + "\0".repeat(50), StandardOpenOption.APPEND);
        assertThat(new AgyCredentialExtractor(binary).extract(directory).orElseThrow().attributes())
                .as("the login is offered the whole id once, never its cut tail as a second client")
                .containsEntry(AgyCredentialExtractor.CLIENT_ID_CANDIDATES, id);
    }

    @Test
    void anEmptyCollectedListIsRefusedAsMalformedNotAsAMissingCli() throws IOException {
        writeLogin();
        Files.writeString(directory.resolve(".client-ids"), CLIENT_ID);
        Files.writeString(directory.resolve(".client-secrets"), "\n");
        refuses(extractor());
        assertThatThrownBy(() -> extractor().extract(directory))
                .as("the collected file is named, not the CLI it came from")
                .hasMessageContaining(".client-secrets")
                .hasMessageEndingWith("is malformed");
    }

    @Test
    void aLocalBinarySuppliesItsClientsWholeAcrossAChunkBoundary() throws IOException {
        writeLogin();
        final byte[] prefix = new byte[8190];
        Files.write(directory.resolve("antigravity"), prefix);
        Files.writeString(
                directory.resolve("antigravity"),
                CLIENT_ID + "\0" + CLIENT_SECRET + "\0" + CLIENT_SECRET,
                StandardOpenOption.APPEND);
        assertCredential(extractor().extract(directory).orElseThrow(), EXPIRY);
    }

    @Test
    void anExplicitInstalledBinaryCanSupplyTheClientSecret() throws IOException {
        writeLogin();
        final Path binary =
                Files.writeString(
                        directory.resolve("installed-cli"),
                        "binary\0" + CLIENT_ID + "\0" + CLIENT_SECRET);
        assertCredential(extractor(binary).extract(directory).orElseThrow(), EXPIRY);
    }

    @Test
    void anUnmatchedLocalBinaryFallsBackToTheInstalledBinary() throws IOException {
        writeLogin();
        Files.writeString(directory.resolve("antigravity"), "no OAuth constant here");
        final Path binary =
                Files.writeString(
                        directory.resolve("installed-cli"), CLIENT_ID + "\0" + CLIENT_SECRET);
        assertCredential(extractor(binary).extract(directory).orElseThrow(), EXPIRY);
    }

    @Test
    void theCollectedSecretTakesPrecedenceOverAnyBinary() throws IOException {
        writeLogin();
        writeClientMetadata();
        Files.createDirectory(directory.resolve("antigravity"));
        assertCredential(extractor().extract(directory).orElseThrow(), EXPIRY);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "2026-10-22T11:58:00.123456789+02:00",
                "1970-01-01T00:00:00.001Z",
                "+33658-09-27T01:46:39.999Z"
            })
    void anOffsetOrFractionalExpiryIsConvertedToEpochMilliseconds(String expiry)
            throws IOException {
        write(login(ACCESS, REFRESH, expiry));
        writeClientMetadata();
        assertCredential(extractor().extract(directory).orElseThrow(), expiry);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "[]",
                "null",
                "{\"auth_method\":\"enterprise\",\"token\":{}}",
                "{\"auth_method\":\"consumer\",\"token\":null}",
                "{\"auth_method\":\"consumer\",\"token\":[]}",
                "{\"access-secret-marker\":1,\"access-secret-marker\":2}",
                "{\"refresh-secret-marker\":"
            })
    void anInvalidRecordFailsWithoutLeakingParserInput(String json) throws IOException {
        writeClientMetadata();
        write(json);
        refuses(extractor());
    }

    @ParameterizedTest
    @ValueSource(strings = {"access_token", "refresh_token", "expiry"})
    void missingBlankOrNonStringFieldsCannotEnterTheVault(String field) throws IOException {
        writeClientMetadata();
        for (final Object value : java.util.Arrays.asList(null, "", " ", 42, Map.of())) {
            final Map<String, Object> token = token(ACCESS, REFRESH, EXPIRY);
            if (value == null) {
                token.remove(field);
            } else {
                token.put(field, value);
            }
            write(Json.write(Map.of("auth_method", "consumer", "token", token)));
            refuses(extractor());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"access_token", "refresh_token"})
    void unsafeTokensCannotReachTheBrokerOrRenewalRequest(String field) throws IOException {
        writeClientMetadata();
        for (final String suffix :
                new String[] {" ", "\t", "\n", "\r", "\"", "\\", "\0", "é", ":header"}) {
            final Map<String, Object> token = token(ACCESS, REFRESH, EXPIRY);
            token.put(field, token.get(field) + suffix);
            write(Json.write(Map.of("auth_method", "consumer", "token", token)));
            refuses(extractor());
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "access-secret-marker",
                "1970-01-01T00:00:00Z",
                "1969-12-31T23:59:59Z",
                "+33658-09-27T01:46:40Z",
                "+999999999-12-31T23:59:59Z"
            })
    void anUnparseableOrOutOfRangeExpiryCannotDisableRenewal(String expiry) throws IOException {
        writeClientMetadata();
        write(login(ACCESS, REFRESH, expiry));
        refuses(extractor());
    }

    @Test
    void aTokenSymlinkCannotReadAnotherCredential() throws IOException {
        final Path other =
                Files.writeString(directory.resolve("other"), login(ACCESS, REFRESH, EXPIRY));
        Files.createSymbolicLink(directory.resolve("antigravity-oauth-token"), other);
        refuses(extractor());
    }

    @Test
    void aDanglingTokenSymlinkIsNotAnUnfinishedLogin() throws IOException {
        Files.createSymbolicLink(
                directory.resolve("antigravity-oauth-token"), directory.resolve("missing"));
        refuses(extractor());
    }

    @Test
    void aTokenDirectoryIsNotARegularCredential() throws IOException {
        Files.createDirectory(directory.resolve("antigravity-oauth-token"));
        refuses(extractor());
    }

    @Test
    void anOversizedTokenFileCannotExhaustCollection() throws IOException {
        write(login(ACCESS, REFRESH, EXPIRY) + " ".repeat(1024 * 1024));
        refuses(extractor());
    }

    @Test
    void unreadableCredentialFilesFailWithoutFilesystemDiagnostics() throws IOException {
        writeLogin();
        final Path file = directory.resolve("antigravity-oauth-token");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
        try {
            refuses(extractor());
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "too-short",
                "test-client-secret-that-exceeds-the-sixty-four-character-limit-1234567890",
                "test-client-secret-é",
                "test-client-secret\0",
                "test-client-secret extra"
            })
    void aMalformedCollectedClientSecretCannotFallBackToABinary(String secret) throws IOException {
        writeLogin();
        Files.writeString(directory.resolve(".client-ids"), CLIENT_ID);
        Files.writeString(directory.resolve(".client-secrets"), secret);
        Files.writeString(directory.resolve("antigravity"), CLIENT_ID + "\0" + CLIENT_SECRET);
        refuses(extractor());
    }

    @Test
    void aClientSecretSymlinkCannotReadAnotherFile() throws IOException {
        writeLogin();
        Files.writeString(directory.resolve(".client-ids"), CLIENT_ID);
        final Path other = Files.writeString(directory.resolve("other"), CLIENT_SECRET);
        Files.createSymbolicLink(directory.resolve(".client-secrets"), other);
        refuses(extractor());
    }

    @Test
    void aClientSecretDirectoryIsRefused() throws IOException {
        writeLogin();
        Files.writeString(directory.resolve(".client-ids"), CLIENT_ID);
        Files.createDirectory(directory.resolve(".client-secrets"));
        refuses(extractor());
    }

    @Test
    void anOversizedClientSecretIsRefused() throws IOException {
        writeLogin();
        Files.writeString(directory.resolve(".client-ids"), CLIENT_ID);
        Files.writeString(directory.resolve(".client-secrets"), CLIENT_SECRET + " ".repeat(4096));
        refuses(extractor());
    }

    @Test
    void anUnreadableClientSecretCannotFallBackToABinary() throws IOException {
        writeLogin();
        Files.writeString(directory.resolve(".client-ids"), CLIENT_ID);
        final Path file = Files.writeString(directory.resolve(".client-secrets"), CLIENT_SECRET);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
        try {
            refuses(extractor());
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void aMissingClientSecretFailsWithTheSafeInstalledCliDiagnostic() throws IOException {
        writeLogin();
        Files.writeString(directory.resolve(".client-ids"), CLIENT_ID);
        refuses(extractor());
        assertThatThrownBy(() -> extractor().extract(directory))
                .as("absence names the missing runtime source")
                .hasMessage(
                        "Antigravity CLI OAuth client secret could not be read from the installed CLI");
    }

    @Test
    void aLocalBinarySymlinkOrDirectoryCannotSupplyASecret() throws IOException {
        writeLogin();
        Files.writeString(directory.resolve(".client-ids"), CLIENT_ID);
        final Path other = Files.writeString(directory.resolve("other"), CLIENT_SECRET);
        final Path binary = directory.resolve("antigravity");
        Files.createSymbolicLink(binary, other);
        refuses(extractor());
        Files.delete(binary);
        Files.createDirectory(binary);
        refuses(extractor());
    }

    @Test
    void aFallbackBinarySymlinkCannotRedirectTheRead() throws IOException {
        writeLogin();
        Files.writeString(directory.resolve(".client-ids"), CLIENT_ID);
        final Path other = Files.writeString(directory.resolve("other"), CLIENT_SECRET);
        final Path link = Files.createSymbolicLink(directory.resolve("fallback"), other);
        refuses(extractor(link));
    }

    @Test
    void anOversizedBinaryCannotCauseAnUnboundedScan() throws IOException {
        writeLogin();
        Files.writeString(directory.resolve(".client-ids"), CLIENT_ID);
        final Path binary = directory.resolve("antigravity");
        try (var channel =
                Files.newByteChannel(
                        binary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.position(512L * 1024 * 1024);
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {0}));
        }
        refuses(extractor());
    }

    @Test
    void aSymlinkedConfigDirectoryCannotRedirectCredentialCollection() throws IOException {
        writeLogin();
        writeClientMetadata();
        final Path parent = Files.createTempDirectory("agy-link-parent");
        try {
            final Path linked = Files.createSymbolicLink(parent.resolve("linked"), directory);
            refuses(extractor(), linked);
        } finally {
            Files.deleteIfExists(parent.resolve("linked"));
            Files.deleteIfExists(parent);
        }
    }

    @Test
    void theDefaultExtractorDoesNotUseOrEchoUnsafeUserHomeProperties() throws IOException {
        writeLogin();
        final String originalHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", "access-secret-marker\0/invalid");
            refuses(new AgyCredentialExtractor());
        } finally {
            if (originalHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", originalHome);
            }
        }
    }

    private AgyCredentialExtractor extractor() {
        return extractor(directory.resolve("missing-cli"));
    }

    private AgyCredentialExtractor extractor(Path binary) {
        return new AgyCredentialExtractor(binary, BINARY_ID, BINARY_SECRET);
    }

    private void refuses(AgyCredentialExtractor extractor) {
        refuses(extractor, directory);
    }

    private void refuses(AgyCredentialExtractor extractor, Path config) {
        assertThatThrownBy(() -> extractor.extract(config))
                .as("unusable sign-in input fails closed")
                .isInstanceOf(AgentException.class)
                .hasNoCause()
                .satisfies(
                        error -> {
                            final StringWriter trace = new StringWriter();
                            error.printStackTrace(new PrintWriter(trace));
                            assertThat(trace.toString())
                                    .as(
                                            "messages, causes, and suppressed errors never repeat credential bytes")
                                    .doesNotContain(
                                            ACCESS, REFRESH, "too-short", CLIENT_ID, CLIENT_SECRET);
                        });
    }

    private void writeLogin() throws IOException {
        write(login(ACCESS, REFRESH, EXPIRY));
    }

    private void writeClientMetadata() throws IOException {
        Files.writeString(directory.resolve(".client-ids"), CLIENT_ID);
        Files.writeString(directory.resolve(".client-secrets"), CLIENT_SECRET);
    }

    private void write(String json) throws IOException {
        Files.writeString(directory.resolve("antigravity-oauth-token"), json);
    }

    static String login(String access, String refresh, String expiry) {
        return Json.write(
                Map.of("auth_method", "consumer", "token", token(access, refresh, expiry)));
    }

    private static Map<String, Object> token(String access, String refresh, String expiry) {
        final Map<String, Object> token = new LinkedHashMap<>();
        token.put("access_token", access);
        token.put("refresh_token", refresh);
        token.put("expiry", expiry);
        return token;
    }

    private static void assertCredential(Credential credential, String expiry) {
        assertThat(credential.type()).as("a consumer sign-in is OAuth").isEqualTo("oauth");
        assertThat(credential.secret())
                .as("the broker receives the access token")
                .isEqualTo(ACCESS);
        assertThat(credential.attributes())
                .as("the host receives the renewal attributes and every client to pick from")
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                Credential.REFRESH_TOKEN,
                                REFRESH,
                                Credential.EXPIRES_AT,
                                Long.toString(Instant.parse(expiry).toEpochMilli()),
                                Credential.TOKEN_URL,
                                "https://oauth2.googleapis.com/token",
                                AgyCredentialExtractor.CLIENT_ID_CANDIDATES,
                                CLIENT_ID,
                                AgyCredentialExtractor.CLIENT_SECRET_CANDIDATES,
                                CLIENT_SECRET));
        assertThat(credential.toString())
                .as("credential diagnostics remain redacted")
                .doesNotContain(ACCESS, REFRESH, CLIENT_SECRET);
    }
}
