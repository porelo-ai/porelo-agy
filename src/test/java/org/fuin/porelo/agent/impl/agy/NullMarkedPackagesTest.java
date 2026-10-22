package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

/**
 * Every package holding main code is {@code @NullMarked}.
 *
 * <p>NullAway runs with {@code OnlyNullMarked}, so an unmarked package is not an error - it is
 * skipped, in silence, and its nullness is a promise nothing checks. This test is what makes a
 * missing {@code package-info.java} fail the build instead.
 */
class NullMarkedPackagesTest {

    private static final Path MAIN = Path.of("src/main/java");

    @Test
    void everyMainPackageIsNullMarked() throws IOException {

        final Set<String> packages = new TreeSet<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            files.filter(file -> file.toString().endsWith(".java"))
                    .map(
                            file ->
                                    MAIN.relativize(file.getParent())
                                            .toString()
                                            .replace(file.getFileSystem().getSeparator(), "."))
                    .forEach(packages::add);
        }
        // A walk from the wrong root finds nothing, and nothing passes every check below.
        assertThat(packages).contains(AgyAgent.class.getPackageName());

        for (final String name : packages) {
            // Read from the compiled class, not the source text: a comment or a string that says
            // @NullMarked is not the annotation.
            assertThat(annotated(name)).as("package %s is @NullMarked", name).isTrue();
        }
    }

    private static boolean annotated(String packageName) {
        try {
            return Class.forName(packageName + ".package-info")
                    .isAnnotationPresent(NullMarked.class);
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
