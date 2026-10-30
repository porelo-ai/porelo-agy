# AG06 — A Sign-In Without A Browser Is Tested On Every CLI Update

**Status:** Idea.

**What must be true.** `porelo vault login` for Antigravity CLI, started over `ssh -t` in a host account with no browser
opener set, shows its link and waits for the pasted authorization code, and the link it shows names no loopback address
as its redirect; the weekly update job runs that login against each new CLI release, and a release that no longer does
so fails its pin bump, so a person signing in from a host is never left with a login that waits for a callback nobody
can reach.
