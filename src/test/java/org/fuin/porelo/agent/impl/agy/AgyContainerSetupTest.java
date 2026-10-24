package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.fuin.porelo.agent.api.AgentException;
import org.fuin.porelo.agent.api.ContainerFile;
import org.fuin.porelo.agent.api.SetupContext;
import org.fuin.porelo.wire.Json;
import org.junit.jupiter.api.Test;

/**
 * Guards container setup files against credential leaks, interactive onboarding blocks, and
 * untrusted or overbroad workspaces.
 */
class AgyContainerSetupTest {

    private final AgyAgent agent = new AgyAgent();

    @Test
    void writesNoSetupFilesWhenNeitherEndpointNorTokenIsProvided() {
        assertThat(files("", ""))
                .as("an unbrokered shell receives no credential or onboarding files")
                .isEmpty();
    }

    @Test
    void writesSecretSettingsAndCompletedOnboardingForABrokeredTask() {
        final List<ContainerFile> files =
                files("porelo_pt_secret_token", "http://10.0.2.2:43123", "/workspace/repo");

        assertThat(files).as("both settings.json and onboarding.json are written").hasSize(2);

        final ContainerFile settings = files.get(0);
        assertThat(settings.path())
                .as("settings path in the container")
                .isEqualTo("/home/agent/.gemini/antigravity-cli/settings.json");
        assertThat(settings.ownerOnly()).as("settings file is owner-readable only").isTrue();
        assertThat(Json.parse(settings.content()))
                .as(
                        "settings select Gemini API-key mode, a free-tier default model, auto-approve tools, disable telemetry, and trust the workspace")
                .isEqualTo(
                        Map.of(
                                "modelProvider",
                                "gemini",
                                "model",
                                "Gemini 3.6 Flash (Low)",
                                "toolPermission",
                                "always-proceed",
                                "artifactReviewPolicy",
                                "always-proceed",
                                "enableTelemetry",
                                false,
                                "trustedWorkspaces",
                                List.of("/workspace/repo")));

        final ContainerFile onboarding = files.get(1);
        assertThat(onboarding.path())
                .as("onboarding cache path in the container")
                .isEqualTo("/home/agent/.gemini/antigravity-cli/cache/onboarding.json");
        assertThat(onboarding.ownerOnly())
                .as("onboarding cache file does not need secret restriction")
                .isFalse();
        assertThat(Json.parse(onboarding.content()))
                .as("onboarding flags prevent interactive first-run setup wizards")
                .isEqualTo(
                        Map.of(
                                "consumerOnboardingComplete",
                                true,
                                "enterpriseOnboardingComplete",
                                true,
                                "onboardingComplete",
                                true));
    }

    @Test
    void workspaceTextCannotEscapeItsJsonArray() {
        final ContainerFile settings =
                files("porelo_pt_token", "http://10.0.2.2:43123", "/work/quoted\"\\name")
                        .getFirst();
        assertThat(Json.parse(settings.content()))
                .as("quotes and backslashes in a task path cannot inject JSON fields")
                .isInstanceOf(Map.class)
                .extracting(map -> ((Map<?, ?>) map).get("trustedWorkspaces"))
                .isEqualTo(List.of("/work/quoted\"\\name"));
    }

    @Test
    void refusesPartialOrUnsafeEndpointAndTokenWithoutEchoingTheSecret() {
        for (final String[] pair :
                List.of(
                        new String[] {"", "http://10.0.2.2:43123"},
                        new String[] {"porelo_pt_secret_token", ""},
                        new String[] {"porelo_pt_secret_token", "   "})) {
            assertThatThrownBy(() -> files(pair[0], pair[1]))
                    .as("endpoint and task token must be supplied together")
                    .isInstanceOf(AgentException.class)
                    .hasMessageNotContaining("porelo_pt_secret_token")
                    .hasNoCause();
        }

        for (final String endpoint :
                List.of(
                        "file:///secret-marker",
                        "http://secret-marker@10.0.2.2:43123",
                        "http://10.0.2.2:43123?key=secret-marker",
                        "http://10.0.2.2:43123#secret-marker",
                        "http://10.0.2.2:70000/secret-marker",
                        "http://10.0.2.2/secret-marker\n\"",
                        "http://10.0.2.2/secret-marker\\")) {
            assertThatThrownBy(() -> files("porelo_pt_secret_token", endpoint))
                    .as("external endpoint input cannot leak via diagnostics")
                    .isInstanceOf(AgentException.class)
                    .hasMessageNotContaining("secret-marker")
                    .hasMessageNotContaining("porelo_pt_secret_token")
                    .hasNoCause();
        }

        for (final String token :
                List.of(
                        "secret-marker\n",
                        "secret-marker\r",
                        "secret-marker\"",
                        "secret-marker\\",
                        "secret-marker ",
                        "secret-marker\t")) {
            assertThatThrownBy(() -> files(token, "http://10.0.2.2:43123"))
                    .as("an unsafe task token is refused without echoing its contents")
                    .isInstanceOf(AgentException.class)
                    .hasMessageNotContaining("secret-marker")
                    .hasNoCause();
        }
    }

    @Test
    void refusesRelativeRootOrUnnormalizedWorkspacePaths() {
        for (final String workspace :
                List.of(
                        "",
                        "/",
                        "relative",
                        "/work/../",
                        "/work/./task",
                        "/work//task",
                        "/work/task/",
                        "/work/secret-marker\n")) {
            assertThatThrownBy(() -> files("porelo_pt_token", "http://10.0.2.2:43123", workspace))
                    .as("trust must not expand to an ambiguous or broader path")
                    .isInstanceOf(AgentException.class)
                    .hasMessageNotContaining("secret-marker")
                    .hasNoCause();
        }
    }

    @Test
    void aSignedInTaskReceivesOnlyAPhantomCredentialAndCompletedOnboarding() {
        final List<ContainerFile> files =
                agent.containerSetup()
                        .files(
                                new SetupContext(
                                        "synthetic-phantom-token",
                                        "oauth",
                                        "/workspace/repo",
                                        "http://10.0.2.2:43123",
                                        "google-sign-in"));
        assertThat(files)
                .as(
                        "the sign-in profile has settings, token, onboarding, and post-onboarding state")
                .hasSize(4);
        final ContainerFile settings = files.get(0);
        assertThat(settings.path())
                .as("settings stay in the task profile")
                .isEqualTo("/home/agent/.gemini/antigravity-cli/settings.json");
        assertThat(settings.ownerOnly()).as("settings are owner-only").isTrue();
        assertThat(Json.parse(settings.content()))
                .as("OAuth must not select Gemini API-key mode")
                .isEqualTo(
                        Map.of(
                                "toolPermission",
                                "always-proceed",
                                "artifactReviewPolicy",
                                "always-proceed",
                                "enableTelemetry",
                                false,
                                "trustedWorkspaces",
                                List.of("/workspace/repo")));
        final ContainerFile token = files.get(1);
        assertThat(token.path())
                .as("the CLI reads the task-local sign-in file")
                .isEqualTo("/home/agent/.gemini/antigravity-cli/antigravity-oauth-token");
        assertThat(token.ownerOnly()).as("the phantom credential is owner-only").isTrue();
        assertThat(Json.parse(token.content()))
                .as(
                        "the token profile contains only the phantom and a far expiry, with no renewal material")
                .isEqualTo(
                        Map.of(
                                "auth_method",
                                "consumer",
                                "token",
                                Map.of(
                                        "access_token",
                                        "synthetic-phantom-token",
                                        "token_type",
                                        "Bearer",
                                        "expiry",
                                        "2099-01-01T00:00:00Z")));
        assertThat(files.get(2).path())
                .as("onboarding cache is installed")
                .isEqualTo("/home/agent/.gemini/antigravity-cli/cache/onboarding.json");
        assertThat(Json.parse(files.get(2).content()))
                .as("all first-run flows are completed")
                .isEqualTo(
                        Map.of(
                                "consumerOnboardingComplete",
                                true,
                                "enterpriseOnboardingComplete",
                                true,
                                "onboardingComplete",
                                true));
        final ContainerFile state = files.get(3);
        assertThat(state.path())
                .as("post-login state uses the CLI's profile path")
                .isEqualTo("/home/agent/.gemini/antigravity-cli/jetski_state.pbtxt");
        assertThat(state.ownerOnly()).as("prompt completion contains no credential").isFalse();
        assertThat(state.content())
                .as(
                        "welcome, usage, agent configuration, workspace, and migrations cannot block startup")
                .contains(
                        "POST_ONBOARDING_STEP_TYPE_MANAGER_WELCOME",
                        "POST_ONBOARDING_STEP_TYPE_USAGE_MODE",
                        "POST_ONBOARDING_STEP_TYPE_AGENT_CONFIGURATION",
                        "POST_ONBOARDING_STEP_TYPE_ADD_WORKSPACE",
                        "key: 3",
                        "key: 4",
                        "key: 5");
        assertThat(state.content().split("MIGRATION_STATUS_COMPLETED", -1))
                .as("all three migrations are complete")
                .hasSize(4);
    }

    @Test
    void unsupportedCredentialTypesCannotSelectAnAuthenticationPath() {
        for (final String type : java.util.Arrays.asList(null, "", " ", "access-secret-marker")) {
            assertThatThrownBy(
                            () ->
                                    agent.containerSetup()
                                            .files(
                                                    new SetupContext(
                                                            "access-secret-marker",
                                                            type,
                                                            "/workspace",
                                                            "http://10.0.2.2:43123",
                                                            "google-sign-in")))
                    .as("unknown credential types fail closed without exposing input")
                    .isInstanceOf(AgentException.class)
                    .hasNoCause()
                    .hasMessageNotContaining("access-secret-marker")
                    .satisfies(
                            error ->
                                    assertThat(stackTrace(error))
                                            .as("the full diagnostic is safe")
                                            .doesNotContain("access-secret-marker"));
        }
    }

    @Test
    void signedInTasksRejectUnsafeInputsBeforeWritingAnyFile() {
        for (final SetupContext context :
                List.of(
                        new SetupContext(
                                "access-secret-marker",
                                "oauth",
                                "/workspace",
                                "file:///refresh-secret-marker",
                                "google-sign-in"),
                        new SetupContext(
                                "access-secret-marker\n",
                                "oauth",
                                "/workspace",
                                "http://10.0.2.2:43123",
                                "google-sign-in"),
                        new SetupContext(
                                "access-secret-marker",
                                "oauth",
                                "/work/../refresh-secret-marker",
                                "http://10.0.2.2:43123",
                                "google-sign-in"))) {
            assertThatThrownBy(() -> agent.containerSetup().files(context))
                    .as("OAuth shares the API-key input guards")
                    .isInstanceOf(AgentException.class)
                    .hasNoCause()
                    .satisfies(
                            error ->
                                    assertThat(stackTrace(error))
                                            .as("the diagnostic does not echo token or path input")
                                            .doesNotContain(
                                                    "access-secret-marker",
                                                    "refresh-secret-marker"));
        }
    }

    private static String stackTrace(Throwable error) {
        final java.io.StringWriter output = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(output));
        return output.toString();
    }

    private List<ContainerFile> files(String token, String endpoint) {
        return files(token, endpoint, "/workspace");
    }

    private List<ContainerFile> files(String token, String endpoint, String workspace) {
        return agent.containerSetup()
                .files(new SetupContext(token, "api-key", workspace, endpoint, "google"));
    }
}
