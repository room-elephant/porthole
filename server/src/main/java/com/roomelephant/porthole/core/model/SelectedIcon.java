package com.roomelephant.porthole.core.model;

import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A user's icon choice for one container: a library icon name, or the file name of an upload. */
@NullMarked
public record SelectedIcon(String type, String name) {
    public static final String LIBRARY = "library";

    public static final String UPLOAD = "upload";

    public static final Pattern LIBRARY_NAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,254}$");

    public static final Pattern UPLOAD_NAME = Pattern.compile("^[0-9a-f]{32}\\.(png|webp|jpg|svg)$");

    public static SelectedIcon library(String name) {
        return new SelectedIcon(LIBRARY, name);
    }

    public static SelectedIcon upload(String name) {
        return new SelectedIcon(UPLOAD, name);
    }

    public boolean selectsUpload() {
        return UPLOAD.equals(type);
    }

    public void requireValid() {
        @Nullable String checked = name;
        if (checked == null) {
            throw new IllegalArgumentException("icon.name must not be blank");
        }
        if (LIBRARY.equals(type)) {
            if (!LIBRARY_NAME.matcher(checked).matches()) {
                throw new IllegalArgumentException("Invalid library icon name");
            }
        } else if (UPLOAD.equals(type)) {
            if (!UPLOAD_NAME.matcher(checked).matches()) {
                throw new IllegalArgumentException("Invalid uploaded icon name");
            }
        } else {
            throw new IllegalArgumentException("icon.type must be library or upload");
        }
    }
}
