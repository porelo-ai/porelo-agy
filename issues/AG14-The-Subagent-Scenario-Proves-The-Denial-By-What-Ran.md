# AG14 — The Subagent Scenario Proves The Denial By What Ran

```json
{
  "$schema": "issue-1.json",
  "id": "AG14",
  "title": "The Subagent Scenario Proves The Denial By What Ran",
  "state": "Estimated",
  "size": "S",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": ["AG18"],
  "covers": "the subagent scenario decides by what the task ran, not by the word the model answers; it failed once on ubuntu and passed on its re-run",
  "openQuestions": 0
}
```

**Why this size.** one scenario in `session.feature` and no Java: every step it needs is already in Porelo's
acceptance kit (`a script runs:`, `it exits zero`), and the task's own log is read with `porelo task logs`. The work
is the measuring on the VM: what a denied `invoke_subagent` leaves in agy's `stream-json` lines and in its
conversations, and the scenario seen red against an adapter without the hook. It reads the steps that
[AG18](README.md#ag18) puts into the task's log, so it is built after it.

**Estimate.** S: 27 min, the measured median of S over all repositories (n=167). More is likely: two VM runs (red,
then green) and a measuring run before them.

**What must be true.** The scenario "a task that allows no subagents has the start of one denied" (`session.feature`)
passes or fails by what the task ran, not by the word the model chooses to answer: the task's log shows that the agent
called a subagent tool and that the hook denied the call, and no subagent ran, as Codex CLI's adapter counts session
files. A model that answers `STARTED` after a denied call does not fail it; a hook that stopped working fails it
whatever the model answers.

**Why.** Build 42477648330 on 2026-11-08, on a commit that changed nothing in the adapter: the ubuntu leg failed
because the agent answered `STARTED`, while the fedora leg passed the same scenario. The ubuntu leg, re-run on the
same commit, passed. Today the scenario cannot tell a subagent that started from a model that says one did, so it
fails on the model's word, and a hook that stopped working would pass the day the model answered `MISSING` anyway.

## Plan

Read in today's code:

- The scenario checks only `its output contains "MISSING"` and `does not contain "STARTED"`, the output of
  `porelo task start` being the formatted text of agy's run. With today's `--output-format json` (`agy.yaml`
  `output_format_flags`) agy prints one final object (`status`, `response`, `duration_seconds`, `num_turns`, `usage`)
  and no step, so nothing that ran can be read from it.
- Porelo keeps what the agent printed, raw, in the task's state directory as `task.log`, emptied at the start of each
  run (`TaskLaunch`), and `porelo task logs <task> task.log` prints it (`TaskLogsCommand`, `TaskInventory.isLog`
  takes any `*.log`). With `stream-json`, which [AG18](README.md#ag18) switches to, each tool step is a `step_update`
  line with `conversation_id`, `step_index`, `step_type: tool`, `tool_name`, `state` ACTIVE then DONE, and on DONE its
  `output`.
- The hook `AgyContainerSetup` writes answers `{"decision":"deny","reason":"this task allows no subagents"}`;
  attended, the call ended in "tool call denied by pre-tool hook: this task allows no subagents".
- agy keeps each conversation as `<conversation_id>.db` under `.gemini/antigravity-cli/conversations` in the agent's
  home (`agy.yaml` `session_id`).

Steps:

1. Measure on the VM, once, with the adapter of [AG18](README.md#ag18): the scenario's task with the hook, and the same
   task with the hook left out (an adapter built locally without it, never committed). Keep both `task.log`s and the
   lists of conversation files. Read: the `invoke_subagent` step's DONE line when denied (its `output` or an error
   field holding the hook's reason), and what a subagent that ran leaves (its own `conversation_id` in the stream,
   steps under it, a second `.db`).
2. In `session.feature`, replace the two checks of the model's word by one `a script runs:` block, shaped like Codex
   CLI's adapter's: it reads `porelo task logs solo task.log` and requires
   - a `step_update` of `tool_name` `invoke_subagent` (the agent tried; otherwise the scenario proves nothing and
     fails),
   - that step ending with the hook's reason `this task allows no subagents`,
   - nothing of a subagent that ran: no `step_update` of another `conversation_id`, and, if step 1 shows a subagent
     writes a conversation of its own, exactly one `.db` in the container's conversations directory, copied out with
     `podman cp` from the container `porelo task status solo --json` names, as the codex scenario does.

   The script prints what it counted, so a red leg says why. The prompt stays; `it exits zero` stays; the word checks
   go.
3. Red first, on the VM: the new scenario against the adapter without the hook fails on the denial check (and on the
   count, where step 1 found one); with the hook it is green. Seen once each, written into this issue.

Level: acceptance only, on the VM; no unit test, since no code changes. `DocumentTestsTaggedTest` and the kit's own
tests are untouched.

## Acceptance

- The scenario passes with the hook whatever word the model answers, and fails with the hook left out whatever word
  the model answers: seen red once on the VM against an adapter without the hook, then green.
- A run in which the agent never calls `invoke_subagent` fails, saying so, instead of passing on nothing.
- The scenario uses only steps of Porelo's acceptance kit and names the task's container only by what
  `porelo task status --json` says.

## What makes it uncertain

- How agy's `stream-json` shows a step a `PreToolUse` hook denied was not measured: the DONE line may carry the reason
  in `output`, in an error field, or the step may end in another state. Step 1 settles it; a denied step that leaves
  no line at all would leave only the conversation count, and the scenario would then lean on that alone.
- Whether a subagent's steps reach the parent's stream, and whether it writes a `.db` of its own, was not measured:
  agy's subagents are off and the sub-agent measurement in porelo-claude-code did not run agy. If neither shows, a
  subagent that ran is told only by the `invoke_subagent` step ending without the denial, which is still not the
  model's word.
- The model may still decline to call the tool at all, which now fails the scenario instead of passing it. A flaky
  refusal would show as a new kind of red leg; the prompt is kept as plain as today's.
- The ubuntu leg's `STARTED` was never read from what ran. If the hook really did not hold there, this scenario turns
  that flake into a steady red, which is a fault in the adapter, not in this issue, and is filed of its own.

## To be checked

- What a denied `invoke_subagent` step looks like in `stream-json`, and what a subagent that ran leaves in the stream
  and in the conversations directory (step 1).
- That `task.log` holds the steps for a `porelo task start --prompt` run, as it did in the measurement of
  [AG18](README.md#ag18), and is still there after the task ends.
