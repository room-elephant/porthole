package com.roomelephant.porthole.core.port.inbound;

import com.roomelephant.porthole.core.model.PresentedLink;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ListLinks {
    List<PresentedLink> list();
}
