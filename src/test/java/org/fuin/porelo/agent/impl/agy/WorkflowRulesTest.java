package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.fuin.porelo.agent.impl.agy.WorkflowSteps.Run;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Guards package checks and the acceptance leg's secret gate. */
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
    void anAcceptanceLegRefusesToRunWithoutTheKey(@TempDir final Path dir) throws Exception {
        final List<String> steps =
                WorkflowSteps.scripts("build.yml", "Acceptance on ${{ matrix.os }}");
        assertThat(steps).as("the acceptance step of build.yml").hasSize(1);
        final Path calls = stubJava(dir);
        final Map<String, String> env = new HashMap<>();
        env.put("PATH", dir + ":" + System.getenv("PATH"));
        env.put("GITHUB_REF_TYPE", "branch");
        env.put("OS", "ubuntu");
        env.put("GEMINI_API_KEY", "");
        Files.writeString(dir.resolve("pom.xml"), "<agent.cli.version>1.4.5</agent.cli.version>\n");

        final Run empty = WorkflowSteps.run(steps.getFirst(), dir, env, WorkflowSteps.LIMIT);
        assertThat(empty.exit())
                .as("exit of the leg with an empty key:%n%s", empty.output())
                .isNotZero();
        assertThat(empty.output())
                .as("the empty GOOGLE_API refusal message")
                .contains("the secret GOOGLE_API is empty");
        assertThat(calls).as("the acceptance run although the key is empty").doesNotExist();

        env.put("GEMINI_API_KEY", "porelo-test-key");
        final Run keyed = WorkflowSteps.run(steps.getFirst(), dir, env, WorkflowSteps.LIMIT);
        assertThat(keyed.exit()).as("exit of the leg with a key:%n%s", keyed.output()).isZero();
        assertThat(Files.readString(calls))
                .as("what the leg ran with a key")
                .contains("acceptance --os ubuntu --package porelo-agent-agy --cucumber .");
        assertThat(Files.readString(Path.of(".github/workflows/build.yml")))
                .as("the acceptance leg's refused-key setting in build.yml")
                .contains("PORELO_E2E_REFUSED_KEY: porelo-acceptance-not-a-key");
    }

    private static Path stubJava(final Path dir) throws IOException {
        final Path calls = dir.resolve("java-calls");
        final Path java = dir.resolve("java");
        Files.writeString(java, "#!/bin/sh\necho \"$*\" >> '" + calls + "'\n");
        Files.setPosixFilePermissions(java, PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.createDirectories(dir.resolve("target"));
        Files.writeString(dir.resolve("target/cp.txt"), "cp");
        return calls;
    }
}
