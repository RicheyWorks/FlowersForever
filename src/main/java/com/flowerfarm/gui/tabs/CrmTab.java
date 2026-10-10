package com.flowerfarm.gui.tabs;

import com.flowerfarm.util.InventoryQuantity;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmFileChooser;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.gui.presenter.CrmPresenter;
import com.flowerfarm.gui.view.CrmCustomerSearchView;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.util.InventoryUnits;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * CRM — customers + wholesale/market orders: search, filter, confirm, fulfill, export.
 */
public class CrmTab implements FlowerFarmTab {

    private final CrmPresenter presenter;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final FarmFileChooser files;
    private String defaultUnit = "stems";

    private JPanel panel;
    private DefaultTableModel customerModel;
    private DefaultTableModel orderModel;
    private JTable customerTable;
    private JTable orderTable;

    private JTextField custSearch;
    private JTextField custName;
    private JTextField custContact;
    private JTextField custEmail;
    private JTextField custPhone;
    private JComboBox<String> custType;
    private JTextField custNotes;
    private Long editingCustomerId;
    private CustomerFormState cleanCustomerForm;
    private boolean failedCustomerDraft;
    private long customerFormRevision;

    private JComboBox<CrmPresenter.CustomerChoice> orderCustomer;
    private JTextArea newOrderCustomerContext;
    private JButton openNewOrderCustomerBtn;
    private JTextField orderDate;
    private JTextField orderProduct;
    private JTextField orderQty;
    private JTextField orderUnit;
    private JTextField orderPrice;
    private JComboBox<String> orderStatus;
    private JComboBox<String> orderHandoffType;
    private JTextField orderNotes;
    private Long newOrderCustomerBaselineId;
    private boolean newOrderCustomerBaselineInitialized;
    private boolean replacingNewOrderCustomers;
    private String newOrderDateBaseline = "";
    private String newOrderHandoffBaseline = "Pickup";
    private JComboBox<CrmPresenter.InventoryChoice> orderInventoryProduct;
    private DefaultListModel<CrmPresenter.InventoryChoice> inventoryDragModel;
    private JList<CrmPresenter.InventoryChoice> inventoryDragList;
    private JButton useDraggedInventoryBtn;
    private JLabel orderLinePreviewLabel;
    private JLabel selectedOrderLabel;
    private JTextArea selectedOrderCustomerContext;
    private JButton reviewOrderCustomerBtn;
    private JLabel selectedOrderStockLabel;
    private JTextField selectedOrderDate;
    private JButton saveOrderDateBtn;
    private Long selectedOrderDateOrderId;
    private String selectedOrderDateBaseline = "";
    private Long pendingOrderDateRefreshId;
    private String pendingOrderDateRefreshValue;
    private boolean updatingSelectedOrderDate;
    private final Map<Long, String> selectedOrderDateDrafts = new LinkedHashMap<>();
    private final Map<Long, String> selectedOrderDateDraftBaselines = new LinkedHashMap<>();
    private final Map<Long, String> selectedOrderDateConflicts = new LinkedHashMap<>();
    private JComboBox<String> selectedOrderHandoffType;
    private JButton saveOrderHandoffBtn;
    private Long selectedOrderHandoffOrderId;
    private String selectedOrderHandoffBaseline = "Unspecified";
    private Long pendingOrderHandoffRefreshId;
    private String pendingOrderHandoffRefreshValue;
    private boolean updatingSelectedOrderHandoff;
    private final Map<Long, String> selectedOrderHandoffDrafts = new LinkedHashMap<>();
    private final Map<Long, String> selectedOrderHandoffDraftBaselines = new LinkedHashMap<>();
    private final Map<Long, String> selectedOrderHandoffConflicts = new LinkedHashMap<>();
    private JTextField selectedOrderNotes;
    private JButton saveOrderNotesBtn;
    private Long selectedOrderNotesOrderId;
    private String selectedOrderNotesBaseline = "";
    private Long pendingOrderNotesRefreshId;
    private String pendingOrderNotesRefreshValue;
    private boolean updatingSelectedOrderNotes;
    private final Map<Long, String> selectedOrderNotesDrafts = new LinkedHashMap<>();
    private final Map<Long, String> selectedOrderNotesDraftBaselines = new LinkedHashMap<>();
    private final Map<Long, String> selectedOrderNotesConflicts = new LinkedHashMap<>();
    private JLabel fulfillmentStatusLabel;
    private JButton resolveOrderStockBtn;
    private JComboBox<OrderLineChoice> selectedOrderLine;
    private JButton editOrderLineBtn;
    private JButton cancelOrderLineEditBtn;
    private JButton removeOrderLineBtn;
    private boolean selectedOrderQuantityIssue;
    private boolean selectedOrderUnitIssue;
    private Long editingOrderId;
    private Long editingOrderLineId;
    private OrderService.OrderReviewLine editingOrderLineReview;
    private OrderService.OrderReviewSnapshot editingOrderReview;
    private Long activeProductEntryOrderId;
    private ProductEntryDraft newOrderProductDraft;
    private ProductEntryDraft pausedNewOrderProductDraft;
    private Long unavailableProductDraftOrderId;
    private final Map<Long, ProductEntryDraft> orderProductDrafts = new LinkedHashMap<>();
    private List<String> selectedOrderStockProducts = List.of();
    private boolean updatingOrderLineChoices;
    private boolean orderLineValid;
    private DuplicateLineApproval duplicateLineApproval;
    private ProductEntryClearApproval productEntryClearApproval;
    private NewOrderIdentityClearApproval newOrderIdentityClearApproval;
    private EarlyCompletionApproval earlyCompletionApproval;
    private OverdueCompletionApproval overdueCompletionApproval;
    private boolean refreshingInventoryChoices;
    private boolean newOrderIdentityValid = true;
    private boolean fulfillingOrder;
    private boolean savingCustomer;
    private boolean deletingCustomer;
    private long customerWriteRevision;
    private boolean creatingOrder;
    private boolean addingOrderLine;
    private boolean runningOrderMutation;

    private JComboBox<String> filterStatus;
    private JComboBox<String> filterHandoff;
    private JTextField filterCustomer;
    private JTextField filterFrom;
    private JTextField filterTo;
    private JLabel orderStatusLabel;
    private JLabel accessLabel;
    private JLabel customerModeLabel;
    private JPanel customerHandoffBanner;
    private JLabel customerHandoffLabel;
    private JButton returnFromCustomerBtn;
    private JLabel crmRefreshLabel;
    private JLabel documentStatusLabel;
    private List<CustomerOrder> lastOrderView = List.of();
    private List<CustomerOrder> confirmedCompletionQueueSnapshot = List.of();
    private List<CrmPresenter.InventoryChoice> inventoryChoiceSnapshot = List.of();
    private Map<Long, OrderStockAssessment> orderReadinessAssessments = Map.of();
    private Set<Long> reservationLimitedOrderIds = Set.of();
    private boolean canWrite = true;
    private boolean crmHasCustomers;

    private JButton addCustomerBtn;
    private JButton loadCustomerBtn;
    private JButton saveCustomerBtn;
    private JButton clearCustomerBtn;
    private JButton deleteCustomerBtn;
    private JButton statementCustomerBtn;
    private JButton reviewCustomerConflictBtn;
    private JButton createOrderBtn;
    private JButton addOrderLineBtn;
    private JButton confirmOrderBtn;
    private JButton fulfillOrderBtn;
    private JButton startAnotherOrderBtn;
    private JButton resetNewOrderDetailsBtn;
    private JButton applyOrderFilterBtn;
    private JButton clearOrderFilterBtn;
    private JButton readyToFulfillBtn;
    private JButton needsHandoffReviewBtn;
    private JButton focusNewOrderBtn;
    private JComboBox<String> orderTools;
    private JButton runOrderToolBtn;
    private JButton refreshCrmBtn;
    private JPanel orderHandoffBanner;
    private JLabel orderHandoffLabel;
    private JButton returnFromOrderBtn;
    private CardLayout customerCardLayout;
    private JPanel customerState;
    private FarmUi.EmptyStateView customerEmptyState;
    private CardLayout orderCardLayout;
    private JPanel orderState;
    private JPanel newOrderSetupPanel;
    private JPanel selectedOrderReviewPanel;
    private JLabel orderEmptyTitle;
    private JLabel orderEmptyGuidance;
    private JButton orderEmptyShowAllBtn;
    private JTabbedPane crmWorkspace;
    private boolean refreshingCrm;
    private boolean focusOrderProductAfterRefresh;
    private boolean crmRefreshQueued;
    private boolean runningDocument;
    private String crmRefreshFailure;
    private String crmRefreshFailureSource;
    private Long pendingOrderSelectionId;
    private Long pendingCreatedOrderSelectionId;
    private String pendingCreatedOrderDescription;
    private Long pendingAddedProductOrderId;
    private String pendingAddedProductName;
    private Long pendingExistingOrderProductId;
    private String pendingExistingOrderProductName;
    private ProductWriteSuccessPresentation pendingProductWriteSuccess;
    private Long handoffOrderSelectionId;
    private Long pendingCustomerSelectionId;
    private OpenCustomerRequest pendingOpenCustomerRequest;
    private OpenCustomerPresentation pendingOpenCustomerPresentation;
    private OpenCustomerRequest customerHandoffReturn;
    private ProductConflictPresentation pendingProductConflict;
    private Long pendingProductEditReviewRebaseId;
    private String pendingOrderCreationConflict;
    private FulfillmentFailurePresentation pendingFulfillmentFailure;
    private FulfillmentSuccessPresentation pendingFulfillmentSuccess;
    private ConfirmationFailurePresentation pendingConfirmationFailure;
    private ConfirmationSuccessPresentation pendingConfirmationSuccess;
    private DestructiveSuccessPresentation pendingDestructiveSuccess;
    private CorrectionSuccessPresentation pendingCorrectionSuccess;
    private CustomerWriteSuccessPresentation pendingCustomerWriteSuccess;
    private DestructiveFailurePresentation pendingDestructiveFailure;
    private Long pendingOrderLineSelectionId;
    private String pendingOrderLineProduct;
    private boolean pendingOrderLineCorrectionEdit;
    private Long conflictingCustomerId;
    private String conflictingCustomerName;
    private Long handoffReturnOrderId;
    private String handoffReturnTab;
    private String handoffReturnLabel;
    private Long handoffOrderLineOrderId;
    private Long handoffOrderLineId;
    private String handoffOrderLineProduct;

    public CrmTab(CustomerService customerService, OrderService orderService, TabHost host) {
        this(customerService, orderService, null, null, host,
                FarmDialogs.swing(), FarmFileChooser.swing());
    }

    public CrmTab(CustomerService customerService, OrderService orderService,
                  TabHost host, FarmDialogs dialogs) {
        this(customerService, orderService, null, null, host,
                dialogs, FarmFileChooser.swing());
    }

    public CrmTab(CustomerService customerService, OrderService orderService,
                  TabHost host, FarmDialogs dialogs, FarmFileChooser files) {
        this(customerService, orderService, null, null, host, dialogs, files);
    }

    public CrmTab(CustomerService customerService, OrderService orderService,
                  CustomerStatementService customerStatementService, TabHost host) {
        this(customerService, orderService, customerStatementService, null,
                host, FarmDialogs.swing(), FarmFileChooser.swing());
    }

    public CrmTab(CustomerService customerService, OrderService orderService,
                  CustomerStatementService customerStatementService,
                  InventoryService inventoryService, TabHost host) {
        this(customerService, orderService, customerStatementService, inventoryService,
                host, FarmDialogs.swing(), FarmFileChooser.swing());
    }

    public CrmTab(CustomerService customerService, OrderService orderService,
                  CustomerStatementService customerStatementService,
                  InventoryService inventoryService, TabHost host, FarmDialogs dialogs) {
        this(customerService, orderService, customerStatementService, inventoryService,
                host, dialogs, FarmFileChooser.swing());
    }

    public CrmTab(CustomerService customerService, OrderService orderService,
                  CustomerStatementService customerStatementService,
                  InventoryService inventoryService, TabHost host, FarmDialogs dialogs,
                  FarmFileChooser files) {
        this.presenter = new CrmPresenter(
                customerService, orderService, customerStatementService, inventoryService);
        this.host = host;
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        this.files = Objects.requireNonNull(files, "files");
    }

    /** Configure the initial order-product unit before the tab UI is built. */
    public void setDefaultUnit(String unit) {
        if (panel != null) {
            throw new IllegalStateException("Set the default order unit before building the tab.");
        }
        String clean = Objects.requireNonNull(unit, "unit").trim();
        if (clean.isEmpty() || clean.length() > 64) {
            throw new IllegalArgumentException("Default order unit must have 1 to 64 characters.");
        }
        defaultUnit = clean;
    }

    @Override public String getTabTitle() { return "Customers & Orders"; }

    @Override
    public String getDescription() {
        return "Customers and wholesale or market orders — filter, confirm, complete, and export";
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
        if (customerModel == null) {
            return;
        }
        startCrmRefresh();
    }

    void startCrmRefresh() {
        if (refreshingCrm) {
            crmRefreshQueued = true;
            showCrmRefreshState("Refresh queued · current CRM load finishing",
                    "One newer customer, order, and inventory snapshot will load next.",
                    FarmUi.Tone.INFO);
            return;
        }
        CrmRefreshRequest request = currentCrmRefreshRequest();
        refreshingCrm = true;
        crmRefreshFailure = null;
        crmRefreshFailureSource = null;
        refreshCrmBtn.setEnabled(false);
        updateCustomerFormMode();
        updateOrderActions();
        showCrmRefreshState("Refreshing customers and orders…",
                "Loading customer accounts, filtered orders, and inventory choices in the background.",
                FarmUi.Tone.INFO);
        if (host != null) {
            host.setStatus("Refreshing customers and orders…");
        }

        new SwingWorker<CrmRefreshSnapshot, Void>() {
            @Override
            protected CrmRefreshSnapshot doInBackground() {
                CrmPresenter.OrderView baseView = loadCrmSource("Orders", () ->
                        presenter.filterOrders(
                                request.orderStatus(), request.orderCustomer(),
                                request.from(), request.to(), request.orderHandoff()));
                List<CrmPresenter.InventoryChoice> inventoryChoices =
                        List.copyOf(loadCrmSource(
                                "Inventory", presenter::inventoryChoices));
                List<CustomerOrder> confirmedQueue =
                        List.copyOf(loadCrmSource(
                                "Confirmed orders", presenter::confirmedCompletionQueue));
                List<CrmPresenter.CustomerRow> customers = List.copyOf(loadCrmSource(
                        "Customers", () -> presenter.searchCustomers(request.customerQuery())));
                List<CrmPresenter.CustomerChoice> customerChoices = List.copyOf(loadCrmSource(
                        "Customer choices", presenter::customerChoices));
                return new CrmRefreshSnapshot(
                        customers,
                        filterOrderReadiness(baseView, inventoryChoices,
                                request.orderReadiness(), confirmedQueue),
                        customerChoices,
                        inventoryChoices, confirmedQueue);
            }

            @Override
            protected void done() {
                try {
                    CrmRefreshSnapshot snapshot = get();
                    if (!crmRefreshQueued) {
                        applyCrmRefreshSnapshot(snapshot);
                        showCrmRefreshState("Customers and orders current",
                                snapshot.customerRows().size() + " customer account(s) · "
                                        + snapshot.orderReadiness().view().rows().size()
                                        + " order(s) in the current view.",
                                FarmUi.Tone.SUCCESS);
                    } else if (host != null) {
                        host.setStatus("A newer CRM refresh is queued; discarding the older snapshot.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    if (!crmRefreshQueued) {
                        failCrmRefresh(exception);
                    }
                } catch (java.util.concurrent.ExecutionException exception) {
                    if (!crmRefreshQueued) {
                        failCrmRefresh(exception.getCause() == null
                                ? exception : exception.getCause());
                    }
                } catch (RuntimeException exception) {
                    if (!crmRefreshQueued) {
                        failCrmRefresh(exception);
                    }
                } finally {
                    finishCrmRefresh();
                }
            }
        }.execute();
    }

    private static <T> T loadCrmSource(String source, Supplier<T> loader) {
        try {
            return Objects.requireNonNull(
                    loader.get(), source + " returned no refresh response");
        } catch (CrmPresenter.InputValidationException validation) {
            throw validation;
        } catch (RuntimeException failure) {
            throw new CrmRefreshSourceException(source, failure);
        }
    }

    private CrmRefreshRequest currentCrmRefreshRequest() {
        String readiness = selectedReadinessFilter();
        String status = "ALL".equals(readiness) ? selectedStatusFilter() : "CONFIRMED";
        return new CrmRefreshRequest(
                custSearch == null ? "" : custSearch.getText(),
                status,
                filterCustomer == null ? "" : filterCustomer.getText().trim(),
                selectedHandoffFilter(),
                readiness,
                filterFrom == null ? "" : filterFrom.getText(),
                filterTo == null ? "" : filterTo.getText());
    }

    private void applyCrmRefreshSnapshot(CrmRefreshSnapshot snapshot) {
        Long customerId = selectedId(customerTable, customerModel);
        Long orderId = pendingOrderSelectionId != null
                ? pendingOrderSelectionId : selectedId(orderTable, orderModel);
        CrmPresenter.CustomerChoice selectedCustomer =
                (CrmPresenter.CustomerChoice) orderCustomer.getSelectedItem();
        String product = orderProduct == null ? "" : orderProduct.getText().trim();

        // Capture the exact order context beside an unfinished product before replacing
        // lastOrderView. Otherwise a refresh could silently make the draft appear to have
        // started against the newer customer, date, handoff, or saved-product set.
        rememberCurrentProductDraft();

        crmHasCustomers = !snapshot.customerChoices().isEmpty();
        confirmedCompletionQueueSnapshot = snapshot.confirmedCompletionQueue().isEmpty()
                ? snapshot.orderReadiness().view().sourceOrders().stream()
                        .filter(order -> "CONFIRMED".equalsIgnoreCase(order.getStatus())).toList()
                : snapshot.confirmedCompletionQueue();
        replaceCustomerRows(snapshot.customerRows());
        replaceOrderView(snapshot.orderReadiness());
        replaceCustomerChoices(snapshot.customerChoices(), selectedCustomer);
        replaceInventoryChoices(snapshot.inventoryChoices(), product);
        Long customerToSelect = pendingCustomerSelectionId != null
                ? pendingCustomerSelectionId : customerId;
        boolean customerRestored = restoreSelectedId(customerTable, customerModel, customerToSelect);
        if (customerRestored && pendingCustomerSelectionId != null
                && pendingCustomerSelectionId.equals(customerToSelect)) {
            pendingCustomerSelectionId = null;
        }
        if (pendingOpenCustomerRequest != null) {
            OpenCustomerRequest request = pendingOpenCustomerRequest;
            pendingOpenCustomerRequest = null;
            pendingCustomerSelectionId = null;
            pendingOpenCustomerPresentation = new OpenCustomerPresentation(
                    request, customerRestored
                            && Objects.equals(customerToSelect, request.customerId()));
        }
        boolean orderRestored = restoreSelectedId(orderTable, orderModel, orderId);
        boolean createdOrderPending = pendingCreatedOrderSelectionId != null
                && pendingCreatedOrderSelectionId.equals(orderId);
        boolean addedProductPending = pendingAddedProductOrderId != null
                && pendingAddedProductOrderId.equals(orderId);
        boolean existingOrderProductPending = pendingExistingOrderProductId != null
                && pendingExistingOrderProductId.equals(orderId);
        if (orderRestored && pendingOrderSelectionId != null
                && pendingOrderSelectionId.equals(orderId)) {
            pendingOrderSelectionId = null;
        }
        if (unavailableProductDraftOrderId != null
                && unavailableProductDraftOrderId.equals(orderId)) {
            if (orderRestored) {
                unavailableProductDraftOrderId = null;
            } else {
                ProductEntryDraft unavailableDraft =
                        orderProductDrafts.get(unavailableProductDraftOrderId);
                if (unavailableDraft != null) {
                    activeProductEntryOrderId = unavailableProductDraftOrderId;
                    applyProductEntryDraft(unavailableDraft);
                }
                pendingOrderSelectionId = null;
            }
        }
        updateOrderLinePreview();
        updateOrderActions();
        updateCustomerFormMode();
        if (createdOrderPending) {
            if (orderRestored) {
                String description = pendingCreatedOrderDescription == null
                        ? "Order #" + orderId : pendingCreatedOrderDescription;
                pendingCreatedOrderSelectionId = null;
                pendingCreatedOrderDescription = null;
                applyLifecycleRefreshGuidance("", true);
                status(description
                        + " is saved; CRM selected it. Enter the next product or confirm the order.");
            } else {
                status("Order #" + orderId + " was saved but is not visible yet. "
                        + "Refresh CRM to retry the exact-order selection; do not create it again.");
            }
        }
        if (addedProductPending) {
            if (orderRestored) {
                String productName = pendingAddedProductName == null
                        ? "Product" : pendingAddedProductName;
                pendingAddedProductOrderId = null;
                pendingAddedProductName = null;
                applyLifecycleRefreshGuidance("", true);
                status("Added “" + productName + "” to order #" + orderId
                        + ". Exact order selected; the next product entry is ready."
                        + " Use Ctrl+Enter to add it after review.");
            } else {
                String productName = pendingAddedProductName == null
                        ? "The product" : "“" + pendingAddedProductName + "”";
                String recovery = productName + " was saved once to order #" + orderId
                        + ", but that order is not visible in the current filtered view. The "
                        + "product form was cleared to prevent a duplicate. Choose Show all or "
                        + "adjust filters, then Refresh CRM; do not enter the product again.";
                FarmUi.updateStatusBadge(selectedOrderLabel,
                        "Product saved · order #" + orderId + " outside current view",
                        FarmUi.Tone.WARNING);
                selectedOrderLabel.setToolTipText(recovery);
                selectedOrderLabel.getAccessibleContext().setAccessibleDescription(recovery);
                status(recovery);
                updateOrderActions();
            }
        }
        if (handoffOrderSelectionId != null
                && handoffOrderSelectionId.equals(orderId)) {
            reportOrderHandoff(handoffOrderSelectionId, orderRestored);
        }
        if (orderRestored && (createdOrderPending || addedProductPending)) {
            focusOrderProductAfterRefresh = true;
        }
    }

    private void failCrmRefresh(Throwable failure) {
        if (failure instanceof CrmPresenter.InputValidationException validation
                && ("filterFrom".equals(validation.field())
                || "filterTo".equals(validation.field()))) {
            showOrderFilterValidation(validation);
            showCrmRefreshState("CRM refresh not started · check order filter",
                    "Existing customer and order rows were kept. Correct the highlighted date, "
                            + "then refresh again.", FarmUi.Tone.WARNING);
            return;
        }
        Throwable detailFailure = failure;
        if (failure instanceof CrmRefreshSourceException sourceFailure) {
            crmRefreshFailureSource = sourceFailure.source();
            if (sourceFailure.getCause() != null) {
                detailFailure = sourceFailure.getCause();
            }
        }
        String detail = FarmUi.actionableError("Refreshing customers and orders", detailFailure);
        String sourceDetail = crmRefreshFailureSource == null
                ? "The CRM snapshot could not be completed."
                : crmRefreshFailureSource + " could not be loaded.";
        crmRefreshFailure = sourceDetail
                + " Any partial refresh response was discarded so customers, orders, and "
                + "inventory cannot be mixed across snapshots. " + detail
                + " Existing rows, filters, selection, and order-entry details were kept; "
                + "retry when ready.";
        showCrmRefreshState(crmRefreshFailureStatus(),
                crmRefreshFailure, FarmUi.Tone.DANGER);
        if (pendingOpenCustomerRequest != null) {
            String recovery = "Could not reload customer “"
                    + pendingOpenCustomerRequest.customerName() + "” from "
                    + pendingOpenCustomerRequest.source() + ". The exact account target, order, "
                    + "and unfinished entry work were kept. Choose Refresh CRM to retry.";
            FarmUi.updateStatusBadge(customerModeLabel,
                    "Linked customer not loaded · retry refresh", FarmUi.Tone.DANGER);
            customerModeLabel.setToolTipText(recovery);
            customerModeLabel.getAccessibleContext().setAccessibleDescription(recovery);
            SwingUtilities.invokeLater(() -> FarmUi.revealAndFocus(refreshCrmBtn));
        }
        if (host != null) {
            host.setStatus(pendingCustomerWriteSuccess != null
                    ? "Customer “" + pendingCustomerWriteSuccess.name() + "” was "
                            + (pendingCustomerWriteSuccess.action() == CustomerWriteAction.DELETE
                            ? "deleted" : "saved")
                            + " once, but CRM could not reload authoritative customer data. "
                            + "Retry Refresh CRM only; do not repeat the write."
                    : pendingOpenCustomerRequest != null
                    ? "Could not reload customer “" + pendingOpenCustomerRequest.customerName()
                            + "” from " + pendingOpenCustomerRequest.source()
                            + ". The order and unfinished entry work were kept. Retry Refresh CRM."
                    : pendingAddedProductOrderId != null
                    ? (pendingAddedProductName == null ? "The product" : "“"
                            + pendingAddedProductName + "”")
                            + " was saved once to order #" + pendingAddedProductOrderId
                            + ", but CRM could not reload the order. The product form was cleared "
                            + "to prevent a duplicate. Retry Refresh CRM; do not add it again."
                    : pendingCreatedOrderSelectionId != null
                    ? "Order #" + pendingCreatedOrderSelectionId
                            + " was saved, but CRM could not reload it for selection. "
                            + "Retry Refresh CRM; do not create the order again."
                    : pendingProductWriteSuccess != null
                    ? productWriteRecovery(pendingProductWriteSuccess,
                            "CRM could not reload the exact order. Retry Refresh CRM only")
                    : pendingDestructiveSuccess != null
                    ? destructiveSuccessRecovery(pendingDestructiveSuccess,
                            "CRM could not reload authoritative order data. Retry Refresh CRM only")
                    : pendingCorrectionSuccess != null
                    ? correctionSuccessRecovery(pendingCorrectionSuccess,
                            "CRM could not reload the exact order. Retry Refresh CRM only")
                    : (crmRefreshFailureSource == null
                    ? "Customer and order refresh failed."
                    : "CRM refresh failed because " + crmRefreshFailureSource
                            + " could not be loaded.")
                            + " Existing work was kept; review the status and retry.");
        }
    }

    private String crmRefreshFailureStatus() {
        return crmRefreshFailureSource == null
                ? "CRM refresh failed · previous snapshot shown · retry"
                : "CRM refresh failed · " + crmRefreshFailureSource
                        + " unavailable · previous snapshot shown · retry";
    }

    private void finishCrmRefresh() {
        refreshingCrm = false;
        refreshCrmBtn.setEnabled(true);
        updateInventoryDragButton();
        updateCustomerFormMode();
        updateOrderActions();
        if (pendingExistingOrderProductId != null && !crmRefreshQueued) {
            boolean refreshSucceeded = crmRefreshFailure == null;
            boolean targetRestored = refreshSucceeded
                    && Objects.equals(selectedOrderId(), pendingExistingOrderProductId);
            applyExistingOrderProductPresentation(refreshSucceeded, targetRestored);
            if (refreshSucceeded) {
                pendingExistingOrderProductId = null;
                pendingExistingOrderProductName = null;
            }
        }
        if (pendingProductConflict != null && !crmRefreshQueued) {
            rebasePendingProductEditReview();
            applyProductConflictPresentation(pendingProductConflict);
            pendingProductConflict = null;
        }
        if (pendingOrderCreationConflict != null && !crmRefreshQueued) {
            applyOrderCreationConflict(pendingOrderCreationConflict);
            pendingOrderCreationConflict = null;
        }
        if (pendingProductWriteSuccess != null && !crmRefreshQueued) {
            ProductWriteSuccessPresentation presentation = pendingProductWriteSuccess;
            boolean refreshSucceeded = crmRefreshFailure == null;
            boolean targetRestored = refreshSucceeded
                    && Objects.equals(selectedOrderId(), presentation.orderId());
            if (refreshSucceeded) {
                if (!targetRestored) {
                    pendingOrderSelectionId = null;
                }
                pendingProductWriteSuccess = null;
                updateOrderActions();
            }
            applyProductWriteSuccessPresentation(
                    presentation, refreshSucceeded, targetRestored);
        }
        if (pendingDestructiveSuccess != null && !crmRefreshQueued) {
            DestructiveSuccessPresentation presentation = pendingDestructiveSuccess;
            boolean refreshSucceeded = crmRefreshFailure == null;
            boolean targetRestored = refreshSucceeded
                    && Objects.equals(selectedOrderId(), presentation.orderId());
            if (refreshSucceeded) {
                if (!targetRestored) {
                    pendingOrderSelectionId = null;
                }
                pendingDestructiveSuccess = null;
                updateOrderActions();
            }
            applyDestructiveSuccessPresentation(
                    presentation, refreshSucceeded, targetRestored);
        }
        if (pendingCorrectionSuccess != null && !crmRefreshQueued) {
            CorrectionSuccessPresentation presentation = pendingCorrectionSuccess;
            boolean refreshSucceeded = crmRefreshFailure == null;
            boolean targetRestored = refreshSucceeded
                    && Objects.equals(selectedOrderId(), presentation.orderId());
            if (refreshSucceeded) {
                if (!targetRestored) {
                    pendingOrderSelectionId = null;
                }
                pendingCorrectionSuccess = null;
                updateOrderActions();
            }
            applyCorrectionSuccessPresentation(
                    presentation, refreshSucceeded, targetRestored);
        }
        if (pendingFulfillmentSuccess != null && !crmRefreshQueued) {
            boolean refreshSucceeded = crmRefreshFailure == null;
            boolean targetRestored = refreshSucceeded
                    && Objects.equals(selectedOrderId(), pendingFulfillmentSuccess.orderId());
            applyFulfillmentSuccessPresentation(
                    pendingFulfillmentSuccess, refreshSucceeded, targetRestored);
            if (refreshSucceeded) {
                if (!targetRestored) {
                    pendingOrderSelectionId = null;
                }
                pendingFulfillmentSuccess = null;
            }
        }
        if (pendingFulfillmentFailure != null && !crmRefreshQueued) {
            boolean refreshSucceeded = crmRefreshFailure == null;
            boolean targetRestored = refreshSucceeded
                    && Objects.equals(selectedOrderId(), pendingFulfillmentFailure.orderId());
            applyFulfillmentFailurePresentation(
                    pendingFulfillmentFailure, refreshSucceeded, targetRestored);
            if (crmRefreshFailure == null) {
                if (!targetRestored) {
                    pendingOrderSelectionId = null;
                }
                pendingFulfillmentFailure = null;
                updateOrderActions();
            }
        }
        if (pendingConfirmationSuccess != null && !crmRefreshQueued) {
            boolean refreshSucceeded = crmRefreshFailure == null;
            boolean targetRestored = refreshSucceeded
                    && Objects.equals(selectedOrderId(), pendingConfirmationSuccess.orderId());
            applyConfirmationSuccessPresentation(
                    pendingConfirmationSuccess, refreshSucceeded, targetRestored);
            if (refreshSucceeded) {
                if (!targetRestored) {
                    pendingOrderSelectionId = null;
                }
                pendingConfirmationSuccess = null;
            }
        }
        if (pendingConfirmationFailure != null && !crmRefreshQueued) {
            boolean refreshSucceeded = crmRefreshFailure == null;
            boolean targetRestored = refreshSucceeded
                    && Objects.equals(selectedOrderId(), pendingConfirmationFailure.orderId());
            applyConfirmationFailurePresentation(
                    pendingConfirmationFailure, refreshSucceeded, targetRestored);
            if (refreshSucceeded) {
                if (!targetRestored) {
                    pendingOrderSelectionId = null;
                }
                pendingConfirmationFailure = null;
                updateOrderActions();
            }
        }
        if (pendingDestructiveFailure != null && !crmRefreshQueued) {
            boolean refreshSucceeded = crmRefreshFailure == null;
            boolean targetRestored = refreshSucceeded
                    && Objects.equals(selectedOrderId(), pendingDestructiveFailure.orderId());
            applyDestructiveFailurePresentation(
                    pendingDestructiveFailure, refreshSucceeded, targetRestored);
            if (refreshSucceeded) {
                if (!targetRestored) {
                    pendingOrderSelectionId = null;
                }
                pendingDestructiveFailure = null;
                updateOrderActions();
            }
        }
        if (pendingCustomerWriteSuccess != null && !crmRefreshQueued) {
            CustomerWriteSuccessPresentation presentation = pendingCustomerWriteSuccess;
            boolean refreshSucceeded = crmRefreshFailure == null;
            boolean targetCurrent = refreshSucceeded && customerWriteTargetCurrent(presentation);
            if (refreshSucceeded) {
                pendingCustomerWriteSuccess = null;
                pendingCustomerSelectionId = null;
                applyRolePermissions(canWrite);
            }
            applyCustomerWriteSuccessPresentation(presentation, refreshSucceeded, targetCurrent);
        }
        if (pendingOpenCustomerPresentation != null && !crmRefreshQueued) {
            applyOpenCustomerPresentation(pendingOpenCustomerPresentation);
            pendingOpenCustomerPresentation = null;
        }
        if (crmRefreshFailure != null) {
            showCrmRefreshState(crmRefreshFailureStatus(),
                    crmRefreshFailure, FarmUi.Tone.DANGER);
        }
        if (crmRefreshQueued) {
            crmRefreshQueued = false;
            startCrmRefresh();
        } else if (crmRefreshFailure == null) {
            preparePendingOrderLineCorrection();
            if (focusOrderProductAfterRefresh) {
                focusOrderProductAfterRefresh = false;
                focusNextOrderProductEntry();
            }
        } else {
            focusOrderProductAfterRefresh = false;
        }
    }

    void focusNextOrderProductEntry() {
        String guidance = "Next product entry is ready for the selected exact order. Enter the "
                + "product, quantity, unit, and price; press Ctrl+Enter after review.";
        orderProduct.setToolTipText(guidance);
        orderProduct.getAccessibleContext().setAccessibleDescription(guidance);
        FarmUi.revealAndFocus(orderProduct);
    }

    private void applyExistingOrderProductPresentation(
            boolean refreshSucceeded, boolean targetRestored) {
        Long orderId = pendingExistingOrderProductId;
        String productName = pendingExistingOrderProductName == null
                || pendingExistingOrderProductName.isBlank()
                ? "Product" : pendingExistingOrderProductName;
        String message;
        String recovery;
        if (targetRestored) {
            unavailableProductDraftOrderId = null;
            message = "Order #" + orderId + " opened · “" + productName
                    + "” preserved for review";
            recovery = "Order #" + orderId + " is selected with the unsaved “"
                    + productName + "” entry preserved and bound to this exact order. Review "
                    + "the current customer, date, handoff, instructions, saved products, and "
                    + "stock before choosing Add product. Nothing has been added yet.";
        } else if (refreshSucceeded) {
            unavailableProductDraftOrderId = orderId;
            activeProductEntryOrderId = orderId;
            applyProductEntryDraft(orderProductDrafts.get(orderId));
            message = "Order #" + orderId + " missing · product preserved";
            recovery = "Order #" + orderId + " is no longer available. The unsaved “"
                    + productName + "” entry was preserved and cannot move silently. Choose "
                    + "Move product to new order or Clear product entry.";
        } else {
            unavailableProductDraftOrderId = orderId;
            activeProductEntryOrderId = orderId;
            applyProductEntryDraft(orderProductDrafts.get(orderId));
            message = "Order #" + orderId + " not loaded · product preserved";
            recovery = "CRM could not reload order #" + orderId + ". The unsaved “"
                    + productName + "” entry remains bound to that exact order and cannot be "
                    + "submitted elsewhere. Use Refresh CRM to retry; nothing has been added.";
        }
        updateOrderActions();
        FarmUi.updateStatusBadge(selectedOrderLabel, message, FarmUi.Tone.WARNING);
        selectedOrderLabel.setToolTipText(recovery);
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(recovery);
        status(recovery);
        FarmUi.revealAndFocus(targetRestored ? orderProduct : refreshCrmBtn);
    }

    private void showCrmRefreshState(String message, String description, FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(crmRefreshLabel, message, tone);
        crmRefreshLabel.setToolTipText(description);
        crmRefreshLabel.getAccessibleContext().setAccessibleDescription(description);
    }

    private static Long selectedId(JTable table, DefaultTableModel model) {
        if (table == null || model == null || table.getSelectedRow() < 0) {
            return null;
        }
        Object value = model.getValueAt(table.convertRowIndexToModel(table.getSelectedRow()), 0);
        return value instanceof Long id ? id : null;
    }

    private static boolean restoreSelectedId(JTable table, DefaultTableModel model, Long id) {
        if (table == null || model == null || id == null) {
            return false;
        }
        for (int modelRow = 0; modelRow < model.getRowCount(); modelRow++) {
            if (id.equals(model.getValueAt(modelRow, 0))) {
                int viewRow = table.convertRowIndexToView(modelRow);
                if (viewRow >= 0) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                    table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        this.canWrite = canWrite;
        boolean customerWriteEnabled = canWrite && !savingCustomer && !deletingCustomer
                && pendingCustomerWriteSuccess == null;
        boolean orderWriteEnabled = canWrite && !creatingOrder && !addingOrderLine
                && !runningOrderMutation
                && !fulfillingOrder && !refreshingCrm;
        // Search / filter / export stay enabled for VIEWER
        GuiPermissions.setWritable(customerWriteEnabled,
                custName, custContact, custEmail, custPhone, custType, custNotes);
        GuiPermissions.setWritable(orderWriteEnabled,
                orderCustomer, orderDate, orderInventoryProduct, orderProduct, orderQty, orderUnit,
                orderPrice, orderStatus, orderHandoffType, orderNotes,
                createOrderBtn, addOrderLineBtn, confirmOrderBtn, fulfillOrderBtn,
                startAnotherOrderBtn, editOrderLineBtn, cancelOrderLineEditBtn,
                removeOrderLineBtn, selectedOrderDate, saveOrderDateBtn,
                selectedOrderHandoffType, saveOrderHandoffBtn,
                selectedOrderNotes, saveOrderNotesBtn);
        if (inventoryDragList != null) {
            inventoryDragList.setEnabled(orderWriteEnabled);
            updateInventoryDragButton();
        }
        setOrderToolChoices(canWrite);
        FarmUi.updateStatusBadge(accessLabel,
                canWrite ? "Editing enabled" : "View only — statements and exports remain available",
                canWrite ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING);
        updateCustomerFormMode();
        updateOrderActions();
    }

    private void buildUI() {
        panel = FarmUi.page();
        accessLabel = FarmUi.statusBadge("Editing enabled", FarmUi.Tone.SUCCESS);
        crmRefreshLabel = FarmUi.wrappingStatusBadge("Customers and orders not loaded",
                FarmUi.Tone.NEUTRAL);
        crmRefreshLabel.setName("crm.refreshStatus");
        crmRefreshLabel.getAccessibleContext().setAccessibleName(
                "Customer and order refresh status");
        documentStatusLabel = FarmUi.wrappingStatusBadge("Documents ready", FarmUi.Tone.NEUTRAL);
        documentStatusLabel.setName("crm.documentStatus");
        documentStatusLabel.getAccessibleContext().setAccessibleName(
                "Customer statement, invoice, and order export status");
        refreshCrmBtn = FarmUi.secondaryButton("Refresh CRM");
        refreshCrmBtn.setToolTipText(
                "Reload customers, filtered orders, and inventory choices.");
        refreshCrmBtn.addActionListener(event -> startCrmRefresh());
        panel.add(FarmUi.pageHeader(
                "Customers and orders",
                "stems".equalsIgnoreCase(defaultUnit)
                        ? "Manage market and florist relationships from inquiry through fulfillment."
                        : "Manage buyers and orders from inquiry through fulfillment.",
                FarmUi.toolbar(accessLabel, refreshCrmBtn)), BorderLayout.NORTH);
        JPanel statusMessages = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        statusMessages.setName("crm.statusMessages");
        statusMessages.setOpaque(false);
        statusMessages.add(crmRefreshLabel, BorderLayout.NORTH);
        statusMessages.add(documentStatusLabel, BorderLayout.SOUTH);
        panel.add(statusMessages, BorderLayout.SOUTH);

        crmWorkspace = new JTabbedPane();
        crmWorkspace.setName("crm.workspace");
        crmWorkspace.getAccessibleContext().setAccessibleName("Customer and order workspaces");
        crmWorkspace.addTab("Customers", FarmUi.section(
                "Customer accounts",
                "Search contacts, maintain account details, and export statements.",
                buildCustomerPanel()));
        crmWorkspace.addTab("Orders", FarmUi.section(
                "Wholesale and market orders",
                "Confirm orders, complete them against inventory, and save invoices.",
                buildOrderPanel()));
        crmWorkspace.setMnemonicAt(0, 'C');
        crmWorkspace.setMnemonicAt(1, 'O');
        crmWorkspace.addChangeListener(event -> {
            if (crmWorkspace.getSelectedIndex() == 1 && customerHandoffReturn != null) {
                clearCustomerHandoffReturn();
            }
        });
        panel.add(crmWorkspace, BorderLayout.CENTER);
    }

    private JComponent buildCustomerPanel() {
        JPanel p = new CrmWorkspaceBody(160);
        p.setName("crm.customerBody");

        CrmCustomerSearchView searchView = new CrmCustomerSearchView(this::applyCustomerFilter);
        custSearch = searchView.searchField();
        FarmUi.initialFocus(custSearch);
        customerHandoffLabel = FarmUi.statusBadge(
                "Customer opened from order workflow", FarmUi.Tone.INFO);
        customerHandoffLabel.getAccessibleContext().setAccessibleName(
                "Customer workflow return context");
        returnFromCustomerBtn = FarmUi.secondaryButton("Return to order entry");
        returnFromCustomerBtn.setName("crm.returnFromCustomer");
        returnFromCustomerBtn.setMnemonic(KeyEvent.VK_R);
        returnFromCustomerBtn.addActionListener(event -> returnFromCustomerHandoff());
        customerHandoffBanner = FarmUi.toolbar(customerHandoffLabel, returnFromCustomerBtn);
        customerHandoffBanner.setName("crm.customerHandoffBanner");
        customerHandoffBanner.setVisible(false);
        JPanel customerNorth = new JPanel();
        customerNorth.setOpaque(false);
        customerNorth.setLayout(new BoxLayout(customerNorth, BoxLayout.Y_AXIS));
        customerNorth.add(customerHandoffBanner);
        customerNorth.add(searchView);
        p.add(customerNorth, BorderLayout.NORTH);

        customerModel = new DefaultTableModel(
                new String[]{"Id", "Name", "Contact", "Phone", "Email", "Type", "Notes"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        customerTable = new JTable(customerModel);
        customerTable.setName("crm.customers.table");
        FarmUi.configureTable(customerTable, "Customer accounts table");
        customerTable.getColumnModel().getColumn(0).setMaxWidth(54);
        customerTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateCustomerFormMode();
            }
        });
        customerTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    loadCustomerIntoForm();
                }
            }
        });
        customerCardLayout = new CardLayout();
        customerState = new JPanel(customerCardLayout);
        customerState.add(FarmUi.tableScroll(customerTable), "table");
        customerEmptyState = FarmUi.emptyStateView(
                "No customers yet",
                "Add the first customer account below; it will be ready for order entry after save.");
        customerState.add(customerEmptyState.component(), "empty");
        p.add(customerState, BorderLayout.CENTER);

        JPanel form = new JPanel(new GridBagLayout());
        form.setName("crm.orderEntryForm");
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(
                0, FarmUi.SECTION_GAP, 0, FarmUi.SECTION_GAP));
        custName = new JTextField();
        custContact = new JTextField();
        custEmail = new JTextField();
        custPhone = new JTextField();
        custType = new JComboBox<>(new String[]{"Wholesale", "Market", "Florist", "Other"});
        custNotes = new JTextField();
        custName.getAccessibleContext().setAccessibleName("Customer or business name");
        custContact.getAccessibleContext().setAccessibleName("Customer contact person");
        custPhone.getAccessibleContext().setAccessibleName("Customer phone");
        custEmail.getAccessibleContext().setAccessibleName("Customer email");
        custType.getAccessibleContext().setAccessibleName("Customer type");
        custNotes.getAccessibleContext().setAccessibleName("Customer notes");
        for (JComponent control : List.of(
                custName, custContact, custPhone, custEmail, custType, custNotes)) {
            FarmUi.entryControl(control, null,
                    control.getAccessibleContext().getAccessibleName());
        }
        custNotes.addActionListener(event -> {
            if (editingCustomerId == null) {
                addCustomer();
            } else {
                saveCustomerEdit();
            }
        });
        addCustomerFormField(form, "Name*", custName, 'N');
        addCustomerFormField(form, "Contact", custContact, 'C');
        addCustomerFormField(form, "Phone", custPhone, 'P');
        addCustomerFormField(form, "Email", custEmail, 'E');
        addCustomerFormField(form, "Type", custType, 'T');
        addCustomerFormField(form, "Notes", custNotes, '\0');

        addCustomerBtn = FarmUi.primaryButton("Add customer");
        addCustomerBtn.addActionListener(e -> addCustomer());
        loadCustomerBtn = FarmUi.secondaryButton("Load selected");
        loadCustomerBtn.addActionListener(e -> loadCustomerIntoForm());
        saveCustomerBtn = FarmUi.primaryButton("Save edit");
        saveCustomerBtn.setToolTipText("Update loaded customer (double-click row to load).");
        saveCustomerBtn.addActionListener(e -> saveCustomerEdit());
        clearCustomerBtn = FarmUi.secondaryButton("Clear form");
        clearCustomerBtn.addActionListener(e -> clearCustomerFormWithReview());
        deleteCustomerBtn = FarmUi.dangerButton("Delete selected");
        deleteCustomerBtn.addActionListener(e -> deleteCustomer());
        statementCustomerBtn = FarmUi.secondaryButton("Statement PDF…");
        statementCustomerBtn.setToolTipText(
                "Account statement for selected customer (default last 90 days). VIEWER OK.");
        statementCustomerBtn.addActionListener(e -> exportCustomerStatement());
        reviewCustomerConflictBtn = FarmUi.secondaryButton("Review existing customer");
        reviewCustomerConflictBtn.setName("crm.reviewCustomerConflict");
        reviewCustomerConflictBtn.setVisible(false);
        reviewCustomerConflictBtn.setEnabled(false);
        reviewCustomerConflictBtn.setToolTipText(
                "Find and select the existing account without clearing the retained customer draft.");
        reviewCustomerConflictBtn.getAccessibleContext().setAccessibleName(
                "Review the existing customer with this name");
        reviewCustomerConflictBtn.addActionListener(e -> reviewExistingCustomer());
        customerModeLabel = FarmUi.wrappingStatusBadge("New customer", FarmUi.Tone.INFO);
        watchValidationCorrection(custName, () -> {
            clearCustomerConflictHandoff();
            showCustomerCorrectionReady();
        });
        watchValidationCorrection(custEmail, this::showCustomerCorrectionReady);
        JPanel primaryActions = FarmUi.toolbar(
                addCustomerBtn, loadCustomerBtn, saveCustomerBtn, clearCustomerBtn);
        primaryActions.setName("crm.customerPrimaryActions");
        JPanel secondaryActions = FarmUi.toolbar(
                deleteCustomerBtn, statementCustomerBtn, reviewCustomerConflictBtn);
        secondaryActions.setName("crm.customerSecondaryActions");
        JPanel buttons = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        buttons.setName("crm.customerFormActions");
        buttons.setOpaque(false);
        buttons.add(primaryActions, BorderLayout.NORTH);
        buttons.add(secondaryActions, BorderLayout.CENTER);
        buttons.add(customerModeLabel, BorderLayout.SOUTH);
        trackCustomerFormChanges();
        markCustomerFormClean();
        updateCustomerFormMode();

        JPanel south = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        south.setOpaque(false);
        south.add(form, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        p.add(south, BorderLayout.SOUTH);
        return scrollWorkspace(p, "crm.customerScroll");
    }

    private static void addCustomerFormField(JPanel form, String text,
                                            JComponent field, char mnemonic) {
        int position = form.getComponentCount() / 2;
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = (position % 2) * 2;
        constraints.gridy = position / 2;
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.insets = new Insets(position < 2 ? 0 : FarmUi.CONTROL_GAP,
                position % 2 == 0 ? 0 : FarmUi.SECTION_GAP, 0, FarmUi.CONTROL_GAP);
        form.add(fieldLabel(text, field, mnemonic), constraints);
        constraints.gridx++;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(position < 2 ? 0 : FarmUi.CONTROL_GAP, 0, 0, 0);
        form.add(field, constraints);
    }

    private static JScrollPane scrollWorkspace(JPanel content, String name) {
        FarmUi.revealFocusedControls(content);
        JScrollPane scroll = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName(name);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        return scroll;
    }

    /** Preserve table and form space while shorter windows scroll the whole workspace. */
    private static final class CrmWorkspaceBody extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;
        private final int minimumCenterHeight;

        private CrmWorkspaceBody(int minimumCenterHeight) {
            super(new BorderLayout(6, 6));
            this.minimumCenterHeight = minimumCenterHeight;
            setOpaque(false);
        }

        @Override public Dimension getPreferredSize() {
            Dimension preferred = super.getPreferredSize();
            Component center = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.CENTER);
            if (center != null) {
                preferred.height += minimumCenterHeight - center.getPreferredSize().height;
            }
            return preferred;
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 24;
        }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(24, visible.height - 24);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() >= getPreferredSize().height;
        }
    }

    private JComponent buildOrderPanel() {
        JPanel p = new CrmWorkspaceBody(360);
        p.setName("crm.orderPanel");
        p.setOpaque(false);

        filterStatus = new JComboBox<>(new String[]{
                "All statuses", "Open", "Draft", "Confirmed", "Ready to complete",
                "Needs attention", "Completed", "Cancelled"});
        filterStatus.setPrototypeDisplayValue("All statuses");
        filterStatus.setName("crm.orderStatusFilter");
        filterStatus.getAccessibleContext().setAccessibleName("Order status filter");
        filterHandoff = new JComboBox<>(new String[]{
                "All handoffs", "Needs review", "Pickup", "Delivery", "Market"});
        filterHandoff.setName("crm.orderHandoffFilter");
        filterHandoff.getAccessibleContext().setAccessibleName("Order handoff filter");
        filterHandoff.setToolTipText(
                "Show all handoffs or isolate Pickup, Delivery, Market, or legacy orders needing review.");
        filterStatus.setToolTipText(
                "Filter by lifecycle, or split Confirmed orders into Ready to complete and Needs attention using the current inventory snapshot.");
        filterCustomer = FarmUi.searchField("Order customer filter", "Customer name", 12);
        filterFrom = new JTextField(LocalDate.now().minusDays(30).toString(), 10);
        filterTo = new JTextField(LocalDate.now().toString(), 10);
        filterFrom.setName("crm.orderFilterFrom");
        filterTo.setName("crm.orderFilterTo");
        filterFrom.getAccessibleContext().setAccessibleName("Order filter start date");
        filterTo.getAccessibleContext().setAccessibleName("Order filter end date");
        orderStatusLabel = FarmUi.wrappingStatusBadge("Orders not loaded", FarmUi.Tone.NEUTRAL);
        watchValidationCorrection(filterFrom, this::showOrderFilterCorrectionReady);
        watchValidationCorrection(filterTo, this::showOrderFilterCorrectionReady);
        fulfillmentStatusLabel = FarmUi.wrappingStatusBadge(
                "Completion ready · inventory changes only after confirmation",
                FarmUi.Tone.NEUTRAL);
        fulfillmentStatusLabel.setName("crm.fulfillmentStatus");
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleName(
                "Order completion status");
        applyOrderFilterBtn = FarmUi.primaryButton("Apply filters");
        applyOrderFilterBtn.addActionListener(e -> applyOrderFilter());
        clearOrderFilterBtn = FarmUi.secondaryButton("Show all");
        clearOrderFilterBtn.setName("crm.showAllOrders");
        clearOrderFilterBtn.setMnemonic(KeyEvent.VK_L);
        clearOrderFilterBtn.setToolTipText(
                "Show all orders without clearing unfinished order or product entry (Alt+L).");
        clearOrderFilterBtn.getAccessibleContext().setAccessibleDescription(
                clearOrderFilterBtn.getToolTipText());
        clearOrderFilterBtn.addActionListener(e -> showAllOrders());
        readyToFulfillBtn = FarmUi.secondaryButton("Ready to complete");
        readyToFulfillBtn.setName("crm.readyToComplete");
        readyToFulfillBtn.setMnemonic(KeyEvent.VK_Y);
        readyToFulfillBtn.setToolTipText(
                "Clear broad filters and show confirmed orders with a specific handoff, saved products, valid quantities and units, and enough stock.");
        readyToFulfillBtn.getAccessibleContext().setAccessibleDescription(
                readyToFulfillBtn.getToolTipText());
        readyToFulfillBtn.addActionListener(e -> {
            filterStatus.setSelectedItem("Ready to complete");
            filterHandoff.setSelectedItem("All handoffs");
            filterCustomer.setText("");
            filterFrom.setText("");
            filterTo.setText("");
            applyOrderFilter();
            if (orderTable != null) {
                orderTable.requestFocusInWindow();
            }
        });
        needsHandoffReviewBtn = FarmUi.secondaryButton("Needs handoff review");
        needsHandoffReviewBtn.setName("crm.needsHandoffReview");
        needsHandoffReviewBtn.setMnemonic(KeyEvent.VK_G);
        needsHandoffReviewBtn.setToolTipText(
                "Clear broad filters and show every open legacy order that still needs Pickup, Delivery, or Market.");
        needsHandoffReviewBtn.getAccessibleContext().setAccessibleDescription(
                needsHandoffReviewBtn.getToolTipText());
        needsHandoffReviewBtn.addActionListener(event -> showNeedsHandoffReview());
        focusNewOrderBtn = FarmUi.primaryButton("New order");
        focusNewOrderBtn.setName("crm.focusNewOrder");
        focusNewOrderBtn.setToolTipText(
                "Open a clean new-order workspace without losing unfinished work on a selected order.");
        focusNewOrderBtn.getAccessibleContext().setAccessibleDescription(
                focusNewOrderBtn.getToolTipText());
        focusNewOrderBtn.addActionListener(event -> focusNewOrderEntry());
        JPanel filterCriteria = FarmUi.toolbar(
                fieldLabel("Status", filterStatus, '\0'), filterStatus,
                fieldLabel("Handoff", filterHandoff, '\0'), filterHandoff,
                fieldLabel("Customer", filterCustomer, '\0'), filterCustomer);
        JPanel filterDates = FarmUi.toolbar(
                fieldLabel("From", filterFrom, '\0'), filterFrom,
                fieldLabel("To", filterTo, '\0'), filterTo);
        JPanel filterActions = FarmUi.toolbar(
                focusNewOrderBtn, applyOrderFilterBtn, clearOrderFilterBtn, readyToFulfillBtn,
                needsHandoffReviewBtn);
        filterActions.setName("crm.orderFilterActions");
        JPanel filterStatuses = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        filterStatuses.setName("crm.orderFilterStatuses");
        filterStatuses.setOpaque(false);
        filterStatuses.add(orderStatusLabel, BorderLayout.NORTH);
        filterStatuses.add(fulfillmentStatusLabel, BorderLayout.CENTER);
        JPanel filterBar = new JPanel();
        filterBar.setOpaque(false);
        filterBar.setLayout(new BoxLayout(filterBar, BoxLayout.Y_AXIS));
        orderHandoffLabel = FarmUi.statusBadge(
                "Exact order review", FarmUi.Tone.INFO);
        orderHandoffLabel.getAccessibleContext().setAccessibleName(
                "Exact order handoff context");
        returnFromOrderBtn = FarmUi.secondaryButton("Return to previous workflow");
        returnFromOrderBtn.setName("crm.returnFromOrder");
        returnFromOrderBtn.addActionListener(event -> returnFromOrderHandoff());
        orderHandoffBanner = FarmUi.toolbar(orderHandoffLabel, returnFromOrderBtn);
        orderHandoffBanner.setName("crm.orderHandoffBanner");
        orderHandoffBanner.setVisible(false);
        filterBar.add(orderHandoffBanner);
        filterBar.add(filterCriteria);
        filterBar.add(filterDates);
        filterBar.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        filterBar.add(filterActions);
        filterBar.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        filterBar.add(filterStatuses);
        for (JComponent row : List.of(
                orderHandoffBanner, filterCriteria, filterDates, filterActions, filterStatuses)) {
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        p.add(filterBar, BorderLayout.NORTH);

        orderModel = new DefaultTableModel(
                new String[]{"Id", "Date", "Customer", "Status", "Handoff", "Lines", "Total $", "Notes"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        orderTable = new JTable(orderModel);
        orderTable.setName("crm.orders.table");
        FarmUi.configureTable(orderTable, "Customer orders table");
        orderTable.getColumnModel().getColumn(0).setMaxWidth(54);
        orderTable.getColumnModel().getColumn(4).setPreferredWidth(92);
        orderTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                orderSelectionChanged();
            }
        });
        orderCardLayout = new CardLayout();
        orderState = new JPanel(orderCardLayout);
        orderState.add(FarmUi.tableScroll(orderTable), "table");
        orderState.add(buildOrderEmptyState(), "empty");

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JPanel identityForm = new JPanel(new GridLayout(0, 2, 6, 4));
        identityForm.setOpaque(false);
        identityForm.setName("crm.newOrderDetails");
        JPanel productForm = new JPanel(new GridLayout(0, 2, 6, 4));
        productForm.setOpaque(false);
        productForm.setName("crm.productEntry");
        orderCustomer = new JComboBox<>();
        orderCustomer.getAccessibleContext().setAccessibleName("New order customer");
        orderCustomer.addActionListener(event -> {
            if (!replacingNewOrderCustomers) {
                updateNewOrderCustomerContext();
                updateOrderActions();
            }
        });
        newOrderCustomerContext = FarmUi.displayArea(2, 42);
        newOrderCustomerContext.setName("crm.newOrderCustomerContext");
        newOrderCustomerContext.setText(
                "Add or select a customer to verify contact and delivery context.");
        newOrderCustomerContext.setBackground(FarmUi.surfaceColor());
        newOrderCustomerContext.setBorder(
                BorderFactory.createLineBorder(FarmUi.borderColor()));
        newOrderCustomerContext.getAccessibleContext().setAccessibleName(
                "New order customer and delivery context");
        openNewOrderCustomerBtn = FarmUi.secondaryButton("Open draft customer");
        openNewOrderCustomerBtn.setName("crm.openNewOrderCustomer");
        openNewOrderCustomerBtn.setMnemonic(KeyEvent.VK_V);
        openNewOrderCustomerBtn.setEnabled(false);
        openNewOrderCustomerBtn.setToolTipText(
                "Open the selected draft customer's account without clearing order entry.");
        openNewOrderCustomerBtn.getAccessibleContext().setAccessibleDescription(
                openNewOrderCustomerBtn.getToolTipText());
        openNewOrderCustomerBtn.addActionListener(event -> reviewNewOrderCustomer());
        orderDate = new JTextField(LocalDate.now().toString(), 10);
        orderDate.setName("crm.orderDate");
        orderDate.getAccessibleContext().setAccessibleName("Order date");
        orderDate.setToolTipText(
                "Pickup, delivery, or market date in YYYY-MM-DD format.");
        orderProduct = new JTextField();
        orderProduct.getAccessibleContext().setAccessibleName("Order product name");
        orderQty = new JTextField("1");
        orderQty.getAccessibleContext().setAccessibleName("Order quantity");
        orderUnit = new JTextField(defaultUnit);
        orderUnit.getAccessibleContext().setAccessibleName("Order unit");
        orderPrice = new JTextField();
        orderPrice.getAccessibleContext().setAccessibleName("Order unit price");
        orderPrice.addActionListener(event -> submitOrderLineEntry());
        orderStatus = new JComboBox<>(new String[]{"Draft"});
        orderStatus.setSelectedItem("Draft");
        orderStatus.getAccessibleContext().setAccessibleName("New order status");
        orderHandoffType = new JComboBox<>(
                new String[]{"Pickup", "Delivery", "Market"});
        orderHandoffType.setSelectedItem("Pickup");
        orderHandoffType.setName("crm.orderHandoffType");
        orderHandoffType.getAccessibleContext().setAccessibleName(
                "New order handoff type");
        orderHandoffType.setToolTipText(
                "Choose whether farm staff will prepare this order for pickup, delivery, or market.");
        orderHandoffType.addActionListener(event -> {
            updateNewOrderCustomerContext();
            updateOrderActions();
        });
        orderNotes = new JTextField();
        orderNotes.getAccessibleContext().setAccessibleName("Order notes");
        acceptNewOrderIdentityBaseline();
        orderInventoryProduct = new JComboBox<>();
        orderInventoryProduct.setPrototypeDisplayValue(
                new CrmPresenter.InventoryChoice("Long inventory product name", "", 0, 999));
        orderInventoryProduct.setName("crm.inventoryProductChoice");
        orderInventoryProduct.getAccessibleContext().setAccessibleName(
                "Choose an inventory product for this order line");
        orderInventoryProduct.addActionListener(event -> applyInventoryChoice());

        inventoryDragModel = new DefaultListModel<>();
        inventoryDragList = new JList<>(inventoryDragModel);
        inventoryDragList.setName("crm.inventoryDragList");
        inventoryDragList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        inventoryDragList.setVisibleRowCount(3);
        inventoryDragList.setToolTipText("Drag a stock item to the drop area, or select it and use the button.");
        inventoryDragList.getAccessibleContext().setAccessibleName(
                "Inventory products to drag into an order draft");
        inventoryDragList.getAccessibleContext().setAccessibleDescription(
                inventoryDragList.getToolTipText());
        inventoryDragList.setTransferHandler(new TransferHandler() {
            @Override protected Transferable createTransferable(JComponent source) {
                CrmPresenter.InventoryChoice choice = inventoryDragList.getSelectedValue();
                return choice == null ? null : new StringSelection(choice.name());
            }

            @Override public int getSourceActions(JComponent source) {
                return COPY;
            }
        });
        if (!GraphicsEnvironment.isHeadless()) {
            inventoryDragList.setDragEnabled(true);
        }
        useDraggedInventoryBtn = FarmUi.secondaryButton("Use selected product");
        useDraggedInventoryBtn.setName("crm.useInventoryProduct");
        useDraggedInventoryBtn.setEnabled(false);
        useDraggedInventoryBtn.setToolTipText(
                "Fill the order draft from the selected stock item. Review quantity and save separately.");
        useDraggedInventoryBtn.addActionListener(event -> {
            CrmPresenter.InventoryChoice selected = inventoryDragList.getSelectedValue();
            if (selected != null) {
                useInventoryChoiceFromShelf(selected.name());
            }
        });
        inventoryDragList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateInventoryDragButton();
            }
        });
        JPanel inventoryDropTarget = new JPanel(new BorderLayout());
        inventoryDropTarget.setName("crm.inventoryDropTarget");
        inventoryDropTarget.setOpaque(false);
        JLabel dropInstruction = new JLabel("↓ Drop inventory product here to fill this draft",
                SwingConstants.CENTER);
        dropInstruction.setForeground(FarmUi.accentColor());
        dropInstruction.setName("crm.inventoryDropInstruction");
        inventoryDropTarget.add(dropInstruction, BorderLayout.CENTER);
        inventoryDropTarget.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(FarmUi.accentColor(), 1.5f, 4f),
                BorderFactory.createEmptyBorder(9, 10, 9, 10)));
        inventoryDropTarget.getAccessibleContext().setAccessibleName(
                "Drop inventory product into order draft");
        inventoryDropTarget.getAccessibleContext().setAccessibleDescription(
                "Drop fills product, unit, and price only; review quantity and save the order separately.");
        TransferHandler inventoryDropHandler = new TransferHandler() {
            @Override public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.stringFlavor)
                        && canFillProductFromInventory();
            }

            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    String name = (String) support.getTransferable()
                            .getTransferData(DataFlavor.stringFlavor);
                    return useInventoryChoiceFromShelf(name);
                } catch (Exception exception) {
                    status("Inventory product could not be read from the drop. Choose it from the list.");
                    return false;
                }
            }
        };
        inventoryDropTarget.setTransferHandler(inventoryDropHandler);
        dropInstruction.setTransferHandler(inventoryDropHandler);
        JPanel inventoryShelf = new JPanel(new BorderLayout(6, 6));
        inventoryShelf.setName("crm.inventoryDragShelf");
        inventoryShelf.setOpaque(false);
        inventoryShelf.add(new JLabel("Stock items · drag or select"), BorderLayout.NORTH);
        inventoryShelf.add(new JScrollPane(inventoryDragList), BorderLayout.CENTER);
        inventoryShelf.add(useDraggedInventoryBtn, BorderLayout.EAST);
        JPanel productEntryBody = new JPanel();
        productEntryBody.setOpaque(false);
        productEntryBody.setLayout(new BoxLayout(productEntryBody, BoxLayout.Y_AXIS));
        if (presenter.inventoryGuidanceAvailable()) {
            productEntryBody.add(inventoryShelf);
            productEntryBody.add(Box.createVerticalStrut(6));
            productEntryBody.add(inventoryDropTarget);
            productEntryBody.add(Box.createVerticalStrut(8));
        }
        productEntryBody.add(productForm);

        addFormField(identityForm, "Customer*", orderCustomer, 'U');
        addFormField(identityForm, "Order date*", orderDate, 'D');
        addFormField(identityForm, "Handoff*", orderHandoffType, 'H');
        addFormField(identityForm, "Starting status", orderStatus, '\0');
        addFormField(identityForm, "Notes", orderNotes, '\0');
        addFormField(productForm, "Inventory product", orderInventoryProduct, 'I');
        addFormField(productForm, "Product*", orderProduct, 'R');
        addFormField(productForm, "Quantity*", orderQty, 'Q');
        addFormField(productForm, "Unit", orderUnit, '\0');
        addFormField(productForm, "Unit price $", orderPrice, '\0');
        JPanel identitySection = FarmUi.section(
                "1 · Order basics",
                "New orders start as Draft. Review details before confirmation.",
                identityForm);
        JPanel productSection = FarmUi.section(
                "2 · Product and quantity",
                "Choose an inventory product when possible, then confirm quantity, unit, and selling price.",
                productEntryBody);
        JPanel newOrderCustomerContextPanel = new JPanel(new BorderLayout(6, 6));
        newOrderCustomerContextPanel.setOpaque(false);
        newOrderCustomerContextPanel.add(newOrderCustomerContext, BorderLayout.CENTER);
        newOrderCustomerContextPanel.add(openNewOrderCustomerBtn, BorderLayout.EAST);
        JPanel customerContextSection = FarmUi.section(
                "Customer & delivery context",
                "Delivery readiness updates from customer contact, account notes, and order instructions; Draft creation remains available.",
                newOrderCustomerContextPanel);
        newOrderSetupPanel = new JPanel();
        newOrderSetupPanel.setName("crm.newOrderSetup");
        newOrderSetupPanel.setOpaque(false);
        newOrderSetupPanel.setLayout(new BoxLayout(newOrderSetupPanel, BoxLayout.Y_AXIS));
        newOrderSetupPanel.add(identitySection);
        newOrderSetupPanel.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        newOrderSetupPanel.add(customerContextSection);

        createOrderBtn = FarmUi.primaryButton("Create & select order");
        createOrderBtn.setToolTipText(
                "Create the dated order with this first product and keep it selected for more products.");
        createOrderBtn.addActionListener(e -> createOrder());
        createOrderBtn.setMnemonic(KeyEvent.VK_N);
        addOrderLineBtn = FarmUi.secondaryButton("Add product to selected order");
        addOrderLineBtn.setToolTipText(
                "Add the product, quantity, unit, and price above to the selected order.");
        addOrderLineBtn.addActionListener(e -> addProductToSelectedOrder());
        addOrderLineBtn.setMnemonic(KeyEvent.VK_A);
        confirmOrderBtn = FarmUi.secondaryButton("Confirm order");
        confirmOrderBtn.setToolTipText(
                "Mark a draft order as confirmed. Inventory does not change until completion.");
        confirmOrderBtn.addActionListener(e -> confirmOrder());
        confirmOrderBtn.setMnemonic(KeyEvent.VK_M);
        fulfillOrderBtn = FarmUi.primaryButton("Complete order");
        fulfillOrderBtn.setName("crm.completeOrder");
        fulfillOrderBtn.setToolTipText(
                "Complete the selected order and deduct matching products from inventory.");
        fulfillOrderBtn.addActionListener(e -> markFulfilled());
        fulfillOrderBtn.setMnemonic(KeyEvent.VK_P);
        startAnotherOrderBtn = FarmUi.secondaryButton("Start another order");
        startAnotherOrderBtn.setName("crm.startAnotherOrder");
        startAnotherOrderBtn.setToolTipText(
                "Pause any unfinished selected-order product entry and switch to the separate new-order draft.");
        startAnotherOrderBtn.addActionListener(event -> startAnotherOrder());
        startAnotherOrderBtn.setMnemonic(KeyEvent.VK_W);
        resetNewOrderDetailsBtn = FarmUi.secondaryButton("Reset new-order details");
        resetNewOrderDetailsBtn.setName("crm.resetNewOrderDetails");
        resetNewOrderDetailsBtn.setEnabled(false);
        resetNewOrderDetailsBtn.setToolTipText(
                "Review before restoring the accepted customer, date, and handoff and clearing instructions.");
        resetNewOrderDetailsBtn.addActionListener(event -> resetNewOrderDetails());
        resetNewOrderDetailsBtn.setMnemonic(KeyEvent.VK_Z);
        selectedOrderLabel = FarmUi.statusBadge(
                "New order mode · Create & select starts a separate order",
                FarmUi.Tone.NEUTRAL);
        selectedOrderLabel.setName("crm.selectedOrder");
        selectedOrderLabel.getAccessibleContext().setAccessibleName("Active order entry target");
        selectedOrderCustomerContext = FarmUi.displayArea(2, 42);
        selectedOrderCustomerContext.setName("crm.selectedOrderCustomer");
        selectedOrderCustomerContext.setText(
                "Select an order to see its customer contact and account context.");
        selectedOrderCustomerContext.setBackground(FarmUi.surfaceColor());
        selectedOrderCustomerContext.setBorder(
                BorderFactory.createLineBorder(FarmUi.borderColor()));
        selectedOrderCustomerContext.getAccessibleContext().setAccessibleName(
                "Selected order customer contact");
        selectedOrderCustomerContext.getAccessibleContext().setAccessibleDescription(
                selectedOrderCustomerContext.getText());
        reviewOrderCustomerBtn = FarmUi.secondaryButton("Open customer");
        reviewOrderCustomerBtn.setName("crm.openOrderCustomer");
        reviewOrderCustomerBtn.setEnabled(false);
        reviewOrderCustomerBtn.setToolTipText(
                "Open the customer account linked to the selected order.");
        reviewOrderCustomerBtn.addActionListener(event -> reviewSelectedOrderCustomer());
        reviewOrderCustomerBtn.setMnemonic(KeyEvent.VK_K);
        watchNewOrderIdentityValidation(orderDate);
        watchNewOrderIdentityValidation(orderNotes);
        watchValidationCorrection(orderProduct, this::showOrderCorrectionReady);
        watchValidationCorrection(orderQty, this::showOrderCorrectionReady);
        watchValidationCorrection(orderUnit, this::showOrderCorrectionReady);
        watchValidationCorrection(orderPrice, this::showOrderCorrectionReady);
        selectedOrderStockLabel = FarmUi.statusBadge(
                "Select an order to check total stock", FarmUi.Tone.NEUTRAL);
        selectedOrderStockLabel.setName("crm.selectedOrderStock");
        selectedOrderStockLabel.getAccessibleContext().setAccessibleName(
                "Selected order stock readiness");
        resolveOrderStockBtn = FarmUi.secondaryButton("Resolve order stock");
        resolveOrderStockBtn.setName("crm.resolveOrderStock");
        resolveOrderStockBtn.setEnabled(false);
        resolveOrderStockBtn.setToolTipText(
                "Open Inventory with only products blocking the selected order.");
        resolveOrderStockBtn.setMnemonic(KeyEvent.VK_B);
        resolveOrderStockBtn.addActionListener(event -> {
            if (selectedOrderQuantityIssue) {
                beginInvalidQuantityCorrection();
                return;
            }
            if (selectedOrderUnitIssue) {
                beginUnitMismatchCorrection();
                return;
            }
            if (host == null || selectedOrderStockProducts.isEmpty()) {
                return;
            }
            Long orderId = selectedOrderId();
            host.showInventoryProducts(selectedOrderStockProducts,
                    "Customers & Orders", "order #" + orderId);
            host.setStatus("Opened Inventory for order #" + orderId + " stock gaps: "
                    + String.join(", ", selectedOrderStockProducts) + ".");
        });
        selectedOrderDate = new JTextField(10);
        selectedOrderDate.setName("crm.selectedOrderDate");
        selectedOrderDate.getAccessibleContext().setAccessibleName(
                "Selected order pickup or delivery date");
        selectedOrderDate.setToolTipText(
                "Correct the selected order's pickup, delivery, or market date (YYYY-MM-DD). "
                        + "Unsaved corrections are kept if you inspect another order.");
        selectedOrderDate.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) {
                selectedOrderDateChanged();
            }

            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) {
                selectedOrderDateChanged();
            }

            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) {
                selectedOrderDateChanged();
            }
        });
        selectedOrderDate.addActionListener(event -> saveSelectedOrderDate());
        saveOrderDateBtn = FarmUi.secondaryButton("Save order date");
        saveOrderDateBtn.setName("crm.saveOrderDate");
        saveOrderDateBtn.setEnabled(false);
        saveOrderDateBtn.setToolTipText(
                "Save the corrected date and refresh Market Day; inventory does not change.");
        saveOrderDateBtn.addActionListener(event -> saveSelectedOrderDate());
        saveOrderDateBtn.setMnemonic(KeyEvent.VK_T);
        selectedOrderHandoffType = new JComboBox<>(
                new String[]{"Unspecified", "Pickup", "Delivery", "Market"});
        selectedOrderHandoffType.setName("crm.selectedOrderHandoffType");
        selectedOrderHandoffType.getAccessibleContext().setAccessibleName(
                "Selected order handoff type");
        selectedOrderHandoffType.setToolTipText(
                "Correct whether the selected order is a pickup, delivery, or market handoff. "
                        + "Unsaved corrections are kept if you inspect another order.");
        selectedOrderHandoffType.addActionListener(event -> selectedOrderHandoffChanged());
        saveOrderHandoffBtn = FarmUi.secondaryButton("Save handoff type");
        saveOrderHandoffBtn.setName("crm.saveOrderHandoff");
        saveOrderHandoffBtn.setEnabled(false);
        saveOrderHandoffBtn.setToolTipText(
                "Save the handoff type and refresh invoices, packing lists, and Market Day.");
        saveOrderHandoffBtn.addActionListener(event -> saveSelectedOrderHandoff());
        saveOrderHandoffBtn.setMnemonic(KeyEvent.VK_F);
        selectedOrderNotes = new JTextField(28);
        selectedOrderNotes.setName("crm.selectedOrderNotes");
        selectedOrderNotes.getAccessibleContext().setAccessibleName(
                "Selected order saved instructions");
        selectedOrderNotes.setToolTipText(
                "Edit pickup, delivery, packing, or customer instructions for the selected order. "
                        + "Unsaved corrections are kept if you inspect another order.");
        selectedOrderNotes.getDocument().addDocumentListener(
                new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) {
                selectedOrderNotesChanged();
            }

            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) {
                selectedOrderNotesChanged();
            }

            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) {
                selectedOrderNotesChanged();
            }
        });
        selectedOrderNotes.addActionListener(event -> saveOrderNotes());
        saveOrderNotesBtn = FarmUi.secondaryButton("Save order instructions");
        saveOrderNotesBtn.setName("crm.saveOrderNotes");
        saveOrderNotesBtn.setEnabled(false);
        saveOrderNotesBtn.setToolTipText(
                "Save these instructions and refresh invoices, packing lists, and farm handoffs.");
        saveOrderNotesBtn.addActionListener(event -> saveOrderNotes());
        saveOrderNotesBtn.setMnemonic(KeyEvent.VK_S);
        selectedOrderLine = new JComboBox<>();
        selectedOrderLine.setName("crm.selectedOrderLine");
        selectedOrderLine.setPrototypeDisplayValue(
                new OrderLineChoice(1L, "Long product name for review", 999, "bunches", 999));
        selectedOrderLine.getAccessibleContext().setAccessibleName(
                "Products on the selected order");
        selectedOrderLine.setToolTipText(
                "Review one saved product line before editing or removing it from this order.");
        selectedOrderLine.addActionListener(event -> {
            if (!updatingOrderLineChoices) {
                updateOrderActions();
            }
        });
        editOrderLineBtn = FarmUi.secondaryButton("Edit selected product");
        editOrderLineBtn.setName("crm.editOrderLine");
        editOrderLineBtn.setEnabled(false);
        editOrderLineBtn.setToolTipText(
                "Load the chosen saved product into the entry fields for correction.");
        editOrderLineBtn.addActionListener(event -> editSelectedOrderLine());
        editOrderLineBtn.setMnemonic(KeyEvent.VK_E);
        cancelOrderLineEditBtn = FarmUi.secondaryButton("Clear product entry");
        cancelOrderLineEditBtn.setName("crm.cancelOrderLineEdit");
        cancelOrderLineEditBtn.setEnabled(false);
        cancelOrderLineEditBtn.setToolTipText(
                "Clear the unsaved product entry without changing the selected order.");
        cancelOrderLineEditBtn.addActionListener(event -> clearProductEntry());
        cancelOrderLineEditBtn.setMnemonic(KeyEvent.VK_X);
        removeOrderLineBtn = FarmUi.dangerButton("Remove selected product");
        removeOrderLineBtn.setName("crm.removeOrderLine");
        removeOrderLineBtn.setEnabled(false);
        removeOrderLineBtn.setToolTipText(
                "Remove the chosen product from this order after an exact customer, delivery, and "
                        + "product review (Alt+O). Inventory does not change before completion.");
        removeOrderLineBtn.getAccessibleContext().setAccessibleDescription(
                removeOrderLineBtn.getToolTipText());
        removeOrderLineBtn.addActionListener(event -> removeSelectedOrderLine());
        removeOrderLineBtn.setMnemonic(KeyEvent.VK_O);
        orderLinePreviewLabel = FarmUi.statusBadge(
                "Enter a product, quantity, unit, and price", FarmUi.Tone.NEUTRAL);
        orderLinePreviewLabel.setName("crm.orderLinePreview");
        orderLinePreviewLabel.getAccessibleContext().setAccessibleName("Order line preview");
        javax.swing.event.DocumentListener linePreviewListener =
                new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) {
                updateOrderLinePreview();
            }

            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) {
                updateOrderLinePreview();
            }

            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) {
                updateOrderLinePreview();
            }
        };
        orderProduct.getDocument().addDocumentListener(linePreviewListener);
        orderQty.getDocument().addDocumentListener(linePreviewListener);
        orderUnit.getDocument().addDocumentListener(linePreviewListener);
        orderPrice.getDocument().addDocumentListener(linePreviewListener);
        updateOrderLinePreview();
        updateOrderActions();
        JPanel lifecycle = FarmUi.toolbar(
                createOrderBtn, addOrderLineBtn, confirmOrderBtn, fulfillOrderBtn);
        lifecycle.setName("crm.orderLifecycleActions");

        orderTools = new JComboBox<>();
        setOrderToolChoices(true);
        orderTools.setName("crm.orderTools");
        orderTools.getAccessibleContext().setAccessibleName("More order actions");
        orderTools.addActionListener(event -> updateOrderActions());
        runOrderToolBtn = FarmUi.secondaryButton("Run action");
        runOrderToolBtn.addActionListener(e -> runOrderTool((String) orderTools.getSelectedItem()));
        JPanel secondaryActions = new JPanel();
        secondaryActions.setOpaque(false);
        secondaryActions.setLayout(new BoxLayout(secondaryActions, BoxLayout.Y_AXIS));
        secondaryActions.add(FarmUi.toolbar(
                startAnotherOrderBtn, resetNewOrderDetailsBtn));
        secondaryActions.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        JPanel orderToolRow = new JPanel(new BorderLayout(FarmUi.CONTROL_GAP, FarmUi.CONTROL_GAP));
        orderToolRow.setOpaque(false);
        orderToolRow.add(controlLabel("More order actions", orderTools), BorderLayout.NORTH);
        orderToolRow.add(orderTools, BorderLayout.CENTER);
        orderToolRow.add(runOrderToolBtn, BorderLayout.EAST);
        secondaryActions.add(orderToolRow);
        updateOrderActions();

        JPanel south = new JPanel(new BorderLayout());
        JPanel selectedReviewBody = new JPanel();
        selectedReviewBody.setOpaque(false);
        selectedReviewBody.setLayout(
                new BoxLayout(selectedReviewBody, BoxLayout.Y_AXIS));
        JPanel customerContext = new JPanel(new BorderLayout(FarmUi.CONTROL_GAP, 0));
        customerContext.setOpaque(false);
        customerContext.add(selectedOrderCustomerContext, BorderLayout.CENTER);
        customerContext.add(reviewOrderCustomerBtn, BorderLayout.EAST);
        selectedReviewBody.add(customerContext);
        selectedReviewBody.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        selectedReviewBody.add(FarmUi.toolbar(
                selectedOrderStockLabel, resolveOrderStockBtn));
        selectedReviewBody.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        JPanel correctionGrid = new JPanel(new GridLayout(
                2, 1, FarmUi.CONTROL_GAP, FarmUi.CONTROL_GAP));
        correctionGrid.setOpaque(false);
        correctionGrid.add(FarmUi.toolbar(
                controlLabel("Selected order date", selectedOrderDate),
                selectedOrderDate, saveOrderDateBtn));
        correctionGrid.add(FarmUi.toolbar(
                controlLabel("Selected handoff type", selectedOrderHandoffType),
                selectedOrderHandoffType, saveOrderHandoffBtn));
        selectedReviewBody.add(correctionGrid);
        selectedReviewBody.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        selectedReviewBody.add(FarmUi.toolbar(
                controlLabel("Pickup / packing instructions", selectedOrderNotes),
                selectedOrderNotes, saveOrderNotesBtn));
        selectedReviewBody.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        selectedReviewBody.add(FarmUi.toolbar(
                controlLabel("Saved products", selectedOrderLine), selectedOrderLine));
        selectedReviewBody.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        selectedReviewBody.add(FarmUi.toolbar(
                editOrderLineBtn, cancelOrderLineEditBtn, removeOrderLineBtn));
        selectedOrderReviewPanel = FarmUi.section(
                "Selected order readiness",
                "Review the customer, delivery details, stock, and saved products for this order only.",
                selectedReviewBody);
        selectedOrderReviewPanel.setName("crm.selectedOrderReview");
        selectedOrderReviewPanel.setVisible(false);

        JPanel contextColumn = new JPanel();
        contextColumn.setName("crm.orderContextColumn");
        contextColumn.setOpaque(false);
        contextColumn.setLayout(new BoxLayout(contextColumn, BoxLayout.Y_AXIS));
        contextColumn.add(newOrderSetupPanel);
        contextColumn.add(selectedOrderReviewPanel);

        JPanel productColumn = new JPanel();
        productColumn.setName("crm.orderProductColumn");
        productColumn.setOpaque(false);
        productColumn.setLayout(new BoxLayout(productColumn, BoxLayout.Y_AXIS));
        productColumn.add(productSection);
        productColumn.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        productColumn.add(orderLinePreviewLabel);
        productColumn.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        productColumn.add(FarmUi.section(
                "3 · Save the next step",
                "Create a Draft, add another product, confirm it for packing, or complete the physical handoff.",
                lifecycle));
        productColumn.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        productColumn.add(secondaryActions);

        JPanel entryColumns = new JPanel(new GridLayout(
                1, 2, 12, 0));
        entryColumns.setName("crm.orderEntryColumns");
        entryColumns.setOpaque(false);
        entryColumns.add(contextColumn);
        entryColumns.add(productColumn);
        form.add(selectedOrderLabel);
        form.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        form.add(entryColumns);
        south.add(form, BorderLayout.CENTER);
        Dimension entryPreferred = south.getPreferredSize();
        south.setPreferredSize(new Dimension(0, entryPreferred.height));

        JScrollPane entryScroll = new JScrollPane(south);
        entryScroll.setName("crm.orderEntryScroll");
        entryScroll.setBorder(null);
        entryScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        entryScroll.getVerticalScrollBar().setUnitIncrement(16);
        orderState.setMinimumSize(new Dimension(0, 140));
        entryScroll.setMinimumSize(new Dimension(0, 180));
        JSplitPane orderWorkspace = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, orderState, entryScroll) {
            private boolean dividerPlaced;

            @Override public void doLayout() {
                if (!dividerPlaced && getHeight() > 0) {
                    setDividerLocation(0.4);
                    dividerPlaced = true;
                }
                super.doLayout();
            }
        };
        orderWorkspace.setName("crm.orderWorkspace");
        orderWorkspace.setBorder(null);
        orderWorkspace.setResizeWeight(0.4);
        orderWorkspace.setContinuousLayout(true);
        orderWorkspace.setOneTouchExpandable(true);
        orderWorkspace.getAccessibleContext().setAccessibleName(
                "Order list and entry workspace");
        p.add(orderWorkspace, BorderLayout.CENTER);
        installOrderKeyboardActions(p);
        return scrollWorkspace(p, "crm.orderScroll");
    }

    private void installOrderKeyboardActions(JComponent scope) {
        InputMap input = scope.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actions = scope.getActionMap();
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK),
                "crm.submitOrderProduct");
        actions.put("crm.submitOrderProduct", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                submitOrderProductFromKeyboard();
            }
        });
        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), "crm.cycleOrderFocus");
        actions.put("crm.cycleOrderFocus", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                cycleOrderWorkflowFocus();
            }
        });
    }

    private void submitOrderProductFromKeyboard() {
        if (blockPendingOrderProductKeyboardRetry()) {
            return;
        }
        if (creatingOrder || addingOrderLine || runningOrderMutation || fulfillingOrder) {
            status("Order work is already saving. Wait for the result before submitting again.");
            return;
        }
        if (unavailableProductDraftOrderId != null) {
            status("Product not submitted. Order #" + unavailableProductDraftOrderId
                    + " is missing; choose Move product to new order or Clear product entry.");
            FarmUi.revealAndFocus(startAnotherOrderBtn);
            return;
        }
        if (selectedOrderId() == null && !validateNewOrderIdentity(true)) {
            return;
        }
        JButton target = selectedOrderId() == null ? createOrderBtn : addOrderLineBtn;
        if (target != null && target.isEnabled()) {
            target.doClick();
            return;
        }
        updateOrderLinePreview();
        if (revealFirstOrderEntryProblem()) {
            return;
        }
        status("Product submission is currently unavailable. Wait for the current refresh or "
                + "select an editable Draft or Confirmed order.");
        FarmUi.revealAndFocus(selectedOrderId() == null ? orderCustomer : orderTable);
    }

    private boolean blockPendingOrderProductKeyboardRetry() {
        if (blockPendingProductWriteRetry()
                || blockPendingDestructiveSuccessRetry()
                || blockPendingCorrectionSuccessRetry()) {
            return true;
        }
        if (pendingAddedProductOrderId != null) {
            status(pendingAddedProductRecovery());
            return true;
        }
        if (pendingCreatedOrderSelectionId != null) {
            status(pendingCreatedOrderRecovery());
            return true;
        }
        if (pendingConfirmationSuccess != null) {
            status(confirmationReloadRecovery(pendingConfirmationSuccess));
            return true;
        }
        if (pendingFulfillmentSuccess != null) {
            status(fulfillmentReloadRecovery(pendingFulfillmentSuccess));
            return true;
        }
        return false;
    }

    private boolean revealFirstOrderEntryProblem() {
        JComboBox<CrmPresenter.CustomerChoice> customerControl = orderCustomer;
        if (selectedOrderId() == null
                && (customerControl == null || customerControl.getSelectedItem() == null)) {
            String message = "Add or select a customer before creating this order.";
            if (customerControl != null) {
                customerControl.setToolTipText(message);
                customerControl.getAccessibleContext().setAccessibleDescription(message);
                FarmUi.revealAndFocus(customerControl);
            }
            status(message);
            return true;
        }
        try {
            presenter.validateOrderLine(currentOrderLineDraft());
            return false;
        } catch (CrmPresenter.InputValidationException validation) {
            clearOrderValidation();
            markOrderError(validation);
            status(validation.getMessage() + " The first value needing attention is highlighted "
                    + "and ready for correction; press Ctrl+Enter to retry.");
            return true;
        }
    }

    private void cycleOrderWorkflowFocus() {
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        cycleOrderWorkflowFocus(focus);
    }

    void cycleOrderWorkflowFocus(Component focus) {
        boolean tableFocused = focus == orderTable || (focus != null && orderTable != null
                && SwingUtilities.isDescendingFrom(focus, orderTable));
        boolean tableAvailable = orderTable != null && orderTable.isEnabled()
                && orderTable.isVisible() && orderTable.getRowCount() > 0;
        boolean productAvailable = orderProduct != null && orderProduct.isEnabled()
                && orderProduct.isVisible();
        if (tableFocused && productAvailable) {
            revealOrderWorkflowFocus(orderProduct,
                    "Keyboard focus moved to Product entry. Review the current line; "
                            + "Ctrl+Enter submits it when ready.");
            return;
        }
        if (tableFocused && tableAvailable) {
            revealOrderWorkflowFocus(orderTable,
                    "Product entry is temporarily locked by the current " + orderFocusLockReason()
                            + ". Keyboard focus remains on the order list; wait for the result, "
                            + "then press F6 again.");
            return;
        }
        if (tableAvailable) {
            revealOrderWorkflowFocus(orderTable,
                    "Keyboard focus moved to the order list. Use the arrow keys to choose an order; "
                            + "press F6 to return to Product entry.");
            return;
        }
        if (productAvailable) {
            String reason = orderTable != null && orderTable.getRowCount() > 0
                    ? "The order list is temporarily locked by the current " + orderFocusLockReason()
                            + ". Keyboard focus moved to Product entry; wait for the result before submitting."
                    : "The order list is empty. Keyboard focus moved to Product entry; complete the "
                            + "new-order fields and press Ctrl+Enter when ready.";
            revealOrderWorkflowFocus(orderProduct,
                    reason);
            return;
        }
        status("F6 focus did not move because the order list and Product entry are locked by the "
                + "current " + orderFocusLockReason() + ". Wait for its result, then press F6 again.");
    }

    private String orderFocusLockReason() {
        if (refreshingCrm) {
            return "CRM refresh";
        }
        if (creatingOrder || addingOrderLine || runningOrderMutation || fulfillingOrder) {
            return "order save";
        }
        return "order operation";
    }

    private void revealOrderWorkflowFocus(JComponent target, String announcement) {
        FarmUi.revealAndFocus(target);
        status(announcement);
    }

    private void runOrderTool(String action) {
        if (action == null) {
            return;
        }
        switch (action) {
            case "Save invoice" -> exportInvoicePdf();
            case "Export filtered orders" -> exportOrdersCsv(true);
            case "Export all orders" -> exportOrdersCsv(false);
            case "Cancel selected order" -> cancelOrder();
            case "Delete selected order" -> deleteOrder();
            default -> {
                // The action list is fixed.
            }
        }
    }

    private void updateCustomerFormMode() {
        if (addCustomerBtn == null || saveCustomerBtn == null) {
            return;
        }
        boolean editing = editingCustomerId != null;
        boolean selected = customerTable != null && customerTable.getSelectedRow() >= 0;
        boolean current = customerFormCurrent();
        boolean authoritative = current && crmRefreshFailure == null;
        boolean writable = customerAccessAllowed();
        addCustomerBtn.setEnabled(writable && authoritative && !editing);
        saveCustomerBtn.setEnabled(writable && authoritative && editing);
        loadCustomerBtn.setEnabled(writable && current && selected);
        clearCustomerBtn.setEnabled(writable && current);
        deleteCustomerBtn.setEnabled(writable && authoritative && selected);
        statementCustomerBtn.setEnabled(current && selected && !runningDocument);
        if (crmRefreshFailure != null) {
            String guidance = "Customer details are retained for review. Refresh CRM successfully "
                    + "before adding, saving, or deleting an account.";
            for (JButton action : List.of(addCustomerBtn, saveCustomerBtn, deleteCustomerBtn)) {
                action.setToolTipText(guidance);
                action.getAccessibleContext().setAccessibleDescription(guidance);
            }
        } else {
            String addGuidance = "Create a new customer account from this form.";
            String saveGuidance = "Update the loaded customer (double-click a row to load it).";
            String deleteGuidance = "Delete the selected customer account after review.";
            addCustomerBtn.setToolTipText(addGuidance);
            addCustomerBtn.getAccessibleContext().setAccessibleDescription(addGuidance);
            saveCustomerBtn.setToolTipText(saveGuidance);
            saveCustomerBtn.getAccessibleContext().setAccessibleDescription(saveGuidance);
            deleteCustomerBtn.setToolTipText(deleteGuidance);
            deleteCustomerBtn.getAccessibleContext().setAccessibleDescription(deleteGuidance);
        }
        boolean hasConflict = conflictingCustomerId != null;
        reviewCustomerConflictBtn.setVisible(hasConflict);
        reviewCustomerConflictBtn.setEnabled(hasConflict && current);
        if (!savingCustomer && !deletingCustomer && pendingCustomerWriteSuccess == null
                && pendingOpenCustomerRequest == null
                && pendingOpenCustomerPresentation == null) {
            FarmUi.updateStatusBadge(customerModeLabel,
                    editing ? "Editing “" + custName.getText().trim() + "”" : "New customer",
                    editing ? FarmUi.Tone.WARNING : FarmUi.Tone.INFO);
        }
    }

    private void orderSelectionChanged() {
        Long previousTarget = activeProductEntryOrderId;
        boolean previousDraftSaved = rememberCurrentProductDraft();
        Long nextTarget = selectedOrderId();
        if (Objects.equals(previousTarget, nextTarget)) {
            updateOrderActions();
            return;
        }
        activeProductEntryOrderId = nextTarget;
        ProductEntryDraft restored = storedProductDraft(nextTarget);
        applyProductEntryDraft(restored);
        updateOrderActions();
        if (refreshingCrm) {
            return;
        }
        if (restored != null) {
            status(nextTarget == null
                    ? "Restored the unfinished new-order product entry. It remains separate from saved orders."
                    : "Restored the unfinished product entry for order #" + nextTarget
                            + ". It remains bound to this order until saved or cleared.");
        } else if (previousDraftSaved && previousTarget != null) {
            status("Paused the unfinished product entry for order #" + previousTarget
                    + ". The newly selected target starts with its own product entry.");
        }
    }

    private boolean rememberCurrentProductDraft() {
        if (orderProduct == null) {
            return false;
        }
        Long lineId = editingOrderId != null
                && Objects.equals(editingOrderId, activeProductEntryOrderId)
                ? editingOrderLineId : null;
        ProductEntryDraft previous = storedProductDraft(activeProductEntryOrderId);
        OrderService.OrderReviewSnapshot reviewedOrder = lineId == null
                ? previous == null
                        ? reviewedOrderSnapshot(activeProductEntryOrderId)
                        : previous.reviewedOrder()
                : editingOrderReview;
        ProductEntryDraft draft = new ProductEntryDraft(
                orderProduct.getText(), orderQty.getText(),
                orderUnit.getText(), orderPrice.getText(), lineId,
                lineId == null ? null : editingOrderLineReview, reviewedOrder);
        if (!draft.hasWork(defaultUnit)) {
            clearStoredProductDraft(activeProductEntryOrderId);
            return false;
        }
        if (activeProductEntryOrderId == null) {
            newOrderProductDraft = draft;
        } else {
            orderProductDrafts.put(activeProductEntryOrderId, draft);
        }
        return true;
    }

    private ProductEntryDraft storedProductDraft(Long orderId) {
        return orderId == null ? newOrderProductDraft : orderProductDrafts.get(orderId);
    }

    private OrderService.OrderReviewSnapshot reviewedOrderSnapshot(Long orderId) {
        if (orderId == null) {
            return null;
        }
        return lastOrderView.stream()
                .filter(order -> orderId.equals(order.getId()))
                .findFirst()
                .map(presenter::reviewSnapshot)
                .orElse(null);
    }

    private ProductOrderConflict productOrderConflict(Long orderId) {
        if (orderId == null) {
            return null;
        }
        ProductEntryDraft draft = orderProductDrafts.get(orderId);
        if (draft == null || !draft.hasWork(defaultUnit) || draft.editingLineId() != null
                || draft.reviewedOrder() == null) {
            return null;
        }
        OrderService.OrderReviewSnapshot current = reviewedOrderSnapshot(orderId);
        return current == null || current.equals(draft.reviewedOrder())
                ? null
                : new ProductOrderConflict(orderId, draft.reviewedOrder(), current);
    }

    private String productOrderConflictGuidance(ProductOrderConflict conflict) {
        return "Order #" + conflict.orderId()
                + " changed after this product entry began. Originally reviewed: "
                + orderReviewSummary(conflict.reviewed()) + ". Current saved order: "
                + orderReviewSummary(conflict.current()) + ". Your product draft is “"
                + compactContext(orderProduct.getText().trim(), 100) + "” × "
                + compactContext(orderQty.getText().trim(), 40)
                + ". Review the changed order first; acknowledging does not add the product "
                + "or change inventory.";
    }

    private static String orderReviewSummary(OrderService.OrderReviewSnapshot review) {
        String products = review.lines().isEmpty()
                ? "no saved products"
                : review.lines().stream()
                        .map(line -> editableNumber(line.quantity()) + " " + line.unit() + " "
                                + line.productName())
                        .collect(java.util.stream.Collectors.joining(", "));
        String contact = review.contactName().isBlank()
                ? review.phone().isBlank() ? review.email() : review.phone()
                : review.contactName();
        return "customer “" + compactContext(review.customerName(), 80) + "”"
                + (contact.isBlank() ? "" : " / " + compactContext(contact, 80))
                + ", " + review.orderDate() + ", "
                + friendlyHandoffType(review.handoffType()) + ", " + review.status()
                + ", instructions “" + compactContext(review.notes(), 100) + "”, products ["
                + compactContext(products, 220) + "]";
    }

    private void reviewProductOrderConflict(ProductOrderConflict conflict) {
        String guidance = productOrderConflictGuidance(conflict);
        int choice = panel.isShowing()
                ? JOptionPane.showConfirmDialog(
                        panel,
                        FarmUi.confirmationPanel(
                                "Review changed order #" + conflict.orderId(),
                                guidance + "\n\nChoose Yes only to acknowledge the current saved "
                                        + "order. Your product remains unsaved; choose Add product "
                                        + "again only if it belongs on this changed order.",
                                FarmUi.Tone.WARNING),
                        "Review changed order before adding",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE)
                : JOptionPane.YES_OPTION;
        if (choice != JOptionPane.YES_OPTION) {
            status("Changed-order review cancelled. The product draft remains on order #"
                    + conflict.orderId() + " and nothing was saved.");
            return;
        }
        rememberCurrentProductDraft();
        ProductEntryDraft draft = orderProductDrafts.get(conflict.orderId());
        if (draft != null) {
            orderProductDrafts.put(
                    conflict.orderId(), draft.withReviewedOrder(conflict.current()));
        }
        updateOrderActions();
        String message = "Order #" + conflict.orderId()
                + " changes reviewed · product still unsaved";
        String recovery = "The current customer, date, handoff, instructions, and saved products "
                + "were acknowledged. Nothing was added and inventory was unchanged. Choose Add "
                + "product to selected order only if this preserved product belongs on the "
                + "changed order.";
        FarmUi.updateStatusBadge(selectedOrderLabel, message, FarmUi.Tone.WARNING);
        selectedOrderLabel.setToolTipText(recovery);
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(recovery);
        status(recovery);
    }

    private void clearStoredProductDraft(Long orderId) {
        if (orderId == null) {
            newOrderProductDraft = null;
        } else {
            orderProductDrafts.remove(orderId);
        }
    }

    private void applyProductEntryDraft(ProductEntryDraft draft) {
        ProductEntryDraft value = draft == null ? ProductEntryDraft.empty(defaultUnit) : draft;
        editingOrderId = value.editingLineId() == null ? null : activeProductEntryOrderId;
        editingOrderLineId = value.editingLineId();
        editingOrderLineReview = value.reviewedLine();
        editingOrderReview = value.editingLineId() == null ? null : value.reviewedOrder();
        if (orderInventoryProduct != null) {
            orderInventoryProduct.setSelectedItem(null);
        }
        orderProduct.setText(value.product());
        orderQty.setText(value.quantity());
        orderUnit.setText(value.unit());
        orderPrice.setText(value.unitPrice());
        clearOrderValidation();
    }

    private void updateOrderActions() {
        if (createOrderBtn == null || orderTable == null) {
            return;
        }
        int row = orderTable.getSelectedRow();
        boolean unavailableTarget = row < 0 && unavailableProductDraftOrderId != null;
        boolean createdOrderPending = pendingCreatedOrderSelectionId != null;
        boolean addedProductPending = pendingAddedProductOrderId != null;
        boolean productWritePending = pendingProductWriteSuccess != null;
        boolean destructiveSuccessPending = pendingDestructiveSuccess != null;
        boolean correctionSuccessPending = pendingCorrectionSuccess != null;
        boolean exactOrderReloadPending = createdOrderPending || addedProductPending
                || productWritePending || destructiveSuccessPending || correctionSuccessPending;
        Long currentOrderId = row < 0 ? null : (Long) orderTable.getValueAt(row, 0);
        String status = row < 0 ? "" : String.valueOf(orderTable.getValueAt(row, 3));
        OrderStockAssessment stockAssessment = selectedOrderStockAssessment(row);
        boolean stockBlocked = stockAssessment.available()
                && !stockAssessment.gaps().isEmpty();
        boolean orderWriteEnabled = canWrite && !creatingOrder && !addingOrderLine
                && !runningOrderMutation
                && !fulfillingOrder && !refreshingCrm && crmRefreshFailure == null
                && !exactOrderReloadPending;
        if (reviewOrderCustomerBtn != null) {
            reviewOrderCustomerBtn.setEnabled(row >= 0 && !creatingOrder && !addingOrderLine
                    && !runningOrderMutation && !fulfillingOrder && !refreshingCrm);
        }
        if (openNewOrderCustomerBtn != null) {
            openNewOrderCustomerBtn.setEnabled(orderCustomer.getSelectedItem() != null
                    && !creatingOrder && !addingOrderLine && !runningOrderMutation
                    && !fulfillingOrder && !refreshingCrm && !exactOrderReloadPending);
        }
        boolean editingLine = editingOrderLineId != null
                && editingOrderId != null && editingOrderId.equals(currentOrderId);
        boolean pendingProductEntry = row >= 0 && hasPendingProductEntry();
        boolean lineMutable = "DRAFT".equalsIgnoreCase(status)
                || "CONFIRMED".equalsIgnoreCase(status);
        ProductOrderConflict productConflict = editingLine || !lineMutable
                ? null : productOrderConflict(currentOrderId);
        if (duplicateLineApproval != null
                && !duplicateLineApprovalMatches(currentOrderId)) {
            duplicateLineApproval = null;
        }
        if (productEntryClearApproval != null && !productEntryClearApprovalMatches()) {
            productEntryClearApproval = null;
        }
        if (newOrderIdentityClearApproval != null
                && !newOrderIdentityClearApprovalMatches()) {
            newOrderIdentityClearApproval = null;
        }
        if (earlyCompletionApproval != null
                && !earlyCompletionApprovalMatches(currentOrderId)) {
            earlyCompletionApproval = null;
        }
        if (overdueCompletionApproval != null
                && !overdueCompletionApprovalMatches(currentOrderId)) {
            overdueCompletionApproval = null;
        }
        int savedProductCount = row < 0 ? 0 : savedProductCount(row);
        updateNewOrderIdentityAvailability(orderWriteEnabled,
                row < 0 && !unavailableTarget && !createdOrderPending);
        updateProductEntryAvailability(orderWriteEnabled,
                row, currentOrderId, status,
                lineMutable, unavailableTarget, pendingProductEntry);
        if (!addingOrderLine) {
            addOrderLineBtn.setText(editingLine
                    ? "Save product changes"
                    : duplicateLineApproval != null
                            ? "Add separate product line"
                    : productConflict == null
                            ? "Add product to selected order"
                            : "Review changed order before adding");
        }
        newOrderIdentityValid = validateNewOrderIdentity(false);
        createOrderBtn.setEnabled(orderWriteEnabled && row < 0 && !unavailableTarget
                && !createdOrderPending
                && orderCustomer != null
                && orderCustomer.getItemCount() > 0 && orderLineValid
                && newOrderIdentityValid);
        addOrderLineBtn.setEnabled(orderWriteEnabled && row >= 0
                && orderLineValid
                && ("DRAFT".equalsIgnoreCase(status) || "CONFIRMED".equalsIgnoreCase(status)));
        if (productConflict != null) {
            String guidance = productOrderConflictGuidance(productConflict);
            addOrderLineBtn.setToolTipText(guidance);
            addOrderLineBtn.getAccessibleContext().setAccessibleDescription(guidance);
        } else {
            String guidance = duplicateLineApproval != null
                    ? "Choose again only to approve this unchanged product as a separate saved "
                            + "line on order #" + currentOrderId + "."
                    : editingLine
                    ? "Save this correction to the selected saved product. Inventory does not change."
                    : currentOrderId == null
                            ? "Select a draft or confirmed order first."
                            : "Add the entered product to order #" + currentOrderId + ".";
            addOrderLineBtn.setToolTipText(guidance);
            addOrderLineBtn.getAccessibleContext().setAccessibleDescription(guidance);
        }
        if (startAnotherOrderBtn != null) {
            boolean immutableDraft = row >= 0 && !lineMutable && pendingProductEntry;
            startAnotherOrderBtn.setText(unavailableTarget || immutableDraft
                    ? "Move product to new order" : "Start another order");
            startAnotherOrderBtn.setToolTipText(unavailableTarget
                    ? "Explicitly move this preserved product from its missing order into the "
                            + "separate new-order draft."
                    : immutableDraft
                    ? "Move this preserved product out of the read-only " + status
                            + " order and into the separate new-order draft."
                    : "Pause any unfinished selected-order product entry and switch to the "
                            + "separate new-order draft.");
            startAnotherOrderBtn.getAccessibleContext().setAccessibleDescription(
                    startAnotherOrderBtn.getToolTipText());
            startAnotherOrderBtn.setEnabled(orderWriteEnabled
                    && (row >= 0 || unavailableTarget));
        }
        if (resetNewOrderDetailsBtn != null) {
            boolean identityChanged = hasUnfinishedNewOrderIdentity();
            resetNewOrderDetailsBtn.setEnabled(orderWriteEnabled && row < 0
                    && !unavailableTarget && identityChanged);
            resetNewOrderDetailsBtn.setText(newOrderIdentityClearApproval == null
                    ? "Reset new-order details" : "Discard new-order changes");
            String guidance = newOrderIdentityClearApproval == null
                    ? "Review before restoring the accepted customer, date, and handoff and "
                            + "clearing instructions. Product entry stays unchanged."
                    : "Choose again only to discard these unchanged customer, date, handoff, "
                            + "and instruction edits. Product entry stays unchanged.";
            resetNewOrderDetailsBtn.setToolTipText(guidance);
            resetNewOrderDetailsBtn.getAccessibleContext().setAccessibleDescription(guidance);
        }
        updateSelectedOrderContext(row, status, stockAssessment);
        OrderLineChoice selectedLine = selectedOrderLine == null
                ? null : (OrderLineChoice) selectedOrderLine.getSelectedItem();
        boolean selectedDateDirty = selectedOrderDateOrderId != null
                && selectedOrderDateOrderId.equals(currentOrderId)
                && !selectedOrderDate.getText().trim().equals(selectedOrderDateBaseline);
        boolean selectedDateValid = false;
        if (selectedOrderDateOrderId != null && selectedOrderDateOrderId.equals(currentOrderId)) {
            try {
                presenter.validatedOrderDate(selectedOrderDate.getText());
                FarmUi.clearFieldError(selectedOrderDate);
                selectedDateValid = true;
            } catch (CrmPresenter.InputValidationException exception) {
                FarmUi.markFieldError(selectedOrderDate, exception.getMessage());
            }
        } else {
            FarmUi.clearFieldError(selectedOrderDate);
        }
        selectedOrderDate.setEnabled(orderWriteEnabled && lineMutable);
        boolean selectedDateConflict = currentOrderId != null
                && selectedOrderDateConflicts.containsKey(currentOrderId);
        if (!runningOrderMutation) {
            saveOrderDateBtn.setText(selectedDateConflict
                    ? "Review newer saved date" : "Save order date");
        }
        saveOrderDateBtn.setEnabled(orderWriteEnabled && lineMutable
                && selectedDateDirty && selectedDateValid);
        if (selectedDateConflict) {
            String guidance = correctionConflictGuidance(
                    "date", currentOrderId, selectedOrderDate.getText().trim(),
                    selectedOrderDateConflicts.get(currentOrderId));
            saveOrderDateBtn.setToolTipText(guidance);
            saveOrderDateBtn.getAccessibleContext().setAccessibleDescription(guidance);
        } else {
            String guidance = "Save the selected order's pickup, delivery, or market date.";
            saveOrderDateBtn.setToolTipText(guidance);
            saveOrderDateBtn.getAccessibleContext().setAccessibleDescription(guidance);
        }
        boolean selectedHandoffCurrent = selectedOrderHandoffOrderId != null
                && selectedOrderHandoffOrderId.equals(currentOrderId);
        String selectedHandoffValue = selectedOrderHandoffType.getSelectedItem() == null
                ? "Unspecified" : String.valueOf(selectedOrderHandoffType.getSelectedItem());
        boolean selectedHandoffDirty = selectedHandoffCurrent
                && !selectedHandoffValue.equals(selectedOrderHandoffBaseline);
        boolean selectedHandoffSpecified = selectedHandoffCurrent
                && !"Unspecified".equalsIgnoreCase(selectedHandoffValue);
        if (selectedHandoffCurrent && !selectedHandoffSpecified) {
            String guidance = "Choose Pickup, Delivery, or Market before confirming or "
                    + "completing this order.";
            selectedOrderHandoffType.putClientProperty("JComponent.outline", "error");
            selectedOrderHandoffType.setToolTipText(guidance);
            selectedOrderHandoffType.getAccessibleContext().setAccessibleDescription(guidance);
        } else {
            String guidance = "Correct whether the selected order is a pickup, delivery, or "
                    + "market handoff. Unsaved corrections are kept if you inspect another order.";
            selectedOrderHandoffType.putClientProperty("JComponent.outline", null);
            selectedOrderHandoffType.setToolTipText(guidance);
            selectedOrderHandoffType.getAccessibleContext().setAccessibleDescription(guidance);
        }
        selectedOrderHandoffType.setEnabled(orderWriteEnabled && lineMutable);
        boolean selectedHandoffConflict = currentOrderId != null
                && selectedOrderHandoffConflicts.containsKey(currentOrderId);
        if (!runningOrderMutation) {
            saveOrderHandoffBtn.setText(selectedHandoffConflict
                    ? "Review newer saved handoff" : "Save handoff type");
        }
        saveOrderHandoffBtn.setEnabled(orderWriteEnabled && lineMutable
                && selectedHandoffDirty && selectedHandoffSpecified);
        if (selectedHandoffConflict) {
            String guidance = correctionConflictGuidance(
                    "handoff type", currentOrderId, selectedHandoffValue,
                    selectedOrderHandoffConflicts.get(currentOrderId));
            saveOrderHandoffBtn.setToolTipText(guidance);
            saveOrderHandoffBtn.getAccessibleContext().setAccessibleDescription(guidance);
        } else {
            String guidance = "Save the handoff type and refresh invoices, packing lists, and Market Day.";
            saveOrderHandoffBtn.setToolTipText(guidance);
            saveOrderHandoffBtn.getAccessibleContext().setAccessibleDescription(guidance);
        }
        boolean selectedNotesCurrent = selectedOrderNotesOrderId != null
                && selectedOrderNotesOrderId.equals(currentOrderId);
        String selectedNotesValue = selectedOrderNotes.getText().trim();
        boolean selectedNotesDirty = selectedNotesCurrent
                && !selectedNotesValue.equals(selectedOrderNotesBaseline);
        boolean selectedNotesValid = selectedNotesValue.length() <= 2000;
        if (!selectedNotesValid) {
            FarmUi.markFieldError(selectedOrderNotes,
                    "Order instructions cannot exceed 2000 characters.");
        } else {
            FarmUi.clearFieldError(selectedOrderNotes);
        }
        selectedOrderNotes.setEnabled(orderWriteEnabled && lineMutable);
        boolean selectedNotesConflict = currentOrderId != null
                && selectedOrderNotesConflicts.containsKey(currentOrderId);
        if (!runningOrderMutation) {
            saveOrderNotesBtn.setText(selectedNotesConflict
                    ? "Review newer saved instructions" : "Save order instructions");
        }
        saveOrderNotesBtn.setEnabled(orderWriteEnabled && lineMutable
                && selectedNotesDirty && selectedNotesValid);
        if (selectedNotesConflict) {
            String guidance = correctionConflictGuidance(
                    "instructions", currentOrderId, selectedNotesValue,
                    selectedOrderNotesConflicts.get(currentOrderId));
            saveOrderNotesBtn.setToolTipText(guidance);
            saveOrderNotesBtn.getAccessibleContext().setAccessibleDescription(guidance);
        } else {
            String guidance = "Save these instructions and refresh invoices, packing lists, and farm handoffs.";
            saveOrderNotesBtn.setToolTipText(guidance);
            saveOrderNotesBtn.getAccessibleContext().setAccessibleDescription(guidance);
        }
        applyCorrectionConflictGuidance(currentOrderId);
        if (productConflict != null) {
            String message = "Order #" + currentOrderId
                    + " changed · review before adding preserved product";
            String guidance = productOrderConflictGuidance(productConflict);
            addOrderLineBtn.setToolTipText(guidance);
            addOrderLineBtn.getAccessibleContext().setAccessibleDescription(guidance);
            FarmUi.updateStatusBadge(selectedOrderLabel, message, FarmUi.Tone.WARNING);
            selectedOrderLabel.setToolTipText(guidance);
            selectedOrderLabel.getAccessibleContext().setAccessibleDescription(guidance);
        }
        if (selectedOrderLine != null) {
            selectedOrderLine.setEnabled(row >= 0 && selectedOrderLine.getItemCount() > 0
                    && !runningOrderMutation && !fulfillingOrder && !refreshingCrm);
        }
        if (removeOrderLineBtn != null) {
            removeOrderLineBtn.setEnabled(orderWriteEnabled && lineMutable
                    && !editingLine && selectedLine != null && selectedLine.id() != null);
        }
        if (editOrderLineBtn != null) {
            editOrderLineBtn.setEnabled(orderWriteEnabled && lineMutable
                    && selectedLine != null && selectedLine.id() != null);
        }
        if (cancelOrderLineEditBtn != null) {
            cancelOrderLineEditBtn.setEnabled(orderWriteEnabled
                    && (editingLine || hasPendingProductEntry()));
            cancelOrderLineEditBtn.setText(productEntryClearApproval != null
                    ? "Discard product entry" : "Clear product entry");
            String clearGuidance = productEntryClearApproval != null
                    ? "Choose again only to discard this unchanged product entry."
                    : editingLine
                            ? "Review before discarding unsaved product corrections."
                            : "Review before clearing the unsaved product entry.";
            cancelOrderLineEditBtn.setToolTipText(clearGuidance);
            cancelOrderLineEditBtn.getAccessibleContext().setAccessibleDescription(
                    clearGuidance);
        }
        CustomerOrder selectedOrder = currentOrderId == null ? null : lastOrderView.stream()
                .filter(order -> currentOrderId.equals(order.getId()))
                .findFirst().orElse(null);
        boolean deliveryContextMissing = selectedOrder != null
                && "DELIVERY".equals(CustomerOrder.normalizeHandoffType(
                        selectedOrder.getHandoffType()))
                && !hasDeliveryContext(selectedOrder);
        OrderLifecycleReadiness lifecycleReadiness = orderLifecycleReadiness(
                pendingProductEntry, editingLine, selectedDateDirty, selectedDateValid,
                selectedHandoffDirty, selectedHandoffSpecified,
                selectedNotesDirty, selectedNotesValid,
                savedProductCount > 0, deliveryContextMissing);
        confirmOrderBtn.setEnabled(orderWriteEnabled && pendingConfirmationFailure == null
                && pendingConfirmationSuccess == null
                && lifecycleReadiness.ready()
                && "DRAFT".equalsIgnoreCase(status));
        fulfillOrderBtn.setEnabled(orderWriteEnabled && lifecycleReadiness.ready()
                && !stockBlocked && pendingFulfillmentFailure == null
                && pendingFulfillmentSuccess == null
                && "CONFIRMED".equalsIgnoreCase(status));
        if (!fulfillingOrder) {
            fulfillOrderBtn.setText(earlyCompletionApproval != null
                    ? "Review early completion"
                    : overdueCompletionApproval != null
                            ? "Review overdue completion" : "Complete order");
        }
        if (selectedOrder != null && "CONFIRMED".equalsIgnoreCase(status)
                && selectedOrder.getOrderDate().isAfter(LocalDate.now())
                && lifecycleReadiness.ready() && !stockBlocked) {
            String guidance = earlyCompletionApproval != null
                    ? "Review order #" + currentOrderId + " again before opening the early "
                            + "completion dialog for " + selectedOrder.getOrderDate() + "."
                    : "Order #" + currentOrderId + " is scheduled for "
                            + selectedOrder.getOrderDate() + ". Completion will pause before the "
                            + "final dialog so an early physical handoff must be explicit.";
            fulfillOrderBtn.setToolTipText(guidance);
            fulfillOrderBtn.getAccessibleContext().setAccessibleDescription(guidance);
        } else if (selectedOrder != null && "CONFIRMED".equalsIgnoreCase(status)
                && selectedOrder.getOrderDate().isBefore(LocalDate.now())
                && lifecycleReadiness.ready() && !stockBlocked) {
            String guidance = overdueCompletionApproval != null
                    ? "Review order #" + currentOrderId + " again before opening the overdue "
                            + "completion dialog for " + selectedOrder.getOrderDate() + "."
                    : "Order #" + currentOrderId + " was scheduled for "
                            + selectedOrder.getOrderDate() + ". Completion will pause before the "
                            + "final dialog so an old order is not mistaken for today's handoff.";
            fulfillOrderBtn.setToolTipText(guidance);
            fulfillOrderBtn.getAccessibleContext().setAccessibleDescription(guidance);
        }
        applyLifecycleReadinessGuidance(row, currentOrderId, status, lifecycleReadiness);
        if (runOrderToolBtn != null && orderTools != null) {
            String tool = String.valueOf(orderTools.getSelectedItem());
            boolean selectionRequired = !"Export filtered orders".equals(tool)
                    && !"Export all orders".equals(tool);
            boolean destructiveTool = "Cancel selected order".equals(tool)
                    || "Delete selected order".equals(tool);
            String destructiveLifecycleGuidance = destructiveTool && currentOrderId != null
                    ? destructiveLifecycleGuidance(currentOrderId, tool, status)
                    : null;
            String destructiveDraftGuidance = destructiveTool && currentOrderId != null
                    ? destructiveDraftGuidance(
                            currentOrderId, tool, pendingProductEntry,
                            selectedDateDirty, selectedHandoffDirty, selectedNotesDirty)
                    : null;
            boolean documentReady = !fulfillingOrder && !runningOrderMutation
                    && !refreshingCrm && !runningDocument;
            orderTools.setEnabled(documentReady);
            runOrderToolBtn.setEnabled(documentReady
                    && (!destructiveTool || pendingDestructiveFailure == null)
                    && destructiveLifecycleGuidance == null
                    && destructiveDraftGuidance == null
                    && (!selectionRequired || row >= 0));
            String localDestructiveBlock = destructiveLifecycleGuidance != null
                    ? destructiveLifecycleGuidance : destructiveDraftGuidance;
            if (localDestructiveBlock != null && pendingDestructiveFailure == null) {
                runOrderToolBtn.setToolTipText(localDestructiveBlock);
                runOrderToolBtn.getAccessibleContext().setAccessibleDescription(
                        localDestructiveBlock);
            } else if (pendingDestructiveFailure == null) {
                String guidance = destructiveTool
                        ? "Open an exact saved-order consequence review before running " + tool + "."
                        : "Run the selected order action.";
                runOrderToolBtn.setToolTipText(guidance);
                runOrderToolBtn.getAccessibleContext().setAccessibleDescription(guidance);
            }
        }
        applyPendingCreatedOrderGuidance(row);
        applyPendingAddedProductGuidance(row);
        applyPendingProductWriteGuidance();
        applyPendingDestructiveSuccessGuidance();
        applyPendingCorrectionSuccessGuidance();
        updateOrderModePresentation(row, unavailableTarget, orderWriteEnabled);
    }

    /** Shows only the controls that belong to the active new- or saved-order workflow. */
    private void updateOrderModePresentation(int selectedRow, boolean unavailableTarget,
                                             boolean orderWriteEnabled) {
        boolean newOrderMode = selectedRow < 0;
        boolean presentationChanged = false;
        if (newOrderSetupPanel != null) {
            presentationChanged |= newOrderSetupPanel.isVisible() != newOrderMode;
            newOrderSetupPanel.setVisible(newOrderMode);
        }
        if (selectedOrderReviewPanel != null) {
            presentationChanged |= selectedOrderReviewPanel.isVisible() == newOrderMode;
            selectedOrderReviewPanel.setVisible(!newOrderMode);
        }
        presentationChanged |= setOrderModeVisible(createOrderBtn, newOrderMode);
        presentationChanged |= setOrderModeVisible(resetNewOrderDetailsBtn, newOrderMode);
        presentationChanged |= setOrderModeVisible(addOrderLineBtn, !newOrderMode);
        presentationChanged |= setOrderModeVisible(confirmOrderBtn, !newOrderMode);
        presentationChanged |= setOrderModeVisible(fulfillOrderBtn, !newOrderMode);
        presentationChanged |= setOrderModeVisible(
                startAnotherOrderBtn, !newOrderMode || unavailableTarget);
        if (focusNewOrderBtn != null) {
            focusNewOrderBtn.setText(newOrderMode ? "New order open" : "New order");
            focusNewOrderBtn.setEnabled(orderWriteEnabled
                    && (!newOrderMode || unavailableTarget));
            String guidance = newOrderMode && !unavailableTarget
                    ? "The new-order workspace is already open below."
                    : "Pause selected-order entry safely and open the separate new-order workspace.";
            focusNewOrderBtn.setToolTipText(guidance);
            focusNewOrderBtn.getAccessibleContext().setAccessibleDescription(guidance);
        }
        if (presentationChanged && panel != null) {
            panel.revalidate();
            panel.repaint();
        }
    }

    private static boolean setOrderModeVisible(JComponent component, boolean visible) {
        if (component == null) {
            return false;
        }
        boolean changed = component.isVisible() != visible;
        component.setVisible(visible);
        return changed;
    }

    private void applyPendingCreatedOrderGuidance(int selectedRow) {
        if (pendingCreatedOrderSelectionId == null || refreshingCrm || selectedRow >= 0) {
            return;
        }
        boolean refreshFailed = crmRefreshFailure != null;
        String recovery = pendingCreatedOrderRecovery();
        FarmUi.updateStatusBadge(selectedOrderLabel,
                "Order #" + pendingCreatedOrderSelectionId + " saved once · "
                        + (refreshFailed
                        ? "exact-order reload failed" : "outside exact view"),
                FarmUi.Tone.WARNING);
        selectedOrderLabel.setToolTipText(recovery);
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(recovery);
        createOrderBtn.setToolTipText(recovery);
        createOrderBtn.getAccessibleContext().setAccessibleDescription(recovery);
        for (JComponent component : List.of(
                orderCustomer, orderDate, orderHandoffType, orderNotes,
                orderInventoryProduct, orderProduct, orderQty, orderUnit, orderPrice)) {
            component.setEnabled(false);
            component.setToolTipText(recovery);
            component.getAccessibleContext().setAccessibleDescription(recovery);
        }
        applyLifecycleRefreshGuidance(recovery, false);
    }

    private String pendingCreatedOrderRecovery() {
        String description = pendingCreatedOrderDescription == null
                ? "Order #" + pendingCreatedOrderSelectionId
                : pendingCreatedOrderDescription;
        return description + " was saved once, but CRM "
                + (crmRefreshFailure != null
                ? "could not reload the exact order. "
                : "did not find it in the exact customer and date view. ")
                + "The submitted order fields were cleared and locked to prevent a duplicate. "
                + "Retry Refresh CRM only; do not create this order again.";
    }

    private void applyPendingAddedProductGuidance(int selectedRow) {
        if (pendingAddedProductOrderId == null || refreshingCrm) {
            return;
        }
        boolean refreshFailed = crmRefreshFailure != null;
        String recovery = pendingAddedProductRecovery();
        FarmUi.updateStatusBadge(selectedOrderLabel,
                refreshFailed
                        ? "Product saved · exact order reload failed"
                        : "Product saved · order #" + pendingAddedProductOrderId
                                + " outside current view",
                FarmUi.Tone.WARNING);
        selectedOrderLabel.setToolTipText(recovery);
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(recovery);
        for (JComponent component : List.of(
                orderInventoryProduct, orderProduct, orderQty, orderUnit, orderPrice,
                createOrderBtn, addOrderLineBtn, confirmOrderBtn, fulfillOrderBtn,
                startAnotherOrderBtn, editOrderLineBtn, removeOrderLineBtn,
                saveOrderDateBtn, saveOrderHandoffBtn, saveOrderNotesBtn, runOrderToolBtn)) {
            component.setEnabled(false);
            component.setToolTipText(recovery);
            component.getAccessibleContext().setAccessibleDescription(recovery);
        }
        applyLifecycleRefreshGuidance(recovery, false);
    }

    private String pendingAddedProductRecovery() {
        boolean refreshFailed = crmRefreshFailure != null;
        String productName = pendingAddedProductName == null
                ? "The product" : "“" + pendingAddedProductName + "”";
        return productName + " was saved once to order #"
                + pendingAddedProductOrderId + ", but CRM "
                + (refreshFailed
                ? "could not reload the exact order. "
                : "did not find that order in the current filtered view. ")
                + "The product form was cleared to prevent a duplicate. "
                + (refreshFailed ? "Retry Refresh CRM" : "Choose Show all or adjust filters, then Refresh CRM")
                + "; do not enter the product again.";
    }

    private void applyPendingProductWriteGuidance() {
        if (pendingProductWriteSuccess == null || refreshingCrm) {
            return;
        }
        ProductWriteSuccessPresentation presentation = pendingProductWriteSuccess;
        boolean refreshFailed = crmRefreshFailure != null;
        String recovery = productWriteRecovery(presentation, refreshFailed
                ? "CRM could not reload the exact order. Retry Refresh CRM only"
                : "CRM has not restored the exact order yet. Use Refresh CRM only");
        String message = presentation.removed()
                ? "Product removed once · exact order reload failed"
                : "Product updated once · exact order reload failed";
        showProductWriteReceipt(message, recovery, FarmUi.Tone.WARNING);
        for (JComponent component : List.of(
                orderInventoryProduct, orderProduct, orderQty, orderUnit, orderPrice,
                createOrderBtn, addOrderLineBtn, confirmOrderBtn, fulfillOrderBtn,
                startAnotherOrderBtn, editOrderLineBtn, removeOrderLineBtn,
                saveOrderDateBtn, saveOrderHandoffBtn, saveOrderNotesBtn, runOrderToolBtn)) {
            component.setEnabled(false);
            component.setToolTipText(recovery);
            component.getAccessibleContext().setAccessibleDescription(recovery);
        }
        applyLifecycleRefreshGuidance(recovery, false);
    }

    private void applyProductWriteSuccessPresentation(
            ProductWriteSuccessPresentation presentation,
            boolean refreshSucceeded, boolean targetRestored) {
        String message;
        String recovery;
        String hostStatus;
        FarmUi.Tone tone;
        if (refreshSucceeded && targetRestored) {
            message = presentation.removed()
                    ? "Order #" + presentation.orderId() + " product removed"
                    : "Product updated once · order #" + presentation.orderId() + " refreshed";
            recovery = productWriteRecovery(presentation,
                    "The exact order and its saved products are refreshed");
            hostStatus = presentation.removed()
                    ? presentation.description() + " Inventory was not changed."
                    : "Updated “" + presentation.productName() + "” on order #"
                            + presentation.orderId()
                            + ". Order totals, Market Day, and stock guidance are refreshed; "
                            + "inventory was not changed.";
            tone = FarmUi.Tone.SUCCESS;
        } else if (refreshSucceeded) {
            message = presentation.removed()
                    ? "Product removed once · order outside current view"
                    : "Product updated once · order outside current view";
            recovery = productWriteRecovery(presentation,
                    "CRM refreshed successfully, but the order is outside the current filtered "
                            + "view. Choose Show all or adjust the filters before reviewing it");
            hostStatus = recovery;
            tone = FarmUi.Tone.WARNING;
        } else {
            message = presentation.removed()
                    ? "Product removed once · exact order reload failed"
                    : "Product updated once · exact order reload failed";
            recovery = productWriteRecovery(presentation,
                    "CRM could not reload the exact order. Retry Refresh CRM only");
            hostStatus = recovery;
            tone = FarmUi.Tone.WARNING;
        }
        showProductWriteReceipt(message, recovery, tone);
        applyLifecycleRefreshGuidance(recovery, refreshSucceeded);
        status(hostStatus);
    }

    private void showProductWriteReceipt(String message, String recovery, FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(selectedOrderLabel, message, tone);
        selectedOrderLabel.setToolTipText(recovery);
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(recovery);
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, message, tone);
        fulfillmentStatusLabel.setToolTipText(recovery);
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(recovery);
    }

    private static String productWriteRecovery(ProductWriteSuccessPresentation presentation,
                                               String reloadGuidance) {
        String committed = presentation.removed()
                ? "“" + presentation.productName() + "” was removed once from order #"
                        + presentation.orderId()
                : "Changes to “" + presentation.productName() + "” were saved once on order #"
                        + presentation.orderId();
        String repeat = presentation.removed()
                ? "do not remove this product again."
                : "do not save or re-enter this correction again.";
        return committed + ". Inventory was not changed. " + reloadGuidance + "; " + repeat;
    }

    private boolean blockPendingProductWriteRetry() {
        if (pendingProductWriteSuccess == null) {
            return false;
        }
        status(productWriteRecovery(pendingProductWriteSuccess,
                "CRM has not reloaded the exact saved order. Use Refresh CRM only"));
        return true;
    }

    private void applyPendingDestructiveSuccessGuidance() {
        if (pendingDestructiveSuccess == null || refreshingCrm) {
            return;
        }
        DestructiveSuccessPresentation presentation = pendingDestructiveSuccess;
        String recovery = destructiveSuccessRecovery(presentation,
                "CRM could not reload authoritative order data. Retry Refresh CRM only");
        String message = presentation.action() == OrderMutation.DELETE
                ? "Order #" + presentation.orderId() + " deleted once · CRM reload failed"
                : "Order #" + presentation.orderId() + " cancelled once · CRM reload failed";
        showDestructiveSuccessReceipt(message, recovery, FarmUi.Tone.WARNING);
        for (JComponent component : List.of(
                orderInventoryProduct, orderProduct, orderQty, orderUnit, orderPrice,
                createOrderBtn, addOrderLineBtn, confirmOrderBtn, fulfillOrderBtn,
                startAnotherOrderBtn, editOrderLineBtn, removeOrderLineBtn,
                saveOrderDateBtn, saveOrderHandoffBtn, saveOrderNotesBtn, runOrderToolBtn)) {
            component.setEnabled(false);
            component.setToolTipText(recovery);
            component.getAccessibleContext().setAccessibleDescription(recovery);
        }
        applyLifecycleRefreshGuidance(recovery, false);
    }

    private void applyDestructiveSuccessPresentation(
            DestructiveSuccessPresentation presentation,
            boolean refreshSucceeded, boolean targetRestored) {
        boolean deleting = presentation.action() == OrderMutation.DELETE;
        String message;
        String recovery;
        String hostStatus;
        FarmUi.Tone tone;
        if (refreshSucceeded && deleting) {
            message = "Order #" + presentation.orderId() + " deleted";
            recovery = destructiveSuccessRecovery(presentation,
                    "CRM refreshed and the permanently deleted order is no longer available");
            hostStatus = presentation.description() + " was permanently deleted once. "
                    + "Inventory was not changed, and CRM no longer shows the order.";
            tone = FarmUi.Tone.SUCCESS;
        } else if (refreshSucceeded && targetRestored) {
            message = "Order #" + presentation.orderId() + " cancelled";
            recovery = destructiveSuccessRecovery(presentation,
                    "The refreshed Cancelled history order remains selected");
            hostStatus = presentation.description() + " was cancelled once. Inventory was not "
                    + "changed, and the refreshed Cancelled order remains selected.";
            tone = FarmUi.Tone.SUCCESS;
        } else if (refreshSucceeded) {
            message = "Order #" + presentation.orderId()
                    + " cancelled once · outside current view";
            recovery = destructiveSuccessRecovery(presentation,
                    "CRM refreshed successfully, but the Cancelled order is outside the current "
                            + "filtered view. Choose Show all or Cancelled to review its history");
            hostStatus = recovery;
            tone = FarmUi.Tone.WARNING;
        } else {
            message = "Order #" + presentation.orderId()
                    + (deleting ? " deleted once · CRM reload failed"
                            : " cancelled once · CRM reload failed");
            recovery = destructiveSuccessRecovery(presentation,
                    "CRM could not reload authoritative order data. Retry Refresh CRM only");
            hostStatus = recovery;
            tone = FarmUi.Tone.WARNING;
        }
        showDestructiveSuccessReceipt(message, recovery, tone);
        applyLifecycleRefreshGuidance(recovery, refreshSucceeded);
        status(hostStatus);
    }

    private void showDestructiveSuccessReceipt(
            String message, String recovery, FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, message, tone);
        fulfillmentStatusLabel.setToolTipText(recovery);
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(recovery);
        if (tone != FarmUi.Tone.SUCCESS) {
            FarmUi.updateStatusBadge(selectedOrderLabel, message, tone);
            selectedOrderLabel.setToolTipText(recovery);
            selectedOrderLabel.getAccessibleContext().setAccessibleDescription(recovery);
        }
    }

    private static String destructiveSuccessRecovery(
            DestructiveSuccessPresentation presentation, String reloadGuidance) {
        boolean deleting = presentation.action() == OrderMutation.DELETE;
        String committed = presentation.description()
                + (deleting ? " was permanently deleted once" : " was cancelled once");
        String stale = deleting
                ? "Any retained row is stale and no longer exists. Do not select it or delete it again."
                : "Do not cancel, delete, confirm, or complete it from retained pre-cancellation data.";
        return committed + ". Inventory was not changed. " + reloadGuidance + ". " + stale;
    }

    private boolean blockPendingDestructiveSuccessRetry() {
        if (pendingDestructiveSuccess == null) {
            return false;
        }
        status(destructiveSuccessRecovery(pendingDestructiveSuccess,
                "CRM has not reloaded authoritative order data. Use Refresh CRM only"));
        return true;
    }

    private void applyPendingCorrectionSuccessGuidance() {
        if (pendingCorrectionSuccess == null || refreshingCrm) {
            return;
        }
        CorrectionSuccessPresentation presentation = pendingCorrectionSuccess;
        String recovery = correctionSuccessRecovery(presentation,
                "CRM could not reload the exact order. Retry Refresh CRM only");
        String message = "Order #" + presentation.orderId() + " "
                + correctionField(presentation.action()) + " saved once · CRM reload failed";
        showCorrectionSuccessReceipt(message, recovery, FarmUi.Tone.WARNING);
        for (JComponent component : List.of(
                orderInventoryProduct, orderProduct, orderQty, orderUnit, orderPrice,
                createOrderBtn, addOrderLineBtn, confirmOrderBtn, fulfillOrderBtn,
                startAnotherOrderBtn, editOrderLineBtn, removeOrderLineBtn,
                selectedOrderDate, saveOrderDateBtn,
                selectedOrderHandoffType, saveOrderHandoffBtn,
                selectedOrderNotes, saveOrderNotesBtn, runOrderToolBtn)) {
            component.setEnabled(false);
            component.setToolTipText(recovery);
            component.getAccessibleContext().setAccessibleDescription(recovery);
        }
        applyLifecycleRefreshGuidance(recovery, false);
    }

    private void applyCorrectionSuccessPresentation(
            CorrectionSuccessPresentation presentation,
            boolean refreshSucceeded, boolean targetRestored) {
        String field = correctionField(presentation.action());
        String message;
        String recovery;
        String hostStatus;
        FarmUi.Tone tone;
        if (refreshSucceeded && targetRestored) {
            message = presentation.action().successStatus(presentation.orderId());
            recovery = correctionSuccessRecovery(presentation,
                    "The exact order and saved " + field + " are refreshed");
            hostStatus = presentation.action().successDescription(presentation.description());
            tone = FarmUi.Tone.SUCCESS;
        } else if (refreshSucceeded) {
            message = "Order #" + presentation.orderId() + " " + field
                    + " saved once · outside current view";
            recovery = correctionSuccessRecovery(presentation,
                    "CRM refreshed successfully, but the order is outside the current filtered "
                            + "view. Choose Show all or adjust the filters before reviewing it");
            hostStatus = recovery;
            tone = FarmUi.Tone.WARNING;
        } else {
            message = "Order #" + presentation.orderId() + " " + field
                    + " saved once · CRM reload failed";
            recovery = correctionSuccessRecovery(presentation,
                    "CRM could not reload the exact order. Retry Refresh CRM only");
            hostStatus = recovery;
            tone = FarmUi.Tone.WARNING;
        }
        showCorrectionSuccessReceipt(message, recovery, tone);
        applyLifecycleRefreshGuidance(recovery, refreshSucceeded);
        status(hostStatus);
    }

    private void showCorrectionSuccessReceipt(
            String message, String recovery, FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, message, tone);
        fulfillmentStatusLabel.setToolTipText(recovery);
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(recovery);
        if (tone != FarmUi.Tone.SUCCESS) {
            FarmUi.updateStatusBadge(selectedOrderLabel, message, tone);
            selectedOrderLabel.setToolTipText(recovery);
            selectedOrderLabel.getAccessibleContext().setAccessibleDescription(recovery);
        }
    }

    private static String correctionSuccessRecovery(
            CorrectionSuccessPresentation presentation, String reloadGuidance) {
        String field = correctionField(presentation.action());
        String value = presentation.action() == OrderMutation.SAVE_NOTES
                ? " “" + compactContext(presentation.value(), 160) + "”"
                : " “" + presentation.value() + "”";
        return "Order #" + presentation.orderId() + " " + field + value
                + " was saved once. Inventory was not changed. " + reloadGuidance
                + "; do not save this correction again or use retained pre-save order data.";
    }

    private static String correctionField(OrderMutation action) {
        return switch (action) {
            case SAVE_DATE -> "date";
            case SAVE_HANDOFF -> "handoff";
            case SAVE_NOTES -> "instructions";
            default -> "details";
        };
    }

    private boolean blockPendingCorrectionSuccessRetry() {
        if (pendingCorrectionSuccess == null) {
            return false;
        }
        status(correctionSuccessRecovery(pendingCorrectionSuccess,
                "CRM has not reloaded the exact saved order. Use Refresh CRM only"));
        return true;
    }

    private void updateNewOrderIdentityAvailability(boolean orderWriteEnabled,
                                                    boolean newOrderMode) {
        if (orderCustomer == null || orderDate == null || orderStatus == null
                || orderHandoffType == null
                || orderNotes == null) {
            return;
        }
        boolean enabled = orderWriteEnabled && newOrderMode;
        orderCustomer.setEnabled(enabled);
        orderDate.setEnabled(enabled);
        orderHandoffType.setEnabled(enabled);
        orderStatus.setEnabled(false);
        orderNotes.setEnabled(enabled);
        String guidance = newOrderMode
                ? "Used when Create & select order starts a separate order."
                : "New-order draft paused while a saved order is selected. Choose Start another order to edit it.";
        orderCustomer.setToolTipText(guidance);
        String statusGuidance = newOrderMode
                ? "New desktop orders always start as Draft. Create and select the order, review "
                        + "its saved details, then choose Confirm order."
                : guidance;
        orderStatus.setToolTipText(statusGuidance);
        orderNotes.setToolTipText(guidance);
        orderDate.setToolTipText(newOrderMode
                ? "Pickup, delivery, or market date in YYYY-MM-DD format."
                : guidance);
        for (JComponent component : List.of(
                orderCustomer, orderDate, orderHandoffType, orderNotes)) {
            component.getAccessibleContext().setAccessibleDescription(guidance);
        }
        orderStatus.getAccessibleContext().setAccessibleDescription(statusGuidance);
        updateNewOrderCustomerContext();
    }

    private void updateProductEntryAvailability(
            boolean orderWriteEnabled, int row, Long orderId, String status,
            boolean lineMutable, boolean unavailableTarget, boolean pendingProductEntry) {
        if (orderProduct == null || orderQty == null || orderUnit == null
                || orderPrice == null || orderInventoryProduct == null) {
            return;
        }
        boolean editableTarget = row < 0 ? !unavailableTarget : lineMutable;
        boolean enabled = orderWriteEnabled && editableTarget;
        String guidance;
        if (unavailableTarget) {
            guidance = "This product belongs to an order that is no longer available. Move it to "
                    + "a new order or clear it; it cannot be saved to the missing order.";
        } else if (row >= 0 && !lineMutable) {
            guidance = "Order #" + orderId + " is " + status
                    + " and read-only. Review its saved products below"
                    + (pendingProductEntry
                    ? ", or move this preserved product to a new order or clear it."
                    : ", or choose Start another order to enter a new product.");
        } else if (!canWrite) {
            guidance = "Product entry is read-only for the current role.";
        } else {
            guidance = row < 0
                    ? "Enter the first product for the new-order draft."
                    : "Enter a product for order #" + orderId + ".";
        }
        for (JComponent component : List.of(
                orderInventoryProduct, orderProduct, orderQty, orderUnit, orderPrice)) {
            component.setEnabled(enabled);
            if (!"error".equals(component.getClientProperty("JComponent.outline"))) {
                component.setToolTipText(guidance);
                component.getAccessibleContext().setAccessibleDescription(guidance);
            }
        }
    }

    private void updateNewOrderCustomerContext() {
        if (newOrderCustomerContext == null || orderCustomer == null) {
            return;
        }
        CrmPresenter.CustomerChoice choice =
                (CrmPresenter.CustomerChoice) orderCustomer.getSelectedItem();
        String handoff = orderHandoffType == null || orderHandoffType.getSelectedItem() == null
                ? "" : String.valueOf(orderHandoffType.getSelectedItem());
        String instructions = orderNotes == null ? "" : orderNotes.getText();
        OrderCustomerContext context = newOrderCustomerContext(choice, handoff, instructions);
        boolean paused = orderTable != null && orderTable.getSelectedRow() >= 0;
        String prefix = paused ? "Retained new-order customer (paused)\n" : "";
        String accessiblePrefix = paused
                ? "The retained new-order customer draft is paused while a saved order is selected. "
                : "";
        newOrderCustomerContext.setText(prefix + context.displayText());
        newOrderCustomerContext.setCaretPosition(0);
        newOrderCustomerContext.setToolTipText(accessiblePrefix + context.accessibleText());
        newOrderCustomerContext.getAccessibleContext().setAccessibleDescription(
                accessiblePrefix + context.accessibleText());
        orderCustomer.getAccessibleContext().setAccessibleDescription(
                context.accessibleText());
    }

    static OrderCustomerContext newOrderCustomerContext(CrmPresenter.CustomerChoice choice) {
        return newOrderCustomerContext(choice, "", "");
    }

    static OrderCustomerContext newOrderCustomerContext(
            CrmPresenter.CustomerChoice choice, String handoffType, String orderInstructions) {
        if (choice == null) {
            String empty = "Add or select a customer to verify contact and delivery context.";
            return new OrderCustomerContext(empty, empty, true);
        }
        String type = friendlyCustomerType(choice.type());
        List<String> contact = new ArrayList<>();
        if (!choice.contact().isBlank()) {
            contact.add(choice.contact());
        }
        if (!choice.phone().isBlank()) {
            contact.add(choice.phone());
        }
        if (!choice.email().isBlank()) {
            contact.add(choice.email());
        }
        boolean missingContact = contact.isEmpty();
        String contactLine = missingContact
                ? "No contact person, phone, or email saved — verify delivery details before confirmation."
                : "Contact · " + String.join(" · ", contact);
        String display = "New order customer · " + choice.name() + " · " + type
                + "\n" + contactLine;
        if (!choice.notes().isBlank()) {
            display += "\nAccount note · " + compactContext(choice.notes(), 150);
        }
        String accessible = "New order customer " + choice.name() + ", type " + type + ". "
                + (missingContact
                ? "No contact person, phone, or email is saved; verify delivery details before confirmation."
                : "Contact " + String.join(", ", contact) + ".")
                + (choice.notes().isBlank() ? "" : " Account note: " + choice.notes());
        if ("DELIVERY".equals(CustomerOrder.normalizeHandoffType(handoffType))) {
            List<String> contextSources = new ArrayList<>();
            if (!contact.isEmpty()) {
                contextSources.add("customer contact");
            }
            if (!choice.notes().isBlank()) {
                contextSources.add("account delivery note");
            }
            if (orderInstructions != null && !orderInstructions.isBlank()) {
                contextSources.add("order instruction");
            }
            if (contextSources.isEmpty()) {
                String missing = "Delivery readiness · Missing — add customer contact, an account "
                        + "delivery note, or an order instruction before confirmation";
                display += "\n" + missing;
                accessible += " Delivery context is missing; add customer contact, an account "
                        + "delivery note, or an order instruction before confirmation.";
            } else {
                String sources = String.join(", ", contextSources);
                display += "\nDelivery readiness · Ready from " + sources;
                accessible += " Delivery context is ready from " + sources + ".";
            }
        }
        return new OrderCustomerContext(display, accessible, missingContact);
    }

    private void updateSelectedOrderContext(int row, String status,
                                            OrderStockAssessment stockAssessment) {
        if (selectedOrderLabel == null) {
            return;
        }
        if (row < 0) {
            String message = "New order mode · Create & select starts a separate order";
            FarmUi.updateStatusBadge(selectedOrderLabel, message, FarmUi.Tone.NEUTRAL);
            selectedOrderLabel.getAccessibleContext().setAccessibleDescription(
                    "No existing order is selected. Create and select order will start a new order.");
            createOrderBtn.setToolTipText(
                    "Create a separate order with this first product and keep it selected.");
            addOrderLineBtn.setToolTipText("Select a draft or confirmed order first.");
            confirmOrderBtn.setToolTipText("Select a draft order first.");
            fulfillOrderBtn.setToolTipText("Select a confirmed order first.");
            replaceSelectedOrderDate(null);
            replaceSelectedOrderHandoff(null);
            replaceSelectedOrderNotes(null);
            replaceSelectedOrderLines(null);
            updateSelectedOrderCustomerContext(null);
            updateSelectedOrderStockContext(null, OrderStockAssessment.unavailable());
            return;
        }
        Object id = orderTable.getValueAt(row, 0);
        String handoff = lastOrderView.stream()
                .filter(order -> Objects.equals(order.getId(), id))
                .map(CustomerOrder::getHandoffType)
                .map(CrmTab::friendlyHandoffType)
                .findFirst().orElse("Unspecified");
        String customer = String.valueOf(orderTable.getValueAt(row, 2));
        Object linesValue = orderTable.getValueAt(row, 5);
        int lines = linesValue instanceof Number number ? number.intValue() : 0;
        Object totalValue = orderTable.getValueAt(row, 6);
        double total = totalValue instanceof Number number
                ? number.doubleValue() : parseDisplayTotal(totalValue);
        String prefix = "COMPLETED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status) ? "Viewing" : "Selected";
        String message = prefix + " #" + id + " · " + customer + " · " + status
                + ("Unspecified".equals(handoff) ? "" : " · " + handoff)
                + " · " + lines + " product" + (lines == 1 ? "" : "s")
                + " · $" + String.format(java.util.Locale.ROOT, "%,.2f", total);
        FarmUi.Tone tone = "CONFIRMED".equalsIgnoreCase(status)
                ? FarmUi.Tone.SUCCESS
                : "CANCELLED".equalsIgnoreCase(status)
                ? FarmUi.Tone.DANGER
                : "DRAFT".equalsIgnoreCase(status)
                ? FarmUi.Tone.INFO : FarmUi.Tone.NEUTRAL;
        FarmUi.updateStatusBadge(selectedOrderLabel, message, tone);
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(
                "Order actions apply to order " + id + " for " + customer
                        + ", status " + status + ".");
        createOrderBtn.setToolTipText(
                "Choose Start another order before creating a separate order.");
        addOrderLineBtn.setToolTipText(editingOrderId != null && editingOrderId.equals(id)
                ? "Save the corrected product on order #" + id + " for " + customer + "."
                : "Add the entered product to order #" + id + " for " + customer + ".");
        confirmOrderBtn.setToolTipText("Confirm order #" + id + " for " + customer + ".");
        confirmOrderBtn.getAccessibleContext().setAccessibleDescription(
                "Confirm order " + id + " for " + customer
                        + ". Inventory does not change until completion.");
        replaceSelectedOrderDate((Long) id);
        replaceSelectedOrderHandoff((Long) id);
        replaceSelectedOrderNotes((Long) id);
        replaceSelectedOrderLines((Long) id);
        updateSelectedOrderCustomerContext((Long) id);
        updateSelectedOrderStockContext((Long) id, stockAssessment);
        fulfillOrderBtn.setToolTipText(stockAssessment.available()
                && !stockAssessment.gaps().isEmpty()
                ? "Resolve the selected order's stock gaps in Inventory, then refresh CRM "
                        + "before completion."
                : "Complete order #" + id + " for " + customer + " and deduct inventory.");
        fulfillOrderBtn.getAccessibleContext().setAccessibleDescription(
                stockAssessment.available() && !stockAssessment.gaps().isEmpty()
                        ? "Completion is locked until the selected order's stock issues are resolved."
                        : "Complete order " + id + " for " + customer
                                + " and deduct matching inventory.");
    }

    private OrderStockAssessment selectedOrderStockAssessment(int row) {
        if (!presenter.inventoryGuidanceAvailable() || row < 0) {
            return OrderStockAssessment.unavailable();
        }
        Object value = orderTable.getValueAt(row, 0);
        if (!(value instanceof Long orderId)) {
            return OrderStockAssessment.unavailable();
        }
        return orderStockAssessment(orderId);
    }

    private int savedProductCount(int row) {
        if (row < 0 || row >= orderTable.getRowCount()) {
            return 0;
        }
        Object value = orderTable.getValueAt(row, 5);
        return value instanceof Number number ? Math.max(0, number.intValue()) : 0;
    }

    private void updateSelectedOrderCustomerContext(Long orderId) {
        if (selectedOrderCustomerContext == null) {
            return;
        }
        CustomerOrder order = orderId == null ? null : lastOrderView.stream()
                .filter(candidate -> orderId.equals(candidate.getId()))
                .findFirst()
                .orElse(null);
        OrderCustomerContext context = orderCustomerContext(order);
        selectedOrderCustomerContext.setText(context.displayText());
        selectedOrderCustomerContext.setCaretPosition(0);
        selectedOrderCustomerContext.setToolTipText(context.accessibleText());
        selectedOrderCustomerContext.getAccessibleContext().setAccessibleDescription(
                context.accessibleText());
        if (reviewOrderCustomerBtn != null) {
            reviewOrderCustomerBtn.setToolTipText(orderId == null || order == null
                    ? "Select an order before opening its customer account."
                    : "Open “" + order.getCustomer().getName()
                            + "” in Customer accounts without changing the order draft.");
            reviewOrderCustomerBtn.getAccessibleContext().setAccessibleDescription(
                    reviewOrderCustomerBtn.getToolTipText());
        }
    }

    private boolean validateNewOrderIdentity(boolean showError) {
        if (orderDate == null || orderNotes == null) {
            return true;
        }
        JTextField invalidField = null;
        String message = null;
        try {
            presenter.validatedOrderDate(orderDate.getText());
        } catch (CrmPresenter.InputValidationException validation) {
            invalidField = orderDate;
            message = validation.getMessage();
        }
        if (invalidField == null && orderNotes.getText().trim().length() > 2000) {
            invalidField = orderNotes;
            message = "Order instructions cannot exceed 2000 characters.";
        }
        if (createOrderBtn != null) {
            String guidance = invalidField == null
                    ? "Create the dated order with this first product and keep it selected for more products."
                    : message + " Correct the new-order details before creating it.";
            createOrderBtn.setToolTipText(guidance);
            createOrderBtn.getAccessibleContext().setAccessibleDescription(guidance);
        }
        if (invalidField == null || !showError) {
            return invalidField == null;
        }
        FarmUi.clearFieldError(orderDate);
        FarmUi.clearFieldError(orderNotes);
        FarmUi.markFieldError(invalidField, message);
        String fieldName = invalidField == orderDate ? "order date" : "order instructions";
        String recovery = message
                + " Customer, handoff, product, quantity, unit, price, and instructions were kept.";
        FarmUi.updateStatusBadge(selectedOrderLabel,
                "Order not created · check " + fieldName, FarmUi.Tone.DANGER);
        selectedOrderLabel.setToolTipText(recovery);
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(recovery);
        FarmUi.revealAndFocus(invalidField);
        status("Order not created. Correct the highlighted " + fieldName + ", then retry.");
        return false;
    }

    private void watchNewOrderIdentityValidation(JTextField field) {
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() {
                if (createOrderBtn != null) {
                    if (field == orderNotes) {
                        updateNewOrderCustomerContext();
                    }
                    boolean correcting = "error".equals(
                            field.getClientProperty("JComponent.outline"));
                    updateOrderActions();
                    if (correcting) {
                        SwingUtilities.invokeLater(() -> {
                            if (newOrderIdentityFieldValid(field)) {
                                FarmUi.clearFieldError(field);
                                showOrderCorrectionReady();
                            }
                        });
                    }
                }
            }

            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { changed(); }
        });
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent event) {
                if (selectedOrderId() == null && field.isEnabled()) {
                    updateOrderActions();
                    validateNewOrderIdentity(true);
                }
            }
        });
    }

    private void acceptNewOrderIdentityBaseline() {
        acceptNewOrderCustomerBaseline();
        newOrderDateBaseline = orderDate == null ? "" : orderDate.getText().trim();
        newOrderHandoffBaseline = orderHandoffType == null
                || orderHandoffType.getSelectedItem() == null
                ? "" : String.valueOf(orderHandoffType.getSelectedItem()).trim();
    }

    private void acceptNewOrderCustomerBaseline() {
        if (orderCustomer == null || orderCustomer.getItemCount() == 0) {
            return;
        }
        newOrderCustomerBaselineId = currentNewOrderCustomerId();
        newOrderCustomerBaselineInitialized = true;
    }

    private Long currentNewOrderCustomerId() {
        CrmPresenter.CustomerChoice choice = orderCustomer == null
                ? null : (CrmPresenter.CustomerChoice) orderCustomer.getSelectedItem();
        return choice == null ? null : choice.id();
    }

    private String currentNewOrderHandoff() {
        return orderHandoffType == null || orderHandoffType.getSelectedItem() == null
                ? "" : String.valueOf(orderHandoffType.getSelectedItem()).trim();
    }

    private boolean hasUnfinishedNewOrderIdentity() {
        return !Objects.equals(currentNewOrderCustomerId(), newOrderCustomerBaselineId)
                || orderDate != null && !orderDate.getText().trim().equals(newOrderDateBaseline)
                || !currentNewOrderHandoff().equals(newOrderHandoffBaseline)
                || orderNotes != null && !orderNotes.getText().isBlank();
    }

    private NewOrderIdentityClearApproval currentNewOrderIdentityClearApproval() {
        return new NewOrderIdentityClearApproval(
                currentNewOrderCustomerId(),
                orderDate == null ? "" : orderDate.getText(),
                currentNewOrderHandoff(),
                orderNotes == null ? "" : orderNotes.getText());
    }

    private boolean newOrderIdentityClearApprovalMatches() {
        return newOrderIdentityClearApproval != null
                && newOrderIdentityClearApproval.equals(
                        currentNewOrderIdentityClearApproval());
    }

    private void resetNewOrderDetails() {
        if (!GuiPermissions.requireWrite(host, panel, "reset new-order details")) {
            return;
        }
        if (selectedOrderId() != null) {
            status("New-order details not reset. Choose Start another order first.");
            return;
        }
        if (!hasUnfinishedNewOrderIdentity()) {
            return;
        }
        if (!newOrderIdentityClearApprovalMatches()) {
            newOrderIdentityClearApproval = currentNewOrderIdentityClearApproval();
            updateOrderActions();
            String guidance = "New-order customer, date, handoff, and instructions are still unsaved. "
                    + "Choose Discard new-order changes again only to restore the accepted "
                    + "customer, date, and handoff and clear instructions. Product entry "
                    + "will be kept.";
            FarmUi.updateStatusBadge(selectedOrderLabel,
                    "New-order details kept · confirm discard", FarmUi.Tone.WARNING);
            selectedOrderLabel.setToolTipText(guidance);
            selectedOrderLabel.getAccessibleContext().setAccessibleDescription(guidance);
            FarmUi.revealAndFocus(resetNewOrderDetailsBtn);
            status("New-order details not reset. " + guidance);
            return;
        }
        newOrderIdentityClearApproval = null;
        restoreNewOrderCustomerBaseline();
        orderDate.setText(newOrderDateBaseline);
        orderHandoffType.setSelectedItem(newOrderHandoffBaseline);
        orderNotes.setText("");
        FarmUi.clearFieldError(orderDate);
        FarmUi.clearFieldError(orderNotes);
        updateOrderActions();
        FarmUi.updateStatusBadge(selectedOrderLabel,
                "New order mode · details reset · product kept", FarmUi.Tone.NEUTRAL);
        String guidance = "Restored the accepted new-order customer, date, and handoff and "
                + "cleared instructions. Product entry was kept.";
        selectedOrderLabel.setToolTipText(guidance);
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(guidance);
        orderProduct.requestFocusInWindow();
        status(guidance);
    }

    private void restoreNewOrderCustomerBaseline() {
        if (newOrderCustomerBaselineId == null) {
            orderCustomer.setSelectedItem(null);
            return;
        }
        for (int i = 0; i < orderCustomer.getItemCount(); i++) {
            if (newOrderCustomerBaselineId.equals(orderCustomer.getItemAt(i).id())) {
                orderCustomer.setSelectedIndex(i);
                return;
            }
        }
        orderCustomer.setSelectedItem(null);
    }

    private boolean newOrderIdentityFieldValid(JTextField field) {
        if (field == orderNotes) {
            return orderNotes.getText().trim().length() <= 2000;
        }
        try {
            presenter.validatedOrderDate(orderDate.getText());
            return true;
        } catch (CrmPresenter.InputValidationException validation) {
            return false;
        }
    }

    static OrderCustomerContext orderCustomerContext(CustomerOrder order) {
        if (order == null || order.getCustomer() == null) {
            String empty = "Select an order to see its customer contact and account context.";
            return new OrderCustomerContext(empty, empty, true);
        }
        Customer customer = order.getCustomer();
        String name = customer.getName();
        String type = friendlyCustomerType(customer.getCustomerType());
        List<String> contact = new ArrayList<>();
        if (!customer.getContactName().isBlank()) {
            contact.add(customer.getContactName());
        }
        if (!customer.getPhone().isBlank()) {
            contact.add(customer.getPhone());
        }
        if (!customer.getEmail().isBlank()) {
            contact.add(customer.getEmail());
        }
        boolean missingContact = contact.isEmpty();
        String contactLine = missingContact
                ? "No contact person, phone, or email saved — open the customer to add one."
                : "Contact · " + String.join(" · ", contact);
        String display = "Customer · " + name + " · " + type + "\n" + contactLine;
        if (!customer.getNotes().isBlank()) {
            display += "\nAccount note · " + compactContext(customer.getNotes(), 150);
        }
        String accessible = "Selected order customer " + name + ", type " + type + ". "
                + (missingContact
                ? "No contact person, phone, or email is saved."
                : "Contact " + String.join(", ", contact) + ".")
                + (customer.getNotes().isBlank()
                ? "" : " Account note: " + customer.getNotes());
        return new OrderCustomerContext(display, accessible, missingContact);
    }

    private static String compactContext(String value, int maxLength) {
        String clean = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return clean.length() <= maxLength
                ? clean : clean.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private void reviewSelectedOrderCustomer() {
        Long orderId = selectedOrderId();
        Customer customer = orderId == null ? null : lastOrderView.stream()
                .filter(order -> orderId.equals(order.getId()))
                .map(CustomerOrder::getCustomer)
                .findFirst()
                .orElse(null);
        if (customer == null || customer.getId() == null) {
            status("The selected order's customer account is not available.");
            return;
        }
        openCustomerAccount(customer.getId(), customer.getName(), orderId,
                "order #" + orderId);
    }

    private void reviewNewOrderCustomer() {
        CrmPresenter.CustomerChoice choice = orderCustomer == null
                ? null : (CrmPresenter.CustomerChoice) orderCustomer.getSelectedItem();
        if (choice == null || choice.id() == null) {
            status("Select a new-order customer before opening its account.");
            return;
        }
        openCustomerAccount(choice.id(), choice.name(), null, "new-order draft");
    }

    private void openCustomerAccount(long customerId, String customerName,
                                     Long orderId, String source) {
        custSearch.setText("");
        boolean selected = restoreSelectedId(customerTable, customerModel, customerId);
        crmWorkspace.setSelectedIndex(0);
        pendingCustomerSelectionId = customerId;
        OpenCustomerRequest request = new OpenCustomerRequest(
                orderId, source, customerId, customerName);
        pendingOpenCustomerRequest = request;
        customerHandoffReturn = request;
        showCustomerHandoffReturn(request);
        startCrmRefresh();
        String loading = "Opening customer “" + customerName + "” from " + source
                + " in the background. The order and unfinished entry work are kept.";
        FarmUi.updateStatusBadge(customerModeLabel,
                selected ? "Customer selected · refreshing account list" : "Opening linked customer…",
                FarmUi.Tone.INFO);
        customerModeLabel.setToolTipText(loading);
        customerModeLabel.getAccessibleContext().setAccessibleDescription(loading);
        if (selected) {
            customerTable.scrollRectToVisible(
                    customerTable.getCellRect(customerTable.getSelectedRow(), 0, true));
            status("Opened customer “" + customerName + "” from " + source
                    + ". The order and unfinished entry work were kept while CRM refreshes the full customer list.");
        } else {
            status("Opening customer “" + customerName + "” from " + source
                    + " in the background. The order and unfinished entry work were kept.");
        }
    }

    private void showCustomerHandoffReturn(OpenCustomerRequest request) {
        if (customerHandoffBanner == null) {
            return;
        }
        String destination = request.orderId() == null
                ? "unfinished order entry" : "order #" + request.orderId();
        String message = "Reviewing “" + request.customerName() + "” from " + request.source();
        String guidance = "Return to " + destination
                + " without clearing its customer, delivery, or product work.";
        FarmUi.updateStatusBadge(customerHandoffLabel, message, FarmUi.Tone.INFO);
        customerHandoffLabel.setToolTipText(guidance);
        customerHandoffLabel.getAccessibleContext().setAccessibleDescription(guidance);
        returnFromCustomerBtn.setText("Return to " + destination);
        returnFromCustomerBtn.setToolTipText(guidance);
        returnFromCustomerBtn.getAccessibleContext().setAccessibleDescription(guidance);
        customerHandoffBanner.setVisible(true);
    }

    private void returnFromCustomerHandoff() {
        OpenCustomerRequest request = customerHandoffReturn;
        if (request == null) {
            return;
        }
        clearCustomerHandoffReturn();
        crmWorkspace.setSelectedIndex(1);
        if (request.orderId() == null) {
            orderTable.clearSelection();
            updateOrderActions();
            FarmUi.revealAndFocus(orderProduct);
            status("Returned to unfinished order entry from customer “"
                    + request.customerName() + "”. Every draft field was kept.");
            return;
        }
        restoreSelectedId(orderTable, orderModel, request.orderId());
        updateOrderActions();
        FarmUi.revealAndFocus(orderTable);
        status("Returned to order #" + request.orderId() + " from customer “"
                + request.customerName() + "”. Order entry work was kept.");
    }

    private void clearCustomerHandoffReturn() {
        OpenCustomerRequest request = customerHandoffReturn;
        if (request != null && request.equals(pendingOpenCustomerRequest)) {
            pendingOpenCustomerRequest = null;
            pendingCustomerSelectionId = null;
        }
        if (request != null && pendingOpenCustomerPresentation != null
                && request.equals(pendingOpenCustomerPresentation.request())) {
            pendingOpenCustomerPresentation = null;
        }
        customerHandoffReturn = null;
        if (customerHandoffBanner != null) {
            customerHandoffBanner.setVisible(false);
        }
    }

    private void applyOpenCustomerPresentation(OpenCustomerPresentation presentation) {
        OpenCustomerRequest request = presentation.request();
        if (presentation.restored()) {
            String guidance = "Opened customer “" + request.customerName() + "” from "
                    + request.source() + ". The exact account is selected and keyboard focus moved "
                    + "to the customer table. The order and unfinished entry work were kept.";
            FarmUi.updateStatusBadge(customerModeLabel,
                    "Opened linked customer · “" + request.customerName() + "”",
                    FarmUi.Tone.SUCCESS);
            customerModeLabel.setToolTipText(guidance);
            customerModeLabel.getAccessibleContext().setAccessibleDescription(guidance);
            status("Opened customer “" + request.customerName() + "” from "
                    + request.source() + ". The order and unfinished entry work were kept.");
            FarmUi.revealAndFocus(customerTable);
            return;
        }
        String guidance = "Customer “" + request.customerName() + "” from " + request.source()
                + " is outside the current customer search. The order and "
                + "unfinished entry work were kept. Keyboard focus moved to Customer search; clear "
                + "it or choose Show all to retry.";
        FarmUi.updateStatusBadge(customerModeLabel,
                "Linked customer outside current search", FarmUi.Tone.WARNING);
        customerModeLabel.setToolTipText(guidance);
        customerModeLabel.getAccessibleContext().setAccessibleDescription(guidance);
        status(guidance);
        FarmUi.revealAndFocus(custSearch);
    }

    private OrderStockAssessment orderStockAssessment(long orderId) {
        OrderStockAssessment readinessAssessment = orderReadinessAssessments.get(orderId);
        if (readinessAssessment != null) {
            return readinessAssessment;
        }
        if (!presenter.inventoryGuidanceAvailable()) {
            return OrderStockAssessment.unavailable();
        }
        return lastOrderView.stream()
                .filter(order -> order.getId() != null && order.getId() == orderId)
                .findFirst()
                .map(order -> assessOrderStock(order, inventoryChoiceSnapshot))
                .orElseGet(OrderStockAssessment::unavailable);
    }

    static OrderStockAssessment assessOrderStock(
            CustomerOrder order, List<CrmPresenter.InventoryChoice> inventory) {
        if (order == null || inventory == null) {
            return OrderStockAssessment.unavailable();
        }
        Map<String, Double> requiredByProduct = new LinkedHashMap<>();
        Map<String, String> displayNames = new LinkedHashMap<>();
        Map<String, String> orderUnits = new LinkedHashMap<>();
        Map<String, Boolean> invalidQuantities = new LinkedHashMap<>();
        order.getLines().forEach(line -> {
            String name = line.getProductName() == null ? "" : line.getProductName().trim();
            String key = name.toLowerCase(Locale.ROOT);
            double required = line.getQuantity();
            requiredByProduct.merge(key, required, com.flowerfarm.util.InventoryQuantity::add);
            invalidQuantities.merge(key, !InventoryQuantity.validPositive(required, line.getUnit()), Boolean::logicalOr);
            displayNames.putIfAbsent(key, name);
            String lineUnit = line.getUnit() == null ? "" : line.getUnit().trim();
            orderUnits.merge(key, lineUnit, (current, next) ->
                    InventoryUnits.equivalent(current, next) ? current : "mixed units");
        });
        Map<String, CrmPresenter.InventoryChoice> availableByProduct = new LinkedHashMap<>();
        inventory.forEach(choice -> availableByProduct.putIfAbsent(
                choice.name().trim().toLowerCase(Locale.ROOT), choice));
        List<OrderStockGap> gaps = requiredByProduct.entrySet().stream()
                .map(entry -> {
                    CrmPresenter.InventoryChoice choice = availableByProduct.get(entry.getKey());
                    double available = choice == null ? 0 : choice.available();
                    String orderUnit = orderUnits.getOrDefault(entry.getKey(), "");
                    String inventoryUnit = choice == null ? "" : choice.unit();
                    boolean unitMismatch = choice != null
                            && !InventoryUnits.equivalent(inventoryUnit, orderUnit);
                    return new OrderStockGap(displayNames.get(entry.getKey()), entry.getValue(),
                            available, Math.max(0, entry.getValue() - available), choice == null,
                            unitMismatch, orderUnit, inventoryUnit,
                            invalidQuantities.getOrDefault(entry.getKey(), false));
                })
                .filter(gap -> gap.missing() > 0 || gap.unitMismatch()
                        || gap.quantityInvalid())
                .toList();
        return new OrderStockAssessment(true, requiredByProduct.size(), gaps);
    }

    static boolean isOrderCompletionReady(
            CustomerOrder order, List<CrmPresenter.InventoryChoice> inventory) {
        if (order == null || !"CONFIRMED".equalsIgnoreCase(order.getStatus())) {
            return false;
        }
        String handoff = CustomerOrder.normalizeHandoffType(order.getHandoffType());
        if ("UNSPECIFIED".equals(handoff)
                || ("DELIVERY".equals(handoff) && !hasDeliveryContext(order))) {
            return false;
        }
        OrderStockAssessment assessment = assessOrderStock(order, inventory);
        return assessment.available() && assessment.productCount() > 0
                && assessment.gaps().isEmpty();
    }

    static OrderReadinessView filterOrderReadiness(
            CrmPresenter.OrderView baseView,
            List<CrmPresenter.InventoryChoice> inventory,
            String readiness) {
        return filterOrderReadiness(baseView, inventory, readiness, List.of());
    }

    static OrderReadinessView filterOrderReadiness(
            CrmPresenter.OrderView baseView,
            List<CrmPresenter.InventoryChoice> inventory,
            String readiness,
            List<CustomerOrder> fullConfirmedQueue) {
        String normalized = readiness == null ? "ALL"
                : readiness.trim().toUpperCase(Locale.ROOT);
        List<CustomerOrder> visibleConfirmed = baseView.sourceOrders().stream()
                .filter(order -> "CONFIRMED".equalsIgnoreCase(order.getStatus()))
                .toList();
        List<CustomerOrder> queueSource = fullConfirmedQueue == null
                || fullConfirmedQueue.isEmpty() ? visibleConfirmed : fullConfirmedQueue;
        List<CustomerOrder> confirmed = queueSource.stream()
                .filter(order -> "CONFIRMED".equalsIgnoreCase(order.getStatus()))
                .sorted(Comparator
                        .comparing(CustomerOrder::getOrderDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CustomerOrder::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<String, Double> remainingByProduct = new LinkedHashMap<>();
        if (inventory != null) {
            inventory.forEach(choice -> remainingByProduct.putIfAbsent(
                    inventoryKey(choice.name()), choice.available()));
        }
        List<CustomerOrder> ready = new ArrayList<>();
        List<CustomerOrder> attention = new ArrayList<>();
        Map<Long, OrderStockAssessment> assessments = new LinkedHashMap<>();
        Set<Long> reservationLimited = new LinkedHashSet<>();
        for (CustomerOrder order : confirmed) {
            OrderStockAssessment standalone = assessOrderStock(order, inventory);
            boolean intrinsicallyReady = isOrderCompletionReady(order, inventory);
            if (!intrinsicallyReady) {
                attention.add(order);
                putReadinessAssessment(assessments, order, standalone);
                continue;
            }
            OrderStockAssessment queued = assessOrderStock(
                    order, inventoryWithRemaining(inventory, remainingByProduct));
            putReadinessAssessment(assessments, order, queued);
            if (queued.gaps().isEmpty()) {
                ready.add(order);
                reserveOrderStock(order, remainingByProduct);
            } else {
                attention.add(order);
                if (order.getId() != null) {
                    reservationLimited.add(order.getId());
                }
            }
        }
        Set<Long> readyIds = ready.stream().map(CustomerOrder::getId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Set<Long> attentionIds = attention.stream().map(CustomerOrder::getId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Set<Long> visibleConfirmedIds = visibleConfirmed.stream().map(CustomerOrder::getId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        List<CustomerOrder> selected = switch (normalized) {
            case "READY" -> ready.stream()
                    .filter(order -> visibleConfirmedIds.contains(order.getId())).toList();
            case "ATTENTION" -> attention.stream()
                    .filter(order -> visibleConfirmedIds.contains(order.getId())).toList();
            default -> baseView.sourceOrders();
        };
        int visibleReadyCount = (int) visibleConfirmed.stream()
                .filter(order -> readyIds.contains(order.getId())).count();
        int visibleAttentionCount = (int) visibleConfirmed.stream()
                .filter(order -> attentionIds.contains(order.getId())).count();
        return new OrderReadinessView(
                "ALL".equals(normalized) ? baseView : CrmPresenter.orderView(selected),
                normalized, visibleReadyCount, visibleAttentionCount,
                "ALL".equals(normalized) ? Map.of() : assessments,
                "ALL".equals(normalized) ? Set.of() : reservationLimited);
    }

    private static void putReadinessAssessment(
            Map<Long, OrderStockAssessment> assessments,
            CustomerOrder order, OrderStockAssessment assessment) {
        if (order.getId() != null) {
            assessments.put(order.getId(), assessment);
        }
    }

    private static String inventoryKey(String productName) {
        return productName == null ? "" : productName.trim().toLowerCase(Locale.ROOT);
    }

    private static List<CrmPresenter.InventoryChoice> inventoryWithRemaining(
            List<CrmPresenter.InventoryChoice> inventory,
            Map<String, Double> remainingByProduct) {
        if (inventory == null) {
            return null;
        }
        return inventory.stream()
                .map(choice -> new CrmPresenter.InventoryChoice(
                        choice.name(), choice.unit(), choice.unitPrice(),
                        remainingByProduct.getOrDefault(
                                inventoryKey(choice.name()), choice.available())))
                .toList();
    }

    private static void reserveOrderStock(
            CustomerOrder order, Map<String, Double> remainingByProduct) {
        order.getLines().forEach(line -> remainingByProduct.computeIfPresent(
                inventoryKey(line.getProductName()),
                (key, available) -> InventoryQuantity.subtract(available, line.getQuantity())));
    }

    private void updateSelectedOrderStockContext(Long orderId,
                                                  OrderStockAssessment assessment) {
        if (selectedOrderStockLabel == null || resolveOrderStockBtn == null) {
            return;
        }
        selectedOrderStockProducts = assessment.gaps().stream()
                .map(OrderStockGap::productName).distinct().toList();
        selectedOrderQuantityIssue = assessment.gaps().stream()
                .anyMatch(OrderStockGap::quantityInvalid);
        selectedOrderUnitIssue = assessment.gaps().stream()
                .anyMatch(OrderStockGap::unitMismatch);
        resolveOrderStockBtn.setText(selectedOrderQuantityIssue
                ? "Correct saved quantity"
                : selectedOrderUnitIssue ? "Correct order unit" : "Resolve order stock");
        if (orderId == null) {
            FarmUi.updateStatusBadge(selectedOrderStockLabel,
                    "Select an order to check total stock", FarmUi.Tone.NEUTRAL);
            selectedOrderStockLabel.getAccessibleContext().setAccessibleDescription(
                    "Select an order to compare all of its product quantities with inventory.");
            resolveOrderStockBtn.setEnabled(false);
            return;
        }
        if (!assessment.available()) {
            FarmUi.updateStatusBadge(selectedOrderStockLabel,
                    "Order stock check unavailable · completion verifies inventory",
                    FarmUi.Tone.NEUTRAL);
            selectedOrderStockLabel.getAccessibleContext().setAccessibleDescription(
                    "Inventory guidance is unavailable. Completion will still verify stock "
                            + "transactionally before changing the order.");
            resolveOrderStockBtn.setEnabled(false);
            return;
        }
        if (assessment.productCount() == 0) {
            String guidance = "Order #" + orderId
                    + " has no saved products. Enter a product above and choose Add product to "
                    + "selected order before confirmation or completion.";
            FarmUi.updateStatusBadge(selectedOrderStockLabel,
                    "Order has no saved products · add one to continue", FarmUi.Tone.DANGER);
            selectedOrderStockLabel.getAccessibleContext().setAccessibleDescription(guidance);
            selectedOrderStockLabel.setToolTipText(guidance);
            resolveOrderStockBtn.setEnabled(false);
            return;
        }
        if (assessment.gaps().isEmpty()) {
            FarmUi.updateStatusBadge(selectedOrderStockLabel,
                    "Stock covers selected order · " + assessment.productCount() + " product"
                            + (assessment.productCount() == 1 ? "" : "s"),
                    FarmUi.Tone.SUCCESS);
            selectedOrderStockLabel.getAccessibleContext().setAccessibleDescription(
                    "The current inventory snapshot covers every product quantity on order "
                            + orderId + ".");
            resolveOrderStockBtn.setEnabled(false);
            return;
        }
        String details = assessment.gaps().stream().limit(2)
                .map(gap -> gap.productName() + (gap.quantityInvalid()
                        ? " quantity is invalid; counted products require whole quantities"
                        : gap.noInventoryMatch()
                        ? " has no inventory match"
                        : gap.unitMismatch()
                                ? " unit mismatch: order " + gap.orderUnit()
                                        + ", inventory " + gap.inventoryUnit()
                                : " short " + InventoryQuantity.format(gap.missing())
                                        + (reservationLimitedOrderIds.contains(orderId)
                                        ? " after earlier orders" : "")))
                .reduce((left, right) -> left + " · " + right).orElse("");
        if (assessment.gaps().size() > 2) {
            details += " · +" + (assessment.gaps().size() - 2) + " more";
        }
        boolean orderIssue = assessment.gaps().stream()
                .anyMatch(gap -> gap.unitMismatch() || gap.quantityInvalid());
        boolean reservationLimited = reservationLimitedOrderIds.contains(orderId);
        String issueName = orderIssue ? "order packing issue"
                : reservationLimited ? "queue stock gap" : "order stock gap";
        FarmUi.updateStatusBadge(selectedOrderStockLabel,
                assessment.gaps().size() + " " + issueName
                        + (assessment.gaps().size() == 1 ? "" : "s") + " · " + details,
                FarmUi.Tone.DANGER);
        selectedOrderStockLabel.getAccessibleContext().setAccessibleDescription(
                assessment.gaps().size() + " " + issueName
                        + (assessment.gaps().size() == 1 ? " blocks" : "s block")
                        + " completion for order " + orderId
                        + (selectedOrderQuantityIssue
                        ? ". Choose Correct saved quantity, enter a valid positive quantity for its "
                                + "unit, and save the product before completion."
                        : selectedOrderUnitIssue
                                ? ". Choose Correct order unit to load the first mismatched saved "
                                        + "product with Inventory's required unit, then save it "
                                        + "before completion."
                        : reservationLimited
                                ? ". Earlier dated confirmed orders are counted first in this "
                                        + "review. "
                                        + "Complete those orders first, or open Inventory to "
                                        + "replenish "
                                        + String.join(", ", selectedOrderStockProducts)
                                        + ", then refresh Customers and Orders."
                                : ". Open Inventory to correct units, count, or replenish "
                                        + String.join(", ", selectedOrderStockProducts)
                                        + ", then refresh Customers and Orders."));
        resolveOrderStockBtn.setToolTipText(selectedOrderQuantityIssue
                ? "Load the first saved line invalid for its unit; counted products require whole quantities for correction."
                : selectedOrderUnitIssue
                        ? "Load the first saved line whose unit does not match Inventory."
                : "Open Inventory with only products blocking the selected order.");
        resolveOrderStockBtn.setEnabled(!refreshingCrm && !fulfillingOrder
                && !runningOrderMutation && !creatingOrder && !addingOrderLine
                && (!(selectedOrderQuantityIssue || selectedOrderUnitIssue) || canWrite));
    }

    private void beginInvalidQuantityCorrection() {
        Long orderId = selectedOrderId();
        OrderLine invalid = orderId == null ? null : lastOrderView.stream()
                .filter(order -> orderId.equals(order.getId()))
                .flatMap(order -> order.getLines().stream())
                .filter(line -> !InventoryQuantity.validPositive(line.getQuantity(), line.getUnit()))
                .findFirst()
                .orElse(null);
        if (invalid == null || invalid.getId() == null) {
            status("The invalid saved quantity could not be loaded. Refresh CRM and review "
                    + "the order's saved products.");
            return;
        }
        for (int index = 0; index < selectedOrderLine.getItemCount(); index++) {
            OrderLineChoice choice = selectedOrderLine.getItemAt(index);
            if (invalid.getId().equals(choice.id())) {
                selectedOrderLine.setSelectedIndex(index);
                editSelectedOrderLine();
                status("Correct “" + invalid.getProductName() + "” on order #" + orderId
                        + ": enter a valid positive quantity; counted products require whole quantities.");
                return;
            }
        }
        status("The invalid saved product is not visible. Refresh CRM, then retry correction.");
    }

    private void beginUnitMismatchCorrection() {
        Long orderId = selectedOrderId();
        OrderStockGap mismatch = orderId == null ? null : orderStockAssessment(orderId).gaps()
                .stream()
                .filter(OrderStockGap::unitMismatch)
                .findFirst()
                .orElse(null);
        CustomerOrder order = orderId == null ? null : lastOrderView.stream()
                .filter(candidate -> orderId.equals(candidate.getId()))
                .findFirst()
                .orElse(null);
        OrderLine invalid = mismatch == null || order == null ? null : order.getLines().stream()
                .filter(line -> line.getId() != null
                        && mismatch.productName().equalsIgnoreCase(line.getProductName())
                        && !InventoryUnits.equivalent(line.getUnit(), mismatch.inventoryUnit()))
                .findFirst()
                .orElse(null);
        if (invalid == null) {
            status("The mismatched saved unit could not be loaded. Refresh CRM and review the "
                    + "order's saved products.");
            return;
        }
        for (int index = 0; index < selectedOrderLine.getItemCount(); index++) {
            OrderLineChoice choice = selectedOrderLine.getItemAt(index);
            if (invalid.getId().equals(choice.id())) {
                selectedOrderLine.setSelectedIndex(index);
                editSelectedOrderLine();
                orderUnit.setText(mismatch.inventoryUnit());
                updateOrderLinePreview();
                FarmUi.revealAndFocus(orderUnit);
                status("Correct “" + invalid.getProductName() + "” on order #" + orderId
                        + ": Inventory requires " + mismatch.inventoryUnit()
                        + ". Review the prefilled unit, then save product changes.");
                return;
            }
        }
        status("The mismatched saved product is not visible. Refresh CRM, then retry correction.");
    }

    private static double parseDisplayTotal(Object value) {
        try {
            return Double.parseDouble(String.valueOf(value).replace(",", "").trim());
        } catch (RuntimeException exception) {
            return 0.0;
        }
    }

    private void focusNewOrderEntry() {
        if (orderTable != null && (orderTable.getSelectedRow() >= 0
                || unavailableProductDraftOrderId != null)) {
            startAnotherOrder();
            return;
        }
        FarmUi.revealAndFocus(orderCustomer);
        status("New order mode is open. Choose the customer and delivery details, then add the first product.");
    }

    private void startAnotherOrder() {
        if (unavailableProductDraftOrderId != null && selectedOrderId() == null) {
            moveUnavailableProductToNewOrder();
            return;
        }
        if (orderTable == null || orderTable.getSelectedRow() < 0) {
            return;
        }
        String status = String.valueOf(
                orderModel.getValueAt(orderTable.getSelectedRow(), 3));
        boolean mutable = "Draft".equalsIgnoreCase(status)
                || "Confirmed".equalsIgnoreCase(status);
        if (!mutable && hasPendingProductEntry()) {
            moveImmutableOrderProductToNewOrder(status);
            return;
        }
        Long pausedOrderId = selectedOrderId();
        boolean pausedProductEntry = hasPendingProductEntry();
        orderTable.clearSelection();
        updateOrderActions();
        FarmUi.revealAndFocus(orderProduct);
        if (hasPendingProductEntry()) {
            status("New order mode. Restored the separate unfinished new-order product entry.");
        } else if (pausedProductEntry) {
            status("New order mode. The unfinished product entry for order #" + pausedOrderId
                    + " was paused safely; reselect that order to restore it.");
        } else {
            status("New order mode. The next valid product will create a separate order.");
        }
    }

    private void moveImmutableOrderProductToNewOrder(String status) {
        Long sourceOrderId = selectedOrderId();
        if (sourceOrderId == null) {
            return;
        }
        rememberCurrentProductDraft();
        ProductEntryDraft recovered = orderProductDrafts.get(sourceOrderId);
        if (recovered == null || !recovered.hasWork(defaultUnit)) {
            updateOrderActions();
            status("Order #" + sourceOrderId + " has no unfinished product to move.");
            return;
        }
        if (newOrderProductDraft != null && newOrderProductDraft.hasWork(defaultUnit)) {
            if (pausedNewOrderProductDraft != null) {
                status("The product could not move from read-only order #" + sourceOrderId
                        + " because two separate new-order product drafts are already preserved. "
                        + "Clear or save one before retrying.");
                return;
            }
            pausedNewOrderProductDraft = newOrderProductDraft;
        }
        newOrderProductDraft = recovered;
        orderProductDrafts.remove(sourceOrderId);
        applyProductEntryDraft(ProductEntryDraft.empty(defaultUnit));
        orderTable.clearSelection();
        updateOrderActions();
        FarmUi.revealAndFocus(orderProduct);
        status("Moved the preserved product from read-only " + status + " order #"
                + sourceOrderId + " into the new-order draft. Review customer and delivery "
                + "details before creating it"
                + (pausedNewOrderProductDraft == null ? "."
                : "; the earlier new-order product draft is paused and will return after this one."));
    }

    private void moveUnavailableProductToNewOrder() {
        Long missingOrderId = unavailableProductDraftOrderId;
        ProductEntryDraft recovered = orderProductDrafts.remove(missingOrderId);
        if (recovered == null || !recovered.hasWork(defaultUnit)) {
            unavailableProductDraftOrderId = null;
            activeProductEntryOrderId = null;
            applyProductEntryDraft(newOrderProductDraft);
            updateOrderActions();
            status("Order #" + missingOrderId
                    + " is unavailable and had no unfinished product to recover.");
            return;
        }
        if (newOrderProductDraft != null && newOrderProductDraft.hasWork(defaultUnit)) {
            if (pausedNewOrderProductDraft != null) {
                status("The missing-order product could not move because two separate new-order "
                        + "product drafts are already preserved. Clear or save one before retrying.");
                return;
            }
            pausedNewOrderProductDraft = newOrderProductDraft;
        }
        newOrderProductDraft = recovered;
        unavailableProductDraftOrderId = null;
        activeProductEntryOrderId = null;
        applyProductEntryDraft(recovered);
        updateOrderActions();
        FarmUi.revealAndFocus(orderProduct);
        status("Moved the preserved product from missing order #" + missingOrderId
                + " into the new-order draft. Review the customer, delivery date, instructions, "
                + "and product before creating a replacement order."
                + (pausedNewOrderProductDraft == null ? ""
                : " The earlier new-order product draft is paused and will return afterward."));
    }

    private void replaceInventoryChoices(List<CrmPresenter.InventoryChoice> choices,
                                         String currentProduct) {
        inventoryChoiceSnapshot = List.copyOf(choices);
        String selectedShelfProduct = inventoryDragList.getSelectedValue() == null
                ? null : inventoryDragList.getSelectedValue().name();
        inventoryDragModel.clear();
        choices.forEach(inventoryDragModel::addElement);
        if (selectedShelfProduct != null) {
            for (int index = 0; index < inventoryDragModel.size(); index++) {
                if (inventoryDragModel.get(index).name().equalsIgnoreCase(selectedShelfProduct)) {
                    inventoryDragList.setSelectedIndex(index);
                    break;
                }
            }
        }
        refreshingInventoryChoices = true;
        orderInventoryProduct.removeAllItems();
        orderInventoryProduct.addItem(null);
        for (CrmPresenter.InventoryChoice choice : choices) {
            orderInventoryProduct.addItem(choice);
            if (currentProduct != null && !currentProduct.isEmpty()
                    && choice.name().equalsIgnoreCase(currentProduct)) {
                orderInventoryProduct.setSelectedItem(choice);
            }
        }
        refreshingInventoryChoices = false;
    }

    private void replaceCustomerChoices(List<CrmPresenter.CustomerChoice> choices,
                                         CrmPresenter.CustomerChoice selected) {
        boolean hadChoices = orderCustomer.getItemCount() > 0;
        replacingNewOrderCustomers = true;
        try {
            orderCustomer.removeAllItems();
            for (CrmPresenter.CustomerChoice customer : choices) {
                orderCustomer.addItem(customer);
            }
            if (selected != null) {
                for (int i = 0; i < orderCustomer.getItemCount(); i++) {
                    if (orderCustomer.getItemAt(i).id().equals(selected.id())) {
                        orderCustomer.setSelectedIndex(i);
                        break;
                    }
                }
            }
        } finally {
            replacingNewOrderCustomers = false;
        }
        if (!newOrderCustomerBaselineInitialized || !hadChoices) {
            acceptNewOrderCustomerBaseline();
        } else if (newOrderCustomerBaselineId != null && choices.stream()
                .noneMatch(choice -> newOrderCustomerBaselineId.equals(choice.id()))) {
            // Never silently substitute another account when the accepted one was removed.
            newOrderCustomerBaselineId = null;
        }
        updateNewOrderCustomerContext();
        updateOrderActions();
    }

    private void applyInventoryChoice() {
        if (refreshingInventoryChoices || orderInventoryProduct == null) {
            return;
        }
        CrmPresenter.InventoryChoice choice =
                (CrmPresenter.InventoryChoice) orderInventoryProduct.getSelectedItem();
        if (choice == null) {
            return;
        }
        orderProduct.setText(choice.name());
        orderUnit.setText(choice.unit());
        orderPrice.setText(String.format(java.util.Locale.ROOT, "%.2f", choice.unitPrice()));
        orderQty.requestFocusInWindow();
    }

    private boolean canFillProductFromInventory() {
        return canWrite && !refreshingCrm && !creatingOrder && !addingOrderLine
                && !runningOrderMutation && !fulfillingOrder
                && unavailableProductDraftOrderId == null
                && editingOrderLineId == null
                && orderInventoryProduct != null && orderInventoryProduct.isEnabled()
                && orderProduct != null && orderProduct.isEnabled();
    }

    private void updateInventoryDragButton() {
        if (inventoryDragList != null && useDraggedInventoryBtn != null) {
            useDraggedInventoryBtn.setEnabled(inventoryDragList.getSelectedValue() != null
                    && canFillProductFromInventory());
        }
    }

    /** A drop fills only the editable line draft; it never adds or saves an order line. */
    private boolean useInventoryChoiceFromShelf(String rawName) {
        if (!canFillProductFromInventory()) {
            status("Inventory drop is unavailable while this order entry cannot be edited.");
            return false;
        }
        String name = rawName == null ? "" : rawName.trim();
        CrmPresenter.InventoryChoice current = inventoryChoiceSnapshot.stream()
                .filter(choice -> choice.name().equalsIgnoreCase(name))
                .findFirst().orElse(null);
        if (current == null) {
            status("That product is no longer in the current inventory list. Refresh and choose a stock item.");
            return false;
        }
        if (hasPendingProductEntry()) {
            status("This order already has an unfinished product draft. Save or clear it before choosing another stock item.");
            return false;
        }
        orderInventoryProduct.setSelectedItem(current);
        status("Filled the draft with " + current.name()
                + ". Review quantity, unit, and price, then save the product.");
        return true;
    }

    private void updateOrderLinePreview() {
        if (orderProduct == null || orderQty == null || orderUnit == null
                || orderPrice == null || orderLinePreviewLabel == null) {
            return;
        }
        Long orderId = selectedOrderId();
        List<OrderLine> savedLines = orderId == null ? List.of() : lastOrderView.stream()
                .filter(order -> orderId.equals(order.getId()))
                .findFirst()
                .map(CustomerOrder::getLines)
                .orElseGet(List::of);
        Long excludedLineId = editingOrderId != null && editingOrderId.equals(orderId)
                ? editingOrderLineId : null;
        List<CrmPresenter.InventoryChoice> previewInventory =
                presenter.inventoryGuidanceAvailable()
                        ? inventoryForProductPreview(orderId) : null;
        CrmPresenter.LinePreview preview = presenter.previewLine(
                currentOrderLineDraft(),
                previewInventory,
                savedLines, excludedLineId);
        double confirmedReservation = confirmedReservationForCurrentProduct(previewInventory);
        if (preview.valid() && preview.inventoryMatched() && confirmedReservation > 0) {
            boolean selectedConfirmed = orderId != null && lastOrderView.stream()
                    .anyMatch(order -> orderId.equals(order.getId())
                            && "CONFIRMED".equalsIgnoreCase(order.getStatus()));
            String reservation = InventoryQuantity.format(confirmedReservation) + " inventory unit"
                    + (confirmedReservation == 1 ? "" : "s") + " counted toward "
                    + (selectedConfirmed ? "earlier " : "")
                    + "confirmed orders in this preview";
            preview = new CrmPresenter.LinePreview(
                    preview.valid(), preview.inventoryMatched(), preview.enoughStock(),
                    preview.duplicateSavedLines(), preview.lineTotal(), preview.invalidField(),
                    preview.message() + " · " + reservation);
        }
        orderLineValid = preview.valid();
        clearOrderLineInlineValidation();
        if (!preview.valid() && hasPendingProductEntry()) {
            markOrderLineInlineError(preview.invalidField(), preview.message());
        }
        FarmUi.Tone tone = !preview.valid()
                ? FarmUi.Tone.WARNING
                : preview.duplicateSavedLines() > 0 || !preview.enoughStock()
                        ? FarmUi.Tone.WARNING : FarmUi.Tone.SUCCESS;
        FarmUi.updateStatusBadge(orderLinePreviewLabel, preview.message(), tone);
        orderLinePreviewLabel.getAccessibleContext().setAccessibleDescription(preview.message());
        updateOrderActions();
    }

    private List<CrmPresenter.InventoryChoice> inventoryForProductPreview(Long orderId) {
        Map<String, Double> remainingByProduct = new LinkedHashMap<>();
        inventoryChoiceSnapshot.forEach(choice -> remainingByProduct.putIfAbsent(
                inventoryKey(choice.name()), choice.available()));
        List<CustomerOrder> confirmed = confirmedCompletionQueueSnapshot.stream()
                .filter(order -> "CONFIRMED".equalsIgnoreCase(order.getStatus()))
                .sorted(Comparator
                        .comparing(CustomerOrder::getOrderDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CustomerOrder::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (CustomerOrder order : confirmed) {
            if (orderId != null && orderId.equals(order.getId())) {
                break;
            }
            if (!isOrderCompletionReady(order, inventoryChoiceSnapshot)) {
                continue;
            }
            OrderStockAssessment queued = assessOrderStock(
                    order, inventoryWithRemaining(inventoryChoiceSnapshot, remainingByProduct));
            if (queued.gaps().isEmpty()) {
                reserveOrderStock(order, remainingByProduct);
            }
        }
        return inventoryWithRemaining(inventoryChoiceSnapshot, remainingByProduct);
    }

    private double confirmedReservationForCurrentProduct(
            List<CrmPresenter.InventoryChoice> previewInventory) {
        if (previewInventory == null || orderProduct == null) {
            return 0;
        }
        String key = inventoryKey(orderProduct.getText());
        double onHand = inventoryChoiceSnapshot.stream()
                .filter(choice -> inventoryKey(choice.name()).equals(key))
                .mapToDouble(CrmPresenter.InventoryChoice::available)
                .findFirst().orElse(0);
        double afterReservations = previewInventory.stream()
                .filter(choice -> inventoryKey(choice.name()).equals(key))
                .mapToDouble(CrmPresenter.InventoryChoice::available)
                .findFirst().orElse(onHand);
        return Math.max(0, onHand - afterReservations);
    }

    private CrmPresenter.LineDraft currentOrderLineDraft() {
        return new CrmPresenter.LineDraft(
                orderProduct.getText(), orderQty.getText(),
                orderUnit.getText(), orderPrice.getText());
    }

    private boolean duplicateLineApprovalMatches(Long orderId) {
        if (duplicateLineApproval == null || orderId == null
                || duplicateLineApproval.orderId() != orderId) {
            return false;
        }
        CustomerOrder current = lastOrderView.stream()
                .filter(order -> orderId.equals(order.getId()))
                .findFirst()
                .orElse(null);
        return current != null
                && duplicateLineApproval.draft().equals(
                        normalizedLineDraft(currentOrderLineDraft()))
                && duplicateLineApproval.reviewedOrder().equals(
                        presenter.reviewSnapshot(current));
    }

    private static CrmPresenter.LineDraft normalizedLineDraft(CrmPresenter.LineDraft draft) {
        return new CrmPresenter.LineDraft(
                draft.product() == null ? "" : draft.product().trim().toLowerCase(Locale.ROOT),
                draft.quantity() == null ? "" : draft.quantity().trim(),
                draft.unit() == null ? "" : draft.unit().trim().toLowerCase(Locale.ROOT),
                draft.unitPrice() == null ? "" : draft.unitPrice().trim());
    }

    private void setOrderToolChoices(boolean writable) {
        if (orderTools == null) {
            return;
        }
        String selected = String.valueOf(orderTools.getSelectedItem());
        String[] choices = writable
                ? new String[]{"Save invoice", "Export filtered orders",
                    "Export all orders", "Cancel selected order", "Delete selected order"}
                : new String[]{"Save invoice", "Export filtered orders", "Export all orders"};
        orderTools.setModel(new DefaultComboBoxModel<>(choices));
        for (String choice : choices) {
            if (choice.equals(selected)) {
                orderTools.setSelectedItem(choice);
                break;
            }
        }
    }

    private static void addFormField(JPanel form, String text,
                                     JComponent field, char mnemonic) {
        form.add(fieldLabel(text, field, mnemonic));
        form.add(field);
    }

    private static JLabel fieldLabel(String text, JComponent field, char mnemonic) {
        JLabel label = new JLabel(text + ":");
        label.setLabelFor(field);
        if (mnemonic != '\0') {
            label.setDisplayedMnemonic(mnemonic);
        }
        return label;
    }

    private static JLabel controlLabel(String text, JComponent control) {
        JLabel label = new JLabel(text);
        label.setLabelFor(control);
        return label;
    }

    private void applyCustomerFilter() {
        if (customerModel == null) {
            return;
        }
        // Customer searches share the CRM snapshot worker. This keeps repository work
        // off Swing's event thread and gives rapid searches the same latest-request
        // queue/discard behavior as order filters, without disturbing unfinished forms.
        startCrmRefresh();
    }

    private void replaceCustomerRows(List<CrmPresenter.CustomerRow> list) {
        customerModel.setRowCount(0);
        for (CrmPresenter.CustomerRow customer : list) {
            customerModel.addRow(customer.cells());
        }
        if (customerCardLayout != null && customerState != null) {
            updateCustomerEmptyState();
            customerCardLayout.show(customerState, list.isEmpty() ? "empty" : "table");
        }
    }

    private void updateCustomerEmptyState() {
        if (customerEmptyState == null) {
            return;
        }
        if (custSearch == null || custSearch.getText().isBlank()) {
            customerEmptyState.updateCopy(
                    "No customers yet",
                    "Add the first customer account below; it will be ready for order entry after save.");
        } else {
            customerEmptyState.updateCopy(
                    "No customers match this search",
                    "Show all customers or add a new account below.");
        }
    }

    private void applyOrderFilter() {
        if (orderModel == null) {
            return;
        }
        FarmUi.clearFieldError(filterFrom);
        FarmUi.clearFieldError(filterTo);
        startCrmRefresh();
    }

    private void showAllOrders() {
        filterStatus.setSelectedItem("All statuses");
        filterHandoff.setSelectedItem("All handoffs");
        filterCustomer.setText("");
        filterFrom.setText("");
        filterTo.setText("");
        applyOrderFilter();
        status("Showing all orders; unfinished new-order entry was kept.");
    }

    private JPanel buildOrderEmptyState() {
        JPanel state = new JPanel(new GridBagLayout());
        state.setOpaque(false);
        state.setName("farm.emptyState");
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        orderEmptyTitle = new JLabel("No orders match these filters");
        orderEmptyTitle.setFont(orderEmptyTitle.getFont().deriveFont(Font.BOLD, 16f));
        orderEmptyTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        orderEmptyGuidance = FarmUi.mutedLabel(
                "Show all orders or create an order for a customer below.");
        orderEmptyGuidance.setAlignmentX(Component.CENTER_ALIGNMENT);
        orderEmptyShowAllBtn = FarmUi.secondaryButton("Show all orders");
        orderEmptyShowAllBtn.setName("crm.orderEmptyShowAll");
        orderEmptyShowAllBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        orderEmptyShowAllBtn.setToolTipText(
                "Clear order filters without clearing unfinished new-order or product entry. "
                        + "Use this button or press Alt+L for Show all on the Orders toolbar.");
        orderEmptyShowAllBtn.getAccessibleContext().setAccessibleDescription(
                orderEmptyShowAllBtn.getToolTipText());
        orderEmptyShowAllBtn.addActionListener(event -> showAllOrders());
        content.add(orderEmptyTitle);
        content.add(Box.createVerticalStrut(5));
        content.add(orderEmptyGuidance);
        content.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        content.add(orderEmptyShowAllBtn);
        state.add(content, new GridBagConstraints());
        state.getAccessibleContext().setAccessibleName(orderEmptyTitle.getText());
        state.getAccessibleContext().setAccessibleDescription(orderEmptyGuidance.getText());
        return state;
    }

    private boolean orderFiltersActive() {
        return !"All statuses".equals(filterStatus.getSelectedItem())
                || !"All handoffs".equals(filterHandoff.getSelectedItem())
                || !filterCustomer.getText().isBlank()
                || !filterFrom.getText().isBlank()
                || !filterTo.getText().isBlank();
    }

    private void updateOrderEmptyState() {
        if (orderEmptyTitle == null) {
            return;
        }
        boolean filtered = orderFiltersActive();
        boolean firstRun = !crmHasCustomers;
        String title = firstRun || !filtered
                ? "No orders yet" : "No orders match these filters";
        String guidance = firstRun
                ? "Add the first customer on the Customers tab, then create a draft order."
                : filtered
                ? "Show all orders; unfinished new-order and product entry will be kept."
                : "Select or add a customer below, then create the first draft order.";
        orderEmptyTitle.setText(title);
        orderEmptyGuidance.setText(guidance);
        orderEmptyShowAllBtn.setVisible(filtered && !firstRun);
        orderState.getAccessibleContext().setAccessibleName(title);
        orderState.getAccessibleContext().setAccessibleDescription(guidance);
    }

    private void showNeedsHandoffReview() {
        filterStatus.setSelectedItem("Open");
        filterHandoff.setSelectedItem("Needs review");
        filterCustomer.setText("");
        filterFrom.setText("");
        filterTo.setText("");
        applyOrderFilter();
        if (orderTable != null) {
            orderTable.requestFocusInWindow();
        }
        status("Opened the complete Needs handoff review queue; customer and date filters were cleared.");
    }

    private void showOrderFilterValidation(CrmPresenter.InputValidationException validation) {
        FarmUi.clearFieldError(filterFrom);
        FarmUi.clearFieldError(filterTo);
        JTextField field = "filterTo".equals(validation.field()) ? filterTo : filterFrom;
        String label = field == filterTo ? "To date" : "From date";
        FarmUi.markFieldError(field, validation.getMessage());
        String recovery = validation.getMessage()
                + " Existing order results were kept. Correct the highlighted date, then apply again.";
        FarmUi.updateStatusBadge(orderStatusLabel,
                "Check " + label, FarmUi.Tone.DANGER);
        orderStatusLabel.setToolTipText(recovery);
        orderStatusLabel.getAccessibleContext().setAccessibleDescription(recovery);
        status("Order filters not applied. " + recovery);
    }

    private void showOrderFilterCorrectionReady() {
        String guidance = "The highlighted date changed. Apply filters again to validate the range.";
        FarmUi.updateStatusBadge(orderStatusLabel,
                "Changed · ready to apply filters", FarmUi.Tone.INFO);
        orderStatusLabel.setToolTipText(guidance);
        orderStatusLabel.getAccessibleContext().setAccessibleDescription(guidance);
    }

    /**
     * Focus the order workspace on one lifecycle status. Date and customer
     * limits are cleared so a cross-workflow handoff cannot hide older work.
     */
    public void showOrdersByStatus(String status) {
        showOrdersByStatus(status, null, null);
    }

    /**
     * Prepares sourced event-service instructions without overwriting another
     * unfinished new-order brief or treating services as physical inventory.
     */
    public boolean prepareEventOrderBrief(String eventBrief) {
        if (panel == null) {
            getUIComponent();
        }
        String safeBrief = eventBrief == null ? "" : eventBrief.trim();
        if (safeBrief.isBlank()) {
            return false;
        }
        if (safeBrief.length() > 2000) {
            safeBrief = safeBrief.substring(0, 1999) + "…";
        }
        if (orderTable != null && orderTable.getSelectedRow() >= 0) {
            startAnotherOrder();
        }
        String current = orderNotes.getText().trim();
        if (!current.isBlank() && !current.equals(safeBrief)) {
            FarmUi.revealAndFocus(orderNotes);
            status("The existing new-order instructions were kept. Copy the event brief after "
                    + "saving or clearing that separate draft.");
            return false;
        }
        orderNotes.setText(safeBrief);
        FarmUi.clearFieldError(orderNotes);
        updateNewOrderCustomerContext();
        updateOrderActions();
        FarmUi.revealAndFocus(orderNotes);
        status("Event-service brief prepared in new-order instructions. Add the customer, date, "
                + "handoff, and physical products before creating the draft order.");
        return true;
    }

    /** Focus a lifecycle queue and retain a route back to its source workflow. */
    public void showOrdersByStatus(String status, String returnTab, String returnLabel) {
        if (panel == null) {
            getUIComponent();
        }
        String normalized = status == null ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        String label = switch (normalized) {
            case "DRAFT" -> "Draft";
            case "CONFIRMED" -> "Confirmed";
            case "FULFILLED", "COMPLETED" -> "Completed";
            case "CANCELLED" -> "Cancelled";
            default -> "All statuses";
        };
        configureOrderQueueHandoff(label, returnTab, returnLabel);
        filterStatus.setSelectedItem(label);
        filterHandoff.setSelectedItem("All handoffs");
        filterCustomer.setText("");
        filterFrom.setText("");
        filterTo.setText("");
        applyOrderFilter();
        if (orderTable != null) {
            orderTable.requestFocusInWindow();
        }
        if (host != null) {
            host.setStatus("Opened " + label + " order queue"
                    + (handoffReturnLabel == null ? "."
                            : " from " + handoffReturnLabel + "; filters were cleared."));
        }
    }

    private void configureOrderQueueHandoff(String statusLabel,
                                            String returnTab, String returnLabel) {
        String safeTab = returnTab == null ? "" : returnTab.trim();
        String safeLabel = returnLabel == null ? "" : returnLabel.trim();
        if (safeTab.isEmpty()) {
            handoffReturnOrderId = null;
            handoffReturnTab = null;
            handoffReturnLabel = null;
            orderHandoffBanner.setVisible(false);
            return;
        }
        handoffReturnOrderId = null;
        handoffReturnTab = safeTab;
        handoffReturnLabel = safeLabel.isEmpty() ? safeTab : safeLabel;
        String context = "Reviewing " + statusLabel + " orders from " + handoffReturnLabel;
        orderHandoffLabel.setText(context);
        orderHandoffLabel.setToolTipText(
                "Customer and date filters were cleared so the complete queue is visible.");
        orderHandoffLabel.getAccessibleContext().setAccessibleDescription(context);
        returnFromOrderBtn.setText("Return to " + handoffReturnLabel);
        returnFromOrderBtn.setToolTipText(
                "Return to " + handoffReturnLabel + " without rebuilding its current work.");
        returnFromOrderBtn.getAccessibleContext().setAccessibleName(
                "Return to " + handoffReturnLabel + " after reviewing "
                        + statusLabel + " orders");
        orderHandoffBanner.setVisible(true);
    }

    /**
     * Reveal one order from another operational workflow. Broad filters are
     * cleared so the requested order cannot remain hidden, and the pending
     * selection survives an in-flight CRM refresh.
     */
    public void showOrder(long orderId) {
        clearOrderLineHandoff();
        showOrderInternal(orderId, null, null);
    }

    /** Reveal one order and retain a contextual route back to its source workflow. */
    public void showOrder(long orderId, String returnTab, String returnLabel) {
        clearOrderLineHandoff();
        showOrderInternal(orderId, returnTab, returnLabel);
    }

    /** Reveal one exact saved product line for correction from another workflow. */
    public void showOrderLine(long orderId, Long lineId, String productName,
                              String returnTab, String returnLabel) {
        showOrderLineInternal(orderId, lineId, productName, returnTab, returnLabel, false);
    }

    /** Reveal and prepare one exact saved product for correction after a current CRM reload. */
    public void showOrderLineCorrection(long orderId, Long lineId, String productName,
                                        String returnTab, String returnLabel) {
        showOrderLineInternal(orderId, lineId, productName, returnTab, returnLabel, true);
    }

    private void showOrderLineInternal(long orderId, Long lineId, String productName,
                                       String returnTab, String returnLabel,
                                       boolean prepareCorrection) {
        handoffOrderLineOrderId = orderId;
        handoffOrderLineId = lineId;
        handoffOrderLineProduct = productName == null ? "" : productName.trim();
        pendingOrderLineSelectionId = lineId;
        pendingOrderLineProduct = handoffOrderLineProduct;
        pendingOrderLineCorrectionEdit = prepareCorrection;
        showOrderInternal(orderId, returnTab, returnLabel);
        if (selectedOrderLine != null) {
            selectedOrderLine.requestFocusInWindow();
        }
        if (!refreshingCrm) {
            preparePendingOrderLineCorrection();
        }
    }

    private void showOrderInternal(long orderId, String returnTab, String returnLabel) {
        if (panel == null) {
            getUIComponent();
        }
        crmWorkspace.setSelectedIndex(1);
        configureOrderHandoff(orderId, returnTab, returnLabel);
        pendingOrderSelectionId = orderId;
        handoffOrderSelectionId = orderId;
        filterStatus.setSelectedItem("All statuses");
        filterHandoff.setSelectedItem("All handoffs");
        filterCustomer.setText("");
        filterFrom.setText("");
        filterTo.setText("");
        applyOrderFilter();
        boolean restored = restoreSelectedId(orderTable, orderModel, orderId);
        if (restored && handoffOrderLineOrderId != null
                && handoffOrderLineOrderId == orderId) {
            replaceSelectedOrderLines(orderId);
        }
        if (!refreshingCrm) {
            reportOrderHandoff(orderId, restored);
        }
        if (orderTable != null) {
            FarmUi.revealAndFocus(orderTable);
        }
    }

    private void clearOrderLineHandoff() {
        handoffOrderLineOrderId = null;
        handoffOrderLineId = null;
        handoffOrderLineProduct = null;
        pendingOrderLineSelectionId = null;
        pendingOrderLineProduct = null;
        pendingOrderLineCorrectionEdit = false;
    }

    private void configureOrderHandoff(long orderId, String returnTab, String returnLabel) {
        String safeTab = returnTab == null ? "" : returnTab.trim();
        String safeLabel = returnLabel == null ? "" : returnLabel.trim();
        if (safeTab.isEmpty()) {
            handoffReturnOrderId = null;
            handoffReturnTab = null;
            handoffReturnLabel = null;
            orderHandoffBanner.setVisible(false);
            return;
        }
        handoffReturnOrderId = orderId;
        handoffReturnTab = safeTab;
        handoffReturnLabel = safeLabel.isEmpty() ? safeTab : safeLabel;
        boolean lineHandoff = handoffOrderLineOrderId != null
                && handoffOrderLineOrderId == orderId
                && handoffOrderLineProduct != null && !handoffOrderLineProduct.isBlank();
        String context = lineHandoff
                ? "Correcting " + handoffOrderLineProduct + " on order #" + orderId
                        + " from " + handoffReturnLabel
                : "Reviewing order #" + orderId + " from " + handoffReturnLabel;
        orderHandoffLabel.setText(context);
        orderHandoffLabel.setToolTipText(
                lineHandoff
                        ? "Filters were cleared and the affected saved product is selected for review."
                        : "Filters were cleared so this exact order can be reviewed before returning.");
        orderHandoffLabel.getAccessibleContext().setAccessibleDescription(context);
        returnFromOrderBtn.setText("Return to " + handoffReturnLabel);
        returnFromOrderBtn.setToolTipText(
                "Return to " + handoffReturnLabel + " without rebuilding its current work.");
        returnFromOrderBtn.getAccessibleContext().setAccessibleName(
                "Return to " + handoffReturnLabel + " after reviewing "
                        + (lineHandoff ? handoffOrderLineProduct + " on " : "")
                        + "order " + orderId);
        orderHandoffBanner.setVisible(true);
    }

    private void returnFromOrderHandoff() {
        if (host == null || handoffReturnTab == null) {
            return;
        }
        String returnTab = handoffReturnTab;
        String returnLabel = handoffReturnLabel;
        Long orderId = handoffReturnOrderId;
        String product = handoffOrderLineOrderId != null
                && handoffOrderLineOrderId.equals(orderId) ? handoffOrderLineProduct : null;
        boolean unsavedCorrection = preserveOrClearHandoffCorrection(orderId);
        handoffReturnOrderId = null;
        handoffReturnTab = null;
        handoffReturnLabel = null;
        clearOrderLineHandoff();
        orderHandoffBanner.setVisible(false);
        host.selectTab(returnTab);
        host.setStatus("Returned to " + returnLabel
                + (orderId == null ? "." : " after reviewing "
                        + (product == null || product.isBlank() ? "" : product + " on ")
                        + "order #" + orderId + ".")
                + (unsavedCorrection
                        ? " The unsaved product correction remains in Customers & Orders."
                        : ""));
    }

    private boolean preserveOrClearHandoffCorrection(Long orderId) {
        boolean targetEdit = orderId != null && orderId.equals(editingOrderId)
                && handoffOrderLineOrderId != null && handoffOrderLineOrderId.equals(orderId)
                && (handoffOrderLineId == null || handoffOrderLineId.equals(editingOrderLineId));
        if (!targetEdit) {
            return false;
        }
        if (editingFieldsMatchReviewedLine()) {
            clearOrderLineEditState(true);
            clearStoredProductDraft(orderId);
            updateOrderActions();
            return false;
        }
        rememberCurrentProductDraft();
        return true;
    }

    private boolean editingFieldsMatchReviewedLine() {
        if (editingOrderLineReview == null) {
            return false;
        }
        try {
            return orderProduct.getText().trim().equals(editingOrderLineReview.productName())
                    && Double.compare(Double.parseDouble(orderQty.getText().trim()),
                            editingOrderLineReview.quantity()) == 0
                    && orderUnit.getText().trim().equalsIgnoreCase(
                            editingOrderLineReview.unit().trim())
                    && Double.compare(Double.parseDouble(orderPrice.getText().trim()),
                            editingOrderLineReview.unitPrice()) == 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void reportOrderHandoff(long orderId, boolean restored) {
        if (pendingOrderSelectionId != null && pendingOrderSelectionId == orderId) {
            pendingOrderSelectionId = null;
        }
        handoffOrderSelectionId = null;
        if (host != null) {
            boolean lineHandoff = handoffOrderLineOrderId != null
                    && handoffOrderLineOrderId == orderId;
            boolean lineRestored = lineHandoff
                    && pendingOrderLineSelectionId == null && pendingOrderLineProduct == null;
            if (lineHandoff && !lineRestored) {
                pendingOrderLineCorrectionEdit = false;
            }
            host.setStatus(!restored
                    ? "Order #" + orderId
                            + " is no longer available in Customers & Orders."
                    : lineHandoff && lineRestored
                            ? "Opened order #" + orderId + "; filters were cleared and saved product “"
                                    + handoffOrderLineProduct + "” is selected for correction."
                            : lineHandoff
                                    ? "Opened order #" + orderId + ", but saved product “"
                                            + handoffOrderLineProduct
                                            + "” changed or is no longer available. Review the current products."
                                    : "Opened order #" + orderId
                                            + "; filters were cleared and the saved order is selected.");
        }
        if (!restored || handoffOrderLineOrderId != null
                && handoffOrderLineOrderId == orderId
                && (pendingOrderLineSelectionId != null || pendingOrderLineProduct != null)) {
            pendingOrderLineSelectionId = null;
            pendingOrderLineProduct = null;
        }
    }

    private void preparePendingOrderLineCorrection() {
        if (!pendingOrderLineCorrectionEdit || refreshingCrm || crmRefreshQueued
                || crmRefreshFailure != null) {
            return;
        }
        pendingOrderLineCorrectionEdit = false;
        if (!canWrite || selectedOrderId() == null
                || !selectedOrderId().equals(handoffOrderLineOrderId)) {
            return;
        }
        OrderLineChoice line = selectedOrderLine == null
                ? null : (OrderLineChoice) selectedOrderLine.getSelectedItem();
        boolean exactLine = line != null && (handoffOrderLineId != null
                ? handoffOrderLineId.equals(line.id())
                : sameProduct(handoffOrderLineProduct, line.product()));
        if (exactLine) {
            editSelectedOrderLine();
        }
    }

    private void replaceOrderView(CrmPresenter.OrderView view) {
        FarmUi.clearFieldError(filterFrom);
        FarmUi.clearFieldError(filterTo);
        lastOrderView = view.sourceOrders();
        orderModel.setRowCount(0);
        for (CrmPresenter.OrderRow row : view.rows()) {
            orderModel.addRow(row.cells());
        }
        if (orderStatusLabel != null) {
            FarmUi.updateStatusBadge(orderStatusLabel, String.format(
                    "Showing %d · $%,.2f total · %d confirmed",
                    view.rows().size(), view.total(), view.pipelineCount()), FarmUi.Tone.NEUTRAL);
            String guidance = "Confirmed orders enter packing review. Check the current "
                    + "handoff, products, delivery context, and stock before completion.";
            orderStatusLabel.setToolTipText(guidance);
            orderStatusLabel.getAccessibleContext().setAccessibleDescription(guidance);
        }
        if (orderCardLayout != null && orderState != null) {
            updateOrderEmptyState();
            orderCardLayout.show(orderState, view.rows().isEmpty() ? "empty" : "table");
        }
        updateOrderActions();
    }

    private void replaceOrderView(OrderReadinessView readinessView) {
        orderReadinessAssessments = readinessView.assessments();
        reservationLimitedOrderIds = readinessView.reservationLimitedOrderIds();
        replaceOrderView(readinessView.view());
        if (orderStatusLabel == null || "ALL".equals(readinessView.filter())) {
            return;
        }
        String text;
        String guidance;
        FarmUi.Tone tone;
        if ("READY".equals(readinessView.filter())) {
            text = readinessView.readyCount() + " ready to complete · "
                    + readinessView.attentionCount() + " need attention";
            guidance = "Ready orders are confirmed, have a Pickup, Delivery, or Market handoff, "
                    + "contain saved products with valid quantities and matching units, have "
                    + "delivery contact or instructions when needed, and fit the current "
                    + "inventory snapshot in earliest-date order. Market Day checks the whole "
                    + "selected packing list. "
                    + readinessView.attentionCount()
                    + " confirmed order(s) were excluded; choose Needs attention to review them.";
            tone = readinessView.readyCount() > 0 ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING;
        } else {
            text = readinessView.attentionCount() + " need attention · "
                    + readinessView.readyCount() + " ready";
            guidance = "These confirmed orders cannot complete from the current snapshot. Review "
                    + "handoff, delivery context, saved products, quantities, units, and stock "
                    + "counted toward earlier dated orders in this review. "
                    + readinessView.readyCount() + " completion-ready order(s) are hidden.";
            tone = readinessView.attentionCount() > 0
                    ? FarmUi.Tone.WARNING : FarmUi.Tone.SUCCESS;
        }
        FarmUi.updateStatusBadge(orderStatusLabel, text, tone);
        orderStatusLabel.setToolTipText(guidance);
        orderStatusLabel.getAccessibleContext().setAccessibleDescription(guidance);
    }

    private boolean customerFormCurrent() {
        return !refreshingCrm && !crmRefreshQueued && !savingCustomer && !deletingCustomer
                && pendingCustomerWriteSuccess == null;
    }

    private boolean customerAccessAllowed() {
        return canWrite && (host == null || host.canMutateData());
    }

    private boolean requireCustomerAction(boolean mutation) {
        if (!customerAccessAllowed()) {
            status("Customer entry is read-only. The entered fields were kept; sign in as HAND or OWNER to edit.");
            return false;
        }
        if (pendingCustomerWriteSuccess != null) {
            showPendingCustomerWriteGuidance();
            return false;
        }
        if (!customerFormCurrent()) {
            status("Customer work is still loading or saving. The entered fields were kept; "
                    + "wait for the result before changing the form or submitting another action.");
            return false;
        }
        if (mutation && crmRefreshFailure != null) {
            status("Customer details are retained for review. Refresh CRM successfully "
                    + "before adding, saving, or deleting an account.");
            return false;
        }
        return true;
    }

    private CrmPresenter.CustomerRow selectedCustomerReview() {
        if (customerTable == null || customerTable.getSelectedRow() < 0) {
            return null;
        }
        int row = customerTable.convertRowIndexToModel(customerTable.getSelectedRow());
        return new CrmPresenter.CustomerRow((Long) customerModel.getValueAt(row, 0),
                nullToEmpty(customerModel.getValueAt(row, 1)),
                nullToEmpty(customerModel.getValueAt(row, 2)),
                nullToEmpty(customerModel.getValueAt(row, 3)),
                nullToEmpty(customerModel.getValueAt(row, 4)),
                nullToEmpty(customerModel.getValueAt(row, 5)),
                nullToEmpty(customerModel.getValueAt(row, 6)));
    }

    private void addCustomer() {
        if (!requireCustomerAction(true)) {
            return;
        }
        if (editingCustomerId != null) {
            status("A customer is loaded. Choose Save edit, or clear the form before adding an account.");
            return;
        }
        startCustomerSave(new CustomerSaveRequest(null, customerDraft(),
                custName.getText().trim()));
    }

    private void loadCustomerIntoForm() {
        if (!requireCustomerAction(false)) {
            return;
        }
        CrmPresenter.CustomerRow target = selectedCustomerReview();
        if (target == null) {
            error("Select a customer (or double-click a row).");
            return;
        }
        if (!approveCustomerReplacement(target)) {
            return;
        }
        editingCustomerId = target.id();
        custName.setText(target.name());
        custContact.setText(target.contact());
        custPhone.setText(target.phone());
        custEmail.setText(target.email());
        custType.setSelectedItem(friendlyCustomerType(target.type()));
        custNotes.setText(target.notes());
        clearCustomerConflictHandoff();
        clearCustomerValidation();
        markCustomerFormClean();
        updateCustomerFormMode();
        status("Loaded “" + custName.getText() + "”. Update the fields, then choose Save edit.");
    }

    private void saveCustomerEdit() {
        if (!requireCustomerAction(true)) {
            return;
        }
        if (editingCustomerId == null) {
            error("Load a customer first (Load selected / double-click).");
            return;
        }
        startCustomerSave(new CustomerSaveRequest(editingCustomerId, customerDraft(),
                custName.getText().trim()));
    }

    private void startCustomerSave(CustomerSaveRequest request) {
        if (!requireCustomerAction(true)) {
            return;
        }
        clearCustomerValidation();
        clearCustomerConflictHandoff();
        savingCustomer = true;
        customerWriteRevision++;
        if (customerTable != null) {
            customerTable.setEnabled(false);
        }
        addCustomerBtn.setText(request.editing() ? "Add customer" : "Adding customer…");
        saveCustomerBtn.setText(request.editing() ? "Saving edit…" : "Save edit");
        FarmUi.updateStatusBadge(customerModeLabel,
                request.editing() ? "Saving customer changes…" : "Adding customer…",
                FarmUi.Tone.INFO);
        applyRolePermissions(canWrite);

        new SwingWorker<Customer, Void>() {
            @Override
            protected Customer doInBackground() {
                return request.editing()
                        ? presenter.updateCustomer(request.customerId(), request.draft())
                        : presenter.addCustomer(request.draft());
            }

            @Override
            protected void done() {
                try {
                    Customer saved = get();
                    finishCustomerSave();
                    clearCustomerForm();
                    Long customerId = saved == null ? request.customerId() : saved.getId();
                    pendingCustomerWriteSuccess = new CustomerWriteSuccessPresentation(
                            request.editing() ? CustomerWriteAction.EDIT : CustomerWriteAction.ADD,
                            customerId, request.displayName());
                    pendingCustomerSelectionId = customerId;
                    applyRolePermissions(canWrite);
                    refreshData();
                    showPendingCustomerWriteGuidance();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failCustomerSave(request, exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failCustomerSave(request, exception.getCause() == null
                            ? exception : exception.getCause());
                }
            }
        }.execute();
    }

    private void failCustomerSave(CustomerSaveRequest request, Throwable failure) {
        failedCustomerDraft = true;
        customerFormRevision++;
        finishCustomerSave();
        if (failure instanceof CustomerService.DuplicateCustomerNameException duplicate) {
            conflictingCustomerId = duplicate.existingId();
            conflictingCustomerName = duplicate.customerName();
            FarmUi.markFieldError(custName, duplicate.getMessage());
            String recovery = duplicate.getMessage()
                    + " No duplicate was added and every entered detail was kept. Choose Review "
                    + "existing customer to select that account, or enter a unique name and retry.";
            FarmUi.updateStatusBadge(customerModeLabel,
                    "“" + conflictingCustomerName + "” already exists · no duplicate added",
                    FarmUi.Tone.WARNING);
            customerModeLabel.getAccessibleContext().setAccessibleDescription(recovery);
            customerModeLabel.setToolTipText(recovery);
            reviewCustomerConflictBtn.setVisible(true);
            reviewCustomerConflictBtn.setEnabled(true);
            reviewCustomerConflictBtn.getParent().revalidate();
            status("Customer “" + conflictingCustomerName
                    + "” already exists. The entered details were kept; review the existing account "
                    + "or use a unique name.");
            return;
        }
        if (failure instanceof CrmPresenter.InputValidationException validation) {
            markCustomerError(validation);
            String recovery = validation.getMessage()
                    + " Your customer details are still in the form; correct the highlighted field and retry.";
            FarmUi.updateStatusBadge(customerModeLabel,
                    "Customer not saved · correct highlighted field",
                    FarmUi.Tone.DANGER);
            customerModeLabel.getAccessibleContext().setAccessibleDescription(recovery);
            customerModeLabel.setToolTipText(recovery);
            status("Customer not saved. Correct the highlighted field and retry.");
            return;
        }
        String recovery = FarmUi.actionableError(
                request.editing() ? "Saving this customer" : "Adding this customer", failure)
                + " Your customer details were kept; retry when ready.";
        FarmUi.updateStatusBadge(customerModeLabel,
                "Customer not saved · retry available", FarmUi.Tone.DANGER);
        customerModeLabel.getAccessibleContext().setAccessibleDescription(recovery);
        customerModeLabel.setToolTipText(recovery);
        status("Customer not saved. Entered details were kept; review the reason and retry.");
        if (panel.isShowing()) {
            error(recovery);
        }
    }

    private void finishCustomerSave() {
        savingCustomer = false;
        addCustomerBtn.setText("Add customer");
        saveCustomerBtn.setText("Save edit");
        if (customerTable != null) {
            customerTable.setEnabled(true);
        }
        applyRolePermissions(canWrite);
    }

    private void clearCustomerForm() {
        editingCustomerId = null;
        custName.setText("");
        custContact.setText("");
        custEmail.setText("");
        custPhone.setText("");
        custType.setSelectedItem("Wholesale");
        custNotes.setText("");
        clearCustomerConflictHandoff();
        clearCustomerValidation();
        markCustomerFormClean();
        updateCustomerFormMode();
    }

    private void clearCustomerFormWithReview() {
        if (requireCustomerAction(false) && approveCustomerReplacement(null)) {
            clearCustomerForm();
            status("Customer form cleared. Ready to enter a new account.");
        }
    }

    private CustomerFormState currentCustomerForm() {
        return new CustomerFormState(editingCustomerId, customerDraft());
    }

    private boolean customerDraftChanged() {
        return cleanCustomerForm != null
                && (failedCustomerDraft || !cleanCustomerForm.equals(currentCustomerForm()));
    }

    private void markCustomerFormClean() {
        cleanCustomerForm = currentCustomerForm();
        failedCustomerDraft = false;
        customerFormRevision++;
    }

    private void trackCustomerFormChanges() {
        javax.swing.event.DocumentListener listener = new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            private void changed() { customerFormRevision++; }
        };
        for (JTextField field : List.of(custName, custContact, custEmail, custPhone, custNotes)) {
            field.getDocument().addDocumentListener(listener);
        }
        custType.addItemListener(event -> customerFormRevision++);
    }

    private boolean approveCustomerReplacement(CrmPresenter.CustomerRow target) {
        if (!customerDraftChanged()) {
            return true;
        }
        CustomerReplacementDecision decision = new CustomerReplacementDecision(
                currentCustomerForm(), customerFormRevision, customerWriteRevision, crmRefreshFailure);
        String draft = editingCustomerId == null ? "the new customer"
                : "customer #" + editingCustomerId;
        String action = target == null ? "clear the form"
                : "load “" + target.name() + "” (customer #" + target.id() + ")";
        Object[] options = {"Keep editing", target == null ? "Discard and clear" : "Discard and load"};
        int choice = dialogs.showOption(panel, FarmUi.confirmationPanel(
                        "Discard the unfinished customer details?",
                        "The entered details for " + draft + " will be lost when you " + action
                                + ". Save the customer first to keep these changes.", FarmUi.Tone.WARNING),
                "Discard customer changes?", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);
        if (choice != 1) {
            status("Customer changes kept. The entered fields and account being edited were preserved.");
            return false;
        }
        if (!requireCustomerAction(false)) {
            return false;
        }
        if (!decision.equals(new CustomerReplacementDecision(
                currentCustomerForm(), customerFormRevision, customerWriteRevision, crmRefreshFailure))
                || target != null && !target.equals(selectedCustomerReview())) {
            status("Customer work or the selected account changed while discard was reviewed. "
                    + "The current fields were kept; review the form and try again.");
            return false;
        }
        return true;
    }

    private void reviewExistingCustomer() {
        if (conflictingCustomerId == null || conflictingCustomerName == null) {
            return;
        }
        Long customerId = conflictingCustomerId;
        String customerName = conflictingCustomerName;
        pendingCustomerSelectionId = customerId;
        custSearch.setText(customerName);
        clearCustomerConflictHandoff();
        startCrmRefresh();
        status("Finding existing customer “" + customerName
                + "”. The unsubmitted customer details remain in the form.");
    }

    private void clearCustomerConflictHandoff() {
        conflictingCustomerId = null;
        conflictingCustomerName = null;
        if (reviewCustomerConflictBtn != null) {
            reviewCustomerConflictBtn.setEnabled(false);
            reviewCustomerConflictBtn.setVisible(false);
        }
    }

    private CrmPresenter.CustomerDraft customerDraft() {
        return new CrmPresenter.CustomerDraft(
                custName.getText(), custContact.getText(), custEmail.getText(),
                custPhone.getText(), String.valueOf(custType.getSelectedItem()),
                custNotes.getText());
    }

    private void markCustomerError(CrmPresenter.InputValidationException exception) {
        JTextField field = switch (exception.field()) {
            case "customerName" -> custName;
            case "customerEmail" -> custEmail;
            default -> custName;
        };
        FarmUi.markFieldError(field, exception.getMessage());
    }

    private void clearCustomerValidation() {
        FarmUi.clearFieldError(custName);
        FarmUi.clearFieldError(custEmail);
    }

    private void showCustomerCorrectionReady() {
        String guidance = "The highlighted customer value changed. Submit again to validate and save it.";
        FarmUi.updateStatusBadge(customerModeLabel,
                "Changed · ready to retry customer", FarmUi.Tone.INFO);
        customerModeLabel.setToolTipText(guidance);
        customerModeLabel.getAccessibleContext().setAccessibleDescription(guidance);
    }

    private void deleteCustomer() {
        if (!requireCustomerAction(true)) {
            return;
        }
        CrmPresenter.CustomerRow reviewed = selectedCustomerReview();
        if (reviewed == null) {
            error("Select a customer.");
            return;
        }
        CustomerDeleteDecision decision = new CustomerDeleteDecision(
                reviewed, editingCustomerId, customerDraft(), customerWriteRevision);
        String name = reviewed.name();
        int ok = dialogs.showConfirm(panel,
                FarmUi.confirmationPanel(
                        "Delete “" + name + "”?",
                        "This permanently removes the customer. Delete their orders first if "
                                + "the account has order history."
                                + (Objects.equals(editingCustomerId, reviewed.id())
                                ? " The loaded customer form and its unsaved changes will also be cleared."
                                : ""),
                        FarmUi.Tone.DANGER),
                "Delete customer", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        if (!requireCustomerAction(true)) {
            return;
        }
        if (!decision.equals(new CustomerDeleteDecision(selectedCustomerReview(),
                editingCustomerId, customerDraft(), customerWriteRevision))) {
            status("Customer selection, saved details, or entered work changed while deletion was reviewed. "
                    + "Nothing was deleted. Review the current account and choose Delete selected again.");
            return;
        }
        startCustomerDelete(reviewed.id(), name);
    }

    /** Deletes one captured customer without blocking Swing's event thread. */
    void startCustomerDelete(long customerId, String customerName) {
        if (!requireCustomerAction(true)) {
            return;
        }
        String name = customerName == null || customerName.isBlank()
                ? "customer #" + customerId : customerName.trim();
        deletingCustomer = true;
        customerWriteRevision++;
        customerTable.setEnabled(false);
        deleteCustomerBtn.setText("Deleting customer…");
        applyRolePermissions(canWrite);
        FarmUi.updateStatusBadge(customerModeLabel,
                "Deleting “" + name + "”…", FarmUi.Tone.INFO);
        String busy = "Deleting customer " + name + " in the background. The captured account, "
                + "selection, and customer form are locked to prevent a duplicate or wrong-account deletion.";
        customerModeLabel.setToolTipText(busy);
        customerModeLabel.getAccessibleContext().setAccessibleDescription(busy);
        status("Deleting customer “" + name + "”…");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                presenter.deleteCustomer(customerId);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    completeCustomerDelete(customerId, name);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failCustomerDelete(customerId, name, exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failCustomerDelete(customerId, name,
                            exception.getCause() == null ? exception : exception.getCause());
                }
            }
        }.execute();
    }

    private void completeCustomerDelete(long customerId, String name) {
        finishCustomerDeleteControls();
        customerTable.clearSelection();
        if (editingCustomerId != null && editingCustomerId.equals(customerId)) {
            clearCustomerForm();
        }
        pendingCustomerWriteSuccess = new CustomerWriteSuccessPresentation(
                CustomerWriteAction.DELETE, customerId, name);
        applyRolePermissions(canWrite);
        refreshData();
        showPendingCustomerWriteGuidance();
    }

    private boolean customerWriteTargetCurrent(CustomerWriteSuccessPresentation presentation) {
        boolean found = false;
        for (int row = 0; row < customerModel.getRowCount(); row++) {
            Object rowId = customerModel.getValueAt(row, 0);
            String rowName = String.valueOf(customerModel.getValueAt(row, 1));
            if ((presentation.customerId() != null
                    && Objects.equals(presentation.customerId(), rowId))
                    || (presentation.customerId() == null
                    && presentation.name().equalsIgnoreCase(rowName.trim()))) {
                found = true;
                break;
            }
        }
        return presentation.action() == CustomerWriteAction.DELETE ? !found : found;
    }

    private void showPendingCustomerWriteGuidance() {
        if (pendingCustomerWriteSuccess == null) {
            return;
        }
        CustomerWriteSuccessPresentation presentation = pendingCustomerWriteSuccess;
        String verb = presentation.action() == CustomerWriteAction.DELETE ? "deleted" : "saved";
        String recovery = "Customer “" + presentation.name() + "” was " + verb
                + " once. Customer mutations are locked until CRM reloads authoritative data. "
                + "Choose Refresh CRM only; do not repeat the "
                + (presentation.action() == CustomerWriteAction.DELETE ? "deletion" : "save") + ".";
        FarmUi.updateStatusBadge(customerModeLabel,
                "Customer " + verb + " once · reload required", FarmUi.Tone.WARNING);
        customerModeLabel.setToolTipText(recovery);
        customerModeLabel.getAccessibleContext().setAccessibleDescription(recovery);
        status(recovery);
    }

    private void applyCustomerWriteSuccessPresentation(
            CustomerWriteSuccessPresentation presentation,
            boolean refreshSucceeded, boolean targetCurrent) {
        String verb = presentation.action() == CustomerWriteAction.DELETE ? "deleted" : "saved";
        if (!refreshSucceeded) {
            showPendingCustomerWriteGuidance();
            return;
        }
        String message;
        FarmUi.Tone tone;
        if (targetCurrent) {
            message = "Customer " + verb + " · “" + presentation.name() + "”";
            tone = FarmUi.Tone.SUCCESS;
        } else {
            message = "Customer " + verb + " · outside current search";
            tone = FarmUi.Tone.WARNING;
        }
        String detail = targetCurrent
                ? "The database confirmed that customer “" + presentation.name() + "” was "
                        + verb + " once, and CRM reloaded authoritative customer data."
                : "Customer “" + presentation.name() + "” was " + verb
                        + " once, and CRM reloaded successfully, but the current customer search "
                        + "does not show the expected result. Clear the search to review it; do not repeat the write.";
        FarmUi.updateStatusBadge(customerModeLabel, message, tone);
        customerModeLabel.setToolTipText(detail);
        customerModeLabel.getAccessibleContext().setAccessibleDescription(detail);
        status(switch (presentation.action()) {
            case ADD -> "Added customer “" + presentation.name() + "”.";
            case EDIT -> "Saved changes to “" + presentation.name() + "”.";
            case DELETE -> "Deleted customer “" + presentation.name() + "”.";
        });
    }

    private void failCustomerDelete(long customerId, String name, Throwable failure) {
        finishCustomerDeleteControls();
        restoreSelectedId(customerTable, customerModel, customerId);
        String detail = FarmUi.actionableError("Deleting this customer", failure);
        String recovery = detail + " The account, selection, and any loaded customer form were "
                + "kept. Delete linked orders first when applicable, then retry.";
        FarmUi.updateStatusBadge(customerModeLabel,
                "Customer not deleted · retry available", FarmUi.Tone.DANGER);
        customerModeLabel.setToolTipText(recovery);
        customerModeLabel.getAccessibleContext().setAccessibleDescription(recovery);
        status("Customer “" + name
                + "” was not deleted. The account remains selected; review linked orders and retry.");
        if (panel.isShowing()) {
            error(recovery);
        }
    }

    private void finishCustomerDeleteControls() {
        deletingCustomer = false;
        deleteCustomerBtn.setText("Delete selected");
        customerTable.setEnabled(true);
        applyRolePermissions(canWrite);
    }

    private void exportCustomerStatement() {
        if (!presenter.statementsAvailable()) {
            error("Customer statements are unavailable in this session. Restart the application and try again.");
            return;
        }
        int row = customerTable.getSelectedRow();
        if (row < 0) {
            error("Select a customer for the account statement.");
            return;
        }
        Long id = (Long) customerModel.getValueAt(row, 0);
        String name = String.valueOf(customerModel.getValueAt(row, 1));
        try {
            FarmUi.clearFieldError(filterFrom);
            FarmUi.clearFieldError(filterTo);
            CrmPresenter.StatementRange range = presenter.statementRange(
                    filterFrom.getText(), filterTo.getText(), LocalDate.now());
            startCustomerStatement(id, name, range.from(), range.to());
        } catch (CrmPresenter.InputValidationException ex) {
            JTextField field = "statementFrom".equals(ex.field()) ? filterFrom : filterTo;
            FarmUi.markFieldError(field, ex.getMessage());
            showDocumentInputFailure(ex.getMessage());
        }
    }

    void startCustomerStatement(long customerId, String customerName,
                                LocalDate from, LocalDate to) {
        if (!beginDocumentWork("Preparing customer statement…",
                "Orders and account totals are loading in the background.")) {
            return;
        }
        new SwingWorker<CustomerStatementService.CustomerStatement, Void>() {
            @Override
            protected CustomerStatementService.CustomerStatement doInBackground() {
                return presenter.statement(customerId, from, to);
            }

            @Override
            protected void done() {
                try {
                    CustomerStatementService.CustomerStatement statement = get();
                    completeDocumentWork("Statement ready · " + statement.orderCount()
                                    + " order" + (statement.orderCount() == 1 ? "" : "s"),
                            "The captured account statement is ready for review or PDF export.",
                            FarmUi.Tone.SUCCESS,
                            "Statement: " + statement.orderCount() + " order(s) · $"
                                    + String.format("%.2f", statement.grandTotal()));
                    if (panel.isShowing() || dialogs != FarmDialogs.swing()) {
                        presentCustomerStatement(statement,
                                customerName == null ? statement.customerName() : customerName);
                    }
                } catch (Exception exception) {
                    failDocumentWork("Preparing the customer statement",
                            unwrapDocumentFailure(exception));
                }
            }
        }.execute();
    }

    private void presentCustomerStatement(
            CustomerStatementService.CustomerStatement statement, String customerName) {
        JTextArea area = documentArea(statement.plainText());
        Object[] options = {"Export PDF…", "Close"};
        int choice = dialogs.showOption(panel, new JScrollPane(area),
                "Statement — " + customerName + " (" + statement.from()
                        + " → " + statement.to() + ")",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[1]);
        if (choice == 0) {
            File file = files.chooseSave(panel, "Save customer statement PDF",
                    new File("statement-customer-" + statement.customerId() + ".pdf"))
                    .orElse(null);
            if (file != null && approveReplacement(file)
                    && beginDocumentWork("Creating statement PDF…",
                    "The reviewed customer statement is being rendered in the background.")) {
                startDocumentFileExport(file,
                        () -> com.flowerfarm.safety.SafeFiles.write(file.toPath(), presenter.statementPdf(statement)),
                        "Statement PDF saved · " + statement.orderCount() + " orders",
                        "Statement PDF → " + file.getName()
                                + " (" + statement.orderCount() + " orders)",
                        "Saving the customer statement PDF");
            }
        }
    }

    private void createOrder() {
        if (!GuiPermissions.requireWrite(host, panel, "create orders")) {
            return;
        }
        if (blockPendingProductWriteRetry()) {
            return;
        }
        if (blockPendingDestructiveSuccessRetry()) {
            return;
        }
        if (blockPendingCorrectionSuccessRetry()) {
            return;
        }
        if (pendingAddedProductOrderId != null) {
            String productName = pendingAddedProductName == null
                    ? "The product" : "“" + pendingAddedProductName + "”";
            status(productName + " was already saved once to order #"
                    + pendingAddedProductOrderId
                    + ". Retry Refresh CRM only; do not create or add it again.");
            return;
        }
        if (pendingCreatedOrderSelectionId != null) {
            String recovery = "Order #" + pendingCreatedOrderSelectionId
                    + " was already saved once. Retry Refresh CRM only; do not create it again.";
            FarmUi.updateStatusBadge(selectedOrderLabel,
                    "Order #" + pendingCreatedOrderSelectionId + " already saved · refresh only",
                    FarmUi.Tone.WARNING);
            selectedOrderLabel.setToolTipText(recovery);
            selectedOrderLabel.getAccessibleContext().setAccessibleDescription(recovery);
            status(recovery);
            return;
        }
        if (unavailableProductDraftOrderId != null) {
            status("Product not submitted. Order #" + unavailableProductDraftOrderId
                    + " is missing; choose Move product to new order or Clear product entry.");
            return;
        }
        CrmPresenter.CustomerChoice ci =
                (CrmPresenter.CustomerChoice) orderCustomer.getSelectedItem();
        if (ci == null) {
            error("Add a customer first.");
            return;
        }
        if (!validateNewOrderIdentity(true)) {
            return;
        }
        startOrderCreation(new OrderSaveRequest(ci, new CrmPresenter.OrderDraft(
                ci.id(), orderDate.getText(), orderProduct.getText(), orderQty.getText(),
                orderUnit.getText(),
                orderPrice.getText(), String.valueOf(orderStatus.getSelectedItem()),
                orderNotes.getText(), String.valueOf(orderHandoffType.getSelectedItem()))));
    }

    private void submitOrderLineEntry() {
        if (unavailableProductDraftOrderId != null) {
            status("Product not submitted. Order #" + unavailableProductDraftOrderId
                    + " is missing; choose Move product to new order or Clear product entry.");
            return;
        }
        if (selectedOrderId() == null) {
            createOrder();
        } else {
            addProductToSelectedOrder();
        }
    }

    private void startOrderCreation(OrderSaveRequest request) {
        if (creatingOrder || addingOrderLine || pendingCreatedOrderSelectionId != null) {
            return;
        }
        clearOrderValidation();
        creatingOrder = true;
        createOrderBtn.setText("Checking existing orders…");
        FarmUi.updateStatusBadge(selectedOrderLabel,
                "Checking “" + request.customer().name() + "” on "
                        + request.draft().orderDate() + "…",
                FarmUi.Tone.INFO);
        applyRolePermissions(canWrite);

        new SwingWorker<List<CrmPresenter.ActiveOrderMatch>, Void>() {
            @Override
            protected List<CrmPresenter.ActiveOrderMatch> doInBackground() {
                return presenter.activeOrdersForCustomerDate(
                        request.customer().id(), request.draft().orderDate());
            }

            @Override
            protected void done() {
                try {
                    List<CrmPresenter.ActiveOrderMatch> matches = get();
                    if (matches.isEmpty()) {
                        runOrderCreation(request);
                    } else {
                        finishOrderCreation();
                        reviewSameDayOrderCreation(request, matches);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failOrderCreation(exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failOrderCreation(exception.getCause() == null
                            ? exception : exception.getCause());
                }
            }
        }.execute();
    }

    private void runOrderCreation(OrderSaveRequest request) {
        boolean controlsAlreadyLocked = creatingOrder;
        creatingOrder = true;
        createOrderBtn.setText("Creating order…");
        FarmUi.updateStatusBadge(selectedOrderLabel,
                "Creating " + request.draft().orderDate() + " order for “"
                        + request.customer().name() + "”…",
                FarmUi.Tone.INFO);
        if (!controlsAlreadyLocked) {
            applyRolePermissions(canWrite);
        }
        new SwingWorker<CustomerOrder, Void>() {
            @Override
            protected CustomerOrder doInBackground() {
                return presenter.createOrder(request.draft(),
                        request.reviewedActiveOrders(), request.separateOrderApproved());
            }

            @Override
            protected void done() {
                try {
                    CustomerOrder created = get();
                    finishOrderCreation();
                    resetOrderProductFields();
                    clearStoredProductDraft(null);
                    restorePausedNewOrderProductDraft();
                    acceptNewOrderIdentityBaseline();
                    orderNotes.setText("");
                    focusCreatedOrder(created, request);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failOrderCreation(exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failOrderCreation(exception.getCause() == null
                            ? exception : exception.getCause());
                }
            }
        }.execute();
    }

    private void reviewSameDayOrderCreation(
            OrderSaveRequest request, List<CrmPresenter.ActiveOrderMatch> matches) {
        String summary = sameDayOrderSummary(request, matches);
        FarmUi.updateStatusBadge(selectedOrderLabel,
                matches.size() + " active " + (matches.size() == 1 ? "order" : "orders")
                        + " already on " + request.draft().orderDate() + " · draft kept",
                FarmUi.Tone.WARNING);
        selectedOrderLabel.setToolTipText(summary);
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(summary);
        createOrderBtn.setToolTipText(summary);
        createOrderBtn.getAccessibleContext().setAccessibleDescription(summary);
        status("Order not created. " + matches.size() + " active order"
                + (matches.size() == 1 ? " already exists" : "s already exist")
                + " for “" + request.customer().name() + "” on "
                + request.draft().orderDate() + "; the complete draft was kept.");

        JComboBox<CrmPresenter.ActiveOrderMatch> existingOrders = new JComboBox<>(
                matches.toArray(CrmPresenter.ActiveOrderMatch[]::new));
        existingOrders.getAccessibleContext().setAccessibleName(
                "Existing active order to open");
        existingOrders.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean selected, boolean focused) {
                Object label = value instanceof CrmPresenter.ActiveOrderMatch match
                        ? match.label() : value;
                return super.getListCellRendererComponent(
                        list, label, index, selected, focused);
            }
        });
        JPanel review = new JPanel(new BorderLayout(0, 8));
        review.setOpaque(false);
        review.add(FarmUi.confirmationPanel(
                "Active order already exists for this handoff date",
                summary, FarmUi.Tone.WARNING), BorderLayout.CENTER);
        JPanel selector = new JPanel(new BorderLayout(8, 0));
        selector.setOpaque(false);
        JLabel selectorLabel = new JLabel("Open existing order:");
        selectorLabel.setLabelFor(existingOrders);
        selector.add(selectorLabel, BorderLayout.WEST);
        selector.add(existingOrders, BorderLayout.CENTER);
        review.add(selector, BorderLayout.SOUTH);
        Object[] options = {"Open selected order", "Create separate order", "Keep draft"};
        int choice = panel.isShowing()
                ? JOptionPane.showOptionDialog(panel, review,
                        "Review possible duplicate order", JOptionPane.DEFAULT_OPTION,
                        JOptionPane.WARNING_MESSAGE, null, options, options[2])
                : 2;
        if (choice == 0) {
            CrmPresenter.ActiveOrderMatch selected =
                    (CrmPresenter.ActiveOrderMatch) existingOrders.getSelectedItem();
            openExistingOrderForProduct(selected, request.customer().name(),
                    request.draft().orderDate(), new CrmPresenter.LineDraft(
                            request.draft().product(), request.draft().quantity(),
                            request.draft().unit(), request.draft().unitPrice()));
        } else if (choice == 1) {
            runOrderCreation(request.approveSeparate(matches));
        } else {
            FarmUi.revealAndFocus(createOrderBtn);
        }
    }

    void openExistingOrderForProduct(
            CrmPresenter.ActiveOrderMatch match, String customerName, String orderDate,
            CrmPresenter.LineDraft line) {
        if (match == null || match.row() == null || match.row().id() == null || line == null) {
            status("The existing order could not be opened. The new-order draft was kept.");
            return;
        }
        Long orderId = match.row().id();
        ProductEntryDraft transferred = new ProductEntryDraft(
                line.product(), line.quantity(), line.unit(), line.unitPrice(),
                null, null, match.reviewedOrder());
        if (!transferred.hasWork(defaultUnit)) {
            status("The existing order was not opened because there is no product entry to move.");
            return;
        }
        orderProductDrafts.put(orderId, transferred);
        newOrderProductDraft = null;
        activeProductEntryOrderId = null;
        applyProductEntryDraft(ProductEntryDraft.empty(defaultUnit));
        pendingExistingOrderProductId = orderId;
        pendingExistingOrderProductName = transferred.product().trim();
        pendingOrderSelectionId = orderId;
        filterStatus.setSelectedItem("All statuses");
        filterHandoff.setSelectedItem("All handoffs");
        filterCustomer.setText(customerName == null ? "" : customerName);
        filterFrom.setText(orderDate == null ? "" : orderDate);
        filterTo.setText(orderDate == null ? "" : orderDate);
        String message = "Opening order #" + orderId + " with the unsaved “"
                + pendingExistingOrderProductName + "” entry bound to that exact order…";
        FarmUi.updateStatusBadge(selectedOrderLabel, message, FarmUi.Tone.INFO);
        selectedOrderLabel.setToolTipText(
                "CRM is reloading the reviewed order before the preserved product can be added.");
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(
                selectedOrderLabel.getToolTipText());
        status(message);
        refreshData();
    }

    private static String sameDayOrderSummary(
            OrderSaveRequest request, List<CrmPresenter.ActiveOrderMatch> matches) {
        String existing = matches.stream()
                .map(CrmPresenter.ActiveOrderMatch::label)
                .collect(java.util.stream.Collectors.joining("; "));
        return "“" + request.customer().name() + "” already has " + matches.size()
                + " active order" + (matches.size() == 1 ? "" : "s") + " on "
                + request.draft().orderDate() + ": " + existing + ". Open the intended order to "
                + "move this unsaved product entry into an exact-order review. Choose Create "
                + "separate order only when the customer truly needs another order. Keeping the "
                + "draft changes nothing and preserves every entered field.";
    }

    private void failOrderCreation(Throwable failure) {
        finishOrderCreation();
        if (failure instanceof OrderService.DuplicateOrderReviewChangedException changed) {
            rememberCurrentProductDraft();
            String recovery = changed.getMessage()
                    + " The complete new-order draft was kept; use Create & select order again "
                    + "after the current active orders load.";
            pendingOrderCreationConflict = recovery;
            refreshData();
            if (host != null) {
                host.refreshAll();
            }
            applyOrderCreationConflict(recovery);
            return;
        }
        if (failure instanceof CrmPresenter.InputValidationException validation) {
            markOrderError(validation);
            String recovery = validation.getMessage()
                    + " Your other order details are still in the form; correct the highlighted field and retry.";
            FarmUi.updateStatusBadge(selectedOrderLabel,
                    "Order not created · correct highlighted field", FarmUi.Tone.DANGER);
            selectedOrderLabel.getAccessibleContext().setAccessibleDescription(recovery);
            selectedOrderLabel.setToolTipText(recovery);
            status("Order not created. Correct the highlighted field and retry.");
            return;
        }
        String recovery = FarmUi.actionableError("Creating this order", failure)
                + " Your order details were kept; retry when ready.";
        FarmUi.updateStatusBadge(selectedOrderLabel,
                "Order not created · retry available", FarmUi.Tone.DANGER);
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(recovery);
        selectedOrderLabel.setToolTipText(recovery);
        status("Order not created. Entered details were kept; review the reason and retry.");
        if (panel.isShowing()) {
            error(recovery);
        }
    }

    private void applyOrderCreationConflict(String recovery) {
        FarmUi.updateStatusBadge(selectedOrderLabel,
                "Active orders changed · draft kept · review again", FarmUi.Tone.WARNING);
        selectedOrderLabel.setToolTipText(recovery);
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(recovery);
        status("Order not created because the same-customer/date queue changed. The complete "
                + "draft was kept and CRM is current for another duplicate review.");
    }

    private void finishOrderCreation() {
        creatingOrder = false;
        createOrderBtn.setText("Create & select order");
        applyRolePermissions(canWrite);
    }

    private void focusCreatedOrder(CustomerOrder created, OrderSaveRequest request) {
        Long orderId = created.getId();
        pendingOrderSelectionId = orderId;
        pendingCreatedOrderSelectionId = orderId;
        pendingCreatedOrderDescription = "Draft order #" + orderId + " for “"
                + request.customer().name() + "” on " + request.draft().orderDate();
        filterStatus.setSelectedItem("Draft");
        filterHandoff.setSelectedItem("All handoffs");
        filterCustomer.setText(request.customer().name());
        filterFrom.setText(request.draft().orderDate());
        filterTo.setText(request.draft().orderDate());
        FarmUi.updateStatusBadge(selectedOrderLabel,
                "Order #" + orderId + " saved · loading exact order", FarmUi.Tone.SUCCESS);
        selectedOrderLabel.setToolTipText(
                "The order is saved. CRM is reloading its exact customer and date so it can be "
                        + "selected for more products or confirmation.");
        status("Draft order #" + orderId + " was saved. Loading its exact customer and date "
                + "for selection; do not submit it again.");
        refreshData();
    }

    private void addProductToSelectedOrder() {
        boolean editing = editingOrderLineId != null;
        if (!GuiPermissions.requireWrite(host, panel,
                editing ? "correct products on orders" : "add products to orders")) {
            return;
        }
        if (blockPendingProductWriteRetry()) {
            return;
        }
        if (blockPendingDestructiveSuccessRetry()) {
            return;
        }
        if (blockPendingCorrectionSuccessRetry()) {
            return;
        }
        if (pendingAddedProductOrderId != null) {
            String productName = pendingAddedProductName == null
                    ? "The product" : "“" + pendingAddedProductName + "”";
            status(productName + " was already saved once to order #"
                    + pendingAddedProductOrderId
                    + ". Retry Refresh CRM only; do not add it again.");
            return;
        }
        Long id = selectedOrderId();
        if (id == null) {
            error("Select the order that should receive this product.");
            return;
        }
        if (!Objects.equals(id, activeProductEntryOrderId)) {
            orderSelectionChanged();
            status("Product not added. The entry target changed and was resynchronized; review "
                    + "order #" + id + " before retrying.");
            return;
        }
        if (editing && !id.equals(editingOrderId)) {
            error("The order selection changed. Select the saved product again before editing.");
            clearOrderLineEditState(true);
            updateOrderActions();
            return;
        }
        ProductOrderConflict productConflict = editing ? null : productOrderConflict(id);
        if (productConflict != null) {
            reviewProductOrderConflict(productConflict);
            return;
        }
        CustomerOrder reviewedOrder = lastOrderView.stream()
                .filter(candidate -> id.equals(candidate.getId()))
                .findFirst()
                .orElse(null);
        if (reviewedOrder == null) {
            status("Product not added. Order #" + id
                    + " is no longer available; refresh Customers and Orders before retrying.");
            return;
        }
        CrmPresenter.LineDraft draft = currentOrderLineDraft();
        List<OrderLine> matchingLines = reviewedOrder.getLines().stream()
                .filter(line -> line.getProductName() != null
                        && line.getProductName().equalsIgnoreCase(
                                draft.product().trim()))
                .toList();
        if (!editing && !matchingLines.isEmpty() && !duplicateLineApprovalMatches(id)) {
            duplicateLineApproval = new DuplicateLineApproval(
                    id, normalizedLineDraft(draft), presenter.reviewSnapshot(reviewedOrder));
            updateOrderActions();
            String guidance = "“" + draft.product().trim() + "” is already saved on order #"
                    + id + " in " + matchingLines.size() + " line"
                    + (matchingLines.size() == 1 ? "" : "s")
                    + ". Review the existing saved product. Choose Add separate product line "
                    + "again only when a distinct price, grade, or allocation is intentional.";
            FarmUi.updateStatusBadge(orderLinePreviewLabel,
                    "Duplicate product paused · review saved line", FarmUi.Tone.WARNING);
            orderLinePreviewLabel.setToolTipText(guidance);
            orderLinePreviewLabel.getAccessibleContext().setAccessibleDescription(guidance);
            addOrderLineBtn.setToolTipText(guidance);
            addOrderLineBtn.getAccessibleContext().setAccessibleDescription(guidance);
            FarmUi.revealAndFocus(addOrderLineBtn);
            status("Product not added. " + guidance);
            return;
        }
        duplicateLineApproval = null;
        startOrderLineSave(new OrderLineSaveRequest(id,
                draft,
                orderProduct.getText().trim(), editing ? editingOrderLineId : null,
                editing ? editingOrderLineReview : null,
                editing ? editingOrderReview : presenter.reviewSnapshot(reviewedOrder)));
    }

    private void startOrderLineSave(OrderLineSaveRequest request) {
        if (addingOrderLine || creatingOrder || pendingAddedProductOrderId != null
                || pendingProductWriteSuccess != null || pendingDestructiveSuccess != null
                || pendingCorrectionSuccess != null) {
            return;
        }
        clearOrderValidation();
        addingOrderLine = true;
        orderTable.setEnabled(false);
        addOrderLineBtn.setText(request.editing() ? "Saving changes…" : "Adding product…");
        FarmUi.updateStatusBadge(selectedOrderLabel,
                (request.editing() ? "Saving changes to “" : "Adding “")
                        + request.productName() + (request.editing() ? "” on order #" : "” to order #")
                        + request.orderId() + "…",
                FarmUi.Tone.INFO);
        applyRolePermissions(canWrite);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                if (request.editing()) {
                    presenter.updateOrderLine(
                            request.orderId(), request.lineId(), request.reviewedOrder(),
                            request.reviewedLine(), request.draft());
                } else {
                    presenter.addOrderLine(
                            request.orderId(), request.reviewedOrder(), request.draft());
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    editingOrderId = null;
                    editingOrderLineId = null;
                    editingOrderLineReview = null;
                    editingOrderReview = null;
                    finishOrderLineSave();
                    resetOrderProductFields();
                    clearStoredProductDraft(request.orderId());
                    pendingOrderSelectionId = request.orderId();
                    if (request.editing()) {
                        pendingProductWriteSuccess = new ProductWriteSuccessPresentation(
                                request.orderId(), request.productName(), false,
                                "Changes to “" + request.productName() + "” on order #"
                                        + request.orderId() + " were saved once.");
                    } else {
                        pendingAddedProductOrderId = request.orderId();
                        pendingAddedProductName = request.productName();
                    }
                    refreshData();
                    if (host != null) {
                        host.refreshAll();
                    }
                    status(request.editing()
                            ? "Updated “" + request.productName() + "” on order #"
                                    + request.orderId()
                                    + ". Order totals, Market Day, and stock guidance are refreshing; "
                                    + "inventory was not changed."
                            : "Added “" + request.productName() + "” to order #"
                                    + request.orderId() + ".");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failOrderLineSave(request, exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failOrderLineSave(request, exception.getCause() == null
                            ? exception : exception.getCause());
                }
            }
        }.execute();
    }

    private void failOrderLineSave(OrderLineSaveRequest request, Throwable failure) {
        finishOrderLineSave();
        String recovery;
        if (failure instanceof OrderService.StaleOrderReviewException staleOrder) {
            rememberCurrentProductDraft();
            if (request.editing()) {
                pendingProductEditReviewRebaseId = request.orderId();
            }
            if (staleOrder.orderMissing()) {
                unavailableProductDraftOrderId = request.orderId();
            }
            pendingOrderSelectionId = request.orderId();
            refreshData();
            if (host != null) {
                host.refreshAll();
            }
            recovery = failure.getMessage()
                    + (request.editing()
                    ? " Your entered product correction was kept. Compare it with the refreshed "
                            + "customer, date, handoff, status, instructions, and saved products "
                            + "before saving again."
                    : " Your entered product was kept for order #" + request.orderId()
                            + ". Compare it with the refreshed customer, date, status, instructions, "
                            + "and saved products before adding it again.");
            pendingProductConflict = new ProductConflictPresentation(
                    staleOrder.orderMissing()
                            ? "Order #" + request.orderId() + " missing · product preserved"
                            : "Order #" + request.orderId() + " changed · product "
                                    + (request.editing() ? "correction kept" : "not added"),
                    recovery + (staleOrder.orderMissing()
                            ? " The target order no longer exists. Choose Move product to new "
                                    + "order to recover this entry deliberately, or Clear product "
                                    + "entry to discard it."
                            : ""),
                    "Order #" + request.orderId()
                            + (staleOrder.orderMissing()
                            ? " no longer exists. The product was not added, inventory was "
                                    + "unchanged, and the entry is preserved for explicit recovery."
                            : " changed before the product saved. "
                                    + (request.editing()
                                    ? "The correction was not applied"
                                    : "It was not added")
                                    + ", inventory was unchanged, and the entered product was kept "
                                    + "for review."));
            applyProductConflictPresentation(pendingProductConflict);
        } else if (failure instanceof OrderService.StaleOrderLineException stale) {
            editingOrderLineReview = stale.currentLine();
            rememberCurrentProductDraft();
            pendingProductEditReviewRebaseId = request.orderId();
            pendingOrderSelectionId = request.orderId();
            refreshData();
            if (host != null) {
                host.refreshAll();
            }
            recovery = stale.getMessage()
                    + " Your entered product correction was kept while the current saved line "
                    + "refreshes. Compare both, then save again only if still intended.";
            pendingProductConflict = new ProductConflictPresentation(
                    "Saved product changed · refresh and review", recovery,
                    "The saved product on order #" + request.orderId()
                            + " changed first. No order or inventory value changed; the current "
                            + "line is visible and your correction was kept for comparison.");
            applyProductConflictPresentation(pendingProductConflict);
        } else if (failure instanceof CrmPresenter.InputValidationException validation) {
            markOrderError(validation);
            recovery = validation.getMessage()
                    + " The product details remain in the form; correct the highlighted field and retry.";
            FarmUi.updateStatusBadge(selectedOrderLabel,
                    request.editing()
                            ? "Product changes not saved · correct highlighted field"
                            : "Product not added · correct highlighted field",
                    FarmUi.Tone.DANGER);
        } else {
            recovery = FarmUi.actionableError(
                    (request.editing() ? "Updating" : "Adding")
                            + " this product on order #" + request.orderId(), failure)
                    + " The product details were kept; retry when ready.";
            FarmUi.updateStatusBadge(selectedOrderLabel,
                    request.editing()
                            ? "Product changes not saved · retry available"
                            : "Product not added · retry available",
                    FarmUi.Tone.DANGER);
        }
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(recovery);
        selectedOrderLabel.setToolTipText(recovery);
        status(failure instanceof OrderService.StaleOrderReviewException
                ? "Order #" + request.orderId()
                        + " changed before the product saved. "
                        + (request.editing() ? "The correction was not applied" : "It was not added")
                        + ", inventory was unchanged, and the entered product was kept for review."
                : failure instanceof OrderService.StaleOrderLineException
                ? "The saved product on order #" + request.orderId()
                        + " changed first. No order or inventory value changed; the current line "
                        + "is refreshing and your correction was kept for comparison."
                : request.editing()
                ? "Product changes were not saved on order #" + request.orderId()
                        + ". Entered details were kept; review the reason and retry."
                : "Product not added to order #" + request.orderId()
                        + ". Entered details were kept; review the reason and retry.");
        if (!(failure instanceof CrmPresenter.InputValidationException)
                && panel.isShowing()) {
            error(recovery);
        }
    }

    private void finishOrderLineSave() {
        addingOrderLine = false;
        addOrderLineBtn.setText(editingOrderLineId == null
                ? "Add product to selected order" : "Save product changes");
        orderTable.setEnabled(true);
        applyRolePermissions(canWrite);
    }

    private void applyProductConflictPresentation(ProductConflictPresentation presentation) {
        FarmUi.updateStatusBadge(selectedOrderLabel,
                presentation.message(), FarmUi.Tone.DANGER);
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(
                presentation.recovery());
        selectedOrderLabel.setToolTipText(presentation.recovery());
        status(presentation.hostStatus());
    }

    private void rebasePendingProductEditReview() {
        Long orderId = pendingProductEditReviewRebaseId;
        if (orderId == null || crmRefreshFailure != null
                || !Objects.equals(orderId, selectedOrderId())) {
            return;
        }
        OrderService.OrderReviewSnapshot current = reviewedOrderSnapshot(orderId);
        ProductEntryDraft draft = orderProductDrafts.get(orderId);
        if (current == null || draft == null || draft.editingLineId() == null) {
            return;
        }
        editingOrderReview = current;
        orderProductDrafts.put(orderId, draft.withReviewedOrder(current));
        pendingProductEditReviewRebaseId = null;
    }

    private Long selectedOrderId() {
        int row = orderTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return (Long) orderModel.getValueAt(row, 0);
    }

    private static void updateCorrectionConflict(
            Map<Long, String> conflicts, Long orderId,
            String reviewedBaseline, String authoritative) {
        if (orderId == null) {
            return;
        }
        if (Objects.equals(reviewedBaseline, authoritative)) {
            conflicts.remove(orderId);
        } else {
            conflicts.put(orderId, authoritative);
        }
    }

    private static String correctionConflictGuidance(
            String field, long orderId, String draft, String saved) {
        return "Order #" + orderId + " has a newer saved " + field + ": “"
                + compactContext(saved, 180) + "”. Your preserved draft is: “"
                + compactContext(draft, 180) + "”. Review both values first; this click only "
                + "acknowledges the newer saved value and does not save your draft.";
    }

    private void reviewCorrectionConflict(
            String field, long orderId, String draft, String saved, Runnable acknowledge) {
        String guidance = correctionConflictGuidance(field, orderId, draft, saved);
        int choice = panel.isShowing()
                ? JOptionPane.showConfirmDialog(
                        panel,
                        FarmUi.confirmationPanel(
                                "Review newer saved " + field + " for order #" + orderId,
                                guidance + "\n\nChoose Yes only to acknowledge the newer saved "
                                        + "value. Your draft remains unsaved, and you must choose "
                                        + "Save again if you still intend to replace it.",
                                FarmUi.Tone.WARNING),
                        "Review newer saved " + field,
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE)
                : JOptionPane.YES_OPTION;
        if (choice != JOptionPane.YES_OPTION) {
            status("Review cancelled for order #" + orderId + ". " + guidance);
            return;
        }
        acknowledge.run();
        updateOrderActions();
        String message = "Order #" + orderId + " newer saved " + field
                + " reviewed · draft still unsaved";
        String recovery = "The newer saved " + field + " for order #" + orderId
                + " was acknowledged. Your draft “" + compactContext(draft, 180)
                + "” remains in the editor and nothing was saved. Choose Save only if that draft "
                + "should replace the reviewed saved value “" + compactContext(saved, 180) + "”.";
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, message, FarmUi.Tone.WARNING);
        fulfillmentStatusLabel.setToolTipText(recovery);
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(recovery);
        status(recovery);
    }

    private void applyCorrectionConflictGuidance(Long orderId) {
        if (orderId == null) {
            return;
        }
        List<String> fields = new ArrayList<>();
        if (selectedOrderDateConflicts.containsKey(orderId)) {
            fields.add("date");
        }
        if (selectedOrderHandoffConflicts.containsKey(orderId)) {
            fields.add("handoff type");
        }
        if (selectedOrderNotesConflicts.containsKey(orderId)) {
            fields.add("instructions");
        }
        if (fields.isEmpty()) {
            return;
        }
        String names = String.join(", ", fields);
        String message = "Order #" + orderId + " changed · review newer saved " + names;
        String recovery = "Your local " + names + " draft"
                + (fields.size() == 1 ? " was" : "s were")
                + " kept, but CRM loaded newer saved work. Use each Review newer saved control "
                + "to compare and acknowledge the current value before Save becomes available. "
                + "No draft or database value has been changed.";
        FarmUi.updateStatusBadge(selectedOrderLabel, message, FarmUi.Tone.WARNING);
        selectedOrderLabel.setToolTipText(recovery);
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(recovery);
    }

    private void selectedOrderDateChanged() {
        if (!updatingSelectedOrderDate) {
            rememberSelectedOrderDateDraft();
            updateOrderActions();
        }
    }

    private void rememberSelectedOrderDateDraft() {
        if (selectedOrderDateOrderId == null) {
            return;
        }
        String value = selectedOrderDate.getText().trim();
        if (value.equals(selectedOrderDateBaseline)) {
            selectedOrderDateDrafts.remove(selectedOrderDateOrderId);
            selectedOrderDateDraftBaselines.remove(selectedOrderDateOrderId);
            selectedOrderDateConflicts.remove(selectedOrderDateOrderId);
        } else {
            selectedOrderDateDrafts.put(selectedOrderDateOrderId, value);
            selectedOrderDateDraftBaselines.putIfAbsent(
                    selectedOrderDateOrderId, selectedOrderDateBaseline);
        }
    }

    private void replaceSelectedOrderDate(Long orderId) {
        if (selectedOrderDate == null) {
            return;
        }
        String authoritative = orderId == null ? "" : lastOrderView.stream()
                .filter(order -> orderId.equals(order.getId()))
                .findFirst()
                .map(order -> order.getOrderDate().toString())
                .orElse("");
        if (orderId != null && orderId.equals(pendingOrderDateRefreshId)) {
            if (!authoritative.equals(pendingOrderDateRefreshValue)) {
                return;
            }
            pendingOrderDateRefreshId = null;
            pendingOrderDateRefreshValue = null;
        }
        boolean sameOrder = orderId != null && orderId.equals(selectedOrderDateOrderId);
        boolean dirty = sameOrder
                && !selectedOrderDate.getText().trim().equals(selectedOrderDateBaseline);
        if (dirty) {
            String draft = selectedOrderDate.getText().trim();
            if (draft.equals(authoritative)) {
                selectedOrderDateDrafts.remove(orderId);
                selectedOrderDateDraftBaselines.remove(orderId);
                selectedOrderDateConflicts.remove(orderId);
                selectedOrderDateBaseline = authoritative;
                return;
            }
            String reviewed = selectedOrderDateDraftBaselines.getOrDefault(
                    orderId, selectedOrderDateBaseline);
            selectedOrderDateBaseline = reviewed;
            updateCorrectionConflict(
                    selectedOrderDateConflicts, orderId, reviewed, authoritative);
            return;
        }
        String draft = orderId == null ? null : selectedOrderDateDrafts.get(orderId);
        if (orderId != null && authoritative.equals(draft)) {
            selectedOrderDateDrafts.remove(orderId);
            selectedOrderDateDraftBaselines.remove(orderId);
            selectedOrderDateConflicts.remove(orderId);
            draft = null;
        }
        String reviewed = orderId == null || draft == null ? authoritative
                : selectedOrderDateDraftBaselines.getOrDefault(orderId, authoritative);
        updateCorrectionConflict(
                selectedOrderDateConflicts, orderId, reviewed, authoritative);
        String value = orderId == null ? "" : draft == null ? authoritative : draft;
        selectedOrderDateOrderId = orderId;
        selectedOrderDateBaseline = reviewed;
        if (selectedOrderDate.getText().trim().equals(value)) {
            FarmUi.clearFieldError(selectedOrderDate);
            return;
        }
        updatingSelectedOrderDate = true;
        try {
            selectedOrderDate.setText(value);
            FarmUi.clearFieldError(selectedOrderDate);
        } finally {
            updatingSelectedOrderDate = false;
        }
    }

    private void saveSelectedOrderDate() {
        if (!GuiPermissions.requireWrite(host, panel, "reschedule orders")) {
            return;
        }
        Long orderId = selectedOrderId();
        if (orderId == null || !orderId.equals(selectedOrderDateOrderId)) {
            error("Select the order whose pickup or delivery date should change.");
            return;
        }
        String newerSaved = selectedOrderDateConflicts.get(orderId);
        if (newerSaved != null) {
            reviewCorrectionConflict(
                    "date", orderId, selectedOrderDate.getText().trim(), newerSaved, () -> {
                        selectedOrderDateBaseline = newerSaved;
                        selectedOrderDateDraftBaselines.put(orderId, newerSaved);
                        selectedOrderDateConflicts.remove(orderId);
                    });
            return;
        }
        try {
            LocalDate date = presenter.validatedOrderDate(selectedOrderDate.getText());
            FarmUi.clearFieldError(selectedOrderDate);
            startOrderDateSave(orderId, selectedOrderDescription(), date.toString());
        } catch (CrmPresenter.InputValidationException exception) {
            FarmUi.markFieldError(selectedOrderDate, exception.getMessage());
            if (panel.isShowing()) {
                error(exception.getMessage());
            }
        }
    }

    void startOrderDateSave(long orderId, String description, String orderDate) {
        String reviewedDate = orderId == selectedOrderDateOrderId
                ? selectedOrderDateBaseline
                : lastOrderView.stream()
                        .filter(order -> order.getId() != null && order.getId() == orderId)
                        .map(order -> order.getOrderDate().toString())
                        .findFirst().orElse("");
        startOrderMutation(new OrderMutationRequest(
                OrderMutation.SAVE_DATE, orderId, description, orderDate, null, reviewedDate));
    }

    private void selectedOrderHandoffChanged() {
        if (!updatingSelectedOrderHandoff) {
            rememberSelectedOrderHandoffDraft();
            updateOrderActions();
        }
    }

    private void rememberSelectedOrderHandoffDraft() {
        if (selectedOrderHandoffOrderId == null) {
            return;
        }
        String value = selectedOrderHandoffType.getSelectedItem() == null
                ? "Unspecified" : String.valueOf(selectedOrderHandoffType.getSelectedItem());
        if (value.equals(selectedOrderHandoffBaseline)) {
            selectedOrderHandoffDrafts.remove(selectedOrderHandoffOrderId);
            selectedOrderHandoffDraftBaselines.remove(selectedOrderHandoffOrderId);
            selectedOrderHandoffConflicts.remove(selectedOrderHandoffOrderId);
        } else {
            selectedOrderHandoffDrafts.put(selectedOrderHandoffOrderId, value);
            selectedOrderHandoffDraftBaselines.putIfAbsent(
                    selectedOrderHandoffOrderId, selectedOrderHandoffBaseline);
        }
    }

    private void replaceSelectedOrderHandoff(Long orderId) {
        if (selectedOrderHandoffType == null) {
            return;
        }
        String authoritative = orderId == null ? "Unspecified" : lastOrderView.stream()
                .filter(order -> orderId.equals(order.getId()))
                .findFirst()
                .map(CustomerOrder::getHandoffType)
                .map(CrmTab::friendlyHandoffType)
                .orElse("Unspecified");
        if (orderId != null && orderId.equals(pendingOrderHandoffRefreshId)) {
            if (!authoritative.equals(pendingOrderHandoffRefreshValue)) {
                return;
            }
            pendingOrderHandoffRefreshId = null;
            pendingOrderHandoffRefreshValue = null;
        }
        boolean sameOrder = orderId != null && orderId.equals(selectedOrderHandoffOrderId);
        String current = selectedOrderHandoffType.getSelectedItem() == null
                ? "Unspecified" : String.valueOf(selectedOrderHandoffType.getSelectedItem());
        boolean dirty = sameOrder && !current.equals(selectedOrderHandoffBaseline);
        if (dirty) {
            if (current.equals(authoritative)) {
                selectedOrderHandoffDrafts.remove(orderId);
                selectedOrderHandoffDraftBaselines.remove(orderId);
                selectedOrderHandoffConflicts.remove(orderId);
                selectedOrderHandoffBaseline = authoritative;
                return;
            }
            String reviewed = selectedOrderHandoffDraftBaselines.getOrDefault(
                    orderId, selectedOrderHandoffBaseline);
            selectedOrderHandoffBaseline = reviewed;
            updateCorrectionConflict(
                    selectedOrderHandoffConflicts, orderId, reviewed, authoritative);
            return;
        }
        String draft = orderId == null ? null : selectedOrderHandoffDrafts.get(orderId);
        if (orderId != null && authoritative.equals(draft)) {
            selectedOrderHandoffDrafts.remove(orderId);
            selectedOrderHandoffDraftBaselines.remove(orderId);
            selectedOrderHandoffConflicts.remove(orderId);
            draft = null;
        }
        String reviewed = orderId == null || draft == null ? authoritative
                : selectedOrderHandoffDraftBaselines.getOrDefault(orderId, authoritative);
        updateCorrectionConflict(
                selectedOrderHandoffConflicts, orderId, reviewed, authoritative);
        String value = orderId == null ? "Unspecified"
                : draft == null ? authoritative : draft;
        selectedOrderHandoffOrderId = orderId;
        selectedOrderHandoffBaseline = reviewed;
        if (current.equals(value)) {
            return;
        }
        updatingSelectedOrderHandoff = true;
        try {
            selectedOrderHandoffType.setSelectedItem(value);
        } finally {
            updatingSelectedOrderHandoff = false;
        }
    }

    private void saveSelectedOrderHandoff() {
        if (!GuiPermissions.requireWrite(host, panel, "update order handoff type")) {
            return;
        }
        Long orderId = selectedOrderId();
        if (orderId == null || !orderId.equals(selectedOrderHandoffOrderId)) {
            error("Select the order whose handoff type should change.");
            return;
        }
        String handoff = selectedOrderHandoffType.getSelectedItem() == null
                ? "Unspecified" : String.valueOf(selectedOrderHandoffType.getSelectedItem());
        String newerSaved = selectedOrderHandoffConflicts.get(orderId);
        if (newerSaved != null) {
            reviewCorrectionConflict("handoff type", orderId, handoff, newerSaved, () -> {
                selectedOrderHandoffBaseline = newerSaved;
                selectedOrderHandoffDraftBaselines.put(orderId, newerSaved);
                selectedOrderHandoffConflicts.remove(orderId);
            });
            return;
        }
        startOrderHandoffSave(orderId, selectedOrderDescription(), handoff);
    }

    void startOrderHandoffSave(long orderId, String description, String handoffType) {
        String reviewedHandoff = orderId == selectedOrderHandoffOrderId
                ? selectedOrderHandoffBaseline
                : lastOrderView.stream()
                        .filter(order -> order.getId() != null && order.getId() == orderId)
                        .map(CustomerOrder::getHandoffType)
                        .map(CrmTab::friendlyHandoffType)
                        .findFirst().orElse("Unspecified");
        startOrderMutation(new OrderMutationRequest(
                OrderMutation.SAVE_HANDOFF, orderId, description,
                handoffType, null, reviewedHandoff));
    }

    private void selectedOrderNotesChanged() {
        if (!updatingSelectedOrderNotes) {
            rememberSelectedOrderNotesDraft();
            updateOrderActions();
        }
    }

    private void rememberSelectedOrderNotesDraft() {
        if (selectedOrderNotesOrderId == null) {
            return;
        }
        String value = selectedOrderNotes.getText().trim();
        if (value.equals(selectedOrderNotesBaseline)) {
            selectedOrderNotesDrafts.remove(selectedOrderNotesOrderId);
            selectedOrderNotesDraftBaselines.remove(selectedOrderNotesOrderId);
            selectedOrderNotesConflicts.remove(selectedOrderNotesOrderId);
        } else {
            selectedOrderNotesDrafts.put(selectedOrderNotesOrderId, value);
            selectedOrderNotesDraftBaselines.putIfAbsent(
                    selectedOrderNotesOrderId, selectedOrderNotesBaseline);
        }
    }

    private void replaceSelectedOrderNotes(Long orderId) {
        if (selectedOrderNotes == null) {
            return;
        }
        String authoritative = orderId == null ? "" : lastOrderView.stream()
                .filter(order -> orderId.equals(order.getId()))
                .findFirst()
                .map(CustomerOrder::getNotes)
                .orElse("");
        if (orderId != null && orderId.equals(pendingOrderNotesRefreshId)) {
            if (!authoritative.equals(pendingOrderNotesRefreshValue)) {
                return;
            }
            pendingOrderNotesRefreshId = null;
            pendingOrderNotesRefreshValue = null;
        }
        boolean sameOrder = orderId != null && orderId.equals(selectedOrderNotesOrderId);
        boolean dirty = sameOrder
                && !selectedOrderNotes.getText().trim().equals(selectedOrderNotesBaseline);
        if (dirty) {
            String draft = selectedOrderNotes.getText().trim();
            if (draft.equals(authoritative)) {
                selectedOrderNotesDrafts.remove(orderId);
                selectedOrderNotesDraftBaselines.remove(orderId);
                selectedOrderNotesConflicts.remove(orderId);
                selectedOrderNotesBaseline = authoritative;
                return;
            }
            String reviewed = selectedOrderNotesDraftBaselines.getOrDefault(
                    orderId, selectedOrderNotesBaseline);
            selectedOrderNotesBaseline = reviewed;
            updateCorrectionConflict(
                    selectedOrderNotesConflicts, orderId, reviewed, authoritative);
            return;
        }
        String draft = orderId == null ? null : selectedOrderNotesDrafts.get(orderId);
        if (orderId != null && authoritative.equals(draft)) {
            selectedOrderNotesDrafts.remove(orderId);
            selectedOrderNotesDraftBaselines.remove(orderId);
            selectedOrderNotesConflicts.remove(orderId);
            draft = null;
        }
        String reviewed = orderId == null || draft == null ? authoritative
                : selectedOrderNotesDraftBaselines.getOrDefault(orderId, authoritative);
        updateCorrectionConflict(
                selectedOrderNotesConflicts, orderId, reviewed, authoritative);
        String value = orderId == null ? "" : draft == null ? authoritative : draft;
        selectedOrderNotesOrderId = orderId;
        selectedOrderNotesBaseline = reviewed;
        if (selectedOrderNotes.getText().trim().equals(value)) {
            FarmUi.clearFieldError(selectedOrderNotes);
            return;
        }
        updatingSelectedOrderNotes = true;
        try {
            selectedOrderNotes.setText(value);
            FarmUi.clearFieldError(selectedOrderNotes);
        } finally {
            updatingSelectedOrderNotes = false;
        }
    }

    private void replaceSelectedOrderLines(Long orderId) {
        if (selectedOrderLine == null) {
            return;
        }
        OrderLineChoice previous = (OrderLineChoice) selectedOrderLine.getSelectedItem();
        List<OrderLineChoice> choices = orderId == null ? List.of() : lastOrderView.stream()
                .filter(order -> orderId.equals(order.getId()))
                .findFirst()
                .map(order -> order.getLines().stream()
                        .map(line -> new OrderLineChoice(line.getId(), line.getProductName(),
                                line.getQuantity(), line.getUnit(), line.getUnitPrice()))
                        .toList())
                .orElse(List.of());
        updatingOrderLineChoices = true;
        try {
            selectedOrderLine.setModel(new DefaultComboBoxModel<>(
                    choices.toArray(OrderLineChoice[]::new)));
            if (previous != null && previous.id() != null) {
                choices.stream().filter(choice -> previous.id().equals(choice.id()))
                        .findFirst().ifPresent(selectedOrderLine::setSelectedItem);
            }
            boolean activeLineHandoff = handoffOrderLineOrderId != null
                    && handoffOrderLineOrderId.equals(orderId);
            Long targetLineId = pendingOrderLineSelectionId != null
                    ? pendingOrderLineSelectionId
                    : activeLineHandoff ? handoffOrderLineId : null;
            String targetProduct = pendingOrderLineProduct != null
                    ? pendingOrderLineProduct
                    : activeLineHandoff ? handoffOrderLineProduct : null;
            if (targetLineId != null) {
                OrderLineChoice pendingChoice = choices.stream()
                        .filter(choice -> targetLineId.equals(choice.id()))
                        .findFirst().orElse(null);
                if (pendingChoice != null) {
                    selectedOrderLine.setSelectedItem(pendingChoice);
                    pendingOrderLineSelectionId = null;
                    pendingOrderLineProduct = null;
                }
            } else if (targetProduct != null && !targetProduct.isBlank()) {
                OrderLineChoice pendingChoice = choices.stream()
                        .filter(choice -> sameProduct(targetProduct, choice.product()))
                        .findFirst().orElse(null);
                if (pendingChoice != null) {
                    selectedOrderLine.setSelectedItem(pendingChoice);
                    pendingOrderLineProduct = null;
                }
            }
        } finally {
            updatingOrderLineChoices = false;
        }
    }

    private static boolean sameProduct(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private void editSelectedOrderLine() {
        if (!GuiPermissions.requireWrite(host, panel, "correct products on orders")) {
            return;
        }
        Long orderId = selectedOrderId();
        OrderLineChoice line = selectedOrderLine == null
                ? null : (OrderLineChoice) selectedOrderLine.getSelectedItem();
        if (orderId == null || line == null || line.id() == null) {
            error("Select an order and one saved product to edit.");
            return;
        }
        editingOrderId = orderId;
        editingOrderLineId = line.id();
        editingOrderLineReview = line.reviewLine();
        editingOrderReview = reviewedOrderSnapshot(orderId);
        if (orderInventoryProduct != null) {
            orderInventoryProduct.setSelectedItem(null);
        }
        orderProduct.setText(line.product());
        orderQty.setText(editableNumber(line.quantity()));
        orderUnit.setText(line.unit());
        orderPrice.setText(String.format(Locale.ROOT, "%.2f", line.unitPrice()));
        updateOrderActions();
        String message = "Editing “" + line.product() + "” · save changes or cancel";
        FarmUi.updateStatusBadge(selectedOrderLabel, message, FarmUi.Tone.WARNING);
        String description = "The product entry fields contain saved line " + line.id()
                + " from order #" + orderId
                + ". Save product changes to update only this line, or cancel the edit."
                + " Inventory will not change.";
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(description);
        selectedOrderLabel.setToolTipText(description);
        orderQty.requestFocusInWindow();
        status("Editing “" + line.product() + "” on order #" + orderId
                + ". Correct the fields, then save product changes.");
    }

    private void clearProductEntry() {
        if (editingOrderLineId == null && !hasPendingProductEntry()) {
            return;
        }
        if (!productEntryClearApprovalMatches()) {
            productEntryClearApproval = currentProductEntryClearApproval();
            updateOrderActions();
            String target = activeProductEntryOrderId == null
                    ? "the new-order draft" : "order #" + activeProductEntryOrderId;
            String guidance = "The product entry for " + target
                    + " is still unsaved. Review its product, quantity, unit, and price. Choose "
                    + "Discard product entry again only to remove all of those values; no saved "
                    + "order or inventory value will change.";
            FarmUi.updateStatusBadge(orderLinePreviewLabel,
                    "Product entry kept · confirm discard", FarmUi.Tone.WARNING);
            orderLinePreviewLabel.setToolTipText(guidance);
            orderLinePreviewLabel.getAccessibleContext().setAccessibleDescription(guidance);
            cancelOrderLineEditBtn.setToolTipText(guidance);
            cancelOrderLineEditBtn.getAccessibleContext().setAccessibleDescription(guidance);
            FarmUi.revealAndFocus(cancelOrderLineEditBtn);
            status("Product entry not cleared. " + guidance);
            return;
        }
        productEntryClearApproval = null;
        Long orderId = editingOrderId;
        Long unavailableOrderId = unavailableProductDraftOrderId;
        boolean editing = editingOrderLineId != null;
        boolean restoredPausedDraft = false;
        clearOrderLineEditState(true);
        clearStoredProductDraft(activeProductEntryOrderId);
        if (unavailableOrderId != null) {
            unavailableProductDraftOrderId = null;
            activeProductEntryOrderId = null;
            applyProductEntryDraft(newOrderProductDraft);
        } else if (activeProductEntryOrderId == null && restorePausedNewOrderProductDraft()) {
            restoredPausedDraft = true;
            applyProductEntryDraft(newOrderProductDraft);
        }
        updateOrderActions();
        status(unavailableOrderId != null
                ? "Discarded the preserved product from missing order #" + unavailableOrderId
                        + ". No saved order or inventory values changed."
                : editing
                ? "Product edit cleared for order #" + orderId
                        + ". No saved order or inventory values changed."
                : restoredPausedDraft
                ? "Product entry cleared. The earlier new-order product draft was restored; "
                        + "no saved order or inventory values changed."
                : "Product entry cleared. No saved order or inventory values changed.");
    }

    private ProductEntryClearApproval currentProductEntryClearApproval() {
        return new ProductEntryClearApproval(
                activeProductEntryOrderId, editingOrderId, editingOrderLineId,
                unavailableProductDraftOrderId,
                normalizedLineDraft(currentOrderLineDraft()));
    }

    private boolean productEntryClearApprovalMatches() {
        return productEntryClearApproval != null
                && productEntryClearApproval.equals(currentProductEntryClearApproval());
    }

    private boolean restorePausedNewOrderProductDraft() {
        if (pausedNewOrderProductDraft == null) {
            return false;
        }
        newOrderProductDraft = pausedNewOrderProductDraft;
        pausedNewOrderProductDraft = null;
        return true;
    }

    private boolean hasPendingProductEntry() {
        if (orderProduct == null || orderQty == null || orderUnit == null
                || orderPrice == null) {
            return false;
        }
        boolean inventorySelected = orderInventoryProduct != null
                && orderInventoryProduct.getSelectedItem() != null;
        return editingOrderLineId != null || inventorySelected
                || !orderProduct.getText().trim().isEmpty()
                || !orderPrice.getText().trim().isEmpty()
                || !"1".equals(orderQty.getText().trim())
                || !defaultUnit.equalsIgnoreCase(orderUnit.getText().trim());
    }

    /** Returns an actionable close warning only when meaningful CRM work could be lost. */
    public String applicationExitWarning() {
        if (orderProduct == null) {
            return null;
        }
        rememberCurrentProductDraft();
        int productDrafts = orderProductDrafts.size()
                + (newOrderProductDraft != null
                        && newOrderProductDraft.hasWork(defaultUnit) ? 1 : 0)
                + (pausedNewOrderProductDraft != null
                        && pausedNewOrderProductDraft.hasWork(defaultUnit) ? 1 : 0);
        Set<Long> correctedOrders = new LinkedHashSet<>();
        correctedOrders.addAll(selectedOrderDateDrafts.keySet());
        correctedOrders.addAll(selectedOrderHandoffDrafts.keySet());
        correctedOrders.addAll(selectedOrderNotesDrafts.keySet());
        boolean newOrderDateChanged = orderDate != null
                && !orderDate.getText().trim().equals(newOrderDateBaseline);
        boolean newOrderCustomerChanged = !Objects.equals(
                currentNewOrderCustomerId(), newOrderCustomerBaselineId);
        String newOrderHandoff = currentNewOrderHandoff();
        boolean newOrderHandoffChanged = !newOrderHandoff.equals(newOrderHandoffBaseline);
        boolean newOrderInstructions = orderNotes != null && !orderNotes.getText().isBlank();
        boolean writeRunning = hasPendingSave();
        List<String> work = new ArrayList<>();
        if (customerDraftChanged()) {
            work.add(editingCustomerId == null ? "an unfinished new-customer draft"
                    : "unsaved customer details for account #" + editingCustomerId);
        }
        if (productDrafts > 0) {
            work.add(productDrafts + " unfinished product entr"
                    + (productDrafts == 1 ? "y" : "ies"));
        }
        if (newOrderCustomerChanged) {
            work.add("unfinished new-order customer");
        }
        if (newOrderDateChanged) {
            work.add("unfinished new-order date");
        }
        if (newOrderHandoffChanged) {
            work.add("unfinished new-order handoff");
        }
        if (newOrderInstructions) {
            work.add("unfinished new-order instructions");
        }
        if (!correctedOrders.isEmpty()) {
            work.add("unsaved corrections on " + correctedOrders.size() + " saved order"
                    + (correctedOrders.size() == 1 ? "" : "s"));
        }
        if (writeRunning) {
            work.add("an order or customer write still running");
        }
        if (work.isEmpty()) {
            return null;
        }
        return "Customers & Orders still has " + String.join(", ", work)
                + ". Closing now can discard unsaved entry fields or interrupt the running "
                + "result. Keep working to save or explicitly clear each draft first.";
    }

    /** Whether closing could hide the outcome of an already submitted record change. */
    public boolean hasPendingSave() {
        return creatingOrder || addingOrderLine || runningOrderMutation
                || fulfillingOrder || savingCustomer || deletingCustomer;
    }

    private static OrderLifecycleReadiness orderLifecycleReadiness(
            boolean pendingProductEntry, boolean editingProduct,
            boolean dateDirty, boolean dateValid,
            boolean handoffDirty, boolean handoffSpecified,
            boolean notesDirty, boolean notesValid,
            boolean hasSavedProducts, boolean deliveryContextMissing) {
        List<String> actions = new ArrayList<>();
        if (!hasSavedProducts) {
            actions.add("add at least one saved product");
        }
        if (pendingProductEntry) {
            actions.add(editingProduct
                    ? "save or clear the product changes"
                    : "add or clear the product entry");
        }
        if (dateDirty) {
            actions.add(dateValid
                    ? "save or restore the order date"
                    : "correct and save the order date");
        }
        if (!handoffSpecified) {
            actions.add("choose Pickup, Delivery, or Market and save the handoff type");
        } else if (handoffDirty) {
            actions.add("save or restore the handoff type");
        }
        if (notesDirty) {
            actions.add(notesValid
                    ? "save or restore the order instructions"
                    : "shorten and save the order instructions");
        }
        if (deliveryContextMissing) {
            actions.add("open the customer account and add a contact, account delivery note, "
                    + "or save explicit order delivery instructions");
        }
        if (actions.isEmpty()) {
            return OrderLifecycleReadiness.readyState();
        }
        return new OrderLifecycleReadiness(false,
                "Before confirming or completing, " + String.join(", ", actions) + ".",
                deliveryContextMissing);
    }

    private static boolean hasDeliveryContext(CustomerOrder order) {
        if (order == null || order.getCustomer() == null) {
            return false;
        }
        Customer customer = order.getCustomer();
        return !customer.getContactName().isBlank()
                || !customer.getPhone().isBlank()
                || !customer.getEmail().isBlank()
                || !customer.getNotes().isBlank()
                || order.getNotes() != null && !order.getNotes().isBlank();
    }

    private static String destructiveDraftGuidance(
            long orderId, String action, boolean pendingProductEntry,
            boolean dateDirty, boolean handoffDirty, boolean notesDirty) {
        List<String> unsaved = new ArrayList<>();
        if (pendingProductEntry) {
            unsaved.add("product entry");
        }
        if (dateDirty) {
            unsaved.add("order date");
        }
        if (handoffDirty) {
            unsaved.add("handoff type");
        }
        if (notesDirty) {
            unsaved.add("order instructions");
        }
        if (unsaved.isEmpty()) {
            return null;
        }
        return "Order #" + orderId + " has unsaved " + String.join(", ", unsaved)
                + ". Save each intended change or restore or clear it before opening " + action
                + ". The destructive review uses saved order data only; no cancellation or "
                + "deletion has started.";
    }

    private static String destructiveLifecycleGuidance(
            long orderId, String action, String status) {
        String lifecycle = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        boolean completed = "COMPLETED".equals(lifecycle) || "FULFILLED".equals(lifecycle);
        if ("Cancel selected order".equals(action) && completed) {
            return "Order #" + orderId + " is already completed. Inventory was deducted, so it "
                    + "cannot be cancelled. Keep the completed order as history and review its "
                    + "invoice or activity record instead.";
        }
        if ("Cancel selected order".equals(action) && "CANCELLED".equals(lifecycle)) {
            return "Order #" + orderId + " is already cancelled and is no longer in active "
                    + "packing or Market Day work. No second cancellation is available.";
        }
        if ("Delete selected order".equals(action) && completed) {
            return "Order #" + orderId + " is already completed. Inventory was deducted, so the "
                    + "order cannot be deleted. Keep it as the permanent sales and inventory "
                    + "history record.";
        }
        return null;
    }

    private String selectedOrderDestructiveLifecycleGuidance(long orderId, String action) {
        int row = orderTable.getSelectedRow();
        String status = row >= 0 && Objects.equals(orderModel.getValueAt(row, 0), orderId)
                ? String.valueOf(orderModel.getValueAt(row, 3)) : "";
        return destructiveLifecycleGuidance(orderId, action, status);
    }

    private String selectedOrderDestructiveDraftGuidance(long orderId, String action) {
        boolean selected = Objects.equals(selectedOrderId(), orderId);
        boolean dateDirty = selected && Objects.equals(selectedOrderDateOrderId, orderId)
                && !selectedOrderDate.getText().trim().equals(selectedOrderDateBaseline);
        boolean handoffDirty = selected && Objects.equals(selectedOrderHandoffOrderId, orderId)
                && !String.valueOf(selectedOrderHandoffType.getSelectedItem())
                        .equals(selectedOrderHandoffBaseline);
        boolean notesDirty = selected && Objects.equals(selectedOrderNotesOrderId, orderId)
                && !selectedOrderNotes.getText().trim().equals(selectedOrderNotesBaseline);
        return destructiveDraftGuidance(orderId, action,
                selected && hasPendingProductEntry(), dateDirty, handoffDirty, notesDirty);
    }

    private void reportDestructiveActionBlock(String message, String guidance) {
        FarmUi.updateStatusBadge(fulfillmentStatusLabel,
                message, FarmUi.Tone.WARNING);
        fulfillmentStatusLabel.setToolTipText(guidance);
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(guidance);
        runOrderToolBtn.setToolTipText(guidance);
        runOrderToolBtn.getAccessibleContext().setAccessibleDescription(guidance);
        status(guidance);
    }

    private void applyLifecycleReadinessGuidance(
            int row, Long orderId, String status, OrderLifecycleReadiness readiness) {
        if (row < 0 || readiness.ready()) {
            return;
        }
        String issue = readiness.deliveryContextMissing()
                ? " is missing delivery contact or destination context. " : " has unsaved work. ";
        String guidance = "Order #" + orderId + issue + readiness.guidance()
                + " The draft stays bound to this order if you inspect another order, and returns "
                + "when you reselect it.";
        if ("DRAFT".equalsIgnoreCase(status)) {
            confirmOrderBtn.setToolTipText(guidance);
            confirmOrderBtn.getAccessibleContext().setAccessibleDescription(guidance);
        }
        if ("CONFIRMED".equalsIgnoreCase(status)) {
            fulfillOrderBtn.setToolTipText(guidance);
            fulfillOrderBtn.getAccessibleContext().setAccessibleDescription(guidance);
        }
        selectedOrderLabel.setToolTipText(guidance);
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(guidance);
    }

    private void clearOrderLineEditState(boolean resetFields) {
        editingOrderId = null;
        editingOrderLineId = null;
        editingOrderLineReview = null;
        editingOrderReview = null;
        if (resetFields && orderProduct != null) {
            resetOrderProductFields();
            clearOrderValidation();
        }
    }

    private static String editableNumber(double value) {
        return value == Math.rint(value)
                ? Long.toString((long) value)
                : String.format(Locale.ROOT, "%.4f", value)
                        .replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void removeSelectedOrderLine() {
        if (!GuiPermissions.requireWrite(host, panel, "remove products from orders")) {
            return;
        }
        Long orderId = selectedOrderId();
        OrderLineChoice line = selectedOrderLine == null
                ? null : (OrderLineChoice) selectedOrderLine.getSelectedItem();
        if (orderId == null || line == null || line.id() == null) {
            error("Select an order and one saved product to remove.");
            return;
        }
        OrderService.OrderReviewSnapshot reviewedOrder = reviewedOrderSnapshot(orderId);
        if (reviewedOrder == null) {
            status("Order #" + orderId
                    + " is unavailable for product-removal review. Refresh CRM and try again.");
            return;
        }
        String removalContext = editableNumber(line.quantity()) + " " + line.unit() + " “"
                + line.product() + "” at $" + String.format(Locale.ROOT, "%.2f", line.unitPrice())
                + " each. Customer: " + reviewedOrder.customerName() + ". Date: "
                + reviewedOrder.orderDate() + ". Handoff: "
                + friendlyHandoffType(reviewedOrder.handoffType()) + ". Status: "
                + reviewedOrder.status() + ". Instructions: "
                + (reviewedOrder.notes() == null || reviewedOrder.notes().isBlank()
                ? "none saved" : compactContext(reviewedOrder.notes(), 180)) + ". ";
        int confirmation = panel.isShowing()
                ? JOptionPane.showConfirmDialog(panel,
                        FarmUi.confirmationPanel(
                                "Remove “" + line.product() + "” from order #" + orderId + "?",
                                removalContext
                                        + "The order total and packing list will update. Inventory is unchanged "
                                        + "because the order has not been completed.",
                                FarmUi.Tone.WARNING),
                        "Remove order product", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE)
                : JOptionPane.YES_OPTION;
        if (confirmation != JOptionPane.YES_OPTION) {
            return;
        }
        startOrderLineRemoval(orderId, line.id(), line.product(), selectedOrderDescription(),
                line.reviewLine(), reviewedOrder);
    }

    void startOrderLineRemoval(long orderId, long lineId, String product,
                               String orderDescription,
                               OrderService.OrderReviewLine reviewedLine) {
        startOrderLineRemoval(orderId, lineId, product, orderDescription, reviewedLine,
                reviewedOrderSnapshot(orderId));
    }

    private void startOrderLineRemoval(long orderId, long lineId, String product,
                                       String orderDescription,
                                       OrderService.OrderReviewLine reviewedLine,
                                       OrderService.OrderReviewSnapshot reviewedOrder) {
        String description = "“" + product + "” from " + orderDescription;
        startOrderMutation(new OrderMutationRequest(
                OrderMutation.REMOVE_LINE, orderId, description, null, lineId,
                null, reviewedLine, reviewedOrder));
    }

    private String selectedOrderDescription() {
        int row = orderTable.getSelectedRow();
        if (row < 0) {
            return "selected order";
        }
        return "the " + orderModel.getValueAt(row, 2) + " order for "
                + orderModel.getValueAt(row, 1);
    }

    private void confirmOrder() {
        if (!GuiPermissions.requireWrite(host, panel, "confirm orders")) {
            return;
        }
        Long id = selectedOrderId();
        if (id == null) {
            error("Select an order to confirm.");
            return;
        }
        CustomerOrder order = lastOrderView.stream()
                .filter(candidate -> id.equals(candidate.getId()))
                .findFirst()
                .orElse(null);
        if (order == null) {
            status("Order #" + id + " is no longer available. Refresh CRM before confirming.");
            return;
        }
        OrderConfirmationReview review = orderConfirmationReview(
                order, orderStockAssessment(id));
        OrderService.OrderReviewSnapshot reviewedSnapshot = presenter.reviewSnapshot(order);
        int confirmation = !panel.isShowing() && dialogs == FarmDialogs.swing()
                ? JOptionPane.YES_OPTION
                : dialogs.showConfirm(panel, orderReviewDialog(orderConfirmationPanel(review)),
                        "Confirm order for packing", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
        if (confirmation != JOptionPane.YES_OPTION) {
            status("Confirmation cancelled for order #" + id
                    + ". It remains a draft and inventory was not changed.");
            return;
        }
        String description = selectedOrderDescription();
        startOrderConfirmation(id, description, reviewedSnapshot);
    }

    static OrderConfirmationReview orderConfirmationReview(
            CustomerOrder order, OrderStockAssessment stockAssessment) {
        if (order == null || order.getCustomer() == null) {
            return new OrderConfirmationReview(
                    "Confirm selected order?",
                    "The order details are unavailable. Refresh CRM and review the order before confirming.");
        }
        int products = order.getLines().size();
        String contact = confirmationContact(order.getCustomer());
        String productLines;
        if (order.getLines().isEmpty()) {
            productLines = "No saved products.";
        } else {
            List<String> lines = new ArrayList<>();
            for (int index = 0; index < order.getLines().size(); index++) {
                OrderLine line = order.getLines().get(index);
                lines.add((index + 1) + ". " + editableNumber(line.getQuantity()) + " "
                        + line.getUnit() + " " + line.getProductName() + " · $"
                        + String.format(Locale.ROOT, "%,.2f", line.getUnitPrice())
                        + " each · $"
                        + String.format(Locale.ROOT, "%,.2f", line.lineTotal()));
            }
            productLines = String.join("\n", lines);
        }
        String instructions = order.getNotes() == null || order.getNotes().isBlank()
                ? "No pickup or packing instructions saved."
                : "Instructions: " + compactContext(order.getNotes(), 180);
        String dateWarning = order.getOrderDate().isBefore(LocalDate.now())
                ? "Date warning: this handoff date is in the past. Verify it before confirmation.\n"
                : "";
        String stock;
        if (stockAssessment == null || !stockAssessment.available()) {
            stock = "Stock check is unavailable; refresh CRM and verify stock before completion.";
        } else if (stockAssessment.gaps().isEmpty()) {
            stock = "Stock check: all " + stockAssessment.productCount()
                    + " product" + (stockAssessment.productCount() == 1 ? "" : "s")
                    + " currently covered.";
        } else {
            int issues = stockAssessment.gaps().size();
            stock = "Stock check: " + issues + " packing issue" + (issues == 1 ? "" : "s")
                    + ". Confirmation is allowed, but completion stays locked until resolved.";
        }
        String heading = "Confirm order #" + order.getId() + " · "
                + order.getCustomer().getName() + " · " + order.getOrderDate() + "?";
        String details = contact + "\n\nProducts: " + products + "\n" + productLines
                + "\n\nOrder total: $"
                + String.format(Locale.ROOT, "%,.2f", order.lineTotal()) + "\n"
                + "Handoff type: " + friendlyHandoffType(order.getHandoffType()) + "\n"
                + dateWarning + instructions + "\n" + stock + "\n"
                + "Confirmation makes this order eligible for packing and Market Day date "
                + "windows. Inventory is not deducted until Complete order.";
        return new OrderConfirmationReview(heading, details);
    }

    private static String confirmationContact(Customer customer) {
        List<String> values = new ArrayList<>();
        if (customer != null) {
            if (!customer.getContactName().isBlank()) {
                values.add(customer.getContactName());
            }
            if (!customer.getPhone().isBlank()) {
                values.add(customer.getPhone());
            }
            if (!customer.getEmail().isBlank()) {
                values.add(customer.getEmail());
            }
        }
        String contact = values.isEmpty()
                ? "Customer contact: none saved — verify delivery details before confirmation."
                : "Customer contact: " + String.join(" · ", values);
        if (customer != null && customer.getNotes() != null
                && !customer.getNotes().isBlank()) {
            contact += "\nAccount delivery note: " + compactContext(customer.getNotes(), 240);
        }
        return contact;
    }

    static JComponent orderConfirmationPanel(OrderConfirmationReview review) {
        JPanel content = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        content.setName("crm.orderConfirmationReview");
        content.setBackground(FarmUi.surfaceColor());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel heading = FarmUi.wrappingStatusBadge(review.heading(), FarmUi.Tone.WARNING);
        heading.setFont(heading.getFont().deriveFont(13f));
        heading.setName("crm.orderConfirmationHeading");
        heading.setHorizontalAlignment(SwingConstants.LEFT);
        content.add(heading, BorderLayout.NORTH);

        JTextArea details = FarmUi.displayArea(10, 58);
        details.setName("crm.orderConfirmationDetails");
        details.setText(review.details());
        details.setCaretPosition(0);
        details.setBackground(FarmUi.surfaceColor());
        details.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        details.getAccessibleContext().setAccessibleName(
                "Saved order products and confirmation consequences");
        details.getAccessibleContext().setAccessibleDescription(review.details());
        JScrollPane scroll = new JScrollPane(details);
        scroll.setName("crm.orderConfirmationScroll");
        scroll.setPreferredSize(new Dimension(620, 250));
        scroll.setMinimumSize(new Dimension(340, 170));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getAccessibleContext().setAccessibleName(
                "Scrollable saved-order confirmation review");
        content.add(scroll, BorderLayout.CENTER);
        content.getAccessibleContext().setAccessibleName(
                review.heading() + " saved-order review");
        return content;
    }

    static OrderCompletionReview orderCompletionReview(
            CustomerOrder order, OrderStockAssessment stockAssessment) {
        if (order == null || order.getCustomer() == null) {
            return new OrderCompletionReview(
                    "Complete selected order?",
                    "The saved order details are unavailable. Refresh CRM and review the exact "
                            + "customer load before completing it.");
        }
        List<String> productLines = new ArrayList<>();
        for (int index = 0; index < order.getLines().size(); index++) {
            OrderLine line = order.getLines().get(index);
            double inventoryUnits = line.getQuantity();
            productLines.add((index + 1) + ". " + editableNumber(line.getQuantity()) + " "
                    + line.getUnit() + " " + line.getProductName() + " · $"
                    + String.format(Locale.ROOT, "%,.2f", line.getUnitPrice())
                    + " each · $"
                    + String.format(Locale.ROOT, "%,.2f", line.lineTotal())
                    + " · deduct " + InventoryQuantity.format(inventoryUnits) + " " + line.getUnit() + " inventory unit"
                    + (inventoryUnits == 1 ? "" : "s"));
        }
        String stock;
        if (stockAssessment == null || !stockAssessment.available()) {
            stock = "Stock snapshot is unavailable. The completion service will still recheck "
                    + "live inventory and refuse any uncovered product.";
        } else if (stockAssessment.gaps().isEmpty()) {
            stock = "Stock snapshot: all " + stockAssessment.productCount() + " product"
                    + (stockAssessment.productCount() == 1 ? "" : "s")
                    + " covered. Live stock is checked again before inventory changes.";
        } else {
            stock = "Stock snapshot: " + stockAssessment.gaps().size() + " blocking issue"
                    + (stockAssessment.gaps().size() == 1 ? "" : "s")
                    + ". Completion must remain locked until resolved and refreshed.";
        }
        String instructions = order.getNotes() == null || order.getNotes().isBlank()
                ? "Instructions: none saved"
                : "Instructions: " + compactContext(order.getNotes(), 240);
        String dateWarning = order.getOrderDate().isAfter(LocalDate.now())
                ? "EARLY COMPLETION WARNING: this handoff is scheduled for "
                        + order.getOrderDate() + ". Continue only if the physical handoff "
                        + "already happened; otherwise cancel and correct or wait for the date.\n"
                : order.getOrderDate().isBefore(LocalDate.now())
                        ? "OVERDUE COMPLETION WARNING: this handoff was scheduled for "
                                + order.getOrderDate() + ". Continue only if this exact physical "
                                + "handoff happened late; otherwise cancel and correct the date or "
                                + "cancel the abandoned order.\n"
                        : "";
        String heading = "Complete order #" + order.getId() + " · "
                + order.getCustomer().getName() + " · $"
                + String.format(Locale.ROOT, "%,.2f", order.lineTotal()) + "?";
        String details = "Handoff type: " + friendlyHandoffType(order.getHandoffType()) + "\n"
                + "Pickup / delivery date: " + order.getOrderDate() + "\n"
                + dateWarning
                + confirmationContact(order.getCustomer()) + "\n"
                + instructions + "\n\nProducts: " + order.getLines().size() + "\n"
                + (productLines.isEmpty() ? "No saved products." : String.join("\n", productLines))
                + "\n\nOrder total: $"
                + String.format(Locale.ROOT, "%,.2f", order.lineTotal()) + "\n"
                + stock + "\n"
                + "Completing marks this order completed and deducts the listed matching inventory "
                + "now. Continue only after the physical pickup, delivery, or load-out is complete. "
                + "Cancel if any customer, date, product, quantity, unit, price, or instruction is not exact.";
        return new OrderCompletionReview(heading, details);
    }

    static JComponent orderCompletionPanel(OrderCompletionReview review) {
        JPanel content = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        content.setName("crm.orderCompletionReview");
        content.setBackground(FarmUi.surfaceColor());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel heading = FarmUi.wrappingStatusBadge(review.heading(), FarmUi.Tone.WARNING);
        heading.setFont(heading.getFont().deriveFont(13f));
        heading.setName("crm.orderCompletionHeading");
        heading.setHorizontalAlignment(SwingConstants.LEFT);
        content.add(heading, BorderLayout.NORTH);

        JTextArea details = FarmUi.displayArea(12, 62);
        details.setName("crm.orderCompletionDetails");
        details.setText(review.details());
        details.setCaretPosition(0);
        details.setBackground(FarmUi.surfaceColor());
        details.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        details.getAccessibleContext().setAccessibleName(
                "Exact customer load and inventory deduction review");
        details.getAccessibleContext().setAccessibleDescription(review.details());
        JScrollPane scroll = new JScrollPane(details);
        scroll.setName("crm.orderCompletionScroll");
        scroll.setPreferredSize(new Dimension(680, 300));
        scroll.setMinimumSize(new Dimension(340, 180));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getAccessibleContext().setAccessibleName(
                "Scrollable direct order completion review");
        content.add(scroll, BorderLayout.CENTER);
        content.getAccessibleContext().setAccessibleName(
                review.heading() + " exact completion review");
        return content;
    }

    void startOrderConfirmation(long orderId, String description) {
        CustomerOrder order = lastOrderView.stream()
                .filter(candidate -> candidate.getId() != null
                        && candidate.getId() == orderId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order #" + orderId + " is unavailable for confirmation review."));
        startOrderConfirmation(orderId, description, presenter.reviewSnapshot(order));
    }

    void startOrderConfirmation(long orderId, String description,
                                OrderService.OrderReviewSnapshot reviewedSnapshot) {
        startOrderMutation(new OrderMutationRequest(
                OrderMutation.CONFIRM, orderId, description, null, null, reviewedSnapshot));
    }

    private void markFulfilled() {
        if (!GuiPermissions.requireWrite(host, panel, "fulfill orders")) {
            return;
        }
        if (fulfillingOrder) {
            status("Order completion is already running. Wait for its result before retrying.");
            return;
        }
        Long id = selectedOrderId();
        if (id == null) {
            error("Select an order.");
            return;
        }
        String statusNow = String.valueOf(orderModel.getValueAt(orderTable.getSelectedRow(), 3));
        String description = selectedOrderDescription();
        if ("Completed".equalsIgnoreCase(statusNow)) {
            error(description + " is already complete.");
            return;
        }
        CustomerOrder order = lastOrderView.stream()
                .filter(candidate -> id.equals(candidate.getId()))
                .findFirst()
                .orElse(null);
        if (order == null) {
            status("Order #" + id + " is no longer available. Refresh CRM before completing it.");
            return;
        }
        if (order.getOrderDate().isAfter(LocalDate.now())
                && !earlyCompletionApprovalMatches(id)) {
            overdueCompletionApproval = null;
            earlyCompletionApproval = new EarlyCompletionApproval(
                    id, presenter.reviewSnapshot(order));
            updateOrderActions();
            String guidance = "Order #" + id + " is scheduled for " + order.getOrderDate()
                    + ". It was not completed and inventory was not changed. Verify that the "
                    + "physical " + friendlyHandoffType(order.getHandoffType()).toLowerCase(
                            Locale.ROOT)
                    + " actually happened early, or correct the order date. Choose Review early "
                    + "completion only while every saved and unsaved detail remains unchanged.";
            FarmUi.updateStatusBadge(fulfillmentStatusLabel,
                    "Future handoff · completion paused", FarmUi.Tone.WARNING);
            fulfillmentStatusLabel.setToolTipText(guidance);
            fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(guidance);
            fulfillOrderBtn.setToolTipText(guidance);
            fulfillOrderBtn.getAccessibleContext().setAccessibleDescription(guidance);
            FarmUi.revealAndFocus(fulfillOrderBtn);
            status(guidance);
            return;
        }
        if (order.getOrderDate().isBefore(LocalDate.now())
                && !overdueCompletionApprovalMatches(id)) {
            earlyCompletionApproval = null;
            overdueCompletionApproval = new OverdueCompletionApproval(
                    id, presenter.reviewSnapshot(order));
            updateOrderActions();
            String guidance = "Order #" + id + " was scheduled for " + order.getOrderDate()
                    + ". It was not completed and inventory was not changed. Verify that this "
                    + "physical " + friendlyHandoffType(order.getHandoffType()).toLowerCase(
                            Locale.ROOT)
                    + " happened late and still belongs on this order, or correct the order date "
                    + "or cancel the abandoned order. Choose Review overdue completion only while "
                    + "every saved and unsaved detail remains unchanged.";
            FarmUi.updateStatusBadge(fulfillmentStatusLabel,
                    "Overdue handoff · completion paused", FarmUi.Tone.WARNING);
            fulfillmentStatusLabel.setToolTipText(guidance);
            fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(guidance);
            fulfillOrderBtn.setToolTipText(guidance);
            fulfillOrderBtn.getAccessibleContext().setAccessibleDescription(guidance);
            FarmUi.revealAndFocus(fulfillOrderBtn);
            status(guidance);
            return;
        }
        earlyCompletionApproval = null;
        overdueCompletionApproval = null;
        OrderCompletionReview review = orderCompletionReview(order, orderStockAssessment(id));
        OrderService.OrderReviewSnapshot reviewedSnapshot = presenter.reviewSnapshot(order);
        int ok = !panel.isShowing() && dialogs == FarmDialogs.swing()
                ? JOptionPane.YES_OPTION
                : dialogs.showConfirm(panel, orderReviewDialog(orderCompletionPanel(review)),
                        "Complete order", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        startOrderFulfillment(id, description, reviewedSnapshot);
    }

    private boolean earlyCompletionApprovalMatches(Long orderId) {
        if (earlyCompletionApproval == null || orderId == null
                || earlyCompletionApproval.orderId() != orderId
                || hasPendingProductEntry()
                || !Objects.equals(selectedOrderDateOrderId, orderId)
                || !selectedOrderDate.getText().trim().equals(selectedOrderDateBaseline)
                || !Objects.equals(selectedOrderHandoffOrderId, orderId)
                || !String.valueOf(selectedOrderHandoffType.getSelectedItem())
                        .equals(selectedOrderHandoffBaseline)
                || !Objects.equals(selectedOrderNotesOrderId, orderId)
                || !selectedOrderNotes.getText().trim().equals(selectedOrderNotesBaseline)) {
            return false;
        }
        CustomerOrder current = lastOrderView.stream()
                .filter(order -> orderId.equals(order.getId()))
                .findFirst().orElse(null);
        return current != null && earlyCompletionApproval.reviewedOrder().equals(
                presenter.reviewSnapshot(current));
    }

    private boolean overdueCompletionApprovalMatches(Long orderId) {
        if (overdueCompletionApproval == null || orderId == null
                || overdueCompletionApproval.orderId() != orderId
                || hasPendingProductEntry()
                || !Objects.equals(selectedOrderDateOrderId, orderId)
                || !selectedOrderDate.getText().trim().equals(selectedOrderDateBaseline)
                || !Objects.equals(selectedOrderHandoffOrderId, orderId)
                || !String.valueOf(selectedOrderHandoffType.getSelectedItem())
                        .equals(selectedOrderHandoffBaseline)
                || !Objects.equals(selectedOrderNotesOrderId, orderId)
                || !selectedOrderNotes.getText().trim().equals(selectedOrderNotesBaseline)) {
            return false;
        }
        CustomerOrder current = lastOrderView.stream()
                .filter(order -> orderId.equals(order.getId()))
                .findFirst().orElse(null);
        return current != null && overdueCompletionApproval.reviewedOrder().equals(
                presenter.reviewSnapshot(current));
    }

    /** Starts one captured completion without blocking Swing's event thread. */
    void startOrderFulfillment(long orderId, String description) {
        CustomerOrder order = lastOrderView.stream()
                .filter(candidate -> candidate.getId() != null
                        && candidate.getId() == orderId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order #" + orderId + " is unavailable for completion review."));
        startOrderFulfillment(orderId, description, presenter.reviewSnapshot(order));
    }

    void startOrderFulfillment(long orderId, String description,
                               OrderService.OrderReviewSnapshot reviewedSnapshot) {
        if (fulfillingOrder || runningOrderMutation || pendingDestructiveSuccess != null
                || pendingCorrectionSuccess != null) {
            return;
        }
        OrderStockAssessment stockAssessment = orderStockAssessment(orderId);
        if (stockAssessment.available() && !stockAssessment.gaps().isEmpty()) {
            updateSelectedOrderStockContext(orderId, stockAssessment);
            String guidance = "Order #" + orderId + " has " + stockAssessment.gaps().size()
                    + " stock gap" + (stockAssessment.gaps().size() == 1 ? "" : "s")
                    + ". Resolve stock in Inventory, then refresh Customers and Orders.";
            FarmUi.updateStatusBadge(fulfillmentStatusLabel,
                    "Completion locked · resolve order stock", FarmUi.Tone.DANGER);
            fulfillmentStatusLabel.setToolTipText(guidance);
            fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(guidance);
            status(guidance);
            return;
        }
        String target = description == null || description.isBlank()
                ? "order #" + orderId : description.trim();
        fulfillingOrder = true;
        setOrderFulfillmentControls(false);
        fulfillOrderBtn.setText("Completing order…");
        FarmUi.updateStatusBadge(fulfillmentStatusLabel,
                "Completing #" + orderId + " · inventory updating",
                FarmUi.Tone.INFO);
        String busyDescription = "Completing order " + orderId
                + " in the background. Order selection and filters are locked to prevent "
                + "a duplicate completion.";
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(busyDescription);
        fulfillmentStatusLabel.setToolTipText(busyDescription);
        status("Completing " + target + " and updating inventory…");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                presenter.fulfillOrder(orderId, reviewedSnapshot);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    completeOrderFulfillment(orderId, target);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failOrderFulfillment(orderId, target, exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failOrderFulfillment(orderId, target,
                            exception.getCause() == null ? exception : exception.getCause());
                } catch (RuntimeException exception) {
                    failOrderFulfillment(orderId, target, exception);
                }
            }
        }.execute();
    }

    private void completeOrderFulfillment(long orderId, String description) {
        finishOrderFulfillmentControls();
        pendingFulfillmentSuccess = new FulfillmentSuccessPresentation(orderId, description);
        pendingOrderSelectionId = orderId;
        refreshData();
        if (host != null) {
            host.refreshAll();
        }
        String message = "Order #" + orderId + " completed · inventory updated";
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, message, FarmUi.Tone.SUCCESS);
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(
                description + " completed successfully and its matching inventory was deducted.");
        fulfillmentStatusLabel.setToolTipText(
                description + " completed successfully. Matching inventory is updated.");
        status(description + " is complete. Inventory is updated.");
    }

    private void applyFulfillmentSuccessPresentation(
            FulfillmentSuccessPresentation presentation, boolean refreshSucceeded,
            boolean targetRestored) {
        String message;
        String recovery;
        String hostStatus;
        FarmUi.Tone tone;
        if (refreshSucceeded && targetRestored) {
            message = "Order #" + presentation.orderId()
                    + " completed once · inventory deducted";
            recovery = presentation.description() + " completed successfully. Its matching "
                    + "inventory was deducted once, and the refreshed Completed order remains "
                    + "selected. Do not run completion again.";
            hostStatus = presentation.description() + " is complete. Inventory was deducted "
                    + "once, and the refreshed Completed order is selected.";
            tone = FarmUi.Tone.SUCCESS;
        } else if (refreshSucceeded) {
            message = "Order #" + presentation.orderId()
                    + " completed once · outside current view";
            recovery = presentation.description() + " completed successfully and its matching "
                    + "inventory was deducted once. The order is outside the current filtered "
                    + "view, which is expected when a Confirmed-only queue hides Completed work. "
                    + "Choose Show all or the Completed filter to locate it. Do not complete or "
                    + "deduct this order again.";
            hostStatus = presentation.description() + " is complete and inventory was deducted "
                    + "once. It is outside the current view; use Show all or Completed to review "
                    + "it, and do not complete it again.";
            tone = FarmUi.Tone.WARNING;
        } else {
            message = "Order #" + presentation.orderId()
                    + " completed once · exact-order reload failed";
            recovery = presentation.description() + " completed successfully and its matching "
                    + "inventory was deducted once, but CRM could not reload the exact order. "
                    + "Retry Refresh CRM only; do not run completion or deduct inventory again.";
            hostStatus = presentation.description() + " is complete and inventory was deducted "
                    + "once, but CRM reload failed. Retry Refresh CRM; do not complete it again.";
            tone = FarmUi.Tone.WARNING;
        }
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, message, tone);
        fulfillmentStatusLabel.setToolTipText(recovery);
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(recovery);
        fulfillOrderBtn.setToolTipText(recovery);
        fulfillOrderBtn.getAccessibleContext().setAccessibleDescription(recovery);
        applyLifecycleRefreshGuidance(recovery, refreshSucceeded);
        status(hostStatus);
    }

    private static String fulfillmentReloadRecovery(
            FulfillmentSuccessPresentation presentation) {
        return presentation.description() + " completed successfully and its matching inventory "
                + "was deducted once, but CRM has not reloaded the exact order. Use Refresh CRM "
                + "only; do not add a product, run completion, or deduct inventory again.";
    }

    private void applyLifecycleRefreshGuidance(String recovery, boolean refreshSucceeded) {
        String guidance = refreshSucceeded
                ? "Reload customers, filtered orders, and inventory choices."
                : recovery + " Refresh CRM is the only safe retry action.";
        refreshCrmBtn.setToolTipText(guidance);
        refreshCrmBtn.getAccessibleContext().setAccessibleDescription(guidance);
    }

    private void failOrderFulfillment(long orderId, String description, Throwable failure) {
        finishOrderFulfillmentControls();
        String detail = compactMessage(FarmUi.actionableError(
                "Completing order #" + orderId, failure));
        boolean staleReview = detail.toLowerCase(Locale.ROOT)
                .contains("changed after completion review");
        pendingFulfillmentFailure = new FulfillmentFailurePresentation(
                orderId, description, detail, staleReview);
        pendingOrderSelectionId = orderId;
        String refreshing = staleReview
                ? "Order #" + orderId + " changed · refreshing current load"
                : "Order #" + orderId + " not completed · refreshing stock";
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, refreshing, FarmUi.Tone.INFO);
        String refreshGuidance = detail + " The exact order remains selected while CRM reloads "
                + "its current saved details and inventory before another completion review.";
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(refreshGuidance);
        fulfillmentStatusLabel.setToolTipText(refreshGuidance);
        status(description + " was not completed. Refreshing its current order and stock guidance "
                + "before another attempt.");
        refreshData();
        if (host != null) {
            host.refreshAll();
        }
        if (panel.isShowing()) {
            JOptionPane.showMessageDialog(panel, refreshGuidance,
                    "Order completion failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyFulfillmentFailurePresentation(
            FulfillmentFailurePresentation presentation, boolean refreshSucceeded,
            boolean targetRestored) {
        String message;
        String recovery;
        String hostStatus;
        if (refreshSucceeded && targetRestored) {
            message = presentation.staleReview()
                    ? "Order #" + presentation.orderId() + " changed · review refreshed order"
                    : "Order #" + presentation.orderId() + " not completed · current stock refreshed";
            recovery = presentation.detail() + " The exact order remains selected and its current "
                    + "saved details and stock guidance are now refreshed. Review them before "
                    + "opening another completion review.";
            hostStatus = presentation.description() + " was not completed. Its current order and "
                    + "stock guidance are refreshed; review them before retrying.";
        } else if (refreshSucceeded) {
            message = "Order #" + presentation.orderId()
                    + " not completed · no longer in current view";
            recovery = presentation.detail() + " CRM refreshed successfully, but the exact order "
                    + "is no longer in the current filtered view. It may have been completed, "
                    + "cancelled, deleted, or moved outside the filters by another worker. Clear "
                    + "the filters and locate order #" + presentation.orderId()
                    + " before taking any further action; do not repeat completion from old data.";
            hostStatus = presentation.description() + " was not completed from this review. Order #"
                    + presentation.orderId() + " is no longer in the refreshed view; locate its "
                    + "current state before any further action.";
        } else {
            message = "Order #" + presentation.orderId()
                    + " not completed · automatic refresh failed";
            recovery = presentation.detail() + " The exact order remains selected, but CRM could "
                    + "not reload current order and stock data. Use Refresh CRM before retrying.";
            hostStatus = presentation.description() + " was not completed, and its automatic CRM "
                    + "refresh failed. Existing work was kept; use Refresh CRM before retrying.";
        }
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, message, FarmUi.Tone.DANGER);
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(recovery);
        fulfillmentStatusLabel.setToolTipText(recovery);
        fulfillOrderBtn.setToolTipText(recovery);
        fulfillOrderBtn.getAccessibleContext().setAccessibleDescription(recovery);
        status(hostStatus);
    }

    private void finishOrderFulfillmentControls() {
        fulfillingOrder = false;
        fulfillOrderBtn.setText("Complete order");
        setOrderFulfillmentControls(true);
        applyRolePermissions(canWrite);
    }

    private void setOrderFulfillmentControls(boolean enabled) {
        if (orderTable != null) {
            orderTable.setEnabled(enabled);
        }
        for (JComponent component : List.of(
                filterStatus, filterHandoff, filterCustomer, filterFrom, filterTo,
                applyOrderFilterBtn, clearOrderFilterBtn, readyToFulfillBtn,
                needsHandoffReviewBtn,
                orderCustomer, orderDate, orderInventoryProduct, orderProduct, orderQty,
                orderUnit, orderPrice, orderStatus, orderNotes,
                createOrderBtn, addOrderLineBtn, confirmOrderBtn, fulfillOrderBtn,
                startAnotherOrderBtn, selectedOrderLine, editOrderLineBtn,
                cancelOrderLineEditBtn, removeOrderLineBtn,
                selectedOrderDate, saveOrderDateBtn,
                selectedOrderHandoffType, saveOrderHandoffBtn,
                selectedOrderNotes, saveOrderNotesBtn,
                openNewOrderCustomerBtn, reviewOrderCustomerBtn,
                orderTools, runOrderToolBtn)) {
            component.setEnabled(enabled);
        }
        updateOrderActions();
    }

    private static String compactMessage(String message) {
        String clean = message == null ? "The operation did not return a result."
                : message.replaceAll("\\s+", " ").trim();
        return clean.length() <= 220 ? clean : clean.substring(0, 217) + "…";
    }

    private void cancelOrder() {
        if (!GuiPermissions.requireWrite(host, panel, "cancel orders")) {
            return;
        }
        Long id = selectedOrderId();
        if (id == null) {
            error("Select an order to cancel.");
            return;
        }
        String lifecycleGuidance = selectedOrderDestructiveLifecycleGuidance(
                id, "Cancel selected order");
        if (lifecycleGuidance != null) {
            reportDestructiveActionBlock(
                    "Cancellation unavailable · review order status", lifecycleGuidance);
            return;
        }
        String draftGuidance = selectedOrderDestructiveDraftGuidance(
                id, "Cancel selected order");
        if (draftGuidance != null) {
            reportDestructiveActionBlock(
                    "Destructive review locked · resolve unsaved order work", draftGuidance);
            return;
        }
        CustomerOrder order = orderForDestructiveReview(id, "cancellation");
        String description = selectedOrderDescription();
        OrderDestructiveReview review = orderDestructiveReview(order, false);
        OrderService.OrderReviewSnapshot reviewedSnapshot = presenter.reviewSnapshot(order);
        int confirmation = !panel.isShowing() && dialogs == FarmDialogs.swing()
                ? JOptionPane.YES_OPTION
                : dialogs.showConfirm(panel, orderReviewDialog(orderDestructivePanel(review)), "Cancel order",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmation != JOptionPane.YES_OPTION) {
            return;
        }
        startOrderCancellation(id, description, reviewedSnapshot);
    }

    void startOrderCancellation(long orderId, String description,
                                OrderService.OrderReviewSnapshot reviewedSnapshot) {
        startOrderMutation(new OrderMutationRequest(
                OrderMutation.CANCEL, orderId, description, null, null, reviewedSnapshot));
    }

    private void saveOrderNotes() {
        if (!GuiPermissions.requireWrite(host, panel, "update order notes")) {
            return;
        }
        Long id = selectedOrderId();
        if (id == null || !id.equals(selectedOrderNotesOrderId)) {
            error("Select the order whose pickup or packing instructions should change.");
            return;
        }
        String notes = selectedOrderNotes.getText().trim();
        if (notes.length() > 2000) {
            FarmUi.markFieldError(selectedOrderNotes,
                    "Order instructions cannot exceed 2000 characters.");
            if (panel.isShowing()) {
                error("Order instructions cannot exceed 2000 characters.");
            }
            return;
        }
        String newerSaved = selectedOrderNotesConflicts.get(id);
        if (newerSaved != null) {
            reviewCorrectionConflict("instructions", id, notes, newerSaved, () -> {
                selectedOrderNotesBaseline = newerSaved;
                selectedOrderNotesDraftBaselines.put(id, newerSaved);
                selectedOrderNotesConflicts.remove(id);
            });
            return;
        }
        String description = selectedOrderDescription();
        startOrderNotesSave(id, description, notes);
    }

    void startOrderNotesSave(long orderId, String description, String notes) {
        String reviewedNotes = orderId == selectedOrderNotesOrderId
                ? selectedOrderNotesBaseline
                : lastOrderView.stream()
                        .filter(order -> order.getId() != null && order.getId() == orderId)
                        .map(CustomerOrder::getNotes)
                        .findFirst().orElse("");
        startOrderMutation(new OrderMutationRequest(
                OrderMutation.SAVE_NOTES, orderId, description, notes, null, reviewedNotes));
    }

    private void deleteOrder() {
        if (!GuiPermissions.requireWrite(host, panel, "delete orders")) {
            return;
        }
        Long id = selectedOrderId();
        if (id == null) {
            error("Select an order.");
            return;
        }
        String lifecycleGuidance = selectedOrderDestructiveLifecycleGuidance(
                id, "Delete selected order");
        if (lifecycleGuidance != null) {
            reportDestructiveActionBlock(
                    "Deletion unavailable · completed order retained", lifecycleGuidance);
            return;
        }
        String draftGuidance = selectedOrderDestructiveDraftGuidance(
                id, "Delete selected order");
        if (draftGuidance != null) {
            reportDestructiveActionBlock(
                    "Destructive review locked · resolve unsaved order work", draftGuidance);
            return;
        }
        CustomerOrder order = orderForDestructiveReview(id, "deletion");
        String description = selectedOrderDescription();
        OrderDestructiveReview review = orderDestructiveReview(order, true);
        OrderService.OrderReviewSnapshot reviewedSnapshot = presenter.reviewSnapshot(order);
        int ok = !panel.isShowing() && dialogs == FarmDialogs.swing()
                ? JOptionPane.YES_OPTION
                : dialogs.showConfirm(panel, orderReviewDialog(orderDestructivePanel(review)),
                        "Delete order", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        startOrderDeletion(id, description, reviewedSnapshot);
    }

    void startOrderDeletion(long orderId, String description,
                            OrderService.OrderReviewSnapshot reviewedSnapshot) {
        startOrderMutation(new OrderMutationRequest(
                OrderMutation.DELETE, orderId, description, null, null, reviewedSnapshot));
    }

    private CustomerOrder orderForDestructiveReview(long orderId, String action) {
        return lastOrderView.stream()
                .filter(order -> order.getId() != null && order.getId() == orderId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order #" + orderId + " is unavailable for " + action
                                + " review. Refresh Customers and Orders and try again."));
    }

    static OrderDestructiveReview orderDestructiveReview(
            CustomerOrder order, boolean deleting) {
        if (order == null || order.getCustomer() == null) {
            return new OrderDestructiveReview(
                    deleting ? "Delete selected order?" : "Cancel selected order?",
                    "The saved order details are unavailable. Refresh Customers and Orders before "
                            + (deleting ? "deleting" : "cancelling") + " anything.");
        }
        List<String> products = new ArrayList<>();
        for (int index = 0; index < order.getLines().size(); index++) {
            OrderLine line = order.getLines().get(index);
            products.add((index + 1) + ". " + editableNumber(line.getQuantity()) + " "
                    + line.getUnit() + " " + line.getProductName() + " · $"
                    + String.format(Locale.ROOT, "%,.2f", line.getUnitPrice()) + " each · $"
                    + String.format(Locale.ROOT, "%,.2f", line.lineTotal()));
        }
        String instructions = order.getNotes() == null || order.getNotes().isBlank()
                ? "Instructions: none saved"
                : "Instructions: " + compactContext(order.getNotes(), 240);
        String heading = (deleting ? "Permanently delete" : "Cancel") + " order #"
                + order.getId() + " · " + order.getCustomer().getName() + " · $"
                + String.format(Locale.ROOT, "%,.2f", order.lineTotal()) + "?";
        String consequence = deleting
                ? "Deleting permanently removes this order and every saved product line. It "
                        + "cannot be undone. Inventory is unchanged because completed orders cannot "
                        + "be deleted."
                : "Cancelling changes this order to Cancelled and removes it from active packing "
                        + "and Market Day work. It does not deduct or restore inventory. Cancelled "
                        + "orders remain as history and cannot be reopened through this action.";
        String details = "Current status: " + order.getStatus() + "\n"
                + "Handoff type: " + friendlyHandoffType(order.getHandoffType()) + "\n"
                + "Pickup / delivery date: " + order.getOrderDate() + "\n"
                + confirmationContact(order.getCustomer()) + "\n" + instructions + "\n\n"
                + "Products: " + order.getLines().size() + "\n"
                + (products.isEmpty() ? "No saved products." : String.join("\n", products))
                + "\n\nOrder total: $"
                + String.format(Locale.ROOT, "%,.2f", order.lineTotal()) + "\n" + consequence
                + "\nContinue only if every customer, date, instruction, and product above is exact.";
        return new OrderDestructiveReview(heading, details);
    }

    static JComponent orderDestructivePanel(OrderDestructiveReview review) {
        JPanel content = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        content.setName("crm.orderDestructiveReview");
        content.setBackground(FarmUi.surfaceColor());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel heading = FarmUi.wrappingStatusBadge(review.heading(), FarmUi.Tone.DANGER);
        heading.setFont(heading.getFont().deriveFont(13f));
        heading.setName("crm.orderDestructiveHeading");
        heading.setHorizontalAlignment(SwingConstants.LEFT);
        content.add(heading, BorderLayout.NORTH);
        JTextArea details = FarmUi.displayArea(11, 62);
        details.setName("crm.orderDestructiveDetails");
        details.setText(review.details());
        details.setCaretPosition(0);
        details.setBackground(FarmUi.surfaceColor());
        details.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        details.getAccessibleContext().setAccessibleName(
                "Exact order and destructive action consequences");
        details.getAccessibleContext().setAccessibleDescription(review.details());
        JScrollPane scroll = new JScrollPane(details);
        scroll.setName("crm.orderDestructiveScroll");
        scroll.setPreferredSize(new Dimension(680, 280));
        scroll.setMinimumSize(new Dimension(340, 180));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getAccessibleContext().setAccessibleName(
                "Scrollable cancellation or deletion review");
        content.add(scroll, BorderLayout.CENTER);
        content.getAccessibleContext().setAccessibleName(
                review.heading() + " exact destructive-action review");
        return content;
    }

    private JComponent orderReviewDialog(JComponent review) {
        JScrollPane scroll = FarmUi.dialogScroll(panel, review, review.getPreferredSize());
        scroll.setName("crm.orderReviewDialogScroll");
        scroll.getAccessibleContext().setAccessibleName("Scrollable order action review");
        return scroll;
    }

    private void startOrderMutation(OrderMutationRequest request) {
        if (runningOrderMutation || fulfillingOrder || creatingOrder || addingOrderLine
                || pendingProductWriteSuccess != null || pendingDestructiveSuccess != null
                || pendingCorrectionSuccess != null) {
            return;
        }
        runningOrderMutation = true;
        setOrderFulfillmentControls(false);
        if (request.action() == OrderMutation.CONFIRM) {
            confirmOrderBtn.setText("Confirming…");
        } else if (request.action() == OrderMutation.SAVE_DATE) {
            saveOrderDateBtn.setText("Saving date…");
        } else if (request.action() == OrderMutation.SAVE_HANDOFF) {
            saveOrderHandoffBtn.setText("Saving handoff…");
        } else if (request.action() == OrderMutation.SAVE_NOTES) {
            saveOrderNotesBtn.setText("Saving instructions…");
        } else if (request.action() == OrderMutation.REMOVE_LINE) {
            removeOrderLineBtn.setText("Removing product…");
        } else {
            runOrderToolBtn.setText(request.action().busyLabel());
        }
        String busy = request.action().busyStatus(request.orderId());
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, busy, FarmUi.Tone.INFO);
        String busyDescription = request.action().label() + " for order #" + request.orderId()
                + " is running in the background. The captured order and lifecycle controls "
                + "are locked to prevent a duplicate or wrong-target update.";
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(busyDescription);
        fulfillmentStatusLabel.setToolTipText(busyDescription);
        status(busy);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                switch (request.action()) {
                    case CONFIRM -> presenter.confirmOrder(
                            request.orderId(), request.reviewSnapshot());
                    case CANCEL -> presenter.cancelOrder(
                            request.orderId(), request.reviewSnapshot());
                    case SAVE_NOTES -> presenter.updateOrderNotes(
                            request.orderId(), request.reviewedValue(), request.payload());
                    case SAVE_DATE -> presenter.updateOrderDate(
                            request.orderId(), request.reviewedValue(), request.payload());
                    case SAVE_HANDOFF -> presenter.updateOrderHandoffType(
                            request.orderId(), request.reviewedValue(), request.payload());
                    case REMOVE_LINE -> presenter.removeOrderLine(
                            request.orderId(), request.lineId(), request.reviewSnapshot(),
                            request.reviewedLine());
                    case DELETE -> presenter.deleteOrder(
                            request.orderId(), request.reviewSnapshot());
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    completeOrderMutation(request);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failOrderMutation(request, exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failOrderMutation(request, exception.getCause() == null
                            ? exception : exception.getCause());
                }
            }
        }.execute();
    }

    private void completeOrderMutation(OrderMutationRequest request) {
        finishOrderMutation();
        if (request.action() == OrderMutation.DELETE) {
            selectedOrderDateDrafts.remove(request.orderId());
            selectedOrderDateDraftBaselines.remove(request.orderId());
            selectedOrderDateConflicts.remove(request.orderId());
            selectedOrderHandoffDrafts.remove(request.orderId());
            selectedOrderHandoffDraftBaselines.remove(request.orderId());
            selectedOrderHandoffConflicts.remove(request.orderId());
            selectedOrderNotesDrafts.remove(request.orderId());
            selectedOrderNotesDraftBaselines.remove(request.orderId());
            selectedOrderNotesConflicts.remove(request.orderId());
            orderTable.clearSelection();
            orderProductDrafts.remove(request.orderId());
            pendingOrderSelectionId = null;
        } else {
            pendingOrderSelectionId = request.orderId();
        }
        if (request.action() == OrderMutation.CONFIRM) {
            pendingConfirmationSuccess = new ConfirmationSuccessPresentation(
                    request.orderId(), request.description());
        }
        if (request.action() == OrderMutation.CANCEL
                || request.action() == OrderMutation.DELETE) {
            pendingDestructiveSuccess = new DestructiveSuccessPresentation(
                    request.action(), request.orderId(), request.description());
        }
        if (request.action() == OrderMutation.REMOVE_LINE) {
            String productName = request.reviewedLine() == null
                    ? "The selected product" : request.reviewedLine().productName();
            pendingProductWriteSuccess = new ProductWriteSuccessPresentation(
                    request.orderId(), productName, true,
                    request.action().successDescription(request.description()));
        }
        if (request.action() == OrderMutation.SAVE_DATE
                || request.action() == OrderMutation.SAVE_HANDOFF
                || request.action() == OrderMutation.SAVE_NOTES) {
            pendingCorrectionSuccess = new CorrectionSuccessPresentation(
                    request.action(), request.orderId(), request.description(), request.payload());
        }
        if (request.action() == OrderMutation.SAVE_DATE) {
            selectedOrderDateDrafts.remove(request.orderId());
            selectedOrderDateDraftBaselines.remove(request.orderId());
            selectedOrderDateConflicts.remove(request.orderId());
            selectedOrderDateOrderId = request.orderId();
            selectedOrderDateBaseline = request.payload();
            pendingOrderDateRefreshId = request.orderId();
            pendingOrderDateRefreshValue = request.payload();
        } else if (request.action() == OrderMutation.SAVE_HANDOFF) {
            selectedOrderHandoffDrafts.remove(request.orderId());
            selectedOrderHandoffDraftBaselines.remove(request.orderId());
            selectedOrderHandoffConflicts.remove(request.orderId());
            selectedOrderHandoffOrderId = request.orderId();
            selectedOrderHandoffBaseline = request.payload();
            pendingOrderHandoffRefreshId = request.orderId();
            pendingOrderHandoffRefreshValue = request.payload();
        } else if (request.action() == OrderMutation.SAVE_NOTES) {
            selectedOrderNotesDrafts.remove(request.orderId());
            selectedOrderNotesDraftBaselines.remove(request.orderId());
            selectedOrderNotesConflicts.remove(request.orderId());
            selectedOrderNotesOrderId = request.orderId();
            selectedOrderNotesBaseline = request.payload();
            pendingOrderNotesRefreshId = request.orderId();
            pendingOrderNotesRefreshValue = request.payload();
        }
        refreshData();
        if (host != null) {
            host.refreshAll();
        }
        String message = request.action().successStatus(request.orderId());
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, message, FarmUi.Tone.SUCCESS);
        String description = request.action().successDescription(request.description());
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(description);
        fulfillmentStatusLabel.setToolTipText(description);
        status(description);
    }

    private void failOrderMutation(OrderMutationRequest request, Throwable failure) {
        finishOrderMutation();
        String detail = FarmUi.actionableError(
                request.action().label() + " for order #" + request.orderId(), failure);
        boolean staleLine = failure instanceof OrderService.StaleOrderLineException;
        boolean staleRemoval = request.action() == OrderMutation.REMOVE_LINE
                && (staleLine || failure instanceof OrderService.StaleOrderReviewException);
        String lowerDetail = detail.toLowerCase(Locale.ROOT);
        boolean staleDestructive = (request.action() == OrderMutation.CANCEL
                || request.action() == OrderMutation.DELETE)
                && lowerDetail.contains("changed after") && lowerDetail.contains("review");
        boolean staleConfirmation = request.action() == OrderMutation.CONFIRM
                && lowerDetail.contains("changed after confirmation review");
        boolean staleCorrection = staleRemoval || staleDestructive || staleConfirmation
                || lowerDetail.contains("changed after you started editing");
        String message = staleConfirmation
                ? "Order #" + request.orderId() + " changed · refreshing current draft"
                : staleCorrection
                ? "Order #" + request.orderId() + " changed · refresh and review"
                : "Order #" + request.orderId() + " not updated · retry available";
        String recovery = staleConfirmation
                ? detail + " The exact draft remains selected while CRM reloads its current "
                        + "customer, handoff, date, instructions, products, and stock guidance. "
                        + "Open a new confirmation review only after that refresh finishes."
                : staleRemoval
                ? detail + " The saved order is refreshing. Review its current customer, date, "
                        + "handoff, status, instructions, and every saved product before choosing "
                        + "Remove again; nothing was removed."
                : staleDestructive
                ? detail + " The saved order is refreshing. Review every current detail before "
                        + "choosing the destructive action again."
                : staleCorrection
                ? detail + " Your entered correction was kept and the saved order is refreshing. "
                        + "Compare the current saved value with your draft, then save again only "
                        + "if your correction is still intended."
                : detail + " The same order remains selected and its entered details "
                        + "were kept; review the reason and retry.";
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, message, FarmUi.Tone.DANGER);
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(recovery);
        fulfillmentStatusLabel.setToolTipText(recovery);
        if (staleCorrection) {
            if (staleConfirmation) {
                pendingConfirmationFailure = new ConfirmationFailurePresentation(
                        request.orderId(), request.description(), detail);
            } else if (staleDestructive) {
                pendingDestructiveFailure = new DestructiveFailurePresentation(
                        request.action(), request.orderId(), request.description(), detail);
            }
            pendingOrderSelectionId = request.orderId();
            refreshData();
            if (staleRemoval) {
                // Set this after refresh startup's synchronous control pass so only the
                // newly loaded authoritative product list can consume the selection.
                pendingOrderLineSelectionId = request.lineId();
            }
            if (host != null) {
                host.refreshAll();
            }
        }
        status(staleCorrection
                ? staleConfirmation
                        ? "Order #" + request.orderId()
                                + " changed before confirmation. No status or inventory value "
                                + "changed; the current draft and stock guidance are refreshing."
                        : staleRemoval
                        ? "Order #" + request.orderId() + " changed before removal. No order or "
                                + "inventory value changed; the current product is refreshing."
                        : staleDestructive
                        ? "Order #" + request.orderId() + " changed before "
                                + (request.action() == OrderMutation.DELETE
                                ? "deletion. Nothing was deleted"
                                : "cancellation. It was not cancelled")
                                + " and inventory was unchanged; the current order is refreshing."
                        : "Order #" + request.orderId()
                                + " changed before the correction saved. No order or inventory "
                                + "value changed; the current order is refreshing and your entered "
                                + "correction was kept for comparison."
                : "Order #" + request.orderId()
                        + " was not updated. The selected order was kept; review the reason and retry.");
        if (panel.isShowing()) {
            error(recovery);
        }
    }

    private void applyConfirmationFailurePresentation(
            ConfirmationFailurePresentation presentation, boolean refreshSucceeded,
            boolean targetRestored) {
        String message;
        String recovery;
        String hostStatus;
        if (refreshSucceeded && targetRestored) {
            message = "Order #" + presentation.orderId()
                    + " changed · review refreshed draft";
            recovery = presentation.detail() + " The exact order remains selected and its current "
                    + "customer, handoff, date, instructions, products, and stock guidance are now "
                    + "refreshed. Open a new confirmation review before confirming.";
            hostStatus = presentation.description() + " was not confirmed. Its current draft and "
                    + "stock guidance are refreshed; review every saved detail before confirming.";
        } else if (refreshSucceeded) {
            message = "Order #" + presentation.orderId()
                    + " not confirmed · no longer in current view";
            recovery = presentation.detail() + " CRM refreshed successfully, but the exact draft "
                    + "is no longer in the current filtered view. It may have been confirmed, "
                    + "cancelled, deleted, or moved outside the filters by another worker. Clear "
                    + "the filters and locate order #" + presentation.orderId()
                    + " before taking any further action; do not confirm from old data.";
            hostStatus = presentation.description() + " was not confirmed from this review. Order #"
                    + presentation.orderId() + " is no longer in the refreshed view; locate its "
                    + "current state before any further action.";
        } else {
            message = "Order #" + presentation.orderId()
                    + " not confirmed · automatic refresh failed";
            recovery = presentation.detail() + " The exact draft remains selected, but CRM could "
                    + "not reload its current order and stock data. Confirmation stays locked; use "
                    + "Refresh CRM before opening another confirmation review.";
            hostStatus = presentation.description() + " was not confirmed, and its automatic CRM "
                    + "refresh failed. Existing work was kept; use Refresh CRM before confirming.";
        }
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, message, FarmUi.Tone.DANGER);
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(recovery);
        fulfillmentStatusLabel.setToolTipText(recovery);
        confirmOrderBtn.setToolTipText(recovery);
        confirmOrderBtn.getAccessibleContext().setAccessibleDescription(recovery);
        status(hostStatus);
    }

    private void applyConfirmationSuccessPresentation(
            ConfirmationSuccessPresentation presentation, boolean refreshSucceeded,
            boolean targetRestored) {
        String message;
        String recovery;
        String hostStatus;
        FarmUi.Tone tone;
        if (refreshSucceeded && targetRestored) {
            message = "Order #" + presentation.orderId()
                    + " confirmed once · review packing";
            recovery = presentation.description() + " was confirmed once. Inventory was not "
                    + "changed, and the refreshed Confirmed order remains selected. Do not "
                    + "confirm it again; review packing checks and complete only at handoff.";
            hostStatus = presentation.description() + " is confirmed once. Review packing "
                    + "checks before completion. Inventory was not changed, and the refreshed "
                    + "Confirmed order is selected.";
            tone = FarmUi.Tone.SUCCESS;
        } else if (refreshSucceeded) {
            message = "Order #" + presentation.orderId()
                    + " confirmed once · outside current view";
            recovery = presentation.description() + " was confirmed once. Inventory was not "
                    + "changed. The order is outside the current filtered view, which is expected "
                    + "when a Draft-only queue hides Confirmed work. Choose Show all or the "
                    + "Confirmed filter to locate it. Do not confirm this order again.";
            hostStatus = presentation.description() + " is confirmed once and inventory was not "
                    + "changed. It is outside the current view; use Show all or Confirmed to "
                    + "review it, and do not confirm it again.";
            tone = FarmUi.Tone.WARNING;
        } else {
            message = "Order #" + presentation.orderId()
                    + " confirmed once · exact-order reload failed";
            recovery = presentation.description() + " was confirmed once and inventory was not "
                    + "changed, but CRM could not reload the exact order. Retry Refresh CRM only; "
                    + "do not run confirmation again.";
            hostStatus = presentation.description() + " is confirmed once and inventory was not "
                    + "changed, but CRM reload failed. Retry Refresh CRM; do not confirm it again.";
            tone = FarmUi.Tone.WARNING;
        }
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, message, tone);
        fulfillmentStatusLabel.setToolTipText(recovery);
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(recovery);
        confirmOrderBtn.setToolTipText(recovery);
        confirmOrderBtn.getAccessibleContext().setAccessibleDescription(recovery);
        applyLifecycleRefreshGuidance(recovery, refreshSucceeded);
        status(hostStatus);
    }

    private static String confirmationReloadRecovery(
            ConfirmationSuccessPresentation presentation) {
        return presentation.description() + " was confirmed once. Inventory was not changed, but "
                + "CRM has not reloaded the exact order. Use Refresh CRM only; do not add or "
                + "re-enter a product from retained pre-confirmation data.";
    }

    private void applyDestructiveFailurePresentation(
            DestructiveFailurePresentation presentation, boolean refreshSucceeded,
            boolean targetRestored) {
        boolean deleting = presentation.action() == OrderMutation.DELETE;
        String action = deleting ? "deletion" : "cancellation";
        String imperative = deleting ? "Delete selected order" : "Cancel selected order";
        String message;
        String recovery;
        String hostStatus;
        if (refreshSucceeded && targetRestored) {
            message = "Order #" + presentation.orderId()
                    + " changed · review refreshed order";
            recovery = presentation.detail() + " The exact order remains selected and its current "
                    + "customer, status, handoff, date, instructions, products, prices, and total "
                    + "are now refreshed. Open a new consequence review before choosing "
                    + imperative + ".";
            hostStatus = presentation.description() + " was not changed. Its current saved order "
                    + "is refreshed; review every detail before attempting " + action + " again.";
        } else if (refreshSucceeded) {
            message = "Order #" + presentation.orderId() + " " + action
                    + " stopped · no longer in current view";
            recovery = presentation.detail() + " CRM refreshed successfully, but the exact order "
                    + "is no longer in the current filtered view. It may have been completed, "
                    + "cancelled, deleted, or moved outside the filters by another worker. Clear "
                    + "the filters and locate order #" + presentation.orderId()
                    + " before any further action; do not reuse the old consequence review.";
            hostStatus = presentation.description() + " was not changed from this review. Order #"
                    + presentation.orderId() + " is no longer in the refreshed view; locate its "
                    + "current state before any further action.";
        } else {
            message = "Order #" + presentation.orderId() + " " + action
                    + " stopped · automatic refresh failed";
            recovery = presentation.detail() + " The exact order remains selected, but CRM could "
                    + "not reload its current saved details. " + imperative
                    + " stays locked; use Refresh CRM before opening another consequence review.";
            hostStatus = presentation.description() + " was not changed, and its automatic CRM "
                    + "refresh failed. Existing work was kept; use Refresh CRM before attempting "
                    + action + " again.";
        }
        FarmUi.updateStatusBadge(fulfillmentStatusLabel, message, FarmUi.Tone.DANGER);
        fulfillmentStatusLabel.getAccessibleContext().setAccessibleDescription(recovery);
        fulfillmentStatusLabel.setToolTipText(recovery);
        runOrderToolBtn.setToolTipText(recovery);
        runOrderToolBtn.getAccessibleContext().setAccessibleDescription(recovery);
        status(hostStatus);
    }

    private void finishOrderMutation() {
        runningOrderMutation = false;
        confirmOrderBtn.setText("Confirm order");
        saveOrderDateBtn.setText("Save order date");
        saveOrderHandoffBtn.setText("Save handoff type");
        saveOrderNotesBtn.setText("Save order instructions");
        removeOrderLineBtn.setText("Remove selected product");
        runOrderToolBtn.setText("Run action");
        setOrderFulfillmentControls(true);
        applyRolePermissions(canWrite);
    }

    private void exportInvoicePdf() {
        Long id = selectedOrderId();
        if (id == null) {
            error("Select an order to print an invoice.");
            return;
        }
        File file = files.chooseSave(panel, "Save order invoice PDF",
                new File("invoice-order-" + id + ".pdf")).orElse(null);
        if (file != null && approveReplacement(file)) {
            startInvoiceExport(id, file);
        }
    }

    void startInvoiceExport(long orderId, File file) {
        if (file == null || !beginDocumentWork("Creating invoice PDF…",
                "The selected order invoice is being rendered and saved in the background.")) {
            return;
        }
        startDocumentFileExport(file,
                () -> com.flowerfarm.safety.SafeFiles.write(file.toPath(), presenter.invoicePdf(orderId)),
                "Invoice saved · order #" + orderId,
                "Invoice saved as “" + file.getName() + "”.",
                "Saving invoice for order #" + orderId);
    }

    private void exportOrdersCsv(boolean filteredOnly) {
        String title = filteredOnly ? "Export filtered orders" : "Export all orders";
        File file = files.chooseSave(panel, title,
                new File(filteredOnly ? "orders_filtered.csv" : "orders_export.csv"))
                .orElse(null);
        if (file == null) {
            return;
        }
        if (approveReplacement(file)) {
            startOrdersExport(file, filteredOnly);
        }
    }

    void startOrdersExport(File file, boolean filteredOnly) {
        if (file == null || !beginDocumentWork("Exporting orders…",
                "The captured order rows are being written in the background.")) {
            return;
        }
        List<CustomerOrder> snapshot = List.copyOf(lastOrderView);
        startDocumentFileExport(file,
                () -> {
                    if (filteredOnly) {
                        presenter.exportOrders(file.getAbsolutePath(), snapshot);
                    } else {
                        presenter.exportAllOrders(file.getAbsolutePath());
                    }
                },
                "Orders CSV saved · "
                        + (filteredOnly ? snapshot.size() + " visible rows" : "all rows"),
                "Orders exported → " + file.getName()
                        + (filteredOnly ? " (filtered view)" : " (all)"),
                "Exporting orders");
    }

    private boolean beginDocumentWork(String message, String description) {
        if (runningDocument) {
            return false;
        }
        runningDocument = true;
        updateCustomerFormMode();
        updateOrderActions();
        showDocumentState(message, description, FarmUi.Tone.INFO);
        status(message);
        return true;
    }

    private void startDocumentFileExport(File file, DocumentWriter writer,
                                         String successMessage, String hostMessage,
                                         String errorAction) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                writer.write();
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

    private void completeDocumentWork(String message, String description,
                                      FarmUi.Tone tone, String hostMessage) {
        runningDocument = false;
        updateCustomerFormMode();
        updateOrderActions();
        showDocumentState(message, description, tone);
        status(hostMessage);
    }

    private void failDocumentWork(String action, Throwable failure) {
        runningDocument = false;
        updateCustomerFormMode();
        updateOrderActions();
        String detail = FarmUi.actionableError(action, failure);
        showDocumentState("Document or export failed · retry",
                detail + " Customer rows, order rows, filters, selection, and entry work were kept.",
                FarmUi.Tone.DANGER);
        status(action + " failed. Current CRM work was kept; retry when ready.");
        if (panel.isShowing()) {
            error(detail);
        }
    }

    private void showDocumentInputFailure(String message) {
        showDocumentState("Statement dates need attention",
                message + " Correct the highlighted date and retry.", FarmUi.Tone.DANGER);
        if (panel.isShowing()) {
            error(message);
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
        return choice == JOptionPane.YES_OPTION;
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

    @FunctionalInterface
    private interface DocumentWriter {
        void write() throws Exception;
    }

    private static String nullToEmpty(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String friendlyCustomerType(String value) {
        if (value == null || value.isBlank()) {
            return "Other";
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String friendlyHandoffType(String value) {
        if (value == null || value.isBlank() || "UNSPECIFIED".equalsIgnoreCase(value)) {
            return "Unspecified";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private void status(String msg) {
        if (host != null) {
            host.setStatus(msg);
        }
    }

    private void error(String msg) {
        dialogs.showMessage(panel, msg, "Customers & Orders", JOptionPane.ERROR_MESSAGE);
    }

    private String selectedStatusFilter() {
        String selected = filterStatus == null
                ? "All statuses" : String.valueOf(filterStatus.getSelectedItem());
        return switch (selected) {
            case "Open" -> "OPEN";
            case "Draft" -> "DRAFT";
            case "Confirmed" -> "CONFIRMED";
            case "Completed" -> "FULFILLED";
            case "Cancelled" -> "CANCELLED";
            default -> "ALL";
        };
    }

    private String selectedHandoffFilter() {
        String selected = filterHandoff == null
                ? "All handoffs" : String.valueOf(filterHandoff.getSelectedItem());
        return switch (selected) {
            case "Needs review" -> "UNSPECIFIED";
            case "Pickup" -> "PICKUP";
            case "Delivery" -> "DELIVERY";
            case "Market" -> "MARKET";
            default -> "ALL";
        };
    }

    private String selectedReadinessFilter() {
        String selected = filterStatus == null
                ? "All statuses" : String.valueOf(filterStatus.getSelectedItem());
        return switch (selected) {
            case "Ready to complete" -> "READY";
            case "Needs attention" -> "ATTENTION";
            default -> "ALL";
        };
    }

    private void markOrderError(CrmPresenter.InputValidationException exception) {
        JTextField field = "orderDate".equals(exception.field())
                ? orderDate : orderLineField(exception.field());
        FarmUi.markFieldError(field, exception.getMessage());
    }

    private JTextField orderLineField(String field) {
        return switch (field == null ? "" : field) {
            case "quantity" -> orderQty;
            case "unit" -> orderUnit;
            case "price" -> orderPrice;
            default -> orderProduct;
        };
    }

    private void clearOrderLineInlineValidation() {
        FarmUi.clearFieldError(orderProduct);
        FarmUi.clearFieldError(orderQty);
        FarmUi.clearFieldError(orderUnit);
        FarmUi.clearFieldError(orderPrice);
    }

    private void markOrderLineInlineError(String field, String message) {
        JTextField target = orderLineField(field);
        String guidance = message == null || message.isBlank()
                ? "Check this order product value." : message;
        target.putClientProperty("JComponent.outline", "error");
        target.setToolTipText(guidance);
        target.getAccessibleContext().setAccessibleDescription(guidance);
    }

    private void resetOrderProductFields() {
        if (orderInventoryProduct != null) {
            orderInventoryProduct.setSelectedItem(null);
        }
        orderProduct.setText("");
        orderQty.setText("1");
        orderUnit.setText(defaultUnit);
        orderPrice.setText("");
        orderProduct.requestFocusInWindow();
    }

    private void clearOrderValidation() {
        FarmUi.clearFieldError(orderDate);
        FarmUi.clearFieldError(orderProduct);
        FarmUi.clearFieldError(orderQty);
        FarmUi.clearFieldError(orderUnit);
        FarmUi.clearFieldError(orderPrice);
    }

    private void showOrderCorrectionReady() {
        updateOrderLinePreview();
        String guidance = "The highlighted order value changed. Submit again to validate and save it.";
        FarmUi.updateStatusBadge(selectedOrderLabel,
                "Changed · ready to retry order", FarmUi.Tone.INFO);
        selectedOrderLabel.setToolTipText(guidance);
        selectedOrderLabel.getAccessibleContext().setAccessibleDescription(guidance);
    }

    private void watchValidationCorrection(JTextField field, Runnable correction) {
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() {
                if (!"error".equals(field.getClientProperty("JComponent.outline"))) {
                    return;
                }
                FarmUi.clearFieldError(field);
                correction.run();
            }

            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { changed(); }
        });
    }

    private record CrmRefreshRequest(String customerQuery, String orderStatus,
                                     String orderCustomer, String orderHandoff,
                                     String orderReadiness,
                                     String from, String to) {
    }

    private static final class CrmRefreshSourceException extends RuntimeException {
        private final String source;

        private CrmRefreshSourceException(String source, RuntimeException cause) {
            super(source + " unavailable", cause);
            this.source = source;
        }

        private String source() {
            return source;
        }
    }

    private record CustomerSaveRequest(Long customerId, CrmPresenter.CustomerDraft draft,
                                       String displayName) {
        boolean editing() {
            return customerId != null;
        }
    }

    private record CustomerDeleteDecision(CrmPresenter.CustomerRow customer,
                                           Long editingId, CrmPresenter.CustomerDraft draft,
                                           long writeRevision) {
    }

    private record CustomerFormState(Long editingId, CrmPresenter.CustomerDraft draft) {
    }

    private record CustomerReplacementDecision(CustomerFormState form,
                                                long formRevision, long writeRevision,
                                                String refreshFailure) {
    }

    private record OpenCustomerRequest(Long orderId, String source,
                                       long customerId, String customerName) {
    }

    private record DuplicateLineApproval(
            long orderId, CrmPresenter.LineDraft draft,
            OrderService.OrderReviewSnapshot reviewedOrder) {
    }

    private record ProductEntryClearApproval(
            Long activeOrderId, Long editingOrderId, Long editingLineId,
            Long unavailableOrderId, CrmPresenter.LineDraft draft) {
    }

    private record NewOrderIdentityClearApproval(
            Long customerId, String orderDate, String handoffType, String instructions) {
    }

    private record EarlyCompletionApproval(
            long orderId, OrderService.OrderReviewSnapshot reviewedOrder) {
    }

    private record OverdueCompletionApproval(
            long orderId, OrderService.OrderReviewSnapshot reviewedOrder) {
    }

    private record OpenCustomerPresentation(OpenCustomerRequest request, boolean restored) {
    }

    private enum CustomerWriteAction {
        ADD, EDIT, DELETE
    }

    private record CustomerWriteSuccessPresentation(
            CustomerWriteAction action, Long customerId, String name) {
    }

    private record OrderSaveRequest(
            CrmPresenter.CustomerChoice customer, CrmPresenter.OrderDraft draft,
            java.util.Map<Long, OrderService.OrderReviewSnapshot> reviewedActiveOrders,
            boolean separateOrderApproved) {
        private OrderSaveRequest(
                CrmPresenter.CustomerChoice customer, CrmPresenter.OrderDraft draft) {
            this(customer, draft, java.util.Map.of(), false);
        }

        private OrderSaveRequest approveSeparate(
                List<CrmPresenter.ActiveOrderMatch> matches) {
            java.util.Map<Long, OrderService.OrderReviewSnapshot> reviewed = matches.stream()
                    .filter(match -> match.row() != null && match.row().id() != null
                            && match.reviewedOrder() != null)
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(
                            match -> match.row().id(),
                            CrmPresenter.ActiveOrderMatch::reviewedOrder));
            return new OrderSaveRequest(customer, draft, reviewed, true);
        }
    }

    private record OrderLineSaveRequest(
            long orderId, CrmPresenter.LineDraft draft, String productName, Long lineId,
            OrderService.OrderReviewLine reviewedLine,
            OrderService.OrderReviewSnapshot reviewedOrder) {
        boolean editing() {
            return lineId != null;
        }
    }

    private record ProductConflictPresentation(
            String message, String recovery, String hostStatus) {
    }

    private record ProductWriteSuccessPresentation(
            long orderId, String productName, boolean removed, String description) {
    }

    private record ProductOrderConflict(
            long orderId, OrderService.OrderReviewSnapshot reviewed,
            OrderService.OrderReviewSnapshot current) {
    }

    private record FulfillmentFailurePresentation(
            long orderId, String description, String detail, boolean staleReview) {
    }

    private record FulfillmentSuccessPresentation(long orderId, String description) {
    }

    private record ConfirmationFailurePresentation(
            long orderId, String description, String detail) {
    }

    private record ConfirmationSuccessPresentation(long orderId, String description) {
    }

    private record DestructiveSuccessPresentation(
            OrderMutation action, long orderId, String description) {
    }

    private record CorrectionSuccessPresentation(
            OrderMutation action, long orderId, String description, String value) {
    }

    private record DestructiveFailurePresentation(
            OrderMutation action, long orderId, String description, String detail) {
    }

    private record ProductEntryDraft(
            String product, String quantity, String unit, String unitPrice, Long editingLineId,
            OrderService.OrderReviewLine reviewedLine,
            OrderService.OrderReviewSnapshot reviewedOrder) {
        private ProductEntryDraft {
            product = product == null ? "" : product;
            quantity = quantity == null ? "" : quantity;
            unit = unit == null ? "" : unit;
            unitPrice = unitPrice == null ? "" : unitPrice;
        }

        static ProductEntryDraft empty(String defaultUnit) {
            return new ProductEntryDraft("", "1", defaultUnit, "", null, null, null);
        }

        ProductEntryDraft withReviewedOrder(OrderService.OrderReviewSnapshot value) {
            return new ProductEntryDraft(
                    product, quantity, unit, unitPrice, editingLineId, reviewedLine, value);
        }

        boolean hasWork(String defaultUnit) {
            return editingLineId != null
                    || !product.trim().isEmpty()
                    || !unitPrice.trim().isEmpty()
                    || !"1".equals(quantity.trim())
                    || !defaultUnit.equalsIgnoreCase(unit.trim());
        }
    }

    private record OrderMutationRequest(
            OrderMutation action, long orderId, String description, String payload, Long lineId,
            String reviewedValue, OrderService.OrderReviewLine reviewedLine,
            OrderService.OrderReviewSnapshot reviewSnapshot) {
        private OrderMutationRequest(OrderMutation action, long orderId,
                                     String description, String payload, Long lineId) {
            this(action, orderId, description, payload, lineId, null, null, null);
        }

        private OrderMutationRequest(OrderMutation action, long orderId,
                                     String description, String payload, Long lineId,
                                     String reviewedValue) {
            this(action, orderId, description, payload, lineId, reviewedValue, null, null);
        }

        private OrderMutationRequest(OrderMutation action, long orderId,
                                     String description, String payload, Long lineId,
                                     OrderService.OrderReviewLine reviewedLine) {
            this(action, orderId, description, payload, lineId, null, reviewedLine, null);
        }

        private OrderMutationRequest(OrderMutation action, long orderId,
                                     String description, String payload, Long lineId,
                                     OrderService.OrderReviewSnapshot reviewSnapshot) {
            this(action, orderId, description, payload, lineId, null, null, reviewSnapshot);
        }
    }

    private enum OrderMutation {
        CONFIRM, CANCEL, SAVE_NOTES, SAVE_DATE, SAVE_HANDOFF, REMOVE_LINE, DELETE;

        String label() {
            return switch (this) {
                case CONFIRM -> "Confirming order";
                case CANCEL -> "Cancelling order";
                case SAVE_NOTES -> "Saving order notes";
                case SAVE_DATE -> "Saving order date";
                case SAVE_HANDOFF -> "Saving order handoff type";
                case REMOVE_LINE -> "Removing order product";
                case DELETE -> "Deleting order";
            };
        }

        String busyLabel() {
            return switch (this) {
                case CANCEL -> "Cancelling…";
                case SAVE_NOTES -> "Saving notes…";
                case SAVE_DATE -> "Saving date…";
                case SAVE_HANDOFF -> "Saving handoff…";
                case REMOVE_LINE -> "Removing product…";
                case DELETE -> "Deleting…";
                case CONFIRM -> "Confirming…";
            };
        }

        String busyStatus(long orderId) {
            return label() + " #" + orderId + "…";
        }

        String successStatus(long orderId) {
            return switch (this) {
                case CONFIRM -> "Order #" + orderId + " confirmed · review packing";
                case CANCEL -> "Order #" + orderId + " cancelled";
                case SAVE_NOTES -> "Order #" + orderId + " instructions saved";
                case SAVE_DATE -> "Order #" + orderId + " date saved · Market Day refreshed";
                case SAVE_HANDOFF -> "Order #" + orderId
                        + " handoff saved · packing refreshed";
                case REMOVE_LINE -> "Order #" + orderId + " product removed";
                case DELETE -> "Order #" + orderId + " deleted";
            };
        }

        String successDescription(String description) {
            String target = description == null || description.isBlank()
                    ? "The selected order" : description;
            return switch (this) {
                case CONFIRM -> target + " is confirmed and eligible for packing review.";
                case CANCEL -> target + " was cancelled.";
                case SAVE_NOTES -> "Instructions were saved for " + target
                        + ". Invoices, packing lists, Market Day, and farm summaries were "
                        + "refreshed; inventory was not changed.";
                case SAVE_DATE -> "The pickup or delivery date for " + target
                        + " was saved. Market Day and farm summaries were refreshed; "
                        + "inventory was not changed.";
                case SAVE_HANDOFF -> "The handoff type for " + target
                        + " was saved. Invoices, packing lists, Market Day, and farm summaries "
                        + "were refreshed; inventory was not changed.";
                case REMOVE_LINE -> target + " was removed. Inventory was not changed.";
                case DELETE -> target + " was permanently deleted.";
            };
        }
    }

    private record CrmRefreshSnapshot(List<CrmPresenter.CustomerRow> customerRows,
                                      OrderReadinessView orderReadiness,
                                      List<CrmPresenter.CustomerChoice> customerChoices,
                                      List<CrmPresenter.InventoryChoice> inventoryChoices,
                                      List<CustomerOrder> confirmedCompletionQueue) {
    }

    record OrderReadinessView(CrmPresenter.OrderView view, String filter,
                              int readyCount, int attentionCount,
                              Map<Long, OrderStockAssessment> assessments,
                              Set<Long> reservationLimitedOrderIds) {
        OrderReadinessView {
            assessments = assessments == null ? Map.of() : Map.copyOf(assessments);
            reservationLimitedOrderIds = reservationLimitedOrderIds == null
                    ? Set.of() : Set.copyOf(reservationLimitedOrderIds);
        }
    }

    private record OrderLineChoice(Long id, String product, double quantity,
                                   String unit, double unitPrice) {
        OrderService.OrderReviewLine reviewLine() {
            return new OrderService.OrderReviewLine(
                    id, product, quantity, unit, unitPrice);
        }

        @Override
        public String toString() {
            String quantityText = quantity == Math.rint(quantity)
                    ? Long.toString((long) quantity)
                    : String.format(Locale.ROOT, "%.2f", quantity)
                            .replaceAll("0+$", "").replaceAll("\\.$", "");
            return product + " · " + quantityText + " " + unit + " · $"
                    + String.format(Locale.ROOT, "%,.2f", unitPrice);
        }
    }

    record OrderLifecycleReadiness(boolean ready, String guidance,
                                   boolean deliveryContextMissing) {
        static OrderLifecycleReadiness readyState() {
            return new OrderLifecycleReadiness(true, "", false);
        }
    }

    record OrderCustomerContext(String displayText, String accessibleText,
                                boolean missingContact) {
    }

    record OrderConfirmationReview(String heading, String details) {
    }

    record OrderCompletionReview(String heading, String details) {
    }

    record OrderDestructiveReview(String heading, String details) {
    }

    record OrderStockAssessment(boolean available, int productCount,
                                List<OrderStockGap> gaps) {
        OrderStockAssessment {
            gaps = gaps == null ? List.of() : List.copyOf(gaps);
        }

        static OrderStockAssessment unavailable() {
            return new OrderStockAssessment(false, 0, List.of());
        }
    }

    record OrderStockGap(String productName, double required, double available,
                         double missing, boolean noInventoryMatch, boolean unitMismatch,
                         String orderUnit, String inventoryUnit, boolean quantityInvalid) {
        OrderStockGap(String productName, double required, double available,
                      double missing, boolean noInventoryMatch, boolean unitMismatch,
                      String orderUnit, String inventoryUnit) {
            this(productName, required, available, missing, noInventoryMatch,
                    unitMismatch, orderUnit, inventoryUnit, false);
        }
    }

}
