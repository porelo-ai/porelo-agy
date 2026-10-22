package org.fuin.porelo.agent.impl.agy;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fuin.porelo.agent.api.AgentEnd;
import org.fuin.porelo.agent.api.LogFormatter;
import org.fuin.porelo.wire.Json;
import org.jspecify.annotations.Nullable;

/** Formats Antigravity CLI JSON output into readable status lines and turn outcomes. */
public final class AgyJsonFormatter implements LogFormatter {

    private static final String ERROR_PREFIX = "AGY_ERROR:";
    private static final int MAX_CODE_POINTS = 300;
    private static final Pattern HTTP_STATUS = Pattern.compile("\\b([45][0-9]{2})\\b");

    /** Creates a stateless JSON formatter. */
    public AgyJsonFormatter() {}

    /** {@inheritDoc} */
    @Override
    public @Nullable String format(String line) {
        final String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith(ERROR_PREFIX)) {
            final Map<?, ?> payload = parseObject(trimmed.substring(ERROR_PREFIX.length()).strip());
            if (payload != null) {
                final String message =
                        text(payload, "message", text(payload, "error", "Antigravity CLI error"));
                return sanitize("Error: " + message);
            }
            return sanitize("Error: " + trimmed.substring(ERROR_PREFIX.length()).strip());
        }
        final Map<?, ?> record = parseObject(trimmed);
        if (record == null) {
            return null;
        }
        if ("step_update".equals(record.get("event"))
                && record.get("content") instanceof String content
                && !content.isBlank()) {
            return sanitize(content);
        }
        if ("ERROR".equals(record.get("status"))) {
            final String error =
                    text(record, "error", text(record, "message", "Antigravity CLI turn failed"));
            return sanitize("Error: " + error);
        }
        if ("SUCCESS".equals(record.get("status"))
                && record.get("response") instanceof String response
                && !response.isBlank()) {
            return sanitize(response);
        }
        return null;
    }

    static @Nullable AgentEnd ended(String line) {
        final String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith(ERROR_PREFIX)) {
            final Map<?, ?> payload = parseObject(trimmed.substring(ERROR_PREFIX.length()).strip());
            if (payload == null) {
                final String raw = sanitize(trimmed.substring(ERROR_PREFIX.length()).strip());
                final Integer status = extractStatus(raw, null);
                return new AgentEnd(
                        false, raw, status == null ? AgentEnd.AGENT : AgentEnd.PROVIDER, status);
            }
            final String message =
                    sanitize(
                            text(
                                    payload,
                                    "message",
                                    text(payload, "error", "Antigravity CLI error")));
            final Integer status = extractStatus(message, payload.get("error_code"));
            return new AgentEnd(
                    false, message, status == null ? AgentEnd.AGENT : AgentEnd.PROVIDER, status);
        }
        final Map<?, ?> record = parseObject(trimmed);
        if (record == null) {
            return null;
        }
        final Object event = record.get("event");
        if (event != null && !"result".equals(event)) {
            return null;
        }
        final Object status = record.get("status");
        if ("SUCCESS".equals(status)) {
            return new AgentEnd(true, "", AgentEnd.AGENT, null);
        }
        if (!"ERROR".equals(status)) {
            return null;
        }
        final String message =
                sanitize(
                        text(
                                record,
                                "error",
                                text(record, "message", "Antigravity CLI turn failed")));
        final Integer httpStatus = extractStatus(message, record.get("error_code"));
        return new AgentEnd(
                false,
                message,
                httpStatus == null ? AgentEnd.AGENT : AgentEnd.PROVIDER,
                httpStatus);
    }

    private static @Nullable Integer extractStatus(String message, @Nullable Object codeValue) {
        if (codeValue instanceof Number number) {
            final int code = number.intValue();
            if (code >= 400 && code <= 599) {
                return code;
            }
        }
        final Matcher matcher = HTTP_STATUS.matcher(message);
        if (matcher.find()) {
            return Integer.valueOf(matcher.group(1));
        }
        return null;
    }

    private static @Nullable Map<?, ?> parseObject(String text) {
        if (!text.startsWith("{")) {
            return null;
        }
        try {
            final Object parsed = Json.parse(text);
            return parsed instanceof Map<?, ?> map ? map : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String text(Map<?, ?> map, String key, String fallback) {
        return map.get(key) instanceof String value && !value.isBlank() ? value : fallback;
    }

    private static String sanitize(String input) {
        final StringBuilder clean = new StringBuilder();
        int count = 0;
        for (int index = 0; index < input.length(); ) {
            final int codePoint = input.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\t') {
                continue;
            }
            if (count == MAX_CODE_POINTS) {
                clean.append("...");
                break;
            }
            clean.appendCodePoint(codePoint);
            count++;
        }
        return clean.toString();
    }
}
