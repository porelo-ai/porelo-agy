package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.fuin.porelo.agent.impl.agy.WorkflowSteps.Run;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs both "Which channel" steps of the build workflow as a tag's run would, against a pom that
 * names a Porelo snapshot and one that names a release. The step itself is what refuses a release
 * built on a snapshot, so the step is run, not looked for.
 */
class ReleaseChannelTest {

    private static final String REFUSAL =
            "is a snapshot; a release is built against a released Porelo";

    @Test
    void aTagIsRefusedWhilePoreloIsASnapshot(@TempDir final Path dir) throws Exception {
        final List<String> steps = whichChannel();
        assertThat(steps)
                .as("\"Which channel\" steps in build.yml, the build job's and the release job's")
                .hasSize(2);
        for (final String step : steps) {
            final Run run = run(step, dir, "0.5.1-SNAPSHOT");
            assertThat(run.exit()).as("exit of a tag on a Porelo snapshot").isNotZero();
            assertThat(run.output()).as("the snapshot refusal").contains(REFUSAL);
        }
    }

    @Test
    void aTagOnAReleasedPoreloIsAReleaseChannel(@TempDir final Path dir) throws Exception {
        for (final String step : whichChannel()) {
            final Run run = run(step, dir, "0.5.1");
            assertThat(run.exit())
                    .as("exit of a tag on a released Porelo:%n%s", run.output())
                    .isZero();
            assertThat(Files.readString(dir.resolve("output")))
                    .as("the channel written to GITHUB_OUTPUT")
                    .contains("channel=releases");
        }
    }

    private static Run run(final String script, final Path dir, final String porelo)
            throws IOException, InterruptedException {
        Files.writeString(
                dir.resolve("pom.xml"),
                "<project>\n    <version>0.1.0</version>\n    <properties>\n"
                        + "        <porelo.version>"
                        + porelo
                        + "</porelo.version>\n    </properties>\n</project>\n");
        Files.writeString(dir.resolve("output"), "");
        return WorkflowSteps.run(
                script,
                dir,
                Map.of(
                        "GITHUB_REF_TYPE", "tag",
                        "GITHUB_REF_NAME", "v0.1.0",
                        "GITHUB_OUTPUT", dir.resolve("output").toString()),
                WorkflowSteps.LIMIT);
    }

    private static List<String> whichChannel() throws IOException {
        return WorkflowSteps.scripts("build.yml", "Which channel");
    }
}
