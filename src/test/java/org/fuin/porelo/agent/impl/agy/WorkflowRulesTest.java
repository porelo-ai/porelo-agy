package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.fuin.porelo.agent.impl.agy.WorkflowSteps.Run;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Guards package checks and the secret gates of the acceptance legs and the update job. */
class WorkflowRulesTest {

    /** A Maven call whose profiles include {@code dist}, in any order: it builds the packages. */
    private static final Pattern PACKAGE_BUILD =
            Pattern.compile("\\./mvnw\\b.*\\s-P([\\w-]+,)*dist(,[\\w-]+)*(\\s|$)");

    /** Skips the tests, NativeLinkageCheck and PackageVersionCheck among them. */
    private static final Pattern SKIPS_TESTS =
            Pattern.compile("-D(skipTests|maven\\.test\\.skip)(=true)?(\\s|$)");

    @Test
    void noWorkflowBuildsPackagesWithoutTheirTests() throws IOException {

        // -DskipTests also skips NativeLinkageCheck and PackageVersionCheck, the tests that need
        // the binary and the packages; packages built so ship what nothing ran.
        final List<String> builds = new ArrayList<>();
        final List<String> skipping = new ArrayList<>();
        try (Stream<Path> files =
                Files.list(RepositoryDocuments.root().resolve(".github/workflows"))) {
            for (final Path file :
                    files.filter(f -> f.toString().endsWith(".yml")).sorted().toList()) {
                for (final String command : commands(Files.readAllLines(file))) {
                    if (PACKAGE_BUILD.matcher(command).find()) {
                        builds.add(file.getFileName().toString());
                        if (SKIPS_TESTS.matcher(command).find()) {
                            skipping.add(file.getFileName() + ": " + command);
                        }
                    }
                }
            }
        }
        assertThat(builds)
                .as("the workflows that build the packages, one build each")
                .containsExactly("build.yml", "update.yml");
        assertThat(skipping).as("package builds that skip their tests").isEmpty();
    }

    @Test
    void seesAPackageBuildWhateverTheOrderOfItsProfilesOrItsFolding() {

        final List<String> commands =
                commands(
                        List.of(
                                "      run: ./mvnw -B -Pdist,native verify",
                                "          -DskipTests",
                                "      run: |",
                                "          ./mvnw -B -Pnative,dist verify \\",
                                "          -Dmaven.test.skip=true"));
        assertThat(commands).as("commands read from a folded and a continued line").hasSize(2);
        assertThat(commands)
                .allSatisfy(
                        command -> {
                            assertThat(PACKAGE_BUILD.matcher(command).find())
                                    .as("a package build: %s", command)
                                    .isTrue();
                            assertThat(SKIPS_TESTS.matcher(command).find())
                                    .as("skips its tests: %s", command)
                                    .isTrue();
                        });
        assertThat(PACKAGE_BUILD.matcher("./mvnw -B -Pnative verify").find())
                .as("a build without the dist profile")
                .isFalse();
        assertThat(PACKAGE_BUILD.matcher("./mvnw -B -Pdistx verify").find())
                .as("a profile that only starts with dist")
                .isFalse();
    }

    @Test
    void theUpdateStopsWithoutTheKey(@TempDir final Path dir) throws Exception {

        // The update merges what its legs passed; a leg without the key passes less than it says.
        final List<String> steps =
                WorkflowSteps.scripts("update.yml", "The credential scenarios can run");
        assertThat(steps).as("the key's gate in update.yml").hasSize(1);
        final Path githubEnv = dir.resolve("github-env");

        Files.writeString(githubEnv, "");
        final Run empty =
                WorkflowSteps.run(
                        steps.getFirst(),
                        dir,
                        Map.of("KEY", "", "GITHUB_ENV", githubEnv.toString()),
                        WorkflowSteps.LIMIT);
        assertThat(empty.exit()).as("exit of the gate:%n%s", empty.output()).isZero();
        assertThat(Files.readString(githubEnv))
                .as("what the gate left for the later steps with an empty key")
                .startsWith("STOP=")
                .contains("GOOGLE_API");

        Files.writeString(githubEnv, "");
        final Run keyed =
                WorkflowSteps.run(
                        steps.getFirst(),
                        dir,
                        Map.of("KEY", "porelo-test-key", "GITHUB_ENV", githubEnv.toString()),
                        WorkflowSteps.LIMIT);
        assertThat(keyed.exit()).as("exit of the gate with a key:%n%s", keyed.output()).isZero();
        assertThat(Files.readString(githubEnv))
                .as("what the gate left for the later steps with a key")
                .isEmpty();
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
        env.put("PORELO_PUBLISH", "true");
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
                .contains(
                        "acceptance --os ubuntu --kind amd64 --package porelo-agent-agy --cucumber .");

        // Prevents a leg on a forge that publishes nothing testing what Artifactory held before.
        env.put("PORELO_PUBLISH", "false");
        final Run local = WorkflowSteps.run(steps.getFirst(), dir, env, WorkflowSteps.LIMIT);
        assertThat(local.exit())
                .as("exit of the leg on such a forge:%n%s", local.output())
                .isZero();
        assertThat(Files.readString(calls))
                .as("what the leg ran where nothing is published")
                .contains("--package porelo-agent-agy --candidate candidate --cucumber .");
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

    /**
     * Joins each command of a workflow: a line continued with a backslash, or folded onto the
     * more-indented lines after it, is one command. A step's whole block joins too, which can only
     * make a package build look as if it skipped its tests, never hide one that does.
     */
    private static List<String> commands(final List<String> lines) {
        final List<String> result = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            final int indent = indentOf(lines.get(i));
            final StringBuilder command = new StringBuilder(strip(lines.get(i)));
            i++;
            while (i < lines.size()
                    && !lines.get(i).isBlank()
                    && !lines.get(i).strip().startsWith("#")
                    && !lines.get(i).strip().startsWith("- ")
                    && (lines.get(i - 1).endsWith("\\") || indentOf(lines.get(i)) > indent)) {
                command.append(' ').append(strip(lines.get(i)));
                i++;
            }
            result.add(command.toString().strip());
        }
        return result;
    }

    private static String strip(final String line) {
        final String text = line.strip();
        return text.endsWith("\\") ? text.substring(0, text.length() - 1).strip() : text;
    }

    private static int indentOf(final String line) {
        return line.length() - line.stripLeading().length();
    }

    @Test
    void theAcceptanceLegsRunOnEveryForge() throws IOException {

        // On the host's forge a leg takes a machine of its kind from the host's pool, told where by
        // the forge; a condition naming GitHub's server would leave the host's forge untested until
        // the gathered push.
        final List<String> lines =
                Files.readAllLines(
                        RepositoryDocuments.root().resolve(".github/workflows/build.yml"));
        final int start = lines.indexOf("    acceptance:");
        assertThat(start).as("the acceptance job in build.yml").isNotNegative();
        int end = start + 1;
        while (end < lines.size() && !lines.get(end).matches("^ {4}[a-z].*:$")) {
            end++;
        }
        assertThat(lines.subList(start, end))
                .as("the acceptance job names no forge's server")
                .noneMatch(line -> line.contains("github.server_url"));
        assertThat(acceptanceStep(lines, "Acceptance on ${{ matrix.os }}"))
                .as("the leg is given the pool's run key, empty where there is no pool")
                .anyMatch(
                        line ->
                                line.trim()
                                        .equals("PORELO_POOL_SSH: ${{ secrets.PORELO_POOL_SSH }}"));

        // GitHub's artifact actions refuse every other server, so the packages travel through the
        // cache, saved and restored under the condition the leg passes --candidate on.
        final String key = "key: candidate-${{ github.run_id }}-${{ github.run_attempt }}";
        final String when = "if: github.ref_type == 'tag' || vars.PORELO_PUBLISH != 'true'";
        assertThat(lines)
                .as("the build job saves the packages under the key the legs restore")
                .filteredOn(line -> line.trim().equals(key))
                .hasSize(2);
        assertThat(acceptanceStep(lines, "The packages this run built"))
                .map(String::trim)
                .contains(
                        when,
                        key,
                        "restore-keys: candidate-${{ github.run_id }}-",
                        "fail-on-cache-miss: true")
                .noneMatch(line -> line.contains("download-artifact"));
        assertThat(acceptanceStep(lines, "Acceptance on ${{ matrix.os }}"))
                .as("the leg passes --candidate where the packages were restored")
                .anyMatch(
                        line ->
                                line.contains(
                                        "if [ \"$GITHUB_REF_TYPE\" = tag ] || [ \"$PORELO_PUBLISH\" != true ]; then FROM=\"--package porelo-agent-agy --candidate candidate\""));
    }

    /** The lines of the named step, up to the next step or the next job. */
    private static List<String> acceptanceStep(final List<String> lines, final String name) {
        final int start = lines.indexOf("            - name: " + name);
        assertThat(start).as("the step %s", name).isNotNegative();
        int end = start + 1;
        while (end < lines.size()
                && !lines.get(end).startsWith("            - ")
                && !lines.get(end).matches("^ {0,8}\\S.*")) {
            end++;
        }
        return lines.subList(start, end);
    }
}
