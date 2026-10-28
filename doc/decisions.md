# Decisions

| Subject | What holds | Detail |
|---|---|---|
| Provider boundary | Google sign-in defaults to Cloud Code Assist; API keys use the Gemini dialect | [Provider boundary](#provider-boundary) |
| Upstream artifact | The task image verifies the `antigravity` binary and installs a login-aware `agy` wrapper | [Installation](#installation) |
| Command grammar | Unattended and resumed turns use the declarative flag builder; turn limits are refused | [Commands](#commands) |
| Task-local profile | Phantom-only OAuth state and completed onboarding suppress interactive setup | [Profile](#profile) |
| Terminal state | Measured composer, busy and dialog boundaries gate wake input | [Terminal state](#terminal-state) |
| Artifact checks | Shared named-pin calls check and record the fetched CLI component | [Artifact checks](#artifact-checks) |
| Build boundary | Packages require `porelo >= 0.5.1~snapshot.318`, publish to Artifactory and run acceptance against `GOOGLE_API` | [Build boundary](#build-boundary) |

## Provider boundary

The adapter defaults to `google-sign-in`, supported by Porelo's published Google sign-in contract
in `porelo >= 0.5.1~snapshot.318`. Its URL broker supports `google-generative-ai` through
`GOOGLE_GEMINI_BASE_URL` and `cloud-code` through `CLOUD_CODE_URL`, both with `adds_path: true`
because the CLI appends the dialect path itself. API keys use `GEMINI_API_KEY`; OAuth uses
`GOOGLE_SIGN_IN_TOKEN` and the task-local phantom token file. The `google` provider remains the
explicit API-key choice.

`AgyCredentialExtractor` verifies that the configuration directory and every file it reads are
regular and contain no symbolic links along their path, reads `antigravity-oauth-token` of at most
one mebibyte, validates the consumer access and refresh tokens and converts its ISO-8601 expiry to
epoch milliseconds. It collects every client ID and every client secret from `.client-ids` and
`.client-secrets` first, then a profile-local `antigravity` binary, then
`/home/agent/.local/bin/antigravity`; a single `.client-id` or `.client-secret` from an older image is
ignored. The candidate files are limited to 4096 bytes and every line must be a whole id or secret;
binary scans stream at most 512 mebibytes and keep a value split across chunk boundaries whole and
once. Candidates are kept distinct in the binary's order, and more than eight of one kind is refused.
A present invalid candidate file fails closed. Diagnostics never include credential contents or parser
causes. The host receives the refresh token, expiry, token URL and the two candidate lists
(`clientIdCandidates`, `clientSecretCandidates`); Porelo's login tries each id with each secret once
with the fresh refresh token and stores the pair that renews it. This adapter-specific extraction
differs from the single-CLI baseline because Google's consumer renewal requires the installed client's
ID and secret as well as the person's refresh token.

The CLI holds several OAuth clients and chooses one by sign-in kind in its code: the client IDs differ
in their number of leading digits, and nothing in the strings' order or neighborhood pairs an id with
its secret. Reading the pairing from the machine code would tie the adapter to x86-64 and to one
compiler's code shape, so the pair is picked live at login instead.

The login-only plaintext collection window is an accepted exposure: the wrapper creates owner-only
`.client-ids` and `.client-secrets` files beside the CLI's login file so the host can collect and
encrypt the picked pair before deleting the temporary host directory and login container. Runtime-backed
collection that avoids plaintext filesystem copies would remove the need for this window. Signed-in
tasks never receive either renewal secret.

## Installation

Antigravity CLI publishes `agy_cli_linux_x64.tar.gz` on GitHub Releases with a publisher SHA-256
digest matching its Google Cloud Storage manifest. The archive contains a single statically linked
binary named `antigravity`. The definition verifies the archive digest and member list before
installing `/home/agent/.local/bin/antigravity` and a mode `0755` POSIX shell wrapper at
`/home/agent/.local/bin/agy`. With neither broker URL set and no `/run/porelo`, the wrapper uses
`set -eu`, `umask 077`, removes any preexisting `.client-id`, `.client-secret`, `.client-ids` and
`.client-secrets`, and writes every matching client ID and client secret with `set -C` (`noclobber`);
when `grep` finds none of either, it stops the wrapper before the CLI runs. In a task it immediately executes the CLI
without writing files.
The image sets `AGY_CLI_DISABLE_AUTO_UPDATE=true` to disable the background self-updater, and
refuses `antigravity-cli-auto-updater-974169037036.us-central1.run.app`, `play.googleapis.com`,
`www.googleapis.com` and `antigravity-unleash.goog` at the network boundary. The signed-in CLI's
user-info request to `www.googleapis.com` tolerates that refusal and proceeds through Cloud Code Assist.

## Commands

Antigravity CLI uses `-p` for unattended work, `--conversation` to resume a recorded session,
`--model` for model selection, `--verbose`, and `--output-format json` for structured turn output.
The sandboxed launch passes `--dangerously-skip-permissions` rather than `--mode auto`, because
`--mode auto` emits a warning on API-key and standard accounts while `--dangerously-skip-permissions`
combined with `"toolPermission": "always-proceed"` suppresses tool and command confirmation prompts.
The CLI has no `--max-turns` flag, so a requested turn limit is refused rather than silently ignored.

## Artifact checks

The shared pin command checks `agy` against the GitHub release asset digest. The fetched-CLI
recorder selects the artifact by its named pin and records `agy` in the bill of materials. It is a
Maven plugin dependency only, never a dependency of the adapter or its tests. The package checks
read the actual generated bill and require the fetched component, its built version, checked digest,
download URL and fetched-at-image-build provenance.

## Profile

The image links `/home/agent/.gemini/GEMINI.md` to `/run/porelo/guide/README.md`, which Antigravity
CLI loads into its `<user_rules>` system prompt when the guide is mounted and ignores cleanly when
absent.

For a brokered task, `AgyContainerSetup` validates the endpoint, task token, credential type and workspace path,
refusing a missing endpoint/token pair, unsafe URL/token characters, or a relative/root workspace
without echoing secret values. For `api-key` it writes `/home/agent/.gemini/antigravity-cli/settings.json` (mode
`0600`) with `"modelProvider": "gemini"`, `"model": "Gemini 3.6 Flash (Low)"`,
`"toolPermission": "always-proceed"`, `"artifactReviewPolicy": "always-proceed"`,
`"enableTelemetry": false` and `"trustedWorkspaces"` scoped to the normalized task workspace,
alongside `/home/agent/.gemini/antigravity-cli/cache/onboarding.json` with onboarding flags set to
`true` so interactive first-run wizards and workspace-trust dialogs never block startup, and tasks
started without an explicit `--model` flag default to a free-tier model rather than a zero-quota Pro
model.

For `oauth`, settings omit `modelProvider` and `model` so the CLI uses Cloud Code Assist. The
owner-only `antigravity-oauth-token` contains only `auth_method: consumer`, the phantom access
token, `token_type: Bearer` and `expiry: 2099-01-01T00:00:00Z`; it contains no refresh token,
ID token or client secret. Alongside the same onboarding cache, non-secret `jetski_state.pbtxt`
completes welcome, usage mode, agent configuration, workspace selection and migrations 3, 4 and 5.
A shell with neither endpoint nor token receives no setup files.

## Terminal state

The composer footer `? for shortcuts` establishes startup readiness and idle composer state. During
a working turn the composer remains visible while the footer switches to `esc to cancel`; wake input
requires `? for shortcuts` and excludes `esc to cancel` together with interactive confirmation or
selection footers (`enter Confirm · esc Exit` and `enter Select · esc Cancel`).
Waiting rules cover the measured workspace-trust (`enter Confirm · esc Exit`) and question
(`enter Select · esc Cancel`) footers in the last five lines of the terminal.

## Build boundary

The Java and resource layout follows the existing adapters: a small `YamlAgent` subclass, a
stateless JSON formatter and container setup, service registration and a null-marked main package.
The setup and extractor consume only the published agent contract; provider upstreams, headers,
vault storage and renewal requests remain outside the adapter.

Both packages require `porelo >= 0.5.1~snapshot.318`, which carries the `google-sign-in` provider
and `cloud-code` dialect alongside API-key URL brokering and structured conversation-ID extraction. CI
publishes `porelo-agent-agy` (`.deb`, `.rpm` and `.cdx.json`) to Artifactory using the shared curl
helper with the token on standard input, verifies that the Debian index lists the built package, and
runs the acceptance suite on Ubuntu and Fedora with `GOOGLE_API` and `gemini-3.6-flash-low`.
