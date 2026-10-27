# Issues

What is open in this repository, grouped by state in the order Review, Rework, Implementing, Backlog,
Analyzed, Accepted, Idea, Estimated, Finished and Rejected; a group with nothing in it is left out.
Within a group, the order is the priority. The number is identity, not sequence.

## Finished

| # | Title | What was built |
|---|---|---|
| <a id="ag01"></a>[AG01](README.md#ag01) | Agent Antigravity CLI | `porelo-agent-agy` ships Antigravity CLI (`agy`) as a packaged agent with its digest-verified binary download, brokering both the Google sign-in (`cloud-code` via `CLOUD_CODE_URL` with encrypted host-side OAuth renewal) and the Gemini API key (`google-generative-ai` via `GOOGLE_GEMINI_BASE_URL`), with the real credential never in the container and the terms' risk stated in `doc/index.md`. |
