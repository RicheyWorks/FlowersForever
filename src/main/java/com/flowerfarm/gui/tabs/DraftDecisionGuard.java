package com.flowerfarm.gui.tabs;

import java.util.Objects;

/** Binds a discard answer to the notebook state reviewed before the modal dialog. */
record DraftDecisionGuard(Long editingId, Object form, Object savedRows,
                          int selectedRow, boolean canWrite, boolean loading) {

    /** Row snapshots use identity: a completed refresh requires a fresh decision. */
    boolean stillCurrent(Long currentId, Object currentForm, Object currentRows,
                         int currentRow, boolean writable, boolean refreshing,
                         boolean saving) {
        return !saving && Objects.equals(editingId, currentId)
                && Objects.equals(form, currentForm) && savedRows == currentRows
                && selectedRow == currentRow && canWrite == writable
                && loading == refreshing;
    }
}
