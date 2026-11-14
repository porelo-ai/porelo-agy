# AG28 — The Docs Say What The Code Does

```json
{
  "$schema": "issue-1.json",
  "id": "AG28",
  "title": "The Docs Say What The Code Does",
  "state": "Review",
  "size": "S",
  "priority": "Highest",
  "rank": 1,
  "blockedBy": [],
  "covers": "every sentence of the user-facing documentation checked against the code on main, and each one the code does not bear out fixed"
}
```

**Why this size.** Documents only, no behavior change and no contract: one reading of the READMEs, `doc/` and the
changelog's latest section, each finding checked in the code and fixed in its page.

**Estimate.** S: 27 min, the measured median of S over all repositories (n=167).

**What must be true.** Every command, flag, path, default, state, message and behavior the user-facing documentation
of `porelo-agy` names is one the code on `main` has.

## Plan

- A reader reads every tracked `README.md`, `doc/` but `doc/decisions.md`, and the latest section of
  `CHANGELOG.md` against the code, and lists each sentence the code does not bear out, with the page's line, the
  claim and the code's line that contradicts it.
- Each finding is checked in the code by this repository's agent; only a confirmed one is changed.
- Where the code is right, the page is fixed. Where the page is right and the code is wrong, that is filed as a bug
  of its own, in Backlog with its estimate, and the page stays.
- No behavior changes. The test level is the tests tagged `documents`, with `check-citations`, `check-readmes`
  and `check-doc-site`.

## Acceptance

- The hand-over names how many claims were checked, how many findings were confirmed, how many pages were fixed
  and how many bugs were filed.
- The tests tagged `documents` and the document checks pass on the commit.

## What the reading found

- 78 claims checked, 1 confirmed, 1 fixed, 0 filed as bugs.
- Not checkable here, left as they stand: what Porelo answers in the attended sign-in walk, and Google's terms.
- `README.md`: the adapter depends on Porelo's agent API and its wire artifacts, not the API
  alone, as the pom shows.
