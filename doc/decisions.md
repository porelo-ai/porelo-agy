# Decisions

| Subject | What holds | Detail |
|---|---|---|
| Provider boundary | Google sign-in defaults to Cloud Code Assist; API keys use the Gemini dialect | [Provider boundary](#provider-boundary) |
| Upstream artifact | The task image verifies the `antigravity` binary and installs a login-aware `agy` wrapper | [Installation](#installation) |
| Command grammar | Unattended and resumed turns use the declarative flag builder; turn limits are refused | [Commands](#commands) |
| Task-local profile | Phantom-only OAuth state and completed onboarding suppress interactive setup | [Profile](#profile) |
| Terminal state | Measured composer, busy and dialog boundaries gate wake input | [Terminal state](#terminal-state) |
| Artifact checks | Shared named-pin calls check and record the fetched CLI component; the weekly job follows the release | [Artifact checks](#artifact-checks) |
| Build boundary | Packages require `porelo >= 0.5.1~snapshot.318`, publish to Artifactory and run acceptance against `GOOGLE_API` | [Build boundary](#build-boundary) |
| Snapshot repositories | The forge's token goes to the forge's registry alone; what it does not hold comes from an anonymous upstream | [Snapshot repositories](#snapshot-repositories) |
| Subagent bound | The CLI holds it, and its shell can start another copy; a hard bound is Porelo's | [Accepted risk: a task's subagent bound is the agent's own](#accepted-risk-a-tasks-subagent-bound-is-the-agents-own) |

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
the installed `/usr/local/lib/agy/antigravity`; a single `.client-id` or `.client-secret` from an older image is
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
installing `/usr/local/lib/agy/antigravity` and a mode `0755` POSIX shell wrapper at
`/usr/local/bin/agy`, both root's, so the agent in a task cannot replace what the next start runs. With neither broker URL set and no `/run/porelo`, the wrapper uses
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

The weekly job "Follow Antigravity CLI" in `update.yml` follows the release through the same named pin. It takes
the newest release once it is three days old (`porelo.release.min-age`), pins its version and the digest GitHub
publishes for `agy_cli_linux_x64.tar.gz`, and proposes it in a pull request only after the unit suite, the pin check,
the packages, the bill comparison and both acceptance legs on the candidate packages. It stops, opens a request
that says why and merges nothing when a gate fails, the key `GOOGLE_API` is empty, the major version moved or upstream
points below the pin. A week with nothing to take still checks the pinned digest. No gate checks that a new release
still holds the OAuth client IDs and secrets the login wrapper collects: the legs sign in with an API key, so a
release that moves them passes.

## Profile

The image links `.gemini/GEMINI.md` in the agent's home to `/run/porelo/guide/README.md`, which Antigravity
CLI loads into its `<user_rules>` system prompt when the guide is mounted and ignores cleanly when
absent.

For a brokered task, `AgyContainerSetup` validates the endpoint, task token, credential type and workspace path,
refusing a missing endpoint/token pair, unsafe URL/token characters, or a relative/root workspace
without echoing secret values. For `api-key` it writes `.gemini/antigravity-cli/settings.json` in the agent's home (mode
`0600`) with `"modelProvider": "gemini"`, `"model": "Gemini 3.6 Flash (Low)"`,
`"toolPermission": "always-proceed"`, `"artifactReviewPolicy": "always-proceed"`,
`"enableTelemetry": false` and `"trustedWorkspaces"` scoped to the normalized task workspace,
alongside `.gemini/antigravity-cli/cache/onboarding.json` in the agent's home with onboarding flags set to
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

## Last message

A finished unattended run answers its text from the `result` record of a successful run, field
`result.response`. In 1.4.5 a reply streams as `agent_response` records of `text_delta` pieces, and
the piece closing a step may be a bare newline. A short reply can stand whole in its closing piece,
as the last one of the recorded run does, but a longer one does not, so no streamed record holds a
reply for sure; the result's `response` holds every reply of the run joined. The answer is therefore
all replies, ending in the last, rather than the last alone; tool output stays out. A run that ends
without a successful result, failed or cut off, answers nothing. The declaration holds no screen
rule.

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

## Snapshot repositories

`settings.xml` reads snapshots through one of three profiles, the same as in `porelo-claude-code`, `porelo-pi` and
`porelo-omp`: `standard` (Central's snapshots, under the id `central.sonatype.snapshots`, asked only for
`org.fuin.porelo`) wherever the filter's switch in `.mvn/maven.config` and its file in `.mvn/rrf/` are, on a forge
too, `forge` where `MAVEN_SNAPSHOTS_URL` does, read with the forge's read-only token under the server id
`porelo-forge`, and `upstream` where `MAVEN_UPSTREAM_SNAPSHOTS_URL` names where the Porelo snapshots a forge's
registry does not hold come from. On a forge Sonatype is so asked beside its registry: on the local Forgejo a newer
snapshot of `main` on Sonatype can shadow one the forge published from a branch, and a forge that cannot reach
Sonatype waits on it; on GitHub the forge's repository is Sonatype itself and unfiltered. Accepted with the filter, by
the coordinator on 2026-11-12. The upstream repository's id has no `<server>`, so no token is sent there;
`UpstreamSnapshotsTest` fails when any repository other than the forge's would get its credential. A tag's build
passes `-P!standard,!forge,!upstream`, so a release reads no snapshot repository; `UpstreamSnapshotsTest` fails when a
profile that reads snapshots is left out of it.

Measured from an empty local repository, with `MAVEN_SNAPSHOTS_URL` naming a repository without Porelo's snapshots:
the build resolves them from `porelo-upstream-snapshots` with `MAVEN_UPSTREAM_SNAPSHOTS_URL` set, and fails without it
and with `-P!standard,!forge,!upstream`.

**Accepted risk:** for one snapshot version Maven takes the newest timestamp over every repository it reads, so where
the forge and upstream hold the same version, upstream's can win. Nothing here builds against a snapshot it publishes
itself; a forge that does would name only what it lacks upstream, or mirror upstream itself.

## Accepted risk: a task's subagent bound is the agent's own

Antigravity CLI has no setting that turns subagents off or counts them, so `agy.yaml` declares off alone. At 0,
`AgyContainerSetup` writes a `PreToolUse` hook that denies `invoke_subagent`, `run_workflow`,
`browser_subagent`, `define_subagent` and `manage_subagents`, and Porelo refuses a task that allows more.

**The exposure:** the agent has a shell and can start another copy of the CLI. Its skills are not switched off: nothing
measured at the pin hides them, and it reads one with `read_file`. The acceptance scenario rests on what the model
says, since the CLI's output is the agent's text alone. Whether the CLI leaves something to count from outside was
not measured.

**Why it stays:** the container, not the CLI, is the boundary of a task, and a bound the agent cannot leave is Porelo's own to set, outside this repository.

**What would change it:** a CLI setting that reaches its internal choice of allowed subagents, which would replace the
hook, or a bound Porelo enforces from outside the agent.
