# AG02 — The Repository's Agent Works In A Porelo Task

**Status:** Analyzed; blocked by [AG04](README.md#ag04); waits for its turn in the project's procedure for an agent's move.
**Size:** S - no code here beyond `porelo.yml`, the move itself done by the procedure; about 2 hours of the agent's
work, plus the operator's attended start of the task.

**What must be true.** `porelo-agy`'s agent works in a Porelo task of the project `porelo` on the move's host, reached
in the project's room under the repository's name, and works this issue from start to Review inside that task with
the local forge's build green.

One agent per repository, each move checked before the next, as the project's procedure for an agent's move lays out.

## Plan

Read in today's `porelo.yml`, `settings.xml` and agent definition, porelo's `doc/project-file.md` (*A repository's own
image*) and `doc/running.md` (the agent's layer), and porelo-project's `project.yml`.

- **The image:** today's `porelo.yml` holds the pinned GraalVM, shfmt and the build tools, and `mvnw` brings
  Maven, so the unit suite and the native packages build in the task as porelo-codex's did in a real task. The agent's
  CLI comes from its package's own layer, not from `porelo.yml`. The acceptance legs do not run in a task (no container
  inside it, no rented machine); they run in the forge's Build ([AG04](README.md#ag04)).
- **What the task reaches while it runs:** Maven Central, Sonatype or the forge's registry, `fuinorg.jfrog.io` for
  the skills, `github.com` for `check-citations`, and the forge's address as a host service. That egress is the
  project's, in porelo-project's `project.yml`; the image, the agent's CLI with it, is built on the host outside the
  task's firewall. The forge's registry refuses a task today (the organization is private), so
  until then the task's build reads Sonatype's snapshots.
- **The sub-agent reading:** no sub-agent CLI is in the task's image, since a project file names one
  agent per repository and porelo describes no second sign-in inside a task. Until Porelo has a task that reviews, an agent outside the task reads the pull request's diff against the skills, as today's sub-agent reading does, and posts its
  findings in the room.
- **The CLI:** Claude Code runs the agent in the task, as it runs this repository's agent today: woken by a
  message, its subscription brokered from the vault. Antigravity CLI itself runs it in an issue of its own, afterwards.
- **Steps:**
  0. *A dry run on this machine first*, as for porelo-message-sluice: the task online with GitHub as upstream, its
     Build read through the `github` reader, snapshots from Sonatype; it shows the image, the room, the wake and the
     gate, and only the `forgejo` lines wait for the host.
  1. *Before, from this machine:* a dry run of the task start on the host names each image key's source as
     `porelo.yml`; the image builds; inside it, `./mvnw -s settings.xml verify` and the native packages pass.
  2. *porelo-project (the coordinator):* `repositories.agy.agent` and the nickname `Agy`
     in `project.yml`, and the upstream switched to the host's forge, as the project's procedure for an agent's move lays out.
  3. *The start:* the operator starts the task attended; inside it the agent takes this issue to Review, its push
     through the gate to `porelo/<task>`, the forge's Build read through the `forgejo` reader.
  4. *After the check:* the session on this machine gives up `porelo-agy` (the responsibility line of the shared area
     of `.AGENTS.md` changes through the channel); its VM accounts stay with the repositories not moved yet.
- **Red first:** the move's check from the project's procedure for an agent's move, for
  `porelo-agy` before the move, each line failing by name.

## Acceptance

- The task's image is built from `porelo-agy`'s `porelo.yml`, with no sub-agent CLI in it.
- The task follows `porelo-agy` through the gate; its pushes reach the local Forgejo, and it reads its builds through the
  `forgejo` reader.
- Its build runs on the local forge as on the central one, [AG04](README.md#ag04).
- The room shows it as `Agent Agy`, from the nickname `Agy` in porelo-project's `project.yml`.
- Claude Code works in the task with the operator's subscription from the vault.
- The move's check is red for `porelo-agy` before the move and green after it; the agent's session on this machine and its
  VM accounts are no longer used, and the way back the project's procedure for an agent's move describes is open.

## To be checked

- That porelo-project's `project.yml` lets the task reach the hosts named under *Plan*.
- Where the task's agent finds what `.AGENTS.md` gives it here (its name, the room in place of the channel, the
  operator's language and time zone): `.AGENTS.md` is not committed, so it is not in the task.
- That the outside reader can fetch `porelo/<task>` from the host's forge, not only from GitHub.
- The way back, rehearsed once: the task's open work fetched from the host's forge into the checkout here.
- That the host's inventory names `porelo-agy` in `forge_repositories`: the forge role skips the migration, `main`'s
  protection and the gate's identity where it names none (porelo-host `roles/forge/tasks/main.yml`), and a task
  start then finds no copy of the repository on the host's forge.
- What porelo-message-sluice's dry run showed, each to hold here before the start:
  - That the task's Claude writes `Assisted-by: Claude Code, <model>` and no `Co-Authored-By` line, set in the image or
    the task's settings, since check-commits refuses the other.
  - Whether a fix after the outside reading lands as a new commit on top, so no force push is needed (the sluice's recommendation, a question for the operator with the move), or as an amend pushed with `git push --force` to the
    task's own unmerged branch, which the gate passes on.
  - That a revoked sign-in is renewed with `porelo vault login claude --force` and a stop and start of the task: a
    running task keeps the credential it started with.
  - That the outside reader may join the room from its harness; otherwise it posts its findings in the channel and the
    operator relays them.
