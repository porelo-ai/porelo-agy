# AG08 — Subagents Only As Many As The Task Allows

**Status:** Analyzed; Accepted (operator, 2026-10-31).
**Size:** S - one module and its tests, one more file the setup writes; about 45 minutes.

**What must be true.** Antigravity CLI in a Porelo task starts no subagent unless the task allows it; a
task asking for more than it can enforce is refused by Porelo, never run with less.

## Plan

- **The contract built against:** Porelo's agent interface carries the number of subagents an agent may
  start inside a task, 0 (none) by default; each agent definition declares what it can enforce - off, a
  concurrent count, a depth - and Porelo refuses a value the definition cannot enforce instead of
  enforcing less.
- Measured headless at the pin 1.4.5, on the VM in Gemini API-key mode with Gemini 3.6 Flash (Low), the
  settings and onboarding files written as `AgyContainerSetup` writes them: by default `invoke_subagent`
  starts a subagent.
  - **Off:** a `PreToolUse` hook in `~/.gemini/config/hooks.json`, matching `invoke_subagent` and
    answering `{"decision":"deny"}`, holds - the call ends in error and the agent reports it denied.
    Measured shape: `{"no-subagents":{"PreToolUse":[{"matcher":"<regex>","hooks":[{"type":"command",
    "command":"<cmd>","timeout":10}]}]}}`, the command reading the call on standard input and printing
    `{"decision":"deny","reason":"<why>"}`; the reason reaches the model.
  - No CLI setting turns subagents off or counts them; the cap of 128 is only an instruction in the
    system prompt. So this definition declares off and nothing more.
  - The init record also lists `run_workflow`, `browser_subagent`, `define_subagent` and
    `manage_subagents`. Asked to start an agent with them past a hook matching only `invoke_subagent`,
    the model started none: the first two are not offered to it headless, `define_subagent` only
    registers a definition, and `manage_subagents` only lists and stops. The matcher still names all
    five, `invoke_subagent|run_workflow|browser_subagent|define_subagent|manage_subagents`, which was
    measured to hold, so a version that offers them is covered.
- **`agy.yaml`** declares off and nothing more, so Porelo refuses a task allowing more than 0.
- **`AgyContainerSetup`** adds `/home/agent/.gemini/config/hooks.json` to both its file lists, the API-key
  one and the signed-in one: a `PreToolUse` hook matching the five tools whose command prints the deny
  answer inline - a `ContainerFile` carries no executable bit, so there is no script to run.
- **Red first:** `AgyContainerSetupTest` - both credential kinds yield the hooks file with that matcher;
  today neither does.
- **On the VM:** a `session.feature` scenario asks for a subagent and sees the call denied.
- **A soft switch:** the agent has a shell and can start another copy of its CLI; this issue governs only
  the built-in subagents. A hard bound is Porelo's own, outside this repository.
- **Porelo's version:** `porelo.version` in `pom.xml` moves to the Porelo snapshot whose agent API
  carries the value; until then nothing here compiles against it.

## Acceptance

- Seen to fail first: a task with the default asks agy to start a subagent, and it does.
- With the change, the same task starts none, and a task allowing N > 0 is refused by Porelo naming agy.

## Open questions

- None.

## To be checked

- Whether a hook command given inline, not as a script path, runs through a shell; measured only with a
  script path.
- The hook attended, at a terminal, where `run_workflow` and `browser_subagent` may be offered.
- Whether a CLI setting reaches the internal `AllowedSubagents` choice, which would replace the hook.
