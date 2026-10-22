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

**Can it be brokered?** On both paths, yes, measured on 1.4.5:
- **API-key path:** with `{"modelProvider":"gemini"}` in `~/.gemini/antigravity-cli/settings.json`,
  `GEMINI_API_KEY` and `GOOGLE_GEMINI_BASE_URL`, `agy -p` sends
  `POST /v1beta/models/<model>:streamGenerateContent?alt=sse` to that address over plain http, the key in
  `x-goog-api-key`, which the broker swaps. Without the setting it ignores both variables and asks for the
  sign-in (`accounts.google.com`, redirected to `antigravity.google/oauth-callback`).
- **Signed-in path:** `CLOUD_CODE_URL` redirects all Cloud Code Assist calls (`/v1internal:loadCodeAssist`,
  `/v1internal:fetchAdminControls`, `/v1internal:fetchUserInfo`, `/v1internal:retrieveUserQuotaSummary`,
  `/v1internal:fetchAvailableModels`, `/v1internal:listExperiments`, `/v1internal:streamGenerateContent?alt=sse`,
  `/v1internal:writeTrajectoryAcls`) from `https://daily-cloudcode-pa.googleapis.com` to the given URL over plain
  http with `Authorization: Bearer <access_token>`. At startup `keyring.go:93` also calls
  `https://www.googleapis.com/oauth2/v2/userinfo`; when `www.googleapis.com` is unreachable at the container
  firewall, `agy` treats that network error as non-fatal and proceeds with the phantom token in
  `~/.gemini/antigravity-cli/antigravity-oauth-token`.

**Where the sign-in is kept.** Measured on 1.4.5 without a desktop keyring (`composite_token_storage.go`): when
no D-Bus Secret Service collection is available, `agy` falls back to `~/.gemini/antigravity-cli/antigravity-oauth-token`
(mode `0600`), storing `{"auth_method":"consumer","id_token":"<jwt>","token":{"access_token":"...","token_type":"Bearer","refresh_token":"...","expiry":"<RFC3339>","expires_in":3599}}`.
When `expiry` is in the past, `agy` refreshes via `https://oauth2.googleapis.com/token` (`grant_type=refresh_token`,
`client_id` `1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com`). Post-login interactive
prompts (theme, terms/data-use) record their completion in `~/.gemini/antigravity-cli/jetski_state.pbtxt`.

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
