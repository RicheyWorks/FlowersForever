package com.flowerfarm.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FarmUi design system")
class FarmUiTest {

    @Test
    @DisplayName("page and section primitives expose stable semantic names")
    void pageAndSectionSemantics() {
        JPanel page = FarmUi.page();
        JPanel header = FarmUi.pageHeader("Inventory", "Stock and pricing", null);
        JPanel section = FarmUi.section("Current stock", "Search or select a row", new JPanel());

        assertThat(page.getName()).isEqualTo("farm.page");
        assertThat(header.getName()).isEqualTo("farm.pageHeader");
        assertThat(header.getAccessibleContext().getAccessibleName())
                .isEqualTo("Inventory page header");
        assertThat(section.getName()).isEqualTo("farm.section");
        assertThat(section.getAccessibleContext().getAccessibleName())
                .isEqualTo("Current stock section");
    }

    @Test
    @DisplayName("brand header and workflow marks expose accessible farm identity")
    void brandedApplicationShell() {
        JPanel header = FarmUi.brandHeader("PORT ORCHARD, WA", "Monday · August 24");
        Icon dashboard = FarmUi.navigationIcon("Dashboard");
        Icon harvest = FarmUi.navigationIcon("Harvest Log");

        assertThat(header.getName()).isEqualTo("farm.brandHeader");
        assertThat(header.getAccessibleContext().getAccessibleName())
                .isEqualTo("Flowers Forever application header");
        assertThat(dashboard.getIconWidth()).isEqualTo(26);
        assertThat(harvest.getIconHeight()).isEqualTo(26);
    }

    @Test
    @DisplayName("semantic controls remain ordinary accessible Swing controls")
    void semanticControlsAreAccessible() {
        JButton primary = FarmUi.primaryButton("Save harvest");
        JButton danger = FarmUi.dangerButton("Delete selected");
        JTextField search = FarmUi.searchField(
                "Inventory search", "Search name, category, or notes", 24);

        assertThat(primary.getAccessibleContext().getAccessibleName()).isEqualTo("Save harvest");
        assertThat(danger.getAccessibleContext().getAccessibleName()).isEqualTo("Delete selected");
        assertThat(search.getAccessibleContext().getAccessibleName()).isEqualTo("Inventory search");
        assertThat(search.getAccessibleContext().getAccessibleDescription()).contains("Search name");
    }

    @Test
    @DisplayName("table defaults improve scanability and keyboard selection")
    void tableDefaults() {
        JTable table = new JTable(new DefaultTableModel(new Object[][]{{"Rose"}},
                new Object[]{"Name"}));

        FarmUi.configureTable(table, "Inventory table");

        assertThat(table.getRowHeight()).isEqualTo(FarmUi.TABLE_ROW_HEIGHT);
        assertThat(table.getSelectionModel().getSelectionMode())
                .isEqualTo(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        assertThat(table.getFillsViewportHeight()).isTrue();
        assertThat(table.getAccessibleContext().getAccessibleName()).isEqualTo("Inventory table");
        assertThat(table.getAccessibleContext().getAccessibleDescription()).contains("arrow keys");
        assertThat(table.getToolTipText()).contains("selected row");
        assertThat(table.getSurrendersFocusOnKeystroke()).isTrue();
        assertThat(table.getTableHeader().getReorderingAllowed()).isFalse();
        assertThat(table.getTableHeader().isOpaque()).isTrue();
        assertThat(table.getTableHeader().getFont().getStyle()).isEqualTo(Font.BOLD);
        assertThat(table.getTableHeader().getPreferredSize().height).isEqualTo(34);
        assertThat(table.getShowHorizontalLines()).isTrue();
    }

    @Test
    @DisplayName("stock tints preserve healthy rows and flag warning levels")
    void stockTints() {
        Color normal = new Color(250, 250, 250);

        assertThat(FarmUi.stockBackground(20, normal)).isEqualTo(normal);
        assertThat(FarmUi.stockBackground(10, normal)).isNotEqualTo(normal);
        assertThat(FarmUi.stockBackground(2, normal))
                .isNotEqualTo(FarmUi.stockBackground(10, normal));
    }

    @Test
    @DisplayName("botanical tones create distinct card and section surfaces")
    void botanicalPalette() {
        Color growth = FarmUi.toneSurfaceColor(FarmUi.Tone.SUCCESS);
        Color planning = FarmUi.toneSurfaceColor(FarmUi.Tone.WARNING);
        Color floral = FarmUi.toneSurfaceColor(FarmUi.Tone.DANGER);
        Color information = FarmUi.toneSurfaceColor(FarmUi.Tone.INFO);

        assertThat(growth).isNotEqualTo(planning).isNotEqualTo(floral)
                .isNotEqualTo(information);
        assertThat(planning).isNotEqualTo(floral).isNotEqualTo(information);

        JLabel growthValue = new JLabel("42");
        JLabel planningValue = new JLabel("640");
        JPanel growthCard = FarmUi.metricCard(
                "Beds in use", growthValue, new JLabel("Ready"), FarmUi.Tone.SUCCESS);
        JPanel planningCard = FarmUi.metricCard(
                "Expected stems", planningValue, new JLabel("Planned"), FarmUi.Tone.WARNING);
        JPanel searchSection = FarmUi.section(
                "Find stock", "Search inventory", new JPanel());
        JPanel stockSection = FarmUi.section(
                "Current stock", "Review inventory", new JPanel());

        assertThat(growthCard.getBackground()).isEqualTo(growth);
        assertThat(planningCard.getBackground()).isEqualTo(planning);
        assertThat(growthValue.getForeground())
                .isEqualTo(FarmUi.toneForegroundColor(FarmUi.Tone.SUCCESS));
        assertThat(planningValue.getForeground())
                .isEqualTo(FarmUi.toneForegroundColor(FarmUi.Tone.WARNING));
        assertThat(searchSection.getBackground()).isNotEqualTo(stockSection.getBackground());
    }

    @Test
    @DisplayName("error copy remains actionable when exceptions have no message")
    void actionableErrorFallback() {
        assertThat(FarmUi.actionableError("Inventory export", new RuntimeException()))
                .contains("Inventory export could not be completed")
                .contains("did not provide additional details")
                .contains("try again");
        assertThat(FarmUi.actionableError("Refresh", new RuntimeException("Service unavailable")))
                .contains("Service unavailable");
    }

    @Test
    @DisplayName("status badges include their tone in visuals and status in accessibility")
    void statusBadge() {
        JLabel badge = FarmUi.statusBadge("Low stock", FarmUi.Tone.WARNING);

        assertThat(badge.isOpaque()).isTrue();
        assertThat(badge.getBackground()).isNotNull();
        assertThat(badge.getAccessibleContext().getAccessibleName()).isEqualTo("Low stock status");

        FarmUi.updateStatusBadge(badge, "Inventory refreshed", FarmUi.Tone.SUCCESS);
        assertThat(badge.getText()).isEqualTo("Inventory refreshed");
        assertThat(badge.getToolTipText()).isEqualTo("Inventory refreshed");
        assertThat(badge.getAccessibleContext().getAccessibleDescription())
                .isEqualTo("Inventory refreshed");
    }

    @Test
    @DisplayName("form guidance and field errors remain available to assistive technology")
    void formGuidanceAndValidation() {
        JLabel hint = FarmUi.formHint("Required fields are marked.");
        JTextField field = new JTextField("bad");

        FarmUi.markFieldError(field, "Enter a valid quantity.");

        assertThat(hint.getName()).isEqualTo("farm.formHint");
        assertThat(hint.getAccessibleContext().getAccessibleDescription())
                .isEqualTo("Required fields are marked.");
        assertThat(field.getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(field.getToolTipText()).isEqualTo("Enter a valid quantity.");
        assertThat(field.getAccessibleContext().getAccessibleDescription())
                .isEqualTo("Enter a valid quantity.");

        FarmUi.clearFieldError(field);
        assertThat(field.getClientProperty("JComponent.outline")).isNull();
        assertThat(field.getToolTipText()).isNull();
    }

    @Test
    @DisplayName("field errors reveal their input through a compact scroll viewport")
    void validationRevealsCompactScreenInput() throws Exception {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(Box.createRigidArea(new Dimension(180, 320)));
        JTextField field = new JTextField("twelve");
        field.setMaximumSize(new Dimension(180, 28));
        content.add(field);
        content.setPreferredSize(new Dimension(180, 360));
        JScrollPane scroll = new JScrollPane(content);

        SwingUtilities.invokeAndWait(() -> {
            scroll.setSize(200, 100);
            scroll.doLayout();
            content.setSize(180, 360);
            content.doLayout();
            scroll.getViewport().setExtentSize(new Dimension(180, 80));
            scroll.getViewport().setViewSize(new Dimension(180, 360));
            FarmUi.markFieldError(field, "Enter a numeric quantity.");
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(scroll.getViewport().getViewPosition().y).isPositive();
        assertThat(field.getSelectedText()).isEqualTo("twelve");
    }

    @Test
    @DisplayName("destructive confirmations expose consequences consistently")
    void confirmationPanel() {
        JPanel confirmation = FarmUi.confirmationPanel(
                "Delete this harvest?",
                "The amount will be removed from inventory and cannot be undone.",
                FarmUi.Tone.DANGER);

        assertThat(confirmation.getName()).isEqualTo("farm.confirmationPanel");
        assertThat(confirmation.getAccessibleContext().getAccessibleName())
                .isEqualTo("Delete this harvest? confirmation");
        assertThat(confirmation.getComponents()).hasSize(2);
    }

    @Test
    @DisplayName("workflow focus prefers a marked frequent input over header actions")
    void workflowInitialFocus() {
        JPanel page = new JPanel(new BorderLayout());
        JButton refresh = new JButton("Refresh");
        JTextField search = FarmUi.initialFocus(new JTextField());
        page.add(refresh, BorderLayout.NORTH);
        page.add(search, BorderLayout.CENTER);

        assertThat(FarmUi.focusFirstInteractive(page)).isSameAs(search);

        search.setEnabled(false);
        assertThat(FarmUi.focusFirstInteractive(page)).isSameAs(refresh);
    }
}
