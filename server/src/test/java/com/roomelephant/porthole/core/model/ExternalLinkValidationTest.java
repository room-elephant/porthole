package com.roomelephant.porthole.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExternalLinkValidationTest {
    private static final String ID = "3f2a9c1e5b7d4e8a9c217d4e5f6a8b90";

    @Test
    void trimsValuesAndTurnsBlankStackIntoNone() {
        assertThat(ExternalLinkValidation.normalize(new ExternalLinkDetails("  Router ", " http://r/ ", "  ")))
                .isEqualTo(new ExternalLinkDetails("Router", "http://r/", null));
    }

    @Test
    void keepsAStack() {
        assertThat(ExternalLinkValidation.normalize(new ExternalLinkDetails("Router", "http://r/", " base ")))
                .isEqualTo(new ExternalLinkDetails("Router", "http://r/", "base"));
    }

    @Test
    void acceptsLongestNameAndStack() {
        String longest = "a".repeat(255);
        assertThatCode(() -> ExternalLinkValidation.normalize(new ExternalLinkDetails(longest, "http://r/", longest)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrBlankName() {
        assertRejected(new ExternalLinkDetails(null, "http://r/", null));
        assertRejected(new ExternalLinkDetails(" ", "http://r/", null));
    }

    @Test
    void rejectsOverlongNameAndStack() {
        assertRejected(new ExternalLinkDetails("a".repeat(256), "http://r/", null));
        assertRejected(new ExternalLinkDetails("Router", "http://r/", "a".repeat(256)));
    }

    @Test
    void rejectsMissingOrUnsupportedUrl() {
        assertRejected(new ExternalLinkDetails("Router", null, null));
        assertRejected(new ExternalLinkDetails("Router", "javascript:alert(1)", null));
    }

    @Test
    void validatesAStoredLink() {
        assertThatCode(() -> ExternalLinkValidation.validate(
                        new ExternalLink(ID, "Router", "http://r/", null, SelectedIcon.library("router"))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAStoredLinkWithABadId() {
        assertThatThrownBy(() ->
                        ExternalLinkValidation.validate(new ExternalLink("abc", "Router", "http://r/", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAStoredLinkWithABadUrl() {
        assertThatThrownBy(() -> ExternalLinkValidation.validate(
                        new ExternalLink(ID, "Router", "javascript:alert(1)", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAStoredLinkWithABadIcon() {
        assertThatThrownBy(() -> ExternalLinkValidation.validate(
                        new ExternalLink(ID, "Router", "http://r/", null, SelectedIcon.library("../x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertRejected(ExternalLinkDetails details) {
        assertThatThrownBy(() -> ExternalLinkValidation.normalize(details))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
