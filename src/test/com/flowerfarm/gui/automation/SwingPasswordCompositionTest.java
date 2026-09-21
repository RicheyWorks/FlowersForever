package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import com.flowerfarm.automation.HarnessSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import java.awt.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Extension-control fixtures; no current production screen uses these password compositions. */
class SwingPasswordCompositionTest {
    private static final String SECRET = "private-model-value-946";
    private static final String SELECTOR = "protected-control";

    @Test
    void observesPasswordComboEditorsAsUnsupportedWithoutReadingTheirDraft() throws Exception {
        AtomicInteger draftReads = new AtomicInteger();
        JComboBox<String> combo = onEdt(() -> {
            JComboBox<String> result = passwordCombo();
            result.setEditor(new PasswordEditor() {
                @Override public Object getItem() {
                    draftReads.incrementAndGet();
                    return super.getItem();
                }
            });
            return result;
        });
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(combo, title -> false);
        draftReads.set(0);
        try {
            HarnessSnapshot observed = bridge.snapshot(SwingHarnessPlugin.ID);
            assertThat(observed.metadata()).containsEntry("ready", true);
            assertThat(observed.controls()).singleElement().satisfies(control -> {
                assertThat(control.selector()).isEqualTo(SELECTOR);
                assertThat(control.commandable()).isFalse();
            });
            assertThat(draftReads).hasValue(0);
            onEdt(() -> {
                combo.setEditor(new BasicComboBoxEditor());
                return null;
            });
            assertThat(bridge.snapshot(SwingHarnessPlugin.ID).controls()).singleElement()
                    .satisfies(control -> assertThat(control.commandable()).isTrue());
        } finally {
            bridge.detach();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "table, read-control, false", "table, read-item, false",
            "list, read-control, false", "list, read-item, false",
            "combo, read-control, false", "combo, read-item, false",
            "table, read-item, true", "list, read-item, true", "combo, read-item, true"
    })
    void rejectsPasswordRendererValuesBeforeFallingBackToTheModel(
            String kind, String action, boolean nested) throws Exception {
        JComponent control = onEdt(() -> maskedControl(kind, nested));
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(control, title -> false);
        SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);
        Map<String, Object> arguments = new LinkedHashMap<>(Map.of("selector", SELECTOR));
        if (action.equals("read-item")) {
            arguments.put("index", 0);
            if (kind.equals("table")) {
                arguments.put("column", 0);
            }
        }
        HarnessCommand command = new HarnessCommand("masked-read", action, arguments);
        try {
            assertThatThrownBy(() -> plugin.execute(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Password")
                    .hasMessageNotContaining(SECRET);
            onEdt(() -> {
                useOrdinaryRenderer(control);
                return null;
            });
            HarnessExecution recovered = plugin.execute(new HarnessCommand("ordinary-read", action, arguments));
            assertThat(recovered.success()).isTrue();
            assertThat(recovered.output().toString()).contains("Visible value").doesNotContain(SECRET);
        } finally {
            bridge.detach();
        }
    }

    @Test
    void rejectsPasswordComboEditorWithoutReadingItsDraft() throws Exception {
        AtomicInteger draftReads = new AtomicInteger();
        JComboBox<String> combo = onEdt(() -> {
            JComboBox<String> result = passwordCombo();
            result.setEditor(new PasswordEditor() {
                @Override public Object getItem() {
                    draftReads.incrementAndGet();
                    return super.getItem();
                }
            });
            result.getEditor().setItem(SECRET);
            return result;
        });
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(combo, title -> false);
        SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);
        draftReads.set(0);
        try {
            assertThatThrownBy(() -> plugin.execute(new HarnessCommand("editor-read", "read-control",
                    Map.of("selector", SELECTOR))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Password").hasMessageNotContaining(SECRET);
            assertThat(draftReads).hasValue(0);
        } finally {
            bridge.detach();
        }
    }

    @ParameterizedTest
    @CsvSource({"choose-option, available", "choose-option, custom", "choose-option-index, available"})
    void passwordComboEditorCannotBeWrittenThroughItsParent(String action, String requested) throws Exception {
        JComboBox<String> combo = onEdt(SwingPasswordCompositionTest::passwordCombo);
        AtomicInteger selections = new AtomicInteger();
        onEdt(() -> {
            combo.addActionListener(event -> selections.incrementAndGet());
            return null;
        });
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(combo, title -> false);
        SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);
        Map<String, Object> arguments = action.equals("choose-option-index")
                ? Map.of("selector", SELECTOR, "index", 1)
                : Map.of("selector", SELECTOR, "value", requested);
        HarnessCommand command = new HarnessCommand("editor-write", action, arguments);
        try {
            HarnessExecution accepted = plugin.execute(command);
            SwingUtilities.invokeAndWait(() -> { });
            assertFailed(plugin, command.requestId());
            assertThat(onEdt(combo::getSelectedItem)).isEqualTo("retained");
            assertThat(onEdt(() -> new String(((JPasswordField)
                    combo.getEditor().getEditorComponent()).getPassword()))).isEqualTo("retained");
            assertThat(selections).hasValue(0);
            assertThat(plugin.replay(command)).contains(accepted);

            onEdt(() -> {
                combo.setEditor(new BasicComboBoxEditor());
                return null;
            });
            plugin.execute(new HarnessCommand("ordinary-write", action, arguments));
            SwingUtilities.invokeAndWait(() -> { });
            assertThat(plugin.execute(new HarnessCommand("ordinary-status", "action-status",
                    Map.of("request-id", "ordinary-write"))).output()).containsEntry("status", "COMPLETED");
            assertThat(onEdt(combo::getSelectedItem)).isEqualTo(requested);
            assertThat(selections).hasValue(1);
        } finally {
            bridge.detach();
        }
    }

    @Test
    void choosingAMaskedOptionDoesNotMatchItsPrivateBackingValue() throws Exception {
        JComboBox<?> combo = onEdt(() -> (JComboBox<?>) maskedControl("combo", false));
        onEdt(() -> {
            combo.setSelectedIndex(-1);
            return null;
        });
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(combo, title -> false);
        SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);
        HarnessCommand command = new HarnessCommand("masked-write", "choose-option",
                Map.of("selector", SELECTOR, "value", SECRET));
        try {
            HarnessExecution accepted = plugin.execute(command);
            SwingUtilities.invokeAndWait(() -> { });
            assertFailed(plugin, command.requestId());
            assertThat(onEdt(combo::getSelectedIndex)).isEqualTo(-1);
            assertThat(plugin.replay(command)).contains(accepted);
        } finally {
            bridge.detach();
        }
    }

    private static void assertFailed(SwingHarnessPlugin plugin, String requestId) {
        HarnessExecution status = plugin.execute(new HarnessCommand(requestId + "-status", "action-status",
                Map.of("request-id", requestId)));
        assertThat(status.success()).isFalse();
        assertThat(status.output()).containsEntry("status", "FAILED")
                .containsEntry("failureType", "IllegalArgumentException");
        assertThat(status.toString()).doesNotContain(SECRET);
    }

    private static JComponent maskedControl(String kind, boolean nested) {
        JComponent result = switch (kind) {
            case "table" -> {
                JTable table = new JTable(new Object[][]{{SECRET}}, new Object[]{"Credential"});
                table.getColumnModel().getColumn(0).setCellRenderer(
                        (owner, value, selected, focus, row, column) -> maskedValue(value, nested));
                yield table;
            }
            case "list" -> {
                JList<String> list = new JList<>(new String[]{SECRET});
                list.setCellRenderer((owner, value, index, selected, focus) -> maskedValue(value, nested));
                yield list;
            }
            case "combo" -> {
                JComboBox<String> combo = new JComboBox<>(new String[]{SECRET});
                combo.setRenderer((owner, value, index, selected, focus) -> maskedValue(value, nested));
                yield combo;
            }
            default -> throw new IllegalArgumentException(kind);
        };
        result.setName(SELECTOR);
        return result;
    }

    private static Component maskedValue(Object value, boolean nested) {
        JPasswordField password = new JPasswordField(String.valueOf(value));
        if (!nested) {
            return password;
        }
        JPanel wrapper = new JPanel();
        wrapper.add(password);
        return wrapper;
    }

    private static void useOrdinaryRenderer(JComponent control) {
        if (control instanceof JTable table) {
            table.getColumnModel().getColumn(0).setCellRenderer(
                    (owner, value, selected, focus, row, column) -> new JLabel("Visible value"));
        } else if (control instanceof JList<?> list) {
            list.setCellRenderer((owner, value, index, selected, focus) -> new JLabel("Visible value"));
        } else if (control instanceof JComboBox<?> combo) {
            combo.setRenderer((owner, value, index, selected, focus) -> new JLabel("Visible value"));
        }
    }

    private static JComboBox<String> passwordCombo() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"retained", "available"});
        combo.setName(SELECTOR);
        combo.setEditable(true);
        combo.setEditor(new PasswordEditor());
        return combo;
    }

    private static class PasswordEditor extends BasicComboBoxEditor {
        private PasswordEditor() {
            editor = new JPasswordField();
        }
    }

    private static <T> T onEdt(Callable<T> task) throws Exception {
        FutureTask<T> pending = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(pending);
        return pending.get();
    }
}
