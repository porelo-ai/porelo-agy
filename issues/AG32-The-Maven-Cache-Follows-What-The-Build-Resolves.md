# AG32 — The Maven Cache Follows What The Build Resolves

```json
{
  "$schema": "issue-1.json",
  "id": "AG32",
  "title": "The Maven Cache Follows What The Build Resolves",
  "state": "Backlog",
  "size": "S",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "the Build's Maven cache is saved again when the versions it resolves move, so a Build after a cache hit fetches nothing from Maven Central"
}
```

**Why this size.** One cache step in `.github/actions/pinned-jdk/action.yml` and a check in the Build; no code that
ships.

**Estimate.** S: 28 min, the measured median of S over all repositories.

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

- **Red first:** a Build step after the Maven calls fails when its log has a download from Central while the cache
  was restored exactly; seen to fail on the present key.
- The key names what the Build resolves, not only the poms here: the hash of the resolved dependency list (written by
  `dependency:list` offline from the restored cache, then online once), or the restore keeps a prefix key and the
  save takes the run's own key, so a moved version is saved once and the next Build hits it.
- Kept identical with the other adapter repositories, whose action is the same file.

## Acceptance

- Two Builds in a row on one commit: the second downloads nothing from Central; the step is seen to fail on the old key.
