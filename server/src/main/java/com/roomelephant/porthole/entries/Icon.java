package com.roomelephant.porthole.entries;

import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;

/** A saved icon choice: a Dashboard Icons library name, or an uploaded file's content-addressed ID. */
@NullMarked
public sealed interface Icon permits Icon.Library, Icon.Upload {
    Pattern LIBRARY_NAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,254}$");
    Pattern UPLOAD_ID = Pattern.compile("^[0-9a-f]{64}\\.(png|webp|jpg|svg)$");

    record Library(String name) implements Icon {
        public Library {
            if (!LIBRARY_NAME.matcher(name).matches()) throw new IllegalArgumentException("Invalid library icon name");
        }
    }

    record Upload(String id) implements Icon {
        public Upload {
            if (!UPLOAD_ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid uploaded icon id");
        }
    }
}
