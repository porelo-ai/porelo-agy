package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.fuin.porelo.agent.impl.agy.WorkflowSteps.Run;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs every step that deletes what a run rented, with a machine tooling that could not delete
 * everything. A cleanup that fails quietly leaves rented machines running and paid for, with a
 * green run that says nothing of them.
 */
class MachineCleanupTest {

    @Test
    void aCleanupThatLeavesSomethingFailsItsStep(@TempDir final Path dir) throws Exception {

        final List<String> steps = new ArrayList<>();
        steps.addAll(WorkflowSteps.scripts("build.yml", "Delete what this run created"));
        steps.addAll(WorkflowSteps.scripts("update.yml", "Delete what this run created"));
        steps.addAll(
                WorkflowSteps.scripts("update.yml", "Delete what this run created - fedora leg"));
        assertThat(steps)
                .as("cleanup steps: the acceptance job's and the update job's two legs")
                .hasSize(3);
        Files.createDirectories(dir.resolve("target"));
        Files.writeString(dir.resolve("target/cp.txt"), "tooling.jar");
        final Path bin = Files.createDirectories(dir.resolve("bin"));
        final Path java = bin.resolve("java");
        Files.writeString(java, "#!/bin/sh\necho \"left server acc-ubuntu-1\" >&2\nexit 1\n");
        Files.setPosixFilePermissions(java, PosixFilePermissions.fromString("rwxr-xr-x"));

        for (final String step : steps) {
            final Run run =
                    WorkflowSteps.run(
                            step,
                            dir,
                            Map.of("PATH", bin + ":" + System.getenv("PATH")),
                            WorkflowSteps.LIMIT);
            assertThat(run.exit())
                    .as("exit of %s when the sweep left something:%n%s", step.strip(), run.output())
                    .isNotZero();
        }
    }
}
