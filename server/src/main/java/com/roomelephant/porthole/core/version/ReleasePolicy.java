package com.roomelephant.porthole.core.version;

import com.roomelephant.porthole.core.model.TagCatalogue;
import java.util.Optional;
import org.jspecify.annotations.*;

@NullMarked
public final class ReleasePolicy {
    public @Nullable String latest(TagCatalogue catalogue) {
        String best = null;
        NumericVersion highest = null;
        for (String tag : catalogue.tags()) {
            Optional<NumericVersion> candidate = NumericVersion.tryParse(tag);
            if (candidate.isEmpty()) continue;
            int comparison = highest == null ? 1 : candidate.get().compareTo(highest);
            if (comparison > 0 || (comparison == 0 && tag.compareTo(best) > 0)) {
                best = tag;
                highest = candidate.get();
            }
        }
        return best;
    }
}
