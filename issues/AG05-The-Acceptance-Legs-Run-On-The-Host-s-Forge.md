# AG05 — The Acceptance Legs Run On The Host's Forge

```json
{
  "$schema": "issue-1.json",
  "id": "AG05",
  "title": "The Acceptance Legs Run On The Host's Forge",
  "state": "Implementing",
  "size": "S",
  "priority": "Medium",
  "rank": 5,
  "blockedBy": ["AG04"],
  "covers": "the acceptance legs on the host's pool again, once it lends a forge job a machine by kind",
  "openQuestions": 0
}
```

**How it stands.** Waits for machines asked for by kind from the host's pool on the forge: the host's runner telling
every job where the pool is, and `porelo-machines` taking the pool from that (both outside this repository, see the
plan).

**Why this size.** one workflow, `build.yml`'s `acceptance` job and the step that hands it the packages, no contract
another repository reads; the run that proves it needs a host with the forge and the pool on, which is most of the
time. The shape is porelo-claude-code's, built there first and copied here.

**Estimate.** S: 27 min, the measured median of S over all repositories (n=167). Expect more for the host's run and
for settling how a leg gets the packages on the host's forge (see *What makes it uncertain*); the number stays.

**What must be true.** On the host's forge, `porelo-agy`'s Build runs its acceptance legs again, each on a
machine of its kind from the host's pool, so a change made in a task is tested on Ubuntu and Fedora before it reaches
the central forge. The same `build.yml`, with no edit between the forges, still rents a server on the central forge.

## Why

While the repository moves, its Build on the host's forge leaves out every check that rents a machine
([AG04](README.md#ag04)), because the host's pool does not yet lend a forge job a machine by kind. The central
forge still runs them, so a change is tested there only after the gathered push.

## The checks it switches on

- `build.yml`, job `acceptance`: *Acceptance on ubuntu* and *Acceptance on fedora*, each with its *Delete what this
  run created*, on a pool account instead of a rented machine.
- `update.yml` is not one of them: it updates the central forge only and stays off on the host's forge.

## Plan

Read in today's `.github/workflows/build.yml` (job `acceptance`, the `build` job's upload), porelo-buildtools'
`porelo-machines` (`Main` `acceptance` and `sweep`, `doc/machines.md`) and porelo-host's `doc/local-forge.md` and
`roles/vm_pool/README.md`.

- **What stands today.** The `acceptance` job runs only where `github.server_url` is GitHub's, on a push to `main`, a
  tag or a dispatch, with no proof of the tree. Its leg calls `porelo-machines acceptance --os <os> --kind amd64`
  with `--package porelo-agent-agy`, plus `--candidate candidate` on a tag or where `PORELO_PUBLISH` is not
  `true`, and `--cucumber .`; *Delete what this run created* calls `sweep --mine`. The `proof` and `release` jobs
  run only where `PORELO_PUBLISH` is `true`, so on the host's forge the legs run on every push to `main` and
  nothing is released after them.
- **What the host offers today.** Where the pool is on, every job gets the pool file read-only at a fixed path and
  the pool's run key as the organization's secret `PORELO_POOL_SSH`; `porelo-machines acceptance --pool <file>`
  takes one run account per run, waits then refuses beyond the pool's size, and resets the account when it is given
  back, a killed run's at its next rental. The job's `ubuntu-24.04` is a label the forge's runner has.
- **Blocked outside this repository by** (named here, not built here):
  - porelo-host: the forge's runner tells every job where the pool is, `PORELO_MACHINES_POOL`, wherever the pool is
    on; on the central forge the variable does not exist.
  - porelo-buildtools: `porelo-machines acceptance` takes the pool from `PORELO_MACHINES_POOL` when `--pool` is not
    given (empty counting as unset), and `sweep --mine` does nothing where the run took a pool account. No issue in
    porelo-buildtools holds this yet; its larger idea of providers behind one interface would serve too, with other
    variable names.
  - porelo-host: a pool VM handed out after a revert has the host's time, so the package manager does not refuse a
    repository published since the VM's snapshot.
- **Steps:**
  1. Drop `&& github.server_url == 'https://github.com'` from the `acceptance` job's condition and its comment; the
     events stay as they are, so the host's forge runs the legs on a push to `main`, a tag and a dispatch, as the
     central forge does.
  2. Pass `PORELO_POOL_SSH: ${{ secrets.PORELO_POOL_SSH }}` to *Acceptance on ...*; it is empty and unused on the
     central forge, and `PORELO_MACHINES_API` and `PORELO_MACHINES_SSH` are empty and unused on the host's forge.
     The leg's line stays as it is: no `--pool` and no provider in the workflow.
  3. Hand the leg the packages the run built on the host's forge, where `PORELO_PUBLISH` is `false` and the leg
     installs them with `--candidate`: today the `build` job uploads them only where `PORELO_PUBLISH` is `true`, since
     `actions/upload-artifact` v7 and `actions/download-artifact` v8 refuse every server but GitHub's. The route is the
     one found under *To be checked*; the central forge keeps GitHub's actions.
  4. *Delete what this run created* stays `sweep --mine` under `if: always()`; on the host's forge it does nothing,
     and the lease's close gives the account back after a failed leg too.
- **Red first:** `WorkflowRulesTest` gains `theAcceptanceLegsRunOnEveryForge`, reading `build.yml` through
  `WorkflowSteps`: the `acceptance` job's condition names no server, and *Acceptance on ...* passes `PORELO_POOL_SSH`;
  it fails on today's file. It also holds the cache pair: one key saved and restored, the restore's fallback to the
  run and its `fail-on-cache-miss`, under the condition the leg passes `--candidate` on. Then a push's Build on the
  host's forge whose `acceptance` job is skipped, and the same commit after the steps runs both legs. The workflow
  check (`workflows.yml`) and `check-providers` stay green on the changed file.
- **Level:** a unit test over the workflow file, then the Build itself on a host with the forge and a pool of an
  `ubuntu` and a `fedora` VM, and the same commit's Build on the central forge, which still rents.

## Acceptance

- Seen to fail first: a push's Build on the host's forge that skips the `acceptance` job.
- The same commit's Build on the host's forge runs both legs green on the host's pool, and the step that deletes what
  the run created leaves the pool as it found it, after a failed leg too.
- The same commit's Build on the central forge rents its legs as before, and `build.yml` names no provider and no
  pool path.

## What makes it uncertain

- It cannot start before the two changes outside this repository above; if porelo-buildtools takes the larger way,
  one description of providers per kind as a variable, step 2 passes other secret names, which may move the size.
- The packages' route on the host's forge, the Actions cache (see *To be checked*), is unproven there: if the
  forge's cache refuses the pinned action or does not hand a job the entry an earlier job of the run saved, the
  route becomes a rebuild in the leg's job, which could make this M.
- The legs now run twice per change, once on the host's forge and once on the central forge after the gathered push
  (the central `proof` asks only the central forge's runs), so each change spends the model's tokens twice.
- The pool is shared with porelo's and the other adapters' legs; a leg that waits for an account spends its 30
  minutes waiting.
- A pool VM reverted to a snapshot whose clock is behind refuses `porelo`'s repository as *not valid yet*, until the
  host sets the VM's time.

## To be checked

- How a leg gets the packages its run built on the host's forge: settled on the Actions cache, which both forges
  answer. The `build` job saves `candidate/` under `candidate-<run id>-<attempt>` with `actions/cache/save`, the
  leg restores it with `actions/cache/restore`, falling back to the run's last attempt for a re-run of the failed
  legs alone, and `fail-on-cache-miss`, so a missing entry fails the leg instead of installing nothing; both run
  under the leg's own condition for `--candidate`, a tag or `PORELO_PUBLISH` not `true`. `actions/upload-artifact`
  stays for the release job on GitHub. Still to see on the host's forge: that its cache answers the pinned v6.1.0
  and that the entry reaches the `acceptance` job.
- That the host's VM pool holds an `ubuntu` and a `fedora` VM: a rented server once reported a pool of 0 KiB.
- That a forge job reaches the pool VMs over ssh, for the leg's install and for the Cucumber half run from the job
  as a person at a terminal.
- That a pool VM reaches the distributions' mirrors, GitHub's release downloads for the pinned Antigravity CLI,
  Google's API, and Artifactory for `porelo`, which a leg still takes from there with `--candidate`, since the
  candidate holds only this repository's package.
- That `PORELO_POOL_SSH` reaches the project's organization on the forge.
- That `GOOGLE_API` is set on the forge, verified by the operator, as the shared rule on separate access
  lists asks.
