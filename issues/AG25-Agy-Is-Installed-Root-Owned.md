# AG25 — Agy Is Installed Root-Owned

```json
{
  "$schema": "issue-1.json",
  "id": "AG25",
  "title": "Agy Is Installed Root-Owned",
  "state": "Backlog",
  "size": "M",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "the antigravity binary a task starts and the agy wrapper that execs it are installed by root and owned by root, so the agent in a task cannot replace what Porelo starts as agy next time; the version stays pinned by the definition and moves only through the update job, and a self-update inside a task fails"
}
```

**Why this size.** M: the install moves from the agent's phase to the root phase of the image, which Porelo's agent
contract does not offer for a verified download today, so it takes one contract change agreed with Porelo; then the
definition, its tests and one check at the pin.

**Estimate.** M: 53 min, the measured median of M over all repositories (p80 114).

**What must be true.** Whatever the agent writes during a task, the next start of agy runs the pinned binary Porelo
built into the image, through the wrapper Porelo wrote.

- `antigravity` goes to `/usr/local/lib/agy/antigravity` and the wrapper to `/usr/local/bin/agy`, each mode 0755 and
  owned by `root:root`, in directories the agent cannot write; the wrapper execs that path, and nothing named `agy` or
  `antigravity` stays under `/home/agent`. What the wrapper writes into Gemini's directory in the home stays where it is.
- The version is pinned in `pom.xml` (`agent.cli.version`, `agent.cli.sha256`), the archive checked before it is
  unpacked; it moves only through the update job, outside any task.
- Auto-update stays off (`AGY_CLI_DISABLE_AUTO_UPDATE`), and a self-update tried anyway fails to write over the binary,
  by design.

## Plan

- Today the archive is an `install.artifacts` entry fetched into a cache directory in the agent's
  home, and an `as_agent` step unpacks `antigravity` and writes the wrapper `agy` into the home's bin directory. Porelo
  renders every artifact after `USER agent`, so the agent owns both.
- Preferred: Porelo's agent contract lets an artifact be fetched as root, rendered with the same digest check before
  `USER agent`; the archive goes to a root-only staging path, and the unpacking and the wrapper move to `as_root`,
  keeping the check of the archive's one member. The setup of Gemini's directory in the home stays in `as_agent`. The pinned artifact stays
  what `porelo agents --supply-chain` and `PinAgreementTest` read. Without that change the definition would write its
  own verified fetch in `as_root` and lose that record, so the contract change comes first.
- Tests, unit level: the definition's install puts the binary and the wrapper under `/usr/local`, owned by root, with
  no line that writes them as the agent (red first against today's definition); `PinAgreementTest` still holds the pin.
- At the pin, in a task: agy starts through `/usr/local/bin/agy` and still finds its client ids; a write to either file
  as the agent fails.

## Acceptance

- In a built image, `stat` of the wrapper and the binary shows `root`, and the agent's user cannot write them or their
  directories.
- An update tried in a task as the agent fails, and the next start runs the pinned version.

## To be checked

- The contract change for a root-owned artifact, agreed with Porelo's agent in the channel.
- Whether antigravity looks for anything beside its own path, measured at the pin.
