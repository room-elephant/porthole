package com.roomelephant.porthole.entries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roomelephant.porthole.entries.icons.FilesystemIconFileStore;
import com.roomelephant.porthole.testsupport.MutableClock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IconServiceTest {
    private static final Duration HOUR = Duration.ofHours(1);

    @TempDir
    Path directory;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-08T10:00:00Z"));
    private FilesystemIconFileStore files;
    private IconService icons;

    @BeforeEach
    void setUp() {
        files = new FilesystemIconFileStore(directory, clock);
        icons = new IconService(files);
    }

    static byte[] png(int seed) {
        return new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, (byte) seed, (byte) (seed >> 8)};
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    void pngIdIsSha256OfBytes() throws Exception {
        byte[] bytes = png(1);

        assertThat(icons.upload(bytes)).isEqualTo(sha256(bytes) + ".png");
    }

    @Test
    void readReturnsBytesAndContentType() {
        String id = icons.upload(png(1));

        assertThat(icons.read(id)).hasValueSatisfying(icon -> {
            assertThat(icon.bytes()).containsExactly(png(1));
            assertThat(icon.contentType()).isEqualTo("image/png");
        });
    }

    @Test
    void svgIsSanitizedAndHashedAfterSanitizing() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script><rect width=\"1\"/></svg>"
                .getBytes(StandardCharsets.UTF_8);

        String id = icons.upload(svg);

        byte[] stored = icons.read(id).orElseThrow().bytes();
        assertThat(new String(stored, StandardCharsets.UTF_8)).doesNotContain("script");
        assertThat(id).isEqualTo(sha256(stored) + ".svg");
    }

    @Test
    void rejectsUnknownFormat() {
        assertThatThrownBy(() -> icons.upload("hello".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Icon must be a PNG, WebP, JPEG or SVG image");
    }

    @Test
    void rejectsOverOneMegabyte() {
        byte[] bytes = new byte[IconService.MAX_BYTES + 1];
        System.arraycopy(png(1), 0, bytes, 0, 8);

        assertThatThrownBy(() -> icons.upload(bytes))
                .isInstanceOf(IconTooLargeException.class)
                .hasMessage("Icon must be at most 1 MB");
    }

    @Test
    void acceptsExactlyOneMegabyte() {
        byte[] bytes = new byte[IconService.MAX_BYTES];
        System.arraycopy(png(1), 0, bytes, 0, 8);

        assertThat(icons.upload(bytes)).endsWith(".png");
    }

    @Test
    void identicalUploadReturnsSameIdAndStoresOneFile() {
        String first = icons.upload(png(1));

        assertThat(icons.upload(png(1))).isEqualTo(first);
        assertThat(files.count()).isEqualTo(1);
    }

    @Test
    void newUploadAtLimitThrows() {
        for (int index = 0; index < IconService.MAX_FILES; index++) icons.upload(png(index));

        assertThatThrownBy(() -> icons.upload(png(IconService.MAX_FILES)))
                .isInstanceOf(IconLimitReachedException.class)
                .hasMessage("Porthole has reached its limit of 500 uploaded icons");
    }

    @Test
    void identicalUploadAtLimitReturnsExistingId() {
        String first = icons.upload(png(0));
        for (int index = 1; index < IconService.MAX_FILES; index++) icons.upload(png(index));

        assertThat(icons.upload(png(0))).isEqualTo(first);
    }

    @Test
    void identicalUploadRefreshesAge() {
        String id = icons.upload(png(1));
        clock.advance(Duration.ofHours(2));

        icons.upload(png(1));

        assertThat(icons.sweep(Set.of(), clock.instant(), HOUR)).isZero();
        assertThat(icons.read(id)).isPresent();
    }

    @Test
    void sweepDeletesOldUnreferencedKeepsReferencedAndYoung() {
        String old = icons.upload(png(1));
        String referenced = icons.upload(png(2));
        clock.advance(Duration.ofHours(2));
        String young = icons.upload(png(3));

        assertThat(icons.sweep(Set.of(referenced), clock.instant(), HOUR)).isEqualTo(1);

        assertThat(icons.read(old)).isEmpty();
        assertThat(icons.read(referenced)).isPresent();
        assertThat(icons.read(young)).isPresent();
    }

    @Test
    void claimRefreshesAgeAndReportsMissing() {
        String id = icons.upload(png(1));
        clock.advance(Duration.ofHours(2));

        assertThat(icons.claim(id)).isTrue();
        assertThat(icons.claim("d".repeat(64) + ".png")).isFalse();
        assertThat(icons.sweep(Set.of(), clock.instant(), HOUR)).isZero();
    }

    @Test
    void readRejectsNonUploadIds() {
        assertThat(icons.read("../state.json")).isEmpty();
        assertThat(icons.read("a".repeat(32) + ".png")).isEmpty();
        assertThat(icons.claim("../state.json")).isFalse();
    }
}
