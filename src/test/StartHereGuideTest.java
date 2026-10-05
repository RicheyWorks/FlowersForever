package com.flowerfarm.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Start here — cut-flower first run vs spreadsheets")
class StartHereGuideTest {

    @Test
    @DisplayName("an empty farm is recognized only when both products and harvests are missing")
    void detectsEmptyFarm() {
        assertThat(StartHereGuide.looksEmpty(0, 0)).isTrue();
        assertThat(StartHereGuide.looksEmpty(1, 0)).isFalse();
        assertThat(StartHereGuide.looksEmpty(0, 1)).isFalse();
    }

    @Test
    @DisplayName("dialog choices map in grower order: cut, spreadsheet, product, dismiss")
    void dialogChoices() {
        assertThat(StartHereGuide.choiceFromDialogIndex(0)).isEqualTo(StartHereGuide.Choice.LOG_HARVEST);
        assertThat(StartHereGuide.choiceFromDialogIndex(1)).isEqualTo(StartHereGuide.Choice.IMPORT_SPREADSHEET);
        assertThat(StartHereGuide.choiceFromDialogIndex(2)).isEqualTo(StartHereGuide.Choice.ADD_PRODUCT);
        assertThat(StartHereGuide.choiceFromDialogIndex(3)).isEqualTo(StartHereGuide.Choice.DISMISS);
        assertThat(StartHereGuide.choiceFromDialogIndex(-1)).isEqualTo(StartHereGuide.Choice.DISMISS);
    }

    @Test
    @DisplayName("copy mentions cut flowers and spreadsheets without jargon")
    void plainLanguage() {
        assertThat(StartHereGuide.body()).contains("cut-flower", "spreadsheet", "CSV or Excel");
        assertThat(StartHereGuide.dashboardGuidance()).contains("spreadsheet");
        assertThat(StartHereGuide.statusFor(StartHereGuide.Choice.LOG_HARVEST)).contains("Harvest Log");
        assertThat(StartHereGuide.statusFor(StartHereGuide.Choice.IMPORT_SPREADSHEET)).containsIgnoringCase("csv");
    }

    @Test
    @DisplayName("the dashboard banner exposes named actions a grower can take")
    void panelActions() {
        List<StartHereGuide.Choice> choices = new ArrayList<>();
        StartHerePanel panel = new StartHerePanel(choices::add);
        assertThat(panel.getName()).isEqualTo("dashboard.startHere");
        click(panel, "startHere.logHarvest");
        click(panel, "startHere.importSpreadsheet");
        click(panel, "startHere.addProduct");
        click(panel, "startHere.dismiss");
        assertThat(choices).containsExactly(
                StartHereGuide.Choice.LOG_HARVEST,
                StartHereGuide.Choice.IMPORT_SPREADSHEET,
                StartHereGuide.Choice.ADD_PRODUCT,
                StartHereGuide.Choice.DISMISS);
    }

    private static void click(JPanel root, String name) {
        AtomicReference<JButton> found = new AtomicReference<>();
        find(root, name, found);
        assertThat(found.get()).as(name).isNotNull();
        found.get().doClick();
    }

    private static void find(java.awt.Container root, String name, AtomicReference<JButton> found) {
        for (java.awt.Component child : root.getComponents()) {
            if (name.equals(child.getName()) && child instanceof JButton button) {
                found.set(button);
                return;
            }
            if (child instanceof java.awt.Container container) {
                find(container, name, found);
            }
        }
    }
}
