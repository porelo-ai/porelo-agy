# AG24 — The Issue Heads Are Checked Every Day

```json
{
  "$schema": "issue-1.json",
  "id": "AG24",
  "title": "The Issue Heads Are Checked Every Day",
  "state": "Backlog",
  "size": "XS",
  "priority": "Low",
  "rank": 6,
  "blockedBy": [],
  "covers": "a daily run checks this repository's issue heads and index with the released porelo-release, as buildtools' shared rules do"
}
```

**Why this size.** One scheduled step in one workflow, copied from porelo-buildtools' `shared-rules.yml`; no code.

**What must be true.** An issue head or index that breaks the rules turns a daily run red, not only the next push.

## Plan

- **`.github/workflows/shared-rules.yml`**: a daily schedule and a step that runs `issues check issues` with the
  released porelo-release, taken as porelo-buildtools' `shared-rules.yml` does it (BT81, the source).
- **Level:** one dispatched run green; one with a broken head red, tried on a branch.
- Built only when the lane is through, by the operator's order of 2026-11-12.

## Acceptance

- The scheduled step runs `issues check issues` and passes on `main`.
- A head that breaks issue-1.json fails the step.
