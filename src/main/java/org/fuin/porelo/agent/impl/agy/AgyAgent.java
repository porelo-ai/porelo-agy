package org.fuin.porelo.agent.impl.agy;

import org.fuin.porelo.agent.api.AgentEnd;
import org.fuin.porelo.agent.api.AgentMain;
import org.fuin.porelo.agent.api.ContainerSetup;
import org.fuin.porelo.agent.api.CredentialExtractor;
import org.fuin.porelo.agent.api.LogFormatter;
import org.fuin.porelo.agent.api.YamlAgent;
import org.jspecify.annotations.Nullable;

/** Antigravity CLI's definition and agent protocol entry point. */
public final class AgyAgent extends YamlAgent {

    /** Loads the packaged Antigravity CLI definition. */
    public AgyAgent() {
        super("agy");
    }

    /** {@inheritDoc} */
    @Override
    public ContainerSetup containerSetup() {
        return new AgyContainerSetup();
    }

    /** {@inheritDoc} */
    @Override
    public CredentialExtractor credentialExtractor() {
        return new AgyCredentialExtractor();
    }

    /** {@inheritDoc} */
    @Override
    public LogFormatter logFormatter() {
        return new AgyJsonFormatter();
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable AgentEnd ended(String line) {
        return AgyJsonFormatter.ended(line);
    }

    /**
     * Serves the published agent protocol.
     *
     * @param args Protocol mode and its arguments.
     */
    public static void main(String[] args) {
        AgentMain.run(new AgyAgent(), args);
    }
}
