# Changelog

## [Unreleased]

### Changed

- The Build takes the runner's distribution packages from the Actions cache, keyed by the package list's hash, each file checked against the hash apt names before it is installed, so an outage of a package mirror fails only a Build on a new list; the agy archive the task image fetches on a rented machine stays uncached until the machines can take a staged file.
- Unattended tasks use stream-json output, with step text, final outcomes and session discovery
  read from its nested records.
- The package says that the `porelo-wire` classes in its executable are under Apache-2.0, in its
  Debian copyright and its RPM license, with the license's text beside the GPL's in the RPM.

### Added

- The weekly update job follows Antigravity CLI: it takes a release once it is three days old, pins its version and digest, builds and tests the packages, runs both acceptance legs on them and opens the pull request, or says why not; a week with nothing newer still checks the pinned digest.
- The definition says how an attended session takes an opening text, `session.opening_flag: "-i"`, so
  Porelo can open a discussion on Antigravity CLI with the person's text as its first turn, after which it waits
  for them.
- Tool-call events report call identity, kind, shell commands and measured durations. Task status
  and cancellation records correlate background job ends without copying tool output.
- A task's agent starts no subagent, and a task allowing one is refused, since nothing here can count them.
- The definition names how its sign-in is answered, `login.flow: paste-code`, so a person signing
  in on a host over SSH can be told to paste the code Google's page shows; the Porelo it builds
  against reads the key, and its agent listing names the flow.
- A provider-independent Antigravity CLI adapter with task-local settings, structured turn outcomes
  and a pinned, verified CLI installation archive.
- A draft task-build image declaration with pinned build tools and native package prerequisites.
- Google Gemini API-key broker routing (`provider.adds_path: true`) through owner-only task-profile
  settings, with unsafe or incomplete endpoints and task tokens refused before launch.
- Task-scoped workspace trust, pre-populated onboarding state and measured startup, question-dialog
  and safe wake boundaries that exclude a working turn even when its composer remains visible.
- Package dependency floor `porelo (>= 0.5.1~snapshot.318)` on Debian and RPM packages, and a
  free-tier default model (`Gemini 3.6 Flash (Low)`) in task-local settings so brokered API-key tasks
  started without an explicit `--model` flag do not default to a zero-quota Pro model.
- Google sign-in as the default `google-sign-in` provider, with the `cloud-code` dialect routed
  through `CLOUD_CODE_URL` alongside Gemini API-key routing. `AgyCredentialExtractor` reads
  `antigravity-oauth-token` and the installed CLI's OAuth clients for host-side renewal; the login
  wrapper collects them only in a throwaway login container. `AgyContainerSetup` writes a
  phantom-only `antigravity-oauth-token` and completed post-onboarding state for signed-in tasks.
  The task firewall also refuses `www.googleapis.com` and `antigravity-unleash.goog`.
- The package carries the full license texts of what its executable compiles in from outside Porelo - SnakeYAML, JSpecify and the GraalVM runtime - in `THIRD-PARTY-NOTICES`, in its doc directory and as an RPM license file, and its Debian copyright names them.

### Fixed

- A Google sign-in renews on the host: the login hands Porelo every OAuth client id and secret the
  installed CLI holds, each id whole, so Porelo picks the pair the sign-in was granted to. Before,
  the first id and the first secret were taken, a pair Google refuses with `invalid_client`.
