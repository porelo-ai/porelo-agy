# AG14 — The Subagent Scenario Proves The Denial By What Ran

```json
{
  "$schema": "issue-1.json",
  "id": "AG14",
  "title": "The Subagent Scenario Proves The Denial By What Ran",
  "state": "Idea",
  "size": "-",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": [],
  "covers": "the subagent scenario decides by what the task ran, not by the word the model answers; it failed once on ubuntu and passed on its re-run"
}
```

**What must be true.** The scenario "a task that allows no subagents has the start of one denied" (`session.feature`)
passes or fails by what the task ran, not by the word the model chooses to answer: a start of a subagent that the
hook denied leaves no subagent behind, and the scenario counts that, as Codex CLI's adapter counts session files.

**Why.** Build 42477648330 on 2026-11-08, on a commit that changed nothing in the adapter: the ubuntu leg failed
because the agent answered `STARTED`, while the fedora leg passed the same scenario. The ubuntu leg, re-run on the
same commit, passed. Today the scenario cannot tell a subagent that started from a model that says one did, so it
fails on the model's word, and a hook that stopped working would pass the day the model answered `MISSING` anyway.

## To be checked

- What Antigravity CLI leaves of a subagent in a task, a conversation or a log under the agent's home, to count.
- Whether the hook's denial is visible in the task's output or log, for a second check beside the count.
