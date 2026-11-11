package org.fuin.porelo.agent.impl.agy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.fuin.porelo.agent.api.SessionIds;
import org.fuin.porelo.agent.api.ToolEvent;
import org.fuin.porelo.agent.api.ToolEvent.Kind;
import org.fuin.porelo.agent.api.ToolEvent.Phase;
import org.jspecify.annotations.Nullable;

/** Maps Antigravity CLI stream lines to tool events without retaining state. */
public final class AgyToolEvents {

    private static final Set<String> TERMINAL_STATUSES =
            Set.of("DONE", "COMPLETED", "FAILED", "CANCELLED");

    private AgyToolEvents() {}

    /**
     * Reads the tool moments in one raw output line.
     *
     * @param line One stream-json line.
     * @return Its tool events, or an empty list for an unreadable or unknown record.
     */
    public static List<ToolEvent> read(String line) {
        final Map<?, ?> record = AgyJsonFormatter.parseObject(line.strip());
        if (record == null
                || !"step_update".equals(record.get("event"))
                || !(record.get("step_update") instanceof Map<?, ?> step)
                || !"tool".equals(step.get("step_type"))
                || !(step.get("conversation_id") instanceof String conversation)
                || !SessionIds.isId(conversation)
                || !(step.get("step_index") instanceof Number number)
                || !(step.get("tool_name") instanceof String tool)
                || tool.isBlank()) {
            return List.of();
        }
        // The JSON reader uses doubles, whose consecutive whole numbers end below 2^53.
        final double numericIndex = number.doubleValue();
        if (!Double.isFinite(numericIndex)
                || numericIndex < 0
                || numericIndex >= 0x1p53
                || numericIndex != Math.rint(numericIndex)) {
            return List.of();
        }
        final long index = number.longValue();
        final Phase phase;
        if ("ACTIVE".equals(step.get("state"))) {
            phase = Phase.START;
        } else if ("DONE".equals(step.get("state"))) {
            phase = Phase.END;
        } else {
            return List.of();
        }
        final Map<?, ?> info = step.get("tool_info") instanceof Map<?, ?> map ? map : Map.of();
        final Map<?, ?> parameters =
                info.get("parameters") instanceof Map<?, ?> map ? map : Map.of();
        final Kind kind = kind(tool);
        final String command =
                kind == Kind.SHELL && parameters.get("CommandLine") instanceof String value
                        ? value
                        : null;
        // A task id is predictable, but only a later manage_task line proves background work.
        final String jobId = kind == Kind.SHELL ? conversation + "/task-" + index : null;
        final ToolEvent event =
                new ToolEvent(
                        0,
                        conversation + "/" + index,
                        phase,
                        tool,
                        kind,
                        command,
                        false,
                        jobId,
                        null,
                        null,
                        null,
                        phase == Phase.END ? duration(step.get("duration_seconds")) : null);
        final ToolEvent jobEnd =
                phase == Phase.END && "manage_task".equals(tool)
                        ? jobEnd(conversation, parameters, info.get("output"))
                        : null;
        return jobEnd == null ? List.of(event) : List.of(event, jobEnd);
    }

    private static Kind kind(String tool) {
        return switch (tool) {
            case "run_command" -> Kind.SHELL;
            case "edit_file", "write_file" -> Kind.EDIT;
            case "read_file" -> Kind.READ;
            case "grep_search", "find_by_name", "list_dir" -> Kind.SEARCH;
            case "read_url_content", "search_web" -> Kind.WEB;
            default -> tool.startsWith("browser_") ? Kind.WEB : Kind.OTHER;
        };
    }

    private static @Nullable Long duration(@Nullable Object value) {
        if (!(value instanceof Number seconds)) {
            return null;
        }
        final double millis = seconds.doubleValue() * 1000;
        return Double.isFinite(millis) && millis >= 0 && millis < Long.MAX_VALUE
                ? Long.valueOf((long) millis)
                : null;
    }

    private static @Nullable ToolEvent jobEnd(
            String conversation, Map<?, ?> parameters, @Nullable Object outputValue) {
        final String prefix = conversation + "/task-";
        if (!(parameters.get("TaskId") instanceof String taskId)
                || !taskId.startsWith(prefix)
                || !taskId.substring(prefix.length()).matches("0|[1-9][0-9]*")
                || !(outputValue instanceof String output)) {
            return null;
        }
        final boolean cancelled =
                "kill".equals(parameters.get("Action"))
                        && output.equals("Task \"" + taskId + "\" cancelled.");
        final String header = "Task: " + taskId + "\nStatus: ";
        final boolean terminalStatus =
                "status".equals(parameters.get("Action"))
                        && output.startsWith(header)
                        && TERMINAL_STATUSES.contains(
                                output.substring(header.length()).split("\n", 2)[0]);
        if (!cancelled && !terminalStatus) {
            return null;
        }
        // The management call's duration measures the poll, not the background job.
        return new ToolEvent(
                0,
                conversation + "/" + taskId.substring(prefix.length()),
                Phase.END,
                "run_command",
                Kind.SHELL,
                null,
                true,
                taskId,
                null,
                null,
                null,
                null);
    }
}
