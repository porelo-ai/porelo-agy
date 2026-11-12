package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The update rules, kept where Porelo's release tool reads them.
 *
 * <p>The tool applies the rule; what can break here is the property that asks for it. Without it
 * the tool takes a release the hour it appears, and nothing fails.
 */
class UpdateRulesTest {

    private static final Pattern MIN_AGE =
            Pattern.compile("<porelo\\.release\\.min-age>([^<]*)</porelo\\.release\\.min-age>");

    @Test
    void aReleaseIsTakenOnceItIsThreeDaysOld() throws IOException {

        assertThat(minAge(Files.readString(Path.of("pom.xml")))).contains("3d");
    }

    @Test
    void findsNoAgeInAPomThatSetsNone() {

        // The other half: a pom that sets no age must not read as one that does.
        assertThat(
                        minAge(
                                "<properties><porelo.release.channel>x</porelo.release.channel></properties>"))
                .isEmpty();
    }

    /**
     * Returns the age a release must reach before the release tool takes it.
     *
     * @param pom Content of {@code pom.xml}.
     * @return The age as the pom writes it, or empty when it sets none.
     */
    static Optional<String> minAge(final String pom) {
        final Matcher matcher = MIN_AGE.matcher(pom);
        return matcher.find() ? Optional.of(matcher.group(1).strip()) : Optional.empty();
    }
}
