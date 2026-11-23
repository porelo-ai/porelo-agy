package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fuin.porelo.agent.impl.agy.PackageVersionCheck.newest;
import static org.fuin.porelo.agent.impl.agy.PackageVersionCheck.run;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.fuin.porelo.agent.api.AgentProtocol;
import org.junit.jupiter.api.Test;

/**
 * Whether the packages the build made depend on a Porelo whose lowest agent protocol this adapter
 * speaks, read from the packages.
 *
 * <p>Porelo's package provides {@code porelo-agent-protocol} at its lowest agent protocol and
 * accepts every agent at or above it, so this adapter, built against the API's {@code VERSION},
 * works beside any Porelo whose lowest lies from the API's {@code LOWEST} up to that {@code
 * VERSION}. A package without that range installs beside a Porelo that refuses it at the first
 * task. Run by the {@code dist} profile after jdeb and the rpm plugin, like {@link
 * PackageVersionCheck}.
 */
class PackageProtocolCheck {

    private static final String NAME = "porelo-agent-agy";

    private static final String PROTOCOL = "porelo-agent-protocol";

    @Test
    void theDebDependsOnTheProtocolRangeBesidePorelo() throws IOException, InterruptedException {

        final Path deb = newest(NAME + "_", ".deb");
        final List<String> depends =
                Arrays.stream(run("dpkg-deb", "-f", deb.toString(), "Depends").split(","))
                        .map(relation -> relation.strip().replaceAll("\\s+", " "))
                        .toList();

        assertThat(depends)
                .as("the Depends of %s", deb)
                .contains(
                        PROTOCOL + " (>= " + AgentProtocol.LOWEST + ")",
                        PROTOCOL + " (<= " + AgentProtocol.VERSION + ")");
        assertThat(depends)
                .as("porelo itself among the Depends of %s", deb)
                .anyMatch(relation -> relation.equals("porelo") || relation.startsWith("porelo ("));
        assertThat(depends.stream().filter(relation -> relation.startsWith(PROTOCOL)))
                .as("no other relation on %s in %s", PROTOCOL, deb)
                .hasSize(2);
    }

    @Test
    void theRpmRequiresTheProtocolRangeBesidePorelo() throws IOException, InterruptedException {

        final Path rpm = newest(NAME + "-", ".rpm");
        final List<String> requires =
                run("rpm", "-qp", "--requires", rpm.toString())
                        .lines()
                        .map(line -> line.strip().replaceAll("\\s+", " "))
                        .toList();

        assertThat(requires)
                .as("the requirements of %s", rpm)
                .contains(
                        PROTOCOL + " >= " + AgentProtocol.LOWEST,
                        PROTOCOL + " <= " + AgentProtocol.VERSION);
        assertThat(requires)
                .as("porelo itself among the requirements of %s", rpm)
                .anyMatch(line -> line.equals("porelo") || line.startsWith("porelo "));
        assertThat(requires.stream().filter(line -> line.startsWith(PROTOCOL)))
                .as("no other requirement on %s in %s", PROTOCOL, rpm)
                .hasSize(2);
    }
}
