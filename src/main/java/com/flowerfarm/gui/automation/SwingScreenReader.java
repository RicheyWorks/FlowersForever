package com.flowerfarm.gui.automation;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.View;
import java.awt.Component;
import java.awt.Container;
import java.util.Map;
import java.util.Objects;

/** Bounded text reads from the active desktop's logical, visible component tree. */
final class SwingScreenReader {
    private static final int MAX_PAGE_CHARACTERS = 8000;

    private SwingScreenReader() {
    }

    static Map<String, Object> read(JComponent activeRoot, int offset, int limit) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Desktop screen text must be read on the EDT");
        }
        Objects.requireNonNull(activeRoot, "activeRoot");
        if (offset < 0) {
            throw new IllegalArgumentException("Screen text offset must not be negative");
        }
        if (limit < 1 || limit > MAX_PAGE_CHARACTERS) {
            throw new IllegalArgumentException("Screen text limit must be between 1 and 8000");
        }
        Page page = new Page(offset, limit);
        visit(activeRoot, page);
        return page.result();
    }

    private static void visit(Component component, Page page) {
        if (component == null || !component.isVisible()
                || component instanceof JPasswordField || component instanceof JPopupMenu
                || component instanceof JTable || component instanceof JList<?>
                || component instanceof JComboBox<?>) {
            return;
        }
        if (component instanceof JTextComponent text) {
            page.document(text.getDocument());
        } else if (component instanceof JLabel label) {
            componentText(label, label.getText(), page);
        } else if (component instanceof AbstractButton button) {
            componentText(button, button.getText(), page);
        } else if (component instanceof JTabbedPane tabs) {
            int selected = tabs.getSelectedIndex();
            if (selected >= 0) {
                page.text(tabs.getTitleAt(selected));
                visit(tabs.getSelectedComponent(), page);
            }
        } else if (component instanceof JScrollPane scroll) {
            visit(scroll.getViewport(), page);
        } else if (component instanceof JViewport viewport) {
            visit(viewport.getView(), page);
        } else if (component instanceof JSpinner spinner) {
            visit(spinner.getEditor(), page);
        } else if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                visit(child, page);
            }
        }
    }

    private static void componentText(JComponent component, String text, Page page) {
        Object html = component.getClientProperty(BasicHTML.propertyKey);
        if (html instanceof View view) {
            page.document(view.getDocument());
        } else {
            page.text(text);
        }
    }

    private static final class Page {
        private final int offset;
        private final long end;
        private final StringBuilder text;
        private long total;

        private Page(int offset, int limit) {
            this.offset = offset;
            end = (long) offset + limit;
            text = new StringBuilder(limit);
        }

        private void text(String value) {
            if (value != null && !value.isEmpty()) {
                separator();
                append(value.length(), (start, length) -> value.substring(start, start + length));
            }
        }

        private void document(Document document) {
            int length = document.getLength();
            if (length > 0) {
                separator();
                append(length, document::getText);
            }
        }

        private void separator() {
            if (total > 0) {
                append(1, (start, length) -> "\n");
            }
        }

        private void append(int length, TextSlice source) {
            long start = total;
            total += length;
            if (total > Integer.MAX_VALUE) {
                throw new IllegalStateException("Screen text exceeds the supported character range");
            }
            long overlapStart = Math.max(start, offset);
            long overlapEnd = Math.min(total, end);
            if (overlapStart < overlapEnd) {
                try {
                    text.append(source.read((int) (overlapStart - start),
                            (int) (overlapEnd - overlapStart)));
                } catch (BadLocationException failure) {
                    throw new IllegalStateException("Screen text changed during the read; retry it", failure);
                }
            }
        }

        private Map<String, Object> result() {
            if (offset > total) {
                throw new IllegalArgumentException("Screen text offset exceeds the character count");
            }
            int nextOffset = offset + text.length();
            return Map.of("text", text.toString(), "offset", offset,
                    "nextOffset", nextOffset, "totalCharacters", (int) total,
                    "hasMore", nextOffset < total);
        }
    }

    @FunctionalInterface
    private interface TextSlice {
        String read(int start, int length) throws BadLocationException;
    }
}
