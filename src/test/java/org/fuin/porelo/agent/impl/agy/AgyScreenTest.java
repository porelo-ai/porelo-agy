package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.fuin.porelo.agent.api.AtRest;
import org.fuin.porelo.agent.api.Waiting;
import org.junit.jupiter.api.Test;

/** Guards wake input against measured busy screens and open confirmation or selection dialogs. */
class AgyScreenTest {

    private static final String TRUST =
            """
            Workspace Trust Required
            /workspace
            Do you trust the files in this folder?
            ❯ Trust folder
              Don't trust
              ↑/↓ Navigate · enter Confirm · esc Exit
            """;

    private static final String QUESTION =
            """
            Which option should we use?
            ❯ Option A
              Option B
              ↑/↓ Navigate · enter Select · esc Cancel
            """;

    @Test
    void wakeInputIsAllowedOnlyAtTheIdleComposer() throws IOException {
        final AtRest rest = new AgyAgent().definition().atRest();
        assertThat(rest).as("a task needs an explicit safe wake boundary").isNotNull();
        assertThat(rest.matches(screen("ready")))
                .as("the measured idle composer accepts input")
                .isTrue();
        assertThat(rest.matches(screen("busy")))
                .as("a working turn must not receive wake input")
                .isFalse();
        assertThat(rest.matches(TRUST)).as("a wake must not confirm workspace trust").isFalse();
        assertThat(rest.matches(QUESTION)).as("a wake must not answer a question dialog").isFalse();
        assertThat(rest.matches("")).as("an absent CLI cannot receive wake input").isFalse();
    }

    @Test
    void startupReadinessDoesNotPassAFirstRunDialog() throws IOException {
        final var ready = new AgyAgent().definition().ready();
        assertThat(ready).as("attended acceptance must wait for actual readiness").isNotNull();
        assertThat(screen("ready"))
                .as("readiness is observed at the composer")
                .contains(ready.text());
        assertThat(TRUST).as("workspace trust still needs an answer").doesNotContain(ready.text());
        assertThat(QUESTION).as("a question dialog is not readiness").doesNotContain(ready.text());
    }

    @Test
    void observedDialogsRequireInputButWorkingDoesNot() throws IOException {
        final Waiting waiting = new AgyAgent().definition().waiting();
        assertThat(waiting).as("the adapter must declare observed input boundaries").isNotNull();
        assertThat(waiting.read(TRUST).seen())
                .as("workspace trust needs a confirmation")
                .isEqualTo(Waiting.Seen.WAITING);
        assertThat(waiting.read(QUESTION).seen())
                .as("a question dialog needs a selection")
                .isEqualTo(Waiting.Seen.WAITING);
        assertThat(waiting.read(screen("ready")).seen())
                .as("an idle composer is not a dialog")
                .isEqualTo(Waiting.Seen.NOT_WAITING);
        assertThat(waiting.read(screen("busy")).seen())
                .as("a busy turn is not a dialog")
                .isEqualTo(Waiting.Seen.NOT_WAITING);
    }

    private static String screen(String name) throws IOException {
        try (var input = AgyScreenTest.class.getResourceAsStream("/screens/" + name + ".screen")) {
            if (input == null) {
                throw new IOException("Missing measured screen: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
