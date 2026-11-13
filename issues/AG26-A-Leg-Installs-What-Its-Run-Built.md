# AG26 — A Leg Installs What Its Run Built

```json
{
  "$schema": "issue-1.json",
  "id": "AG26",
  "title": "A Leg Installs What Its Run Built",
  "state": "Review",
  "size": "S",
  "priority": "High",
  "rank": 2,
  "blockedBy": [],
  "covers": "every acceptance leg of a run that publishes nothing - a dispatch, a tag - installs the packages that run built, so its result speaks for its own commit; only a push to main, which publishes its snapshot first, installs from the package repository"
}
```

**Why this size.** S: one workflow, `.github/workflows/build.yml`, in four places, and one unit test in
`WorkflowRulesTest`; no contract another repository reads.

**Estimate.** S: 27 min, the measured median of S over all repositories (n=167).

**What must be true.** A green acceptance leg says that the packages of its own commit pass that commit's
scenarios, whatever started the run.

## Why

`build.yml` gives the legs the run's own packages (`--candidate candidate`) only on a tag or where
`PORELO_PUBLISH` is not `true`, but publishes a snapshot only on a push to main. A `workflow_dispatch` on GitHub
therefore installs whatever `porelo-agent-agy` was published last and runs the dispatched commit's scenarios against it. A
commit that changes the install and its scenario together fails such a run although both are right, and a commit
that breaks the install passes it. Measured: a dispatch on a commit that moved the agent's binary to a root-owned
path installed the snapshot published before it and failed the two scenarios that call the new path; the same
image built from the commit's own definition on a local VM held the path. That was measured on an adapter
built by the same workflow; this one takes the same branch.

## Plan

- `build.yml`: the condition for the run's own packages becomes "this run does not publish a snapshot",
  `"!(vars.PORELO_PUBLISH == 'true' && github.event_name == 'push' && github.ref == 'refs/heads/main')"`, the
  same text in the build job's two candidate steps (copy and cache save) and the leg's restore step. The leg's
  shell takes the published package only when `PORELO_PUBLISH` is `true`, `GITHUB_EVENT_NAME` is `push` and
  `GITHUB_REF` is `refs/heads/main`, and passes `--candidate candidate` otherwise.
- `WorkflowRulesTest`, red first: a new test runs the leg step with a stubbed `java` for four triggers and reads
  the call - a dispatch on main, a tag push and a push to main where `PORELO_PUBLISH` is `false` pass
  `--candidate candidate`; a push to main where it is `true` passes `--package porelo-agent-agy` without it. The
  dispatch case fails against today's `build.yml`. The existing test's verbatim hold of the old leg line and
  condition is deleted, and the three candidate steps are held to one condition.
- **Level:** unit (`WorkflowRulesTest`); the next dispatch's legs show it on GitHub.

## Acceptance

- The new test passes, and failed against today's `build.yml` on the dispatch case.
- A dispatched Build's leg log shows the run's own package version installed, not the last published one.
