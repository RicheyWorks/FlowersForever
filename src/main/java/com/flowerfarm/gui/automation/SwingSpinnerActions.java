package com.flowerfarm.gui.automation;

import javax.accessibility.AccessibleRole;
import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.text.InternationalFormatter;
import javax.swing.text.NumberFormatter;
import java.awt.Component;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Date;
import java.util.Objects;

/** Spinner edits use the installed editor's formatting and ordinary commit listeners. */
final class SwingSpinnerActions {
    private static final int MAX_INPUT_LENGTH = 8_000;

    private SwingSpinnerActions() { }

    static void setValue(JSpinner spinner, String displayedValue) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Spinner actions must run on the EDT");
        }
        if (displayedValue == null || displayedValue.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Spinner text is required and must not exceed "
                    + MAX_INPUT_LENGTH + " characters");
        }
        requireUsable(spinner, "Spinner");
        if (!(spinner.getEditor() instanceof JSpinner.DefaultEditor editor)) {
            throw new IllegalArgumentException("Spinner has no supported text editor");
        }
        requireUsable(editor, "Spinner editor");
        JFormattedTextField field = editor.getTextField();
        requireUsable(field, "Spinner text field");
        if (!field.isEditable()) {
            throw new IllegalArgumentException("Spinner text field is read-only");
        }
        if (field.getAccessibleContext() != null
                && field.getAccessibleContext().getAccessibleRole() == AccessibleRole.PASSWORD_TEXT) {
            throw new IllegalArgumentException("Password fields cannot be edited by spinner actions");
        }
        JFormattedTextField.AbstractFormatter formatter = field.getFormatter();
        if (formatter == null) {
            throw new IllegalArgumentException("Spinner text field has no formatter");
        }

        // A failed parse must leave an existing draft and its listeners untouched.
        Object requestedValue;
        try {
            requestedValue = formatter.stringToValue(displayedValue);
        } catch (ParseException | RuntimeException exception) {
            throw new IllegalArgumentException("Text is invalid for the spinner editor", exception);
        }
        validateExactIntegralText(formatter, displayedValue, requestedValue);
        validateModelValue(spinner.getModel(), requestedValue);

        try {
            field.setText(displayedValue);
            spinner.commitEdit();
        } catch (ParseException | RuntimeException exception) {
            throw new IllegalStateException("Spinner refused the requested value during commit", exception);
        }
        // DefaultEditor can swallow a model rejection, and application listeners can restore
        // another value even when commitEdit returns normally.
        if (!Objects.equals(spinner.getValue(), requestedValue)) {
            throw new IllegalStateException("Spinner did not retain the requested value");
        }
    }

    private static void validateExactIntegralText(JFormattedTextField.AbstractFormatter formatter,
                                                  String text, Object value) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger)
                || !(formatter instanceof NumberFormatter numberFormatter)
                || !(numberFormatter.getFormat() instanceof DecimalFormat decimalFormat)) {
            return;
        }
        try {
            // Explicit custom parsers can intentionally accept words or other nonnumeric input.
            if (formatter.getClass().getMethod("stringToValue", String.class).getDeclaringClass()
                    != InternationalFormatter.class) {
                return;
            }
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("Cannot verify the spinner's numeric formatter", exception);
        }
        // NumberFormatter narrows a parsed number before checking its bounds. That can
        // turn 4294967297 into 1, or 2.9 into 2. Preserve the configured format while
        // checking the original number exactly, without changing the live formatter.
        DecimalFormat exactFormat = (DecimalFormat) decimalFormat.clone();
        exactFormat.setParseBigDecimal(true);
        ParsePosition position = new ParsePosition(0);
        Number rawValue;
        try {
            rawValue = exactFormat.parse(text, position);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Spinner text must represent an exact whole number", exception);
        }
        if (!(rawValue instanceof BigDecimal exactValue) || position.getIndex() != text.length()
                || exactValue.compareTo(new BigDecimal(value.toString())) != 0) {
            throw new IllegalArgumentException("Spinner text must represent the requested whole number exactly");
        }
    }

    private static void validateModelValue(SpinnerModel model, Object value) {
        if (model instanceof SpinnerNumberModel number) {
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException("Spinner editor must produce a number");
            }
            requireWithinBounds(value, number.getMinimum(), number.getMaximum());
        } else if (model instanceof SpinnerDateModel date) {
            if (!(value instanceof Date)) {
                throw new IllegalArgumentException("Spinner editor must produce a date");
            }
            requireWithinBounds(value, date.getStart(), date.getEnd());
        } else if (model instanceof SpinnerListModel list && !list.getList().contains(value)) {
            throw new IllegalArgumentException("Spinner value is not in the current list");
        }
        // Other models have no validation API separate from setValue. Their installed
        // formatter and normal commit path remain authoritative; verify the result afterward.
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void requireWithinBounds(Object value, Comparable<?> minimum, Comparable<?> maximum) {
        try {
            if ((minimum != null && ((Comparable) minimum).compareTo(value) > 0)
                    || (maximum != null && ((Comparable) maximum).compareTo(value) < 0)) {
                throw new IllegalArgumentException("Spinner value is outside the current model bounds");
            }
        } catch (ClassCastException exception) {
            throw new IllegalArgumentException("Spinner value is incompatible with the current model bounds",
                    exception);
        }
    }

    private static void requireUsable(Component component, String kind) {
        if (component == null) {
            throw new IllegalArgumentException(kind + " is required");
        }
        if (!component.isVisible()) {
            throw new IllegalArgumentException(kind + " is hidden");
        }
        if (!component.isEnabled()) {
            throw new IllegalArgumentException(kind + " is disabled");
        }
    }
}
