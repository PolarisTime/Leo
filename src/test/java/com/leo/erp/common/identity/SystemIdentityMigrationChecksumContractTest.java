package com.leo.erp.common.identity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SystemIdentityMigrationChecksumContractTest {

    private static final String MANIFEST = "/db/migration/system-identity-v20-v46.sha256";
    private static final String FORWARD_MANIFEST = "/db/migration/system-identity-v47-v55.sha256";
    private static final Pattern MANIFEST_ENTRY = Pattern.compile(
            "^([0-9a-f]{64})\\s{2}(/db/migration/V(\\d+)__[^\\s]+\\.sql)$"
    );

    @Test
    void shouldKeepSystemIdentityMigrationsAtTheCapturedV20ToV46Snapshot() throws IOException {
        List<ChecksumEntry> entries = readManifest();

        // These files predate this guard; the snapshot proves only that they do not drift from now on.
        assertThat(entries)
                .extracting(ChecksumEntry::version)
                .containsExactlyElementsOf(IntStream.rangeClosed(20, 46).boxed().toList());
        for (ChecksumEntry entry : entries) {
            assertThat(sha256(readResource(entry.resource())))
                    .as(entry.resource())
                    .isEqualTo(entry.expectedSha256());
        }
    }

    @Test
    void shouldKeepForwardIdentityMigrationsAtTheCapturedV47ToV55Snapshot() throws IOException {
        List<ChecksumEntry> entries = readManifest(FORWARD_MANIFEST);

        assertThat(entries)
                .extracting(ChecksumEntry::version)
                .containsExactlyElementsOf(IntStream.rangeClosed(47, 55).boxed().toList());
        for (ChecksumEntry entry : entries) {
            assertThat(sha256(readResource(entry.resource())))
                    .as(entry.resource())
                    .isEqualTo(entry.expectedSha256());
        }
    }

    private List<ChecksumEntry> readManifest() throws IOException {
        return readManifest(MANIFEST);
    }

    private List<ChecksumEntry> readManifest(String manifest) throws IOException {
        String content = new String(readResource(manifest), StandardCharsets.UTF_8);
        return content.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(this::parseEntry)
                .toList();
    }

    private ChecksumEntry parseEntry(String line) {
        Matcher matcher = MANIFEST_ENTRY.matcher(line);
        assertThat(matcher.matches()).as("manifest entry: %s", line).isTrue();
        return new ChecksumEntry(
                Integer.parseInt(matcher.group(3)),
                matcher.group(2),
                matcher.group(1)
        );
    }

    private byte[] readResource(String resource) throws IOException {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return input.readAllBytes();
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }
    }

    private record ChecksumEntry(int version, String resource, String expectedSha256) {
    }
}
