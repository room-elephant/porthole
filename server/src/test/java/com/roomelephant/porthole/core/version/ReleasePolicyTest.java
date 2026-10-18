package com.roomelephant.porthole.core.version;

import static org.junit.jupiter.api.Assertions.*;

import com.roomelephant.porthole.core.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReleasePolicyTest {
    @Test
    void selectsNumericMaximumAndDeterministicAliases() {
        var policy = new ReleasePolicy();
        assertEquals(
                "2147483648.0",
                policy.latest(new TagCatalogue(List.of("7", "1.2-alpine", "2.0-rc1", "v1.2.3", "2147483648.0"))));
        assertEquals("v1.0.0", policy.latest(new TagCatalogue(List.of("v1.0.0", "1.0"))));
        assertEquals("v1.0.0", policy.latest(new TagCatalogue(List.of("1.0", "v1.0.0"))));
        assertNull(policy.latest(new TagCatalogue(List.of("latest", "7", "1.2-alpine"))));
    }
}
