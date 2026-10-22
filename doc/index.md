# Antigravity CLI adapter

The adapter is discovered through Porelo's published agent protocol. Its package supplies the
adapter executable and bill of materials; a task image downloads the pinned Antigravity CLI archive
and verifies its digest before extraction.

Its Google Gemini API-key setup points Antigravity CLI at the task's broker using only a phantom
token (`GEMINI_API_KEY` and `GOOGLE_GEMINI_BASE_URL` with `provider.adds_path: true`). Google
OAuth/Cloud Code Assist integration is tracked in porelo's
[OAuth provider issue](https://github.com/porelo-ai/porelo/blob/main/issues/providers/README.md#p12).

Google's terms call it a breach to use third-party software to access the service with Antigravity's
sign-in, and say such use may suspend the account. Porelo does not replace the CLI: the requests are
`agy`'s own, and the broker only keeps the token out of the container. Whether Google reads that as
third-party access is not known; the API-key path is not touched by it.

Build with GraalVM and `./mvnw -U -B -s settings.xml verify`. The native executable and Debian/RPM
packages are built by `./mvnw -U -B -s settings.xml -Pnative,dist verify` and require
`porelo (>= 0.5.1~snapshot.317)`. Main dependencies are the latest published Porelo agent API and
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

See [Decisions](decisions.md) for the adapter's design decisions.
