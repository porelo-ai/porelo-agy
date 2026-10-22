package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link WorkflowSteps}, which the tests of workflow steps run their scripts through. */
class WorkflowStepsTest {

    @Test
    void aStepThatHangsFailsAtTheLimitInsteadOfHoldingTheSuite(@TempDir final Path dir) {

        // A step stuck on a stub or a prompt would otherwise hold the whole suite until the
        // build's own limit, and say nothing about which step it was.
        final Duration limit = Duration.ofSeconds(2);
        final long started = System.nanoTime();
        assertThatThrownBy(() -> WorkflowSteps.run("sleep 20\n", dir, Map.of(), limit))
                .as("a step sleeping past its limit")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("the step ends within");
        assertThat(Duration.ofNanos(System.nanoTime() - started))
                .as("time until the hanging step was given up")
                .isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void aStepThatEndsGivesItsExitAndOutput(@TempDir final Path dir) throws Exception {

        final WorkflowSteps.Run run =
                WorkflowSteps.run(
                        "echo \"out $NAME\"; echo err >&2; exit 3\n",
                        dir,
                        Map.of("NAME", "x"),
                        WorkflowSteps.LIMIT);
        assertThat(run.exit()).as("the script's exit").isEqualTo(3);
        assertThat(run.output()).as("both streams").contains("out x").contains("err");
    }
}
