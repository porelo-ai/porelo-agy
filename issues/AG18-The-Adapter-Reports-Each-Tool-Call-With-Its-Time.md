# AG18 — The Adapter Reports Each Tool Call With Its Time

```json
{
  "$schema": "issue-1.json",
  "id": "AG18",
  "title": "The Adapter Reports Each Tool Call With Its Time",
  "state": "Implementing",
  "size": "M",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "the adapter reads each tool call from its agent's output and hands Porelo its tool, kind, start, end, a shell call's command line, whether it ran in the background, and a sub-agent's name and the calls made under it, in Porelo's common entry",
  "openQuestions": 0
}
```

**Why this size.** the headless output moves from `json` to `stream-json`, with `AgyJsonFormatter`, its tests and the acceptance that reads the final status; then a reader over `step_update`, a name table and tests from the recorded run of CC57 in porelo-claude-code. Grown from the analysis: agy's `json` output, which the adapter asks for today, holds no step at all (CC57 in porelo-claude-code). Its `stream-json` does, so the adapter's output format changes too. It builds against `ToolEvents` of Porelo's agent API, contract version 5.

**Estimate.** M: 53 min, the measured median of M over all repositories (n=113).

**What must be true.** For every tool call the agent makes in an unattended task, Porelo records one entry in its
common tool-call format: the task, the tool as the agent names it, its kind (shell, edit, read, search, web,
sub-agent, mcp or other), when it started and when it ended, for a shell call its command line, whether it ran in the
background, and for a sub-agent the name it was given and the calls made under it. Porelo joins those calls to the
issue whose id names the sub-agent. The adapter only maps its agent's own output; the format, the time a line without
its own time gets, the masking of a command line and where the entry is kept are Porelo's.

## Plan

- Source: agy's `--output-format stream-json`, measured with the pinned agy (CC57 in porelo-claude-code). `agy.yaml`'s
  `output_format_flags` change to it, and `AgyJsonFormatter` reads its lines: `step_update` for each step and `result`
  for the final status, where today it reads one final object.
- A `step_update` with `step_type: tool` and `state: ACTIVE` is START, the same `step_index` with `state: DONE` is END;
  `id` = `<conversation_id>/<step_index>`, `tool` = `tool_name`, the input in `tool_info.parameters`. DONE carries
  `duration_seconds` and `output`, no clock time; `at` stays empty and the END passes `durationMs` =
  `duration_seconds` × 1000, from which porelod dates the start.
- `AgyAgent` overrides `Agent#toolEvents(String line)`; `AgyToolEvents` maps one output line to its
  `ToolEvent(line, id, phase, tool, kind, command, background, jobId, at, name, parent, durationMs)`. It keeps no state between
  lines: Porelo joins a start to its end by `id`, a background job's by `jobId`, and a call to its sub-agent by
  `parent`.
- Kinds: `run_command` -> shell (`command` = `parameters.CommandLine`), `edit_file`/`write_file` -> edit, `read_file` -> read, `grep_search`/`find_by_name`/`list_dir` -> search, `browser_*`/`read_url_content`/`search_web` -> web, any other (`manage_task`, `schedule`, ...) -> other.
- A shell call's `command` is its whole command line as the stream gives it, unmasked; Porelo masks it before it is
  stored.
- Sub-agents: the adapter turns them off (`subagents: off`); `name` and `parent` stay empty.
- Background: agy moves a long `run_command` to a task `<conversation_id>/task-<step_index>` and ends the step
  early; the job's end is not reported by itself, only in the output of a `manage_task` call the agent makes. Such a
  job is recorded with its step's end; a `manage_task` status completed, failed or cancelled for that task id ends it
  as `jobId`.
- Red first: `AgyToolEventsTest` feeds recorded lines (a shell step, a read, an edit, a step moved to a task and its `manage_task` end, the `result` line, and a line that is not JSON) and expects the events above; it fails while
  `toolEvents` still answers the API's default, nothing.
- Level: unit tests over recorded output only; the acceptance legs show the entries of a real run once Porelo writes
  them.

## Acceptance

- A recorded unattended run yields one entry per tool call with tool, kind, start, end and, for a shell call, its
  command line; a background job's start and end are joined by its id; a sub-agent carries its name.
- No entry holds a file's content or a tool's output; a command line reaches Porelo unmasked and is stored masked.
- A line the reader cannot read yields nothing and never stops the run.

## What makes it uncertain

- No line flags a step as moved to the background; it is told from the task id in a later `manage_task`.
- A failed step's `state` was not seen in the measured run.

## Decisions

- Only unattended tasks are measured. Attended (tmux) sessions write no JSON stream; whether their tool calls are
  recorded is decided later.
- A shell call keeps its whole command line, not only its program. Porelo masks it before it is stored; the adapter
  passes the line as the stream gives it.
- A task gives each issue a sub-agent of its own, named by the issue's id; an issue's time is its sub-agent's span.
- agy's sub-agents stay off, so an agy task's time is shared evenly among its issues.
- The task's log holds every step's output with `stream-json`; the larger log is accepted.

## To be checked

- The agent API with `name`, `parent` and `durationMs` on `ToolEvent`, installed locally to build against.
