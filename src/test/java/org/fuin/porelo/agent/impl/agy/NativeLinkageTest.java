package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NativeLinkageCheck}'s reading of {@code readelf}, on output taken from the real
 * binary and shortened.
 */
class NativeLinkageTest {

    private static final String BINARY =
            " 0x0000000000000001 (NEEDED)             Shared library: [libz.so.1]"
                    + "\n"
                    + " 0x0000000000000001 (NEEDED)             Shared library: [libc.so.6]"
                    + "\n"
                    + "Version needs section '.gnu.version_r' contains 2 entries:"
                    + "\n"
                    + "  000000: Version: 1  File: libz.so.1  Cnt: 2"
                    + "\n"
                    + "  0x0010:   Name: ZLIB_1.2.2  Flags: none  Version: 14"
                    + "\n"
                    + "  0x0020:   Name: ZLIB_1.2.0  Flags: none  Version: 7"
                    + "\n"
                    + "  0x0030: Version: 1  File: libc.so.6  Cnt: 4"
                    + "\n"
                    + "  0x0040:   Name: GLIBC_2.9  Flags: none  Version: 15"
                    + "\n"
                    + "  0x0050:   Name: GLIBC_2.3.4  Flags: none  Version: 9"
                    + "\n"
                    + "  0x0060:   Name: GLIBC_2.34  Flags: none  Version: 4"
                    + "\n"
                    + "  0x0070:   Name: GLIBC_2.2.5  Flags: none  Version: 2"
                    + "\n";

    private static final Map<String, String> FLOORS = Map.of("GLIBC", "2.34", "ZLIB", "1.2.2");

    @Test
    void acceptsTheBinaryAsItIsDeclared() {
        assertThat(NativeLinkageCheck.problems(BINARY, FLOORS)).isEmpty();
    }

    @Test
    void refusesASymbolVersionAboveTheFloor() {

        // The case a newer build host or a new dependency brings: installable, then not startable.
        assertThat(NativeLinkageCheck.problems(BINARY, Map.of("GLIBC", "2.33", "ZLIB", "1.2.2")))
                .containsExactly("GLIBC_2.34 is needed and the packages promise only 2.33");
    }

    @Test
    void refusesALibraryNoPackageDeclares() {
        assertThat(
                        NativeLinkageCheck.problems(
                                BINARY
                                        + " 0x0000000000000001 (NEEDED)             Shared library: [libstdc++.so.6]\n",
                                FLOORS))
                .containsExactly("libstdc++.so.6 is needed and no package declares it");
    }

    @Test
    void refusesOutputThatNamesNoLibrary() {

        // A readelf that failed quietly must not read as a binary that needs nothing.
        assertThat(NativeLinkageCheck.problems("", FLOORS)).hasSize(1);
    }

    @Test
    void comparesVersionsAsNumbersRatherThanText() {
        assertThat(NativeLinkageCheck.compare("2.34", "2.4")).isPositive();
        assertThat(NativeLinkageCheck.compare("2.3.4", "2.34")).isNegative();
        assertThat(NativeLinkageCheck.compare("2.34", "2.34.0")).isZero();
    }

    @Test
    void acceptsABinaryThatNeedsOnlyTheBaseline() {

        assertThat(
                        NativeLinkageCheck.cpuProblems(
                                "... required by the image: [CX8, CMOV, FXSR, MMX, SSE, SSE2]. Please rebuild ..."))
                .isEmpty();
    }

    @Test
    void refusesABinaryThatNeedsX8664V2() {

        // The exact set, not "no v3": a v2-only binary still fails on a CPU without SSE4.2 or
        // POPCNT.
        assertThat(
                        NativeLinkageCheck.cpuProblems(
                                "required by the image: [CX8, CMOV, FXSR, MMX, SSE, SSE2, SSE3, SSSE3, SSE4_1, SSE4_2, POPCNT]"))
                .hasSize(5)
                .anySatisfy(problem -> assertThat(problem).contains("POPCNT"));
    }
}
