package com.flowerfarm.service;

import com.flowerfarm.gui.RemindersPanel;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.KnowledgeConfidence;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.ProductionTaskStatus;
import com.flowerfarm.model.ProductionTaskType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Grower reminders — due work checklist")
class GrowerRemindersTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    @Test
    @DisplayName("empty farm facts yield a caught-up digest")
    void emptyDigest() {
        GrowerReminders.Digest digest = GrowerReminders.assemble(
                DAY, List.of(), List.of(), null, false, List.of(), 10);
        assertThat(digest.isEmpty()).isTrue();
        assertThat(digest.headline()).containsIgnoringCase("caught up");
        assertThat(GrowerReminders.traySummary(digest)).contains("No farm reminders");
    }

    @Test
    @DisplayName("due field tasks, quiet harvest, and follow-ups become plain reminders")
    void assemblesCoreKinds() {
        ProductionTask task = new ProductionTask(1L, 1L, DAY.minusDays(1),
                ProductionTaskType.SOIL_PREP, ProductionTaskStatus.TODO,
                "Nootka Rose", "Bed A", 1, true, null);
        KnowledgeEntry lesson = new KnowledgeEntry(
                DAY.minusDays(10), "Wind damage", com.flowerfarm.model.KnowledgeKind.PROBLEM,
                "Nootka Rose", "", "Bed A", "Harvest",
                "Checked after wind", "Tied stems", "Owner", "Stems bruised", DAY.minusDays(1),
                KnowledgeConfidence.PROVEN, List.of("wind"));
        Item low = new Item("Dahlia mix", "cut flowers", 2.5, "bunches", 5.0, 3, "");

        GrowerReminders.Digest digest = GrowerReminders.assemble(
                DAY, List.of(task), List.of(lesson), null, true, List.of(low), 10);

        assertThat(digest.items()).hasSize(4);
        assertThat(digest.items().get(0).kind()).isEqualTo(GrowerReminder.Kind.FIELD_TASK);
        assertThat(digest.items().get(0).title()).contains("Soil prep", "Nootka Rose");
        assertThat(digest.items().get(0).targetTab()).isEqualTo("Production Studio");
        assertThat(digest.items().get(1).kind()).isEqualTo(GrowerReminder.Kind.HARVEST);
        assertThat(digest.items().get(2).kind()).isEqualTo(GrowerReminder.Kind.STOCK);
        assertThat(digest.items().get(3).kind()).isEqualTo(GrowerReminder.Kind.KNOWLEDGE);
        assertThat(GrowerReminders.traySummary(digest)).contains("Soil prep").contains("+3 more");
    }

    @Test
    @DisplayName("task labels stay grower-facing")
    void taskLabels() {
        assertThat(GrowerReminders.taskLabel(ProductionTaskType.SOW)).isEqualTo("Sow");
        assertThat(GrowerReminders.taskLabel(ProductionTaskType.FIRST_HARVEST))
                .isEqualTo("First harvest");
        assertThat(GrowerReminders.taskLabel(ProductionTaskType.SUPPORT))
                .contains("Support");
    }

    @Test
    @DisplayName("reminders panel opens a selected item and can dismiss")
    void panelActions() {
        List<GrowerReminder> opened = new ArrayList<>();
        AtomicReference<Boolean> dismissed = new AtomicReference<>(false);
        RemindersPanel panel = new RemindersPanel(opened::add, () -> dismissed.set(true));
        ProductionTask task = new ProductionTask(1L, 1L, DAY,
                ProductionTaskType.SOW, ProductionTaskStatus.TODO,
                "Sweet Pea", "Bed B", 1, true, null);
        panel.showDigest(GrowerReminders.assemble(
                DAY, List.of(task), List.of(), null, false, List.of(), 10));
        assertThat(panel.getName()).isEqualTo("reminders.panel");
        click(panel, "reminders.open.0");
        assertThat(opened).hasSize(1);
        assertThat(opened.get(0).title()).contains("Sow");
        click(panel, "reminders.dismiss");
        assertThat(dismissed.get()).isTrue();
    }

    private static void click(JPanel root, String name) {
        AtomicReference<JButton> found = new AtomicReference<>();
        findButton(root, name, found);
        assertThat(found.get()).as(name).isNotNull();
        found.get().doClick();
    }

    private static void findButton(Container root, String name, AtomicReference<JButton> found) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && child instanceof JButton button) {
                found.set(button);
                return;
            }
            if (child instanceof Container container) {
                findButton(container, name, found);
            }
        }
    }
}
