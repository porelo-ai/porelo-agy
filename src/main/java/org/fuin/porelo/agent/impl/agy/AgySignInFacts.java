package org.fuin.porelo.agent.impl.agy;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.fuin.porelo.agent.api.Finding;
import org.fuin.porelo.agent.api.Finding.SignedIn;
import org.fuin.porelo.agent.api.Finding.What;
import org.jspecify.annotations.Nullable;

/**
 * What the sign-in Antigravity CLI keeps in its token file says of itself beside its finding, for a
 * person who chooses which one to carry to a host: when it expires and when it last changed. Never
 * its value; the value goes to Porelo through {@link AgyCredentialExtractor} alone.
 *
 * <p>The token file names no account in plain text, only tokens, and a token is never decoded to
 * find one, so the account stays unknown. The expiry is the file's {@code expiry}, read by the
 * extractor's own rule; the last change is the file's own time, which a renewal moves.
 *
 * @param finding The finding these facts belong to.
 * @param account The account the sign-in is for, or {@code null} where the file names none.
 * @param expires When the sign-in's access token expires, or {@code null} where none is held.
 * @param changed When its file was last written, or {@code null} where that cannot be read.
 */
record AgySignInFacts(
        Finding finding,
        @Nullable String account,
        @Nullable Instant expires,
        @Nullable Instant changed) {

    /**
     * Returns the facts of each finding that is the sign-in in Antigravity CLI's token file, in the
     * findings' order.
     *
     * @param findings What {@link AgyFindings} found in the directory.
     * @param configDirectory Antigravity CLI's configuration directory.
     * @return The facts, one per sign-in found in the token file.
     */
    static List<AgySignInFacts> of(final List<Finding> findings, final Path configDirectory) {
        final AgyFindings.Directory directory =
                AgyFindings.Directory.read(configDirectory.toAbsolutePath());
        if (directory.state() != AgyFindings.Directory.State.DIRECTORY) {
            return List.of();
        }
        final AgyFindings.TokenFile file = AgyFindings.TokenFile.read(directory.path());
        if (file.state() != AgyFindings.TokenFile.State.SIGNED_IN) {
            return List.of();
        }
        final String path = file.path().toString();
        return findings.stream()
                .filter(finding -> finding.what() == What.SIGN_IN)
                .filter(finding -> finding.signedIn() == SignedIn.SIGNED_IN)
                .filter(finding -> path.equals(finding.path()))
                .map(finding -> new AgySignInFacts(finding, null, file.expires(), file.changed()))
                .toList();
    }
}
