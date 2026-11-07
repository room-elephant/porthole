package com.roomelephant.porthole.core.port.inbound;

import com.roomelephant.porthole.core.model.UploadedIcon;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ReadUploadedIcon {
    Optional<UploadedIcon> read(String name);
}
