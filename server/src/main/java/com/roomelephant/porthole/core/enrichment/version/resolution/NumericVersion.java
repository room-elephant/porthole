package com.roomelephant.porthole.core.enrichment.version.resolution;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Porthole's numeric dotted tags, including short versions; not full Semantic Versioning. */
@NullMarked
public record NumericVersion(List<BigInteger> components) implements Comparable<NumericVersion> {
    private static final Pattern FORMAT = Pattern.compile("v?\\d++(?:\\.\\d++)++");

    public NumericVersion {
        components = List.copyOf(components);
    }

    public static Optional<NumericVersion> tryParse(@Nullable String value) {
        if (value == null || !FORMAT.matcher(value).matches()) return Optional.empty();
        return Optional.of(
                new NumericVersion(Arrays.stream(value.replaceFirst("^v", "").split("\\."))
                        .map(BigInteger::new)
                        .toList()));
    }

    @Override
    public int compareTo(NumericVersion other) {
        for (int i = 0; i < Math.max(components.size(), other.components.size()); i++) {
            BigInteger left = i < components.size() ? components.get(i) : BigInteger.ZERO;
            BigInteger right = i < other.components.size() ? other.components.get(i) : BigInteger.ZERO;
            int comparison = left.compareTo(right);
            if (comparison != 0) return comparison;
        }
        return 0;
    }
}
