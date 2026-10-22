package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Takes a step's script out of a workflow and runs it with bash, so a test proves what the step
 * does rather than that its text is there.
 */
final class WorkflowSteps {

    /** Long enough for any step script run against stubs, short enough to fail a hang quickly. */
    static final Duration LIMIT = Duration.ofSeconds(30);

    private WorkflowSteps() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * How a step's script ended.
     *
     * @param exit Its exit code.
     * @param output What it wrote to standard output and standard error, together.
     */
    record Run(int exit, String output) {}

    /**
     * Returns the run block of each step with the given name, its indentation removed.
     *
     * @param workflow File name under {@code .github/workflows}.
     * @param name The step's {@code name:}.
     * @return One script per step of that name, in the order of the file.
     * @throws IOException The workflow cannot be read.
     */
    static List<String> scripts(final String workflow, final String name) throws IOException {
        final List<String> lines =
                Files.readAllLines(
                        RepositoryDocuments.root().resolve(".github/workflows").resolve(workflow));
        final List<String> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).strip().equals("- name: " + name)) {
                continue;
            }
            int j = i + 1;
            while (!lines.get(j).strip().equals("run: |")) {
                j++;
            }
            final int key = indentOf(lines.get(j));
            final StringBuilder script = new StringBuilder();
            int body = -1;
            for (int k = j + 1;
                    k < lines.size() && (lines.get(k).isBlank() || indentOf(lines.get(k)) > key);
                    k++) {
                if (lines.get(k).isBlank()) {
                    script.append('\n');
                    continue;
                }
                if (body < 0) {
                    body = indentOf(lines.get(k));
                }
                script.append(lines.get(k).substring(body)).append('\n');
            }
            result.add(script.toString());
        }
        return result;
    }

    /**
     * Runs a script with bash in a directory, with the given variables added to the environment,
     * and fails when it has not ended within the limit.
     *
     * @param script The step's script.
     * @param dir Its working directory, which also holds the script and its output.
     * @param env Variables added to the environment, replacing one of the same name.
     * @param limit How long it may run.
     * @return How it ended.
     * @throws IOException The script cannot be written or started.
     * @throws InterruptedException Interrupted while waiting.
     */
    static Run run(
            final String script,
            final Path dir,
            final Map<String, String> env,
            final Duration limit)
            throws IOException, InterruptedException {
        final Path file = dir.resolve("step.sh");
        Files.writeString(file, script);
        // To a file, not a pipe: reading a pipe to its end would wait out a hang before the limit.
        final Path log = dir.resolve("step.log");
        final ProcessBuilder builder =
                new ProcessBuilder("bash", file.toString())
                        .directory(dir.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile());
        builder.environment().putAll(env);
        final Process process = builder.start();
        final boolean ended = process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS);
        if (!ended) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor();
        }
        final String output = Files.readString(log, StandardCharsets.UTF_8);
        assertThat(ended).as("the step ends within %s; it wrote:%n%s", limit, output).isTrue();
        return new Run(process.exitValue(), output);
    }

    private static int indentOf(final String line) {
        return line.length() - line.stripLeading().length();
    }
}
