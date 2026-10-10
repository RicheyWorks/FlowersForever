package com.flowerfarm.gui.harness;

import com.flowerfarm.gui.FarmDialogs;

import javax.swing.Icon;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Scripted answers for the same modal dialog content used by the installed app. */
public final class SwingDialogHarness implements FarmDialogs {

    private final Deque<DialogScript> scripts = new ArrayDeque<>();
    private final List<String> transcript = new ArrayList<>();
    private boolean safelyCancelUnscripted;

    /**
     * Makes an exploratory button sweep close any otherwise-unscripted modal
     * boundary without approving a mutation. Explicit scripts remain strict.
     */
    public SwingDialogHarness safelyCancelUnscripted() {
        safelyCancelUnscripted = true;
        return this;
    }

    public SwingDialogHarness choose(String title, String optionLabel) {
        scripts.addLast(new OptionScript(title, optionLabel, List.of(), List.of()));
        return this;
    }

    public SwingDialogHarness enterSpinnerAndChoose(
            String title, String spinnerAccessibleName, Object value, String optionLabel) {
        DialogForm form = new DialogForm().spinner(spinnerAccessibleName, value);
        scripts.addLast(new OptionScript(title, optionLabel, form.edits(), form.expectedText()));
        return this;
    }

    /** Scripts edits and copy assertions against a custom-option form. */
    public SwingDialogHarness chooseForm(
            String title, String optionLabel, Consumer<DialogForm> configure) {
        DialogForm form = configuredForm(configure);
        scripts.addLast(new OptionScript(title, optionLabel, form.edits(), form.expectedText()));
        return this;
    }

    public SwingDialogHarness confirm(String title, boolean confirmed) {
        scripts.addLast(new ConfirmScript(title, confirmed, List.of(), List.of()));
        return this;
    }

    /** Scripts edits and copy assertions against an OK/cancel or yes/no form. */
    public SwingDialogHarness confirmForm(
            String title, boolean confirmed, Consumer<DialogForm> configure) {
        DialogForm form = configuredForm(configure);
        scripts.addLast(new ConfirmScript(title, confirmed, form.edits(), form.expectedText()));
        return this;
    }

    /** Expects an informational, warning, or error message and verifies its copy. */
    public SwingDialogHarness message(String title, String expectedText) {
        scripts.addLast(new MessageScript(title, List.of(expectedText)));
        return this;
    }

    @Override
    public int showOption(Component parent, Object message, String title,
                          int optionType, int messageType, Icon icon,
                          Object[] options, Object initialValue) {
        if (scripts.isEmpty() && safelyCancelUnscripted) {
            transcript.add("SAFE CANCEL OPTION " + title + " · available "
                    + List.of(options));
            return JOptionPane.CLOSED_OPTION;
        }
        OptionScript script = next(title, OptionScript.class);
        applyForm(title, message, script.edits(), script.expectedText());
        for (int index = 0; index < options.length; index++) {
            if (script.optionLabel().equals(String.valueOf(options[index]))) {
                transcript.add("OPTION " + title + " = " + script.optionLabel());
                return index;
            }
        }
        throw new AssertionError("Dialog '" + title + "' has no option '"
                + script.optionLabel() + "'. Available: " + List.of(options));
    }

    @Override
    public int showConfirm(Component parent, Object message, String title,
                           int optionType, int messageType) {
        if (scripts.isEmpty() && safelyCancelUnscripted) {
            transcript.add("SAFE CANCEL CONFIRM " + title);
            return JOptionPane.CANCEL_OPTION;
        }
        ConfirmScript script = next(title, ConfirmScript.class);
        applyForm(title, message, script.edits(), script.expectedText());
        transcript.add("CONFIRM " + title + " = " + script.confirmed());
        return script.confirmed() ? JOptionPane.YES_OPTION : JOptionPane.NO_OPTION;
    }

    @Override
    public void showMessage(Component parent, Object message, String title, int messageType) {
        if (scripts.isEmpty() && safelyCancelUnscripted) {
            transcript.add("SAFE CLOSE MESSAGE " + title);
            return;
        }
        MessageScript script = next(title, MessageScript.class);
        applyForm(title, message, List.of(), script.expectedText());
        transcript.add("MESSAGE " + title);
    }

    public List<String> transcript() {
        return List.copyOf(transcript);
    }

    public void assertComplete() {
        if (!scripts.isEmpty()) {
            throw new AssertionError("Unused dialog scripts: " + scripts);
        }
    }

    private <T extends DialogScript> T next(String title, Class<T> type) {
        DialogScript script = scripts.pollFirst();
        if (script == null) {
            throw new AssertionError("Unexpected dialog '" + title + "'");
        }
        if (!type.isInstance(script)) {
            throw new AssertionError("Dialog '" + title + "' expected "
                    + type.getSimpleName() + " but next script is " + script);
        }
        if (!script.title().equals(title)) {
            throw new AssertionError("Expected dialog '" + script.title()
                    + "' but saw '" + title + "'");
        }
        return type.cast(script);
    }

    private static DialogForm configuredForm(Consumer<DialogForm> configure) {
        DialogForm form = new DialogForm();
        Objects.requireNonNull(configure, "configure").accept(form);
        return form;
    }

    private void applyForm(String title, Object message, List<FieldEdit> edits,
                           List<String> expectedText) {
        for (String expected : expectedText) {
            String content = dialogText(message);
            if (!content.contains(expected)) {
                throw new AssertionError("Dialog '" + title + "' does not contain '"
                        + expected + "'. Content: " + content);
            }
            transcript.add("EXPECT " + title + " contains \"" + expected + "\"");
        }
        for (FieldEdit edit : edits) {
            edit.apply(message);
            transcript.add("EDIT " + title + " · " + edit.description());
        }
    }

    private static Component dialogComponent(Object message) {
        if (!(message instanceof Component component)) {
            throw new AssertionError("Dialog content has no Swing controls");
        }
        return component;
    }

    private static void setSpinner(Object message, String accessibleName, Object value) {
        List<JSpinner> matches = new ArrayList<>();
        collect(dialogComponent(message), JSpinner.class, matches);
        matches.removeIf(spinner -> !accessibleName.equals(accessibleName(spinner)));
        if (matches.size() != 1) {
            throw new AssertionError("Expected one dialog spinner named '" + accessibleName
                    + "' but found " + matches.size());
        }
        matches.get(0).setValue(value);
    }

    private static void setText(Object message, String accessibleName, String value) {
        List<JTextComponent> matches = new ArrayList<>();
        collect(dialogComponent(message), JTextComponent.class, matches);
        matches.removeIf(field -> !accessibleName.equals(accessibleName(field)));
        if (matches.size() != 1) {
            throw new AssertionError("Expected one dialog text field named '" + accessibleName
                    + "' but found " + matches.size());
        }
        matches.get(0).setText(value);
    }

    private static void select(Object message, String accessibleName, String option) {
        List<JComboBox> matches = new ArrayList<>();
        collect(dialogComponent(message), JComboBox.class, matches);
        matches.removeIf(selector -> !accessibleName.equals(accessibleName(selector)));
        if (matches.size() != 1) {
            throw new AssertionError("Expected one dialog selector named '" + accessibleName
                    + "' but found " + matches.size());
        }
        JComboBox<?> selector = matches.get(0);
        for (int index = 0; index < selector.getItemCount(); index++) {
            if (option.equals(comboItemText(selector, index))) {
                selector.setSelectedIndex(index);
                return;
            }
        }
        if (selector.isEditable()) {
            selector.setSelectedItem(option);
            return;
        }
        throw new AssertionError("Dialog selector '" + accessibleName
                + "' has no option '" + option + "'");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String comboItemText(JComboBox<?> combo, int index) {
        Object item = combo.getItemAt(index);
        javax.swing.ListCellRenderer renderer = combo.getRenderer();
        if (renderer != null) {
            Component rendered = renderer.getListCellRendererComponent(
                    new JList<>(), item, index, false, false);
            if (rendered instanceof JLabel label && label.getText() != null) {
                return label.getText();
            }
        }
        return String.valueOf(item);
    }

    private static String dialogText(Object message) {
        if (message == null) {
            return "";
        }
        if (!(message instanceof Component component)) {
            return String.valueOf(message);
        }
        List<String> content = new ArrayList<>();
        collectText(component, content);
        return String.join(" | ", content);
    }

    private static void collectText(Component component, List<String> content) {
        String value = null;
        if (component instanceof JLabel label) {
            value = label.getText();
        } else if (component instanceof AbstractButton button) {
            value = button.getText();
        } else if (component instanceof JTextComponent text) {
            value = text.getText();
        }
        if (value != null && !value.isBlank()) {
            content.add(value);
        }
        if (component instanceof JComponent swingComponent) {
            String description = swingComponent.getAccessibleContext()
                    .getAccessibleDescription();
            if (description != null && !description.isBlank()) {
                content.add(description);
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectText(child, content);
            }
        }
    }

    private static <T extends Component> void collect(
            Component component, Class<T> type, List<T> result) {
        if (type.isInstance(component)) {
            result.add(type.cast(component));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, result);
            }
        }
    }

    private static String accessibleName(JComponent component) {
        String name = component.getAccessibleContext().getAccessibleName();
        return name == null ? "" : name.trim();
    }

    /** Mutable builder used only while a queued dialog script is declared. */
    public static final class DialogForm {
        private final List<FieldEdit> edits = new ArrayList<>();
        private final List<String> expectedText = new ArrayList<>();

        public DialogForm type(String accessibleName, String value) {
            edits.add(new TextEdit(accessibleName, value));
            return this;
        }

        public DialogForm select(String accessibleName, String option) {
            edits.add(new SelectionEdit(accessibleName, option));
            return this;
        }

        public DialogForm spinner(String accessibleName, Object value) {
            edits.add(new SpinnerEdit(accessibleName, value));
            return this;
        }

        public DialogForm expectText(String text) {
            expectedText.add(Objects.requireNonNull(text, "text"));
            return this;
        }

        private List<FieldEdit> edits() {
            return List.copyOf(edits);
        }

        private List<String> expectedText() {
            return List.copyOf(expectedText);
        }
    }

    private sealed interface FieldEdit permits TextEdit, SelectionEdit, SpinnerEdit {
        void apply(Object message);

        String description();
    }

    private record TextEdit(String accessibleName, String value) implements FieldEdit {
        @Override public void apply(Object message) {
            setText(message, accessibleName, value);
        }

        @Override public String description() {
            return "TYPE " + accessibleName + " = \"" + value + "\"";
        }
    }

    private record SelectionEdit(String accessibleName, String option) implements FieldEdit {
        @Override public void apply(Object message) {
            select(message, accessibleName, option);
        }

        @Override public String description() {
            return "SELECT " + accessibleName + " = " + option;
        }
    }

    private record SpinnerEdit(String accessibleName, Object value) implements FieldEdit {
        @Override public void apply(Object message) {
            setSpinner(message, accessibleName, value);
        }

        @Override public String description() {
            return "SPINNER " + accessibleName + " = " + value;
        }
    }

    private sealed interface DialogScript permits OptionScript, ConfirmScript, MessageScript {
        String title();
    }

    private record OptionScript(
            String title, String optionLabel, List<FieldEdit> edits,
            List<String> expectedText) implements DialogScript {
    }

    private record ConfirmScript(
            String title, boolean confirmed, List<FieldEdit> edits,
            List<String> expectedText) implements DialogScript {
    }

    private record MessageScript(
            String title, List<String> expectedText) implements DialogScript {
    }
}
