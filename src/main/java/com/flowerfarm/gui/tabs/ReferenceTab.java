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

    @Override
    public String getTabTitle() {
        return "Reference & Planning";
    }

    @Override
    public String getDescription() {
        return "Rose guidance, pricing units, and growth-pattern planning tools";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            panel = new JPanel(new BorderLayout());
            panel.setName("reference.workspace.page");
            panel.setBackground(FarmUi.pageBackgroundColor());
            workspace = new JTabbedPane();
            workspace.setName("reference.workspace");
            workspace.getAccessibleContext().setAccessibleName("Farm reference workspaces");
            workspace.addTab("Rose guide", references.get(0).getUIComponent());
            workspace.addTab("Pricing", references.get(1).getUIComponent());
            workspace.addTab("Growth visualizer", references.get(2).getUIComponent());
            workspace.setMnemonicAt(0, 'R');
            workspace.setMnemonicAt(1, 'P');
            workspace.setMnemonicAt(2, 'G');
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
}
