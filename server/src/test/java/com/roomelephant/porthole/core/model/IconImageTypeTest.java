package com.roomelephant.porthole.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class IconImageTypeTest {
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
    static final byte[] WEBP = ascii("RIFF\0\0\0\0WEBPVP8 ");

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void detectsSupportedTypesFromTheirSignatures() {
        assertThat(IconImageType.detect(PNG)).contains(IconImageType.PNG);
        assertThat(IconImageType.detect(JPEG)).contains(IconImageType.JPEG);
        assertThat(IconImageType.detect(WEBP)).contains(IconImageType.WEBP);
    }

    @Test
    void rejectsEverythingElse() {
        List<byte[]> rejected = List.of(
                ascii("<svg xmlns=\"http://www.w3.org/2000/svg\"/>"),
                ascii("<html></html>"),
                ascii("RIFF\0\0\0\0WAVEfmt "),
                new byte[0],
                new byte[] {(byte) 0x89, 'P', 'N'},
                new byte[] {(byte) 0xFF, (byte) 0xD8},
                ascii("RIFF\0\0\0\0WEB"));
        assertThat(rejected)
                .allSatisfy(bytes -> assertThat(IconImageType.detect(bytes)).isEmpty());
    }

    @Test
    void exposesExtensionAndContentType() {
        assertThat(IconImageType.PNG.extension()).isEqualTo("png");
        assertThat(IconImageType.WEBP.extension()).isEqualTo("webp");
        assertThat(IconImageType.JPEG.extension()).isEqualTo("jpg");
        assertThat(IconImageType.PNG.contentType()).isEqualTo("image/png");
        assertThat(IconImageType.WEBP.contentType()).isEqualTo("image/webp");
        assertThat(IconImageType.JPEG.contentType()).isEqualTo("image/jpeg");
        assertThat(IconImageType.SVG.extension()).isEqualTo("svg");
        assertThat(IconImageType.SVG.contentType()).isEqualTo("image/svg+xml");
    }

    @Test
    void findsTypeFromStoredFileName() {
        assertThat(IconImageType.fromFileName("abc.png")).contains(IconImageType.PNG);
        assertThat(IconImageType.fromFileName("abc.webp")).contains(IconImageType.WEBP);
        assertThat(IconImageType.fromFileName("abc.jpg")).contains(IconImageType.JPEG);
        assertThat(IconImageType.fromFileName("abc.svg")).contains(IconImageType.SVG);
    }
}
