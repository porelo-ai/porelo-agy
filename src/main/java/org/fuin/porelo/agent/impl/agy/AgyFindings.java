package org.fuin.porelo.agent.impl.agy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.fuin.porelo.agent.api.AgentException;
import org.fuin.porelo.agent.api.Finder;
import org.fuin.porelo.agent.api.Finding;
import org.fuin.porelo.agent.api.Finding.Kind;
import org.fuin.porelo.agent.api.Finding.SignedIn;
import org.fuin.porelo.agent.api.Finding.What;
import org.jspecify.annotations.Nullable;

/**
 * What Antigravity CLI finds of itself on the host: its configuration directory and the consumer
 * sign-in in its token file, never a value.
 *
 * <p>Only reads the token file in the directory it is given, by the rules {@link
 * AgyCredentialExtractor} reads it with: neither the directory nor the file is a link, the file is
 * regular and at most {@link AgyCredentialExtractor#MAX_TOKEN_BYTES}. A sign-in is carriable
 * exactly when the extractor's own rule takes it. It runs no command, reaches no network and reads
 * no environment.
 *
 * <p>An import also needs the OAuth clients the sign-in may have been granted to, which the
 * extractor takes from the installed CLI. This never reads that binary: whether the CLI pinned on
 * the host holds the client is the host's question, so carriable answers for the token file alone.
 */
final class AgyFindings implements Finder {

    /** The provider of the agent's definition a consumer sign-in serves. */
    static final String PROVIDER = "google-sign-in";

    @Override
    public List<Finding> find(final Path configDirectory) {
        final Path given = configDirectory.toAbsolutePath();
        final List<Finding> findings = new ArrayList<>();
        final Directory directory = Directory.read(given);
        switch (directory.state()) {
            case ABSENT -> {}
            case UNREADABLE ->
                    findings.add(
                            sign(directory.path().toString(), SignedIn.CANNOT_SAY, null, null));
            case DIRECTORY -> {
                findings.add(
                        new Finding(
                                What.CONFIGURATION,
                                directory.path().toString(),
                                null,
                                null,
                                null,
                                null));
                findToken(TokenFile.read(directory.path()), findings);
            }
        }
        return List.copyOf(findings);
    }

    private static void findToken(final TokenFile file, final List<Finding> findings) {
        final String path = file.path().toString();
        switch (file.state()) {
            case ABSENT -> {}
            case UNREADABLE -> findings.add(sign(path, SignedIn.CANNOT_SAY, null, null));
            case UNUSABLE -> findings.add(sign(path, SignedIn.NOT_SIGNED_IN, null, null));
            case SIGNED_IN -> findings.add(sign(path, SignedIn.SIGNED_IN, true, Kind.SUBSCRIPTION));
        }
    }

    private static Finding sign(
            final String path,
            final SignedIn signedIn,
            final @Nullable Boolean importable,
            final @Nullable Kind kind) {
        return new Finding(
                What.SIGN_IN,
                path,
                signedIn,
                importable,
                signedIn == SignedIn.SIGNED_IN ? PROVIDER : null,
                kind);
    }

    /** The configuration directory, as the extractor verifies it. */
    record Directory(Path path, State state) {

        enum State {
            ABSENT,
            UNREADABLE,
            DIRECTORY
        }

        static Directory read(final Path given) {
            try {
                final BasicFileAttributes attributes =
                        Files.readAttributes(
                                given, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isDirectory()) {
                    return new Directory(
                            given.toRealPath(LinkOption.NOFOLLOW_LINKS), State.DIRECTORY);
                }
                // A link is not followed, so what it leads to cannot be told; a file holds nothing.
                return new Directory(
                        given, attributes.isSymbolicLink() ? State.UNREADABLE : State.ABSENT);
            } catch (NoSuchFileException ex) {
                return new Directory(given, State.ABSENT);
            } catch (IOException | SecurityException ex) {
                return new Directory(given, State.UNREADABLE);
            }
        }
    }

    /**
     * The token file, read once; only its state, expiry and last change leave this class.
     *
     * @param path Where it is.
     * @param state What it holds.
     * @param expires When the access token expires, for a sign-in only.
     * @param changed When the file was last written, for a sign-in only.
     */
    record TokenFile(Path path, State state, @Nullable Instant expires, @Nullable Instant changed) {

        enum State {
            ABSENT,
            UNREADABLE,
            UNUSABLE,
            SIGNED_IN
        }

        static TokenFile read(final Path directory) {
            final Path file = directory.resolve(AgyCredentialExtractor.TOKEN_FILE);
            final int limit = AgyCredentialExtractor.MAX_TOKEN_BYTES;
            final byte[] bytes;
            final Instant changed;
            try {
                final BasicFileAttributes attributes =
                        Files.readAttributes(
                                file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isSymbolicLink()) {
                    // Not followed, as the extractor follows none; what it leads to is not known.
                    return state(file, State.UNREADABLE);
                }
                if (!attributes.isRegularFile() || attributes.size() > limit) {
                    return state(file, State.UNUSABLE);
                }
                changed = attributes.lastModifiedTime().toInstant();
                try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                    bytes = input.readNBytes(limit + 1);
                }
            } catch (NoSuchFileException ex) {
                return state(file, State.ABSENT);
            } catch (IOException | SecurityException ex) {
                return state(file, State.UNREADABLE);
            }
            if (bytes.length > limit) {
                return state(file, State.UNUSABLE);
            }
            final AgyCredentialExtractor.ConsumerSignIn signIn;
            try {
                signIn = AgyCredentialExtractor.consumerSignIn(bytes);
            } catch (AgentException ex) {
                return state(file, State.UNUSABLE);
            }
            return new TokenFile(
                    file,
                    State.SIGNED_IN,
                    Instant.ofEpochMilli(Long.parseLong(signIn.expiresAt())),
                    changed);
        }

        private static TokenFile state(final Path file, final State state) {
            return new TokenFile(file, state, null, null);
        }
    }
}
