# AG16 — Third-Party Notices Ship With The Package

```json
{
  "$schema": "issue-1.json",
  "id": "AG16",
  "title": "Third-Party Notices Ship With The Package",
  "state": "Idea",
  "size": "-",
  "priority": "Medium",
  "rank": 7,
  "blockedBy": [],
  "covers": "the license and notice texts of what the executable compiles in - SnakeYAML, JSpecify, the agent API, the wire classes, the GraalVM runtime - in one THIRD-PARTY-NOTICES file, in the deb's doc directory and as `%license` in the rpm"
}
```

**What must be true.** Whoever installs `porelo-agent-agy` finds the license and notice texts of every third-party component
compiled into its executable in one file, `/usr/share/doc/porelo-agent-agy/THIRD-PARTY-NOTICES`, named in `debian/copyright` and
marked `%license` in the RPM.

## Why

`src/deb/copyright/copyright` says the executable is compiled from the program and porelo-wire's classes alone. It
holds more: the build's runtime dependencies are porelo-agent-api, porelo-wire, SnakeYAML and JSpecify, and the executable is a GraalVM native image; the same set was read in the executables of the other adapters, not yet in this one's. Their licenses ask that their notices travel with the binary, and today
neither package carries them.

## The shape

- The file is made by the shared generator in porelo-buildtools' release tooling, which collects every compiled-in
  jar's `LICENSE` and `NOTICE` and the licenses they embed into one file with the full texts; the package build calls
  it through the plugin dependency it already has on `porelo-release`.
- The deb installs it in its doc directory and names it in `copyright`; the rpm marks it `%license`.
- Nothing is built before that generator is published from porelo-buildtools' `main`.

## Acceptance

- In a built package, the file names SnakeYAML, JSpecify, porelo-agent-api, porelo-wire and the GraalVM runtime,
  each with its full license text; `dpkg -L` and `rpm -qL` list it.
- A test fails when a runtime dependency in the package's bill of materials is missing from the file, so a new
  dependency cannot ship without its notice.

## To be checked

- Which notices the GraalVM runtime asks for in a native executable, and whether the generator gives them or they
  are added beside its output.
- That SnakeYAML is compiled into this repository's executable, read in a native build.
