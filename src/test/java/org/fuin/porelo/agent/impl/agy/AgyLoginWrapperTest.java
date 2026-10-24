package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Guards login-only client-secret collection against writing renewal material in task profiles. */
class AgyLoginWrapperTest {
    private static final String FIRST_ID =
            "0".repeat(12) + "-" + "a".repeat(32) + ".apps.google" + "usercontent.com";
    private static final String SECOND_ID =
            "1".repeat(12) + "-" + "b".repeat(32) + ".apps.google" + "usercontent.com";
    private static final String FIRST_SECRET = "GOC" + "SPX-" + "1".repeat(28);
    private static final String SECOND_SECRET = "GOC" + "SPX-" + "2".repeat(28);
    @TempDir Path directory;

    @Test
    void aLoginCollectsAnOwnerOnlySecretAndPreservesCliArguments() throws Exception {
        final Path wrapper = installWrapper();
        run(wrapper, "", "");
        final Path clientId = directory.resolve("agent/.gemini/antigravity-cli/.client-id");
        final Path secret = directory.resolve("agent/.gemini/antigravity-cli/.client-secret");
        assertThat(Files.readString(clientId))
                .as("the login wrapper collects only the first synthetic client ID")
                .isEqualTo(FIRST_ID + "\n");
        assertThat(Files.getPosixFilePermissions(clientId))
                .as("collected client ID is readable and writable by its owner only")
                .isEqualTo(PosixFilePermissions.fromString("rw-------"));
        assertThat(Files.readString(secret))
                .as("the login wrapper collects only the first synthetic client secret")
                .isEqualTo(FIRST_SECRET + "\n");
        assertThat(Files.getPosixFilePermissions(secret))
                .as("login collection is readable and writable by its owner only")
                .isEqualTo(PosixFilePermissions.fromString("rw-------"));
        assertThat(Files.getPosixFilePermissions(wrapper))
                .as("the installed wrapper is executable with mode 0755")
                .isEqualTo(PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    @Test
    void eitherBrokerUrlPreventsWritingAClientSecret() throws Exception {
        final Path wrapper = installWrapper();
        run(wrapper, "http://broker", "");
        assertThat(directory.resolve("agent/.gemini"))
                .as("Cloud Code tasks do not create a login profile")
                .doesNotExist();
        run(wrapper, "", "http://broker");
        assertThat(directory.resolve("agent/.gemini"))
                .as("Gemini API-key tasks do not create a login profile")
                .doesNotExist();
    }

    @Test
    void theTaskRuntimePreventsCollectionEvenWithoutBrokerVariables() throws Exception {
        final Path wrapper = installWrapper();
        Files.createDirectory(directory.resolve("runtime"));
        run(wrapper, "", "");
        assertThat(directory.resolve("agent/.gemini"))
                .as("a task runtime never collects the installed client's secret")
                .doesNotExist();
    }

    @Test
    void taskStartupLeavesAnExistingCollectedSecretUntouched() throws Exception {
        final Path wrapper = installWrapper();
        final Path config =
                Files.createDirectories(directory.resolve("agent/.gemini/antigravity-cli"));
        final Path secret =
                Files.writeString(config.resolve(".client-secret"), "synthetic-existing-marker");
        run(wrapper, "http://broker", "");
        assertThat(Files.readString(secret))
                .as("task startup cannot truncate an existing profile secret")
                .isEqualTo("synthetic-existing-marker");
    }

    @Test
    void loginReplacesAPreexistingSymlinkWithoutOverwritingItsTarget() throws Exception {
        final Path wrapper = installWrapper();
        final Path config =
                Files.createDirectories(directory.resolve("agent/.gemini/antigravity-cli"));
        final Path target = Files.writeString(directory.resolve("outside-target"), "untouched");
        final Path secret = config.resolve(".client-secret");
        Files.createSymbolicLink(secret, target);
        run(wrapper, "", "");
        assertThat(Files.isSymbolicLink(secret))
                .as("login collection must not write through a preexisting symlink")
                .isFalse();
        assertThat(Files.readString(target))
                .as("the symlink target remains untouched")
                .isEqualTo("untouched");
        assertThat(Files.getPosixFilePermissions(secret))
                .as("the replacement file is owner-only")
                .isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }

    @Test
    void loginFailsClosedWhenTheBinaryHasNoClientSecret() throws Exception {
        final Path wrapper = installWrapper();
        final Path cli = directory.resolve("agent/.local/bin/antigravity");
        Files.writeString(cli, "#!/bin/sh\nprintf 'ran-without-secret\\n'\n");
        Files.setPosixFilePermissions(cli, PosixFilePermissions.fromString("rwxr-xr-x"));
        final ProcessBuilder builder =
                new ProcessBuilder(wrapper.toString()).redirectErrorStream(true);
        final Process process = builder.start();
        try {
            assertThat(process.waitFor(5, TimeUnit.SECONDS))
                    .as("the wrapper must terminate promptly on missing secret")
                    .isTrue();
            final String output =
                    new String(
                            process.getInputStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
            assertThat(process.exitValue())
                    .as("missing client secret must stop the login wrapper")
                    .isNotZero();
            assertThat(output)
                    .as("the CLI must not be executed when secret collection fails")
                    .doesNotContain("ran-without-secret");
        } finally {
            process.destroyForcibly();
            process.waitFor();
        }
    }

    private Path installWrapper() throws Exception {
        final Path bin = Files.createDirectories(directory.resolve("agent/.local/bin"));
        final Path cli =
                Files.writeString(
                        bin.resolve("antigravity"),
                        "#!/bin/sh\n# "
                                + FIRST_ID
                                + "\n# "
                                + SECOND_ID
                                + "\n# "
                                + FIRST_SECRET
                                + "\n# "
                                + SECOND_SECRET
                                + "\nprintf '%s\\n' \"$@\"\n");
        Files.setPosixFilePermissions(cli, PosixFilePermissions.fromString("rwxr-xr-x"));
        final String install =
                new AgyAgent()
                        .imageLayer().asAgent().stream()
                                .filter(line -> line.contains("printf '%s\\n' '#!/bin/sh'"))
                                .findFirst()
                                .orElseThrow();
        final int start = install.indexOf("printf '%s\\n' '#!/bin/sh'");
        final int end = install.indexOf("chmod 0755 /home/agent/.local/bin/agy;");
        final String command =
                install.substring(start, end + "chmod 0755 /home/agent/.local/bin/agy;".length())
                        .replace("/home/agent", directory.resolve("agent").toString())
                        .replace("/run/porelo", directory.resolve("runtime").toString());
        final Process process =
                new ProcessBuilder("/bin/sh", "-c", command).redirectErrorStream(true).start();
        awaitSuccess(process);
        return bin.resolve("agy");
    }

    private void run(Path wrapper, String cloud, String gemini) throws Exception {
        final ProcessBuilder builder =
                new ProcessBuilder(wrapper.toString(), "argument with spaces", "--synthetic-flag")
                        .redirectErrorStream(true);
        builder.environment().put("CLOUD_CODE_URL", cloud);
        builder.environment().put("GOOGLE_GEMINI_BASE_URL", gemini);
        final Process process = builder.start();
        final String output = awaitSuccess(process);
        assertThat(output)
                .as("the wrapper executes the CLI with the original argument boundaries")
                .isEqualTo("argument with spaces\n--synthetic-flag\n");
    }

    private static String awaitSuccess(Process process) throws InterruptedException, IOException {
        try {
            assertThat(process.waitFor(5, TimeUnit.SECONDS))
                    .as("the synthetic wrapper must not hang")
                    .isTrue();
            final String output =
                    new String(
                            process.getInputStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
            assertThat(process.exitValue())
                    .as("the synthetic wrapper succeeds: %s", output)
                    .isZero();
            return output;
        } finally {
            process.destroyForcibly();
            process.waitFor();
        }
    }
}
