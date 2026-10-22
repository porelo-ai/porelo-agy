# AG01 — Agent Antigravity CLI

**Status:** Implementing; blocked by `porelo` [P06](https://github.com/porelo-ai/porelo/blob/main/issues/providers/README.md#p06), `porelo` [B248](https://github.com/porelo-ai/porelo/blob/main/issues/base/README.md#b248) and `porelo` [P12](https://github.com/porelo-ai/porelo/blob/main/issues/providers/README.md#p12).
**Size:** L - this repository's adapter with its packaging and an acceptance leg, on the API-key path first and
the Google sign-in beside it; it needs `porelo` [P06](https://github.com/porelo-ai/porelo/blob/main/issues/providers/README.md#p06) (Provider Google), `porelo` [B248](https://github.com/porelo-ai/porelo/blob/main/issues/base/README.md#b248) (A Base URL Without The Dialect's Path) and `porelo` [P12](https://github.com/porelo-ai/porelo/blob/main/issues/providers/README.md#p12) (The Google Sign-In As A Provider).

**What must be true.** Antigravity CLI (`agy`) runs in a task as a packaged agent, with a Gemini API key or
with the person's Google sign-in, brokered like the supported ones, with the real credential never in the
container. The CLI itself is Google's own, unchanged; Porelo only keeps the credential out of its sight.

## Why

**Upstream.** Google's Antigravity CLI, the terminal agent that serves Google AI plans and free use in
place of Gemini CLI. A Go binary, proprietary, from `storage.googleapis.com/antigravity-public/`, each
archive named with its SHA-512 in a manifest; feedback at https://github.com/google-antigravity/antigravity-cli.

**Providers.** One vendor, which serves Gemini, Claude and gpt-oss models.

**Authentication.** A Gemini API key, or a Google sign-in tied to a Google AI plan.

**Can it be brokered?** On the API-key path, yes, measured on 1.4.5 without network: with
`{"modelProvider":"gemini"}` in `~/.gemini/antigravity-cli/settings.json`, `GEMINI_API_KEY` and
`GOOGLE_GEMINI_BASE_URL`, `agy -p` sends `POST /v1beta/models/<model>:streamGenerateContent?alt=sse` to that
address over plain http, the key in `x-goog-api-key`, which the broker swaps. Without the setting it ignores
both variables and asks for the sign-in (`accounts.google.com`, redirected to `antigravity.google/oauth-callback`).
The signed-in path reads `CLOUD_CODE_URL` for its endpoint; read in the binary, not measured.

**Where the sign-in is kept.** Read, not measured: a D-Bus Secret Service client (`auth.keyringAuth`). A run
without a sign-in leaves `~/.gemini/config/` and `~/.gemini/antigravity-cli/` (installation id, conversation
database, built-in skills) and no token.

**Licence and terms.** Proprietary; no copy may be shipped, so the package fetches the pinned binary at the
image build, by URL and digest, as Claude Code's does. Google's terms call it a breach to use third-party
software to access the service with Antigravity's sign-in, and say such use may suspend the account. Porelo
does not replace the CLI: the requests are `agy`'s own, and the broker only keeps the token out of the
container. Whether Google reads that as third-party access is not known; the adapter's page and Porelo's page
on credentials say so, and the API-key path is not touched by it.

**Unattended use.** `agy -p` runs one prompt and exits, `--output-format json` or `stream-json` structures
its output, `--conversation <id>` resumes. It updates itself in the background
(`antigravity-cli-auto-updater-974169037036.us-central1.run.app`), stopped by `AGY_CLI_DISABLE_AUTO_UPDATE=true`
and refused at egress together with `play.googleapis.com` (`server.go:3274` sends Clearcut logs there on shutdown
even when `"enableTelemetry": false` is set in `settings.json`).

## Plan

1. **The API-key path first:** the agent definition `src/main/resources/agent/agy.yaml` (`provider.adds_path: true`,
   since `agy` appends `/v1beta/models/...` to `GOOGLE_GEMINI_BASE_URL` itself) and `AgyContainerSetup` writing
   `~/.gemini/antigravity-cli/settings.json` (`modelProvider: "gemini"`, `toolPermission: "always-proceed"`,
   `artifactReviewPolicy: "always-proceed"`, `enableTelemetry: false`, `trustedWorkspaces: [<workspace>]`) and
   `~/.gemini/antigravity-cli/cache/onboarding.json` (`onboardingComplete: true`), linking
   `~/.gemini/GEMINI.md` to `/run/porelo/guide/README.md`, setting `AGY_CLI_DISABLE_AUTO_UPDATE=true`, and
   fetching the pinned binary (`1.4.5`, build `4952523841339392`) by its SHA-256 digest at the image build.
2. **The sign-in second:** measured on the VM with the operator's sign-in, once, attended: where the token is
   kept without a desktop keyring, which hosts it reaches, whether `CLOUD_CODE_URL` points it at the broker,
   and how it is refreshed. Then the extractor that hands it to Porelo, and the provider for the signed-in
   path in `porelo` ([P12](https://github.com/porelo-ai/porelo/blob/main/issues/providers/README.md#p12), as [P11](https://github.com/porelo-ai/porelo/blob/main/issues/providers/README.md#p11) is for the ChatGPT sign-in).
3. **This repository**, shaped like `porelo-omp`: `AgyAgent`, `AgyContainerSetup` and `AgyJsonFormatter` against
   `porelo-agent-api` and `porelo-wire`, its definition, the Debian and RPM packaging, unit tests, and an acceptance
   leg on a machine rented through `porelo-machines`, the key from the repository secret `GOOGLE_API`.
4. **The risk said:** `doc/index.md` here and a line in porelo's `doc/credentials.md` name the terms and
   what Porelo does and does not do with the sign-in.
5. **Seen to fail first:** the unit tests (`AgyAgentTest`, `AgyContainerSetupTest`, `AgyJsonFormatterTest`,
   `AgyScreenTest`, `PinAgreementTest`) and the acceptance leg that searches the container for the real credential.

## Acceptance

- It ships as its own package, which fetches the pinned binary by its digest, in a repository of its own,
  discovered without Porelo changing.
- Nothing outside its own directory names it, enforced by the build.
- A task authenticates with an API key and with a sign-in without anyone signing in inside the container.
- The real credential is never in the container, seen by searching the container for it, on both paths.
- The CLI does not update itself in a task.

## To be checked

- Where the sign-in is kept without a keyring, and whether `CLOUD_CODE_URL` redirects the signed-in path. - Agent AGY's to measure, with the operator's sign-in.
