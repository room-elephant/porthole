package com.roomelephant.porthole.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SvgSanitizerTest {
    private static final String SVG_NS = "xmlns=\"http://www.w3.org/2000/svg\"";
    private static final String CIRCLE = "<circle cx=\"5\" cy=\"5\" r=\"4\"/>";

    private static Optional<String> sanitize(String svg) {
        return SvgSanitizer.sanitize(svg.getBytes(StandardCharsets.UTF_8))
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    private static String sanitized(String svg) {
        return sanitize(svg).orElseThrow();
    }

    private static String svg(String body) {
        return "<svg " + SVG_NS + " viewBox=\"0 0 10 10\">" + body + "</svg>";
    }

    @Test
    void keepsAnOrdinaryIcon() {
        String result = sanitized(svg("<g fill=\"#fff\"><path d=\"M0 0h10v10z\"/><title>Logo</title></g>"));
        assertThat(result)
                .contains("viewBox=\"0 0 10 10\"")
                .contains("<path d=\"M0 0h10v10z\"")
                .contains("fill=\"#fff\"")
                .contains("<title>Logo</title>");
    }

    @Test
    void keepsLocalGradientReferences() {
        String result = sanitized(svg("<defs><linearGradient id=\"a\"><stop offset=\"1\" stop-color=\"red\"/>"
                + "</linearGradient></defs><rect width=\"9\" height=\"9\" fill=\"url(#a)\"/>"));
        assertThat(result).contains("<linearGradient id=\"a\"").contains("fill=\"url(#a)\"");
    }

    @Test
    void keepsStylesheetsSoExportedColoursSurvive() {
        String result = sanitized(svg("<style>.a{fill:#f00}</style><path class=\"a\" d=\"M0 0\"/>"));
        assertThat(result).contains("<style>.a{fill:#f00}</style>").contains("class=\"a\"");
    }

    @Test
    void keepsStylesheetsWrittenAsCdata() {
        assertThat(sanitized(svg("<style><![CDATA[.a{fill:#f00}]]></style>" + CIRCLE)))
                .contains(".a{fill:#f00}");
    }

    @Test
    void acceptsAPrefixedSvgNamespace() {
        String result = sanitized("<s:svg xmlns:s=\"http://www.w3.org/2000/svg\"><s:circle r=\"1\"/></s:svg>");
        assertThat(result).contains(SVG_NS).contains("<circle r=\"1\"");
    }

    @Test
    void acceptsAPlainDoctype() {
        String doctype = "<?xml version=\"1.0\"?><!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" "
                + "\"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">";
        assertThat(sanitized(doctype + svg(CIRCLE))).contains("<circle").doesNotContain("DOCTYPE");
    }

    @Test
    void removesScripts() {
        assertThat(sanitized(svg("<script>alert(1)</script>" + CIRCLE)))
                .doesNotContain("script")
                .doesNotContain("alert")
                .contains("<circle");
    }

    @Test
    void removesEventHandlers() {
        assertThat(sanitized("<svg " + SVG_NS + " onload=\"alert(1)\"><circle r=\"1\" onclick=\"alert(2)\"/></svg>"))
                .doesNotContain("onload")
                .doesNotContain("onclick")
                .doesNotContain("alert");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<foreignObject><body xmlns=\"http://www.w3.org/1999/xhtml\"><img src=\"x\"/></body></foreignObject>",
                "<a href=\"javascript:alert(1)\"><text>x</text></a>",
                "<animate attributeName=\"href\" to=\"javascript:alert(1)\"/>",
                "<set attributeName=\"onload\" to=\"alert(1)\"/>",
                "<iframe src=\"https://evil.example\"/>",
                "<metadata>secret</metadata>"
            })
    void removesDangerousElementsAndEverythingInsideThem(String element) {
        assertThat(sanitized(svg(element + CIRCLE)))
                .doesNotContain("foreignObject")
                .doesNotContain("javascript")
                .doesNotContain("<animate")
                .doesNotContain("<set")
                .doesNotContain("iframe")
                .doesNotContain("<body")
                .doesNotContain("secret")
                .contains("<circle");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "javascript:alert(1)",
                "https://evil.example/a.svg#x",
                "//evil.example/a.svg#x",
                "data:image/svg+xml;base64,PHN2Zy8+",
                " javascript:alert(1)"
            })
    void removesLinksThatLeaveTheDocument(String target) {
        assertThat(sanitized(svg("<use href=\"" + target + "\"/><use xmlns:x=\"http://www.w3.org/1999/xlink\" x:href=\""
                        + target + "\"/>" + CIRCLE)))
                .doesNotContain(target.trim());
    }

    @Test
    void keepsLocalLinksInTheirModernForm() {
        assertThat(sanitized(svg(CIRCLE + "<use xmlns:x=\"http://www.w3.org/1999/xlink\" x:href=\"#a\"/>")))
                .contains("href=\"#a\"")
                .doesNotContain("xlink");
    }

    @Test
    void keepsEmbeddedRasterImages() {
        assertThat(sanitized(svg("<image href=\"data:image/png;base64,iVBORw0KGgo=\" width=\"9\"/>")))
                .contains("href=\"data:image/png;base64,iVBORw0KGgo=\"");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://evil.example/a.png",
                "data:text/html;base64,PGh0bWw+",
                "data:image/svg+xml;base64,PHN2Zy8+"
            })
    void removesImageSourcesThatAreNotEmbeddedRasters(String source) {
        assertThat(sanitized(svg(CIRCLE + "<image href=\"" + source + "\"/>"))).doesNotContain(source);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "@import url(https://evil.example/a.css);",
                "@font-face{src:url(https://evil.example/f)}",
                "a{fill:url(https://evil.example/a#b)}",
                "a{fill:url('https://evil.example/a#b')}",
                "a{background:image-set('https://evil.example/a.png' 1x)}",
                "a{fill:\\75rl(https://evil.example)}"
            })
    void removesStylesheetsThatReachOutside(String css) {
        assertThat(sanitized(svg("<style>" + css + "</style>" + CIRCLE)))
                .doesNotContain("evil")
                .doesNotContain("<style");
    }

    @Test
    void removesAttributesThatReachOutside() {
        String result =
                sanitized(svg("<rect fill=\"url(https://evil.example/a#b)\" style=\"fill:url(//evil.example/a)\""
                        + " stroke=\"url('#ok')\" width=\"1\"/>"));
        assertThat(result)
                .doesNotContain("evil")
                .contains("stroke=\"url('#ok')\"")
                .contains("width=\"1\"");
    }

    @Test
    void neverReadsExternalEntities(@TempDir Path directory) throws Exception {
        Path secret = Files.writeString(directory.resolve("secret.txt"), "top-secret");
        String attack =
                "<!DOCTYPE svg [<!ENTITY x SYSTEM \"" + secret.toUri() + "\">]>" + svg(CIRCLE + "<text>&x;</text>");
        assertThat(sanitize(attack).orElse("")).doesNotContain("top-secret");
    }

    @Test
    void neverExpandsEntityBombs() {
        String attack = "<!DOCTYPE svg [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">]>"
                + svg(CIRCLE + "<text>&b;</text>");
        assertThat(sanitize(attack).orElse("")).doesNotContain("aaaaaaaaaa");
    }

    @Test
    void keepsReasonablyNestedGroups() {
        int depth = 50;
        assertThat(sanitize(svg("<g>".repeat(depth) + CIRCLE + "</g>".repeat(depth))))
                .isPresent();
    }

    @Test
    void rejectsExtremeNestingWithoutCrashing() {
        int depth = 100_000;
        assertThat(sanitize(svg("<g>".repeat(depth) + CIRCLE + "</g>".repeat(depth))))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "not xml at all",
                "<svg",
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"><circle r=\"1\"/></html>",
                "<svg><circle r=\"1\"/></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><title>only words</title></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><style>a{<b/>}</style><circle r=\"1\"/></svg>"
            })
    void rejectsAnythingThatIsNotADrawableSvg(String input) {
        assertThat(sanitize(input)).isEmpty();
    }
}
