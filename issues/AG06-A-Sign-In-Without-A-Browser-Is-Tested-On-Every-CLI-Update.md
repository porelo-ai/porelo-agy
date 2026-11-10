# AG06 — A Sign-In Without A Browser Is Tested On Every CLI Update

```json
{
  "$schema": "issue-1.json",
  "id": "AG06",
  "title": "A Sign-In Without A Browser Is Tested On Every CLI Update",
  "state": "Idea",
  "size": "-",
  "priority": "Medium",
  "rank": 4,
  "blockedBy": [],
  "covers": "the sign-in at a terminal without a browser, checked on every CLI update so a release that drops it fails its pin bump"
}
```

**What must be true.** `porelo vault login` for Antigravity CLI, started over `ssh -t` in a host account with no browser
opener set, shows its link and waits for the pasted authorization code, and the link it shows names no loopback address
as its redirect; the weekly update job runs that login against each new CLI release, and a release that no longer does
so fails its pin bump, so a person signing in from a host is never left with a login that waits for a callback nobody
can reach.
