# Antigravity CLI adapter

The adapter is discovered through Porelo's published agent protocol. Its package supplies the
adapter executable and bill of materials; a task image downloads the pinned Antigravity CLI archive
and verifies its digest before extraction.

Google sign-in (`google-sign-in`) is the default provider. Run `porelo vault login agy` for the
CLI's attended login, or use `--dry-run` to preview it. The extractor reads the consumer sign-in
from `~/.gemini/antigravity-cli/antigravity-oauth-token` and collects every OAuth client ID and secret
of the installed CLI; Porelo's login tries them with the fresh sign-in and keeps the pair that renews
it, for encrypted host-side renewal. The login wrapper writes `.client-ids` and `.client-secrets` only in
the throwaway login container; it writes nothing in a brokered task.

Signed-in tasks use the `cloud-code` dialect through `CLOUD_CODE_URL` and receive only a phantom
access token in an owner-only `antigravity-oauth-token`, with a far expiry and completed onboarding
state. No refresh token, ID token or client secret enters the task profile. The firewall refuses
`www.googleapis.com` (the CLI's non-inference user-info request), `antigravity-unleash.goog`,
`play.googleapis.com` and the self-updater host.

The `google` API-key provider remains available explicitly. It uses `GEMINI_API_KEY` and
`GOOGLE_GEMINI_BASE_URL` with `provider.adds_path: true` and selects Gemini mode in task settings.
Both paths use phantom credentials; the broker keeps the real credential on the host.

Google's terms call it a breach to use third-party software to access the service with Antigravity's
sign-in, and say such use may suspend the account. Porelo does not replace the CLI: the requests are
`agy`'s own, and the broker only keeps the token out of the container. Whether Google reads that as
third-party access is not known; the API-key path is not touched by it.

Build with GraalVM and `./mvnw -U -B -s settings.xml verify`. The native executable and Debian/RPM
packages are built by `./mvnw -U -B -s settings.xml -Pnative,dist verify` and require
`porelo (>= 0.5.1~snapshot.318)`. Main dependencies are the latest published Porelo agent API and
wire snapshot selected by the imported BOM; no Porelo implementation checkout is required.

## Building in a task

The root `porelo.yml` drafts the build image: pinned Ubuntu and verified GraalVM and shfmt
downloads, with native-linkage and Debian/RPM packaging tools. It contains tools only; egress,
resource limits, agent selection and credentials belong to the project running the task.

Porelo does not read this repository-image declaration yet. A task-build measurement must derive
an equivalent disposable `project.yml` from its exact image block, outside the repository, and
run the applicable Build commands in that task. This demonstrates the declared toolchain, not
automatic repository-image discovery. GitHub checkout/cache/upload actions and `jdk --github`
are runner-specific: use a commit-scoped handover checkout, the image's pinned JDK and local
package inspection instead. No Maven verification or shared check is exempted.

Task profiles use `/home/agent/.gemini/antigravity-cli` for settings, onboarding state and SQLite
conversation stores, and `/home/agent/.gemini/GEMINI.md` for Porelo's standing instructions.

## Attended Google sign-in acceptance

To verify the Google sign-in path end to end with a dedicated Google AI account on a test machine
(about 3 minutes once the login image is built):

1. Open an interactive shell on the test machine and point `PORELO_VAULT` at a disposable vault file:
   `umask 077 && mkdir -p /tmp/agy-signin && export PORELO_VAULT=/tmp/agy-signin/vault.age`.
2. Initialize and unlock the disposable vault, typing an invented passphrase at each prompt (nothing
   is echoed or placed on a command line): `porelo vault init && porelo vault unlock`.
3. Preview the login without starting a container: `porelo vault login agy --dry-run`.
4. Run `porelo vault login agy`:
   - At `Select login method: > 1. Google OAuth`, press `Enter`.
   - Open the printed `https://accounts.google.com/o/oauth2/auth?...` URL in a browser signed into
     the dedicated Google AI account and approve access; Google redirects to
     `https://antigravity.google/oauth-callback` and displays an authorization code.
   - Paste that authorization code at `authorization code...` in the terminal and press `Enter`.
   - Once the CLI writes `antigravity-oauth-token`, Porelo collects `antigravity-oauth-token`,
     `.client-ids` and `.client-secrets`, picks the client that renews the sign-in, stores it in the
     encrypted vault and removes the login container
     automatically.
5. Verify with `porelo vault list` that `google-sign-in` (`oauth`) is stored with `client_id`,
   `expires_at` and `token_url`.
6. Run a signed-in task in `default`:
   `porelo task start check -p default -r default --agent agy --clearance deny -P "Reply with the single word PORELOOAUTH and nothing else."`
   and verify that the response contains `PORELOOAUTH`, that `/home/agent/.gemini/antigravity-cli/antigravity-oauth-token`
   in `porelo-default-check` holds only the `porelo_pt_` phantom token and `2099-01-01T00:00:00Z`,
   and that no refresh token, ID token, `.client-ids` or `.client-secrets` file exists in `/home/agent/.gemini/antigravity-cli`.
7. Remove the task and disposable vault:
   `porelo task remove porelo-default-check --force && porelo vault lock && rm -rf /tmp/agy-signin`.

See [Decisions](decisions.md) for the adapter's design decisions.
