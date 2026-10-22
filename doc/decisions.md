# Decisions

| Subject | What holds | Detail |
|---|---|---|
| Provider boundary | API-key routing uses the Google Gemini contract with `adds_path: true`; Google OAuth remains separate | [Provider boundary](#provider-boundary) |
| Upstream artifact | The task image verifies and installs the single `antigravity` executable from the GitHub release archive | [Installation](#installation) |
| Command grammar | Unattended and resumed turns use the declarative flag builder; turn limits are refused | [Commands](#commands) |
| Task-local profile | Owner-only `settings.json` and pre-completed `onboarding.json` suppress interactive setup | [Profile](#profile) |
| Terminal state | Measured composer, busy and dialog boundaries gate wake input | [Terminal state](#terminal-state) |
| Artifact checks | Shared named-pin calls check and record the fetched CLI component | [Artifact checks](#artifact-checks) |
| Build boundary | Packages require `porelo >= 0.5.1~snapshot.317`, publish to Artifactory and run acceptance against `GOOGLE_API` | [Build boundary](#build-boundary) |

## Provider boundary

The Google Gemini API-key provider and broker path support are shipped in Porelo (`porelo >= 0.5.1~snapshot.317`),
while Google OAuth/Cloud Code Assist belongs to porelo's
[Google OAuth issue](https://github.com/porelo-ai/porelo/blob/main/issues/providers/README.md#p12).
The adapter defaults to `google` and speaks the `google-generative-ai` dialect through a URL broker
with `adds_path: true`, because the Google GenAI Go SDK inside Antigravity CLI appends
`/v1beta/models/<model>:generateContent` to `GOOGLE_GEMINI_BASE_URL` itself. Its setup selects
`"modelProvider": "gemini"` in owner-only `settings.json` so the CLI uses the phantom token in
`GEMINI_API_KEY` and routes requests to `GOOGLE_GEMINI_BASE_URL`. It neither imports a real
credential nor implements Google OAuth sign-in or refresh before that contract exists.

## Installation

Antigravity CLI publishes `agy_cli_linux_x64.tar.gz` on GitHub Releases with a publisher SHA-256
digest matching its Google Cloud Storage manifest. The archive contains a single statically linked
binary named `antigravity`. The definition verifies the archive digest and member list before
installing `/home/agent/.local/bin/antigravity` and linking `/home/agent/.local/bin/agy` to it.
The image sets `AGY_CLI_DISABLE_AUTO_UPDATE=true` to disable the background self-updater, and
refuses `antigravity-cli-auto-updater-974169037036.us-central1.run.app` and `play.googleapis.com`
(Clearcut telemetry) at the network boundary.

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

For a brokered task, `AgyContainerSetup` validates the endpoint, task token and workspace path,
refusing a missing endpoint/token pair, unsafe URL/token characters, or a relative/root workspace
without echoing secret values. It writes `/home/agent/.gemini/antigravity-cli/settings.json` (mode
`0600`) with `"modelProvider": "gemini"`, `"model": "Gemini 3.6 Flash (Low)"`,
`"toolPermission": "always-proceed"`, `"artifactReviewPolicy": "always-proceed"`,
`"enableTelemetry": false` and `"trustedWorkspaces"` scoped to the normalized task workspace,
alongside `/home/agent/.gemini/antigravity-cli/cache/onboarding.json` with onboarding flags set to
`true` so interactive first-run wizards and workspace-trust dialogs never block startup, and tasks
started without an explicit `--model` flag default to a free-tier model rather than a zero-quota Pro
model. A shell with neither endpoint nor token receives no setup files.

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
The setup consumes only the published agent contract; provider upstreams, headers and credential
handling remain outside the adapter.

Both packages require `porelo >= 0.5.1~snapshot.317`, the first Porelo snapshot carrying the
`google` provider, `adds_path: true` URL brokering and structured conversation-ID extraction. CI
publishes `porelo-agent-agy` (`.deb`, `.rpm` and `.cdx.json`) to Artifactory using the shared curl
helper with the token on standard input, verifies that the Debian index lists the built package, and
runs the acceptance suite on Ubuntu and Fedora with `GOOGLE_API` and `gemini-3.6-flash-low`.
