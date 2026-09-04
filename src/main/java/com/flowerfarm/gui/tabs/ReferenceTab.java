package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.util.List;

/** Consolidates occasional growing, pricing, and visualization references. */
public final class ReferenceTab implements FlowerFarmTab {

    private final List<FlowerFarmTab> references;
    private JPanel panel;
    private JTabbedPane workspace;

    public ReferenceTab(FlowerFarmTab roseGuide, FlowerFarmTab pricing,
                        FlowerFarmTab visualizer) {
        references = List.of(roseGuide, pricing, visualizer);
    }

    public ReferenceTab(FlowerFarmTab plantGuide, FlowerFarmTab roseGuide,
                        FlowerFarmTab pricing, FlowerFarmTab visualizer) {
        references = List.of(plantGuide, roseGuide, pricing, visualizer);
    }

    public ReferenceTab(FlowerFarmTab plantGuide, FlowerFarmTab eventServices,
                        FlowerFarmTab roseGuide, FlowerFarmTab pricing,
                        FlowerFarmTab visualizer) {
        references = List.of(plantGuide, eventServices, roseGuide, pricing, visualizer);
    }

    public ReferenceTab(FlowerFarmTab plantGuide, FlowerFarmTab eventServices,
                        FlowerFarmTab productionStudio, FlowerFarmTab roseGuide,
                        FlowerFarmTab pricing, FlowerFarmTab visualizer) {
        references = List.of(plantGuide, eventServices, productionStudio,
                roseGuide, pricing, visualizer);
    }

    @Override
    public String getTabTitle() {
        return "Reference & Planning";
    }

    @Override
    public String getDescription() {
        return "Sourced PNW plants, rose guidance, pricing units, and planning tools";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            panel = FarmUi.pageSurface(new JPanel(new BorderLayout()));
            panel.setName("reference.workspace.page");
            workspace = new JTabbedPane();
            workspace.setName("reference.workspace");
            workspace.getAccessibleContext().setAccessibleName("Farm reference workspaces");
            for (int index = 0; index < references.size(); index++) {
                FlowerFarmTab reference = references.get(index);
                workspace.addTab(visibleTitle(reference.getTabTitle()),
                        reference.getUIComponent());
                workspace.setMnemonicAt(index, mnemonic(reference.getTabTitle()));
            }
            workspace.addChangeListener(event -> focusSelectedReference());
            panel.add(workspace, BorderLayout.CENTER);
        }
        return panel;
    }

    @Override
    public void initialize() {
        getUIComponent();
        references.forEach(FlowerFarmTab::initialize);
    }

    @Override
    public void refreshData() {
        references.forEach(FlowerFarmTab::refreshData);
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        references.forEach(reference -> reference.applyRolePermissions(canWrite));
    }

    /** Opens an old deep-link title inside the consolidated workspace. */
    public boolean selectReference(String title) {
        getUIComponent();
        for (int index = 0; index < references.size(); index++) {
            if (references.get(index).getTabTitle().equals(title)) {
                workspace.setSelectedIndex(index);
                return true;
            }
        }
        return false;
    }

    private void focusSelectedReference() {
        int index = workspace.getSelectedIndex();
        if (index >= 0 && index < references.size()) {
            javax.swing.SwingUtilities.invokeLater(() ->
                    FarmUi.focusFirstInteractive(references.get(index).getUIComponent()));
        }
    }

    private static String visibleTitle(String title) {
        return switch (title) {
            case "PNW Plant Guide" -> "Plant guide";
            case "Event Services" -> "Event services";
            case "Production Studio" -> "Production studio";
            case "Rose Varieties" -> "Rose guide";
            case "Pricing Info" -> "Pricing";
            case "Rose Visualizer" -> "Growth visualizer";
            default -> title;
        };
    }

    private static char mnemonic(String title) {
        return switch (title) {
            case "PNW Plant Guide" -> 'N';
            case "Event Services" -> 'E';
            case "Production Studio" -> 'U';
            case "Rose Varieties" -> 'R';
            case "Pricing Info" -> 'P';
            case "Rose Visualizer" -> 'G';
            default -> Character.toUpperCase(title.charAt(0));
        };
    }
}
