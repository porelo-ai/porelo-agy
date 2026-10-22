# Changelog

## [Unreleased]

### Added

- A provider-independent Antigravity CLI adapter with task-local settings, structured turn outcomes
  and a pinned, verified CLI installation archive.
- A draft task-build image declaration with pinned build tools and native package prerequisites.
- Google Gemini API-key broker routing (`provider.adds_path: true`) through owner-only task-profile
  settings, with unsafe or incomplete endpoints and task tokens refused before launch.
- Task-scoped workspace trust, pre-populated onboarding state and measured startup, question-dialog
  and safe wake boundaries that exclude a working turn even when its composer remains visible.
- Package dependency floor `porelo (>= 0.5.1~snapshot.317)` on Debian and RPM packages, and a
  free-tier default model (`Gemini 3.6 Flash (Low)`) in task-local settings so brokered API-key tasks
  started without an explicit `--model` flag do not default to a zero-quota Pro model.
