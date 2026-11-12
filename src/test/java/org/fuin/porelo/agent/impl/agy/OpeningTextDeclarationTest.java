package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * How an attended Antigravity CLI session takes its opening text: the harm is a discussion that is
 * refused, or one that runs in print mode and ends after its first answer instead of waiting for
 * the person.
 */
class OpeningTextDeclarationTest {

    @Test
    void declaresThatTheOpeningTextFollowsTheInteractivePromptFlag() {

        assertThat(new AgyAgent().definition().openingFlag())
                .as("agy.yaml declares session.opening_flag, agy's --prompt-interactive")
                .isEqualTo("-i");
    }

    @Test
    void startsTheSessionWithTheTextBehindItsFlagAndWithoutPrintMode() {

        final List<String> command =
                new AgyAgent()
                        .definition()
                        .attendedCommand("cheap-model", "Name the file you read first");

        assertThat(command.subList(command.size() - 2, command.size()))
                .as("the opening text follows -i, last: %s", command)
                .containsExactly("-i", "Name the file you read first");
        assertThat(command)
                .as("-p would run once and end instead of keeping the session: %s", command)
                .doesNotContain("-p", "--print");
    }
}
