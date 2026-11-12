# AG21 — An Attended Session Takes An Opening Text

```json
{
  "$schema": "issue-1.json",
  "id": "AG21",
  "title": "An Attended Session Takes An Opening Text",
  "state": "Backlog",
  "size": "S",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "an attended session of Antigravity CLI opens on the person's opening text as its first turn and then waits for the person, the form (behind `-i`) declared in the agent definition and measured in a task"
}
```

**Why this size.** one definition field, a unit test and one attended check on the local VM; no contract another repository reads beyond
the field Porelo's API names.

**What must be true.** A person who starts an attended session of Antigravity CLI with an opening text, as a discussion does,
sees the agent answer that text as its first turn and then wait at its prompt for them. Porelo takes the form from the
agent definition, never from the flag that runs the agent once and ends.

## Plan

- The contract built against: Porelo's agent API hands an attended session an opening text only to an agent whose
  definition says how its interactive session takes one, positionally or behind a flag of its own. The prompt flag of
  a run that ends says nothing about it, so an agent that declares nothing is refused rather than guessed at. A text
  that is blank, holds a NUL, or begins with `-` or `@` is refused for every agent.
- `src/main/resources/agent/agy.yaml` declares the opening text behind `-i` (`--prompt-interactive`): `agy --help`
  reads "Run an initial prompt interactively and continue the session", while its headless `-p` is print mode. Read
  on a newer agy than the pin, so the pin's help is read first.
- Tests: a unit test reads the definition and asserts the form it declares (red first, against a definition without
  it); the session check below runs once on the local VM, never in the unit suite.

## Acceptance

- In a task on the local VM, attended, with the opening text "Name the file in /workspace that you read first":
  the first turn answers it, and afterwards the screen matches `at_rest` and the session is still open.
- A text that begins with `-` or `@` is refused by Porelo before the agent starts, and the agent never sees it.

## To be checked

- The field's name and shape in Porelo's agent API, before the definition names it.
- Whether a resumed session (the definition's resume flag plus an opening text) takes the text too, or Porelo must
  refuse that pair for Antigravity CLI.
