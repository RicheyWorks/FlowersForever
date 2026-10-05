package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmFileChooser;
import com.flowerfarm.gui.presenter.InventoryItemPresenter;
import com.flowerfarm.gui.view.InventoryItemFormView;
import com.flowerfarm.gui.view.InventoryDraftReviewView;
import com.flowerfarm.gui.view.StockCountView;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Modern Inventory management tab with integrated live search, color-coded
 * stock levels, sortable columns, edit/delete actions, and CSV export of the
 * currently visible (filtered &amp; sorted) rows.
 *
 * <p>Mutations are delegated to the {@link InventoryService}; after a change the
 * tab asks the {@link TabHost} to refresh every tab so the Dashboard KPIs stay
 * consistent with the table.
 */
public class InventoryTab implements FlowerFarmTab {

    private static final String[] COLUMNS =
            {"Name", "Category", "Price", "Unit", "Cost", "Quantity", "Notes"};
    private static final int QTY_COLUMN = 5;
    private static final int NOTES_COLUMN = 6;
    private static final int LOW_STOCK_THRESHOLD = 10;

    private final InventoryService inventoryService;
    private final InventoryItemPresenter itemPresenter;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final FarmFileChooser files;

    private JPanel panel;
    private DefaultTableModel tableModel;
    private JTable inventoryTable;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField searchField;
    private JLabel statusLabel;
    private JLabel accessLabel;
    private JLabel documentStatusLabel;
    private JButton editBtn;
    private JButton deleteBtn;
    private JButton stockCountBtn;
    private JButton refreshBtn;
    private JButton reorderBtn;
    private JButton priceListBtn;
    private JButton exportBtn;
    private JButton reviewNameConflictBtn;
    private JButton reviewDraftsBtn;
    private JLabel draftStatusLabel;
    private JPanel draftPanel;
    private JButton returnHandoffBtn;
    private JButton addMissingProductBtn;
    private JLabel handoffLabel;
    private JLabel missingProductLabel;
    private JPanel handoffPanel;
    private JPanel missingProductPanel;
    private JComboBox<String> missingProductBox;
    private JToggleButton lowStockOnlyBtn;
    private List<? extends RowSorter.SortKey> sortBeforeLowStock = List.of();
    private List<Item> currentItems = List.of();
    private List<String> productQueue = List.of();
    private List<String> missingQueueProducts = List.of();
    private String productQueueReturnTab;
    private String productQueueReturnLabel;
    private boolean updatingProductQueue;
    private boolean canWrite = true;
    private boolean savingStockCount;
    private boolean savingItemMutation;
    private boolean refreshingInventory;
    private boolean inventoryRefreshQueued;
    private boolean runningDocument;
    private String inventoryRefreshFailure;
    private boolean inventoryNeedsRefresh;
    private String savedInventoryOutcome;
    private final Map<DraftKey, RetainedDraft> retainedDrafts = new LinkedHashMap<>();
    private ItemSelection pendingCountNext;
    private CardLayout tableCardLayout;
    private JPanel tableState;
    private FarmUi.EmptyStateView inventoryEmptyState;

    public InventoryTab(InventoryService inventoryService, TabHost host) {
        this(inventoryService, host, FarmDialogs.swing(), FarmFileChooser.swing());
    }

    public InventoryTab(InventoryService inventoryService, TabHost host,
                        FarmDialogs dialogs) {
        this(inventoryService, host, dialogs, FarmFileChooser.swing());
    }

    public InventoryTab(InventoryService inventoryService, TabHost host,
                        FarmDialogs dialogs, FarmFileChooser files) {
        this.inventoryService = inventoryService;
        this.itemPresenter = new InventoryItemPresenter(inventoryService);
        this.host = host;
        this.dialogs = java.util.Objects.requireNonNull(dialogs, "dialogs");
        this.files = java.util.Objects.requireNonNull(files, "files");
    }

    @Override
    public String getTabTitle() {
        return "Inventory";
    }

    @Override
    public String getDescription() {
        return "Search, sort, edit, and export inventory";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUI();
        }
        return panel;
    }

    @Override
    public void initialize() {
        refreshData();
    }

    @Override
    public void refreshData() {
        if (tableModel == null) {
            return;
        }
        startInventoryRefresh();
    }

    void startInventoryRefresh() {
        if (savingStockCount || savingItemMutation) {
            inventoryRefreshQueued = true;
            return;
        }
        if (refreshingInventory) {
            inventoryRefreshQueued = true;
            showInventoryRefreshState(
                    "Refresh queued · current inventory load finishing",
                    "A newer inventory reload will start when the current query returns.",
                    FarmUi.Tone.INFO);
            return;
        }
        inventoryRefreshQueued = false;
        refreshingInventory = true;
        refreshBtn.setEnabled(false);
        updateInventoryActions();
        showInventoryRefreshState("Refreshing inventory…",
                "Loading the inventory list in the background. Existing rows remain visible.",
                FarmUi.Tone.INFO);
        if (host != null) {
            host.setStatus("Refreshing inventory…");
        }
        new SwingWorker<List<Item>, Void>() {
            @Override
            protected List<Item> doInBackground() {
                return List.copyOf(itemPresenter.items());
            }

            @Override
            protected void done() {
                try {
                    List<Item> items = get();
                    if (!inventoryRefreshQueued) {
                        ItemSelection selection = selectedItemIdentity();
                        replaceInventoryItems(items, selection);
                        inventoryNeedsRefresh = false;
                        inventoryRefreshFailure = null;
                        savedInventoryOutcome = null;
                        if (host != null) {
                            host.setStatus("Inventory refreshed: " + items.size() + " item(s).");
                        }
                    } else if (host != null) {
                        host.setStatus(
                                "A newer inventory refresh is queued; discarding the older result.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    if (!inventoryRefreshQueued) {
                        failInventoryRefresh(exception);
                    }
                } catch (java.util.concurrent.ExecutionException exception) {
                    if (!inventoryRefreshQueued) {
                        failInventoryRefresh(exception.getCause() == null
                                ? exception : exception.getCause());
                    }
                } catch (RuntimeException exception) {
                    if (!inventoryRefreshQueued) {
                        failInventoryRefresh(exception);
                    }
                } finally {
                    finishInventoryRefresh();
                }
            }
        }.execute();
    }

    private void failInventoryRefresh(Throwable failure) {
        inventoryNeedsRefresh = true;
        pendingCountNext = null;
        String detail = FarmUi.actionableError("Refreshing inventory", failure);
        String recovery = (savedInventoryOutcome == null ? "" : savedInventoryOutcome + " ")
                + detail + " Existing rows and filters were kept; retry when the data source is ready."
                + " Refresh inventory before editing, deleting, or recording another count."
                + (savedInventoryOutcome == null ? "" : " Do not repeat the saved action.");
        inventoryRefreshFailure = recovery;
        showInventoryRefreshState(inventoryRecoveryTitle(),
                recovery, FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus((savedInventoryOutcome == null ? "" : savedInventoryOutcome + " ")
                    + "Inventory refresh failed. Existing rows were kept; review the status and retry the refresh.");
        }
    }

    private void finishInventoryRefresh() {
        refreshingInventory = false;
        refreshBtn.setEnabled(true);
        updateInventoryActions();
        if (inventoryRefreshFailure != null) {
            showInventoryRefreshState(inventoryRecoveryTitle(),
                    inventoryRefreshFailure, FarmUi.Tone.DANGER);
        }
        if (inventoryRefreshQueued) {
            inventoryRefreshQueued = false;
            startInventoryRefresh();
        } else {
            continueStockCountWhenReady();
        }
    }

    private String inventoryRecoveryTitle() {
        return savedInventoryOutcome == null ? "Inventory refresh failed · retry"
                : savedInventoryOutcome + " Refresh inventory.";
    }

    private boolean inventoryMutationAvailable() {
        return canWrite && !savingStockCount && !savingItemMutation
                && !refreshingInventory && !inventoryNeedsRefresh && !runningDocument;
    }

    private void showInventoryRefreshState(String message, String description,
                                           FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(statusLabel, message, tone);
        statusLabel.getAccessibleContext().setAccessibleName(
                "Inventory results and selected product");
        statusLabel.getAccessibleContext().setAccessibleDescription(description);
        statusLabel.setToolTipText(description);
    }

    private void replaceInventoryItems(List<Item> items, ItemSelection selection) {
        currentItems = List.copyOf(items);
        tableModel.setRowCount(0);
        for (Item item : currentItems) {
            tableModel.addRow(new Object[]{
                    item.getName(),
                    item.getCategory(),
                    String.format("%.2f", item.getPrice()),
                    item.getUnit(),
                    String.format("%.2f", item.getCost()),
                    item.getQuantity(),
                    item.getNotes()
            });
        }
        updateStatus();
        restoreSelection(selection);
    }

    private ItemSelection selectedItemIdentity() {
        if (inventoryTable == null || inventoryTable.getSelectedRow() < 0) {
            return null;
        }
        int modelRow = inventoryTable.convertRowIndexToModel(inventoryTable.getSelectedRow());
        return modelRow < currentItems.size()
                ? ItemSelection.from(currentItems.get(modelRow)) : null;
    }

    private void restoreSelection(ItemSelection selection) {
        if (selection == null || inventoryTable == null) {
            return;
        }
        for (int modelRow = 0; modelRow < currentItems.size(); modelRow++) {
            if (selection.matches(currentItems.get(modelRow))) {
                int viewRow = inventoryTable.convertRowIndexToView(modelRow);
                if (viewRow >= 0) {
                    inventoryTable.setRowSelectionInterval(viewRow, viewRow);
                }
                return;
            }
        }
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        this.canWrite = canWrite;
        // Search / refresh / export stay enabled for VIEWER
        updateInventoryActions();
        updateMissingProductControls();
        if (accessLabel != null) {
            FarmUi.updateStatusBadge(accessLabel,
                    canWrite ? "HAND / OWNER · editing available" : "VIEWER · read and export only",
                    canWrite ? FarmUi.Tone.SUCCESS : FarmUi.Tone.NEUTRAL);
        }
    }

    private void buildUI() {
        panel = FarmUi.page();

        statusLabel = FarmUi.wrappingStatusBadge("Loading inventory…", FarmUi.Tone.NEUTRAL);
        accessLabel = FarmUi.statusBadge("Checking access…", FarmUi.Tone.NEUTRAL);
        documentStatusLabel = FarmUi.wrappingStatusBadge("Reports ready", FarmUi.Tone.NEUTRAL);
        documentStatusLabel.setName("inventory.documentStatus");
        documentStatusLabel.getAccessibleContext().setAccessibleName(
                "Inventory report and export status");
        refreshBtn = FarmUi.secondaryButton("Refresh");
        refreshBtn.setToolTipText("Reload inventory from the database");
        refreshBtn.addActionListener(e -> refreshData());
        JPanel header = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        header.setOpaque(false);
        header.add(FarmUi.pageHeader(
                "Inventory",
                "Search stock, review pricing, and prepare cooler restock sheets.",
                FarmUi.toolbar(accessLabel, refreshBtn)), BorderLayout.NORTH);
        JPanel feedback = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        feedback.setOpaque(false);
        feedback.add(statusLabel, BorderLayout.NORTH);
        feedback.add(documentStatusLabel, BorderLayout.CENTER);
        header.add(feedback, BorderLayout.CENTER);
        panel.add(header, BorderLayout.NORTH);

        searchField = FarmUi.searchField(
                "Inventory search",
                "Search name, category, or notes",
                28);
        FarmUi.initialFocus(searchField);
        searchField.setToolTipText("Filter inventory by name, category, or notes…");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { searchChanged(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { searchChanged(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { searchChanged(); }
        });

        JButton clearSearchBtn = FarmUi.secondaryButton("Clear search");
        clearSearchBtn.addActionListener(e -> {
            clearProductQueueHandoff();
            searchField.setText("");
            filterTable();
            searchField.requestFocusInWindow();
        });
        lowStockOnlyBtn = new JToggleButton("Low stock only");
        lowStockOnlyBtn.setName("inventory.lowStockOnly");
        lowStockOnlyBtn.setToolTipText(
                "Show products with " + LOW_STOCK_THRESHOLD
                        + " or fewer on hand, most depleted first.");
        lowStockOnlyBtn.getAccessibleContext().setAccessibleName(
                "Show low-stock inventory only");
        lowStockOnlyBtn.addActionListener(event -> {
            clearProductQueueHandoff();
            toggleLowStockView();
        });
        JLabel searchLabel = new JLabel("Search inventory");
        searchLabel.setLabelFor(searchField);
        searchLabel.setDisplayedMnemonic('S');
        JPanel searchTools = FarmUi.toolbar(
                searchLabel, searchField, clearSearchBtn, lowStockOnlyBtn);
        handoffLabel = FarmUi.statusBadge("Focused stock queue", FarmUi.Tone.INFO);
        handoffLabel.setName("inventory.handoffStatus");
        handoffLabel.setVisible(false);
        handoffLabel.getAccessibleContext().setAccessibleName(
                "Focused inventory handoff status");
        returnHandoffBtn = FarmUi.secondaryButton("Return to source");
        returnHandoffBtn.setName("inventory.returnHandoff");
        returnHandoffBtn.setVisible(false);
        returnHandoffBtn.setToolTipText(
                "Return to the workflow that requested this focused stock review.");
        returnHandoffBtn.addActionListener(event -> returnToHandoffSource());
        handoffPanel = FarmUi.toolbar(handoffLabel, returnHandoffBtn,
                FarmUi.formHint(
                        "Count or replenish these products, then return and refresh the source workflow."));
        handoffPanel.setVisible(false);
        missingProductLabel = FarmUi.statusBadge(
                "Requested product missing", FarmUi.Tone.WARNING);
        missingProductLabel.setName("inventory.missingProductStatus");
        missingProductLabel.setVisible(false);
        missingProductLabel.getAccessibleContext().setAccessibleName(
                "Missing focused inventory products");
        missingProductBox = new JComboBox<>();
        missingProductBox.setName("inventory.missingProductChoice");
        missingProductBox.setVisible(false);
        missingProductBox.getAccessibleContext().setAccessibleName(
                "Choose a missing product to add to inventory");
        addMissingProductBtn = FarmUi.primaryButton("Add missing product");
        addMissingProductBtn.setName("inventory.addMissingProduct");
        addMissingProductBtn.setVisible(false);
        addMissingProductBtn.setToolTipText(
                "Open Add Item with the selected missing product name prepared safely.");
        addMissingProductBtn.getAccessibleContext().setAccessibleName(
                "Add selected missing product to inventory");
        addMissingProductBtn.addActionListener(event -> openMissingProductEntry());
        missingProductPanel = FarmUi.toolbar(
                missingProductLabel, missingProductBox, addMissingProductBtn,
                FarmUi.formHint("Create the SKU before its count can satisfy this queue."));
        missingProductPanel.setVisible(false);
        JPanel searchWorkspace = new JPanel();
        searchWorkspace.setOpaque(false);
        searchWorkspace.setLayout(new BoxLayout(searchWorkspace, BoxLayout.Y_AXIS));
        searchWorkspace.add(searchTools);
        searchWorkspace.add(handoffPanel);
        searchWorkspace.add(missingProductPanel);

        // ── Table ────────────────────────────────────────────────────────────
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        inventoryTable = new JTable(tableModel);
        inventoryTable.setName("inventory.table");
        inventoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        FarmUi.configureTable(inventoryTable, "Inventory stock table");
        inventoryTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateInventoryActions();
            }
        });

        sorter = new TableRowSorter<>(tableModel);
        sorter.setComparator(QTY_COLUMN, (left, right) -> Integer.compare(
                ((Number) left).intValue(), ((Number) right).intValue()));
        inventoryTable.setRowSorter(sorter);

        // Color coding for low stock on the Qty column.
        inventoryTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    Color bg = table.getBackground();
                    if (column == QTY_COLUMN && value != null) {
                        try {
                            int qty = Integer.parseInt(value.toString().trim());
                            bg = FarmUi.stockBackground(qty, table.getBackground());
                        } catch (NumberFormatException ignored) {
                            // leave default background
                        }
                    }
                    c.setBackground(bg);
                }
                return c;
            }
        });

        JScrollPane tableScroll = FarmUi.tableScroll(inventoryTable);
        tableScroll.setPreferredSize(new Dimension(720, 220));
        tableCardLayout = new CardLayout();
        tableState = new JPanel(tableCardLayout);
        tableState.add(tableScroll, "table");
        inventoryEmptyState = FarmUi.emptyStateView(
                "No inventory yet",
                "Add the first flower or farm product in Add item; it will appear here after save.");
        tableState.add(inventoryEmptyState.component(), "empty");

        JPanel content = new InventoryWorkspace();
        content.setOpaque(false);
        content.add(FarmUi.section(
                "Find stock",
                "Results update as you type; press Alt+S to focus the search field.",
                searchWorkspace), BorderLayout.NORTH);
        content.add(FarmUi.section(
                "Current stock",
                "Select a row to edit or delete it. Low quantities are highlighted.",
                tableState), BorderLayout.CENTER);

        // ── Bottom action bar ────────────────────────────────────────────────
        stockCountBtn = FarmUi.primaryButton("Record stock count…");
        stockCountBtn.setToolTipText(
                "Replace the selected item's quantity with a physical cooler or shed count.");
        editBtn = FarmUi.secondaryButton("Edit selected");
        deleteBtn = FarmUi.dangerButton("Delete selected");
        reorderBtn = FarmUi.secondaryButton("Low-stock reorder…");
        reorderBtn.setToolTipText("Products at or below a chosen amount, with suggested restock quantities.");
        priceListBtn = FarmUi.secondaryButton("Price list…");
        priceListBtn.setToolTipText("Wholesale / market price list PDF (in-stock by default). VIEWER OK.");
        exportBtn = FarmUi.secondaryButton("Export visible CSV…");
        reviewNameConflictBtn = FarmUi.secondaryButton("Review existing SKU");
        reviewNameConflictBtn.setName("inventory.reviewNameConflict");
        reviewNameConflictBtn.setEnabled(false);
        reviewNameConflictBtn.setVisible(false);
        reviewNameConflictBtn.setToolTipText(
                "Open the existing product whose name conflicts with the retained edit draft.");
        reviewNameConflictBtn.getAccessibleContext().setAccessibleName(
                "Review the existing conflicting inventory product");

        stockCountBtn.addActionListener(e -> recordStockCount());
        editBtn.addActionListener(this::editSelected);
        deleteBtn.addActionListener(this::deleteSelected);
        reorderBtn.addActionListener(e -> showLowStockReorder());
        priceListBtn.addActionListener(e -> showPriceList());
        exportBtn.addActionListener(e -> exportVisibleToCsv());
        reviewNameConflictBtn.addActionListener(e -> reviewExistingNameConflict());

        JPanel actionRows = new JPanel(new GridLayout(0, 1, 0, FarmUi.CONTROL_GAP));
        actionRows.setOpaque(false);
        actionRows.add(FarmUi.toolbar(stockCountBtn, editBtn, deleteBtn, reviewNameConflictBtn));
        actionRows.add(FarmUi.toolbar(reorderBtn, priceListBtn, exportBtn));
        draftStatusLabel = FarmUi.wrappingStatusBadge("No retained drafts", FarmUi.Tone.NEUTRAL);
        draftStatusLabel.setName("inventory.draftStatus");
        reviewDraftsBtn = FarmUi.secondaryButton("Review retained drafts…");
        reviewDraftsBtn.setName("inventory.reviewDrafts");
        reviewDraftsBtn.addActionListener(event -> reviewRetainedDrafts());
        draftPanel = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        draftPanel.setOpaque(false);
        draftPanel.add(draftStatusLabel, BorderLayout.NORTH);
        draftPanel.add(FarmUi.toolbar(reviewDraftsBtn), BorderLayout.CENTER);
        JPanel actionPanel = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        actionPanel.setOpaque(false);
        actionPanel.add(actionRows, BorderLayout.NORTH);
        actionPanel.add(draftPanel, BorderLayout.CENTER);
        content.add(FarmUi.section(
                "Inventory actions",
                "Select a row to confirm the Count, Edit, or Delete target. Exports remain available.",
                actionPanel), BorderLayout.SOUTH);

        JScrollPane workspaceScroll = new JScrollPane(content,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        workspaceScroll.setName("inventory.workspaceScroll");
        workspaceScroll.setBorder(BorderFactory.createEmptyBorder());
        workspaceScroll.getViewport().setOpaque(false);
        workspaceScroll.setOpaque(false);
        workspaceScroll.getVerticalScrollBar().setUnitIncrement(FarmUi.TABLE_ROW_HEIGHT);
        panel.add(workspaceScroll, BorderLayout.CENTER);
        updateInventoryActions();
        applyRolePermissions(host == null || host.canMutateData());
    }

    private static final class InventoryWorkspace extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        private InventoryWorkspace() {
            super(new BorderLayout(0, FarmUi.SECTION_GAP));
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return FarmUi.TABLE_ROW_HEIGHT;
        }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(FarmUi.TABLE_ROW_HEIGHT, visible.height - FarmUi.TABLE_ROW_HEIGHT);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() >= getPreferredSize().height;
        }
    }

    private void updateInventoryActions() {
        if (inventoryTable == null || stockCountBtn == null) {
            return;
        }
        Item selectedItem = selectedItemForAction();
        boolean selected = selectedItem != null;
        GuiPermissions.setWritable(inventoryMutationAvailable() && selected,
                stockCountBtn, editBtn, deleteBtn);
        boolean documentsReady = !savingStockCount && !savingItemMutation
                && !refreshingInventory && !runningDocument;
        reorderBtn.setEnabled(documentsReady);
        priceListBtn.setEnabled(documentsReady);
        exportBtn.setEnabled(documentsReady && inventoryTable.getRowCount() > 0);
        RetainedDraft selectedEdit = selectedItem == null ? null : retainedDraft(selectedItem, false);
        boolean hasNameConflict = selectedEdit != null && selectedEdit.conflictingName() != null;
        reviewNameConflictBtn.setVisible(hasNameConflict);
        reviewNameConflictBtn.setEnabled(hasNameConflict
                && !savingStockCount && !savingItemMutation
                && !refreshingInventory && !runningDocument);
        updateRetainedDraftControls();
        if (statusLabel != null) {
            if (selectedItem == null) {
                updateInventorySummaryBadge();
                statusLabel.getAccessibleContext().setAccessibleDescription(
                        "No inventory product is selected. " + statusLabel.getText() + ".");
                stockCountBtn.setToolTipText(
                        "Select a product, then replace its quantity with a physical count.");
                editBtn.setToolTipText("Select the inventory product to edit.");
                deleteBtn.setToolTipText("Select the inventory product to delete.");
            } else {
                String target = compactName(selectedItem.getName()) + " · "
                        + selectedItem.getQuantity() + " " + selectedItem.getUnit()
                        + " · " + inventorySummaryText();
                FarmUi.updateStatusBadge(statusLabel, target,
                        selectedItem.getQuantity() <= LOW_STOCK_THRESHOLD
                                ? FarmUi.Tone.WARNING : FarmUi.Tone.INFO);
                statusLabel.getAccessibleContext().setAccessibleName(
                        "Inventory results and selected product");
                statusLabel.getAccessibleContext().setAccessibleDescription(
                        "Inventory actions apply to " + selectedItem.getName()
                                + ". " + inventorySummaryText() + ".");
                statusLabel.setToolTipText("Selected “" + selectedItem.getName() + "” · "
                        + selectedItem.getQuantity() + " " + selectedItem.getUnit()
                        + " on hand · " + inventorySummaryText());
                stockCountBtn.setToolTipText("Record a physical count for “"
                        + selectedItem.getName() + "”.");
                editBtn.setToolTipText("Edit “" + selectedItem.getName() + "”.");
                deleteBtn.setToolTipText("Permanently delete “" + selectedItem.getName() + "”.");
            }
            if (inventoryRefreshFailure != null && !refreshingInventory
                    && !savingStockCount && !savingItemMutation) {
                showInventoryRefreshState(inventoryRecoveryTitle(),
                        inventoryRefreshFailure, FarmUi.Tone.DANGER);
            }
        }
    }

    private Item selectedItemForAction() {
        if (inventoryTable == null || inventoryTable.getSelectedRow() < 0) {
            return null;
        }
        int modelRow = inventoryTable.convertRowIndexToModel(inventoryTable.getSelectedRow());
        return modelRow >= 0 && modelRow < currentItems.size()
                ? currentItems.get(modelRow) : null;
    }

    private static String compactName(String name) {
        return name.length() <= 24 ? name : name.substring(0, 23) + "…";
    }

    private void recordStockCount() {
        if (!GuiPermissions.requireWrite(host, panel, "record inventory counts")) {
            return;
        }
        if (!inventoryMutationAvailable() || inventoryTable.getSelectedRow() < 0) {
            return;
        }
        int viewRow = inventoryTable.getSelectedRow();
        int modelRow = inventoryTable.convertRowIndexToModel(viewRow);
        Item current = currentItems.get(modelRow);
        InventorySnapshot reviewed = InventorySnapshot.from(current);
        ItemSelection nextItem = null;
        if (viewRow + 1 < inventoryTable.getRowCount()) {
            int nextModelRow = inventoryTable.convertRowIndexToModel(viewRow + 1);
            nextItem = ItemSelection.from(currentItems.get(nextModelRow));
        }
        StockCountView countView = new StockCountView(current);
        RetainedDraft retained = retainedDraft(current, true);
        if (retained != null) {
            countView.setCountedQuantity(retained.count());
        }
        Object[] options = nextItem == null
                ? new Object[]{"Save count", "Cancel"}
                : new Object[]{"Save & count next", "Save count", "Cancel"};
        int choice = dialogs.showOption(panel,
                draftEditor("Record physical count",
                        "System quantity: " + current.getQuantity() + " "
                                + current.getUnit()
                                + ". Enter what is physically on hand now.",
                        countView, retained, current),
                "Count “" + current.getName() + "”",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);
        boolean saveAndContinue = nextItem != null && choice == 0;
        boolean save = choice == 0 || (nextItem != null && choice == 1);
        if (save) {
            if (!reviewedInventoryStillSelected(reviewed) || retainedDraft(current, true) != retained) {
                retainIfUnchanged(retained, new RetainedDraft(reviewed, true,
                        countView.countedQuantity(), null, null));
                return;
            }
            startSelectedStockCountSave(
                    countView.countedQuantity(), countView.variance(), saveAndContinue);
        } else if (retained != null) {
            retainIfUnchanged(retained, new RetainedDraft(retained.product(), true,
                    countView.countedQuantity(), null, null));
        }
    }

    /** Saves one captured physical count without blocking Swing's event thread. */
    void startSelectedStockCountSave(int countedQuantity, int variance,
                                     boolean continueToNext) {
        if (!inventoryMutationAvailable() || inventoryTable == null
                || inventoryTable.getSelectedRow() < 0) {
            return;
        }
        int viewRow = inventoryTable.getSelectedRow();
        int modelRow = inventoryTable.convertRowIndexToModel(viewRow);
        Item current = currentItems.get(modelRow);
        ItemSelection currentIdentity = ItemSelection.from(current);
        RetainedDraft submittedDraft = retainedDraft(current, true);
        ItemSelection nextItem = null;
        if (continueToNext && viewRow + 1 < inventoryTable.getRowCount()) {
            int nextModelRow = inventoryTable.convertRowIndexToModel(viewRow + 1);
            nextItem = ItemSelection.from(currentItems.get(nextModelRow));
        }
        ItemSelection capturedNext = nextItem;

        savingStockCount = true;
        setStockCountControlsEnabled(false);
        stockCountBtn.setText("Saving count…");
        String busy = "Saving “" + current.getName() + "” · "
                + countedQuantity + " " + current.getUnit();
        FarmUi.updateStatusBadge(statusLabel, busy, FarmUi.Tone.INFO);
        statusLabel.getAccessibleContext().setAccessibleName(
                "Inventory results and selected product");
        statusLabel.setToolTipText(
                "The selected product and count are locked until the inventory save finishes.");
        statusLabel.getAccessibleContext().setAccessibleDescription(
                busy + ". The selected product is locked to prevent a duplicate count.");
        if (host != null) {
            host.setStatus("Saving the physical count for “" + current.getName() + "”…");
        }

        new SwingWorker<StockCountResult, Void>() {
            @Override
            protected StockCountResult doInBackground() {
                Item saved = current.getId() == null
                        ? itemPresenter.setQuantity(modelRow, current, countedQuantity)
                        : itemPresenter.setQuantityById(
                                current.getId(), current, countedQuantity);
                return new StockCountResult(saved, readInventoryAfterMutation(),
                        capturedNext, variance);
            }

            @Override
            protected void done() {
                StockCountResult result;
                try {
                    result = get();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failStockCount(currentIdentity, countedQuantity, current, submittedDraft, exception);
                    return;
                } catch (java.util.concurrent.ExecutionException exception) {
                    failStockCount(currentIdentity, countedQuantity, current, submittedDraft,
                            exception.getCause() == null ? exception : exception.getCause());
                    return;
                }
                removeSubmittedDraft(current, true, submittedDraft);
                completeStockCount(result);
            }
        }.execute();
    }

    private void completeStockCount(StockCountResult result) {
        ItemSelection selection = result.nextItem() == null
                ? ItemSelection.from(result.saved()) : result.nextItem();
        boolean refreshed = applySavedInventory(result.inventory(), selection,
                "Count for “" + result.saved().getName() + "” was saved.");
        finishStockCountControls();
        String message = "Counted “" + result.saved().getName() + "”: "
                + result.saved().getQuantity() + " " + result.saved().getUnit()
                + " on hand" + varianceSummary(result.variance()) + ".";
        if (refreshed) {
            showInventoryRefreshState(
                compactName(result.saved().getName()) + " saved · "
                        + result.saved().getQuantity() + " " + result.saved().getUnit(),
                    message, FarmUi.Tone.SUCCESS);
            pendingCountNext = result.nextItem();
        }
        notifyInventorySaved(message);
        continueStockCountWhenReady();
    }

    private void continueStockCountWhenReady() {
        if (pendingCountNext == null || refreshingInventory || inventoryRefreshQueued) {
            return;
        }
        ItemSelection next = pendingCountNext;
        pendingCountNext = null;
        if (inventoryMutationAvailable() && panel.isShowing()) {
            SwingUtilities.invokeLater(() -> {
                Item selected = selectedItemForAction();
                if (panel.isShowing() && selected != null && next.matches(selected)) {
                    recordStockCount();
                }
            });
        }
    }

    private void failStockCount(ItemSelection identity, int countedQuantity,
                                Item current, RetainedDraft submittedDraft, Throwable failure) {
        finishStockCountControls();
        retainIfUnchanged(submittedDraft, new RetainedDraft(InventorySnapshot.from(current),
                true, countedQuantity, null, null));
        restoreSelection(identity);
        String detail = FarmUi.actionableError("Recording this stock count", failure);
        String recovery = detail + " The entered count of " + countedQuantity + " "
                + current.getUnit() + " is retained for retry.";
        FarmUi.updateStatusBadge(statusLabel,
                compactName(current.getName()) + " not saved · retry count",
                FarmUi.Tone.DANGER);
        statusLabel.getAccessibleContext().setAccessibleName(
                "Inventory results and selected product");
        statusLabel.getAccessibleContext().setAccessibleDescription(recovery);
        statusLabel.setToolTipText(recovery);
        if (host != null) {
            host.setStatus("Count for “" + current.getName()
                    + "” was not saved. Review the status and retry.");
        }
        if (panel.isShowing()) {
            JOptionPane.showMessageDialog(panel, recovery,
                    "Count not saved", JOptionPane.ERROR_MESSAGE);
        }
        resumeQueuedInventoryRefresh();
    }

    private void finishStockCountControls() {
        savingStockCount = false;
        stockCountBtn.setText("Record stock count…");
        setStockCountControlsEnabled(true);
        updateInventoryActions();
    }

    private void setStockCountControlsEnabled(boolean enabled) {
        inventoryTable.setEnabled(enabled);
        searchField.setEnabled(enabled);
        lowStockOnlyBtn.setEnabled(enabled);
        refreshBtn.setEnabled(enabled && !refreshingInventory);
        updateInventoryActions();
    }

    private record StockCountResult(Item saved, InventoryReadback inventory,
                                    ItemSelection nextItem, int variance) {
    }

    private static String varianceSummary(int variance) {
        if (variance == 0) {
            return "; matched the system quantity";
        }
        return variance > 0
                ? "; adjusted up by " + variance
                : "; adjusted down by " + Math.abs(variance);
    }

    private void filterTable() {
        String text = searchField.getText().trim();
        boolean lowStockOnly = lowStockOnlyBtn != null && lowStockOnlyBtn.isSelected();
        if (text.isEmpty() && !lowStockOnly && productQueue.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            java.util.List<RowFilter<DefaultTableModel, Integer>> filters =
                    new java.util.ArrayList<>();
            if (!text.isEmpty()) {
                filters.add(RowFilter.regexFilter(
                        "(?i)" + java.util.regex.Pattern.quote(text)));
            }
            if (!productQueue.isEmpty()) {
                filters.add(new RowFilter<>() {
                    @Override
                    public boolean include(Entry<? extends DefaultTableModel,
                            ? extends Integer> entry) {
                        String name = String.valueOf(entry.getValue(0));
                        return productQueue.stream().anyMatch(
                                product -> product.equalsIgnoreCase(name));
                    }
                });
            }
            if (lowStockOnly) {
                filters.add(new RowFilter<>() {
                    @Override
                    public boolean include(Entry<? extends DefaultTableModel,
                            ? extends Integer> entry) {
                        Object value = entry.getValue(QTY_COLUMN);
                        return value instanceof Number number
                                && number.intValue() <= LOW_STOCK_THRESHOLD;
                    }
                });
            }
            sorter.setRowFilter(RowFilter.andFilter(filters));
        }
        updateStatus();
    }

    private void searchChanged() {
        if (!updatingProductQueue) {
            clearProductQueueHandoff();
        }
        filterTable();
    }

    /** Opens a focused inventory queue for an operational handoff such as Market Day. */
    public void showProductQueue(List<String> productNames) {
        showProductQueue(productNames, null, null);
    }

    /** Opens a focused queue and optionally exposes a route back to its source. */
    public void showProductQueue(List<String> productNames,
                                 String returnTab, String returnLabel) {
        getUIComponent();
        productQueue = productNames == null ? List.of() : productNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        productQueueReturnTab = returnTab == null || returnTab.isBlank()
                ? null : returnTab.trim();
        productQueueReturnLabel = returnLabel == null || returnLabel.isBlank()
                ? productQueueReturnTab : returnLabel.trim();
        updateHandoffControls();
        updatingProductQueue = true;
        try {
            searchField.setText("");
            if (lowStockOnlyBtn.isSelected()) {
                lowStockOnlyBtn.setSelected(false);
                sorter.setSortKeys(sortBeforeLowStock);
            }
        } finally {
            updatingProductQueue = false;
        }
        filterTable();
        if (inventoryTable.getRowCount() > 0) {
            inventoryTable.setRowSelectionInterval(0, 0);
            inventoryTable.scrollRectToVisible(inventoryTable.getCellRect(0, 0, true));
            inventoryTable.requestFocusInWindow();
        }
    }

    private void clearProductQueueHandoff() {
        productQueue = List.of();
        productQueueReturnTab = null;
        productQueueReturnLabel = null;
        updateHandoffControls();
    }

    private void updateHandoffControls() {
        if (handoffPanel == null || returnHandoffBtn == null || handoffLabel == null) {
            return;
        }
        boolean hasReturn = !productQueue.isEmpty() && productQueueReturnTab != null;
        handoffPanel.setVisible(hasReturn);
        handoffLabel.setVisible(hasReturn);
        returnHandoffBtn.setVisible(hasReturn);
        if (hasReturn) {
            String label = productQueueReturnLabel == null
                    ? productQueueReturnTab : productQueueReturnLabel;
            returnHandoffBtn.setText("Return to " + label);
            returnHandoffBtn.getAccessibleContext().setAccessibleName(
                    "Return to " + label + " after stock review");
            String description = productQueue.size() + " requested product"
                    + (productQueue.size() == 1 ? "" : "s")
                    + " · return to " + label + " when the stock review is finished";
            FarmUi.updateStatusBadge(handoffLabel, description, FarmUi.Tone.INFO);
            handoffLabel.getAccessibleContext().setAccessibleDescription(description);
            handoffLabel.setToolTipText(description);
        }
        handoffPanel.revalidate();
        handoffPanel.repaint();
    }

    private void returnToHandoffSource() {
        if (host == null || productQueueReturnTab == null) {
            return;
        }
        String label = productQueueReturnLabel == null
                ? productQueueReturnTab : productQueueReturnLabel;
        host.selectTab(productQueueReturnTab);
        host.setStatus("Returned to " + label
                + " after reviewing the focused stock queue. Refresh or rebuild to verify current stock.");
    }

    private void updateMissingProductControls() {
        if (missingProductPanel == null || missingProductBox == null
                || missingProductLabel == null || addMissingProductBtn == null) {
            return;
        }
        missingQueueProducts = productQueue.stream()
                .filter(requested -> currentItems.stream().noneMatch(item ->
                        requested.equalsIgnoreCase(item.getName())))
                .toList();
        Object previous = missingProductBox.getSelectedItem();
        missingProductBox.removeAllItems();
        missingQueueProducts.forEach(missingProductBox::addItem);
        if (previous != null && missingQueueProducts.stream()
                .anyMatch(product -> product.equalsIgnoreCase(String.valueOf(previous)))) {
            missingProductBox.setSelectedItem(previous);
        }
        boolean missing = !missingQueueProducts.isEmpty();
        missingProductPanel.setVisible(missing);
        missingProductLabel.setVisible(missing);
        missingProductBox.setVisible(missing);
        addMissingProductBtn.setVisible(missing);
        addMissingProductBtn.setEnabled(missing && canWrite);
        if (missing) {
            String names = String.join(", ", missingQueueProducts);
            String message = missingQueueProducts.size() + " requested product"
                    + (missingQueueProducts.size() == 1 ? " is" : "s are")
                    + " missing from Inventory";
            String guidance = message + ": " + names + ". "
                    + (canWrite
                    ? "Choose one and add its catalog details before returning to the queue."
                    : "A HAND or OWNER must add the missing catalog item.");
            FarmUi.updateStatusBadge(missingProductLabel, message,
                    canWrite ? FarmUi.Tone.WARNING : FarmUi.Tone.DANGER);
            missingProductLabel.setToolTipText(guidance);
            missingProductLabel.getAccessibleContext().setAccessibleDescription(guidance);
            addMissingProductBtn.setToolTipText(canWrite
                    ? "Open Add Item with the selected missing product name prepared safely."
                    : "A HAND or OWNER is required to add missing inventory products.");
        }
        missingProductPanel.revalidate();
        missingProductPanel.repaint();
    }

    private void openMissingProductEntry() {
        if (host == null || !canWrite) {
            return;
        }
        Object selected = missingProductBox.getSelectedItem();
        if (selected == null) {
            return;
        }
        String product = String.valueOf(selected).trim();
        host.showAddInventoryItem(product);
        host.setStatus("Opened Add Item for missing focused product “" + product
                + "”. Complete its catalog details, save it, then return to the stock queue.");
    }

    private void toggleLowStockView() {
        if (lowStockOnlyBtn.isSelected()) {
            sortBeforeLowStock = List.copyOf(sorter.getSortKeys());
            sorter.setSortKeys(List.of(
                    new RowSorter.SortKey(QTY_COLUMN, SortOrder.ASCENDING)));
        } else {
            sorter.setSortKeys(sortBeforeLowStock);
        }
        filterTable();
        if (lowStockOnlyBtn.isSelected() && inventoryTable.getRowCount() > 0) {
            inventoryTable.setRowSelectionInterval(0, 0);
            inventoryTable.scrollRectToVisible(inventoryTable.getCellRect(0, 0, true));
            if (host != null) {
                host.setStatus("Low-stock count queue ready: "
                        + inventoryTable.getRowCount() + " product(s), lowest quantity first.");
            }
        }
    }

    private void updateStatus() {
        if (statusLabel != null && inventoryTable != null) {
            int visible = inventoryTable.getRowCount();
            updateInventorySummaryBadge();
            if (tableCardLayout != null && tableState != null) {
                updateInventoryEmptyState();
                tableCardLayout.show(tableState, visible == 0 ? "empty" : "table");
            }
            updateInventoryActions();
            updateMissingProductControls();
        }
    }

    private void updateInventorySummaryBadge() {
        if (statusLabel == null || inventoryTable == null || tableModel == null) {
            return;
        }
        int visible = inventoryTable.getRowCount();
        String status = inventorySummaryText();
        FarmUi.Tone tone = visible > 0 ? FarmUi.Tone.SUCCESS
                : inventoryViewFiltered() ? FarmUi.Tone.WARNING : FarmUi.Tone.NEUTRAL;
        FarmUi.updateStatusBadge(statusLabel, status, tone);
        statusLabel.getAccessibleContext().setAccessibleName(
                "Inventory results and selected product");
    }

    private void updateInventoryEmptyState() {
        if (inventoryEmptyState == null) {
            return;
        }
        if (tableModel.getRowCount() == 0 && !inventoryViewFiltered()) {
            inventoryEmptyState.updateCopy(
                    "No inventory yet",
                    "Add the first flower in Add item, or use Help > Start here… / File > Import CSV… "
                            + "to bring products from a spreadsheet.");
        } else {
            inventoryEmptyState.updateCopy(
                    "No inventory matches this view",
                    "Clear the search, turn off Low stock only, or add an item.");
        }
    }

    private boolean inventoryViewFiltered() {
        return searchField != null && !searchField.getText().isBlank()
                || lowStockOnlyBtn != null && lowStockOnlyBtn.isSelected()
                || !productQueue.isEmpty();
    }

    private String inventorySummaryText() {
        int visible = inventoryTable.getRowCount();
        int total = tableModel.getRowCount();
        boolean lowStockOnly = lowStockOnlyBtn != null && lowStockOnlyBtn.isSelected();
        if (!productQueue.isEmpty()) {
            return visible + " focused product" + (visible == 1 ? "" : "s")
                    + " · " + productQueue.size() + " requested";
        }
        return lowStockOnly
                ? visible + " low-stock item" + (visible == 1 ? "" : "s")
                + " · at or below " + LOW_STOCK_THRESHOLD
                : visible == total ? total + " items"
                : visible + " of " + total + " items shown";
    }

    private void editSelected(java.awt.event.ActionEvent e) {
        if (!GuiPermissions.requireWrite(host, panel, "edit inventory")) {
            return;
        }
        if (!inventoryMutationAvailable()) {
            return;
        }
        int viewRow = inventoryTable.getSelectedRow();
        if (viewRow < 0) {
            dialogs.showMessage(panel, "Please select a row to edit.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = inventoryTable.convertRowIndexToModel(viewRow);

        Item current = currentItems.get(modelRow);
        InventorySnapshot reviewed = InventorySnapshot.from(current);
        InventoryItemFormView editor = new InventoryItemFormView();
        editor.setItem(current);
        ItemSelection identity = ItemSelection.from(current);
        RetainedDraft retained = retainedDraft(current, false);
        if (retained != null) {
            editor.setDraft(retained.edit());
        }
        while (true) {
            int choice = dialogs.showConfirm(panel,
                    draftEditor("Item details",
                            "All values stay in place if a field needs correction.", editor, retained, current),
                    "Edit “" + current.getName() + "”",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                if (retained != null) {
                    retainIfUnchanged(retained, new RetainedDraft(retained.product(), false,
                            null, editor.draft(), retained.conflictingName()));
                }
                if (host != null) {
                    host.setStatus(retained == null ? "Inventory edit cancelled. No changes were saved."
                            : "Inventory edit kept for later. No changes were saved.");
                }
                return;
            }
            editor.clearValidation();
            try {
                InventoryItemPresenter.ItemDraft draft = editor.draft();
                itemPresenter.toItem(draft);
                if (!reviewedInventoryStillSelected(reviewed) || retainedDraft(current, false) != retained) {
                    retainIfUnchanged(retained, new RetainedDraft(reviewed, false, null, draft, null));
                    return;
                }
                startItemEditSave(inventoryTable.convertRowIndexToModel(
                        inventoryTable.getSelectedRow()), current, identity, draft);
                return;
            } catch (InventoryItemPresenter.FieldValidationException exception) {
                editor.markError(exception.field(), exception.getMessage());
                dialogs.showMessage(panel,
                        exception.getMessage() + "\nYour other changes are still in the form.",
                        "Check the highlighted field", JOptionPane.WARNING_MESSAGE);
            } catch (RuntimeException exception) {
                dialogs.showMessage(panel,
                        FarmUi.actionableError("Saving this inventory item", exception),
                        "Item not saved", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
    }

    /** Saves one captured inventory edit without blocking Swing's event thread. */
    void startSelectedItemEdit(InventoryItemPresenter.ItemDraft draft) {
        if (!inventoryMutationAvailable() || inventoryTable == null
                || inventoryTable.getSelectedRow() < 0) {
            return;
        }
        int modelRow = inventoryTable.convertRowIndexToModel(inventoryTable.getSelectedRow());
        Item current = currentItems.get(modelRow);
        InventoryItemPresenter.ItemDraft captured = draft;
        RetainedDraft retained = retainedDraft(current, false);
        if (captured == null && retained != null) {
            captured = retained.edit();
        }
        if (captured != null) {
            startItemEditSave(modelRow, current, ItemSelection.from(current), captured);
        }
    }

    private void startItemEditSave(int modelRow, Item current, ItemSelection identity,
                                   InventoryItemPresenter.ItemDraft draft) {
        if (!inventoryMutationAvailable()) {
            return;
        }
        RetainedDraft submittedDraft = retainedDraft(current, false);
        savingItemMutation = true;
        setStockCountControlsEnabled(false);
        editBtn.setText("Saving edit…");
        showInventoryRefreshState("Saving “" + current.getName() + "”…",
                "The selected product and captured edit are locked while the database write finishes.",
                FarmUi.Tone.INFO);
        if (host != null) {
            host.setStatus("Saving inventory changes for “" + current.getName() + "”…");
        }

        new SwingWorker<ItemMutationResult, Void>() {
            @Override
            protected ItemMutationResult doInBackground() {
                Item saved = current.getId() == null
                        ? itemPresenter.update(modelRow, draft)
                        : itemPresenter.updateById(current.getId(), draft);
                return new ItemMutationResult(saved, readInventoryAfterMutation());
            }

            @Override
            protected void done() {
                ItemMutationResult result;
                try {
                    result = get();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failItemEdit(identity, draft, current, submittedDraft, exception);
                    return;
                } catch (java.util.concurrent.ExecutionException exception) {
                    failItemEdit(identity, draft, current, submittedDraft,
                            exception.getCause() == null ? exception : exception.getCause());
                    return;
                }
                removeSubmittedDraft(current, false, submittedDraft);
                boolean refreshed = applySavedInventory(result.inventory(),
                        ItemSelection.from(result.saved()),
                        "Edit for “" + result.saved().getName() + "” was saved.");
                finishItemMutation();
                if (refreshed) {
                    showInventoryRefreshState(
                            compactName(result.saved().getName()) + " updated",
                            "The inventory edit was saved and the updated product remains selected.",
                            FarmUi.Tone.SUCCESS);
                }
                notifyInventorySaved("Updated “" + result.saved().getName() + "”. The edit was saved.");
            }
        }.execute();
    }

    private void failItemEdit(ItemSelection identity, InventoryItemPresenter.ItemDraft draft,
                              Item current, RetainedDraft submittedDraft, Throwable failure) {
        finishItemMutation();
        String detail = FarmUi.actionableError("Saving this inventory item", failure);
        boolean duplicateName = failure instanceof InventoryService.DuplicateItemNameException;
        String conflictingName = duplicateName
                ? ((InventoryService.DuplicateItemNameException) failure).itemName() : null;
        retainIfUnchanged(submittedDraft, new RetainedDraft(InventorySnapshot.from(current),
                false, null, draft, conflictingName));
        restoreSelection(identity);
        String recovery = detail + " Every edited field was retained; "
                + (duplicateName
                ? "choose Edit selected to use a unique name, or Review existing SKU to inspect "
                        + "the conflicting product."
                : "choose Edit selected to review the same draft and retry.");
        showInventoryRefreshState(
                duplicateName
                        ? compactName(current.getName()) + " not renamed · name already used"
                        : compactName(current.getName()) + " not updated · retry edit",
                recovery, duplicateName ? FarmUi.Tone.WARNING : FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus(duplicateName
                    ? "Inventory already contains “" + conflictingName
                            + "”. The edited item was not renamed and its complete draft was kept."
                    : "Inventory edit for “" + current.getName()
                            + "” was not saved. The complete draft was kept for retry.");
        }
        if (panel.isShowing()) {
            JOptionPane.showMessageDialog(panel, recovery,
                    "Item not saved", JOptionPane.ERROR_MESSAGE);
            SwingUtilities.invokeLater(() -> editSelected(null));
        }
        resumeQueuedInventoryRefresh();
    }

    private void reviewExistingNameConflict() {
        Item selected = selectedItemForAction();
        RetainedDraft retained = selected == null ? null : retainedDraft(selected, false);
        if (retained == null || retained.conflictingName() == null) {
            return;
        }
        String product = retained.conflictingName();
        showProductQueue(List.of(product));
        if (host != null) {
            host.setStatus("Reviewing existing Inventory SKU “" + product
                    + "”. The other product's edit draft remains available when you reselect it.");
        }
    }

    private void deleteSelected(java.awt.event.ActionEvent e) {
        if (!GuiPermissions.requireWrite(host, panel, "delete inventory items")) {
            return;
        }
        if (!inventoryMutationAvailable()) {
            return;
        }
        int viewRow = inventoryTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(panel, "Please select a row to delete.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = inventoryTable.convertRowIndexToModel(viewRow);
        Item current = currentItems.get(modelRow);
        InventorySnapshot reviewed = InventorySnapshot.from(current);
        List<RetainedDraft> reviewedDrafts = draftsFor(current);
        String name = current.getName();

        int confirm = dialogs.showConfirm(panel,
                FarmUi.confirmationPanel(
                        "Delete “" + name + "”?",
                        "This permanently removes the product from inventory and cannot be undone."
                                + (reviewedDrafts.isEmpty() ? "" : " It also discards " + reviewedDrafts.size()
                                + " retained draft(s) for this product."),
                        FarmUi.Tone.DANGER),
                "Delete inventory item?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION && reviewedInventoryStillSelected(reviewed)) {
            if (!sameDrafts(reviewedDrafts, draftsFor(current))) {
                if (host != null) {
                    host.setStatus("Retained inventory drafts changed while the delete dialog was open. "
                            + "The product and drafts were kept; review deletion again.");
                }
                return;
            }
            startItemDelete(inventoryTable.convertRowIndexToModel(
                    inventoryTable.getSelectedRow()), current, ItemSelection.from(current));
        }
    }

    /** Deletes the currently selected captured item without blocking Swing. */
    void startSelectedItemDelete() {
        if (!inventoryMutationAvailable() || inventoryTable == null
                || inventoryTable.getSelectedRow() < 0) {
            return;
        }
        int modelRow = inventoryTable.convertRowIndexToModel(inventoryTable.getSelectedRow());
        Item current = currentItems.get(modelRow);
        startItemDelete(modelRow, current, ItemSelection.from(current));
    }

    private void startItemDelete(int modelRow, Item current, ItemSelection identity) {
        if (!inventoryMutationAvailable()) {
            return;
        }
        List<RetainedDraft> submittedDrafts = draftsFor(current);
        savingItemMutation = true;
        setStockCountControlsEnabled(false);
        deleteBtn.setText("Deleting…");
        showInventoryRefreshState("Deleting “" + current.getName() + "”…",
                "The selected product is locked until the database confirms deletion.",
                FarmUi.Tone.INFO);

        new SwingWorker<InventoryReadback, Void>() {
            @Override
            protected InventoryReadback doInBackground() {
                if (current.getId() == null) {
                    itemPresenter.delete(modelRow);
                } else {
                    itemPresenter.deleteById(current.getId());
                }
                return readInventoryAfterMutation();
            }

            @Override
            protected void done() {
                InventoryReadback result;
                try {
                    result = get();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failItemDelete(identity, current, exception);
                    return;
                } catch (java.util.concurrent.ExecutionException exception) {
                    failItemDelete(identity, current,
                            exception.getCause() == null ? exception : exception.getCause());
                    return;
                }
                for (RetainedDraft submitted : submittedDrafts) {
                    removeSubmittedDraft(current, submitted.countDraft(), submitted);
                }
                boolean refreshed = applySavedInventory(result, null,
                        "“" + current.getName() + "” was deleted.");
                finishItemMutation();
                if (refreshed) {
                    showInventoryRefreshState(
                            compactName(current.getName()) + " deleted",
                            "The database confirmed deletion. No replacement product is selected.",
                            FarmUi.Tone.SUCCESS);
                }
                notifyInventorySaved("Deleted “" + current.getName() + "”. Deletion was saved.");
            }
        }.execute();
    }

    private void failItemDelete(ItemSelection identity, Item current, Throwable failure) {
        finishItemMutation();
        restoreSelection(identity);
        String detail = FarmUi.actionableError("Deleting this inventory item", failure);
        String recovery = detail + " The product remains selected and no deletion result "
                + "was received; review the reason and retry if appropriate.";
        showInventoryRefreshState(
                compactName(current.getName()) + " not deleted · retry available",
                recovery, FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus("“" + current.getName()
                    + "” was not deleted. It remains selected for review or retry.");
        }
        if (panel.isShowing()) {
            JOptionPane.showMessageDialog(panel, recovery,
                    "Item not deleted", JOptionPane.ERROR_MESSAGE);
        }
        resumeQueuedInventoryRefresh();
    }

    private void finishItemMutation() {
        savingItemMutation = false;
        editBtn.setText("Edit selected");
        deleteBtn.setText("Delete selected");
        setStockCountControlsEnabled(true);
        updateInventoryActions();
    }

    /** Acknowledged writes stay successful even if their separate readback fails. */
    private InventoryReadback readInventoryAfterMutation() {
        try {
            return new InventoryReadback(List.copyOf(itemPresenter.items()), null);
        } catch (RuntimeException failure) {
            return new InventoryReadback(null, failure);
        }
    }

    private boolean applySavedInventory(InventoryReadback inventory, ItemSelection selection,
                                         String outcome) {
        savedInventoryOutcome = outcome;
        inventoryNeedsRefresh = true;
        if (inventory.failure() != null) {
            failInventoryRefresh(inventory.failure());
            return false;
        }
        try {
            replaceInventoryItems(inventory.items(), selection);
            inventoryNeedsRefresh = false;
            inventoryRefreshFailure = null;
            return true;
        } catch (RuntimeException failure) {
            failInventoryRefresh(failure);
            return false;
        }
    }

    private void notifyInventorySaved(String message) {
        if (host != null) {
            try {
                host.refreshAll();
                if (inventoryRefreshFailure == null) {
                    host.setStatus(message);
                }
            } catch (RuntimeException failure) {
                failInventoryRefresh(failure);
                updateInventoryActions();
            }
        }
        resumeQueuedInventoryRefresh();
    }

    private void resumeQueuedInventoryRefresh() {
        if (inventoryRefreshQueued && !refreshingInventory
                && !savingStockCount && !savingItemMutation) {
            startInventoryRefresh();
        }
    }

    private boolean reviewedInventoryStillSelected(InventorySnapshot reviewed) {
        Item selected = selectedItemForAction();
        if (inventoryMutationAvailable() && selected != null
                && reviewed.equals(InventorySnapshot.from(selected))) {
            return true;
        }
        if (host != null) {
            host.setStatus("Inventory or access changed while the dialog was open. "
                    + "No change was submitted. Reselect the product and review current inventory.");
        }
        return false;
    }

    /** Includes writes whose database result has not reached the desktop yet. */
    public boolean hasPendingSave() {
        return savingStockCount || savingItemMutation;
    }

    public String applicationExitWarning() {
        if (hasPendingSave()) {
            return "An inventory count, edit, or deletion is still running. "
                    + "Wait for its result before exiting; saved changes are not undone by exit.";
        }
        if (!retainedDrafts.isEmpty()) {
            return "Inventory has " + retainedDrafts.size() + " unfinished draft(s) retained for review. "
                    + "Entered count and item edit values will be lost if you exit, "
                    + "including drafts for filtered or missing products.";
        }
        return null;
    }

    private RetainedDraft retainedDraft(Item item, boolean count) {
        return retainedDrafts.get(DraftKey.from(InventorySnapshot.from(item), count));
    }

    private void retainIfUnchanged(RetainedDraft previous, RetainedDraft updated) {
        if (retainedDrafts.get(updated.key()) == previous) {
            retainedDrafts.put(updated.key(), updated);
            updateInventoryActions();
        } else if (host != null) {
            host.setStatus("Retained inventory work changed or was discarded while the dialog was open. "
                    + "The current drafts were kept; review retained drafts before trying again.");
        }
    }

    private void removeSubmittedDraft(Item item, boolean count, RetainedDraft submitted) {
        DraftKey key = DraftKey.from(InventorySnapshot.from(item), count);
        if (retainedDrafts.get(key) == submitted) {
            retainedDrafts.remove(key);
        }
    }

    private List<RetainedDraft> draftsFor(Item item) {
        return retainedDrafts.values().stream().filter(draft ->
                draft.key().equals(DraftKey.from(InventorySnapshot.from(item), draft.countDraft()))).toList();
    }

    private static boolean sameDrafts(List<RetainedDraft> left, List<RetainedDraft> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            if (left.get(index) != right.get(index)) {
                return false;
            }
        }
        return true;
    }

    private Item currentProduct(RetainedDraft draft) {
        return currentItems.stream().filter(item ->
                draft.key().equals(DraftKey.from(InventorySnapshot.from(item), draft.countDraft())))
                .findFirst().orElse(null);
    }

    private void updateRetainedDraftControls() {
        if (draftPanel == null) {
            return;
        }
        draftPanel.setVisible(!retainedDrafts.isEmpty());
        reviewDraftsBtn.setEnabled(!retainedDrafts.isEmpty() && !hasPendingSave());
        Item selected = selectedItemForAction();
        String selection = selected == null ? "" : " · " + draftsFor(selected).size()
                + " for the selected product";
        String message = retainedDrafts.size() + " retained inventory draft(s)" + selection;
        FarmUi.updateStatusBadge(draftStatusLabel, message, FarmUi.Tone.WARNING);
        draftStatusLabel.getAccessibleContext().setAccessibleName("Inventory retained drafts");
        draftStatusLabel.getAccessibleContext().setAccessibleDescription(message
                + ". Review entered values or discard one draft, including work for missing products."
                + " Drafts remain on this desktop until saved, discarded, or the application exits.");
        draftPanel.revalidate();
        draftPanel.repaint();
    }

    private JComponent draftEditor(String title, String description, JComponent editor,
                                    RetainedDraft retained, Item current) {
        if (retained == null) {
            return FarmUi.section(title, description, editor);
        }
        JPanel content = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        content.setOpaque(false);
        boolean changed = !retained.product().equals(InventorySnapshot.from(current));
        String message = changed
                ? "Saved product changed since this draft. Current quantity: " + current.getQuantity()
                        + " " + current.getUnit() + ". Review the entered values before saving; "
                        + "Review retained drafts shows all current saved details."
                : "Retained draft restored for this product.";
        if (retained.countDraft()) {
            message += " Count was entered as " + retained.count() + " " + retained.product().unit()
                    + ". Recount if the selling unit changed.";
        }
        JLabel hint = FarmUi.wrappingStatusBadge(message
                + " Cancel keeps this draft and your corrections for later.", FarmUi.Tone.WARNING);
        hint.getAccessibleContext().setAccessibleName("Inventory draft review guidance");
        content.add(hint, BorderLayout.NORTH);
        content.add(editor, BorderLayout.CENTER);
        return FarmUi.section(title, description, content);
    }

    private void reviewRetainedDrafts() {
        if (retainedDrafts.isEmpty() || hasPendingSave()) {
            return;
        }
        List<RetainedDraft> choices = List.copyOf(retainedDrafts.values());
        InventoryDraftReviewView review = new InventoryDraftReviewView(choices.stream()
                .map(draft -> new InventoryDraftReviewView.DraftDetails(draft.label(), draftDetails(draft)))
                .toList());
        Object[] options = {"Open product", "Discard draft", "Close"};
        int action = dialogs.showOption(panel, review, "Retained inventory drafts",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[2]);
        int index = review.selectedIndex();
        if (action < 0 || action > 1 || index < 0 || index >= choices.size()) {
            return;
        }
        RetainedDraft captured = choices.get(index);
        if (!draftDecisionStillCurrent(captured)) {
            return;
        }
        if (action == 1) {
            int confirmation = dialogs.showConfirm(panel, FarmUi.confirmationPanel(
                    "Discard " + captured.label() + "?",
                    "This removes only this local draft and its entered values. Saved inventory is unchanged.",
                    FarmUi.Tone.WARNING), "Discard retained inventory draft?",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirmation == JOptionPane.YES_OPTION && draftDecisionStillCurrent(captured)) {
                retainedDrafts.remove(captured.key());
                updateInventoryActions();
                if (host != null) {
                    host.setStatus("Discarded " + captured.label() + ". Saved inventory is unchanged.");
                }
            }
            return;
        }
        Item product = currentProduct(captured);
        if (product == null) {
            dialogs.showMessage(panel, "This product is missing from the latest loaded inventory. "
                    + "Its draft was kept. Refresh inventory, or review and discard the draft when ready.",
                    "Retained product unavailable", JOptionPane.WARNING_MESSAGE);
            return;
        }
        clearProductQueueHandoff();
        searchField.setText("");
        if (lowStockOnlyBtn.isSelected()) {
            lowStockOnlyBtn.setSelected(false);
            sorter.setSortKeys(sortBeforeLowStock);
        }
        filterTable();
        restoreSelection(ItemSelection.from(product));
        inventoryTable.scrollRectToVisible(inventoryTable.getCellRect(inventoryTable.getSelectedRow(), 0, true));
        if (host != null) {
            host.setStatus("Opened “" + product.getName() + "”. Choose "
                    + (captured.countDraft() ? "Record stock count" : "Edit selected")
                    + " to review its retained values before saving.");
        }
    }

    private boolean draftDecisionStillCurrent(RetainedDraft captured) {
        if (!hasPendingSave() && retainedDrafts.get(captured.key()) == captured) {
            return true;
        }
        if (host != null) {
            host.setStatus("Inventory draft changed or a save started while the dialog was open. "
                    + "The current draft was kept; review it again.");
        }
        return false;
    }

    private String draftDetails(RetainedDraft draft) {
        StringBuilder details = new StringBuilder(draft.label()).append("\n\nEntered values\n");
        if (draft.countDraft()) {
            details.append("Count: ").append(draft.count()).append(' ').append(draft.product().unit());
        } else {
            InventoryItemPresenter.ItemDraft edit = draft.edit();
            details.append("Name: ").append(edit.name()).append("\nCategory: ").append(edit.category())
                    .append("\nRose variety: ").append(edit.roseVariety())
                    .append("\nSelling price: ").append(edit.price()).append("\nUnit: ").append(edit.unit())
                    .append("\nCost: ").append(edit.cost()).append("\nQuantity: ").append(edit.quantity())
                    .append("\nNotes: ").append(edit.notes());
        }
        if (draft.conflictingName() != null) {
            details.append("\n\nName already used by: ").append(draft.conflictingName());
        }
        Item current = currentProduct(draft);
        if (current == null) {
            return details.append("\n\nProduct missing from the latest loaded inventory. "
                    + "The draft is kept. Refresh inventory to check again.").toString();
        }
        details.append("\n\nCurrent saved values (latest loaded inventory)\n")
                .append("Name: ").append(current.getName()).append("\nCategory: ").append(current.getCategory())
                .append("\nSelling price: ").append(current.getPrice()).append("\nUnit: ").append(current.getUnit())
                .append("\nCost: ").append(current.getCost()).append("\nQuantity: ").append(current.getQuantity())
                .append("\nNotes: ").append(current.getNotes());
        if (!draft.product().equals(InventorySnapshot.from(current))) {
            details.append("\n\nSaved product changed since this draft. Review both sets of values before saving.");
        }
        if (inventoryNeedsRefresh || refreshingInventory) {
            details.append("\n\nRefresh inventory successfully before submitting any saved changes.");
        }
        return details.toString();
    }

    private record DraftKey(Long id, String name, String category, String unit, boolean countDraft) {
        private static DraftKey from(InventorySnapshot product, boolean count) {
            return product.id() != null ? new DraftKey(product.id(), null, null, null, count)
                    : new DraftKey(null, product.name(), product.category(), product.unit(), count);
        }
    }

    private record RetainedDraft(InventorySnapshot product, boolean countDraft, Integer count,
                                  InventoryItemPresenter.ItemDraft edit, String conflictingName) {
        private RetainedDraft {
            if (conflictingName != null && edit != null
                    && !conflictingName.equalsIgnoreCase(edit.name().trim())) {
                conflictingName = null;
            }
        }

        private DraftKey key() { return DraftKey.from(product, countDraft); }

        private String label() {
            return (countDraft ? "Stock count" : "Item edit")
                    + (product.id() == null ? "" : " · #" + product.id()) + " · " + product.name();
        }
    }

    private record InventoryReadback(List<Item> items, RuntimeException failure) {
    }

    private record InventorySnapshot(Long id, String name, String category, double price,
                                      String unit, double cost, int quantity, String notes) {
        private static InventorySnapshot from(Item item) {
            return new InventorySnapshot(item.getId(), item.getName(), item.getCategory(),
                    item.getPrice(), item.getUnit(), item.getCost(), item.getQuantity(), item.getNotes());
        }
    }

    private record ItemMutationResult(Item saved, InventoryReadback inventory) {
    }

    private record ItemSelection(Long id, String name, String category, String unit) {
        private static ItemSelection from(Item item) {
            return new ItemSelection(
                    item.getId(), item.getName(), item.getCategory(), item.getUnit());
        }

        private boolean matches(Item item) {
            if (id != null) {
                return id.equals(item.getId());
            }
            return name.equals(item.getName())
                    && category.equals(item.getCategory())
                    && unit.equals(item.getUnit());
        }
    }

    private void showLowStockReorder() {
        JSpinner thresholdInput = new JSpinner(new SpinnerNumberModel(10, 0, 1_000_000, 1));
        thresholdInput.getAccessibleContext().setAccessibleName("Low-stock quantity threshold");
        JLabel thresholdLabel = new JLabel("Show products with this quantity or less:");
        thresholdLabel.setLabelFor(thresholdInput);
        JPanel thresholdForm = FarmUi.toolbar(thresholdLabel, thresholdInput);
        int inputChoice = dialogs.showConfirm(panel,
                FarmUi.section("Low-stock threshold",
                        "Use a whole number. Products at or below it will be included.",
                        thresholdForm),
                "Create low-stock reorder sheet",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (inputChoice == JOptionPane.OK_OPTION) {
            startLowStockReport(((Number) thresholdInput.getValue()).intValue());
        }
    }

    void startLowStockReport(int threshold) {
        if (!beginDocumentWork("Preparing low-stock reorder…",
                "Products and suggested reorder quantities are loading in the background.")) {
            return;
        }
        new SwingWorker<InventoryService.LowStockReport, Void>() {
            @Override
            protected InventoryService.LowStockReport doInBackground() {
                return inventoryService.buildLowStockReport(threshold);
            }

            @Override
            protected void done() {
                try {
                    InventoryService.LowStockReport report = get();
                    completeDocumentWork("Low-stock report ready · "
                                    + report.lowStockCount() + " products",
                            "The captured reorder sheet is ready for review or PDF export.",
                            FarmUi.Tone.SUCCESS,
                            "Low stock: " + report.lowStockCount()
                                    + " product(s) at " + threshold + " or less");
                    if (panel.isShowing()) {
                        presentLowStockReport(report, threshold);
                    }
                } catch (Exception exception) {
                    failDocumentWork("Preparing the low-stock reorder",
                            unwrapDocumentFailure(exception));
                }
            }
        }.execute();
    }

    private void presentLowStockReport(InventoryService.LowStockReport report, int threshold) {
        Object[] options = {"Export PDF…", "Close"};
        int choice = dialogs.showOption(panel,
                new JScrollPane(documentArea(report.plainText())),
                "Low-stock reorder — ≤ " + threshold,
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[1]);
        if (choice == 0) {
            File file = files.chooseSave(panel, "Save low-stock reorder PDF",
                    new File("low-stock-reorder-" + report.date() + ".pdf")).orElse(null);
            if (file == null) {
                showDocumentExportCancelled();
            } else if (approveReplacement(file)
                    && beginDocumentWork("Creating low-stock PDF…",
                    "The reviewed reorder snapshot is being rendered in the background.")) {
                startDocumentFileExport(file,
                        temporary -> Files.write(temporary,
                                inventoryService.generateLowStockPdf(report)),
                        "Low-stock PDF saved · " + report.lowStockCount() + " products",
                        "Low-stock reorder PDF → " + file.getName(),
                        "Saving the low-stock reorder PDF");
            }
        }
    }

    private void showPriceList() {
        int mode = dialogs.showOption(panel,
                "Include products with no stock?", "Price list",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, new Object[]{"In-stock only", "All products", "Cancel"},
                "In-stock only");
        if (mode >= 0 && mode != 2) {
            startPriceListReport(mode == 0);
        }
    }

    void startPriceListReport(boolean inStockOnly) {
        if (!beginDocumentWork("Preparing price list…",
                "Current products and prices are loading in the background.")) {
            return;
        }
        new SwingWorker<InventoryService.PriceListReport, Void>() {
            @Override
            protected InventoryService.PriceListReport doInBackground() {
                return inventoryService.buildPriceListReport(inStockOnly);
            }

            @Override
            protected void done() {
                try {
                    InventoryService.PriceListReport report = get();
                    completeDocumentWork("Price list ready · " + report.skuCount() + " products",
                            "The captured price list is ready for review or PDF export.",
                            FarmUi.Tone.SUCCESS,
                            "Price list: " + report.skuCount() + " product(s), value $"
                                    + String.format("%.0f", report.sellValue()));
                    if (panel.isShowing()) {
                        presentPriceList(report, inStockOnly);
                    }
                } catch (Exception exception) {
                    failDocumentWork("Preparing the price list",
                            unwrapDocumentFailure(exception));
                }
            }
        }.execute();
    }

    private void presentPriceList(InventoryService.PriceListReport report,
                                  boolean inStockOnly) {
        Object[] options = {"Export PDF…", "Close"};
        int choice = dialogs.showOption(panel,
                new JScrollPane(documentArea(report.plainText())),
                "Price list — " + (inStockOnly ? "in-stock" : "all products"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[1]);
        if (choice == 0) {
            File file = files.chooseSave(panel, "Save inventory price-list PDF",
                    new File("price-list-" + report.date() + ".pdf")).orElse(null);
            if (file == null) {
                showDocumentExportCancelled();
            } else if (approveReplacement(file)
                    && beginDocumentWork("Creating price-list PDF…",
                    "The reviewed price-list snapshot is being rendered in the background.")) {
                startDocumentFileExport(file,
                        temporary -> Files.write(temporary,
                                inventoryService.generatePriceListPdf(report)),
                        "Price-list PDF saved · " + report.skuCount() + " products",
                        "Price list PDF → " + file.getName()
                                + " (" + report.skuCount() + " products)",
                        "Saving the price-list PDF");
            }
        }
    }

    /**
     * Exports the currently visible rows (respecting the active filter and sort
     * order) to a CSV file chosen by the user.
     */
    private void exportVisibleToCsv() {
        List<List<String>> rows = captureVisibleRows();
        if (rows.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "There are no visible rows to export.",
                    "Nothing to Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File file = files.chooseSave(panel, "Export Visible Rows to CSV",
                new File("inventory_visible.csv")).orElse(null);
        if (file == null) {
            showDocumentExportCancelled();
            return;
        }
        if (approveReplacement(file)) {
            startVisibleCsvExport(file, rows);
        }
    }

    void startVisibleCsvExport(File file, List<List<String>> rows) {
        List<List<String>> snapshot = rows == null ? List.of()
                : rows.stream().map(List::copyOf).toList();
        if (file == null || !beginDocumentWork("Exporting visible inventory…",
                "The captured filtered and sorted rows are being written in the background.")) {
            return;
        }
        startDocumentFileExport(file,
                temporary -> {
                    try (BufferedWriter writer = Files.newBufferedWriter(temporary,
                            java.nio.charset.StandardCharsets.UTF_8)) {
                        writer.write(String.join(",", COLUMNS));
                        writer.newLine();
                        for (List<String> row : snapshot) {
                            writer.write(row.stream().map(InventoryTab::csvCell)
                                    .reduce((left, right) -> left + "," + right).orElse(""));
                            writer.newLine();
                        }
                    }
                },
                "Inventory CSV saved · " + snapshot.size() + " visible rows",
                snapshot.size() + " row(s) exported to " + file.getName(),
                "Visible inventory CSV export");
    }

    private List<List<String>> captureVisibleRows() {
        java.util.ArrayList<List<String>> rows = new java.util.ArrayList<>();
        for (int viewRow = 0; viewRow < inventoryTable.getRowCount(); viewRow++) {
            java.util.ArrayList<String> row = new java.util.ArrayList<>(COLUMNS.length);
            for (int column = 0; column < COLUMNS.length; column++) {
                Object value = inventoryTable.getValueAt(viewRow, column);
                row.add(value == null ? "" : value.toString());
            }
            rows.add(List.copyOf(row));
        }
        return List.copyOf(rows);
    }

    private boolean beginDocumentWork(String message, String description) {
        if (runningDocument) {
            return false;
        }
        runningDocument = true;
        updateInventoryActions();
        showDocumentState(message, description, FarmUi.Tone.INFO);
        if (host != null) {
            host.setStatus(message);
        }
        return true;
    }

    private void startDocumentFileExport(File file, DocumentWriter writer,
                                         String successMessage, String hostMessage,
                                         String errorAction) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                writeDocumentAtomically(file.toPath(), writer);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    completeDocumentWork(successMessage,
                            "Saved to " + file.getAbsolutePath(), FarmUi.Tone.SUCCESS,
                            hostMessage);
                    if (panel.isShowing()) {
                        JOptionPane.showMessageDialog(panel,
                                "Saved to:\n" + file.getAbsolutePath(),
                                "Export complete", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception exception) {
                    failDocumentWork(errorAction, unwrapDocumentFailure(exception));
                }
            }
        }.execute();
    }

    private static void writeDocumentAtomically(Path destination, DocumentWriter writer)
            throws Exception {
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("The selected export folder is not available.");
        }
        Path temporary = Files.createTempFile(parent, ".inventory-export-", ".tmp");
        try {
            writer.write(temporary);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void completeDocumentWork(String message, String description,
                                      FarmUi.Tone tone, String hostMessage) {
        runningDocument = false;
        updateInventoryActions();
        showDocumentState(message, description, tone);
        if (host != null) {
            host.setStatus(hostMessage);
        }
    }

    private void failDocumentWork(String action, Throwable failure) {
        runningDocument = false;
        updateInventoryActions();
        String detail = FarmUi.actionableError(action, failure);
        showDocumentState("Report or export failed · retry",
                detail + " Inventory rows, search, sorting, selection, and count work were kept.",
                FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus(action + " failed. Current inventory work was kept; retry when ready.");
        }
        if (panel.isShowing()) {
            JOptionPane.showMessageDialog(panel, detail,
                    "Inventory document failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showDocumentState(String message, String description, FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(documentStatusLabel, message, tone);
        documentStatusLabel.setToolTipText(description);
        documentStatusLabel.getAccessibleContext().setAccessibleDescription(description);
    }

    private boolean approveReplacement(File file) {
        if (file == null || !file.exists()) {
            return file != null;
        }
        int choice = dialogs.showConfirm(panel,
                FarmUi.confirmationPanel(
                        "Replace “" + file.getName() + "”?",
                        "The existing file will be overwritten only if you continue.",
                        FarmUi.Tone.WARNING),
                "Replace existing file", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        boolean approved = choice == JOptionPane.YES_OPTION;
        if (!approved) {
            showDocumentState("Inventory export not saved · existing file kept",
                    "The existing file was kept unchanged: " + file.getAbsolutePath(),
                    FarmUi.Tone.WARNING);
            if (host != null) {
                host.setStatus("Inventory export not saved. Existing file kept: "
                        + file.getName());
            }
        }
        return approved;
    }

    private void showDocumentExportCancelled() {
        showDocumentState("Inventory export cancelled · no file changed",
                "Choose the export again when you are ready to select a destination.",
                FarmUi.Tone.WARNING);
        if (host != null) {
            host.setStatus("Inventory export cancelled. No file was changed.");
        }
    }

    private static Throwable unwrapDocumentFailure(Exception exception) {
        return exception instanceof java.util.concurrent.ExecutionException
                && exception.getCause() != null ? exception.getCause() : exception;
    }

    private static JTextArea documentArea(String text) {
        JTextArea area = new JTextArea(text, 22, 64);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        return area;
    }

    private static String csvCell(String value) {
        String cell = value == null ? "" : value;
        return cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0
                || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0
                ? "\"" + cell.replace("\"", "\"\"") + "\"" : cell;
    }

    @FunctionalInterface
    private interface DocumentWriter {
        void write(Path destination) throws Exception;
    }
}
