# Changelog

## [Unreleased]

### Added

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

### Fixed

- A Google sign-in renews on the host: the login hands Porelo every OAuth client id and secret the
  installed CLI holds, each id whole, so Porelo picks the pair the sign-in was granted to. Before,
  the first id and the first secret were taken, a pair Google refuses with `invalid_client`.
