package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.fuin.porelo.agent.api.Waiting;
import org.fuin.porelo.wire.Json;
import org.junit.jupiter.api.Test;

/**
 * What a finished unattended run said: the harm is a reader whose findings stand in its answer and
 * whose run is judged on an empty one.
 *
 * <p>The records are a whole run of Antigravity CLI 1.4.5, pinned when it was recorded unattended
 * in a Porelo task on 2026-11-12, as the agent wrote them; only the list of tools in its first
 * record is cut short. The run wrote a sentence, ran {@code ls -a}, and replied {@code ALL DONE}.
 */
class LastMessageDeclarationTest {

    private static final Path RUN =
            Path.of("src/test/resources/streams/unattended-run-1.4.5.jsonl");

    private static List<Object> records(final Path file) throws IOException {
        return Files.readAllLines(file).stream()
                .filter(line -> !line.isBlank())
                .map(line -> (Object) Json.parse(line))
                .toList();
    }

    private static Waiting.LastMessage lastMessage() {
        final Waiting waiting = new AgyAgent().definition().waiting();
        assertThat(waiting).as("agy.yaml declares session.waiting").isNotNull();
        final Waiting.LastMessage last = waiting.lastMessage();
        assertThat(last).as("agy.yaml declares session.waiting.last_message").isNotNull();
        return last;
    }

    @Test
    void answersWhatTheRunReplied() throws IOException {

        // The CLI streams each reply in pieces, and the piece that closes a step may be a bare
        // newline (the fifth record); the last reply's closing piece holds it whole here only
        // because it is short. Whole text stands for sure only in the result record.
        assertThat(lastMessage().of(records(RUN)))
                .as("the run's reply, never a tool's output or a streamed piece")
                .isEqualTo("I will look at the directory.\nALL DONE\n");
    }

    @Test
    void aRunThatEndedWithoutAResultAnswersNothing() throws IOException {

        // A run cut off before its result has no answer to give, which is not an empty one.
        final List<Object> cut = records(RUN);

        assertThat(lastMessage().of(cut.subList(0, cut.size() - 1))).isNull();
    }

    @Test
    void aFailedRunAnswersNothing() throws IOException {

        // A failed run's result holds an error, not a reply; read as one, a reader's empty or
        // broken run would pass for a finished one. The status and the empty response are as
        // Antigravity CLI 1.5.7 wrote them for a refused run on 2026-11-12; the recording is of
        // 1.4.5, whose failed result was not seen.
        final List<String> lines = Files.readAllLines(RUN);
        final String result = lines.getLast();
        assertThat(result).as("the recorded result").contains("\"status\":\"SUCCESS\"");
        final List<Object> failed = new ArrayList<>(records(RUN));
        failed.set(
                failed.size() - 1,
                Json.parse(
                        result.replace("\"status\":\"SUCCESS\"", "\"status\":\"ERROR\"")
                                .replace(
                                        "\"response\":\"I will look at the directory.\\nALL DONE\\n\"",
                                        "\"response\":\"\",\"error\":\"refused\"")));

        assertThat(lastMessage().of(failed)).isNull();
    }

    @Test
    void theDeclarationAddsNoScreenRule() {

        // Porelo wakes an agent by its screen rules; the answer of a finished run must not change
        // which screens read as waiting.
        assertThat(new AgyAgent().definition().waiting().screen())
                .containsExactly(
                        new Waiting.Rule("enter Confirm · esc Exit", 5, "workspace trust"),
                        new Waiting.Rule("enter Select · esc Cancel", 5, "a selection"));
    }
}
