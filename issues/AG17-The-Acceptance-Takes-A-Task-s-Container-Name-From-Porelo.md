# AG17 — The Acceptance Takes A Task's Container Name From Porelo

```json
{
  "$schema": "issue-1.json",
  "id": "AG17",
  "title": "The Acceptance Takes A Task's Container Name From Porelo",
  "state": "Implementing",
  "size": "XS",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "the acceptance scenarios name a task by its own name in Porelo's commands and take its container's name from `porelo task status --json`, never built as `porelo-<project>-<task>`"
}
```

**Why this size.** text in the acceptance scenarios alone; no shipped code, no contract of this repository's own.

**What must be true.** The acceptance scenarios hold when Porelo changes how it spells a task's container: Porelo's
commands are given the task's own name, and where a scenario needs the container itself (`podman exec`, `podman top`,
`podman cp`, the task's state directory), it takes the name `porelo task status <task> --json` gives as `name`. Porelo
now names a container `porelo-<project>_<task>`; a scenario that built `porelo-<project>-<task>` by hand failed on it.

## Plan

- Every `porelo task ...` and `porelo talk ...` call in `src/acceptance` names the task by its own name.
- Every `podman exec|top|cp` and the task's state directory under `/run/user/<uid>/porelo/` take the container's name
  from `porelo task status <task> --json`.
- The live-shell scenario checks that the start names a container and removes the task by its own name.

## Acceptance

- The acceptance legs pass against a Porelo that names containers `porelo-<project>_<task>`.
- No scenario in `src/acceptance` spells a task's container name.
