# AG12 — The Clean-Up Says When Nothing Was Rented

```json
{
  "$schema": "issue-1.json",
  "id": "AG12",
  "title": "The Clean-Up Says When Nothing Was Rented",
  "state": "Estimated",
  "size": "XS",
  "priority": "Medium",
  "rank": 2,
  "blockedBy": [],
  "covers": "a leg that failed before resolving the machine tooling rented nothing, and its clean-up says so instead of failing on a missing class",
  "openQuestions": 0
}
```

**Why this size.** one step of `build.yml` and one small test class run through the `WorkflowSteps` helper the workflow tests already use (`scripts`, `run`; the `java` stand-in is a few lines written into a directory put first on `PATH`). No shared action is involved: the clean-up and the step that resolves the tooling are this repository's own, inline in its workflow, so the fix is here alone.

**Estimate.** XS: 9 min, the measured median of XS over all repositories (n=105).

**What must be true.** An acceptance leg that fails before it resolved the machine tooling (a download that does not
answer, a checkout that fails) has rented nothing, and its *Delete what this run created* says so and passes, instead
of failing with a missing class on a classpath that was never resolved. A leg that did resolve the tooling sweeps
what it created as today, and that step still fails loud when the sweep cannot run.

## Plan

- Where it fails, read in `.github/workflows/build.yml`, job `acceptance`: the step order is checkout, the
  packages this run built, the pinned GraalVM, `Resolve the machine tooling` (`./mvnw ... dependency:build-classpath
  -Dmdep.outputFile=target/cp.txt`), the acceptance itself, then *Delete what this run created* (lines
  322-327) under `if: always()`, which runs `java -cp "$(cat target/cp.txt)" org.fuin.porelo.machines.Main sweep
  --mine`. When any step up to the resolve fails, `target/cp.txt` was never written: `cat` complains, the classpath
  is empty and `java` ends with "Could not find or load main class org.fuin.porelo.machines.Main", which fails the
  clean-up and puts a second, false error beside the real one.
- Only `Main acceptance` rents, and it runs from that classpath, so a leg without `target/cp.txt` has rented nothing.
  `dependency:build-classpath` writes the file only once the whole classpath has resolved.
- The change: the clean-up's script starts with a check of the file, and runs the sweep as today when it is there:

  ```
  [ -s target/cp.txt ] || { echo "::notice::The machine tooling was never resolved, so this leg rented nothing to delete."; exit 0; }
  java -cp "$(cat target/cp.txt)" org.fuin.porelo.machines.Main sweep --mine
  ```

  A leg that resolved the tooling reaches the sweep unchanged, so a sweep that cannot run or leaves something still
  fails the step. The check reads the file rather than the resolve step's outcome, so the script holds no runner
  expression and the test runs it as it is.
- A comment above the clean-up says why a missing classpath means nothing was rented.
- The update workflow rents nothing: it has no acceptance leg and no clean-up step.
- Red first: a new `MachineCleanupTest` takes the acceptance job's *Delete what this run created*
  (`WorkflowSteps.scripts("build.yml", ...)`) and runs it twice with a `java` stand-in first on `PATH`: with no
  `target/cp.txt` and a stand-in that fails, it expects exit 0 and the notice; with a `target/cp.txt` and a stand-in
  that exits 1, it expects the step to fail. The first case fails today: `java -cp ""` exits 1. The second passes
  today and guards that the sweep still fails loud.
- Level: unit test over the workflow step's own script; the first real leg that fails before the resolve shows it.

## Acceptance

- A leg whose checkout, package download, GraalVM or tooling download failed shows its clean-up green with the notice
  that nothing was rented, and the job fails on the step that really failed, alone.
- A leg that resolved the tooling runs `sweep --mine` as today, and the test shows that a failing sweep still fails
  the step.

## What makes it uncertain

- A leg cancelled in the middle of the resolve, with a partly written `target/cp.txt`, would still reach the sweep
  and fail on it. Maven writes the file in one go at the end, so this is not expected.
