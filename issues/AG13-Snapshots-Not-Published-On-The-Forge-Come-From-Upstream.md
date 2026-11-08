# AG13 — Snapshots Not Published On The Forge Come From Upstream

**Status:** Review.
**Size:** S - the change `porelo-claude-code`, `porelo-pi` and `porelo-omp` carry since 2026-11-08, carried over.

**What must be true.** On a forge whose registry holds only its organization's packages, the other Porelo snapshots
come from the anonymous repository `MAVEN_UPSTREAM_SNAPSHOTS_URL` names, and the forge's token goes to the forge's
registry alone.

## Plan

- `settings.xml` gains the `upstream` profile, activated by `MAVEN_UPSTREAM_SNAPSHOTS_URL`, its repository id
  `porelo-upstream-snapshots` without a `<server>`.
- Every workflow passes `MAVEN_UPSTREAM_SNAPSHOTS_URL: ${{ vars.MAVEN_UPSTREAM_SNAPSHOTS_URL }}` beside
  `MAVEN_SNAPSHOTS_URL`, and the `pinned-jdk` action's comment names both.
- `UpstreamSnapshotsTest`, the other adapters' copy, guards the token.
- A tag's `MAVEN_ARGS` becomes `-P!standard,!forge,!upstream`, which the test also holds.

## Acceptance

- `UpstreamSnapshotsTest` passes, and goes red when the upstream repository takes the forge's id, when the profile's id
  changes, when its URL names the forge's, and when a tag would read snapshots.
- From an empty local repository, with `MAVEN_SNAPSHOTS_URL` naming a repository without Porelo's snapshots, the build
  resolves with `MAVEN_UPSTREAM_SNAPSHOTS_URL` set and fails without it.

## Measured

- The guards, each broken on purpose and restored: red in every case.
- `dependency:resolve` from an empty local repository, `MAVEN_SNAPSHOTS_URL` naming Maven Central: with
  `MAVEN_UPSTREAM_SNAPSHOTS_URL` naming Central's snapshots it passed, 11 files from `porelo-upstream-snapshots`;
  without it, and with `-P!standard,!forge,!upstream`, it failed.

## To be checked

- The forge's Build green after the move, with the organization's `MAVEN_UPSTREAM_SNAPSHOTS_URL`.
