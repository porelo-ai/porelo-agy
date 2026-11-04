# AG08 — Subagents Only As Many As The Task Allows

**Status:** Review.
**Size:** S - one module and its tests, one more file the setup writes; about 45 minutes: this repository's
median of 35, raised by the scenario on the VM, the check that an inline hook command runs through a shell,
and the attended check.

**What must be true.** Antigravity CLI in a Porelo task starts no subagent unless the task allows it; a
task asking for more than it can enforce is refused by Porelo, never run with less.

**This does not hold today.** Porelo takes a task's default of 0 as none and reads `agy.yaml`, which declares nothing, as
off, but this adapter switches nothing off, so Antigravity CLI in such a task still starts a subagent, as measured below.

## Plan

- **The contract built against:** Porelo's agent interface carries one number, the most subagents an agent may
  run at the same time inside a task (not a total over the task), 0 (none) by default, and no depth; each
  agent definition declares what it can enforce of it - off only, or a count - and Porelo refuses a value the
  definition cannot enforce with exit code 64 instead of enforcing less. Where a CLI needs a depth to make the
  count hold, the adapter sets it itself.
- Measured headless at the pin 1.4.5, on the VM in Gemini API-key mode with Gemini 3.6 Flash (Low), the
  settings and onboarding files written as `AgyContainerSetup` writes them: by default `invoke_subagent`
  starts a subagent.
  - **Off:** a `PreToolUse` hook in `.gemini/config/hooks.json` in the home, matching `invoke_subagent` and
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
- **`AgyContainerSetup`** adds `.gemini/config/hooks.json` in the agent's home to both its file lists, the API-key
  one and the signed-in one: a `PreToolUse` hook matching the five tools whose command prints the deny
  answer inline - a `ContainerFile` carries no executable bit, so there is no script to run.
- **Red first:** `AgyContainerSetupTest` - both credential kinds yield the hooks file with that matcher;
  today neither does.
- **On the VM:** a `session.feature` scenario asks for a subagent and sees the call denied.
- **A soft switch:** the agent has a shell and can start another copy of its CLI; this issue governs only
  the built-in subagents. A hard bound is Porelo's own, outside this repository.
- **Porelo's version:** `porelo.version` in `pom.xml` is already 0.5.1-SNAPSHOT, and the published snapshot's agent API
  carries the value (`SubagentLimit`, `subagents` in `SetupContext`).

## Acceptance

- Seen to fail first: a task with the default asks agy to start a subagent, and it does.
- With the change, the same task starts none, and a task allowing N > 0 is refused by Porelo naming agy.

## Open questions

- None.

## Measured

On the VM at the pin 1.4.5 in Gemini API-key mode with `gemini-3.6-flash-low`, the adapter installed in the account,
against a Porelo older than the one on `main`, so `project remove` in the new scenario's last step was run as
`project unfollow`:

- **Seen to fail first:** the new `session.feature` scenario against the adapter before the change: the agent
  answered `STARTED`, and said the subagent completed.
- **With the change, headless:** the same scenario is green: the agent answers `MISSING`. So a hook command given
  inline runs, through a shell, which was to be checked.
- **Attended, at a terminal, one run, not kept as a scenario:** the call ends in "tool call denied by pre-tool hook:
  this task allows no subagents".
- **Above 0, one run, not kept as a scenario:** `--subagents 1` ends with exit code 64, "agy can only switch its
  subagents off", and nothing is created.

The hook is also written for a task with neither endpoint nor token, which the plan did not name: it holds no
credential, and without it an agent started there would still start a subagent. The signed-in list is covered by the
unit test only: no scenario here signs in.

## To be checked

- What the scenario proves: Antigravity CLI's output is the agent's text alone, so `MISSING` and no `STARTED` rest on
  what the model says. Claude Code's and Oh My Pi's scenarios see the tool call, and Codex's counts its session files.
  Whether agy leaves something to count from outside was not measured.
- Whether `run_workflow` and `browser_subagent` are offered attended: the attended run asked only for
  `invoke_subagent`. The matcher names them either way.
- Whether a CLI setting reaches the internal `AllowedSubagents` choice, which would replace the hook.
