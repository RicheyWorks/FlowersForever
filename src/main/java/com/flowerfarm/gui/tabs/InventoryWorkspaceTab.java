package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.util.List;

/** Groups stock counting and product creation into one inventory workflow. */
public final class InventoryWorkspaceTab implements FlowerFarmTab {

    private final List<FlowerFarmTab> workflows;
    private JPanel panel;
    private JTabbedPane workspace;

    public InventoryWorkspaceTab(FlowerFarmTab stock, FlowerFarmTab addItem) {
        workflows = List.of(stock, addItem);
    }

    @Override
    public String getTabTitle() {
        return "Inventory";
    }

    @Override
    public String getDescription() {
        return "Search stock, record physical counts, and add products or supplies";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            panel = FarmUi.pageSurface(new JPanel(new BorderLayout()));
            panel.setName("inventory.workspace.page");
            workspace = new JTabbedPane();
            workspace.setName("inventory.workspace");
            workspace.getAccessibleContext().setAccessibleName("Inventory workspaces");
            workspace.addTab("Stock & counts", workflows.get(0).getUIComponent());
            workspace.addTab("Add item", workflows.get(1).getUIComponent());
            workspace.setMnemonicAt(0, 'S');
            workspace.setMnemonicAt(1, 'A');
            workspace.addChangeListener(event -> focusSelectedWorkflow());
            panel.add(workspace, BorderLayout.CENTER);
        }
        return panel;
    }

    @Override
    public void initialize() {
        getUIComponent();
        workflows.forEach(FlowerFarmTab::initialize);
    }

    @Override
    public void refreshData() {
        workflows.forEach(FlowerFarmTab::refreshData);
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        workflows.forEach(workflow -> workflow.applyRolePermissions(canWrite));
    }

    /** Opens stock for Inventory links or product creation for Add Item links. */
    public boolean selectWorkspace(String title) {
        getUIComponent();
        if ("Inventory".equals(title)) {
            workspace.setSelectedIndex(0);
            return true;
        }
        if ("Add Item".equals(title)) {
            workspace.setSelectedIndex(1);
            return true;
        }
        return false;
    }

    private void focusSelectedWorkflow() {
        int index = workspace.getSelectedIndex();
        if (index >= 0 && index < workflows.size()) {
            javax.swing.SwingUtilities.invokeLater(() ->
                    FarmUi.focusFirstInteractive(workflows.get(index).getUIComponent()));
        }
    }
}
