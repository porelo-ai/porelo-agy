# AG02 — The Repository's Agent Works In A Porelo Task

**Status:** Analyzed; blocked by [AG04](README.md#ag04) (an Idea), the moves of wave 7a, porelo-project
[PJ59](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj59) (the move's procedure and check)
and porelo [B298](https://github.com/porelo-ai/porelo/blob/main/issues/base/README.md#b298).
**Size:** S - no code here beyond `porelo.yml`, the move itself done by the procedure; about 2 hours of the agent's
work, plus the operator's attended start of the task.

**What must be true.** `porelo-agy`'s agent works in a Porelo task of the project `porelo` on the move's host, reached
in the project's room under the repository's name, and works this issue from start to Review inside that task with
the local forge's build green.

From porelo-project [PJ59](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj59), the move's wave 7b: one agent per repository, each move checked before the next.

## Plan

Read in today's `porelo.yml`, porelo's `doc/project-file.md` (*A repository's own image*) and `doc/running.md` (the
agent's layer), and the other agents' answers of 2026-10-28.

- **The image:** today's `porelo.yml` holds the pinned GraalVM, shfmt and the build tools, and `mvnw` brings
  Maven, so the unit suite and the native packages build in the task as porelo-codex's did in a real task
  ([CX02](https://github.com/porelo-ai/porelo-codex/blob/main/issues/README.md#cx02)). The agent's
  CLI comes from its package's own layer, not from `porelo.yml`. The acceptance legs do not run in a task (no container
  inside it, no rented machine); they run in the forge's Build ([AG04](README.md#ag04)).
- **What the task must reach:** Maven Central and Sonatype or the forge's registry, the agent's own download host
  while the image is built, and the forge's address as a host service. That egress is the project's, in porelo-project's
  `project.yml`.
- **The sub-agent reading (operator's word):** no sub-agent CLI is in the task's image, since a project file names one
  agent per repository and porelo describes no second sign-in inside a task. Until porelo
  [B172](https://github.com/porelo-ai/porelo/blob/main/issues/base/README.md#b172)'s reviewer task is built, an agent
  outside the task reads the pull request's diff against the skills, as today's sub-agent reading does, and posts its
  findings in the room.
- **Steps:**
  0. *A dry run on this machine first*, as for porelo-message-sluice: the task online with GitHub as upstream, its
     Build read through the `github` reader, snapshots from Sonatype; it shows the image, the room, the wake and the
     gate, and only the `forgejo` lines wait for the host.
  1. *Before, from this machine:* a dry run of the task start on the host names each image key's source as
     `porelo.yml`; the image builds; inside it, `./mvnw -s settings.xml verify` and the native packages pass.
  2. *porelo-project (the coordinator):* `repositories.porelo-agy.agent` and the nickname `Agy`
     ([B298](https://github.com/porelo-ai/porelo/blob/main/issues/base/README.md#b298)), and the upstream switched to the
     host's forge, as [PJ59](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj59)'s procedure says.
  3. *The start:* the operator starts the task attended; inside it the agent takes this issue to Review, its push
     through the gate to `porelo/<task>`, the forge's Build read through the `forgejo` reader.
  4. *After the check:* the session on this machine gives up `porelo-agy` (the responsibility line of the shared area
     of `.AGENTS.md` changes through the channel); its VM accounts stay with the repositories not moved yet.
- **Red first:** [PJ59](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj59)'s move check for
  `porelo-agy` before the move, each line failing by name.

## Acceptance

- The task's image is built from `porelo-agy`'s `porelo.yml`, with no sub-agent CLI in it.
- The task follows `porelo-agy` through the gate; its pushes reach the local Forgejo, and it reads its builds through the
  `forgejo` reader.
- Its build runs on the local forge as on the central one, [AG04](README.md#ag04).
- The room shows it as `Agent Agy`, from the nickname `Agy` in porelo-project's `project.yml`, set once porelo
  [B298](https://github.com/porelo-ai/porelo/blob/main/issues/base/README.md#b298) is published.
- Antigravity CLI answers in the task with the operator's Google subscription from the vault (porelo [P12](https://github.com/porelo-ai/porelo/blob/main/issues/providers/README.md#p12)).
- [PJ59](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj59)'s move check is red for `porelo-agy` before the move and green after it; the agent's session on this machine and its
  VM accounts are no longer used, and the way back [PJ59](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj59) describes is open.

## Open questions

- **Which CLI runs this repository's agent in its task.** (a) Claude Code, as for every one of this agent's
  repositories today: woken by a message, its subscription brokered; (b) Antigravity CLI itself, so the adapter is used daily
  where it is built: it declares its resting and its waiting screen, so it is woken, and its sign-in is brokered; the
  agent's own reading then needs another model than the one that built. **Recommended: (a)** for the move, one new
  thing at a time; (b) as an issue of its own afterwards.

## To be checked

- That porelo-project's `project.yml` lets the task reach the hosts named under *Plan*.
- Where the task's agent finds what `.AGENTS.md` gives it here (its name, the room in place of the channel, the
  operator's language and time zone): `.AGENTS.md` is not committed, so it is not in the task.
- That the outside reader can fetch `porelo/<task>` from the host's forge, not only from GitHub.
- The way back, rehearsed once: the task's open work fetched from the host's forge into the checkout here.
