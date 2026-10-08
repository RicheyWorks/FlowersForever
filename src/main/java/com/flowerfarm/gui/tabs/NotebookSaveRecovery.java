package com.flowerfarm.gui.tabs;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Keeps acknowledged notebook writes usable until the full list can be read again. */
final class NotebookSaveRecovery {
    private boolean awaitingRefresh;
    private boolean failedRefresh;

    /** Preserve the returned, committed row before attempting any Swing rendering. */
    <T> List<T> remember(List<T> rows, T saved, Function<T, Long> id) {
        awaitingRefresh = true;
        List<T> current = new ArrayList<>(rows);
        Long savedId = Objects.requireNonNull(id.apply(saved), "Saved record id");
        for (int index = 0; index < current.size(); index++) {
            if (savedId.equals(id.apply(current.get(index)))) {
                current.set(index, saved);
                return List.copyOf(current);
            }
        }
        current.add(saved);
        return List.copyOf(current);
    }

    void refreshed() {
        awaitingRefresh = false;
        failedRefresh = false;
    }

    void refreshFailed() {
        failedRefresh = awaitingRefresh;
    }

    boolean needsRefresh() {
        return failedRefresh;
    }

    String feedback(String message) {
        if (!failedRefresh) {
            return message;
        }
        // Replace generic load/display failures; keep later draft and field feedback.
        String detail = message.startsWith("Could not load ")
                || message.startsWith("Save completed. Refresh ")
                ? "" : message + " ";
        return "Your completed save is kept. " + detail
                + "The list could not refresh. Choose Refresh to retry.";
    }
}
