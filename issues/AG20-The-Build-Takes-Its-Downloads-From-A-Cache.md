# AG20 — The Build Takes Its Downloads From A Cache

```json
{
  "$schema": "issue-1.json",
  "id": "AG20",
  "title": "The Build Takes Its Downloads From A Cache",
  "state": "Implementing",
  "size": "S",
  "priority": "Medium",
  "rank": 2,
  "blockedBy": [],
  "covers": "the runner's packages come from the Actions cache, keyed by their package manifest, and the agy archive the task image needs is named as cacheable only once the rented machines can take a staged file; what must stay live is named, and an entry nobody reads for 7 days is removed by GitHub"
}
```

**Why this size.** one composite action and the Build of this repository, read against the acceptance path; no
contract another repository reads.

**What must be true.** A Build, an update run or the workflow check that runs on a pin it has seen before takes every
outside download it can from the Actions cache, not from the vendor's download host or the runner's package mirrors: an
outage of one of those hosts then fails only the first run on a new pin. Each cached file is still checked against its
pin before it is used, and an entry of an old pin goes by itself, so the cache never fills up.

## Plan

- Already cached by the shared `pinned-jdk` action, and left as it is: the pinned GraalVM (`pinned-graalvm-<sha256>`),
  the wrapper's Maven and the Maven repository.
- The runner's packages (`gcc`, `rpm`, `dpkg`, `zlib1g-dev` in `build.yml`): the package list is resolved from fresh, signed apt metadata, the `.deb` files
  are kept under `runner-apt-<runner image>-<manifest sha256>`, and each restored file is checked against the hash the
  metadata names before it is installed. The indexes stay live.
- The agy archive (`agy_cli_linux_x64.tar.gz`, pinned by digest) is fetched only inside the task image on the rented
  machine, never on the runner. It is kept under `agent-cli-<sha256>` once the shared machine tooling can stage a file
  from the runner onto a rented machine; until then it is named here as not cacheable, with the task's base image and
  its packages.
- Not cacheable, and kept online on purpose: the release answer `check-pin` reads for the publisher's digest, the
  publication indexes on Artifactory (a cached one could approve replacing a release), Maven snapshots asked for anew
  with `-U`, and what the acceptance legs install on a rented machine, which has no Actions cache.
- Retention: GitHub removes a cache entry nobody read for 7 days, and every key carries the digest or the manifest's
  hash, so an old pin's entries go by themselves while the current ones are read on every run. No step deletes
  entries.

## Acceptance

- The second Build on unchanged packages shows a cache hit for the runner's packages and fetches none from the
  mirrors.
- A cached package changed on purpose (one byte) is refused by its hash and fetched again.
- After the package list changes, the Build writes an entry under the new manifest hash, and the old one is gone once
  nobody read it for 7 days.
