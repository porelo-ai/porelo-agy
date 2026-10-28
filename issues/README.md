# Issues

What is open in this repository, grouped by state in the order Review, Rework, Implementing, Backlog,
Analyzed, Accepted, Idea, Estimated, Finished and Rejected; a group with nothing in it is left out.
Within a group, the order is the priority. The number is identity, not sequence.

## Implementing

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ag03"></a>[AG03](AG03-The-Sign-In-s-Client-Is-Picked-Not-Guessed.md) | S |  | the extractor hands Porelo every client id and secret of the CLI, so the login picks the pair the sign-in was granted to |

## Idea

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ag02"></a>[AG02](AG02-The-Repository-s-Agent-Works-In-A-Porelo-Task.md) |  | [PJ59](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md#pj59), porelo [P12](https://github.com/porelo-ai/porelo/blob/main/issues/providers/README.md#p12) | the repository's agent moves into its own Porelo task, wave 7b of the move |

## Finished

| # | Title | What was built |
|---|---|---|
| <a id="ag01"></a>[AG01](README.md#ag01) | Agent Antigravity CLI | `porelo-agent-agy` ships Antigravity CLI (`agy`) as a packaged agent with its digest-verified binary download, brokering both the Google sign-in (`cloud-code` via `CLOUD_CODE_URL` with encrypted host-side OAuth renewal) and the Gemini API key (`google-generative-ai` via `GOOGLE_GEMINI_BASE_URL`), with the real credential never in the container and the terms' risk stated in `doc/index.md`. |
