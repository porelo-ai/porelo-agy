# AG10 — Depend On The Agent Protocol Porelo's Package Provides

```json
{
  "$schema": "issue-1.json",
  "id": "AG10",
  "title": "Depend On The Agent Protocol Porelo's Package Provides",
  "state": "Idea",
  "size": "-",
  "priority": "Medium",
  "rank": 6,
  "blockedBy": [],
  "covers": "the agent contract named at install time"
}
```

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

## Acceptance

- This adapter's packages depend on one of the lowest versions from 3 to its own `VERSION`, beside `porelo`, and a test
  holds that list to the API's `AgentProtocol.VERSION`; it is seen to fail with the numbers apart.
- The `.deb` beside a Porelo package whose lowest version is above the adapter's `VERSION` is refused by `apt`, and
  installs beside one at or below it, a lower one included.
