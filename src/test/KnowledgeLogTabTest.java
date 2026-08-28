package com.flowerfarm.gui;

import com.flowerfarm.gui.tabs.KnowledgeLogTab;
import com.flowerfarm.gui.tabs.TabHost;
import com.flowerfarm.model.KnowledgeConfidence;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.KnowledgeKind;
import com.flowerfarm.service.KnowledgeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Farm Knowledge Log desktop workflow")
class KnowledgeLogTabTest {

    @Test
    @DisplayName("shows saved lessons in farm language and preserves read-only search")
    void rendersKnowledgeAndPermissions() throws Exception {
        KnowledgeService service = mock(KnowledgeService.class);
        when(service.search(any())).thenReturn(List.of(entry()));
        when(service.distinctTags()).thenReturn(List.of("mildew", "timing"));
        KnowledgeLogTab tab = new KnowledgeLogTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitStatus(root[0], "1 farm lesson found");

        JTable table = (JTable) named(root[0], "knowledge.table");
        JTextArea detail = (JTextArea) named(root[0], "knowledge.detail");
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 1)).isEqualTo("Mildew appeared after warm nights");
        assertThat(detail.getText()).contains(
                "WHAT WE NOTICED", "ACTION TAKEN", "RESULT", "Promising");

        SwingUtilities.invokeAndWait(() -> tab.applyRolePermissions(false));
        assertThat(((JButton) named(root[0], "knowledge.record")).isEnabled()).isFalse();
        assertThat(((JButton) named(root[0], "knowledge.edit")).isEnabled()).isFalse();
        assertThat(((JButton) named(root[0], "knowledge.delete")).isEnabled()).isFalse();
        assertThat(((JButton) named(root[0], "knowledge.searchButton")).isEnabled()).isTrue();
    }

    private static KnowledgeEntry entry() {
        KnowledgeEntry entry = new KnowledgeEntry(
                LocalDate.of(2026, 8, 20),
                "Mildew appeared after warm nights",
                KnowledgeKind.PROBLEM,
                "Nootka Rose", "", "Bed A", "Vegetative",
                "White patches appeared on lower leaves.",
                "Removed affected leaves and watered earlier.",
                "Richmond", "No new patches after seven days.",
                LocalDate.of(2026, 8, 28), KnowledgeConfidence.PROMISING,
                List.of("mildew", "timing"));
        entry.setId(7L);
        return entry;
    }

    private static void awaitStatus(Component root, String expected) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            String[] text = new String[1];
            SwingUtilities.invokeAndWait(() ->
                    text[0] = ((JLabel) named(root, "knowledge.status")).getText());
            if (text[0].contains(expected)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for knowledge status: " + expected);
    }

    private static Component named(Component root, String name) {
        if (name.equals(root.getName())) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = namedOrNull(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        throw new AssertionError("Component not found: " + name);
    }

    private static Component namedOrNull(Component root, String name) {
        if (name.equals(root.getName())) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = namedOrNull(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static final class TestHost implements TabHost {
        @Override public void refreshAll() { }
        @Override public void setStatus(String message) { }
        @Override public void selectTab(String title) { }
        @Override public void runTrendAnalysis() { }
    }
}
