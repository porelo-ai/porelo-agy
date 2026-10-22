package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Guards package checks and keeps provider-independent CI free of live credentials. */
class WorkflowRulesTest {
    @Test
    void packageBuildsCannotSkipTheirChecks() throws IOException {
        final List<String> scripts =
                WorkflowSteps.scripts("build.yml", "Native binary, protocol and packages");
        assertThat(scripts).as("the native package build is actually present").hasSize(1);
        assertThat(scripts.getFirst())
                .as("both package verification and native protocol checks run")
                .contains("-Pnative,dist verify")
                .doesNotContain("skipTests", "maven.test.skip");
    }

    @Test
    void workflowsDoNotRequestLiveCredentialsOrRentMachines() throws IOException {
        final List<Path> workflows;
        try (Stream<Path> paths = Files.list(Path.of(".github/workflows"))) {
            workflows = paths.filter(path -> path.toString().endsWith(".yml")).sorted().toList();
        }
        assertThat(workflows).as("all provider-independent workflows").hasSize(3);
        for (final Path workflow : workflows) {
            assertThat(Files.readString(workflow))
                    .as("no live leg before a provider contract: %s", workflow)
                    .doesNotContain(
                            "secrets.",
                            "machines.Main acceptance",
                            "contents: write",
                            "pull-requests: write");
        }
    }
}
