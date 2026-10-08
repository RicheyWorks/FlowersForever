package com.flowerfarm.gui;

import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.service.ProductionStudioService;
import com.flowerfarm.service.StemDemand;
import com.flowerfarm.service.StemDemandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("File → Stem demand board: list, copy, CSV, and counting order lines as recipes")
class DesktopStemDemandControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate EVENT = LocalDate.of(2026, 10, 17);

    @TempDir
    Path temp;

    private final StemDemandService demand = mock(StemDemandService.class);
    private final ProductionStudioService studio = mock(ProductionStudioService.class);
    private final List<String> statuses = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final List<String> copied = new ArrayList<>();
    private final Deque<ToIntFunction<Object>> script = new ArrayDeque<>();
    private Optional<File> saveTo = Optional.empty();
    private DesktopStemDemandController controller;

    private final FarmDialogs dialogs = new FarmDialogs() {
        @Override
        public int showOption(Component parent, Object message, String title, int optionType, int messageType,
                              Icon icon, Object[] options, Object initialValue) {
            return script.isEmpty() ? -1 : script.removeFirst().applyAsInt(message);
        }

        @Override
        public int showConfirm(Component parent, Object message, String title, int optionType, int messageType) {
            return -1;
        }

        @Override
        public void showMessage(Component parent, Object message, String title, int messageType) {
            messages.add(String.valueOf(message));
        }
    };

    private final FarmFileChooser files = (parent, title, suggested) -> {
        statuses.add("suggested " + suggested.getName());
        return saveTo;
    };

    @BeforeEach
    void setUp() {
        StemDemand.Board board = StemDemand.plan(TODAY, TODAY.plusDays(13), false,
                List.of(new StemDemand.Need(EVENT, "Nootka Rose", 40, true, "Order #1 Bloom Studio")),
                List.of(new StemDemand.Stock("Nootka Rose", 25)), List.of(),
                List.of(new StemDemand.NotCounted(EVENT, 2L, 7L, "Order #2 Kitsap Weddings", "Centerpiece",
                        12, "designs", "Sold in designs, so its stems are unknown.")),
                List.of());
        when(demand.today()).thenReturn(TODAY);
        when(demand.farmName()).thenReturn("Hood Canal Blooms");
        when(demand.build(any(), anyInt(), anyBoolean())).thenReturn(board);
        when(demand.recipeLinks(any(), any(), anyBoolean())).thenReturn(List.of(
                new StemDemandService.LinkedLine(EVENT, 3L, 9L, "Order #3 Bloom Studio", "Bud vase", 4, 6L,
                        "Bud vase")));
        FloralRecipe centerpiece = new FloralRecipe("Centerpiece", "Centerpiece", 65, 20, 25, 10, 10, "");
        centerpiece.setId(5L);
        when(studio.listRecipes()).thenReturn(List.of(centerpiece));
        controller = new DesktopStemDemandController(demand, studio);
        controller.attach(new DesktopStemDemandController.Host(null, dialogs, files, copied::add, statuses::add));
    }

    @Test
    @DisplayName("the board shows stems by crop and date with cooler, short, and not-counted lines; copy shares it")
    void showsBoardAndCopies() {
        script.add(message -> {
            Container dialog = (Container) message;
            JTable lines = named(dialog, "stemDemand.lines", JTable.class);
            assertThat(lines.getRowCount()).isEqualTo(1);
            assertThat(lines.getModel().getValueAt(0, 1)).isEqualTo("Nootka Rose");
            assertThat(lines.getModel().getValueAt(0, 4)).isEqualTo(25);
            assertThat(lines.getModel().getValueAt(0, 6)).isEqualTo(15);
            assertThat(lines.getModel().getValueAt(0, 7)).isEqualTo("Short: buy or substitute");
            assertThat(named(dialog, "stemDemand.notCounted", JTable.class).getRowCount()).isEqualTo(1);
            assertThat(named(dialog, "stemDemand.links", JTable.class).getRowCount()).isEqualTo(1);
            assertThat(named(dialog, "stemDemand.headline", JLabel.class).getText())
                    .contains("40 stems needed", "15 stems short");
            named(dialog, "stemDemand.copy", AbstractButton.class).doClick();
            return 0;
        });

        controller.openBoard();

        verify(demand).build(TODAY, StemDemandService.DEFAULT_DAYS, false);
        assertThat(copied).singleElement().asString()
                .contains("Hood Canal Blooms", "Buy or substitute:", "Nootka Rose", "Not counted");
        assertThat(statuses).contains(StemDemand.statusCopied());
    }

    @Test
    @DisplayName("refresh uses the chosen dates and drafts; a bad date is explained instead of failing")
    void refreshUsesFilters() {
        script.add(message -> {
            Container dialog = (Container) message;
            JTextField from = named(dialog, "stemDemand.from", JTextField.class);
            from.setText("2026-10-19");
            named(dialog, "stemDemand.days", JComboBox.class).setSelectedItem(28);
            named(dialog, "stemDemand.drafts", JCheckBox.class).setSelected(true);
            named(dialog, "stemDemand.refresh", AbstractButton.class).doClick();
            from.setText("next friday");
            named(dialog, "stemDemand.refresh", AbstractButton.class).doClick();
            return 0;
        });

        controller.openBoard();

        verify(demand).build(LocalDate.of(2026, 10, 19), 28, true);
        assertThat(statuses).anyMatch(status -> status.contains("YYYY-MM-DD"));
    }

    @Test
    @DisplayName("Save CSV writes the spreadsheet with the suggested dated name")
    void savesCsv() throws Exception {
        Path target = temp.resolve("stem-demand.csv");
        saveTo = Optional.of(target.toFile());
        script.add(message -> {
            named((Container) message, "stemDemand.saveCsv", AbstractButton.class).doClick();
            return 0;
        });

        controller.openBoard();

        assertThat(statuses).contains("suggested stem-demand-2026-10-12-to-2026-10-25.csv",
                StemDemand.statusSaved("stem-demand.csv"));
        assertThat(Files.readString(target, StandardCharsets.UTF_8))
                .startsWith("Date,Crop,Stems needed").contains("Nootka Rose").contains("Not counted");
        assertThat(messages).isEmpty();
    }

    @Test
    @DisplayName("a not-counted line can be counted as a saved recipe, and a counted line can stop counting")
    void linksAndUnlinksRecipes() {
        when(demand.linkRecipe(2L, 7L, 5L))
                .thenReturn(new StemDemandService.RecipeLink(2L, 7L, "Centerpiece", 5L, "Centerpiece"));
        when(demand.linkRecipe(3L, 9L, null))
                .thenReturn(new StemDemandService.RecipeLink(3L, 9L, "Bud vase", null, null));
        script.add(message -> {
            Container dialog = (Container) message;
            named(dialog, "stemDemand.unlinkRecipe", AbstractButton.class).doClick();
            named(dialog, "stemDemand.notCounted", JTable.class).setRowSelectionInterval(0, 0);
            named(dialog, "stemDemand.linkRecipe", AbstractButton.class).doClick();
            named(dialog, "stemDemand.notCounted", JTable.class).clearSelection();
            named(dialog, "stemDemand.links", JTable.class).setRowSelectionInterval(0, 0);
            named(dialog, "stemDemand.unlinkRecipe", AbstractButton.class).doClick();
            return 0;
        });
        script.add(message -> {
            JPanel form = (JPanel) message;
            assertThat(named(form, "stemDemand.recipe", JComboBox.class).getItemCount()).isEqualTo(1);
            return 0;
        });

        controller.openBoard();

        verify(demand).linkRecipe(2L, 7L, 5L);
        verify(demand).linkRecipe(3L, 9L, null);
        assertThat(statuses).contains("Select a line under \"Counted as recipes\" to stop counting it.",
                "Counting Centerpiece as Centerpiece.", "Stopped counting Bud vase as a recipe.");
    }

    @Test
    @DisplayName("cancelling the recipe choice or a service refusal changes nothing silently")
    void cancelAndRefusal() {
        when(demand.linkRecipe(isNull(), any(), any())).thenThrow(new IllegalArgumentException("unused"));
        script.add(message -> {
            Container dialog = (Container) message;
            named(dialog, "stemDemand.notCounted", JTable.class).setRowSelectionInterval(0, 0);
            named(dialog, "stemDemand.linkRecipe", AbstractButton.class).doClick();
            return 0;
        });
        script.add(message -> 1);

        controller.openBoard();

        verify(demand, never()).linkRecipe(any(), any(), any());

        when(demand.linkRecipe(2L, 7L, 5L)).thenThrow(
                new IllegalArgumentException("Only draft or confirmed orders can change; order #2 is fulfilled."));
        script.add(message -> {
            Container dialog = (Container) message;
            named(dialog, "stemDemand.notCounted", JTable.class).setRowSelectionInterval(0, 0);
            named(dialog, "stemDemand.linkRecipe", AbstractButton.class).doClick();
            return 0;
        });
        script.add(message -> 0);

        controller.openBoard();

        assertThat(messages).singleElement().asString().contains("order #2 is fulfilled");
    }

    @Test
    @DisplayName("nothing opens before the window attaches the controller")
    void unattachedIsQuiet() {
        new DesktopStemDemandController(demand, studio).openBoard();
        verify(demand, never()).build(any(), anyInt(), anyBoolean());
    }

    private static <T> T named(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                T found = findOrNull(container, name, type);
                if (found != null) {
                    return found;
                }
            }
        }
        throw new AssertionError("No " + type.getSimpleName() + " named " + name);
    }

    private static <T> T findOrNull(Container root, String name, Class<T> type) {
        try {
            return named(root, name, type);
        } catch (AssertionError missing) {
            return null;
        }
    }
}
