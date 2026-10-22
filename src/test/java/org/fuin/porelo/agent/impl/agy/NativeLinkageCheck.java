package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Whether the native binary needs no more than its packages declare.
 *
 * <p>The binary is linked dynamically, and neither jdeb nor the rpm plugin reads it to find out
 * against what - {@code dpkg-shlibdeps} and {@code rpmbuild} would, and neither runs here. So the
 * dependencies are written by hand in {@code pom.xml}, and this holds them to the binary: a library
 * nobody declared, or a symbol version newer than the floor, fails the build before anything is
 * packed.
 *
 * <p>Not a unit test: the binary exists only after native-image, which runs at {@code package}. The
 * {@code dist} profile runs this class through failsafe, by name, so neither the unit tests nor the
 * acceptance suite pick it up.
 */
class NativeLinkageCheck {

    /** Every library a package declares, with the prefix of its symbol versions. */
    static final Map<String, String> DECLARED = Map.of("libc.so.6", "GLIBC", "libz.so.1", "ZLIB");

    private static final Pattern NEEDED =
            Pattern.compile("\\(NEEDED\\)\\s+Shared library: \\[([^\\]]+)\\]");

    private static final Pattern VERSION =
            Pattern.compile("Name: ([A-Z]+)_([0-9]+(?:\\.[0-9]+)*)\\b");

    /** What native-image prints when a CPU lacks a feature the image was built for. */
    private static final Pattern REQUIRED =
            Pattern.compile("required by the image: \\[([A-Z0-9_, ]+)\\]");

    /**
     * The x86-64 baseline, and the whole of what the binary may need: every native image runs on
     * any x86-64 CPU. native-image's default is v3, and a pre-Haswell CPU or a VM with a
     * conservative CPU model would install the package and then not start it.
     */
    static final Set<String> BASELINE = Set.of("CMOV", "CX8", "FXSR", "MMX", "SSE", "SSE2");

    @Test
    void theBinaryNeedsNoMoreThanItsPackagesDeclare() throws IOException, InterruptedException {

        final Path binary = Path.of(System.getProperty("porelo.native.binary", ""));
        assertThat(Files.isRegularFile(binary)).as("%s - built by -Pnative", binary).isTrue();

        assertThat(
                        problems(
                                readelf(binary),
                                Map.of(
                                        "GLIBC", System.getProperty("package.glibc.floor"),
                                        "ZLIB", System.getProperty("package.zlib.floor"))))
                .isEmpty();
    }

    @Test
    void theBinaryStartsOnAnyX8664Cpu() throws IOException {

        final Path binary = Path.of(System.getProperty("porelo.native.binary", ""));
        assertThat(Files.isRegularFile(binary)).as("%s - built by -Pnative", binary).isTrue();

        assertThat(cpuProblems(new String(Files.readAllBytes(binary), StandardCharsets.ISO_8859_1)))
                .isEmpty();
    }

    /**
     * Returns the CPU features the binary requires beyond the x86-64 baseline.
     *
     * @param binary The binary's bytes, as text.
     * @return One sentence per feature; empty when any x86-64 CPU runs it.
     */
    static List<String> cpuProblems(final String binary) {
        final Matcher required = REQUIRED.matcher(binary);
        if (!required.find()) {
            return List.of(
                    "the binary names no CPU requirement - native-image's message was not found");
        }
        final List<String> problems = new ArrayList<>();
        for (final String feature : required.group(1).split(",\\s*")) {
            if (!BASELINE.contains(feature.strip())) {
                problems.add(
                        feature.strip()
                                + " is required, so a CPU without it cannot start the agent");
            }
        }
        return problems;
    }

    /**
     * Returns every way the binary needs more than the packages promise.
     *
     * @param readelf Output of {@code readelf -d -V -W} in the C locale.
     * @param floors Highest symbol version the packages promise, by prefix.
     * @return Problems, empty when the packages cover the binary.
     */
    static List<String> problems(final String readelf, final Map<String, String> floors) {

        final List<String> problems = new ArrayList<>();
        final Set<String> needed = new LinkedHashSet<>();
        final Matcher lib = NEEDED.matcher(readelf);
        while (lib.find()) {
            needed.add(lib.group(1));
        }
        if (needed.isEmpty()) {
            // A static binary would need no declaration, but readelf answering nothing looks the
            // same, and the second is far likelier here.
            return List.of("readelf names no shared library - nothing to check against");
        }
        for (final String name : needed) {
            if (!DECLARED.containsKey(name)) {
                problems.add(name + " is needed and no package declares it");
            }
        }

        final Map<String, String> highest = new LinkedHashMap<>();
        final Matcher version = VERSION.matcher(readelf);
        while (version.find()) {
            highest.merge(version.group(1), version.group(2), (a, b) -> compare(a, b) >= 0 ? a : b);
        }
        for (final Map.Entry<String, String> floor : floors.entrySet()) {
            final String max = highest.get(floor.getKey());
            if (max != null && compare(max, floor.getValue()) > 0) {
                problems.add(
                        floor.getKey()
                                + "_"
                                + max
                                + " is needed and the packages promise only "
                                + floor.getValue());
            }
        }
        return problems;
    }

    /**
     * Compares two dotted versions numerically, so 2.34 is above 2.4 and 2.3.4.
     *
     * @param a One version.
     * @param b The other.
     * @return Negative, zero or positive, as {@link Comparable}.
     */
    static int compare(final String a, final String b) {
        final String[] left = a.split("\\.");
        final String[] right = b.split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            final int l = i < left.length ? Integer.parseInt(left[i]) : 0;
            final int r = i < right.length ? Integer.parseInt(right[i]) : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static String readelf(final Path binary) throws IOException, InterruptedException {
        final ProcessBuilder builder =
                new ProcessBuilder("readelf", "-d", "-V", "-W", binary.toString())
                        .redirectErrorStream(true);
        // The labels are translated otherwise, and the patterns above are the English ones.
        builder.environment().put("LC_ALL", "C");
        // To a file rather than a pipe: reading a pipe to its end would wait out a hang, not the
        // timeout.
        final Path out = Files.createTempFile("readelf", ".txt");
        try {
            final Process process = builder.redirectOutput(out.toFile()).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("readelf did not finish within 30 seconds");
            }
            final String output = Files.readString(out, StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as(output).isZero();
            return output;
        } finally {
            Files.deleteIfExists(out);
        }
    }
}
