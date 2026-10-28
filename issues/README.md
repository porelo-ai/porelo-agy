# Issues

What is open in this repository, grouped by state in the order Review, Rework, Implementing, Backlog,
Analyzed, Accepted, Idea, Estimated, Finished and Rejected; a group with nothing in it is left out.
Within a group, the order is the priority. The number is identity, not sequence.

## Analyzed

| # | Size | Blocked by | What it covers | Open questions |
|---|---|---|---|---|
| <a id="ag02"></a>[AG02](AG02-The-Repository-s-Agent-Works-In-A-Porelo-Task.md) | S | [AG04](#ag04), porelo-project [PJ59](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj59), porelo [B298](https://github.com/porelo-ai/porelo/blob/main/issues/base/README.md#b298), the moves of wave 7a | the repository's agent moves into its own Porelo task, wave 7b of the move, after a dry run on this machine | 1 |

## Idea

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ag04"></a>[AG04](AG04-The-Build-Runs-On-The-Local-Forge-As-On-The-Central-One.md) |  | porelo-buildtools [BT25](https://github.com/porelo-ai/porelo-buildtools/blob/main/issues/README.md#bt25), [BT24](https://github.com/porelo-ai/porelo-buildtools/blob/main/issues/README.md#bt24), porelo-host [HO12](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho12), [HO27](https://github.com/porelo-ai/porelo-host/blob/main/issues/README.md#ho27) | the workflows run 1:1 on the local Forgejo and the central forge, legs asked by kind, before the move |

## Finished

| # | Title | What was built |
|---|---|---|
| <a id="ag03"></a>[AG03](README.md#ag03) | The Sign-In's Client Is Picked, Not Guessed | The login wrapper collects every OAuth client id (whole, the 13-digit consumer id included) and secret of the CLI, and `AgyCredentialExtractor` hands them to Porelo's login as `clientIdCandidates`/`clientSecretCandidates`, so the login keeps the pair the sign-in was granted to; a live sign-in stored the consumer client and renewed on the host with it. |
| <a id="ag01"></a>[AG01](README.md#ag01) | Agent Antigravity CLI | `porelo-agent-agy` ships Antigravity CLI (`agy`) as a packaged agent with its digest-verified binary download, brokering both the Google sign-in (`cloud-code` via `CLOUD_CODE_URL` with encrypted host-side OAuth renewal) and the Gemini API key (`google-generative-ai` via `GOOGLE_GEMINI_BASE_URL`), with the real credential never in the container and the terms' risk stated in `doc/index.md`. |
