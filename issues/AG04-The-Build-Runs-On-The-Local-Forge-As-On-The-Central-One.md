# AG04 — The Build Runs On The Local Forge As On The Central One

**Status:** Analyzed; waits for Porelo's and the build tools' snapshots in the forge's registry.
**Size:** S - the workflows and `settings.xml` of this one repository, no contract another repository reads; about
2 hours of work once porelo-claude-code has set the shape, plus the runs
on the host's forge.

**What must be true.** `porelo-agy`'s workflows run green on the local Forgejo and on the central forge on the same
commit, with no edit between them: the machines its legs rent are asked for by kind, and the registry and the
provider are variables of the setup.

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
     (activated by `MAVEN_SNAPSHOTS_URL`, the token from `PORELO_PUBLISHER_TOKEN`), and `standard` stays off where
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
- **Red first:** today's `build.yml` on the host's Forgejo, which stops at the snapshot resolution or at Publish; and
  `check-providers` on today's tree, which names `build.yml`'s `HETZNER_*` lines.

## Acceptance

- Seen to fail first: today's `build.yml` on the local Forgejo.
- The same commit's Build green on the local Forgejo (the `acceptance` job skipped, nothing published) and on GitHub (legs
  rented, published as today).
- The kinds' check finds no provider's name in this repository's workflows.

## To be checked

- That the host's Forgejo runner serves `actions/checkout` v7, `actions/upload-artifact` v7,
  `actions/download-artifact` v8 and `actions/cache` v6, and that the `pinned-jdk` action (`JAVA_HOME_25_X64`, `porelo-machines jdk --github`) works there.
- That the runner reaches GitHub where the checks still read it: `check-citations` fetches the other repositories'
  indexes from `raw.githubusercontent.com`, so a cited issue pushed only to the host's forge fails it, and
  `check-pin` asks `api.github.com` without a token, inside the limit of 60 requests an hour per address.
- That the runner has `python3` with `yaml` for `workflows.yml`'s load check.
