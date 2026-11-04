# AG04 — The Build Runs On The Local Forge As On The Central One

**Status:** Review; on a local Forgejo the Build runs green; on the central forge it waits for its push, and what only
a host shows is named below.
**Size:** S - the workflows, the `pinned-jdk` action, `settings.xml` and one test of this one repository, no contract another repository reads; about
2 hours of work once porelo-claude-code has set the shape, plus the runs on a local Forgejo.

**What must be true.** `porelo-agy`'s workflows run green on the local Forgejo and on the central forge on the same
commit, with no edit between them: the machines its legs rent are asked for by kind, and the registry and the
provider are variables of the setup. And a Build on a forge other than the central one whose `MAVEN_SNAPSHOTS_URL` is
empty fails at its start and says so, instead of falling back to Central's snapshots.

The machine kinds are porelo-buildtools' (`porelo-machines acceptance --kind amd64`), before [AG02](README.md#ag02).

## Plan

Read in today's `.github/workflows/`, `.github/actions/pinned-jdk`, `settings.xml` and `pom.xml`. The shape is porelo-claude-code's, with less
of it: there is no `artifactory-smoke.yml` and no `delete-runs.yml`, and `update.yml` opens no pull request.

- **What ties the Build to GitHub today:**
  - `settings.xml` reads snapshots from Sonatype only, through the `standard` profile, active unless
    `-Dporelo.release`. From there come `porelo-bom`, `porelo-agent-api` and `porelo-wire` (`porelo.version`
    0.5.1-SNAPSHOT), `porelo-acceptance-kit` (`porelo.kit.version` 0.5.1-SNAPSHOT), and `porelo-parent`,
    `porelo-release` and `porelo-machines`.
  - In the `build` and the `release` job of `build.yml`, *Publish the packages* takes `vars.JF_URL` and
    `secrets.JF_ACCESS_TOKEN` and *The package is indexed, not merely stored* takes `vars.JF_URL`, whatever the forge.
  - The `proof` job asks GitHub's API for a run that proved the tree (`proven-legs`, `github.token`).
  - The two acceptance legs pass `HETZNER_API` and `HETZNER_SSH` to `porelo-machines acceptance` and `HETZNER_API`
    to *Delete what this run created* (`sweep --mine`), and on `main` and a manual run install the package last
    published to Artifactory (`--package porelo-agent-agy` without `--candidate`), not the one the run built.
  - What needs only step 1's Maven values: `shared-rules.yml` already takes a pull request from the host's forge as
    from inside (`github.server_url`), `workflows.yml` checks the action pins and that every workflow loads, and
    `update.yml` (as `build.yml`) runs `check-pin`, which asks the publisher's release on `api.github.com` and
    pushes nothing.
- **Where Porelo's snapshots come from on the local forge:** the `forge` profile replaces Sonatype, so the forge's
  registry must hold `porelo`'s Maven snapshots before this Build resolves there. `porelo`'s Build on the host's forge
  deploys them there.
- **Steps:**
  1. `settings.xml` gains the `forge` profile and the `porelo-forge` server as porelo-message-sluice has them
     (activated by `MAVEN_SNAPSHOTS_URL`, the token from `PORELO_PACKAGES_READ_TOKEN`), and `standard` stays off where
     `MAVEN_SNAPSHOTS_URL` is set. A profile's activation takes one property only, so `-Dporelo.release` no longer
     turns the snapshot repositories off: a tag's `MAVEN_ARGS` adds `-P!standard,!forge`. Every Maven call passes
     both values through the step's `env`, since a workflow's `env` cannot read a secret: the `pinned-jdk` action's,
     those in `build.yml`, `update.yml`, `workflows.yml` and `shared-rules.yml`, and the `./mvnw verify` that
     `porelo-machines acceptance` starts on the runner for a leg's suite (its `acceptance` profile resolves
     `porelo-acceptance-kit`).
  2. `vars.PORELO_PUBLISH`, checked at the start to be `true` or `false`, switches what only the central forge does:
     Publish and its index check, the `release` job and the `proof` job; where it is `false`, *Not publishing* runs
     and says why. Where the run publishes nothing, the legs install the packages this run built: *The packages this
     run built* and the `FROM=` line of *Acceptance on …* pick the `packages` artifact (`--candidate candidate`), as a
     tag does today, so a leg never tests what Artifactory held before.
  3. The legs ask `porelo-machines acceptance --kind amd64` with `PORELO_MACHINES_API` and `PORELO_MACHINES_SSH`; the
     job learns where the host's pool is from the forge's runner (`PORELO_MACHINES_POOL`, as porelo-host
     proposes it), so the same lines take a pool
     account on the forge and rent on GitHub; *Delete what this run created* takes `PORELO_MACHINES_API` the same
     way. **On the host's forge the `acceptance` job is off while the repository moves:** it runs only where
     `github.server_url` is GitHub's, until the host's pool lends a forge job a machine by kind;
     [AG05](README.md#ag05) switches it on there.
  4. `porelo-release check-providers` joins the Build's checks in the same commit; the provider's names are in the
     workflows only.
  5. The `pinned-jdk` action, the first thing every job that runs Maven does, starts with *The forge names its
     snapshot repository*, as in porelo-claude-code: outside `https://github.com` an empty, white-space-only or unset
     `MAVEN_SNAPSHOTS_URL` stops the job and names the variable, since without it `settings.xml` falls back to
     Central's snapshots without a word; on the central forge it changes nothing. It is the action's first step and
     has no `if:`. `SnapshotTokenTest` comes along as that repository has it, self-contained, and holds this check
     and that no step names the publisher's token.
- **Red first:** today's `build.yml` on the host's Forgejo, which stops at the snapshot resolution or at Publish; and
  `check-providers` on today's tree, which names `build.yml`'s `HETZNER_*` lines;
  and `SnapshotTokenTest` on today's tree, which has no check in the action and the read-only token in no workflow.

## Acceptance

- Seen to fail first: today's `build.yml` on the local Forgejo.
- The same commit's Build green on the local Forgejo (the `acceptance` job skipped, nothing published) and on GitHub (legs
  rented, published as today).
- The kinds' check finds no provider's name in this repository's workflows.
- A Build on the local forge with `MAVEN_SNAPSHOTS_URL` unset is seen to fail at its first step, naming the variable;
  on GitHub the Build is unchanged.

## Measured on a local Forgejo

Forgejo 17 and its runner 13.2.0, pinned as the host's forge pins them, with the host's job image `act-24.04` as root and
a JDK mounted as `JAVA_HOME_25_X64`; the registry held the snapshots this Build resolves, copied in from a Maven cache.

- **Red first:** `check-providers` and `SnapshotTokenTest` on the tree before; the same forge stopped porelo-claude-code's
  Build at Maven's http blocker on a `MAVEN_SNAPSHOTS_URL` of `http://<host>.localhost`, at a denied read that root reads
  through, at the `rpm` the image lacks and at `actions/upload-artifact`, which refuses every server but GitHub's. This
  Build takes the same answers.
- **Beyond the plan:** the packages are uploaded only where `vars.PORELO_PUBLISH` is `true`; *The tools the packages are
  built and read with* installs only what is missing; and each `check-pin` step blanks `GITHUB_TOKEN`, since a forge's
  runner puts its own token there for every step and the check would hand it to GitHub.
- **Green:** the Build, with the `acceptance`, `proof` and `release` jobs skipped, nothing published and every snapshot read from the forge's registry.

- **The reading against `security-audit`, `clean-code`, `java-code-review` and `test-quality` (2026.906.3)** declined three
  findings: that the read-only token's place on each Maven step is not guarded, since a step without it fails on the
  forge with a `401` and gives no write token away; that *Whether this forge publishes* and the index step's condition
  are not guarded, since removing either is a change to the workflow read as such, and a missing variable stops the
  Build today; and that a workflow's `MAVEN_SNAPSHOTS_URL` binding is not guarded, since on a forge the action's first
  step stops a job without it, and on the central forge the fallback reads the same repository.

## What only a host shows

- The registry through the host's proxy and gate, with `MAVEN_SNAPSHOTS_URL` as the host sets it, and the runner's jobs in
  their own containers rather than in the runner's.
- `porelo`'s, `porelo-parent`'s and `porelo-buildtools`' snapshots deployed there by their own Builds, not copied.

## To be checked

- That this repository may read `vars.PORELO_PUBLISH`, `vars.MAVEN_SNAPSHOTS_URL` and `secrets.PORELO_PACKAGES_READ_TOKEN`
  on the central forge, and `secrets.PORELO_MACHINES_API` and `secrets.PORELO_MACHINES_SSH`: the organization's access lists are separate, and only the operator can read them.
