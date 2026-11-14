# AG27 — The Update Checks The Sign-In Clients

```json
{
  "$schema": "issue-1.json",
  "id": "AG27",
  "title": "The Update Checks The Sign-In Clients",
  "state": "Idea",
  "size": "-",
  "priority": "Medium",
  "rank": 3,
  "blockedBy": [],
  "covers": "the weekly update job stops a new Antigravity CLI release whose binary no longer holds the OAuth client IDs and secrets the login wrapper collects, so a release that moves them is not pinned"
}
```

**What must be true.** When the weekly update job proposes a new Antigravity CLI release, a gate finds that its
binary still holds the OAuth client IDs and client secrets the login wrapper collects for a sign-in. A release that
moved or dropped them stops its pin bump and says so. Today the acceptance legs sign in with an API key, so such a
release passes them and a person's sign-in breaks only after the pin has landed.
