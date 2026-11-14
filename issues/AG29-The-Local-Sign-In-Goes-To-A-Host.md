# AG29 — The Local Sign-In Goes To A Host

```json
{
  "$schema": "issue-1.json",
  "id": "AG29",
  "title": "The Local Sign-In Goes To A Host",
  "state": "Backlog",
  "size": "M",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "on the person's machine the adapter lists each Antigravity sign-in it finds - provider, kind, account where known, age and expiry where known, and whether it can be carried, never its value - and hands a chosen one to Porelo, which carries it into a rented host's vault",
  "openQuestions": 2
}
```

**Why this size.** `AgyCredentialExtractor` exists; a finder, `AgyFindings`, is new. Each route below is read, told apart and tested against a fixture file; no new contract of
the adapter's own, the fields come from Porelo's half.

**Estimate.** M: 53 min, the measured median of M over all repositories (n=113).

**What must be true.** A person with Antigravity signed in on their own machine carries that sign-in into a rented host's
vault from the window and is not asked to sign in a second time there. The adapter's half: for each route below it
says provider, kind, the account where the file names one, age and expiry where the file holds them, and whether it
can be carried, with no value in what it says; and taking one hands its value to Porelo through the extractor alone,
never through a file, a command line or a log.

Filed 2026-11-14 on the operator's order, under ST362 "Credentials carried to a host". Agent Core files Porelo's half,
the fields and the carrying by stdin or environment; Agent Frontend files the window's dialog.

## Plan

- `antigravity-oauth-token` in the definition's `config_dir`: the consumer sign-in with its expiry; carriable to a host whose pinned CLI holds the OAuth client it was granted to, and said not carriable otherwise.
- The finder fills the fields Porelo's half adds to a finding; the extractor is the one an import already uses.
- Tests: one fixture per route, each finding's fields asserted, and a test that no finding's text holds any value
  of its fixture. The definition's `config_dir` stays the place read.

## Acceptance

- Each route above is found and said as listed, with its carriable answer, by a test.
- No finding holds a value of its fixture.
- With Porelo's half in, a sign-in found on the person's machine is in the host's vault and a task there runs on it.

## To be checked

- **Porelo's half.** The fields (account, age, expiry, carriable) and the carrying are Agent Core's issue; it goes in
  `blockedBy` once filed.
- **Two machines on one sign-in.** An OAuth sign-in whose refresh token is replaced at each renewal works on one
  machine at a time: the first to renew signs the other out. Whether a carried sign-in is said to sign the person's
  machine out, or is carried only as a kind that does not rotate, is the operator's call.
