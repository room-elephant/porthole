package com.roomelephant.porthole.core.port.inbound;

import com.roomelephant.porthole.core.model.ExternalLinkDetails;
import com.roomelephant.porthole.core.model.PresentedLink;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ManageLinks {
    List<PresentedLink> list();

    PresentedLink create(ExternalLinkDetails details);

    PresentedLink update(String id, ExternalLinkDetails details);

    void delete(String id);

    void selectLibraryIcon(String id, String name);

    void uploadIcon(String id, byte[] bytes);
}
