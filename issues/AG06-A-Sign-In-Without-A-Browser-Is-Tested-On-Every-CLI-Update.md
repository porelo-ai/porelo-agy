# AG06 — A Sign-In Without A Browser Is Tested On Every CLI Update

```json
{
  "$schema": "issue-1.json",
  "id": "AG06",
  "title": "A Sign-In Without A Browser Is Tested On Every CLI Update",
  "state": "Analyzed",
  "size": "M",
  "priority": "Medium",
  "rank": 4,
  "blockedBy": [],
  "covers": "the sign-in at a terminal without a browser, checked on every CLI update so a release that drops it fails its pin bump",
  "openQuestions": 1
}
```

**Why this size.** one acceptance scenario and no Java; Porelo's acceptance kit already has every step it needs. M
because it is an acceptance step, written against the real CLI's screens and proven on a VM. It assumes the update job
that proposes a new release behind the acceptance legs is built elsewhere (see *Open questions*); built here as well,
it is L.

**Estimate.** M: 53 min, the measured median of M over all repositories (n=113).

**What must be true.** `porelo vault login` for Antigravity CLI, started over `ssh -t` in a host account with no browser
opener set, shows its link and waits for the pasted authorization code, and the link it shows names no loopback address
as its redirect; the weekly update job runs that login against each new CLI release, and a release that no longer does
so fails its pin bump, so a person signing in from a host is never left with a login that waits for a callback nobody
can reach.

## How it stands

- `agy.yaml` declares `login: {arguments: [], flow: paste-code}`. `terminal.feature` only previews it
  (`porelo vault login agy --dry-run`), which builds and runs nothing.
- The attended walk in `doc/index.md` measured: `Select login method: > 1. Google OAuth`, Enter, a
  `https://accounts.google.com/o/oauth2/auth?...` link redirecting to `https://antigravity.google/oauth-callback`, and a
  wait at `authorization code...`.
- Porelo runs the login in a throwaway container of a login image built from the agent's pinned artifacts, labelled
  `porelo.login.agent=agy`, with `BROWSER` unset.
- `update.yml` only checks the publisher's digest against the pin: it proposes no new release and runs no acceptance.
  `build.yml` runs the acceptance legs on a push to `main`, after a pin change has landed, and its `proof` job already
  names a job "Follow Antigravity CLI" that does not exist yet. No issue here builds it.

## Plan

- **`terminal.feature`, one scenario** "a sign-in at a terminal without a browser shows its link and waits for the
  code", made only of the kit's steps:
  - `a vault of this scenario's own, unlocked ...`, then `a terminal on the machine` (an SSH session with a pty, as
    `ssh -t` gives).
  - `I run "unset BROWSER; porelo vault login agy"`, `within <n> seconds the terminal shows "Select login method"`,
    `I press Enter`, then `within 60 seconds the terminal shows "authorization code"`; `<n>` covers the first build of
    the login image in a fresh account.
  - A script step reads `podman logs` of the container labelled `porelo.login.agent=agy`, strips escape sequences and
    line breaks, and requires a `redirect_uri=` that names none of `localhost`, `127.0.0.1` or `[::1]`.
  - Cancel: `podman stop` of that container by its label (or Ctrl-C, if it ends the login as measured), then a script
    that sees it gone and no `google-sign-in` credential in the vault.
- **`agy.yaml`**: the comment on `flow:` says the acceptance checks it on every pin.
- **Red first**: the redirect check, run by hand on a local VM against a login whose link names a loopback redirect,
  fails; against the real login it passes.
- **Level**: acceptance only, on a local VM, then one green leg pair on rented machines. No unit test: there is no
  logic of the adapter's in it.
- **If the update job is built here**: `update.yml` becomes the job of `porelo-claude-code` (detect with
  `upstream-version`, pin with `update`, unit suite, `check-pin`, packages, `compare-bills`, both acceptance legs on the
  candidate packages, sweep, pull request, merge or say why not), named "Follow Antigravity CLI" so `proven-legs`
  finds it; `pom.xml` already holds the `porelo.release.pin.agy.*` properties it reads.

## Acceptance

- The scenario passes on ubuntu and fedora against the pinned CLI, with no secret and no model.
- Its redirect check is seen to fail against a loopback redirect.
- A pin bump runs it before the bump reaches `main`, and a failed leg keeps the bump off `main`.
- No login container and no credential is left after it.

## What makes it uncertain

- The weekly job that runs it does not exist yet; until it does, the scenario runs only after a pin change has landed.
- The sign-in reaches Google from a rented machine on every run; a refusal of cloud addresses would read as a failed
  sign-in.
- A cosmetic change of the menu or prompt stops a pin bump until the scenario follows.

## Open questions

- The weekly update job here only checks the pinned digest; it proposes no new Antigravity CLI release and runs no
  acceptance, and no issue here builds one. Is that job (as in `porelo-claude-code`: pin the release, run both
  acceptance legs on its packages, open the pull request) built in this issue, which makes it L, or in an issue of its
  own that this one waits for?

## To be checked

- On 1.4.5: whether the link's redirect is `redirect_uri=https%3A%2F%2Fantigravity.google%2Foauth-callback`, whether
  `podman logs` of the login container holds it whole, and whether Ctrl-C ends the login.
- Which switch, if any, makes the CLI take a loopback redirect, for the red run; else a recorded log with the redirect
  replaced.
- How long the first build of the login image takes on a rented machine.
