# AG05 — The Acceptance Legs Run On The Host's Forge

**Status:** Idea; waits for machines asked for by kind from the host's pool on the forge.

**What must be true.** On the host's forge, `porelo-agy`'s Build runs its acceptance legs again, each on a machine of
its kind from the host's pool, so a change made in a task is tested on Ubuntu and Fedora before it reaches the
central forge.

## Why

While the repository moves, its Build on the host's forge leaves out every check that rents a machine
([AG04](README.md#ag04)), because the host's pool does not yet lend a forge job a machine by kind. The central
forge still runs them, so a change is tested there only after the gathered push.

## The checks it switches on

- `build.yml`, job `acceptance`: *Acceptance on ubuntu* and *Acceptance on fedora*, each with its *Delete what this
  run created*, on a pool account instead of a rented machine.
- `update.yml` is not one of them: it updates the central forge only and stays off on the host's forge.

## Acceptance

- Seen to fail first: a push's Build on the host's forge that skips the `acceptance` job.
- The same commit's Build on the host's forge runs both legs green on the host's pool, and the step that deletes what
  the run created leaves the pool as it found it, after a failed leg too.

## To be checked

- That the host's VM pool holds an `ubuntu` and a `fedora` VM: a rented server once reported a pool of
  0 KiB.
- That a pool VM reaches the hosts the legs install Antigravity CLI and its packages from, `porelo` among them, which a
  leg may still take from Artifactory with `--candidate`, since the candidate holds only this repository's package.
- That `GOOGLE_API` is set on the forge, verified by the operator, as the shared rule on separate access lists asks.
