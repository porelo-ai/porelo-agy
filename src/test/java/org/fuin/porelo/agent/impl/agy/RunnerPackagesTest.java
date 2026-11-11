package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the action that takes the runner's packages from the Actions cache, run as its steps
 * run, against stand-ins for apt and sudo.
 */
class RunnerPackagesTest {

    private static final String GCC = "gcc_4%3a15.2.0-1ubuntu1_amd64.deb";

    private static final String RPM = "rpm_6.0.1-1build1_amd64.deb";

    private static final byte[] GCC_DEB = "the gcc package".getBytes(StandardCharsets.UTF_8);

    /** The workflows that install the runner's packages, each by the action. */
    private static final List<String> INSTALLING = List.of("build.yml");

    private static final byte[] RPM_DEB = "the rpm package".getBytes(StandardCharsets.UTF_8);

    @Test
    void theManifestNamesEachPackageItInstallsWithTheHashAptNames(@TempDir final Path dir)
            throws Exception {

        final Path bin = dir.resolve("bin");
        stubApt(bin, "SHA512:" + sha512(GCC_DEB), "SHA512:" + sha512(RPM_DEB), GCC);

        final WorkflowSteps.Run run = steps(dir, bin, "runner_apt_resolve m gcc rpm\ncat m\n");

        assertThat(run.exit()).as("resolving: %s", run.output()).isZero();
        assertThat(run.output())
                .as("the manifest, one line a package, its pin carrying arch and version")
                .contains(
                        "gcc:amd64=4:15.2.0-1ubuntu1 " + GCC + " 15 SHA512:" + sha512(GCC_DEB),
                        "rpm:amd64=6.0.1-1build1 " + RPM + " 15 SHA512:" + sha512(RPM_DEB));
        assertThat(Files.readString(dir.resolve("apt.log")))
                .as("what apt was asked")
                .contains("update -qq");
        assertThat(Files.readString(dir.resolve("simulate.log")))
                .as("what apt simulated")
                .isEqualTo("install -s -qq --no-install-recommends gcc rpm\n");
    }

    @Test
    void anInstallLineThatCannotBeReadIsRefused(@TempDir final Path dir) throws Exception {

        final Path bin = dir.resolve("bin");
        stubApt(bin, "SHA512:" + sha512(GCC_DEB), "SHA512:" + sha512(RPM_DEB), GCC);

        final WorkflowSteps.Run run =
                steps(
                        dir,
                        bin,
                        "export SIMULATED='Inst gcc something else'\nrunner_apt_resolve m gcc\n");

        assertThat(run.exit()).as("resolving an unread line: %s", run.output()).isNotZero();
        assertThat(run.output())
                .as("what the step says")
                .contains("apt simulates an install this cannot read: 'Inst gcc something else'");
    }

    @Test
    void aSimulationThatFailsFailsTheManifest(@TempDir final Path dir) throws Exception {

        final Path bin = dir.resolve("bin");
        stubApt(bin, "SHA512:" + sha512(GCC_DEB), "SHA512:" + sha512(RPM_DEB), GCC);

        final WorkflowSteps.Run run =
                steps(dir, bin, "export SIMULATION_FAILS=1\nrunner_apt_resolve m no-such\n");

        assertThat(run.exit()).as("resolving an unknown package: %s", run.output()).isNotZero();
        assertThat(dir.resolve("m")).as("no manifest is written").doesNotExist();
    }

    @Test
    void aSimulationThatInstallsNothingFailsTheManifest(@TempDir final Path dir) throws Exception {

        final Path bin = dir.resolve("bin");
        stubApt(bin, "SHA512:" + sha512(GCC_DEB), "SHA512:" + sha512(RPM_DEB), GCC);

        final WorkflowSteps.Run run =
                steps(dir, bin, "export SIMULATED='Conf gcc'\nrunner_apt_resolve m gcc\n");

        assertThat(run.exit()).as("resolving to nothing: %s", run.output()).isNotZero();
        assertThat(run.output())
                .as("what the step says")
                .contains("apt installs nothing for gcc, which were found missing");
    }

    @Test
    void aPackageAptNamesOnlyAnMd5ForIsRefused(@TempDir final Path dir) throws Exception {

        final Path bin = dir.resolve("bin");
        stubApt(bin, "MD5Sum:878773614a047a1f1675f52f272fa67d", "SHA512:" + sha512(RPM_DEB), GCC);

        final WorkflowSteps.Run run = steps(dir, bin, "runner_apt_resolve m gcc rpm\n");

        assertThat(run.exit()).as("resolving with a weak hash: %s", run.output()).isNotZero();
        assertThat(run.output())
                .as("what the step says")
                .contains("apt names no SHA512 or SHA256 for " + GCC);
    }

    @Test
    void aFileNameThatIsAPathIsRefused(@TempDir final Path dir) throws Exception {

        final Path bin = dir.resolve("bin");
        stubApt(bin, "SHA512:" + sha512(GCC_DEB), "SHA512:" + sha512(RPM_DEB), "../" + GCC);

        final WorkflowSteps.Run run = steps(dir, bin, "runner_apt_resolve m gcc rpm\n");

        assertThat(run.exit()).as("resolving a path: %s", run.output()).isNotZero();
        assertThat(run.output())
                .as("what the step says")
                .contains("apt names a file '../" + GCC + "'");
    }

    @Test
    void aCachedPackageChangedByOneByteIsRefusedAndFetchedAgain(@TempDir final Path dir)
            throws Exception {

        final Path bin = dir.resolve("bin");
        final Path cache = dir.resolve("cache");
        stubApt(bin, "", "", GCC);
        Files.createDirectories(cache);
        Files.write(cache.resolve(GCC), GCC_DEB);
        final byte[] changed = RPM_DEB.clone();
        changed[0] ^= 1;
        Files.write(cache.resolve(RPM), changed);
        Files.writeString(cache.resolve("rpm_5.0.0-1_amd64.deb"), "an old pin's package");
        manifest(dir);

        final WorkflowSteps.Run run = steps(dir, bin, "runner_apt_fetch m cache\n");

        assertThat(run.exit()).as("fetching: %s", run.output()).isZero();
        assertThat(run.output())
                .as("what the step says")
                .contains(GCC + " taken from the cache, checked against its SHA512")
                .contains(RPM + " in the cache does not have the SHA512 apt names; refused");
        assertThat(Files.readAllBytes(cache.resolve(RPM)))
                .as("the package fetched again")
                .isEqualTo(RPM_DEB);
        try (Stream<Path> files = Files.list(cache)) {
            assertThat(files.map(f -> f.getFileName().toString()))
                    .as("what the entry keeps")
                    .containsExactlyInAnyOrder(GCC, RPM);
        }
        assertThat(Files.readAllLines(dir.resolve("apt.log")))
                .as("what was fetched")
                .containsExactly("download -qq rpm:amd64=6.0.1-1build1");
    }

    @Test
    void anUnchangedCacheReachesNoMirror(@TempDir final Path dir) throws Exception {

        final Path bin = dir.resolve("bin");
        final Path cache = dir.resolve("cache");
        stubApt(bin, "", "", GCC);
        Files.createDirectories(cache);
        Files.write(cache.resolve(GCC), GCC_DEB);
        Files.write(cache.resolve(RPM), RPM_DEB);
        manifest(dir);

        final WorkflowSteps.Run run = steps(dir, bin, "runner_apt_fetch m cache\n");

        assertThat(run.exit()).as("fetching: %s", run.output()).isZero();
        assertThat(dir.resolve("apt.log")).as("apt asked for nothing").doesNotExist();
    }

    @Test
    void aFetchThatDoesNotMatchEitherFails(@TempDir final Path dir) throws Exception {

        final Path bin = dir.resolve("bin");
        stubApt(bin, "", "", GCC);
        // The mirror serves something else than the metadata names.
        stub(
                bin,
                "apt-get",
                "echo \"$*\" >> \"$HOME/apt.log\"\nprintf 'else' > "
                        + GCC
                        + "\n"
                        + "printf 'else' > "
                        + RPM
                        + "\n");
        manifest(dir);

        final WorkflowSteps.Run run = steps(dir, bin, "runner_apt_fetch m cache\n");

        assertThat(run.exit()).as("fetching: %s", run.output()).isNotZero();
        assertThat(run.output())
                .as("what the step says")
                .contains(GCC + " does not have the SHA512 apt names");
        assertThat(dir.resolve("cache").resolve(GCC))
                .as("the file that does not match")
                .doesNotExist();
    }

    @Test
    void whatTheEntryHoldsBesideThePackagesIsRemoved(@TempDir final Path dir) throws Exception {

        final Path bin = dir.resolve("bin");
        final Path cache = dir.resolve("cache");
        final Path elsewhere = Files.createDirectories(dir.resolve("elsewhere"));
        Files.writeString(elsewhere.resolve("kept"), "not the cache's");
        stubApt(bin, "", "", GCC);
        Files.createDirectories(cache);
        Files.write(cache.resolve(GCC), GCC_DEB);
        Files.write(cache.resolve(RPM), RPM_DEB);
        Files.createSymbolicLink(cache.resolve("partial"), elsewhere);
        Files.writeString(cache.resolve("lock"), "");
        Files.createDirectories(cache.resolve("old").resolve("deeper"));
        manifest(dir);

        final WorkflowSteps.Run run = steps(dir, bin, "runner_apt_fetch m cache\n");

        assertThat(run.exit()).as("fetching: %s", run.output()).isZero();
        try (Stream<Path> files = Files.list(cache)) {
            assertThat(files.map(f -> f.getFileName().toString()))
                    .as("what the entry keeps")
                    .containsExactlyInAnyOrder(GCC, RPM);
        }
        assertThat(elsewhere.resolve("kept")).as("what the link pointed at").exists();
    }

    @Test
    void aCacheThatIsALinkIsReplacedByADirectory(@TempDir final Path dir) throws Exception {

        final Path bin = dir.resolve("bin");
        final Path elsewhere = Files.createDirectories(dir.resolve("elsewhere"));
        Files.writeString(elsewhere.resolve("kept"), "not the cache's");
        stubApt(bin, "", "", GCC);
        Files.createSymbolicLink(dir.resolve("cache"), elsewhere);
        manifest(dir);

        final WorkflowSteps.Run run = steps(dir, bin, "runner_apt_fetch m cache\n");

        assertThat(run.exit()).as("fetching: %s", run.output()).isZero();
        assertThat(dir.resolve("cache")).as("the cache").isDirectory().isNotEqualTo(elsewhere);
        assertThat(Files.isSymbolicLink(dir.resolve("cache"))).as("the cache is a link").isFalse();
        try (Stream<Path> files = Files.list(elsewhere)) {
            assertThat(files.map(f -> f.getFileName().toString()))
                    .as("what the link pointed at")
                    .containsExactly("kept");
        }
    }

    @Test
    void theInstallReadsTheCheckedFilesAndNoMirror(@TempDir final Path dir) throws Exception {

        final Path bin = dir.resolve("bin");
        final Path cache = Files.createDirectories(dir.resolve("cache"));
        stubApt(bin, "", "", GCC);
        Files.write(cache.resolve(GCC), GCC_DEB);
        Files.write(cache.resolve(RPM), RPM_DEB);
        manifest(dir);

        final WorkflowSteps.Run run = steps(dir, bin, "runner_apt_install m cache\n");

        assertThat(run.exit()).as("installing: %s", run.output()).isZero();
        assertThat(Files.readString(dir.resolve("apt.log")))
                .as("what apt was asked, the archive by its absolute path")
                .contains(
                        "install -y -qq --no-install-recommends --no-download -o"
                                + " Dir::Cache::archives="
                                + cache.toRealPath()
                                + "/ gcc:amd64=4:15.2.0-1ubuntu1"
                                + " rpm:amd64=6.0.1-1build1");
    }

    @Test
    void aFileChangedAfterTheFetchIsNotInstalled(@TempDir final Path dir) throws Exception {

        final Path bin = dir.resolve("bin");
        final Path cache = Files.createDirectories(dir.resolve("cache"));
        stubApt(bin, "", "", GCC);
        Files.write(cache.resolve(GCC), GCC_DEB);
        Files.write(cache.resolve(RPM), "changed".getBytes(StandardCharsets.UTF_8));
        manifest(dir);

        final WorkflowSteps.Run run = steps(dir, bin, "runner_apt_install m cache\n");

        assertThat(run.exit()).as("installing: %s", run.output()).isNotZero();
        assertThat(dir.resolve("apt.log")).as("apt asked for nothing").doesNotExist();
    }

    @Test
    void noWorkflowInstallsAPackageAroundTheCache() throws IOException {

        final List<String> using = new ArrayList<>();
        try (Stream<Path> files =
                Files.list(RepositoryDocuments.root().resolve(".github/workflows"))) {
            for (final Path workflow : files.sorted().toList()) {
                final String text = Files.readString(workflow);
                assertThat(text)
                        .as("%s installs packages only by the runner-apt action", workflow)
                        .doesNotContain("apt-get install")
                        .doesNotContain("apt install");
                if (text.contains("uses: ./.github/actions/runner-apt")) {
                    using.add(workflow.getFileName().toString());
                }
            }
        }
        assertThat(using).as("the workflows that install packages").isEqualTo(INSTALLING);
    }

    private static WorkflowSteps.Run steps(final Path dir, final Path bin, final String script)
            throws IOException, InterruptedException {
        final Path actions = RepositoryDocuments.root().resolve(".github/actions");
        return WorkflowSteps.run(
                "set -euo pipefail\n"
                        + ". '"
                        + actions.resolve("pinned-jdk/pinned.sh")
                        + "'\n. '"
                        + actions.resolve("runner-apt/runner-apt.sh")
                        + "'\n"
                        + script,
                dir,
                Map.of("PATH", pathWith(bin), "HOME", dir.toString()),
                WorkflowSteps.LIMIT);
    }

    /**
     * Stands in for apt-get and sudo: the metadata names gcc and rpm, and a download writes the
     * right package into the working directory where none is there yet, so a file left in place
     * stays as it is.
     */
    private static void stubApt(
            final Path bin, final String gccHash, final String rpmHash, final String gccFile)
            throws IOException {
        stub(bin, "sudo", "exec \"$@\"\n");
        stub(
                bin,
                "apt-get",
                String.join(
                        "\n",
                        List.of(
                                "case \"$1 $2\" in",
                                "'install -s')",
                                "  echo \"$*\" >> \"$HOME/simulate.log\"",
                                // A failing apt may have printed part of its plan.
                                "  [ -z \"${SIMULATION_FAILS:-}\" ] || { echo 'Inst gcc (1 x"
                                        + " [amd64])'; exit 100; }",
                                "  if [ -n \"${SIMULATED:-}\" ]; then echo \"$SIMULATED\"; exit 0; fi",
                                "  echo 'Inst gcc [4:13.2.0-7ubuntu1] (4:15.2.0-1ubuntu1"
                                        + " Ubuntu:26.04/resolute [amd64])'",
                                // apt adds the packages it holds unconfigured for now after an
                                // unpack.
                                "  echo 'Inst rpm (6.0.1-1build1 Ubuntu:26.04/resolute"
                                        + " [amd64]) []'",
                                "  echo 'Conf gcc (4:15.2.0-1ubuntu1 Ubuntu:26.04/resolute"
                                        + " [amd64])'",
                                "  exit 0 ;;",
                                "'download --print-uris')",
                                "  case $3 in",
                                "  gcc:*) echo \"'http://mirror/pool/gcc.deb' "
                                        + gccFile
                                        + " 15 "
                                        + gccHash
                                        + "\" ;;",
                                "  rpm:*) echo \"'http://mirror/pool/rpm.deb' "
                                        + RPM
                                        + " 15 "
                                        + rpmHash
                                        + "\" ;;",
                                "  esac",
                                "  exit 0 ;;",
                                "esac",
                                "echo \"$*\" >> \"$HOME/apt.log\"",
                                "for pin in \"$@\"; do",
                                "  case $pin in",
                                "  gcc:*) [ -e "
                                        + GCC
                                        + " ] || printf 'the gcc package' > "
                                        + GCC
                                        + " ;;",
                                "  rpm:*) [ -e "
                                        + RPM
                                        + " ] || printf 'the rpm package' > "
                                        + RPM
                                        + " ;;",
                                "  esac",
                                "done",
                                "")));
    }

    private static void manifest(final Path dir) throws Exception {
        Files.writeString(
                dir.resolve("m"),
                "gcc:amd64=4:15.2.0-1ubuntu1 "
                        + GCC
                        + " 15 SHA512:"
                        + sha512(GCC_DEB)
                        + "\nrpm:amd64=6.0.1-1build1 "
                        + RPM
                        + " 15 SHA512:"
                        + sha512(RPM_DEB)
                        + "\n");
    }

    private static void stub(final Path bin, final String name, final String body)
            throws IOException {
        Files.createDirectories(bin);
        final Path file = bin.resolve(name);
        Files.writeString(file, "#!/bin/bash\n" + body);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwx------"));
    }

    private static String pathWith(final Path bin) {
        return bin + ":" + System.getenv().getOrDefault("PATH", "/usr/bin:/bin");
    }

    private static String sha512(final byte[] content) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(content));
    }
}
