package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;

import org.fuin.porelo.agent.api.AgentEnd;
import org.junit.jupiter.api.Test;

/**
 * Guards headless JSON formatting and turn-end detection against terminal control injection and
 * silent provider or agent failures.
 */
class AgyJsonFormatterTest {

    private final AgyJsonFormatter formatter = new AgyJsonFormatter();

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
