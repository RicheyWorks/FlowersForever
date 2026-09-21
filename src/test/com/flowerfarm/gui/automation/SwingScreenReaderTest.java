package com.flowerfarm.gui.automation;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import java.awt.Dimension;
import java.awt.Point;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingScreenReaderTest {
    @Test
    void pagesTheEntireActiveScreenWithoutTruncatingLongDocuments() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String notes = "Customer detail \ud83c\udf37\n".repeat(1200);
            JTextArea details = new JTextArea(notes);
            details.setEditable(false);
            details.setCaretPosition(19);
            JScrollPane scroll = new JScrollPane(details);
            scroll.getViewport().setExtentSize(new Dimension(200, 50));
            scroll.getViewport().setViewPosition(new Point(0, 200));
            JPanel root = new JPanel();
            root.add(new JLabel("Saved successfully"));
            root.add(scroll);
            root.add(new JButton("Close details"));
            Point previousPosition = scroll.getViewport().getViewPosition();
            String expected = "Saved successfully\n" + notes + "\nClose details";

            StringBuilder observed = new StringBuilder();
            int offset = 0;
            boolean more;
            do {
                Map<String, Object> page = SwingScreenReader.read(root, offset, 113);
                String text = (String) page.get("text");
                assertThat(text.length()).isLessThanOrEqualTo(113);
                assertThat(page).containsEntry("offset", offset)
                        .containsEntry("totalCharacters", expected.length());
                observed.append(text);
                offset = (int) page.get("nextOffset");
                more = (boolean) page.get("hasMore");
            } while (more);

            assertThat(observed.toString()).isEqualTo(expected);
            assertThat(details.getText()).isEqualTo(notes);
            assertThat(details.getCaretPosition()).isEqualTo(19);
            assertThat(scroll.getViewport().getViewPosition()).isEqualTo(previousPosition);
        });
    }

    @Test
    void readsOnlyTheRequestedDocumentSlices() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GuardedDocument document = new GuardedDocument();
            JTextArea details = new JTextArea(document);
            details.setText("a".repeat(20000));
            JPanel root = new JPanel();
            root.add(new JLabel("Status"));
            root.add(details);
            root.add(new JLabel("Finished"));
            document.armed = true;

            Map<String, Object> page = SwingScreenReader.read(root, 18000, 97);

            assertThat(page).containsEntry("text", "a".repeat(97))
                    .containsEntry("nextOffset", 18097)
                    .containsEntry("totalCharacters", 20016)
                    .containsEntry("hasMore", true);
            assertThat(document.charactersRead).isEqualTo(97);
            assertThat(document.largestRead).isEqualTo(97);
            document.charactersRead = 0;
            SwingScreenReader.read(root, 20008, 8);
            assertThat(document.charactersRead).isZero();
        });
    }

    @Test
    void readsOnlyTheSelectedTabAndIncludesOffscreenVisibleContent() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel active = new JPanel();
            active.add(new JLabel("Active details"));
            JLabel offscreen = new JLabel("Below the viewport");
            offscreen.setLocation(0, 10000);
            active.add(offscreen);
            JPanel hidden = new JPanel();
            hidden.add(new JLabel("Hidden by ancestor"));
            hidden.setVisible(false);
            active.add(hidden);
            JLabel hiddenLabel = new JLabel("Hidden label");
            hiddenLabel.setVisible(false);
            active.add(hiddenLabel);
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Inactive", new JLabel("Private inactive detail"));
            tabs.addTab("Current", new JScrollPane(active));
            tabs.setSelectedIndex(1);

            assertThat(SwingScreenReader.read(tabs, 0, 8000))
                    .containsEntry("text", "Current\nActive details\nBelow the viewport");
            assertThat(tabs.getSelectedIndex()).isEqualTo(1);
        });
    }

    @Test
    void suppressesPasswordsModelWidgetsAndPopupSubtrees() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicBoolean armed = new AtomicBoolean();
            Object privateItem = new Object() {
                @Override
                public String toString() {
                    if (armed.get()) {
                        throw new AssertionError("The screen reader rendered a model value");
                    }
                    return "private model value";
                }
            };
            DefaultTableModel model = new DefaultTableModel(new Object[][] {{privateItem}},
                    new Object[] {"Private column"}) {
                @Override
                public Object getValueAt(int row, int column) {
                    if (armed.get()) {
                        throw new AssertionError("The screen reader loaded a table cell");
                    }
                    return super.getValueAt(row, column);
                }
            };
            JPasswordField password = new JPasswordField("never disclose");
            password.add(new JLabel("Password child"));
            JPopupMenu popup = new JPopupMenu() {
                @Override
                public boolean isVisible() {
                    return true;
                }
            };
            popup.add(new JLabel("Popup content"));
            JPanel root = new JPanel();
            root.add(new JLabel("Public status"));
            root.add(password);
            root.add(new JTable(model));
            root.add(new JList<>(new Object[] {privateItem}));
            root.add(new JComboBox<>(new Object[] {privateItem}));
            root.add(popup);
            armed.set(true);

            assertThat(SwingScreenReader.read(root, 0, 8000))
                    .containsEntry("text", "Public status")
                    .containsEntry("totalCharacters", 13);
        });
    }

    @Test
    void keepsReadsWithinTheSuppliedModalRoot() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel application = new JPanel();
            application.add(new JTextField("Unrelated application draft"));
            JPanel modal = new JPanel();
            modal.add(new JLabel("Confirm removal"));
            AtomicInteger invocations = new AtomicInteger();
            JButton confirm = new JButton("Remove");
            confirm.addActionListener(event -> invocations.incrementAndGet());
            modal.add(confirm);
            application.add(modal);

            assertThat(SwingScreenReader.read(modal, 0, 8000))
                    .containsEntry("text", "Confirm removal\nRemove");
            assertThat(invocations).hasValue(0);
        });
    }

    @Test
    void readsRenderedHtmlLabelAndButtonText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel root = new JPanel();
            root.add(new JLabel("<html><b>Saved</b><br>Ready &amp; waiting</html>"));
            root.add(new JButton("<html><b>Continue</b></html>"));

            String text = (String) SwingScreenReader.read(root, 0, 8000).get("text");

            assertThat(text).contains("Saved", "Ready & waiting", "Continue")
                    .doesNotContain("<html>", "<b>", "&amp;");
        });
    }

    @Test
    void validatesOffsetsLimitsAndTheEdtBoundary() throws Exception {
        assertThatThrownBy(() -> SwingScreenReader.read(new JPanel(), 0, 1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("EDT");
        SwingUtilities.invokeAndWait(() -> {
            JPanel empty = new JPanel();
            assertThat(SwingScreenReader.read(empty, 0, 1))
                    .containsEntry("text", "").containsEntry("nextOffset", 0)
                    .containsEntry("totalCharacters", 0).containsEntry("hasMore", false);
            assertThatThrownBy(() -> SwingScreenReader.read(empty, -1, 1))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("offset");
            assertThatThrownBy(() -> SwingScreenReader.read(empty, 1, 1))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("offset");
            assertThatThrownBy(() -> SwingScreenReader.read(empty, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("limit");
            assertThatThrownBy(() -> SwingScreenReader.read(empty, 0, 8001))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("limit");
            empty.add(new JLabel("ab"));
            assertThat(SwingScreenReader.read(empty, 2, 8000))
                    .containsEntry("text", "").containsEntry("nextOffset", 2)
                    .containsEntry("hasMore", false);
        });
    }

    @Test
    void rejectsUnrepresentableContinuationOffsetsWithoutOverflow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicBoolean armed = new AtomicBoolean();
            Document huge = new PlainDocument() {
                @Override
                public int getLength() {
                    return Integer.MAX_VALUE;
                }
            };
            JTextArea details = new JTextArea() {
                @Override
                public Document getDocument() {
                    return armed.get() ? huge : super.getDocument();
                }
            };
            JPanel root = new JPanel();
            root.add(details);
            root.add(new JLabel("Next"));
            armed.set(true);

            assertThatThrownBy(() -> SwingScreenReader.read(root, Integer.MAX_VALUE, 1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("character range");
        });
    }

    private static final class GuardedDocument extends PlainDocument {
        private static final long serialVersionUID = 1L;
        private boolean armed;
        private int charactersRead;
        private int largestRead;

        @Override
        public String getText(int offset, int length) throws BadLocationException {
            if (armed) {
                if (length > 8000) {
                    throw new AssertionError("The screen reader materialized a whole document");
                }
                charactersRead += length;
                largestRead = Math.max(largestRead, length);
            }
            return super.getText(offset, length);
        }
    }
}
