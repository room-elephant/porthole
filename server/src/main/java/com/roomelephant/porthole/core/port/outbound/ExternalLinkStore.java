package com.roomelephant.porthole.core.port.outbound;

import com.roomelephant.porthole.core.model.ExternalLink;
import com.roomelephant.porthole.core.model.ExternalLinkDetails;
import com.roomelephant.porthole.core.model.SelectedIcon;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ExternalLinkStore {
    List<ExternalLink> snapshot();

    void add(ExternalLink link);

    /** @throws com.roomelephant.porthole.core.model.exception.LinkNotFoundException for an unknown ID */
    ExternalLink replaceDetails(String id, ExternalLinkDetails details);

    /** @throws com.roomelephant.porthole.core.model.exception.LinkNotFoundException for an unknown ID */
    void replaceIcon(String id, SelectedIcon icon);

    /**
     * @return the removed link
     * @throws com.roomelephant.porthole.core.model.exception.LinkNotFoundException for an unknown ID
     */
    ExternalLink remove(String id);
}
