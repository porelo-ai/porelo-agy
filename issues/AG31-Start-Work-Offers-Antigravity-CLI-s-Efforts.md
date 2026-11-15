# AG31 — Start Work Offers Antigravity CLI's Efforts

```json
{
  "$schema": "issue-1.json",
  "id": "AG31",
  "title": "Start Work Offers Antigravity CLI's Efforts",
  "state": "Implementing",
  "size": "XS",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "the definition declares the reasoning efforts Antigravity CLI takes and how one is passed (`--effort {effort}`), so Start work offers them to pick instead of typing and an absent effort passes nothing"
}
```

**Why this size.** One section of the agent definition and a test that reads it; no code.

**Estimate.** XS: 9 min, the measured median of XS over all repositories.

**What must be true.** A person starting work with Antigravity CLI picks the effort from the levels the pinned CLI accepts,
and no effort picked passes nothing.

## Why

Part of ST369, the Start work redesign. The operator wants to pick the model and the reasoning effort from what the
chosen agent offers, not type them. Porelo's contract (B795, handed over locally as porelo 5328c5df4) adds an optional
top-level `effort` section to an agent's definition: `effort: { arguments: [..., "{effort}"], values: [...] }`, the
values in the agent's own words and order. Today's definition reader skips an unknown top-level section, so the
declaration does not break an older Porelo.

## Plan

- Declare `effort` in the definition: `--effort {effort}`, with the levels the pinned agy 1.5.3 takes, read from its own help on
  the pin where a VM slot allows, else from the laptop's version and said so.
- A test that reads the section from the definition itself (the API record does not hold it yet), seen red first.
- **Level:** unit tests of the definition.

## Acceptance

- The definition declares the effort levels and how they are passed, in the contract's shape, and today's reader
  still reads the definition.
