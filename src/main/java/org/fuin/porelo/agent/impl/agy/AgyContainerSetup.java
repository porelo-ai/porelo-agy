package org.fuin.porelo.agent.impl.agy;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Pattern;
import org.fuin.porelo.agent.api.AgentException;
import org.fuin.porelo.agent.api.ContainerFile;
import org.fuin.porelo.agent.api.ContainerSetup;
import org.fuin.porelo.agent.api.SetupContext;

/**
 * Writes Antigravity CLI's container settings and onboarding cache so brokered API-key tasks start
 * without interactive setup prompts or telemetry.
 */
public final class AgyContainerSetup implements ContainerSetup {

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9._~+/-]+=*");

    private static final String ONBOARDING =
            """
            {
              "consumerOnboardingComplete": true,
              "enterpriseOnboardingComplete": true,
              "onboardingComplete": true
            }
            """;

    /** Creates a stateless container setup. */
    public AgyContainerSetup() {}

    /** {@inheritDoc} */
    @Override
    public List<ContainerFile> files(SetupContext context) {
        if (context.endpoint().isEmpty() && context.token().isEmpty()) {
            return List.of();
        }
        validateEndpoint(context.endpoint());
        if (!TOKEN.matcher(context.token()).matches()) {
            throw new AgentException(
                    "Antigravity CLI needs a nonempty task token without unsafe characters");
        }
        final String workspace = trustedWorkspace(context.workspace());
        final String settings =
                """
                {
                  "modelProvider": "gemini",
                  "toolPermission": "always-proceed",
                  "artifactReviewPolicy": "always-proceed",
                  "enableTelemetry": false,
                  "trustedWorkspaces": ["%s"]
                }
                """
                        .formatted(workspace);
        return List.of(
                ContainerFile.secret("/home/agent/.gemini/antigravity-cli/settings.json", settings),
                ContainerFile.of(
                        "/home/agent/.gemini/antigravity-cli/cache/onboarding.json", ONBOARDING));
    }

    private static String trustedWorkspace(String workspace) {
        if (!workspace.startsWith("/")
                || workspace.chars().anyMatch(Character::isISOControl)
                || List.of(workspace.substring(1).split("/", -1)).stream()
                        .anyMatch(
                                part -> part.isEmpty() || part.equals(".") || part.equals(".."))) {
            throw new AgentException(
                    "Antigravity CLI needs an absolute, normalized, non-root task workspace");
        }
        return workspace.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void validateEndpoint(String endpoint) {
        try {
            final URI uri = new URI(endpoint);
            if (!("http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || uri.getPort() == 0
                    || uri.getPort() > 65535
                    || endpoint.chars()
                            .anyMatch(c -> c < 0x21 || c > 0x7e || c == '"' || c == '\\')) {
                throw new AgentException(
                        "Antigravity CLI needs a plain HTTP or HTTPS broker endpoint");
            }
        } catch (URISyntaxException exception) {
            throw new AgentException("Antigravity CLI needs a plain HTTP or HTTPS broker endpoint");
        }
    }
}
