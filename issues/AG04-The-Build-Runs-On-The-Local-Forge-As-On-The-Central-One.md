# AG04 — The Build Runs On The Local Forge As On The Central One

**Status:** Idea; blocked by porelo-buildtools [BT25](https://github.com/porelo-ai/porelo-buildtools/blob/main/issues/README.md#bt25) and [BT24](https://github.com/porelo-ai/porelo-buildtools/blob/main/issues/README.md#bt24), porelo-host [HO12](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho12) and [HO27](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho27).

**What must be true.** `porelo-agy`'s workflows run green on the local Forgejo and on the central forge on the same
commit, with no edit between them: the machines its legs rent are asked for by kind, and the registry and the
provider are variables of the setup.

From porelo-project [PJ60](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj60), for the move's
wave 7b, before [AG02](README.md#ag02); the kinds come from porelo-buildtools
[BT25](https://github.com/porelo-ai/porelo-buildtools/blob/main/issues/README.md#bt25).

## The shape

Read in today's `.github/workflows/`; the same change as porelo-claude-code
[CC38](https://github.com/porelo-ai/porelo-claude-code/blob/main/issues/README.md#cc38), with less of it: there is no
`artifactory-smoke.yml` and no `delete-runs.yml`, and `update.yml` only checks the pin.

- `settings.xml` reads the forge's registry through `MAVEN_SNAPSHOTS_URL`, as porelo-message-sluice's does.
- `vars.PORELO_PUBLISH` switches Publish and its index check, the `release` job and the `proof` job (`proven-legs`
  asks GitHub's API); where it is `false`, the legs install the packages the run built.
- The legs ask `porelo-machines acceptance --kind amd64` instead of passing `HETZNER_API` and `HETZNER_SSH`; the
  `GOOGLE_API` secret must be on the forge too, verified by the operator.
- `porelo-release check-providers` joins the Build's checks.

## Acceptance

- Seen to fail first: today's `build.yml` on the local Forgejo.
- The same commit's Build green on the local Forgejo and on GitHub.
- The kinds' check finds no provider's name in this repository's workflows.
