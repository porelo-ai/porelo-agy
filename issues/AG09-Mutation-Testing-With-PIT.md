# AG09 — Mutation Testing With PIT

**Status:** Accepted.

**What must be true.** A person can run mutation testing over this repository by hand and read which changes to the
code no test notices, sorted into real gaps and harmless ones.

- A `pit` profile, run by hand: not in CI and with no threshold.
- PIT and its JUnit 5 plugin are pinned.
- It runs on a VM where tests call real tools.
- A report sorts the survivors into real gaps and harmless ones.

**The model.** The `pit` profile in the messaging repository's `pom.xml`.
