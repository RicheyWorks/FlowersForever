package com.flowerfarm.util;

import java.nio.file.Path;

/** Null-safe access to the last element of a path. */
public final class PathNames {

    private PathNames() {
    }

    /**
     * The file name of {@code path} as text, or {@code ""} when there is none.
     * {@link Path#getFileName()} returns {@code null} for a path with no name elements, such as the
     * root {@code /} or {@code C:\}, so callers that only test the name never dereference a null.
     */
    public static String fileName(Path path) {
        if (path == null) {
            return "";
        }
        Path name = path.getFileName();
        return name == null ? "" : name.toString();
    }
}
