package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fuin.porelo.agent.impl.agy.AgyStreamFixtures.recorded;

import java.io.IOException;
import java.util.List;
import org.fuin.porelo.agent.api.AgentEnd;
import org.fuin.porelo.wire.Json;
import org.junit.jupiter.api.Test;

/**
 * Guards headless JSON formatting and turn-end detection against terminal control injection and
 * silent provider or agent failures.
 */
class AgyJsonFormatterTest {

    private final AgyJsonFormatter formatter = new AgyJsonFormatter();

    @Test
    void theRecordedStreamEndsSuccessfullyAndNamesTheSession() throws IOException {
        final String line = recorded("result");

        assertThat(formatter.format(line))
                .as("the final response is read from the nested result")
                .isEqualTo("1. **Maven build:** Background build process started.");
        assertThat(new AgyAgent().ended(line))
                .as("the actual result shape must finish an unattended turn")
                .isEqualTo(new AgentEnd(true, "", AgentEnd.AGENT, null));
        assertThat(new AgyAgent().definition().sessionIds().of(List.of(Json.parse(line))))
                .as("resume must still discover the conversation from a successful stream result")
                .isEqualTo("0b5d5a8d-336c-43dc-b4d5-b8543aeb2892");
    }

    @Test
    void recordedStepDeltasAreReadableButDoNotEndTheTurn() throws IOException {
        final String line = recorded("response");

        assertThat(formatter.format(line))
                .as("text_delta holds the response text in real step updates")
                .isEqualTo("1.");
        assertThat(new AgyAgent().ended(line))
                .as("a step update cannot finish the entire run")
                .isNull();
        assertThat(formatter.format(recorded("read-end")))
                .as("tool output is not promoted to response text")
                .isNull();
    }

    @Test
    void aNestedErrorResultStillReportsTheProviderFailure() throws IOException {
        final String line =
                recorded("result")
                        .replace(
                                "\"status\":\"SUCCESS\"",
                                "\"status\":\"ERROR\",\"error_code\":429,\"error\":\"Quota exhausted\"");

        assertThat(formatter.format(line))
                .as("a nested error is displayed rather than a stale success response")
                .isEqualTo("Error: Quota exhausted");
        assertThat(new AgyAgent().ended(line))
                .as("the nested provider code survives end detection")
                .isEqualTo(new AgentEnd(false, "Quota exhausted", AgentEnd.PROVIDER, 429));
    }

    @Test
    void formatsCompletedJsonRecordAndReportsSuccessfulTurnEnd() {
        final String line =
                "{\"conversation_id\":\"11111111-2222-3333-4444-555555555555\","
                        + "\"status\":\"SUCCESS\",\"response\":\"PORELOLIVE\"}";

        assertThat(formatter.format(line))
                .as("human-readable response text is extracted from the json record")
                .isEqualTo("PORELOLIVE");
        assertThat(AgyJsonFormatter.ended(line))
                .as("SUCCESS record marks a clean turn end")
                .isEqualTo(new AgentEnd(true, "", AgentEnd.AGENT, null));
    }

    @Test
    void formatsErrorJsonRecordAndReportsProviderFailureWhenHttpStatusIsPresent() {
        final String line =
                "{\"conversation_id\":\"11111111-2222-3333-4444-555555555555\","
                        + "\"status\":\"ERROR\","
                        + "\"error\":\"Error 401, Message: API key not valid.\"}";

        assertThat(formatter.format(line))
                .as("error line is formatted with Error prefix")
                .isEqualTo("Error: Error 401, Message: API key not valid.");
        assertThat(AgyJsonFormatter.ended(line))
                .as("HTTP 401 in error message is reported as a provider failure")
                .isEqualTo(
                        new AgentEnd(
                                false,
                                "Error 401, Message: API key not valid.",
                                AgentEnd.PROVIDER,
                                401));
    }

    @Test
    void formatsAgyErrorPrefixLineAndExtractsStructuredErrorCode() {
        final String line =
                "AGY_ERROR: {\"error\":\"Authentication required\","
                        + "\"message\":\"Please set GEMINI_API_KEY\",\"error_code\":403}";

        assertThat(formatter.format(line))
                .as("AGY_ERROR line is formatted with Error prefix")
                .isEqualTo("Error: Please set GEMINI_API_KEY");
        assertThat(AgyJsonFormatter.ended(line))
                .as("AGY_ERROR line reports a failed turn end with its HTTP status code")
                .isEqualTo(
                        new AgentEnd(false, "Please set GEMINI_API_KEY", AgentEnd.PROVIDER, 403));
    }

    @Test
    void formatsStreamJsonEventsAndSanitizesControlCharacters() {
        final String step =
                "{\"event\":\"step_update\",\"status\":\"DONE\","
                        + "\"content\":\"Answer\u001b[31mok\u0007\"}";
        assertThat(formatter.format(step))
                .as("escape sequences and bell characters are stripped from streamed content")
                .isEqualTo("Answer[31mok");
        assertThat(AgyJsonFormatter.ended(step))
                .as("an intermediate step_update does not end the turn")
                .isNull();

        final String result =
                "{\"event\":\"result\",\"status\":\"ERROR\",\"error\":\"Turn timed\u001b[2J out\"}";
        assertThat(AgyJsonFormatter.ended(result))
                .as("streamed result with ERROR status ends the turn as an agent failure")
                .isEqualTo(new AgentEnd(false, "Turn timed[2J out", AgentEnd.AGENT, null));
    }

    @Test
    void ignoresBlankOrMalformedLinesAndTruncatesLongMessages() {
        assertThat(formatter.format("")).as("blank line produces no output").isNull();
        assertThat(formatter.format("not json")).as("non-JSON line is ignored").isNull();
        assertThat(AgyJsonFormatter.ended("{malformed"))
                .as("malformed JSON does not end a turn")
                .isNull();

        final String repeated = "x".repeat(350);
        final String line = "{\"status\":\"SUCCESS\",\"response\":\"" + repeated + "\"}";
        final String formatted = formatter.format(line);
        assertThat(formatted)
                .as("overlong output is bounded to 300 characters plus ellipsis")
                .isNotNull()
                .hasSize(303)
                .endsWith("...");
    }
}
