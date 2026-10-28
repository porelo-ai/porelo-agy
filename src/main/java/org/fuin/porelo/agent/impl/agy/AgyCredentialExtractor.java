package org.fuin.porelo.agent.impl.agy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fuin.porelo.agent.api.AgentException;
import org.fuin.porelo.agent.api.Credential;
import org.fuin.porelo.agent.api.CredentialExtractor;
import org.fuin.porelo.wire.Json;
import org.fuin.porelo.wire.JsonException;

/**
 * Collects Antigravity's consumer sign-in and every OAuth client of the installed CLI, so the login
 * on the host can pick the client the sign-in was granted to.
 */
public final class AgyCredentialExtractor implements CredentialExtractor {
    private static final int MAX_TOKEN_BYTES = 1024 * 1024;
    private static final int MAX_CANDIDATES = 8;
    private static final int MAX_CANDIDATE_FILE_BYTES = 4096;
    private static final long MAX_BINARY_BYTES = 512L * 1024 * 1024;
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9._~+/-]+=*");
    // The CLI's clients differ in the number of leading digits, so none may be cut.
    private static final Pattern BINARY_CLIENT_ID =
            Pattern.compile("[0-9]{1,20}-[0-9a-z]{16,64}\\.apps\\." + "googleusercontent\\.com");
    private static final Pattern BINARY_CLIENT_SECRET =
            Pattern.compile("GOC" + "SPX-[A-Za-z0-9_-]{28}");
    // Agreed with porelo's login, which tries each id with each secret; local until agents/api has
    // them.
    static final String CLIENT_ID_CANDIDATES = "clientIdCandidates";
    static final String CLIENT_SECRET_CANDIDATES = "clientSecretCandidates";
    private static final String NO_CLIENT_ID =
            "Antigravity CLI OAuth client ID could not be read from the installed CLI";
    private static final String NO_CLIENT_SECRET =
            "Antigravity CLI OAuth client secret could not be read from the installed CLI";
    private final Path cliBinary;
    private final Pattern binaryClientId;
    private final Pattern binaryClientSecret;

    /** Creates an extractor using the installed container CLI. */
    public AgyCredentialExtractor() {
        this(Path.of("/home/agent/.local/bin/antigravity"));
    }

    /**
     * Creates an extractor with an explicit fallback CLI binary.
     *
     * @param cliBinary Regular binary to scan when the configuration directory holds none.
     */
    public AgyCredentialExtractor(Path cliBinary) {
        this(cliBinary, BINARY_CLIENT_ID, BINARY_CLIENT_SECRET);
    }

    AgyCredentialExtractor(Path cliBinary, Pattern binaryClientId, Pattern binaryClientSecret) {
        this.cliBinary = cliBinary;
        this.binaryClientId = binaryClientId;
        this.binaryClientSecret = binaryClientSecret;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Credential> extract(Path configDirectory) {
        final Path directory = realDirectory(configDirectory);
        final Optional<byte[]> bytes =
                readSmallFile(
                        directory.resolve("antigravity-oauth-token"),
                        MAX_TOKEN_BYTES,
                        "Antigravity CLI OAuth token file");
        if (bytes.isEmpty()) {
            return Optional.empty();
        }
        final Map<?, ?> root = object(new String(bytes.orElseThrow(), StandardCharsets.UTF_8));
        if (!"consumer".equals(root.get("auth_method"))
                || !(root.get("token") instanceof Map<?, ?> token)) {
            throw new AgentException(
                    "Antigravity CLI credential must hold a consumer OAuth sign-in");
        }
        final String access = token(token, "access_token");
        final String refresh = token(token, "refresh_token");
        final String expiresAt = expiry(token);
        final String clientIds =
                candidates(directory, ".client-ids", binaryClientId, 112, NO_CLIENT_ID);
        final String clientSecrets =
                candidates(directory, ".client-secrets", binaryClientSecret, 35, NO_CLIENT_SECRET);
        return Optional.of(
                new Credential(
                        "oauth",
                        access,
                        Map.of(
                                Credential.REFRESH_TOKEN,
                                refresh,
                                Credential.EXPIRES_AT,
                                expiresAt,
                                Credential.TOKEN_URL,
                                "https://oauth2.googleapis.com/token",
                                CLIENT_ID_CANDIDATES,
                                clientIds,
                                CLIENT_SECRET_CANDIDATES,
                                clientSecrets)));
    }

    private static Map<?, ?> object(String json) {
        final Object parsed;
        try {
            parsed = Json.parse(json);
        } catch (JsonException exception) {
            // Parser diagnostics can repeat credential-bearing input.
            throw new AgentException("Cannot parse Antigravity CLI OAuth credential JSON");
        }
        if (!(parsed instanceof Map<?, ?> fields)) {
            throw new AgentException("Antigravity CLI OAuth credential JSON must be an object");
        }
        return fields;
    }

    private static String required(Map<?, ?> fields, String key) {
        if (!(fields.get(key) instanceof String value) || value.isBlank()) {
            throw new AgentException(
                    "Antigravity CLI OAuth credential is missing a required field: " + key);
        }
        return value;
    }

    private static String token(Map<?, ?> fields, String key) {
        final String value = required(fields, key);
        if (!TOKEN.matcher(value).matches()) {
            throw new AgentException(
                    "Antigravity CLI OAuth credential has an unusable token field: " + key);
        }
        return value;
    }

    private static String expiry(Map<?, ?> token) {
        final String expiry = required(token, "expiry");
        try {
            final long milliseconds = Instant.parse(expiry).toEpochMilli();
            if (milliseconds <= 0 || milliseconds > 999_999_999_999_999L) {
                throw new ArithmeticException();
            }
            return Long.toString(milliseconds);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new AgentException("Antigravity CLI OAuth credential has an unusable expiry");
        }
    }

    private String candidates(
            Path configDirectory,
            String fileName,
            Pattern pattern,
            int overlap,
            String missingMessage) {
        final String label = "Antigravity CLI collected OAuth client values (" + fileName + ")";
        final Optional<byte[]> collected =
                readSmallFile(configDirectory.resolve(fileName), MAX_CANDIDATE_FILE_BYTES, label);
        SequencedSet<String> found;
        if (collected.isPresent()) {
            found = collectedCandidates(collected.orElseThrow(), pattern, label);
            if (found.isEmpty()) {
                throw new AgentException(label + " is malformed");
            }
        } else {
            found =
                    scanBinary(
                            configDirectory.resolve("antigravity"),
                            pattern,
                            overlap,
                            missingMessage);
            if (found.isEmpty()) {
                found = scanBinary(cliBinary, pattern, overlap, missingMessage);
            }
        }
        if (found.isEmpty()) {
            throw new AgentException(missingMessage);
        }
        return String.join("\n", found);
    }

    private static SequencedSet<String> collectedCandidates(
            byte[] bytes, Pattern pattern, String label) {
        final SequencedSet<String> found = new LinkedHashSet<>();
        for (final String line : new String(bytes, StandardCharsets.US_ASCII).split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            if (!pattern.matcher(line).matches()) {
                throw new AgentException(label + " is malformed");
            }
            found.add(line);
            refuseTooMany(found);
        }
        return found;
    }

    private static void refuseTooMany(SequencedSet<String> found) {
        if (found.size() > MAX_CANDIDATES) {
            throw new AgentException(
                    "Antigravity CLI holds more than "
                            + MAX_CANDIDATES
                            + " OAuth client values of one kind");
        }
    }

    private static Optional<byte[]> readSmallFile(Path file, int limit, String label) {
        try {
            if (!regularFile(file, limit, label)) {
                return Optional.empty();
            }
            try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                final byte[] bytes = input.readNBytes(limit + 1);
                if (bytes.length > limit) {
                    throw new AgentException(label + " exceeds the collection size limit");
                }
                return Optional.of(bytes);
            }
        } catch (IOException | SecurityException exception) {
            throw new AgentException(label + " could not be read");
        }
    }

    private static boolean regularFile(Path file, long limit, String label) throws IOException {
        final BasicFileAttributes attributes;
        try {
            attributes =
                    Files.readAttributes(
                            file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException exception) {
            return false;
        }
        if (!attributes.isRegularFile()) {
            throw new AgentException(label + " must be a regular file, not a link");
        }
        file.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (attributes.size() > limit) {
            throw new AgentException(label + " exceeds the collection size limit");
        }
        return true;
    }

    private static Path realDirectory(Path configDirectory) {
        try {
            final BasicFileAttributes attributes =
                    Files.readAttributes(
                            configDirectory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory()) {
                throw new AgentException(
                        "Antigravity CLI configuration directory must be a directory, not a link");
            }
            return configDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | SecurityException exception) {
            throw new AgentException(
                    "Antigravity CLI configuration directory could not be verified");
        }
    }

    private static SequencedSet<String> scanBinary(
            Path file, Pattern pattern, int overlap, String missingMessage) {
        try {
            final SequencedSet<String> found = new LinkedHashSet<>();
            if (!regularFile(file, MAX_BINARY_BYTES, "Antigravity CLI binary")) {
                return found;
            }
            try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                final byte[] buffer = new byte[8192];
                String tail = "";
                long total = 0;
                int count;
                while ((count =
                                input.read(
                                        buffer,
                                        0,
                                        (int)
                                                Math.min(
                                                        buffer.length,
                                                        MAX_BINARY_BYTES - total + 1)))
                        != -1) {
                    total += count;
                    if (total > MAX_BINARY_BYTES) {
                        throw new AgentException(
                                "Antigravity CLI binary exceeds the collection size limit");
                    }
                    final String chunk =
                            tail + new String(buffer, 0, count, StandardCharsets.ISO_8859_1);
                    final Matcher match = pattern.matcher(chunk);
                    while (match.find()) {
                        // A match lying wholly in the retained tail was taken from the read before.
                        if (match.end() > tail.length()) {
                            found.add(match.group());
                        }
                        refuseTooMany(found);
                    }
                    // Retain a whole value's length, so one split across reads is seen whole and
                    // once.
                    tail = chunk.substring(Math.max(0, chunk.length() - overlap));
                }
                return found;
            }
        } catch (IOException | SecurityException exception) {
            throw new AgentException(missingMessage);
        }
    }
}
