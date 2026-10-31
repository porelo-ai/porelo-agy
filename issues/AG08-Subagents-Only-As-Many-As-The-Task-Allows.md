# AG08 — Subagents Only As Many As The Task Allows

**Status:** Accepted (operator, 2026-10-31).

**What must be true.** Antigravity CLI in a Porelo task starts no subagent unless the task allows it; a
task asking for more than it can enforce is refused by Porelo, never run with less.

## Plan

- **The contract built against:** Porelo's agent interface carries the number of subagents an agent may
  start inside a task, 0 (none) by default; each agent definition declares what it can enforce - off, a
  concurrent count, a depth - and Porelo refuses a value the definition cannot enforce instead of
  enforcing less.
- Read in 1.4.12, not yet at the pin: subagents are the tool `invoke_subagent`, and workflows start agents
  of their own. No CLI setting turns them off or counts them; the cap of 128 is only an instruction in
  the system prompt. A `PreToolUse` hook denying `invoke_subagent` is the one switch found, so this
  definition declares off and nothing more.

## Acceptance

- Seen to fail first: a task with the default asks agy to start a subagent, and it does.
- With the change, the same task starts none, and a task allowing N > 0 is refused by Porelo naming agy.

## To be checked

- On the VM, at the pinned version: that the hook stops `invoke_subagent`, attended and headless, and
  whether a workflow still starts agents past it.
- Whether a CLI setting reaches the internal `AllowedSubagents` choice, which would replace the hook.
