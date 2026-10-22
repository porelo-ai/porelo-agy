package org.fuin.porelo.agent.impl.agy;

import java.nio.file.Path;

/** Where the guards that read the repository's own files find them. */
final class RepositoryDocuments {

    private RepositoryDocuments() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns the repository root, which is where a test runs.
     *
     * @return Absolute path.
     */
    static Path root() {
        return Path.of("").toAbsolutePath();
    }
}
