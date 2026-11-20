---
name: adapter-building
description: Rules shared by the Porelo agent adapter repositories: building an adapter against the published agent API, and measuring what an agent asks at first run. Use when building, changing or measuring an agent adapter.
---

> **BEGIN Shared Skill adapter-building** · sha256 `36c2d45c50f1499f` · changed 2026-11-20T07:25Z

# adapter-building

### Building an adapter

- **An adapter depends on the published Porelo agent API and wire artifacts, never on Porelo's
  implementation.** The agent's own facts live in `src/main/resources/agent/<agent>.yaml`; nothing
  outside `agents/` in Porelo knows the agent's wording, and nothing in the adapter hardcodes Porelo's.
- `./mvnw -o -B -s settings.xml test` is the fast gate; `verify` adds the acceptance module.
- **The release tooling is `porelo-release`**, resolved like `porelo-machines` through `-Pci-tools`;
  what an agent differs in is the `porelo.release.*` properties in `pom.xml`. At package time it is
  a plugin dependency of the exec plugin, so it never becomes a dependency of the package.
- **No test names the pinned version**: the update job bumps `agent.cli.version`, filtering carries
  it into the definition, and `PinAgreementTest` checks the pin against the pom.
- **A gate in the update job that stops reaches a person only through its pull request**, and
  `${{ github.token }}` may not open one unless the organization allows it; otherwise the request
  step needs `secrets.PORELO_UPDATE_TOKEN`, as the merge step uses.
- **Skills used:** `graal`, `java-code-review`, `test-quality`, `security-audit`,
  `concurrency-review`, `clean-code`, `solid-principles`.

### Measuring what an agent asks at first run

- **A first-run question is measured attended, at a real terminal, in `/workspace`, on an account
  where the agent never ran.** `--version` and `-p` never reach the first-run dialogs, and from
  `/home/agent` the agent asks to trust that directory instead.
- **The task image Porelo builds has `python3` but no `node`** where the base has a package manager; a
  project's own image may still lack it. So a probe that changes an agent's JSON writes the whole file,
  which works in every image.
- **Porelo's acceptance suite runs a stub agent without dialogs**, so what a real agent asks is checked
  only in the adapter's repository.

> **END Shared Skill adapter-building** · sha256 `36c2d45c50f1499f`
