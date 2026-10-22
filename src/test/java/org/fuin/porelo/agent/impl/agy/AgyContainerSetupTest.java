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

    private List<ContainerFile> files(String token, String endpoint) {
        return files(token, endpoint, "/workspace");
    }

    private List<ContainerFile> files(String token, String endpoint, String workspace) {
        return agent.containerSetup()
                .files(new SetupContext(token, "api-key", workspace, endpoint, "google"));
    }
}
