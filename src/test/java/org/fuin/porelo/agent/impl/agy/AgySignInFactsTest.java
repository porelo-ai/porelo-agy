package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import org.fuin.porelo.agent.api.Finding;
import org.fuin.porelo.agent.api.Finding.Kind;
import org.fuin.porelo.agent.api.Finding.SignedIn;
import org.fuin.porelo.agent.api.Finding.What;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The token file's shape is the one the extractor takes, which Antigravity CLI writes. */
class AgySignInFactsTest {

    private static final String SECRET = "fixture-secret-value";
    private static final String EXPIRY = "2026-12-01T10:00:00Z";
    private static final Instant CHANGED = Instant.parse("2026-11-01T10:00:00Z");

    @TempDir Path home;

    private Path config;

    @BeforeEach
    void createTheConfigDirectory() throws IOException {
        config = Files.createDirectory(home.resolve("antigravity-cli"));
    }

    private List<AgySignInFacts> facts() {
        return AgySignInFacts.of(new AgyFindings().find(config), config);
    }

    private void token(final String content) throws IOException {
        Files.setLastModifiedTime(
                Files.writeString(config.resolve(AgyCredentialExtractor.TOKEN_FILE), content),
                FileTime.from(CHANGED));
    }

    // Prevents the person choosing a sign-in blind: how fresh it is and how long it holds.
    @Test
    void aConsumerSignInSaysItsExpiryAndLastChange() throws IOException {
        token(AgyFindingsTest.login(SECRET, SECRET, EXPIRY));

        assertThat(facts())
                .singleElement()
                .satisfies(
                        facts -> {
                            assertThat(facts.finding().kind()).isEqualTo(Kind.SUBSCRIPTION);
                            assertThat(facts.finding().importable()).as("carriable").isTrue();
                            assertThat(facts.expires())
                                    .as("the file's expiry")
                                    .isEqualTo(Instant.parse(EXPIRY));
                            assertThat(facts.changed())
                                    .as("the file's own time")
                                    .isEqualTo(CHANGED);
                        });
    }

    // Prevents an expiry with an offset from being shown at the wrong instant.
    @Test
    void anOffsetExpiryIsTheSameInstant() throws IOException {
        token(AgyFindingsTest.login(SECRET, SECRET, "2026-12-01T11:00:00.5+01:00"));

        assertThat(facts())
                .singleElement()
                .extracting(AgySignInFacts::expires)
                .as("converted as the extractor converts it, to the millisecond")
                .isEqualTo(Instant.parse("2026-12-01T10:00:00.500Z"));
    }

    // Prevents a guess: the token file names no account, and a token is never decoded for one.
    @Test
    void noAccountIsNamed() throws IOException {
        token(AgyFindingsTest.login(SECRET, SECRET, EXPIRY));

        assertThat(facts()).singleElement().extracting(AgySignInFacts::account).isNull();
    }

    // Prevents a file an import cannot take from being described as a sign-in.
    @Test
    void aTokenFileWithoutASignInHasNoFacts() throws IOException {
        token("{\"auth_method\":\"enterprise\",\"token\":{}}");

        assertThat(facts()).isEmpty();
    }

    // Prevents facts being attached to a finding about another file.
    @Test
    void aFindingOfAnotherPathHasNoFacts() throws IOException {
        token(AgyFindingsTest.login(SECRET, SECRET, EXPIRY));
        final Finding elsewhere =
                new Finding(
                        What.SIGN_IN,
                        home.resolve("other").toString(),
                        SignedIn.SIGNED_IN,
                        true,
                        AgyFindings.PROVIDER,
                        Kind.SUBSCRIPTION);

        assertThat(AgySignInFacts.of(List.of(elsewhere), config)).isEmpty();
    }

    // Prevents a credential's value leaving the adapter in what is shown to the person.
    @Test
    void noFactCarriesACredentialsValue() throws IOException {
        token(AgyFindingsTest.login(SECRET, SECRET, EXPIRY));

        final List<AgySignInFacts> facts = facts();

        assertThat(facts).as("the sign-in was read").hasSize(1);
        assertThat(facts.toString()).as("no value in any field").doesNotContain(SECRET);
    }
}
