package com.flowerfarm.gui;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/** Replaceable system-clipboard boundary for deterministic desktop journeys. */
@FunctionalInterface
public interface FarmClipboard {

    void copyText(String text);

    /** Uses the operating system clipboard in the installed application. */
    static FarmClipboard system() {
        return text -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text == null ? "" : text), null);
    }
}
