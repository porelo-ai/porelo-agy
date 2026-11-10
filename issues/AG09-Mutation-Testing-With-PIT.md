# AG09 — Mutation Testing With PIT

```json
{
  "$schema": "issue-1.json",
  "id": "AG09",
  "title": "Mutation Testing With PIT",
  "state": "Accepted",
  "size": "-",
  "priority": "Medium",
  "rank": 2,
  "blockedBy": [],
  "covers": "mutation testing by hand: a `pit` profile outside CI with no threshold, and a report sorting survivors into real gaps and harmless ones"
}
```

**What must be true.** A person can run mutation testing over this repository by hand and read which changes to the
code no test notices, sorted into real gaps and harmless ones.

- A `pit` profile, run by hand: not in CI and with no threshold.
- PIT and its JUnit 5 plugin are pinned.
- It runs on a VM where tests call real tools.
- A report sorts the survivors into real gaps and harmless ones.

**The model.** The `pit` profile in the messaging repository's `pom.xml`.
