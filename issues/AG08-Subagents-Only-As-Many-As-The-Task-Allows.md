# AG08 — Subagents Only As Many As The Task Allows

**Status:** Accepted (operator, 2026-10-31).

**What must be true.** Antigravity CLI in a Porelo task starts no subagent unless the task allows it; a
task asking for more than it can enforce is refused by Porelo, never run with less.

## Plan

- **The contract built against:** Porelo's agent interface carries the number of subagents an agent may
  start inside a task, 0 (none) by default; each agent definition declares what it can enforce - off, a
  concurrent count, a depth - and Porelo refuses a value the definition cannot enforce instead of
  enforcing less.
- Measured headless at the pin 1.4.5, on the VM in Gemini API-key mode: by default `invoke_subagent`
  starts a subagent.
  - **Off:** a `PreToolUse` hook in `~/.gemini/config/hooks.json`, matching `invoke_subagent` and
    answering `{"decision":"deny"}`, holds - the call ends in error and the agent reports it denied.
  - No CLI setting turns subagents off or counts them; the cap of 128 is only an instruction in the
    system prompt. So this definition declares off and nothing more.
  - The init record also lists `run_workflow`, `browser_subagent`, `define_subagent` and
    `manage_subagents`. Asked to start an agent with them past a hook matching only `invoke_subagent`,
    the model started none: the first two are not offered to it headless, `define_subagent` only
    registers a definition, and `manage_subagents` only lists and stops. The matcher still names all
    five, `invoke_subagent|run_workflow|browser_subagent|define_subagent|manage_subagents`, which was
    measured to hold, so a version that offers them is covered.

## Acceptance

- Seen to fail first: a task with the default asks agy to start a subagent, and it does.
- With the change, the same task starts none, and a task allowing N > 0 is refused by Porelo naming agy.

## To be checked

- The hook attended, at a terminal, where `run_workflow` and `browser_subagent` may be offered.
- Whether a CLI setting reaches the internal `AllowedSubagents` choice, which would replace the hook.
