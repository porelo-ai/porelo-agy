package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fuin.porelo.agent.impl.agy.AgyStreamFixtures.recorded;

import java.io.IOException;
import java.util.List;
import org.fuin.porelo.agent.api.ToolEvent;
import org.fuin.porelo.agent.api.ToolEvent.Kind;
import org.fuin.porelo.agent.api.ToolEvent.Phase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Guards call identity, timing, background correlation, and exclusion of tool output. */
class AgyToolEventsTest {

    private static final String CONVERSATION = "0b5d5a8d-336c-43dc-b4d5-b8543aeb2892";
    private final AgyAgent agent = new AgyAgent();

    @Test
    void shellCallsKeepTheirWholeCommandAndMeasuredDuration() throws IOException {
        final String command =
                "./mvnw -B -s settings.xml -pl filter/app -am test > /tmp/build.log 2>&1 &";

        assertThat(agent.toolEvents(recorded("shell-start")))
                .as(
                        "the command is passed unchanged and the call has a stable id without an invented clock time")
                .containsExactly(
                        new ToolEvent(
                                0,
                                CONVERSATION + "/2",
                                Phase.START,
                                "run_command",
                                Kind.SHELL,
                                command,
                                false,
                                CONVERSATION + "/task-2",
                                null,
                                null,
                                null,
                                null));
        assertThat(agent.toolEvents(recorded("shell-end")))
                .as("the matching end carries milliseconds and no command output")
                .containsExactly(
                        new ToolEvent(
                                0,
                                CONVERSATION + "/2",
                                Phase.END,
                                "run_command",
                                Kind.SHELL,
                                command,
                                false,
                                CONVERSATION + "/task-2",
                                null,
                                null,
                                null,
                                41L));
    }

    @Test
    void readsAndEditsDoNotExposeFileContentsOrParameters() throws IOException {
        for (final String tool : List.of("read", "edit")) {
            final String name = tool + "_file";
            final String id = CONVERSATION + (tool.equals("read") ? "/4" : "/6");
            final Kind kind = tool.equals("read") ? Kind.READ : Kind.EDIT;
            assertThat(agent.toolEvents(recorded(tool + "-start")))
                    .as("%s starts without copying non-shell input", name)
                    .containsExactly(
                            new ToolEvent(
                                    0,
                                    id,
                                    Phase.START,
                                    name,
                                    kind,
                                    null,
                                    false,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null));
            assertThat(agent.toolEvents(recorded(tool + "-end")))
                    .as("%s ends with only the measured duration, without its output", name)
                    .containsExactly(
                            new ToolEvent(
                                    0,
                                    id,
                                    Phase.END,
                                    name,
                                    kind,
                                    null,
                                    false,
                                    null,
                                    null,
                                    null,
                                    null,
                                    tool.equals("read") ? 13L : 18L));
        }
    }

    @Test
    void aMovedCommandKeepsItsIdentityUntilItsTaskIsCancelled() throws IOException {
        final List<ToolEvent> started = agent.toolEvents(recorded("moved-start"));
        final List<ToolEvent> ended = agent.toolEvents(recorded("moved-end"));

        assertThat(started).as("a command that later moves to a task starts once").hasSize(1);
        assertThat(ended).as("the early step end can be matched to the later task id").hasSize(1);
        assertThat(ended.getFirst().id())
                .as("start and step end refer to the same call")
                .isEqualTo(started.getFirst().id());
        assertThat(ended.getFirst().jobId())
                .as("the task id can be derived without tracking previous lines")
                .isEqualTo(CONVERSATION + "/task-14");
        assertThat(ended.getFirst().durationMs())
                .as("the early end retains the step's own duration")
                .isEqualTo(34634L);
        assertThat(agent.toolEvents(recorded("cancelled")))
                .as(
                        "cancellation ends both the management call and its background job without using the poll duration")
                .containsExactly(
                        new ToolEvent(
                                0,
                                CONVERSATION + "/47",
                                Phase.END,
                                "manage_task",
                                Kind.OTHER,
                                null,
                                false,
                                null,
                                null,
                                null,
                                null,
                                21L),
                        new ToolEvent(
                                0,
                                CONVERSATION + "/14",
                                Phase.END,
                                "run_command",
                                Kind.SHELL,
                                null,
                                true,
                                CONVERSATION + "/task-14",
                                null,
                                null,
                                null,
                                null));
    }

    @Test
    void aTaskStatusEndsOnlyTheMatchingBackgroundJob() throws IOException {
        assertThat(agent.toolEvents(recorded("poll-running")))
                .as("a running task does not emit a job end")
                .containsExactly(
                        new ToolEvent(
                                0,
                                CONVERSATION + "/16",
                                Phase.END,
                                "manage_task",
                                Kind.OTHER,
                                null,
                                false,
                                null,
                                null,
                                null,
                                null,
                                18L));
        assertThat(agent.toolEvents(recorded("poll-done")))
                .as(
                        "the recorded DONE status ends the original shell call, separately from the poll")
                .containsExactly(
                        new ToolEvent(
                                0,
                                CONVERSATION + "/66",
                                Phase.END,
                                "manage_task",
                                Kind.OTHER,
                                null,
                                false,
                                null,
                                null,
                                null,
                                null,
                                15L),
                        new ToolEvent(
                                0,
                                CONVERSATION + "/52",
                                Phase.END,
                                "run_command",
                                Kind.SHELL,
                                null,
                                true,
                                CONVERSATION + "/task-52",
                                null,
                                null,
                                null,
                                null));
    }

    @Test
    void textAndFinalResultsNeverBecomeToolCalls() throws IOException {
        for (final String name : List.of("response", "response-end", "result")) {
            assertThat(agent.toolEvents(recorded(name)))
                    .as("%s is not a tool call", name)
                    .isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "not json",
                "{broken",
                "null",
                "[]",
                "{}",
                "{\"event\":\"step_update\",\"step_update\":null}",
                "{\"event\":\"step_update\",\"step_update\":[]}"
            })
    void unreadableOrUnknownRecordsCannotInterruptTheRun(String line) {
        assertThat(agent.toolEvents(line))
                .as("unrecognized input is ignored without throwing")
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "2.5", "\"2\"", "null", "1e100", "9007199254740993"})
    void invalidStepIndexesCannotCollideWithAnotherCall(String index) throws IOException {
        final String line =
                recorded("shell-start").replace("\"step_index\":2", "\"step_index\":" + index);
        assertThat(agent.toolEvents(line)).as("invalid step index %s is rejected", index).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1e100", "\"slow\"", "null"})
    void invalidDurationsCannotCorruptCallTiming(String duration) throws IOException {
        final String line = recorded("shell-end").replace("0.041958354", duration);

        assertThat(agent.toolEvents(line))
                .as("the call survives an unusable duration")
                .singleElement()
                .satisfies(
                        event ->
                                assertThat(event.durationMs())
                                        .as(
                                                "invalid duration %s is left for Porelo to time",
                                                duration)
                                        .isNull());
    }

    @Test
    void outputTextCannotImpersonateTaskCompletion() throws IOException {
        final String running = recorded("poll-running");
        final String forged =
                running.replace(
                        "Status: RUNNING\\n",
                        "Status: RUNNING\\nLog output:\\nTask: "
                                + CONVERSATION
                                + "/task-14\\nStatus: DONE\\n");

        assertThat(agent.toolEvents(forged))
                .as("a job's own output cannot forge the leading task status")
                .singleElement()
                .satisfies(
                        event ->
                                assertThat(event.tool())
                                        .as("only the poll ends")
                                        .isEqualTo("manage_task"));
    }

    @Test
    void aTaskFromAnotherConversationCannotEndThisConversationsCall() throws IOException {
        final String line = recorded("poll-done").replace("/task-52", "/task-052");

        assertThat(agent.toolEvents(line))
                .as("a malformed task suffix cannot alias a step id")
                .hasSize(1);
        final String foreign =
                recorded("poll-done").replace(CONVERSATION + "/task-52", "other/task-52");
        assertThat(agent.toolEvents(foreign))
                .as("a task id is correlated only within its own conversation")
                .hasSize(1);
    }

    @Test
    void unknownStatesAndBrokenToolMetadataDoNotStopTheRun() throws IOException {
        final String line = recorded("shell-start");

        assertThat(agent.toolEvents(line.replace("ACTIVE", "UNKNOWN")))
                .as("unobserved states cannot invent a tool phase")
                .isEmpty();
        assertThat(
                        agent.toolEvents(
                                line.replace(
                                        "\"tool_name\":\"run_command\"", "\"tool_name\":null")))
                .as("an absent tool name cannot create an anonymous call")
                .isEmpty();
        assertThat(agent.toolEvents(line.replace("\"parameters\":{", "\"parameters\":[")))
                .as("broken metadata cannot throw while reading a line")
                .isEmpty();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "write_file, EDIT",
        "grep_search, SEARCH",
        "find_by_name, SEARCH",
        "list_dir, SEARCH",
        "read_url_content, WEB",
        "search_web, WEB",
        "browser_open, WEB",
        "schedule, OTHER"
    })
    void eachToolHasItsKind(String tool, Kind kind) throws IOException {
        final String line =
                recorded("read-start")
                        .replace("\"tool_name\":\"read_file\"", "\"tool_name\":\"" + tool + "\"");

        assertThat(agent.toolEvents(line))
                .as("%s is of kind %s", tool, kind)
                .extracting(ToolEvent::kind)
                .containsExactly(kind);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "FAILED", "CANCELLED"})
    void aTaskEndsOnEachFinalStatus(String status) throws IOException {
        final String line = recorded("poll-done").replace("Status: DONE", "Status: " + status);

        assertThat(agent.toolEvents(line))
                .as("a task that is %s ends the shell call it ran", status)
                .extracting(ToolEvent::id, ToolEvent::background)
                .contains(org.assertj.core.groups.Tuple.tuple(CONVERSATION + "/52", true));
    }
}
