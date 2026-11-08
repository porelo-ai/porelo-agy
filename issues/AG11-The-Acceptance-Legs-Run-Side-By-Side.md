# AG11 — The Acceptance Legs Run Side By Side

**Status:** Idea.

**What must be true.** A Build's acceptance legs, one per operating system, run at the same time instead of one
after the other, so a Build that runs them is done about one leg sooner.

## Why

- **The change:** `build.yml`'s acceptance matrix drops `max-parallel: 1`. Nothing else changes.
- **Why the legs run one at a time today:** each leg rents its own machine from the shared vCPU limit that the
  other repositories' legs draw on too. Side by side, one Build of this repository holds two machines at once
  instead of one, and Builds of several repositories pushed together can reach the limit, so a leg waits or fails
  to rent.
- **What it costs:** the same machine time and runner minutes per Build (two legs of 10 to 13 minutes each); what
  changes is how many machines one Build holds at a time, two instead of one, and the Build's wall time, shorter by
  about one leg.
