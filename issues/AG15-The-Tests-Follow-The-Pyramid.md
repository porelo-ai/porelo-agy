# AG15 — The Tests Follow The Pyramid

```json
{
  "$schema": "issue-1.json",
  "id": "AG15",
  "title": "The Tests Follow The Pyramid",
  "state": "Analyzed",
  "size": "XS",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": [],
  "covers": "the suites by level with measured times, the refused hosts already held in the unit phase by AgyAgentTest; the timeout test waits 100 ms"
}
```

**Why this size.** one test class, no contract.

**What must be true.** Every flow this adapter owns is proven by unit tests that run in seconds, and no test waits
longer than the thing it checks needs.

## The suites by level

Measured with a clean `./mvnw -o -B -s settings.xml clean test` on the host under a load of about 40 on 16 cores:
8 s in all, compilation included, 122 tests green and none skipped. The end-to-end times are those of the last
green Build on GitHub (run 42477648330). A class that starts a process counts as integration as a whole.

| Level | Tests | Run time | Share by count | What it holds |
|---|---|---|---|---|
| Unit | 98 | 0.7 s | 68 % | `AgyCredentialExtractorTest` 53, `AgyAgentTest` 9, `AgyContainerSetupTest` 9, `AgyJsonFormatterTest` 5, `AgyScreenTest` 3, and the document and pin rules 19 |
| Integration | 24 + 9 | 2.6 s, plus the checks inside the 2.5 min Build job | 23 % | `AgyLoginWrapperTest` 7, the workflow-step tests 15 (bash with stand-in tools), `WorkflowStepsTest` 2, and the `dist` checks of the built packages and the native executable (`NativeLinkageCheck` 2, `NativeProtocolCheck` 3, `PackageVersionCheck` 3, `FetchedArtifactsCheck` 1) |
| End-to-end | 14 scenarios | 12.0 min (fedora) and 11.5 min (ubuntu), one rented machine each | 10 % | `terminal` 4, `installed` 3, `credential` 2, `session` 2, `wake` 2, `broker` 1 |

The unit share is just below the pyramid's 70-80 % because the Build's own workflow steps and the login wrapper are
shell and are proven by running them with stand-in tools, which is their lowest level.

## Flows proven only above the unit level

- **The login wrapper** (`AgyLoginWrapperTest`): that a login collects every client owner-only, writes no client
  secret where a broker serves the task, and fails closed without one, is a shell wrapper the image installs. It
  stays integration: the wrapper is the real piece, and its tests take 0.1 s.
- **The workflow steps** (publish steps and tokens, snapshot tokens, release channels): bash run with stand-in
  tools. They stay integration for the same reason.
- **The native executable's protocol** (`NativeProtocolCheck`): that the built executable answers `describe` and
  keeps a sign-in's renewal attributes is checked against the native build only, since only the native image shows
  what its reflection configuration kept. The same reading in Java is unit-tested in `AgyCredentialExtractorTest`.
- **End-to-end only:** no other flow of this adapter. Every other scenario's adapter fact - the composer's screen at
  rest and at work, the recorded conversation, the subagent switch, the login's arguments, the run's end on a
  refused key, the broker's address - has a unit test over the recorded screen or file. What the scenarios add is
  that the pinned agy CLI honors those facts, which only the real CLI can show.

## Waits, sleeps and the network

- `WorkflowStepsTest` runs `sleep 20` under a limit of 2 s and so waits those two seconds each run; a limit of
  100 ms shows the same kill.
- No test reaches the network: every download in a workflow step is a stand-in, and no test opens a socket or a
  URL (searched for `HttpClient`, `openConnection`, `openStream`, `new URL(`, `InetAddress` and `ServerSocket`).

## Plan

- **The timeout test waits 100 ms** (XS): `WorkflowStepsTest` with a limit of 100 ms, seen red first by a limit
  the sleep does not reach.

## Acceptance

- The unit suite stays under a minute, and no flow of the adapter is reached only from an integration or an
  end-to-end run.
- `WorkflowStepsTest` takes under 0.5 s.

## Open questions

- None.
