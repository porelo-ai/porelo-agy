package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.fuin.porelo.agent.impl.agy.WorkflowSteps.Run;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the update job's last two steps, "Open the pull request" and "Merge, or say why not",
 * against a local git remote and a stub {@code gh}, so what they tell a person and what they merge
 * is seen, not looked for.
 */
class UpdateRequestTest {

    private static final String OPEN = "Open the pull request";

    private static final String MERGE = "Merge, or say why not";

    @Test
    void aJobThatFailedWithoutAStopNeitherSaysGreenNorMerges(@TempDir final Path dir)
            throws Exception {

        // A gate killed by its time limit or a cancel writes no STOP; read as green, an unverified
        // pin would be merged and published.
        final Map<String, String> env = setUp(dir);
        env.put("JOB_STATUS", "failure");

        final Run open =
                WorkflowSteps.run(step(OPEN), dir.resolve("repo"), env, WorkflowSteps.LIMIT);
        assertThat(open.exit()).as("exit of the request:%n%s", open.output()).isZero();
        final String body = Files.readString(dir.resolve("body"));
        assertThat(body).as("the request's body after a failed step").doesNotContain("Green");
        assertThat(body).as("the request's body after a failed step").contains("not ready");

        final Run merge =
                WorkflowSteps.run(step(MERGE), dir.resolve("repo"), env, WorkflowSteps.LIMIT);
        assertThat(merge.exit()).as("exit of the merge step:%n%s", merge.output()).isZero();
        assertThat(calls(dir))
                .as("gh calls after a failed step")
                .noneMatch(c -> c.startsWith("pr merge"));
    }

    @Test
    void aCancelledJobDoesNotMerge(@TempDir final Path dir) throws Exception {

        final Map<String, String> env = setUp(dir);
        env.put("JOB_STATUS", "cancelled");
        final Run open =
                WorkflowSteps.run(step(OPEN), dir.resolve("repo"), env, WorkflowSteps.LIMIT);
        assertThat(open.exit()).as("exit of the request:%n%s", open.output()).isZero();
        assertThat(Files.readString(dir.resolve("body"))).doesNotContain("Green");
        final Run merge =
                WorkflowSteps.run(step(MERGE), dir.resolve("repo"), env, WorkflowSteps.LIMIT);
        assertThat(merge.exit()).as("exit of the merge step:%n%s", merge.output()).isZero();
        assertThat(calls(dir))
                .as("gh calls after a cancel")
                .noneMatch(c -> c.startsWith("pr merge"));
    }

    @Test
    void aGateThatStoppedTheRunNeitherSaysGreenNorMerges(@TempDir final Path dir) throws Exception {

        // The ordinary failure: every gate catches its own and writes STOP, so the job itself
        // still reads as a success when the request is opened.
        final Map<String, String> env = setUp(dir);
        env.put("JOB_STATUS", "success");
        env.put("STOP", "acceptance failed on fedora");

        final Run open =
                WorkflowSteps.run(step(OPEN), dir.resolve("repo"), env, WorkflowSteps.LIMIT);
        assertThat(open.exit()).as("exit of the request:%n%s", open.output()).isZero();
        assertThat(Files.readString(dir.resolve("body")))
                .as("the request's body after a gate stopped the run")
                .doesNotContain("Green")
                .contains("not ready to publish: acceptance failed on fedora");

        final Run merge =
                WorkflowSteps.run(step(MERGE), dir.resolve("repo"), env, WorkflowSteps.LIMIT);
        assertThat(merge.exit()).as("exit of the merge step:%n%s", merge.output()).isZero();
        assertThat(calls(dir))
                .as("gh calls after a gate stopped the run")
                .noneMatch(c -> c.startsWith("pr merge"));
    }

    @Test
    void autoMergeWithoutItsTokenFailsRatherThanMergeUnseen(@TempDir final Path dir)
            throws Exception {

        // The job's own token merges without starting the Build, so nothing would be published
        // while the run looked successful.
        final Map<String, String> env = setUp(dir);
        env.put("JOB_STATUS", "success");
        env.put("HAVE_TOKEN", "false");
        WorkflowSteps.run(step(OPEN), dir.resolve("repo"), env, WorkflowSteps.LIMIT);

        final Run merge =
                WorkflowSteps.run(step(MERGE), dir.resolve("repo"), env, WorkflowSteps.LIMIT);
        assertThat(merge.exit())
                .as("exit of the merge step without its token:%n%s", merge.output())
                .isNotZero();
        assertThat(calls(dir))
                .as("gh calls without the merge token")
                .noneMatch(c -> c.startsWith("pr merge"));
    }

    @Test
    void aJobThatSucceededSaysGreenAndMerges(@TempDir final Path dir) throws Exception {

        // The refusals above are the job's status, not steps that never say green or merge.
        final Map<String, String> env = setUp(dir);
        env.put("JOB_STATUS", "success");

        final Run open =
                WorkflowSteps.run(step(OPEN), dir.resolve("repo"), env, WorkflowSteps.LIMIT);
        assertThat(open.exit()).as("exit of the request:%n%s", open.output()).isZero();
        assertThat(Files.readString(dir.resolve("body"))).contains("Green");
        assertThat(
                        git(
                                dir.resolve("origin.git"),
                                "rev-parse",
                                "--verify",
                                "update/antigravity-cli-1.2.3"))
                .as("the branch pushed to the remote")
                .isZero();

        final Run merge =
                WorkflowSteps.run(step(MERGE), dir.resolve("repo"), env, WorkflowSteps.LIMIT);
        assertThat(merge.exit()).as("exit of the merge step:%n%s", merge.output()).isZero();
        assertThat(calls(dir)).anyMatch(c -> c.startsWith("pr merge"));
    }

    @Test
    void aBranchLeftByAClosedRequestIsDeletedBeforeThePush(@TempDir final Path dir)
            throws Exception {

        // Measured on 2026-10-18: such a branch sits on an older main, so moving it moves the
        // workflows it holds, which GitHub refuses the job's token, and the run opens no request.
        final Map<String, String> env = setUp(dir);
        env.put("JOB_STATUS", "success");
        assertThat(
                        git(
                                dir.resolve("repo"),
                                "push",
                                "-q",
                                "origin",
                                "HEAD:refs/heads/update/antigravity-cli-1.2.3"))
                .as("the branch a closed request left")
                .isZero();

        final Run open =
                WorkflowSteps.run(step(OPEN), dir.resolve("repo"), env, WorkflowSteps.LIMIT);
        assertThat(open.exit()).as("exit of the request:%n%s", open.output()).isZero();
        assertThat(calls(dir))
                .as("gh calls with a branch left by a closed request")
                .contains(
                        "api -X DELETE repos/porelo-ai/porelo-agy/git/refs/heads/update/antigravity-cli-1.2.3")
                .anyMatch(c -> c.startsWith("pr create"));
    }

    @Test
    void onlyThePushHoldsTheTokenAndNothingWritesIt(@TempDir final Path dir) throws Exception {

        // The checkout leaves no token in .git/config, so the build and the legs on rented
        // machines cannot read it; the push gets it from its own environment and nothing keeps it.
        final Map<String, String> env = setUp(dir);
        env.put("JOB_STATUS", "success");
        final Path header = dir.resolve("push-header");
        final Path git = dir.resolve("bin/git");
        Files.writeString(
                git,
                "#!/bin/sh\n"
                        + "if [ \"$1\" = push ]; then\n"
                        + "    '"
                        + realGit()
                        + "' config --get http.https://github.com/.extraheader > '"
                        + header
                        + "' || true\n"
                        + "fi\n"
                        + "exec '"
                        + realGit()
                        + "' \"$@\"\n");
        Files.setPosixFilePermissions(git, PosixFilePermissions.fromString("rwxr-xr-x"));

        final Run open =
                WorkflowSteps.run(step(OPEN), dir.resolve("repo"), env, WorkflowSteps.LIMIT);
        assertThat(open.exit()).as("exit of the request:%n%s", open.output()).isZero();
        final String basic =
                Base64.getEncoder()
                        .encodeToString(
                                "x-access-token:not-a-token".getBytes(StandardCharsets.UTF_8));
        assertThat(Files.readString(header).strip())
                .as("the header git pushed with")
                .isEqualTo("AUTHORIZATION: basic " + basic);
        assertThat(Files.readString(dir.resolve("repo/.git/config")))
                .as("the clone's configuration after the push")
                .doesNotContain("not-a-token")
                .doesNotContain(basic)
                .doesNotContain("extraheader");
        assertThat(dir.resolve("gh-calls.header"))
                .as("gh calls that still held the header")
                .doesNotExist();
    }

    /** The git this machine runs, found on the PATH before the stubs are put in front of it. */
    private static String realGit() {
        for (final String entry : System.getenv("PATH").split(":")) {
            final Path git = Path.of(entry, "git");
            if (Files.isExecutable(git)) {
                return git.toString();
            }
        }
        throw new IllegalStateException("no git on the PATH " + System.getenv("PATH"));
    }

    private static String step(final String name) throws IOException {
        final List<String> steps = WorkflowSteps.scripts("update.yml", name);
        assertThat(steps).as("the step \"%s\" in update.yml", name).hasSize(1);
        return steps.get(0);
    }

    private static List<String> calls(final Path dir) throws IOException {
        final Path calls = dir.resolve("gh-calls");
        return Files.exists(calls) ? Files.readAllLines(calls) : List.of();
    }

    /**
     * A clone with the pin changed, its bare origin, and a stub {@code gh} that has no pull request
     * open, records its calls and keeps the body it was given.
     */
    private static Map<String, String> setUp(final Path dir)
            throws IOException, InterruptedException {
        final Path origin = dir.resolve("origin.git");
        final Path repo = dir.resolve("repo");
        assertThat(git(dir, "init", "-q", "--bare", origin.toString())).isZero();
        assertThat(git(dir, "init", "-q", "-b", "main", repo.toString())).isZero();
        Files.writeString(repo.resolve("pom.xml"), "1.2.2\n");
        assertThat(git(repo, "add", "pom.xml")).isZero();
        assertThat(
                        git(
                                repo,
                                "-c",
                                "user.name=t",
                                "-c",
                                "user.email=t@t",
                                "commit",
                                "-q",
                                "-m",
                                "x"))
                .isZero();
        assertThat(git(repo, "remote", "add", "origin", origin.toString())).isZero();
        Files.writeString(repo.resolve("pom.xml"), "1.2.3\n");

        final Path bin = Files.createDirectories(dir.resolve("bin"));
        final Path gh = bin.resolve("gh");
        Files.writeString(
                gh,
                """
                #!/bin/sh
                echo "$*" >> "$CALLS"
                [ -n "${GIT_CONFIG_VALUE_0:-}" ] && echo "$1 $2" >> "$CALLS.header"
                while [ $# -gt 0 ]; do
                    [ "$1" = --body-file ] && cp "$2" "$BODY"
                    shift
                done
                exit 0
                """);
        Files.setPosixFilePermissions(gh, PosixFilePermissions.fromString("rwxr-xr-x"));
        final Path temp = Files.createDirectories(dir.resolve("runner-temp"));
        Files.createDirectories(repo.resolve("target"));

        final Map<String, String> env = new HashMap<>(gitEnv());
        env.put("PATH", bin + ":" + System.getenv("PATH"));
        env.put("CALLS", dir.resolve("gh-calls").toString());
        env.put("BODY", dir.resolve("body").toString());
        env.put("RUNNER_TEMP", temp.toString());
        env.put("GITHUB_OUTPUT", dir.resolve("output").toString());
        env.put("GH_TOKEN", "not-a-token");
        env.put("TARGET", "1.2.3");
        env.put("WAS", "1.2.2");
        env.put("AUTO", "true");
        env.put("HAVE_TOKEN", "true");
        env.put("GITHUB_REPOSITORY", "porelo-ai/porelo-agy");
        env.put("GITHUB_SHA", "the-checked-out-commit");
        env.put("GITHUB_RUN_NUMBER", "7");
        return env;
    }

    /** The machine's own git configuration stays out. */
    private static Map<String, String> gitEnv() {
        return Map.of("GIT_CONFIG_GLOBAL", "/dev/null", "GIT_CONFIG_NOSYSTEM", "1");
    }

    private static int git(final Path dir, final String... args)
            throws IOException, InterruptedException {
        final ProcessBuilder builder = new ProcessBuilder();
        builder.command().add("git");
        builder.command().addAll(List.of(args));
        builder.directory(dir.toFile()).redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.environment().putAll(gitEnv());
        final Process process = builder.start();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("git %s ends", List.of(args)).isTrue();
        return process.exitValue();
    }
}
