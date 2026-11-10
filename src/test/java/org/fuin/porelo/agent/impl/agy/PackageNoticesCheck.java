package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fuin.porelo.agent.impl.agy.PackageVersionCheck.newest;
import static org.fuin.porelo.agent.impl.agy.PackageVersionCheck.run;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Whether the packages the build made carry the license texts of what the executable compiles in
 * from outside Porelo, read from the packages.
 *
 * <p>A package that ships SnakeYAML, JSpecify or the GraalVM runtime without the texts their
 * licenses ask for is distributed against those licenses; a new dependency must not slip in without
 * its section. The file and the bill are read from the built deb, and the rpm's file is compared
 * with the deb's, so a mapping that drops or replaces the file in one package shows here.
 *
 * <p>Not a unit test: the packages exist only after jdeb and the rpm plugin, which run at {@code
 * verify}. The {@code dist} profile runs this class by name after both, beside {@link
 * PackageVersionCheck}.
 */
class PackageNoticesCheck {

    private static final String NAME = "porelo-agent-agy";

    private static final String FILE = "THIRD-PARTY-NOTICES";

    private static final String IN_DOC = "./usr/share/doc/" + NAME + "/" + FILE;

    private static final String IN_LICENSES = "/usr/share/licenses/" + NAME + "/" + FILE;

    private static final String BILL = "./usr/share/porelo/sbom/" + NAME + ".cdx.json";

    /** The rule the generator opens and closes a section's heading with. */
    private static final String RULE = "=".repeat(78);

    /** Porelo's own modules, whose licenses stand in {@code copyright} and the LICENSE files. */
    private static final String OWN_GROUP = "org.fuin.porelo";

    @Test
    void theDebCarriesASectionForEveryCompiledInComponent()
            throws IOException, InterruptedException {

        final String notices = fromTheDeb(IN_DOC);
        final List<String> components = thirdPartyComponents(fromTheDeb(BILL));

        assertThat(components)
                .as("the Maven components of the deb's bill that are not Porelo's own")
                .anyMatch(c -> c.startsWith("org.yaml:snakeyaml:"))
                .anyMatch(c -> c.startsWith("org.jspecify:jspecify:"));
        assertThat(sectionHeadings(notices))
                .as("the section headings of %s in the deb", FILE)
                .containsAll(components)
                .anyMatch(line -> line.startsWith("GraalVM runtime "));
    }

    @Test
    void theRpmCarriesTheDebsNoticesAsALicense() throws IOException, InterruptedException {

        final Path rpm = newest(NAME + "-", ".rpm");

        assertThat(run("rpm", "-qLp", rpm.toString()).lines().toList())
                .as("what %s lists as license files", rpm)
                .contains(IN_LICENSES);
        // By digest: the file is some 800 KB, too long to read in a failure.
        assertThat(sha256(fromTheRpm(rpm, "." + IN_LICENSES)))
                .as("the SHA-256 of %s in %s, against the deb's", IN_LICENSES, rpm)
                .isEqualTo(sha256(fromTheDeb(IN_DOC)));
    }

    private static String sha256(final String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JDK has SHA-256", e);
        }
    }

    /**
     * The heading of each section: a line between two of the generator's rules, or followed by its
     * {@code License:} line before the second. The license texts draw rules of their own.
     */
    private static List<String> sectionHeadings(final String notices) {
        final List<String> lines = notices.lines().toList();
        final List<String> headings = new ArrayList<>();
        for (int i = 1; i + 1 < lines.size(); i++) {
            final boolean closed =
                    RULE.equals(lines.get(i + 1))
                            || (lines.get(i + 1).startsWith("License: ")
                                    && i + 2 < lines.size()
                                    && RULE.equals(lines.get(i + 2)));
            if (RULE.equals(lines.get(i - 1)) && closed) {
                headings.add(lines.get(i));
            }
        }
        return headings;
    }

    /** A file as the newest deb installs it, by its exact path in the deb's data. */
    private static String fromTheDeb(final String path) throws IOException, InterruptedException {
        final Path deb = newest(NAME + "_", ".deb");
        final Path tar = Files.createTempFile("package-notices", ".tar");
        try {
            toFile(null, tar, "dpkg-deb", "--fsys-tarfile", deb.toString());
            return run("tar", "-xOf", tar.toString(), path);
        } finally {
            Files.deleteIfExists(tar);
        }
    }

    /** A file as an rpm installs it, by its exact path in the rpm's payload. */
    private static String fromTheRpm(final Path rpm, final String path)
            throws IOException, InterruptedException {
        final Path cpio = Files.createTempFile("package-notices", ".cpio");
        final Path out = Files.createTempFile("package-notices", ".out");
        try {
            toFile(null, cpio, "rpm2cpio", rpm.toString());
            toFile(cpio, out, "cpio", "-i", "--to-stdout", "--quiet", path);
            return Files.readString(out, StandardCharsets.UTF_8);
        } finally {
            Files.deleteIfExists(cpio);
            Files.deleteIfExists(out);
        }
    }

    /**
     * Runs a command whose output is binary or large into a file; what it says on stderr shows when
     * it fails.
     */
    private static void toFile(final @Nullable Path in, final Path out, final String... command)
            throws IOException, InterruptedException {
        final Path err = Files.createTempFile("package-notices", ".err");
        try {
            final ProcessBuilder builder =
                    new ProcessBuilder(command)
                            .redirectOutput(out.toFile())
                            .redirectError(err.toFile());
            if (in != null) {
                builder.redirectInput(in.toFile());
            }
            final Process process = builder.start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError(List.of(command) + " did not finish within 30 seconds");
            }
            assertThat(process.exitValue())
                    .as("%s: %s", List.of(command), Files.readString(err, StandardCharsets.UTF_8))
                    .isZero();
        } finally {
            Files.deleteIfExists(err);
        }
    }

    /** {@code group:name:version} of every Maven component of a bill not Porelo's own. */
    @SuppressWarnings("unchecked")
    private static List<String> thirdPartyComponents(final String bill) {
        final Map<String, Object> json = new Yaml().load(bill);
        return ((List<Map<String, Object>>) json.get("components"))
                .stream()
                        .filter(c -> String.valueOf(c.get("purl")).startsWith("pkg:maven/"))
                        .filter(c -> !OWN_GROUP.equals(c.get("group")))
                        .map(c -> c.get("group") + ":" + c.get("name") + ":" + c.get("version"))
                        .toList();
    }
}
