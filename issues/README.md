# Issues

What is open in this repository, grouped by state in the order of [workflow.yml](workflow.yml); a group
with nothing in it is left out. The index is generated from the issues' heads and never edited by hand.
Within a group, the order is the priority. The number is identity, not sequence.

## Idea

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ag23"></a>[AG23](AG23-The-Agent-s-Own-Telemetry-Goes-To-Porelo.md) | - | - | the adapter declares Antigravity CLI's own OpenTelemetry export pointed at Porelo's local receiver in the task, with every switch that would carry content - prompts, tool arguments and output, responses - set explicitly off |
| <a id="ag11"></a>[AG11](AG11-The-Acceptance-Legs-Run-Side-By-Side.md) | - | - | a Build's acceptance legs run at the same time, two machines held at once instead of one, the Build done about one leg sooner |

## Accepted

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ag09"></a>[AG09](AG09-Mutation-Testing-With-PIT.md) | - | - | mutation testing by hand: a `pit` profile outside CI with no threshold, and a report sorting survivors into real gaps and harmless ones |

## Estimated

| # | Size | Blocked by | What it covers | Open questions |
|---|---|---|---|---|
| <a id="ag14"></a>[AG14](AG14-The-Subagent-Scenario-Proves-The-Denial-By-What-Ran.md) | S | [AG18](README.md#ag18) | the subagent scenario decides by what the task ran, not by the word the model answers; it failed once on ubuntu and passed on its re-run | 0 |
| <a id="ag15"></a>[AG15](AG15-The-Tests-Follow-The-Pyramid.md) | XS | - | the suites by level with measured times, the refused hosts already held in the unit phase by AgyAgentTest; the timeout test waits 100 ms | - |
| <a id="ag12"></a>[AG12](AG12-The-Clean-Up-Says-When-Nothing-Was-Rented.md) | XS | - | a leg that failed before resolving the machine tooling rented nothing, and its clean-up says so instead of failing on a missing class | 0 |
| <a id="ag06"></a>[AG06](AG06-A-Sign-In-Without-A-Browser-Is-Tested-On-Every-CLI-Update.md) | M | [AG19](README.md#ag19) | the sign-in at a terminal without a browser, checked on every CLI update so a release that drops it fails its pin bump | 0 |
| <a id="ag10"></a>[AG10](AG10-Depend-On-The-Agent-Protocol-Porelo-Provides.md) | S | - | the agent contract named at install time | 0 |

## Backlog

| # | Size | Blocked by | What it covers | Open questions |
|---|---|---|---|---|
| <a id="ag02"></a>[AG02](AG02-The-Repository-s-Agent-Works-In-A-Porelo-Task.md) | S | [AG04](README.md#ag04) | the repository's agent moves into its own Porelo task, after a dry run on this machine | - |
| <a id="ag05"></a>[AG05](AG05-The-Acceptance-Legs-Run-On-The-Host-s-Forge.md) | S | [AG04](README.md#ag04) | the acceptance legs on the host's pool again, once it lends a forge job a machine by kind | 0 |

## Implementing

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ag24"></a>[AG24](AG24-The-Issue-Heads-Are-Checked-Every-Day.md) | XS | - | a daily run checks this repository's issue heads and index with the released porelo-release, as buildtools' shared rules do |

## Review

| # | Size | Blocked by | What it covers | Open questions |
|---|---|---|---|---|
| <a id="ag17"></a>[AG17](AG17-The-Acceptance-Takes-A-Task-s-Container-Name-From-Porelo.md) | XS | - | the acceptance scenarios name a task by its own name in Porelo's commands and take its container's name from `porelo task status --json`, never built as `porelo-<project>-<task>` | - |
| <a id="ag18"></a>[AG18](AG18-The-Adapter-Reports-Each-Tool-Call-With-Its-Time.md) | M | - | the adapter reads each tool call from its agent's output and hands Porelo its tool, kind, start, end, a shell call's command line, whether it ran in the background, and a sub-agent's name and the calls made under it, in Porelo's common entry | 0 |
| <a id="ag21"></a>[AG21](AG21-An-Attended-Session-Takes-An-Opening-Text.md) | S | - | an attended session of Antigravity CLI opens on the person's opening text as its first turn and then waits for the person, the form (behind `-i`) declared in the agent definition and measured in a task | - |
| <a id="ag22"></a>[AG22](AG22-A-Finished-Run-Answers-Its-Last-Message.md) | S | - | a finished unattended Antigravity CLI run answers the text of its last message, declared in session.waiting.last_message, so a reader's answer is not empty; the declaration adds no screen rule and so changes nothing in how Porelo wakes the agent | - |
| <a id="ag13"></a>[AG13](AG13-Snapshots-Not-Published-On-The-Forge-Come-From-Upstream.md) | S | - | on a forge whose registry holds only its organization's packages, the other Porelo snapshots come from the anonymous repository `MAVEN_UPSTREAM_SNAPSHOTS_URL` names, the forge's token sent to the forge alone | - |
| <a id="ag08"></a>[AG08](AG08-Subagents-Only-As-Many-As-The-Task-Allows.md) | S | - | no subagent in a task unless the task allows it, switched off by a hook, measured to hold at the pin; a task allowing more is refused by Porelo | - |
| <a id="ag20"></a>[AG20](AG20-The-Build-Takes-Its-Downloads-From-A-Cache.md) | S | - | the runner's packages come from the Actions cache, keyed by their package manifest, and the agy archive the task image needs is named as cacheable only once the rented machines can take a staged file; what must stay live is named, and an entry nobody reads for 7 days is removed by GitHub | - |
| <a id="ag04"></a>[AG04](AG04-The-Build-Runs-On-The-Local-Forge-As-On-The-Central-One.md) | S | - | the workflows run 1:1 on the local Forgejo and the central forge, legs asked by kind, before the move | - |
| <a id="ag16"></a>[AG16](AG16-Third-Party-Notices-Ship-With-The-Package.md) | S | - | the license texts of what the executable compiles in from outside Porelo - SnakeYAML, JSpecify, the GraalVM runtime - in one THIRD-PARTY-NOTICES file, in the deb's doc directory and as `%license` in the rpm | - |

## Finished

| # | Title | What was built |
|---|---|---|
| <a id="ag19"></a>[AG19](AG19-The-Update-Job-Proposes-New-Antigravity-CLI-Versions.md) | The Update Job Proposes New Antigravity CLI Versions | the weekly update job follows the Antigravity CLI as porelo-claude-code's follows Claude Code: it finds a new release, pins it, runs the unit suite and both acceptance legs on the candidate packages, and opens the pull request, instead of only re-checking the pinned digest |
| <a id="ag07"></a>[AG07](README.md#ag07) | The Sign-In Says How It Is Answered | `agy.yaml` declares `login.flow: paste-code`, so a person signing in to Antigravity CLI from a host is told to open the link and paste the code Google's page shows; tested in sprint 6m: passed. |
| <a id="ag03"></a>[AG03](README.md#ag03) | The Sign-In's Client Is Picked, Not Guessed | The login wrapper collects every OAuth client id (whole, the 13-digit consumer id included) and secret of the CLI, and `AgyCredentialExtractor` hands them to Porelo's login as `clientIdCandidates`/`clientSecretCandidates`, so the login keeps the pair the sign-in was granted to; a live sign-in stored the consumer client and renewed on the host with it. |
| <a id="ag01"></a>[AG01](README.md#ag01) | Agent Antigravity CLI | `porelo-agent-agy` ships Antigravity CLI (`agy`) as a packaged agent with its digest-verified binary download, brokering both the Google sign-in (`cloud-code` via `CLOUD_CODE_URL` with encrypted host-side OAuth renewal) and the Gemini API key (`google-generative-ai` via `GOOGLE_GEMINI_BASE_URL`), with the real credential never in the container and the terms' risk stated in `doc/index.md`. |
