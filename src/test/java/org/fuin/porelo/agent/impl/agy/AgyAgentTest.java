package org.fuin.porelo.agent.impl.agy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.fuin.porelo.agent.api.AgentEnd;
import org.fuin.porelo.agent.api.AgentException;
import org.fuin.porelo.agent.api.AgentRegistry;
import org.fuin.porelo.agent.api.ProviderRoute;
import org.fuin.porelo.agent.api.RunRequest;
import org.fuin.porelo.agent.api.SessionIds;
import org.junit.jupiter.api.Test;

/** Guards Antigravity CLI command assembly, session discovery, and provider contract metadata. */
class AgyAgentTest {

    private final AgyAgent agent = new AgyAgent();

    @Test
    void discoveryNeedsOnlyThePublishedAgentContract() {
        assertThat(AgentRegistry.discover().require("agy").definition().binary())
                .as("the service descriptor makes the adapter discoverable")
                .isEqualTo("agy");
    }

    @Test
    void definitionDeclaresGoogleProviderAndRefusedDomains() {
        final var definition = agent.definition();
        assertThat(definition.name()).as("agent identifier").isEqualTo("agy");
        assertThat(definition.label())
                .as("human-readable agent label")
                .isEqualTo("Antigravity CLI");
        assertThat(definition.binary()).as("installed CLI binary name").isEqualTo("agy");
        assertThat(definition.configDirectory())
                .as("login configuration directory")
                .isEqualTo("~/.gemini/antigravity-cli");
        assertThat(definition.loginArguments())
                .as("login starts the CLI without flags")
                .isEqualTo(List.of());
        assertThat(agent.credentialExtractor())
                .as("sign-in extraction is adapter-specific")
                .isInstanceOf(AgyCredentialExtractor.class);
        assertThat(definition.provider()).as("provider declaration").isNotNull();
        assertThat(definition.provider().dialects())
                .as("Cloud Code Assist is a supported second dialect")
                .containsKey("cloud-code");
        final var cloud = definition.provider().dialects().get("cloud-code");
        assertThat(cloud.baseUrlEnvironment())
                .as("Cloud Code Assist uses its own broker base URL")
                .isEqualTo("CLOUD_CODE_URL");
        assertThat(cloud.addsPath()).as("the CLI appends the Cloud Code path").isTrue();
        assertThat(definition.provider().defaultProvider())
                .as("default provider uses the Google sign-in")
                .isEqualTo("google-sign-in");
        assertThat(definition.provider().dialect())
                .as("Gemini API dialect")
                .isEqualTo("google-generative-ai");
        assertThat(definition.provider().endpoint())
                .as("URL broker endpoint")
                .isEqualTo(ProviderRoute.Endpoint.URL);
        assertThat(definition.provider().baseUrlEnvironment())
                .as("environment variable pointing the GenAI SDK at the broker")
                .isEqualTo("GOOGLE_GEMINI_BASE_URL");
        assertThat(definition.provider().addsPath())
                .as("base URL omits /v1beta so the GenAI SDK appends it once")
                .isTrue();
        assertThat(definition.tokenEnvironment())
                .as("environment variable carrying the phantom token")
                .isEqualTo(Map.of("_default", "GEMINI_API_KEY", "oauth", "GOOGLE_SIGN_IN_TOKEN"));
        assertThat(definition.refusedDomains())
                .as("self-update and Clearcut telemetry domains are refused at the firewall")
                .containsExactly(
                        "antigravity-cli-auto-updater-974169037036.us-central1.run.app",
                        "play.googleapis.com",
                        "www.googleapis.com",
                        "antigravity-unleash.goog");
    }

    @Test
    void sessionIdsMatchJsonEndRecordAndConversationFiles() {
        final SessionIds sessionIds = agent.definition().sessionIds();
        assertThat(sessionIds)
                .as("session discovery metadata must be declared for resume")
                .isEqualTo(
                        new SessionIds(
                                Map.of("status", "SUCCESS"),
                                "conversation_id",
                                ".gemini/antigravity-cli/conversations",
                                ".db"));
    }

    @Test
    void sandboxedAndAttendedCommandsSkipPermissionsInTheContainer() {
        assertThat(agent.definition().sandboxedCommand())
                .as("interactive launch skips permissions inside the hardened container")
                .containsExactly("agy", "--dangerously-skip-permissions");
        assertThat(agent.definition().attendedCommand("gemini-4-argon-medium"))
                .as("attended launch passes the chosen model")
                .containsExactly(
                        "agy",
                        "--dangerously-skip-permissions",
                        "--model",
                        "gemini-4-argon-medium");
    }

    @Test
    void headlessCommandAssemblesPromptModelAndConversationFlags() {
        assertThat(
                        agent.headlessCommand(
                                new RunRequest(
                                        "Summarize the repository",
                                        "gemini-4-argon-medium",
                                        null,
                                        null,
                                        true,
                                        true)))
                .as("fresh unattended command uses json output and -p")
                .isEqualTo(
                        List.of(
                                "agy",
                                "--dangerously-skip-permissions",
                                "--model",
                                "gemini-4-argon-medium",
                                "--verbose",
                                "--output-format",
                                "json",
                                "-p",
                                "Summarize the repository"));

        assertThat(
                        agent.headlessCommand(
                                new RunRequest(
                                        "--help",
                                        "gemini-4-argon-medium",
                                        null,
                                        "11111111-2222-3333-4444-555555555555",
                                        false,
                                        true)))
                .as("resumed unattended command continues the recorded conversation")
                .isEqualTo(
                        List.of(
                                "agy",
                                "--dangerously-skip-permissions",
                                "--conversation",
                                "11111111-2222-3333-4444-555555555555",
                                "--model",
                                "gemini-4-argon-medium",
                                "--output-format",
                                "json",
                                "-p",
                                "--help"));
    }

    @Test
    void anUnsupportedTurnLimitIsRefused() {
        assertThatThrownBy(
                        () ->
                                agent.headlessCommand(
                                        new RunRequest("Run once", null, 3, null, false, false)))
                .as("Antigravity CLI has no --max-turns flag")
                .isInstanceOf(AgentException.class);
    }

    @Test
    void theTaskImageDisablesAutoUpdateAndLinksStandingInstructions() {
        assertThat(agent.imageLayer().asAgent())
                .as("auto-updater is disabled and standing instructions point at Porelo's guide")
                .anySatisfy(
                        line ->
                                assertThat(line)
                                        .as("auto-update is disabled")
                                        .contains("AGY_CLI_DISABLE_AUTO_UPDATE=true"))
                .anySatisfy(
                        line ->
                                assertThat(line)
                                        .as("standing instructions are linked")
                                        .contains(
                                                "ln -s "
                                                        + "/run/porelo/guide/README"
                                                        + "."
                                                        + "md /home/agent/.gemini/GEMINI"
                                                        + "."
                                                        + "md"));
    }

    @Test
    void agentDelegatesEndDetectionToFormatter() {
        final AgentEnd end =
                agent.ended(
                        "{\"conversation_id\":\"11111111-2222-3333-4444-555555555555\","
                                + "\"status\":\"SUCCESS\",\"response\":\"OK\"}");
        assertThat(end).as("completed turn outcome").isNotNull();
        assertThat(end.finished()).as("SUCCESS turn finishes cleanly").isTrue();
    }
}
