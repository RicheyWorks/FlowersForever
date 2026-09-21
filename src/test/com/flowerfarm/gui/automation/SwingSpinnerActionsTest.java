package com.flowerfarm.gui.automation;

import com.flowerfarm.gui.view.StockCountView;
import com.flowerfarm.model.Item;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractSpinnerModel;
import javax.swing.JFormattedTextField;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultFormatter;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.DocumentFilter;
import javax.swing.text.NumberFormatter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingSpinnerActionsTest {
    @Test
    void commitsTheProductionStockCountEditorAndUpdatesItsLiveVariance() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StockCountView view = new StockCountView(
                    new Item("Dahlia", "Flowers/Plants", 3, "stems", 1, 8, ""));
            AtomicInteger changes = new AtomicInteger();
            view.countInput().addChangeListener(event -> changes.incrementAndGet());

            SwingSpinnerActions.setValue(view.countInput(), "15");

            assertThat(view.countedQuantity()).isEqualTo(15);
            assertThat(view.varianceLabel().getText()).contains("increase by 7");
            assertThat(changes).hasValue(1);
            SwingSpinnerActions.setValue(view.countInput(), "0");
            assertThat(view.countedQuantity()).isZero();
            SwingSpinnerActions.setValue(view.countInput(), "1000000");
            assertThat(view.countedQuantity()).isEqualTo(1_000_000);
        });
    }

    @Test
    void rejectsInvalidPackingWindowAndStockCountsWithoutChangingAnExistingDraft() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int maximum : List.of(30, 1_000_000)) {
                // MarketDayTab uses 1..30; StockCountView uses 0..1,000,000.
                JSpinner spinner = new JSpinner(new SpinnerNumberModel(1, maximum == 30 ? 1 : 0,
                        maximum, 1));
                field(spinner).setText("17");
                for (String invalid : Arrays.asList("-1", Integer.toString(maximum + 1), "flowers",
                        "", null, "1".repeat(8_001))) {
                    assertRejectedWithoutMutation(spinner, invalid);
                }
                SwingSpinnerActions.setValue(spinner, "23");
                assertThat(spinner.getValue()).isEqualTo(23);
            }
        });
    }

    @Test
    void rejectsWrappedFractionalAndPartiallyParsedIntegersBeforeChangingTheDraft() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int maximum : List.of(30, 1_000_000)) {
                JSpinner spinner = new JSpinner(new SpinnerNumberModel(1, maximum == 30 ? 1 : 0,
                        maximum, 1));
                field(spinner).setText("17");
                for (String invalid : List.of("4294967297", "4294967298", "-4294967295",
                        "2.9", "2.1", "2flowers")) {
                    assertRejectedWithoutMutation(spinner, invalid);
                }
                SwingSpinnerActions.setValue(spinner, "2.0");
                assertThat(spinner.getValue()).isEqualTo(2);
                SwingSpinnerActions.setValue(spinner, Integer.toString(maximum));
                assertThat(spinner.getValue()).isEqualTo(maximum);
            }
            JSpinner wide = new JSpinner(new SpinnerNumberModel(Long.valueOf(1),
                    Long.valueOf(Long.MIN_VALUE), Long.valueOf(Long.MAX_VALUE), Long.valueOf(1)));
            field(wide).setText("17");
            assertRejectedWithoutMutation(wide, "18446744073709551617");
            assertRejectedWithoutMutation(wide, "-9223372036854775809");
            SwingSpinnerActions.setValue(wide, Long.toString(Long.MAX_VALUE));
            assertThat(wide.getValue()).isEqualTo(Long.MAX_VALUE);
        });
    }

    @Test
    void preservesLocalizedGroupingScientificNotationAndPercentFormats() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JSpinner stock = new JSpinner(new SpinnerNumberModel(1, 0, 1_000_000, 1));
            JSpinner.NumberEditor grouped = new JSpinner.NumberEditor(stock, "#,##0.###");
            grouped.getFormat().setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.GERMANY));
            stock.setEditor(grouped);

            SwingSpinnerActions.setValue(stock, "1.234,0");
            assertThat(stock.getValue()).isEqualTo(1_234);
            SwingSpinnerActions.setValue(stock, "1.000.000");
            assertThat(stock.getValue()).isEqualTo(1_000_000);
            SwingSpinnerActions.setValue(stock, "0");
            assertThat(stock.getValue()).isEqualTo(0);
            assertThat(grouped.getFormat().isParseBigDecimal()).isFalse();
            assertThat(grouped.getFormat().getDecimalFormatSymbols().getDecimalSeparator()).isEqualTo(',');

            JSpinner scientific = packingWindow();
            scientific.setEditor(new JSpinner.NumberEditor(scientific, "0.###E0"));
            SwingSpinnerActions.setValue(scientific, "2E1");
            assertThat(scientific.getValue()).isEqualTo(20);
            assertRejectedWithoutMutation(scientific, "2.9E0");

            JSpinner percent = packingWindow();
            percent.setEditor(new JSpinner.NumberEditor(percent, "0%"));
            SwingSpinnerActions.setValue(percent, "200%");
            assertThat(percent.getValue()).isEqualTo(2);
            assertRejectedWithoutMutation(percent, "290%");
        });
    }

    @Test
    void preservesExplicitNumberFormatterOverridesThatAcceptWords() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JSpinner spinner = packingWindow();
            NumberFormatter words = new NumberFormatter(new DecimalFormat("0")) {
                @Override public Object stringToValue(String text) throws ParseException {
                    return text.equals("four") ? Integer.valueOf(4) : super.stringToValue(text);
                }
                @Override public String valueToString(Object value) throws ParseException {
                    return Integer.valueOf(4).equals(value) ? "four" : super.valueToString(value);
                }
            };
            words.setValueClass(Integer.class);
            field(spinner).setFormatterFactory(new DefaultFormatterFactory(words));

            SwingSpinnerActions.setValue(spinner, "four");

            assertThat(spinner.getValue()).isEqualTo(4);
            assertThat(field(spinner).getText()).isEqualTo("four");
        });
    }

    @Test
    void refusesHiddenDisabledAndReadOnlyEditorsBeforeTouchingTheirDrafts() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<Consumer<JSpinner>> unavailable = List.of(
                    spinner -> spinner.setVisible(false),
                    spinner -> spinner.setEnabled(false),
                    spinner -> spinner.getEditor().setVisible(false),
                    spinner -> spinner.getEditor().setEnabled(false),
                    spinner -> field(spinner).setVisible(false),
                    spinner -> field(spinner).setEnabled(false),
                    spinner -> field(spinner).setEditable(false));
            for (Consumer<JSpinner> makeUnavailable : unavailable) {
                JSpinner spinner = packingWindow();
                field(spinner).setText("17");
                makeUnavailable.accept(spinner);
                assertRejectedWithoutMutation(spinner, "5");
            }
        });
    }

    @Test
    void refusesMissingEditorsAndFormattersAndRequiresTheEdt() throws Exception {
        JSpinner[] spinner = new JSpinner[1];
        SwingUtilities.invokeAndWait(() -> {
            spinner[0] = packingWindow();
            JSpinner customEditor = packingWindow();
            customEditor.setEditor(new JPanel());
            assertThatThrownBy(() -> SwingSpinnerActions.setValue(customEditor, "5"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("text editor");
            JSpinner noFormatter = packingWindow();
            field(noFormatter).setFormatterFactory(null);
            assertRejectedWithoutMutation(noFormatter, "5");
            assertThatThrownBy(() -> SwingSpinnerActions.setValue(null, "5"))
                    .isInstanceOf(IllegalArgumentException.class);
        });
        assertThatThrownBy(() -> SwingSpinnerActions.setValue(spinner[0], "5"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("EDT");
    }

    @Test
    void usesTheActualCustomFormatterWithoutBypassingTheModelsTypeOrBounds() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JSpinner spinner = packingWindow();
            AtomicInteger parses = new AtomicInteger();
            JFormattedTextField.AbstractFormatter formatter = new JFormattedTextField.AbstractFormatter() {
                @Override public Object stringToValue(String text) throws ParseException {
                    parses.incrementAndGet();
                    return switch (text) {
                        case "four" -> 4;
                        case "outside" -> 31;
                        case "wrong type" -> "four";
                        case "null value" -> null;
                        default -> throw new ParseException("Use a supported word", 0);
                    };
                }
                @Override public String valueToString(Object value) {
                    return Integer.valueOf(4).equals(value) ? "four" : String.valueOf(value);
                }
            };
            field(spinner).setFormatterFactory(new DefaultFormatterFactory(formatter));
            field(spinner).setText("saved draft");
            for (String invalid : List.of("outside", "wrong type", "null value", "unknown")) {
                assertRejectedWithoutMutation(spinner, invalid);
            }
            parses.set(0);

            SwingSpinnerActions.setValue(spinner, "four");

            assertThat(spinner.getValue()).isEqualTo(4);
            assertThat(field(spinner).getText()).isEqualTo("four");
            assertThat(parses.get()).isGreaterThanOrEqualTo(2);
        });
    }

    @Test
    void commitsDatesUsingTheDisplayedPatternAndRejectsInvalidDatesWithoutMutation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JSpinner spinner = new JSpinner(new SpinnerDateModel(date("2026-09-08"),
                    date("2026-01-01"), date("2026-12-31"), Calendar.DAY_OF_MONTH));
            JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "yyyy-MM-dd");
            editor.getFormat().setLenient(false);
            spinner.setEditor(editor);
            field(spinner).setText("2026-09-20");
            for (String invalid : List.of("2027-01-01", "2025-12-31", "2026-02-30", "September")) {
                assertRejectedWithoutMutation(spinner, invalid);
            }

            SwingSpinnerActions.setValue(spinner, "2026-09-15");

            assertThat(spinner.getValue()).isEqualTo(date("2026-09-15"));
            assertThat(field(spinner).getText()).isEqualTo("2026-09-15");
        });
    }

    @Test
    void commitsListValuesAndRejectsMissingOptionsBeforeChangingTheDraft() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JSpinner spinner = new JSpinner(new SpinnerListModel(List.of("Roses", "Dahlias", "Tulips")));
            field(spinner).setText("Tulips");
            assertRejectedWithoutMutation(spinner, "Orchids");

            SwingSpinnerActions.setValue(spinner, "Dahlias");

            assertThat(spinner.getValue()).isEqualTo("Dahlias");
            assertThat(field(spinner).getText()).isEqualTo("Dahlias");
        });
    }

    @Test
    void reportsListenerRollbackAsRefusedAndCanRecoverOnTheNextRequest() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JSpinner spinner = packingWindow();
            spinner.addChangeListener(event -> {
                if (Integer.valueOf(9).equals(spinner.getValue())) {
                    spinner.setValue(1);
                }
            });

            assertThatThrownBy(() -> SwingSpinnerActions.setValue(spinner, "9"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("did not retain");
            assertThat(spinner.getValue()).isEqualTo(1);

            SwingSpinnerActions.setValue(spinner, "8");
            assertThat(spinner.getValue()).isEqualTo(8);
        });
    }

    @Test
    void reportsModelRejectionEvenWhenTheDefaultEditorSwallowsIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(1, 1, 30, 1) {
                @Override public void setValue(Object value) {
                    if (Integer.valueOf(13).equals(value)) {
                        throw new IllegalArgumentException("Unavailable packing window");
                    }
                    super.setValue(value);
                }
            });

            assertThatThrownBy(() -> SwingSpinnerActions.setValue(spinner, "13"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("did not retain");
            assertThat(spinner.getValue()).isEqualTo(1);

            SwingSpinnerActions.setValue(spinner, "14");
            assertThat(spinner.getValue()).isEqualTo(14);
        });
    }

    @Test
    void doesNotReportSuccessWhenTheInstalledDocumentFilterRefusesTheText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JSpinner spinner = packingWindow();
            ((AbstractDocument) field(spinner).getDocument()).setDocumentFilter(new DocumentFilter() {
                @Override public void replace(FilterBypass bypass, int offset, int length, String text,
                                              AttributeSet attributes) { }
            });

            assertThatThrownBy(() -> SwingSpinnerActions.setValue(spinner, "5"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("did not retain");
            assertThat(spinner.getValue()).isEqualTo(1);
        });
    }

    @Test
    void retainsTheNormalCommitPathForAnEditableCustomModel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JSpinner spinner = new JSpinner(new AbstractSpinnerModel() {
                private String value = "Ready";
                @Override public Object getValue() { return value; }
                @Override public Object getNextValue() { return null; }
                @Override public Object getPreviousValue() { return null; }
                @Override public void setValue(Object candidate) {
                    if (!List.of("Ready", "Packed").contains(candidate)) {
                        throw new IllegalArgumentException("Unknown state");
                    }
                    if (!value.equals(candidate)) {
                        value = (String) candidate;
                        fireStateChanged();
                    }
                }
            });
            field(spinner).setEditable(true);
            field(spinner).setFormatterFactory(new DefaultFormatterFactory(new DefaultFormatter()));

            SwingSpinnerActions.setValue(spinner, "Packed");
            assertThat(spinner.getValue()).isEqualTo("Packed");
            assertThatThrownBy(() -> SwingSpinnerActions.setValue(spinner, "Unknown"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("did not retain");
            assertThat(spinner.getValue()).isEqualTo("Packed");
        });
    }

    private static JSpinner packingWindow() {
        return new JSpinner(new SpinnerNumberModel(1, 1, 30, 1));
    }

    private static JFormattedTextField field(JSpinner spinner) {
        return ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
    }

    private static Date date(String text) {
        return Date.from(LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static void assertRejectedWithoutMutation(JSpinner spinner, String text) {
        JFormattedTextField field = field(spinner);
        String draft = field.getText();
        Object value = spinner.getValue();
        AtomicInteger modelChanges = new AtomicInteger();
        AtomicInteger valueChanges = new AtomicInteger();
        AtomicInteger documentChanges = new AtomicInteger();
        javax.swing.event.ChangeListener modelListener = event -> modelChanges.incrementAndGet();
        java.beans.PropertyChangeListener valueListener = event -> valueChanges.incrementAndGet();
        DocumentListener documentListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { documentChanges.incrementAndGet(); }
            @Override public void removeUpdate(DocumentEvent event) { documentChanges.incrementAndGet(); }
            @Override public void changedUpdate(DocumentEvent event) { documentChanges.incrementAndGet(); }
        };
        spinner.addChangeListener(modelListener);
        field.addPropertyChangeListener("value", valueListener);
        field.getDocument().addDocumentListener(documentListener);
        try {
            assertThatThrownBy(() -> SwingSpinnerActions.setValue(spinner, text))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(field.getText()).isEqualTo(draft);
            assertThat(spinner.getValue()).isEqualTo(value);
            assertThat(modelChanges).hasValue(0);
            assertThat(valueChanges).hasValue(0);
            assertThat(documentChanges).hasValue(0);
        } finally {
            spinner.removeChangeListener(modelListener);
            field.removePropertyChangeListener("value", valueListener);
            field.getDocument().removeDocumentListener(documentListener);
        }
    }
}
