# Issues

What is open in this repository, grouped by state in the order of [workflow.yml](workflow.yml); a group
with nothing in it is left out. The index is generated from the issues' heads and never edited by hand.
Within a group, the order is the priority. The number is identity, not sequence.

## Idea

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ag23"></a>[AG23](AG23-The-Agent-s-Own-Telemetry-Goes-To-Porelo.md) | - | - | the adapter declares Antigravity CLI's own OpenTelemetry export pointed at Porelo's local receiver in the task, with every switch that would carry content - prompts, tool arguments and output, responses - set explicitly off |
| <a id="ag11"></a>[AG11](AG11-The-Acceptance-Legs-Run-Side-By-Side.md) | - | - | a Build's acceptance legs run at the same time, two machines held at once instead of one, the Build done about one leg sooner |
| <a id="ag27"></a>[AG27](AG27-The-Update-Checks-The-Sign-In-Clients.md) | - | - | the weekly update job stops a new Antigravity CLI release whose binary no longer holds the OAuth client IDs and secrets the login wrapper collects, so a release that moves them is not pinned |

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
| <a id="ag10"></a>[AG10](AG10-Depend-On-The-Agent-Protocol-Porelo-Provides.md) | S | - | the agent contract named at install time | 0 |
| <a id="ag06"></a>[AG06](AG06-A-Sign-In-Without-A-Browser-Is-Tested-On-Every-CLI-Update.md) | M | - | the sign-in at a terminal without a browser, checked on every CLI update so a release that drops it fails its pin bump | 0 |

## Backlog

| # | Size | Blocked by | What it covers | Open questions |
|---|---|---|---|---|
| <a id="ag02"></a>[AG02](AG02-The-Repository-s-Agent-Works-In-A-Porelo-Task.md) | S | [AG04](README.md#ag04) | the repository's agent moves into its own Porelo task, after a dry run on this machine | - |
| <a id="ag05"></a>[AG05](AG05-The-Acceptance-Legs-Run-On-The-Host-s-Forge.md) | S | [AG04](README.md#ag04) | the acceptance legs on the host's pool again, once it lends a forge job a machine by kind | 0 |

## Review

| # | Size | Blocked by | What it covers | Open questions |
|---|---|---|---|---|
| <a id="ag28"></a>[AG28](AG28-The-Docs-Say-What-The-Code-Does.md) | S | - | every sentence of the user-facing documentation checked against the code on main, and each one the code does not bear out fixed | - |
| <a id="ag18"></a>[AG18](AG18-The-Adapter-Reports-Each-Tool-Call-With-Its-Time.md) | M | - | the adapter reads each tool call from its agent's output and hands Porelo its tool, kind, start, end, a shell call's command line, whether it ran in the background, and a sub-agent's name and the calls made under it, in Porelo's common entry | 0 |
| <a id="ag21"></a>[AG21](AG21-An-Attended-Session-Takes-An-Opening-Text.md) | S | - | an attended session of Antigravity CLI opens on the person's opening text as its first turn and then waits for the person, the form (behind `-i`) declared in the agent definition and measured in a task | - |
| <a id="ag22"></a>[AG22](AG22-A-Finished-Run-Answers-Its-Last-Message.md) | S | - | a finished unattended Antigravity CLI run answers the text of its last message, declared in session.waiting.last_message, so a reader's answer is not empty; the declaration adds no screen rule and so changes nothing in how Porelo wakes the agent | - |
| <a id="ag25"></a>[AG25](AG25-Agy-Is-Installed-Root-Owned.md) | M | - | the antigravity binary a task starts and the agy wrapper that execs it are installed by root and owned by root, so the agent in a task cannot replace what Porelo starts as agy next time; the version stays pinned by the definition and moves only through the update job, and a self-update inside a task fails | - |
| <a id="ag29"></a>[AG29](AG29-The-Local-Sign-In-Goes-To-A-Host.md) | M | - | on the person's machine the adapter lists each Antigravity sign-in it finds - provider, kind, account where known, age and expiry where known, and whether it can be carried, never its value - and hands a chosen one to Porelo, which carries it into a rented host's vault | 2 |
| <a id="ag26"></a>[AG26](AG26-A-Leg-Installs-What-Its-Run-Built.md) | S | - | every acceptance leg of a run that publishes nothing - a dispatch, a tag - installs the packages that run built, so its result speaks for its own commit; only a push to main, which publishes its snapshot first, installs from the package repository | - |
| <a id="ag04"></a>[AG04](AG04-The-Build-Runs-On-The-Local-Forge-As-On-The-Central-One.md) | S | - | the workflows run 1:1 on the local Forgejo and the central forge, legs asked by kind, before the move | - |

## Finished

| # | Title | What was built |
|---|---|---|
| <a id="ag08"></a>[AG08](README.md#ag08) | Subagents Only As Many As The Task Allows | Antigravity CLI in a task starts no subagent unless the task allows it: a `PreToolUse` hook denies its five subagent tools, and Porelo refuses a task that allows more than 0, since no CLI setting counts them. |
| <a id="ag20"></a>[AG20](README.md#ag20) | The Build Takes Its Downloads From A Cache | A Build, an update run and the workflow check take the runner's packages from the Actions cache, each `.deb` checked against the hash its signed apt metadata names before it is installed; an entry of an old package list goes by itself. |
| <a id="ag19"></a>[AG19](README.md#ag19) | The Update Job Proposes New Antigravity CLI Versions | The weekly job "Follow Antigravity CLI" pins a new Antigravity CLI release once it has stood three days, builds and tests its packages, runs both acceptance legs on them and opens a pull request that says whether it is ready, and still checks the pinned digest in a week with nothing new. |
| <a id="ag16"></a>[AG16](README.md#ag16) | Third-Party Notices Ship With The Package | `porelo-agent-agy` carries the license texts of every component compiled into its executable from outside Porelo - SnakeYAML, JSpecify and the GraalVM runtime - in one `THIRD-PARTY-NOTICES` file, named in `copyright` and marked `%license` in the RPM, and `PackageNoticesCheck` refuses a package without it. |
| <a id="ag17"></a>[AG17](README.md#ag17) | The Acceptance Takes A Task's Container Name From Porelo | The acceptance scenarios name a task by its own name and take its container's name from `porelo task status --json`, so they hold however Porelo spells a container. |
| <a id="ag24"></a>[AG24](README.md#ag24) | The Issue Heads Are Checked Every Day | The daily shared-rules run checks every issue head and index with `issues check issues`, so a head that breaks the rules turns it red, not only the next push. |
| <a id="ag13"></a>[AG13](README.md#ag13) | Snapshots Not Published On The Forge Come From Upstream | A Build on a forge whose registry holds only its organization's packages takes the other Porelo snapshots from the anonymous repository `MAVEN_UPSTREAM_SNAPSHOTS_URL` names, the forge's token goes to the forge's registry alone, and a tag still reads no snapshot repository. |
| <a id="ag07"></a>[AG07](README.md#ag07) | The Sign-In Says How It Is Answered | `agy.yaml` declares `login.flow: paste-code`, so a person signing in to Antigravity CLI from a host is told to open the link and paste the code Google's page shows; tested in sprint 6m: passed. |
| <a id="ag03"></a>[AG03](README.md#ag03) | The Sign-In's Client Is Picked, Not Guessed | The login wrapper collects every OAuth client id (whole, the 13-digit consumer id included) and secret of the CLI, and `AgyCredentialExtractor` hands them to Porelo's login as `clientIdCandidates`/`clientSecretCandidates`, so the login keeps the pair the sign-in was granted to; a live sign-in stored the consumer client and renewed on the host with it. |
| <a id="ag01"></a>[AG01](README.md#ag01) | Agent Antigravity CLI | `porelo-agent-agy` ships Antigravity CLI (`agy`) as a packaged agent with its digest-verified binary download, brokering both the Google sign-in (`cloud-code` via `CLOUD_CODE_URL` with encrypted host-side OAuth renewal) and the Gemini API key (`google-generative-ai` via `GOOGLE_GEMINI_BASE_URL`), with the real credential never in the container and the terms' risk stated in `doc/index.md`. |
