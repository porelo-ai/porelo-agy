package org.fuin.porelo.agent.impl.agy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.fuin.porelo.wire.Json;

/** Reads shortened records from the pinned CLI's stream. */
final class AgyStreamFixtures {

    private AgyStreamFixtures() {}

    static String recorded(String name) throws IOException {
        final Map<?, ?> records =
                (Map<?, ?>)
                        Json.parse(
                                Files.readString(
                                        Path.of("src/test/resources/streams/agy-stream.json")));
        if (!(records.get(name) instanceof String line)) {
            throw new IllegalArgumentException("Unknown recorded line: " + name);
        }
        return line;
    }
}
