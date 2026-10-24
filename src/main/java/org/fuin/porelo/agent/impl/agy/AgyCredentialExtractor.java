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
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fuin.porelo.agent.api.AgentException;
import org.fuin.porelo.agent.api.Credential;
import org.fuin.porelo.agent.api.CredentialExtractor;
import org.fuin.porelo.wire.Json;
import org.fuin.porelo.wire.JsonException;

/** Collects Antigravity's consumer sign-in and installed client secret for host-side renewal. */
public final class AgyCredentialExtractor implements CredentialExtractor {
    private static final int MAX_TOKEN_BYTES = 1024 * 1024;
    private static final int MAX_SECRET_BYTES = 4096;
    private static final long MAX_BINARY_BYTES = 512L * 1024 * 1024;
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9._~+/-]+=*");
    private static final Pattern COLLECTED_CLIENT_ID = Pattern.compile("[A-Za-z0-9._-]{6,128}");
    private static final Pattern COLLECTED_CLIENT_SECRET = Pattern.compile("[A-Za-z0-9_-]{12,64}");
    private static final Pattern BINARY_CLIENT_ID =
            Pattern.compile("[0-9]{12}-[a-z0-9]{32}\\.apps\\." + "googleusercontent\\.com");
    private static final Pattern BINARY_CLIENT_SECRET =
            Pattern.compile("GOC" + "SPX-[A-Za-z0-9_-]{28}");
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
     * @param cliBinary Regular binary to scan when the collected profile has no client secret.
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
        final String clientId = clientId(directory);
        final String clientSecret = clientSecret(directory);
        return Optional.of(
                new Credential(
                        "oauth",
                        access,
                        Map.of(
                                Credential.REFRESH_TOKEN, refresh,
                                Credential.EXPIRES_AT, expiresAt,
                                Credential.TOKEN_URL, "https://oauth2.googleapis.com/token",
                                Credential.CLIENT_ID, clientId,
                                Credential.CLIENT_SECRET, clientSecret)));
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

    private String clientId(Path configDirectory) {
        return collectedOrScanned(
                configDirectory,
                ".client-id",
                COLLECTED_CLIENT_ID,
                "Antigravity CLI collected OAuth client ID",
                binaryClientId,
                71,
                NO_CLIENT_ID);
    }

    private String clientSecret(Path configDirectory) {
        return collectedOrScanned(
                configDirectory,
                ".client-secret",
                COLLECTED_CLIENT_SECRET,
                "Antigravity CLI collected OAuth client secret",
                binaryClientSecret,
                34,
                NO_CLIENT_SECRET);
    }

    private String collectedOrScanned(
            Path configDirectory,
            String fileName,
            Pattern collectedPattern,
            String collectedLabel,
            Pattern binaryPattern,
            int overlap,
            String missingMessage) {
        final Optional<byte[]> collected =
                readSmallFile(configDirectory.resolve(fileName), MAX_SECRET_BYTES, collectedLabel);
        if (collected.isPresent()) {
            final String value =
                    new String(collected.orElseThrow(), StandardCharsets.US_ASCII).strip();
            if (!collectedPattern.matcher(value).matches()) {
                throw new AgentException(collectedLabel + " is malformed");
            }
            return value;
        }
        final Optional<String> local =
                scanBinary(
                        configDirectory.resolve("antigravity"),
                        binaryPattern,
                        overlap,
                        missingMessage);
        if (local.isPresent()) {
            return local.orElseThrow();
        }
        return scanBinary(cliBinary, binaryPattern, overlap, missingMessage)
                .orElseThrow(() -> new AgentException(missingMessage));
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

    private static Optional<String> scanBinary(
            Path file, Pattern pattern, int overlap, String missingMessage) {
        try {
            if (!regularFile(file, MAX_BINARY_BYTES, "Antigravity CLI binary")) {
                return Optional.empty();
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
                    if (match.find()) {
                        return Optional.of(match.group());
                    }
                    // Retain enough bytes to recognize a value split across reads.
                    tail = chunk.substring(Math.max(0, chunk.length() - overlap));
                }
                return Optional.empty();
            }
        } catch (IOException | SecurityException exception) {
            throw new AgentException(missingMessage);
        }
    }
}
