# AG12 — The Clean-Up Says When Nothing Was Rented

```json
{
  "$schema": "issue-1.json",
  "id": "AG12",
  "title": "The Clean-Up Says When Nothing Was Rented",
  "state": "Accepted",
  "size": "-",
  "priority": "Medium",
  "rank": 2,
  "blockedBy": [],
  "covers": "a leg that failed before resolving the machine tooling rented nothing, and its clean-up says so instead of failing on a missing class"
}
```

**What must be true.** An acceptance leg that fails before it resolved the machine tooling (a download that does not
answer, a checkout that fails) has rented nothing, and its *Delete what this run created* says so and passes, instead
of failing with a missing class on a classpath that was never resolved. A leg that did resolve the tooling sweeps
what it created as today, and that step still fails loud when the sweep cannot run.
