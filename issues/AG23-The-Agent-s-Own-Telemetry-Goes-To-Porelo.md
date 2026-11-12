# AG23 — The Agent's Own Telemetry Goes To Porelo

```json
{
  "$schema": "issue-1.json",
  "id": "AG23",
  "title": "The Agent's Own Telemetry Goes To Porelo",
  "state": "Idea",
  "size": "-",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": [],
  "covers": "the adapter declares Antigravity CLI's own OpenTelemetry export pointed at Porelo's local receiver in the task, with every switch that would carry content - prompts, tool arguments and output, responses - set explicitly off"
}
```

**What must be true.** A task's Antigravity CLI sends its own telemetry - its events, metrics and, where it has them, traces -
over OTLP to the receiver Porelo runs for the task, and to nowhere else. Every switch that would put content into that
telemetry (the person's prompts, tool arguments and output, the agent's responses) is set off explicitly in the
definition, never left to a default, so a new version that changes a default changes nothing here.

The contract built against: Porelo runs a local OTLP receiver for each task and keeps what arrives in the task's
records under its run; the adapter only declares how its agent is pointed at it.

What is known today, to be confirmed at the pinned version:
- Antigravity's settings name Google usage telemetry and OpenTelemetry only in its SDK; whether the CLI itself has a
  switch is not known.

## To be checked

- Which product and version has an OpenTelemetry switch at all: the CLI at the pin, or only the SDK.
- That Google usage telemetry stays off in a task whatever is decided here.
