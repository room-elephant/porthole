package com.roomelephant.porthole.domain.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NumericVersionTest {
    @ParameterizedTest
    @CsvSource({
        "2.0,1.0,1",
        "v1.0,1.0.0,0",
        "1.0.0,1.0,0",
        "1.0.0.1,1.0,1",
        "2147483648.0,2.0,1",
        "1.0,2.0,-1",
        "01.002,1.2,0"
    })
    void comparesNumericVersions(String left, String right, int sign) {
        assertEquals(
                sign,
                Integer.signum(NumericVersion.tryParse(left)
                        .orElseThrow()
                        .compareTo(NumericVersion.tryParse(right).orElseThrow())));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"7", "latest", "1.0-alpine", "1.0.0-rc1", "release-build", " 1.0", "1.0.", "1..0"})
    void rejectsUnsupportedVersions(String version) {
        assertTrue(NumericVersion.tryParse(version).isEmpty());
    }
}
