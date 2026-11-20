# AG32 — The Maven Cache Follows What The Build Resolves

```json
{
  "$schema": "issue-1.json",
  "id": "AG32",
  "title": "The Maven Cache Follows What The Build Resolves",
  "state": "Review",
  "size": "XS",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "the Build restores the Maven cache by a key holding the run and saves it in one job when that job fetched a jar or pom, so the next Build asks Central for nothing; the shared form, proven by the first two Builds after the push"
}
```

**Why this size.** The shared form copied: two actions and one step in the Build; no code that ships.

**Estimate.** S: 28 min, the measured median of S over all repositories, as filed; built as XS, a copy.

**What must be true.** A Build whose cache was restored asks Maven Central for nothing, so a Central that answers 429
cannot fail it.

## Why

The `pinned-jdk` action restores and saves the runner's local Maven repository under `maven-<os>-<hash of every pom.xml>`. The versions
the Build resolves come from the parent pom and the imported BOMs too, which change without a pom here changing, and
`actions/cache` never saves over a key it restored exactly. Measured in porelo-claude-code's Build of 2026-11-15 (job
"Build and unit tests"): "Cache restored from key: maven-Linux-3ef0684d…", then "Cache hit occurred on the primary
key …, not saving cache", and 26 downloads from Central in the same job (jackson-core, -databind and -annotations in
2.18.3, 2.20 and 2.22, commons-codec 1.20.0). Each later Build fetches them again until a pom here changes.

## Plan

- Taken over from the shared form, the same in every Porelo repository with a Maven Build: `pinned-jdk` restores the
  local Maven repository under `maven-<os>-<hash of every pom.xml>-<run id>-<attempt>`, falling back to the newest
  with the same poms and then to any, outputs that key, and touches a marker after the restore.
- The new action `.github/actions/maven-cache-save` saves under that key when a jar or pom changed status after the
  marker (`find -cnewer`: Maven dates a download by the server's Last-Modified, so modification time finds none),
  and says either "fetched after the restore" or "nothing fetched after the restore".
- One job of each Build run saves, as its last step and even after a failure: "Build and provider-independent checks". The other jobs
  and workflows only restore.
- **Level:** the Build on GitHub; a workflow step gets no test of its own. YAML parsed, `check-actions` and the
  document tests pass locally.

## Acceptance

- The first Build after the push logs "fetched after the restore, among others: ..." and saves; the next logs
  "nothing fetched after the restore: nothing saved", and its Maven output has no "Downloading from central".
