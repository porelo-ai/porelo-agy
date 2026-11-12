# AG22 — A Finished Run Answers Its Last Message

```json
{
  "$schema": "issue-1.json",
  "id": "AG22",
  "title": "A Finished Run Answers Its Last Message",
  "state": "Backlog",
  "size": "S",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "a finished unattended Antigravity CLI run answers the text of its last message, declared in session.waiting.last_message, so a reader's answer is not empty; the declaration adds no screen rule and so changes nothing in how Porelo wakes the agent"
}
```

**Why this size.** one declaration and its test, after reading one recorded run; no contract another repository reads beyond the existing field.

**What must be true.** When an unattended Antigravity CLI run ends, Porelo answers what the agent said last. A reader whose
findings stand in its last message is judged on them, not on an empty answer, which is what a run answers while the
definition declares no last message.

## Plan

- The contract built against: `session.waiting.last_message` names the last record whose fields match `record` and
  the field holding its text; with `part: { record: {...}, text: <field> }` that field may hold a list of parts, and
  the answer is the text of each matching part, joined by a newline. A `waiting` that holds no screen rule declares
  no screen: Porelo then types no wake line into the agent for it.
- Antigravity CLI's unattended stream (`--output-format stream-json`) carries the run's last reply in a record
  still to be named from a recorded run; `src/main/resources/agent/agy.yaml` declares it under its existing `waiting`,
  beside its screen rules.
- Tests: a unit test answers the last message over the agent's records of a run's end (red first, without the
  declaration), from records as the agent writes them in a recorded run, never as the code assumes them.

## Acceptance

- Over a recorded run's end - a reply, a tool call, its reasoning, a later reply - the answer is the text of the last
  reply, and a run without one answers nothing.
- The declaration changes neither the agent's screen rules nor whether Porelo wakes it.

## To be checked

- The record and field the agent's last reply stands in, read in a recorded unattended run at the pinned version.
