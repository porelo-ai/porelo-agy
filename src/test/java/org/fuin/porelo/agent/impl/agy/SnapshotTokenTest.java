package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the forge's snapshot registry is read with: every step that builds or tests holds only the
 * read-only package token, since a branch's code runs in those steps, and a forge that names no
 * snapshot repository stops instead of reading Central's. Self-contained, so the same test stands
 * beside every adapter's workflows.
 */
class SnapshotTokenTest {

    /**
     * The token that writes the forge's registry, by its bare name: nothing in this repository
     * deploys there, so no file names it in any notation.
     */
    private static final String PUBLISHER = "PORELO_PUBLISHER_TOKEN";

    /** The token of scope {@code read:package} the forge's organization holds for builds. */
    private static final String READER = "secrets.PORELO_PACKAGES_READ_TOKEN";

    private static final String CHECK = "The forge names its snapshot repository";

    @Test
    void noStepHoldsTheTokenThatWritesTheRegistry() throws IOException {

        // A branch's tests run in these steps, so a write token there lets any branch replace a
        // snapshot every other repository builds against.
        final Path github = RepositoryDocuments.root().resolve(".github");
        final Map<String, List<String>> uses = uses(github);

        // The pinned-jdk action names no secret: it reads the token its calling step holds.
        assertThat(uses.get(READER))
                .as("the files whose steps read snapshots with the read-only token")
                .containsExactly(
                        "workflows/build.yml",
                        "workflows/shared-rules.yml",
                        "workflows/update.yml",
                        "workflows/workflows.yml");
        assertThat(uses.get(PUBLISHER)).as("the files holding the publisher's token").isEmpty();
        assertThat(Files.readString(RepositoryDocuments.root().resolve("settings.xml")))
                .as("settings.xml")
                .doesNotContain(PUBLISHER)
                .contains("${env.PORELO_PACKAGES_READ_TOKEN}");
    }

    @Test
    void seesThePublishersTokenInAnyFileOfSteps(@TempDir final Path dir) throws IOException {

        Files.createDirectories(dir.resolve("workflows"));
        Files.createDirectories(dir.resolve("actions/jdk"));
        Files.writeString(
                dir.resolve("workflows/a.yml"),
                "                  PORELO_PUBLISHER_TOKEN: ${{ secrets.PORELO_PUBLISHER_TOKEN }}\n");
        Files.writeString(
                dir.resolve("actions/jdk/action.yml"),
                "  TOKEN: ${{secrets.PORELO_PUBLISHER_TOKEN}}\n");
        Files.writeString(
                dir.resolve("workflows/b.yaml"),
                "                  T: ${{ secrets.PORELO_PACKAGES_READ_TOKEN }}\n");
        Files.writeString(
                dir.resolve("workflows/c.yml"),
                "                  T: ${{ secrets['PORELO_PUBLISHER_TOKEN'] }}\n");

        final Map<String, List<String>> uses = uses(dir);

        assertThat(uses.get(PUBLISHER))
                .as("the files holding the publisher's token")
                .containsExactly("actions/jdk/action.yml", "workflows/a.yml", "workflows/c.yml");
        assertThat(uses.get(READER))
                .as("the files holding the read-only token")
                .containsExactly("workflows/b.yaml");
    }

    @Test
    void aForgeThatNamesNoSnapshotRepositoryStops(@TempDir final Path dir) throws Exception {

        // Without it the standard profile reads Central's snapshots, and the forge's own, a task's
        // change among them, are never built against, without a word.
        final String check = check();

        final Run unnamed =
                run(
                        check,
                        dir,
                        Map.of(
                                "GITHUB_SERVER_URL", "https://forge.example.org",
                                "MAVEN_SNAPSHOTS_URL", ""));
        assertThat(unnamed.exit())
                .as("exit on a forge without a snapshot repository:%n%s", unnamed.output())
                .isNotZero();
        assertThat(unnamed.output()).as("what it said").contains("MAVEN_SNAPSHOTS_URL");

        final Run unset = run(check, dir, Map.of("GITHUB_SERVER_URL", "https://forge.example.org"));
        assertThat(unset.exit())
                .as("exit on a forge where the variable is not set at all:%n%s", unset.output())
                .isNotZero();

        final Run blank =
                run(
                        check,
                        dir,
                        Map.of(
                                "GITHUB_SERVER_URL", "https://forge.example.org",
                                "MAVEN_SNAPSHOTS_URL", " \t\n"));
        assertThat(blank.exit())
                .as("exit on a forge whose variable is only white space:%n%s", blank.output())
                .isNotZero();
        assertThat(blank.output()).as("what it said").contains("MAVEN_SNAPSHOTS_URL");

        final Run named =
                run(
                        check,
                        dir,
                        Map.of(
                                "GITHUB_SERVER_URL", "https://forge.example.org",
                                "MAVEN_SNAPSHOTS_URL",
                                        "https://forge.example.org/api/packages/porelo/maven"));
        assertThat(named.exit()).as("exit on a forge that names it:%n%s", named.output()).isZero();
    }

    @Test
    void theCentralForgeBuildsAsBefore(@TempDir final Path dir) throws Exception {

        // There the standard profile is Central's snapshots, which is what the variable names.
        final Run central =
                run(
                        check(),
                        dir,
                        Map.of(
                                "GITHUB_SERVER_URL",
                                "https://github.com",
                                "MAVEN_SNAPSHOTS_URL",
                                ""));
        assertThat(central.exit())
                .as("exit on the central forge without the variable:%n%s", central.output())
                .isZero();
    }

    /**
     * What a script printed and how it exited.
     *
     * @param exit Its exit code.
     * @param output Its standard output and error, in the order written.
     */
    record Run(int exit, String output) {}

    /**
     * The check's script, its block's indentation removed. It must be the first step of the action
     * every job that runs Maven starts with, and run unconditionally, or a Maven call before it
     * reads Central's snapshots after all.
     */
    private static String check() throws IOException {
        final List<String> step =
                firstStep(
                        Files.readAllLines(
                                RepositoryDocuments.root()
                                        .resolve(".github/actions/pinned-jdk/action.yml")));
        final int keys = indentOf(step.get(0)) + 2;
        assertThat(step.get(0).strip())
                .as("the first step of the pinned-jdk action")
                .isEqualTo("- name: " + CHECK);
        assertThat(step.stream().filter(line -> indentOf(line) == keys).map(String::strip))
                .as("the keys of \"%s\"", CHECK)
                .noneMatch(key -> key.startsWith("if:"))
                .contains("run: |");
        final int run = step.indexOf(" ".repeat(keys) + "run: |");
        final StringBuilder script = new StringBuilder();
        int body = -1;
        for (final String line : step.subList(run + 1, step.size())) {
            if (line.isBlank()) {
                script.append('\n');
                continue;
            }
            if (body < 0) {
                body = indentOf(line);
            }
            script.append(line.substring(body)).append('\n');
        }
        return script.toString();
    }

    /** The lines of the first entry under {@code steps:}, up to the next entry or the end. */
    private static List<String> firstStep(final List<String> lines) {
        int i = 0;
        while (i < lines.size() && !lines.get(i).strip().equals("steps:")) {
            i++;
        }
        while (i < lines.size() && !lines.get(i).strip().startsWith("- ")) {
            i++;
        }
        assertThat(i).as("a step under steps: in the pinned-jdk action").isLessThan(lines.size());
        final int indent = indentOf(lines.get(i));
        final List<String> step = new ArrayList<>(List.of(lines.get(i)));
        for (int k = i + 1;
                k < lines.size() && (lines.get(k).isBlank() || indentOf(lines.get(k)) > indent);
                k++) {
            step.add(lines.get(k));
        }
        // Trailing blank lines and the comment above the next step belong to neither.
        while (step.get(step.size() - 1).isBlank()) {
            step.remove(step.size() - 1);
        }
        return step;
    }

    /**
     * Runs a script with {@code bash -e}, as a composite action's {@code shell: bash} does, with
     * only the environment given and no longer than half a minute.
     */
    private static Run run(final String script, final Path dir, final Map<String, String> env)
            throws IOException, InterruptedException {
        final Path file = Files.createTempFile("step", ".sh");
        // To a file rather than a pipe: reading a pipe to its end would wait out a hang.
        final Path out = Files.createTempFile("step", ".out");
        try {
            Files.writeString(file, script);
            return run(file, out, dir, env);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(out);
        }
    }

    private static Run run(
            final Path file, final Path out, final Path dir, final Map<String, String> env)
            throws IOException, InterruptedException {
        final ProcessBuilder builder =
                new ProcessBuilder("bash", "--noprofile", "--norc", "-e", file.toString())
                        .directory(dir.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(out.toFile());
        builder.environment().clear();
        builder.environment().put("PATH", System.getenv().getOrDefault("PATH", "/usr/bin:/bin"));
        builder.environment().put("HOME", dir.toString());
        builder.environment().put("LC_ALL", "C");
        builder.environment().putAll(env);
        final Process process = builder.start();
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                throw new AssertionError(
                        "the step did not end within 30 s:\n"
                                + Files.readString(out, StandardCharsets.UTF_8));
            }
            return new Run(process.exitValue(), Files.readString(out, StandardCharsets.UTF_8));
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly().waitFor();
            }
        }
    }

    /** A workflow or action file, whichever of its two extensions it has. */
    private static boolean isWorkflow(final Path file) {
        final String name = file.getFileName().toString();
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static int indentOf(final String line) {
        return line.length() - line.stripLeading().length();
    }

    /**
     * Reads every workflow and action below a directory for the two tokens.
     *
     * @param github The {@code .github} directory.
     * @return For each token, the files naming it, relative to the directory and sorted.
     * @throws IOException If a file cannot be read.
     */
    private static Map<String, List<String>> uses(final Path github) throws IOException {
        final List<String> publisher = new ArrayList<>();
        final List<String> reader = new ArrayList<>();
        try (Stream<Path> files = Files.walk(github)) {
            for (final Path file :
                    files.filter(Files::isRegularFile)
                            .filter(SnapshotTokenTest::isWorkflow)
                            .sorted()
                            .toList()) {
                final String text = Files.readString(file);
                final String at = github.relativize(file).toString();
                if (text.contains(PUBLISHER)) {
                    publisher.add(at);
                }
                if (text.replaceAll("\\s", "").contains(READER)) {
                    reader.add(at);
                }
            }
        }
        return Map.of(PUBLISHER, publisher, READER, reader);
    }
}
