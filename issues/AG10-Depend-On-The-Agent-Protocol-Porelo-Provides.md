# AG10 — Depend On The Agent Protocol Porelo's Package Provides

```json
{
  "$schema": "issue-1.json",
  "id": "AG10",
  "title": "Depend On The Agent Protocol Porelo's Package Provides",
  "state": "Estimated",
  "size": "S",
  "priority": "Medium",
  "rank": 6,
  "blockedBy": [],
  "covers": "the agent contract named at install time",
  "openQuestions": 0
}
```

**Why this size.** one module: two dependency lines from one pom property, one unit test and one more assertion in
the package check. Porelo's `Provides` is Porelo's own change; this adapter only reads it.

**Estimate.** S: 27 min, the measured median of S over all repositories (n=167).

**How it stands.** Waits for Porelo's published packages providing their lowest agent protocol version; built before
that, the adapter stops installing.

**What must be true.** Installing this adapter beside a Porelo that speaks another agent protocol is refused by the
package manager, not at the first task.

## Why

`src/deb/control/control` and `pom.xml`'s `<requires>` require `porelo` at a version floor and name no protocol,
while the adapter is built against one agent contract. It is not a live defect:
Porelo checks an adapter's `protocolVersion` before using it and refuses one it cannot use with a message, but that
refusal comes at the first task.

Porelo accepts every agent at or above its lowest protocol version, a newer one included, and raises that lowest
version only for a change that breaks the contract, so the one pairing to refuse is an adapter older than Porelo's
lowest version; a dependency on one exact number would refuse pairings that work.

## The shape

- Porelo's packages provide `porelo-agent-protocol-<L>`, with `<L>` its `AgentProtocol.LOWEST`. They are published
  before this adapter depends on it, or it stops installing.
- This adapter depends on any lowest version up to its own `VERSION`, from 3, the first lowest version there is:
  `porelo-agent-protocol-3 | porelo-agent-protocol-4` in the `.deb` for an adapter at 4, and the same as a rich
  dependency in the `.rpm`, both from one pom property. The version floor on `porelo` stays beside it: a floor refuses a Porelo too old,
  not one whose lowest version has passed the adapter.

## Plan

Read in the code on 2026-11-11. The adapter reports `AgentProtocol.VERSION` of the `porelo-agent-api` jar it compiles
against (`porelo.version`, `0.5.1-SNAPSHOT`, which holds 4; `LOWEST` is 3), through the API's `AgentServer`, not in
its own code. `src/deb/control/control:13` depends on `porelo (>= [[porelo.minimum.version]])`; the rpm-builder's `<requires>` on
`<require>porelo &gt;= ${porelo.minimum.version}</require>` (`:448`).

1. **One property** in `pom.xml` beside `porelo.version` and `porelo.minimum.version` (`:49`, `:50`): `porelo.agent.protocols` =
   `porelo-agent-protocol-3 | porelo-agent-protocol-4`.
2. **The `.deb`**: `src/deb/control/control:13` gains `[[porelo.agent.protocols]]` as one more `Depends` entry, beside
   `porelo (>= [[porelo.minimum.version]])`. The version floor on `porelo` stays beside it (`porelo.minimum.version`): a floor refuses a Porelo too old, not one whose lowest version has passed the adapter.
3. **The `.rpm`**: a second `regex-property` execution of `build-helper-maven-plugin` (beside the one that makes
   `agent.package.version`, `:320`) turns `(${porelo.agent.protocols})` into `porelo.agent.protocols.rpm`,
   replacing ` | ` with ` or `; `<requires>` gains `<require>${porelo.agent.protocols.rpm}</require>`, a rich
   dependency.
4. **The built packages**: `PackageVersionCheck` already holds the built `.deb`'s `Depends` and the `.rpm`'s `--requires` to the `porelo` floor (`:43`, `:62`), the floor passed as a system property (`pom.xml:526`); the protocol dependency is asserted beside it the same way.
5. `CHANGELOG.md` under `[Unreleased]`: the package now depends on the agent protocol Porelo provides.

**Red first:** a unit test `AgentProtocolDependencyTest` in `src/test/java/org/fuin/porelo/agent/impl/agy/`
reads `porelo.agent.protocols` out of `pom.xml` (as `PinAgreementTest` reads the pom) and holds it to
`porelo-agent-protocol-3` up to `porelo-agent-protocol-<AgentProtocol.VERSION>`, joined by ` | `, the version read from
the API class on the test classpath. Red before step 1 (no property), and a second case reproduces the refusal with a
list ending one below `VERSION`. Test level: unit (`mvn verify`); the built-package assertion runs in the `dist`
profile; the install on the VM by hand.

## Acceptance

- `dpkg-deb -f porelo-agent-agy_*.deb Depends` names `porelo-agent-protocol-3 | porelo-agent-protocol-4` beside `porelo`,
  and `rpm -qp --requires` names `(porelo-agent-protocol-3 or porelo-agent-protocol-4)` beside `porelo`.
- `AgentProtocolDependencyTest` holds that list to the API's `AgentProtocol.VERSION`; it is seen to fail with the
  numbers apart.
- On the VM, the `.deb` beside a Porelo package whose lowest version is above the adapter's `VERSION` is refused by
  `apt`, and installs beside one at or below it, a lower one included.

## What makes it uncertain

- The order across repositories: merged before Porelo publishes packages that provide the protocol, the adapter no
  longer installs anywhere. It is pushed only after that.
- The rpm-builder (`de.dentrassi.maven:rpm`) writes headers itself, without `rpmbuild`: whether it keeps a
  parenthesized rich dependency whole, rather than splitting it into a name, an operator and a version, is not read
  yet. If it splits it, the `.rpm` takes the dependency in another form (or the floor alone stays there), which may
  move the size to the top of S.
- When the adapter moves to the agent API at contract version 5 (with `ToolEvents`), the test turns red until the
  property lists `porelo-agent-protocol-5` too; that is the test doing its work, done in the same change as the
  upgrade.

## To be checked

- `rpm -qp --requires` on a built `.rpm` with the rich dependency, and that `rpm` reads it as one; on a host with
  `dnf`, if one is at hand, that it installs beside a Porelo providing the protocol.
- That jdeb's `[[...]]` filtering takes the `|` of the property through unchanged into `Depends`.
