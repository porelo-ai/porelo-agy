# AG30 — A Rotating Sign-In Is Made On The Host

```json
{
  "$schema": "issue-1.json",
  "id": "AG30",
  "title": "A Rotating Sign-In Is Made On The Host",
  "state": "Idea",
  "size": "M",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "which of Antigravity's sign-ins rotate their refresh token, measured on the pinned CLI and declared per provider and kind, and how Antigravity signs in on a headless host instead of having a rotating sign-in carried there",
  "openQuestions": 1
}
```

**Why this size.** M: one measurement per sign-in kind on the pinned CLI, and one contract change - the definition says per provider and kind whether its refresh token rotates, which Porelo reads; its tests and the docs move with it.

**Estimate.** M: 53 min, the measured median of M over all repositories (n=113).

**What must be true.** A person who runs Antigravity on a rented host signs in there, pasting the code Google's page shows, and is never offered to carry a sign-in that would sign their own machine out.

## Plan

- **The contract, in words.** Porelo no longer carries a sign-in whose refresh token is replaced at each renewal from the person's machine to a host; the host makes its own, and an API key is carried as before. So the definition says, for each provider and kind it signs in to, whether that refresh token rotates. The key is `refresh_token_rotation`, at the definition's top level: provider, then credential kind, then `does-not-rotate`, written only for a kind measured so. A kind not named there is taken as rotating, and a credential whose reading carries `refreshToken` is then not carried - fail closed.
- **How rotation is measured, never assumed.** First, read in the pinned CLI whether it stores a refresh token its renewal answers with. Then one real renewal per kind on a test sign-in, on a VM: the digest of the refresh token before and after, compared, never the token itself printed. A kind no account here can reach stays unmeasured and is said so, never declared from the CLI's code alone.
- **The kinds, as read in the code today (1.5.3):**
  - Google consumer sign-in, `antigravity-oauth-token`: OAuth with a refresh token, renewed on the host with the installed CLI's client pair Porelo's login picks. Whether Google replaces it at renewal is the measurement.
- **The sign-in on a headless host:**
  - `porelo vault login agy` already runs the CLI's own login: Google's hosted callback page shows a code that is pasted at the terminal (`login.flow: paste-code`). On a host it is the same login, with the client pair the host's pinned CLI holds, which a carried sign-in could not be sure of.
  - What Porelo reads back is the token file and the client candidates, through the extractor an import already uses.
- **The login relayed to the window.** Porelo runs the login in a pty and matches each line of its output, control sequences removed, against `login.relay`: `link` (a Java regex, its first group the link, https to a host the agent or provider declares), `code` for a device code, `answer: line` where the code is typed back as one line. Without `relay` a relayed start of the agent is refused. The patterns are taken from the pinned CLI's login run in a pty with no browser, never from its source alone:
  - `link` and `answer: line`: Google's sign-in link, and the code its hosted page shows, pasted back.
- The findings and the facts stay; a kind declared rotating is still found and described, and Porelo, not the
  adapter, refuses to carry it.
- **Tests:** a definition test that the provider and kind carries its measured answer; the paste-code login is checked against a VM, attended.

## Acceptance

- Each kind above has its rotation answer in the definition, each from a measurement named in `doc/decisions.md`,
  or is said unmeasured.
- On a VM without a browser, the sign-in made on the host as above ends in the host's vault, and a task runs on it.
- A test fails when a measured kind's answer is taken out of the definition.
- A login relayed on a VM shows the link (and a device code where there is one) as the patterns take it, and a test fails when a pattern no longer matches the measured output.

## Open questions

- **The accounts for the measurement.** A real renewal per kind needs a test sign-in to each provider; a renewal of a rotating one signs out every other place that holds it. Which accounts may be used, and that none of them is one the person also uses on their own machine, is the operator's call.
