# AG16 — Third-Party Notices Ship With The Package

```json
{
  "$schema": "issue-1.json",
  "id": "AG16",
  "title": "Third-Party Notices Ship With The Package",
  "state": "Implementing",
  "size": "S",
  "priority": "Medium",
  "rank": 7,
  "blockedBy": [],
  "covers": "the license texts of what the executable compiles in from outside Porelo - SnakeYAML, JSpecify, the GraalVM runtime - in one THIRD-PARTY-NOTICES file, in the deb's doc directory and as `%license` in the rpm"
}
```

**Why this size.** `pom.xml`'s `dist` profile, `copyright` and one package check of this one repository, no contract
another repository reads.

**How it stands.** Backlog (operator, 2026-11-10); Implementing.

**What must be true.** Whoever installs `porelo-agent-agy` finds the license texts of every component compiled into its
executable from outside Porelo in one file, `/usr/share/doc/porelo-agent-agy/THIRD-PARTY-NOTICES`, named in `debian/copyright`
and marked `%license` in the RPM.

## Why

`src/deb/copyright/copyright` says the executable is compiled from the program and porelo-wire's classes alone. It
holds more: the build's runtime dependencies are SnakeYAML and JSpecify beside Porelo's agent API and wire classes, and the executable is a GraalVM native image. The licenses of SnakeYAML, JSpecify and the GraalVM runtime ask that their
texts travel with the binary, and today neither package carries them.

## Plan

- `pom.xml`, profile `dist`, phase `package`: `maven-dependency-plugin` writes the runtime classpath to
  `target/runtime-classpath.txt`; a second execution of the exec plugin, after `record-the-fetched-cli`, runs
  `porelo-release third-party-notices` on that classpath, the build's bill and `${java.home}` (the pinned GraalVM),
  writing `target/THIRD-PARTY-NOTICES`.
- `--own 'org\.fuin\.porelo:.*'`: Porelo's agent API and wire classes are Porelo's own; their licenses stay where
  they are, in `copyright` and the shipped `LICENSE` files. The generator refuses their GPL-3.0-or-later, which is not
  in its set, so without it the build would stop.
- The deb installs the file in `/usr/share/doc/porelo-agent-agy/`; the rpm as
  `/usr/share/licenses/porelo-agent-agy/THIRD-PARTY-NOTICES`, marked `%license`.
- `copyright`: the executable's stanza names SnakeYAML, JSpecify and the GraalVM runtime (GPL-2.0-only with the
  Classpath exception) and points to the file.
- Red first: `PackageNoticesCheck`, run by the `dist` profile after both packages beside `PackageVersionCheck`, reads
  the built packages: the deb holds the file, the rpm lists it as a license, and the file has a section for every
  Maven component of the shipped bill that is not Porelo's own, and one for the GraalVM runtime. Its harm: a package
  that ships compiled-in code without the texts its licenses ask for.

## Acceptance

- `-Pnative,dist verify` builds both packages and `PackageNoticesCheck` passes; it fails on a package without the
  file, and on a bill component the file has no section for.

## To be checked

- That SnakeYAML is compiled into this repository's executable, read in the native build.
