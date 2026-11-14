package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.regex.Pattern;
import org.fuin.porelo.agent.api.AgentException;
import org.fuin.porelo.agent.api.Finding;
import org.fuin.porelo.agent.api.Finding.Kind;
import org.fuin.porelo.agent.api.Finding.SignedIn;
import org.fuin.porelo.agent.api.Finding.What;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The token file's shape is the one the extractor takes, which Antigravity CLI writes. */
class AgyFindingsTest {

    private static final String SECRET = "fixture-secret-value";
    private static final String EXPIRY = "2026-12-01T10:00:00Z";

    @TempDir Path home;

    private Path config;

    @BeforeEach
    void createTheConfigDirectory() throws IOException {
        config = Files.createDirectory(home.resolve("antigravity-cli"));
    }

    static String login(final String access, final String refresh, final String expiry) {
        return "{\"auth_method\":\"consumer\",\"token\":{\"access_token\":\""
                + access
                + "\",\"token_type\":\"Bearer\",\"refresh_token\":\""
                + refresh
                + "\",\"expiry\":\""
                + expiry
                + "\"}}";
    }

    private void token(final String content) throws IOException {
        Files.writeString(config.resolve(AgyCredentialExtractor.TOKEN_FILE), content);
    }

    private List<Finding> find() {
        return new AgyFindings().find(config);
    }

    private String tokenPath() throws IOException {
        return config.toRealPath().resolve(AgyCredentialExtractor.TOKEN_FILE).toString();
    }

    private Finding configuration() throws IOException {
        return new Finding(
                What.CONFIGURATION, config.toRealPath().toString(), null, null, null, null);
    }

    private static Finding signIn(final String path, final SignedIn state) {
        return new Finding(What.SIGN_IN, path, state, null, null, null);
    }

    private AgyCredentialExtractor extractor() {
        return new AgyCredentialExtractor(
                home.resolve("no-cli"),
                Pattern.compile(AgyCredentialExtractorTest.CLIENT_ID),
                Pattern.compile(AgyCredentialExtractorTest.CLIENT_SECRET));
    }

    /** The extractor, given the clients it needs beside the token, so only the token decides. */
    private void extractorTakes() throws IOException {
        Files.writeString(config.resolve(".client-ids"), AgyCredentialExtractorTest.CLIENT_ID);
        Files.writeString(
                config.resolve(".client-secrets"), AgyCredentialExtractorTest.CLIENT_SECRET);
        assertThat(extractor().extract(config))
                .as("the extractor takes the sign-in said to be carriable")
                .isPresent();
    }

    private void extractorRefuses() {
        assertThatThrownBy(() -> extractor().extract(config))
                .as("the extractor refuses the sign-in said not to be one")
                .isInstanceOf(AgentException.class);
    }

    // Prevents a sign-in an import takes from going unoffered, or offered without its provider.
    @Test
    void aConsumerSignInIsCarriableToTheGoogleSignInProvider() throws IOException {
        token(login(SECRET, SECRET, EXPIRY));

        assertThat(find())
                .as("the directory, then its sign-in")
                .containsExactly(
                        configuration(),
                        new Finding(
                                What.SIGN_IN,
                                tokenPath(),
                                SignedIn.SIGNED_IN,
                                true,
                                "google-sign-in",
                                Kind.SUBSCRIPTION));
        extractorTakes();
    }

    // Prevents a renewable sign-in whose access token ran out from being said not carriable.
    @Test
    void anExpiredAccessTokenIsStillCarriable() throws IOException {
        token(login(SECRET, SECRET, "2020-01-01T00:00:00Z"));

        assertThat(find())
                .as("the refresh token renews it on the host")
                .contains(
                        new Finding(
                                What.SIGN_IN,
                                tokenPath(),
                                SignedIn.SIGNED_IN,
                                true,
                                "google-sign-in",
                                Kind.SUBSCRIPTION));
        extractorTakes();
    }

    // Prevents the finding naming a provider the agent's definition does not have.
    @Test
    void theProviderIsTheDefinitionsDefault() {
        assertThat(AgyFindings.PROVIDER)
                .as("a consumer sign-in serves the definition's default provider")
                .isEqualTo(new AgyAgent().definition().provider().defaultProvider());
    }

    // Prevents the agent answering with the API's default finder, which says nothing.
    @Test
    void theAgentAnswersWithThisFinder() {
        assertThat(new AgyAgent().finder())
                .as("Porelo asks the agent for its finder")
                .isInstanceOf(AgyFindings.class);
    }

    // Prevents a file the extractor refuses from being offered as carriable.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"auth_method\":\"enterprise\",\"token\":{}}",
                "{\"auth_method\":\"consumer\",\"token\":null}",
                "{\"auth_method\":\"consumer\",\"token\":{\"access_token\":\"fixture-secret-value\","
                        + "\"expiry\":\"2026-12-01T10:00:00Z\"}}",
                "{\"auth_method\":\"consumer\",\"token\":{\"access_token\":\"fixture-secret-value\","
                        + "\"refresh_token\":\"fixture-secret-value\",\"expiry\":\"soon\"}}",
                "{\"auth_method\":\"consumer\",\"token\":{\"access_token\":\"fixture-secret-value"
                        + " x\",\"refresh_token\":\"fixture-secret-value\","
                        + "\"expiry\":\"2026-12-01T10:00:00Z\"}}",
                "[]",
                "{\"fixture-secret-value\":"
            })
    void aTokenFileTheExtractorRefusesIsNotSignedIn(final String content) throws IOException {
        token(content);

        assertThat(find())
                .as("a file that cannot be used, said without a value")
                .containsExactly(configuration(), signIn(tokenPath(), SignedIn.NOT_SIGNED_IN));
        extractorRefuses();
    }

    // Prevents an oversized file from being read whole or offered.
    @Test
    void anOversizedTokenFileIsNotSignedIn() throws IOException {
        token(login(SECRET, SECRET, EXPIRY) + " ".repeat(AgyCredentialExtractor.MAX_TOKEN_BYTES));

        assertThat(find()).contains(signIn(tokenPath(), SignedIn.NOT_SIGNED_IN));
        extractorRefuses();
    }

    // Prevents a directory in the token file's place from being taken for a sign-in.
    @Test
    void aTokenDirectoryIsNotSignedIn() throws IOException {
        Files.createDirectory(config.resolve(AgyCredentialExtractor.TOKEN_FILE));

        assertThat(find()).contains(signIn(tokenPath(), SignedIn.NOT_SIGNED_IN));
    }

    // Prevents a link from offering another file's sign-in, which an import refuses.
    @Test
    void aLinkedTokenFileIsNotFollowed() throws IOException {
        final Path other = Files.writeString(home.resolve("other"), login(SECRET, SECRET, EXPIRY));
        Files.createSymbolicLink(config.resolve(AgyCredentialExtractor.TOKEN_FILE), other);

        assertThat(find())
                .as("what the link leads to is not read, so it cannot be told")
                .containsExactly(configuration(), signIn(tokenPath(), SignedIn.CANNOT_SAY));
        extractorRefuses();
    }

    // Prevents a linked configuration directory from being read, which an import refuses.
    @Test
    void aLinkedConfigurationDirectoryIsNotFollowed() throws IOException {
        token(login(SECRET, SECRET, EXPIRY));
        final Path link = Files.createSymbolicLink(home.resolve("linked"), config);

        assertThat(new AgyFindings().find(link))
                .as("only that it cannot be told")
                .containsExactly(signIn(link.toString(), SignedIn.CANNOT_SAY));
    }

    // Prevents an unreadable file from being said not signed in, which it may well be.
    @Test
    void anUnreadableTokenFileCannotSay() throws IOException {
        assumeFalse("root".equals(System.getProperty("user.name")), "root reads every file");
        token(login(SECRET, SECRET, EXPIRY));
        final Path file = config.resolve(AgyCredentialExtractor.TOKEN_FILE);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
        try {
            assertThat(find())
                    .containsExactly(configuration(), signIn(tokenPath(), SignedIn.CANNOT_SAY));
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }

    // Prevents a directory without a sign-in from being offered as one.
    @Test
    void noTokenFileIsOnlyTheConfiguration() throws IOException {
        assertThat(find()).as("no sign-in finding at all").containsExactly(configuration());
    }

    // Prevents a person who never ran the CLI from being told of a configuration.
    @Test
    void noConfigurationDirectoryIsNothing() {
        assertThat(new AgyFindings().find(home.resolve("missing"))).isEmpty();
    }

    // Prevents a credential's value leaving the adapter in what is shown to the person.
    @Test
    void noFindingCarriesAValue() throws IOException {
        token(login(SECRET, SECRET, EXPIRY));
        final List<Finding> signedIn = find();
        token("{\"" + SECRET + "\":");
        final List<Finding> broken = find();

        assertThat(signedIn).as("the sign-in was read").hasSize(2);
        assertThat(signedIn + " " + broken).as("no value in any field").doesNotContain(SECRET);
    }

    // Prevents the sign-in the finder and the extractor share from naming a token in its text.
    @Test
    void theSharedSignInNamesNoToken() {
        final var signIn =
                AgyCredentialExtractor.consumerSignIn(
                        login(SECRET, SECRET, EXPIRY)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(signIn.toString()).as("only its expiry").doesNotContain(SECRET);
    }
}
