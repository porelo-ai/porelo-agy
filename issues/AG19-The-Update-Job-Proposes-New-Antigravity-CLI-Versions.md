# AG19 — The Update Job Proposes New Antigravity CLI Versions

```json
{
  "$schema": "issue-1.json",
  "id": "AG19",
  "title": "The Update Job Proposes New Antigravity CLI Versions",
  "state": "Accepted",
  "size": "-",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "the weekly update job follows the Antigravity CLI as porelo-claude-code's follows Claude Code: it finds a new release, pins it, runs the unit suite and both acceptance legs on the candidate packages, and opens the pull request, instead of only re-checking the pinned digest"
}
```

**What must be true.** When Antigravity CLI publishes a release newer than the pin, the weekly update job proposes it:
it finds the release, pins its version and digest, builds and tests the packages, runs both acceptance legs on them,
and opens a pull request, or says why it did not. A release that fails a leg is never pinned. The job is named
"Follow Antigravity CLI", which `build.yml`'s `proof` job already expects.

**Why.** Filed by the operator's decision of 2026-11-11 on [AG06](README.md#ag06): the sign-in check of [AG06](README.md#ag06) runs in
this job's legs, and today `update.yml` only re-checks the pinned digest. `pom.xml` already holds the
`porelo.release.pin.agy.*` properties such a job reads.
