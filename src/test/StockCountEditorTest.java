import com.flowerfarm.gui.view.StockCountView;
import com.flowerfarm.model.Item;
import org.junit.jupiter.api.Test;

import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockCountEditorTest {
    @Test
    void typedFractionalCountIsRefusedWithoutTruncatingOrChangingStock() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StockCountView view = view("plants", 12);
            var field = ((JSpinner.NumberEditor) view.countInput().getEditor()).getTextField();
            field.setText("1.5");
            assertThatThrownBy(view::commitCountedQuantity)
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("whole number");
            assertThat(view.countedQuantity()).isEqualTo(12);
            assertThat(field.getText()).isEqualTo("1.5");
            field.setText("15");
            assertThat(view.commitCountedQuantity()).isEqualTo(15);
            assertThat(view.countInput().getValue()).isInstanceOf(Integer.class);
        });
    }

    @Test
    void typedWeightRetainsAllSixDecimals() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StockCountView view = view("grams", 12.5);
            ((JSpinner.NumberEditor) view.countInput().getEditor()).getTextField().setText("0.123456");
            assertThat(view.commitCountedQuantity()).isEqualTo(0.123456);
            assertThat(view.countInput().getValue()).isInstanceOf(Double.class);
        });
    }

    @Test
    void excessPrecisionAndInvalidWeightTextAreRefusedBeforeSaving() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StockCountView view = view("grams", 12.5);
            var field = ((JSpinner.NumberEditor) view.countInput().getEditor()).getTextField();
            for (String input : List.of("0.1234567", "2flowers", "-1", "2147483648", "NaN", "")) {
                field.setText(input);
                assertThatThrownBy(view::commitCountedQuantity).isInstanceOf(IllegalArgumentException.class);
                assertThat(view.countedQuantity()).isEqualTo(12.5);
            }
        });
    }

    private static StockCountView view(String unit, double quantity) {
        return new StockCountView(new Item("Named product", "Farm products", 3, unit, 1, quantity, ""));
    }
}
