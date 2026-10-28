# AG03 — The Sign-In's Client Is Picked, Not Guessed

**Status:** Implementing; the pick is porelo's, in its next snapshot.
**Size:** S - the extractor and the definition's wrapper, and their tests; the pick itself is
porelo's.

**What must be true.** A Google sign-in collected from Antigravity CLI renews on the host: the
renewal names the client the refresh token was granted to, so Google never answers
`401 invalid_client`.

## Why

A due sign-in renewed by the broker was refused with `401 invalid_client`. The CLI binary holds
three client ids and three client secrets. The wrapper in `agy.yaml` writes `grep -m 1`'s first id and
first secret into `.client-id` and `.client-secret`, the extractor prefers those files, and its own
scan of the binary also takes the first match of each.

Measured in the pinned 1.4.5 binary:

- The strings lie apart in the Go string pool, so neither their order nor their neighborhood says
  which belong together.
- The code chooses the client by sign-in kind: `gcp` takes a 12-digit id with the second secret,
  `consumer` a 13-digit id with the first. The sign-in Porelo collects is `consumer`, and the id
  pattern `[0-9]{12}-…` never matches a 13-digit id whole, so the renewal named the `gcp` id with the
  `consumer` secret, a pair Google does not know.

Reading the pairing from the machine code would tie the adapter to x86-64 and to one compiler's code
shape. Porelo instead tries the candidate pairs once with the fresh refresh token at login and keeps
the one that answers.

## Plan

- Extraction runs on the host over the collected configuration directory, which holds no binary, so
  the wrapper in `agy.yaml` writes every client id
  (`[0-9]{1,20}-[0-9a-z]{16,64}\.apps\.googleusercontent\.com`) and every client secret into
  `.client-ids` and `.client-secrets`, and removes the single `.client-id` and `.client-secret` of an
  older image.
- `AgyCredentialExtractor` reads those lists (else scans the binary, as before), and hands them to
  Porelo as the two candidate attributes agreed with porelo (`clientIdCandidates`,
  `clientSecretCandidates`, `\n`-joined, distinct, in the binary's order, at most eight each). It
  sets no single client id or secret and ignores the old single files: they hold the wrong pair.
- Red first: a made-up binary holding a 12-digit and a 13-digit id and two secrets. Today's extractor
  returns the 13-digit id cut to 12 digits and only the first secret; the test asks for both whole ids
  and both secrets.

## Acceptance

- The red test above fails on today's code and passes with the change.
- A binary with no id or no secret, or with more candidates than the agreed limit, is refused, and the
  message holds no value from the binary.
- On the pinned 1.4.5 binary the extractor hands over three ids, the 13-digit one whole, and three
  secrets (checked by count and length, nothing printed).
- A fresh `vault login agy` on the new snapshots renews a due sign-in on the host after the refused
  tries: the task's `vault.log` says `renewed the sign-in for 'google-sign-in' on the host`.
