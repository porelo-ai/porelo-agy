# AG19 — The Update Job Proposes New Antigravity CLI Versions

```json
{
  "$schema": "issue-1.json",
  "id": "AG19",
  "title": "The Update Job Proposes New Antigravity CLI Versions",
  "state": "Review",
  "size": "M",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "the weekly update job follows the Antigravity CLI as porelo-claude-code's follows Claude Code: it finds a new release, pins it, runs the unit suite and both acceptance legs on the candidate packages, and opens the pull request, instead of only re-checking the pinned digest",
  "openQuestions": 0
}
```

**Why this size.** one workflow file rewritten after the Claude Code and Oh My Pi update jobs, about 330 lines, and
its tests ported; no Java and no change in another repository. Porelo's release tool already follows a named pin:
`upstream-version`, `update` and `check-pin` all take `--pin agy`, and this pin's `github-latest` upstream and
`github-asset` digest are sources it already reads. The shared workflow check already lets the job `update` of
`update.yml` write `contents` and `pull-requests`. M, not S, because it rents machines and is proven only by a real
dispatched run on GitHub.

**Estimate.** M: 53 min, the measured median of M over all repositories (n=113).

**What must be true.** When Antigravity CLI publishes a release newer than the pin, the weekly update job proposes it:
it finds the release, pins its version and digest, builds and tests the packages, runs both acceptance legs on them,
and opens a pull request, or says why it did not. A release that fails a leg is never pinned on `main`. The job is
named "Follow Antigravity CLI", which `build.yml`'s `proof` job already expects, so the Build of the merged tree takes
its legs instead of renting them again. A week with no new release still checks the pinned digest, as today.

**Why.** Filed by the operator's decision of 2026-11-11 on [AG06](README.md#ag06): the sign-in check of [AG06](README.md#ag06) runs in
this job's legs, and today `update.yml` only re-checks the pinned digest. `pom.xml` already holds the
`porelo.release.pin.agy.*` properties such a job reads.

## How it stands

- `update.yml` ("Verify pinned Antigravity CLI artifact", job `update`, ubuntu-26.04, `contents: read`) runs the unit
  suite and `check-pin --pin agy --definition target/classes/agent/agy.yaml`. It proposes nothing.
- `pom.xml` pins `agent.cli.version` and `agent.cli.sha256` through `porelo.release.pin.agy.*`: upstream
  `github-latest` on the CLI's GitHub releases, digest `github-asset` of `agy_cli_linux_x64.tar.gz`. It has no
  `porelo.release.min-age`; the Claude Code, Oh My Pi and pi adapters wait 3 days.
- In the release tool, `upstream-version --pin` reads the pin and honours `porelo.release.min-age` for it (the
  `github-latest` source lists the releases to find the newest that has stood the age, drafts and pre-releases left
  out); `update <version> --pin` writes the version and the asset's digest into the pom, bumps the module's patch
  version and adds a changelog line labelled "Antigravity CLI", since `CHANGELOG.md` exists.
- `build.yml`'s acceptance leg uses `GEMINI_API_KEY` from the secret `GOOGLE_API`, `PORELO_E2E_MODEL:
  gemini-3.6-flash-low` and `PORELO_E2E_REFUSED_KEY`, not the OpenRouter key the other adapters' jobs use.
- `WorkflowRulesTest` guards only `build.yml`; `WorkflowTimeLimitTest` and `SnapshotTokenTest` already list
  `update.yml update` and keep passing as long as the job's id stays `update` and it has `timeout-minutes`.

## Plan

- **`.github/workflows/update.yml`**, rewritten on porelo-omp's job (a GitHub-hosted upstream like this one), job id
  `update`, name "Follow Antigravity CLI", workflow name "Agent version update":
  - Triggers: the weekly cron kept on its own weekday; `workflow_dispatch` with `version`. No `channel` input: the
    upstream has none.
  - `runs-on: ubuntu-24.04` (the Build's glibc, since it builds the native binary), `timeout-minutes: 105`,
    `if: vars.PORELO_PUBLISH == 'true'`, `concurrency: update`, job permissions `contents: write` and
    `pull-requests: write`.
  - Detect: `upstream-version --pin agy [--upstream "$VERSION"]` with the job's `GITHUB_TOKEN`; the plan step as in
    the others (update, rollback, waiting, nothing to do).
  - No update: `check-pin --pin agy` runs as today, so the weekly digest check is not lost.
  - Apply: `update "$TARGET" --pin agy`; then the unit suite, `check-pin --pin agy --definition
    target/classes/agent/agy.yaml`, `-Pnative,dist verify`, `compare-bills target/porelo-agent-agy.cdx.json <published
    bill> --expect-moved agy`, the major-moved stop, the verified-tree artifact, the key gate on `GOOGLE_API`, then
    `Acceptance - ubuntu` and `Acceptance - fedora` with `--package porelo-agent-agy --cucumber . --candidate target`,
    the Build leg's environment plus `PORELO_E2E_EXPECT_CLI` = the target, legs `upd-ubuntu` and `upd-fedora`, each
    swept after.
  - Ask: the same "Open the pull request" and "Merge, or say why not" steps, branch `update/antigravity-cli-<version>`,
    title "Pin Antigravity CLI <version>", the body naming the digest GitHub publishes for the release asset; the
    final "Stop" step.
- **`pom.xml`**: `porelo.release.min-age` = `3d`, as the other adapters.
- **Tests**, ported from porelo-omp: `WorkflowRulesTest` gains "no workflow builds packages without their tests"
  (`build.yml` and `update.yml`, neither skipping tests) and "the update stops without the key"; `UpdateRequestTest`
  (a failed job without a STOP neither says green nor merges, a cancelled job does not merge, a green one says so and
  merges, a branch left by a closed request is deleted before the push, only the push holds the token); a cleanup test
  that a failing sweep fails its step.
- **`doc/decisions.md`**: the *Artifact checks* row and section say the weekly job follows the release and what stops
  it.
- **Red first**: "the update stops without the key" and the `UpdateRequestTest` cases find no such step in
  `update.yml` and fail.
- **Level**: unit tests that run the job's shell steps against stubs; then one dispatched run on GitHub with a named
  version that opens a pull request, and one run that finds nothing to do.

## Acceptance

- A dispatch with `version` set to a release other than the pin pins it, passes the unit suite, the pin check, the
  packages, the bill comparison and both legs on the candidate packages, and opens a pull request that says green.
- A run with nothing newer, or nothing that has stood 3 days, opens nothing, still checks the pinned digest and ends
  green.
- A run whose leg fails, or whose key is empty, opens a request that says it is not ready and why, merges nothing and
  ends red.
- After the request is merged, the Build of `main` finds its legs proven by "Follow Antigravity CLI" and rents none.
- No machine is left after any run.

## What makes it uncertain

- It is the first scheduled job here that rents machines; two legs on the Gemini key in one week may hit a quota the
  Build's single run never did.
- The release tool's handling of this pin's tags (no leading `v`) is read, not yet run.
- No gate here checks that a new release still holds the OAuth client ids and secrets the credential extractor reads at
  login; the legs run with an API key, so a release that moves them would pass. That is a check of its own, not part
  of this issue.

## To be checked

- Measured on 2026-11-12: `upstream-version --pin agy` finds 1.5.3 as the newest release that has stood 3 days
  (1.5.7 is newer), and `update 1.5.3 --pin agy --dry-run` writes the version, the asset's digest
  `1b4850599cce73a17f94ffb422df3bf9531531b77dbddc40c1a435e0c2c16807`, the module's patch version and the changelog
  line; tags without a leading `v` are read. The published bill
  `porelo-dist-deb/pool/main/p/porelo-agent-agy/porelo-agent-agy.cdx.json` answers 200.
- The repository's settings, read by the coordinator through the runs: the legs sign in with `GOOGLE_API`, an API
  key; `PORELO_UPDATE_AUTO_MERGE` is unset, so a green run asks for the merge rather than merging.
- Run 43285715027 on 2026-11-12, dispatched with `version=1.5.3`: green; it opened pull request #1 from
  `update/antigravity-cli-1.5.3` with one commit pinning 1.5.3 at the digest above, the patch version 0.1.1-SNAPSHOT
  and the changelog line; its text says "Green." over the unit suite, the pin check, both packages, the unchanged
  component set and the legs on Ubuntu and Fedora against these packages; nothing merged.
- After the operator merged it, the Build of `main` (43291522045) found its legs proven by "Follow Antigravity CLI",
  rented none, and published the snapshot with 1.5.3.
- Run 43294210209 the same day, dispatched without a version: green and changed nothing; it found 1.5.7 newer than
  the pinned 1.5.3 but not yet three days old ("It is taken from 2026-11-15T15:18:30Z on"), so pin, tests, legs and
  request were skipped.
- Not read here: a run whose leg fails or whose key is empty; that it leaves no machine rests on the sweep step the
  runs end with.
