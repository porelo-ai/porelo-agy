# Issues

What is open in this repository, grouped by state in the order Review, Rework, Implementing, Backlog,
Analyzed, Accepted, Idea, Estimated, Finished and Rejected; a group with nothing in it is left out.
Within a group, the order is the priority. The number is identity, not sequence.

## Review

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ag07"></a>[AG07](AG07-The-Sign-In-Says-How-It-Is-Answered.md) | XS | — | a person signing in to Antigravity CLI from a host is told to open the link and paste the code Google's page shows |

## Analyzed

| # | Size | Blocked by | What it covers | Open questions |
|---|---|---|---|---|
| <a id="ag02"></a>[AG02](AG02-The-Repository-s-Agent-Works-In-A-Porelo-Task.md) | S | [AG04](#ag04) | the repository's agent moves into its own Porelo task, after a dry run on this machine | 0 |
| <a id="ag04"></a>[AG04](AG04-The-Build-Runs-On-The-Local-Forge-As-On-The-Central-One.md) | S | — | the workflows run 1:1 on the local Forgejo and the central forge, legs asked by kind, before the move | 0 |
| <a id="ag08"></a>[AG08](AG08-Subagents-Only-As-Many-As-The-Task-Allows.md) | S | — | no subagent in a task unless the task allows it, switched off by a hook, measured to hold at the pin; a task allowing more is refused by Porelo | 0 |

## Idea

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ag06"></a>[AG06](AG06-A-Sign-In-Without-A-Browser-Is-Tested-On-Every-CLI-Update.md) |  | — | the sign-in at a terminal without a browser, checked on every CLI update so a release that drops it fails its pin bump |
| <a id="ag05"></a>[AG05](AG05-The-Acceptance-Legs-Run-On-The-Host-s-Forge.md) |  | [AG04](#ag04) | the acceptance legs on the host's pool again, once it lends a forge job a machine by kind |

## Finished

| # | Title | What was built |
|---|---|---|
| <a id="ag03"></a>[AG03](README.md#ag03) | The Sign-In's Client Is Picked, Not Guessed | The login wrapper collects every OAuth client id (whole, the 13-digit consumer id included) and secret of the CLI, and `AgyCredentialExtractor` hands them to Porelo's login as `clientIdCandidates`/`clientSecretCandidates`, so the login keeps the pair the sign-in was granted to; a live sign-in stored the consumer client and renewed on the host with it. |
| <a id="ag01"></a>[AG01](README.md#ag01) | Agent Antigravity CLI | `porelo-agent-agy` ships Antigravity CLI (`agy`) as a packaged agent with its digest-verified binary download, brokering both the Google sign-in (`cloud-code` via `CLOUD_CODE_URL` with encrypted host-side OAuth renewal) and the Gemini API key (`google-generative-ai` via `GOOGLE_GEMINI_BASE_URL`), with the real credential never in the container and the terms' risk stated in `doc/index.md`. |
