package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.OrderService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * CRM — customers + wholesale/market orders: search, filter, confirm, fulfill, export.
 */
public class CrmTab implements FlowerFarmTab {

    private final CustomerService customerService;
    private final OrderService orderService;
    private final CustomerStatementService customerStatementService;
    private final TabHost host;

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

    private JComboBox<CustomerItem> orderCustomer;
    private JTextField orderProduct;
    private JTextField orderQty;
    private JTextField orderUnit;
    private JTextField orderPrice;
    private JComboBox<String> orderStatus;
    private JTextField orderNotes;

    private JComboBox<String> filterStatus;
    private JTextField filterCustomer;
    private JTextField filterFrom;
    private JTextField filterTo;
    private JLabel orderStatusLabel;
    private List<CustomerOrder> lastOrderView = List.of();

    private JButton addCustomerBtn;
    private JButton loadCustomerBtn;
    private JButton saveCustomerBtn;
    private JButton clearCustomerBtn;
    private JButton deleteCustomerBtn;
    private JButton createOrderBtn;
    private JButton confirmOrderBtn;
    private JButton fulfillOrderBtn;
    private JButton cancelOrderBtn;
    private JButton saveNotesBtn;
    private JButton deleteOrderBtn;

    public CrmTab(CustomerService customerService, OrderService orderService, TabHost host) {
        this(customerService, orderService, null, host);
    }

    public CrmTab(CustomerService customerService, OrderService orderService,
                  CustomerStatementService customerStatementService, TabHost host) {
        this.customerService = customerService;
        this.orderService = orderService;
        this.customerStatementService = customerStatementService;
        this.host = host;
    }

    @Override public String getTabTitle() { return "CRM"; }

    @Override
    public String getDescription() {
        return "Customers and wholesale / market orders — filter, confirm, fulfill, export";
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
        applyCustomerFilter();
        applyOrderFilter();

        CustomerItem selected = (CustomerItem) orderCustomer.getSelectedItem();
        orderCustomer.removeAllItems();
        for (Customer c : customerService.getAll()) {
            orderCustomer.addItem(new CustomerItem(c.getId(), c.getName()));
        }
        if (selected != null) {
            for (int i = 0; i < orderCustomer.getItemCount(); i++) {
                if (orderCustomer.getItemAt(i).id().equals(selected.id())) {
                    orderCustomer.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        // Search / filter / export stay enabled for VIEWER
        GuiPermissions.setWritable(canWrite,
                custName, custContact, custEmail, custPhone, custType, custNotes,
                addCustomerBtn, loadCustomerBtn, saveCustomerBtn, clearCustomerBtn, deleteCustomerBtn,
                orderCustomer, orderProduct, orderQty, orderUnit, orderPrice, orderStatus, orderNotes,
                createOrderBtn, confirmOrderBtn, fulfillOrderBtn, cancelOrderBtn, saveNotesBtn, deleteOrderBtn);
    }

    private void buildUI() {
        panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel header = new JLabel("CRM — Customers & Wholesale Orders (Kitsap market / florists)");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(header, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildCustomerPanel(), buildOrderPanel());
        split.setResizeWeight(0.42);
        panel.add(split, BorderLayout.CENTER);
    }

    private JPanel buildCustomerPanel() {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(BorderFactory.createTitledBorder("Customers"));

        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        custSearch = new JTextField(16);
        custSearch.setToolTipText("Search name, contact, email, phone, type, notes");
        custSearch.addActionListener(e -> applyCustomerFilter());
        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(e -> applyCustomerFilter());
        JButton clearSearch = new JButton("Show all");
        clearSearch.addActionListener(e -> {
            custSearch.setText("");
            applyCustomerFilter();
        });
        searchBar.add(new JLabel("Search:"));
        searchBar.add(custSearch);
        searchBar.add(searchBtn);
        searchBar.add(clearSearch);
        p.add(searchBar, BorderLayout.NORTH);

        customerModel = new DefaultTableModel(
                new String[]{"Id", "Name", "Contact", "Phone", "Email", "Type", "Notes"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        customerTable = new JTable(customerModel);
        customerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        customerTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    loadCustomerIntoForm();
                }
            }
        });
        p.add(new JScrollPane(customerTable), BorderLayout.CENTER);

        JPanel form = new JPanel(new GridLayout(0, 4, 6, 4));
        custName = new JTextField();
        custContact = new JTextField();
        custEmail = new JTextField();
        custPhone = new JTextField();
        custType = new JComboBox<>(new String[]{"WHOLESALE", "MARKET", "FLORIST", "OTHER"});
        custNotes = new JTextField();
        form.add(new JLabel("Name*"));
        form.add(custName);
        form.add(new JLabel("Contact"));
        form.add(custContact);
        form.add(new JLabel("Phone"));
        form.add(custPhone);
        form.add(new JLabel("Email"));
        form.add(custEmail);
        form.add(new JLabel("Type"));
        form.add(custType);
        form.add(new JLabel("Notes"));
        form.add(custNotes);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addCustomerBtn = new JButton("Add customer");
        addCustomerBtn.addActionListener(e -> addCustomer());
        loadCustomerBtn = new JButton("Load selected");
        loadCustomerBtn.addActionListener(e -> loadCustomerIntoForm());
        saveCustomerBtn = new JButton("Save edit");
        saveCustomerBtn.setToolTipText("Update loaded customer (double-click row to load).");
        saveCustomerBtn.addActionListener(e -> saveCustomerEdit());
        clearCustomerBtn = new JButton("Clear form");
        clearCustomerBtn.addActionListener(e -> clearCustomerForm());
        deleteCustomerBtn = new JButton("Delete selected");
        deleteCustomerBtn.addActionListener(e -> deleteCustomer());
        JButton statementBtn = new JButton("Statement PDF…");
        statementBtn.setToolTipText("Account statement for selected customer (default last 90 days). VIEWER OK.");
        statementBtn.addActionListener(e -> exportCustomerStatement());
        buttons.add(addCustomerBtn);
        buttons.add(loadCustomerBtn);
        buttons.add(saveCustomerBtn);
        buttons.add(clearCustomerBtn);
        buttons.add(deleteCustomerBtn);
        buttons.add(statementBtn);

        JPanel south = new JPanel(new BorderLayout());
        south.add(form, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildOrderPanel() {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(BorderFactory.createTitledBorder("Orders"));

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        filterStatus = new JComboBox<>(new String[]{"ALL", "DRAFT", "CONFIRMED", "FULFILLED", "CANCELLED"});
        filterCustomer = new JTextField(10);
        filterFrom = new JTextField(LocalDate.now().minusDays(30).toString(), 10);
        filterTo = new JTextField(LocalDate.now().toString(), 10);
        orderStatusLabel = new JLabel(" ");
        JButton applyFilter = new JButton("Apply filter");
        applyFilter.addActionListener(e -> applyOrderFilter());
        JButton clearFilter = new JButton("Show all");
        clearFilter.addActionListener(e -> {
            filterStatus.setSelectedItem("ALL");
            filterCustomer.setText("");
            filterFrom.setText("");
            filterTo.setText("");
            applyOrderFilter();
        });
        JButton pipeline = new JButton("Pipeline only");
        pipeline.setToolTipText("Show CONFIRMED orders awaiting fulfill.");
        pipeline.addActionListener(e -> {
            filterStatus.setSelectedItem("CONFIRMED");
            applyOrderFilter();
        });
        filterBar.add(new JLabel("Status:"));
        filterBar.add(filterStatus);
        filterBar.add(new JLabel("Customer:"));
        filterBar.add(filterCustomer);
        filterBar.add(new JLabel("From:"));
        filterBar.add(filterFrom);
        filterBar.add(new JLabel("To:"));
        filterBar.add(filterTo);
        filterBar.add(applyFilter);
        filterBar.add(clearFilter);
        filterBar.add(pipeline);
        filterBar.add(orderStatusLabel);
        p.add(filterBar, BorderLayout.NORTH);

        orderModel = new DefaultTableModel(
                new String[]{"Id", "Date", "Customer", "Status", "Lines", "Total $", "Notes"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        orderTable = new JTable(orderModel);
        orderTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        p.add(new JScrollPane(orderTable), BorderLayout.CENTER);

        JPanel form = new JPanel(new GridLayout(0, 4, 6, 4));
        orderCustomer = new JComboBox<>();
        orderProduct = new JTextField("Nootka Rose");
        orderQty = new JTextField("10");
        orderUnit = new JTextField("bunch");
        orderPrice = new JTextField("12.00");
        orderStatus = new JComboBox<>(new String[]{"DRAFT", "CONFIRMED", "FULFILLED", "CANCELLED"});
        orderStatus.setSelectedItem("CONFIRMED");
        orderNotes = new JTextField();

        form.add(new JLabel("Customer*"));
        form.add(orderCustomer);
        form.add(new JLabel("Status"));
        form.add(orderStatus);
        form.add(new JLabel("Product*"));
        form.add(orderProduct);
        form.add(new JLabel("Qty"));
        form.add(orderQty);
        form.add(new JLabel("Unit"));
        form.add(orderUnit);
        form.add(new JLabel("Unit price $"));
        form.add(orderPrice);
        form.add(new JLabel("Notes"));
        form.add(orderNotes);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        createOrderBtn = new JButton("Create order + line");
        createOrderBtn.addActionListener(e -> createOrder());
        confirmOrderBtn = new JButton("Confirm");
        confirmOrderBtn.setToolTipText("DRAFT → CONFIRMED (adds to revenue pipeline; no inventory change).");
        confirmOrderBtn.addActionListener(e -> confirmOrder());
        fulfillOrderBtn = new JButton("Fulfill (deduct inventory)");
        fulfillOrderBtn.setToolTipText("Marks FULFILLED and decrements matching inventory SKUs by product name.");
        fulfillOrderBtn.addActionListener(e -> markFulfilled());
        cancelOrderBtn = new JButton("Cancel order");
        cancelOrderBtn.addActionListener(e -> cancelOrder());
        saveNotesBtn = new JButton("Save notes");
        saveNotesBtn.addActionListener(e -> saveOrderNotes());
        deleteOrderBtn = new JButton("Delete order");
        deleteOrderBtn.addActionListener(e -> deleteOrder());
        JButton invoicePdfBtn = new JButton("Invoice PDF…");
        invoicePdfBtn.setToolTipText("Printable wholesale invoice for the selected order (VIEWER OK).");
        invoicePdfBtn.addActionListener(e -> exportInvoicePdf());
        JButton exportOrders = new JButton("Export view CSV…");
        exportOrders.setToolTipText("Export currently filtered order rows.");
        exportOrders.addActionListener(e -> exportOrdersCsv(true));
        JButton exportAll = new JButton("Export all CSV…");
        exportAll.addActionListener(e -> exportOrdersCsv(false));
        buttons.add(createOrderBtn);
        buttons.add(confirmOrderBtn);
        buttons.add(fulfillOrderBtn);
        buttons.add(cancelOrderBtn);
        buttons.add(saveNotesBtn);
        buttons.add(deleteOrderBtn);
        buttons.add(invoicePdfBtn);
        buttons.add(exportOrders);
        buttons.add(exportAll);

        JPanel south = new JPanel(new BorderLayout());
        south.add(form, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private void applyCustomerFilter() {
        if (customerModel == null) {
            return;
        }
        String q = custSearch == null ? "" : custSearch.getText();
        List<Customer> list = customerService.search(q);
        customerModel.setRowCount(0);
        for (Customer c : list) {
            customerModel.addRow(new Object[]{
                    c.getId(), c.getName(), c.getContactName(), c.getPhone(),
                    c.getEmail(), c.getCustomerType(), c.getNotes()
            });
        }
    }

    private void applyOrderFilter() {
        if (orderModel == null) {
            return;
        }
        try {
            String status = filterStatus == null ? "ALL" : String.valueOf(filterStatus.getSelectedItem());
            String cust = filterCustomer == null ? "" : filterCustomer.getText().trim();
            LocalDate from = parseOptionalDate(filterFrom == null ? "" : filterFrom.getText());
            LocalDate to = parseOptionalDate(filterTo == null ? "" : filterTo.getText());
            if (from != null && to != null && to.isBefore(from)) {
                error("Filter 'To' must be on or after 'From'.");
                return;
            }
            List<CustomerOrder> orders = orderService.filter(status, cust, from, to);
            lastOrderView = orders;
            orderModel.setRowCount(0);
            double total = 0;
            int pipeline = 0;
            for (CustomerOrder o : orders) {
                String cname = o.getCustomer() != null ? o.getCustomer().getName() : "?";
                double line = o.lineTotal();
                total += line;
                if ("CONFIRMED".equalsIgnoreCase(o.getStatus())) {
                    pipeline++;
                }
                orderModel.addRow(new Object[]{
                        o.getId(), o.getOrderDate(), cname, o.getStatus(),
                        o.getLines().size(), String.format("%.2f", line), o.getNotes()
                });
            }
            if (orderStatusLabel != null) {
                orderStatusLabel.setText(String.format(
                        "Showing %d · $%,.2f total · %d pipeline",
                        orders.size(), total, pipeline));
            }
        } catch (DateTimeParseException ex) {
            error("Dates must be YYYY-MM-DD (or blank).");
        }
    }

    private static LocalDate parseOptionalDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return LocalDate.parse(raw.trim());
    }

    private void addCustomer() {
        if (!GuiPermissions.requireWrite(host, panel, "manage customers")) {
            return;
        }
        try {
            Customer c = new Customer(
                    custName.getText(),
                    custContact.getText(),
                    custEmail.getText(),
                    custPhone.getText(),
                    String.valueOf(custType.getSelectedItem()),
                    custNotes.getText()
            );
            customerService.add(c);
            clearCustomerForm();
            refreshData();
            status("Customer added.");
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    private void loadCustomerIntoForm() {
        int row = customerTable.getSelectedRow();
        if (row < 0) {
            error("Select a customer (or double-click a row).");
            return;
        }
        editingCustomerId = (Long) customerModel.getValueAt(row, 0);
        custName.setText(String.valueOf(customerModel.getValueAt(row, 1)));
        custContact.setText(nullToEmpty(customerModel.getValueAt(row, 2)));
        custPhone.setText(nullToEmpty(customerModel.getValueAt(row, 3)));
        custEmail.setText(nullToEmpty(customerModel.getValueAt(row, 4)));
        custType.setSelectedItem(String.valueOf(customerModel.getValueAt(row, 5)));
        custNotes.setText(nullToEmpty(customerModel.getValueAt(row, 6)));
        status("Editing customer id=" + editingCustomerId + " — Save edit to apply.");
    }

    private void saveCustomerEdit() {
        if (!GuiPermissions.requireWrite(host, panel, "edit customers")) {
            return;
        }
        if (editingCustomerId == null) {
            error("Load a customer first (Load selected / double-click).");
            return;
        }
        try {
            Customer c = new Customer(
                    custName.getText(),
                    custContact.getText(),
                    custEmail.getText(),
                    custPhone.getText(),
                    String.valueOf(custType.getSelectedItem()),
                    custNotes.getText()
            );
            customerService.update(editingCustomerId, c);
            Long id = editingCustomerId;
            clearCustomerForm();
            refreshData();
            status("Customer id=" + id + " updated.");
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    private void clearCustomerForm() {
        editingCustomerId = null;
        custName.setText("");
        custContact.setText("");
        custEmail.setText("");
        custPhone.setText("");
        custType.setSelectedItem("WHOLESALE");
        custNotes.setText("");
    }

    private void deleteCustomer() {
        if (!GuiPermissions.requireWrite(host, panel, "delete customers")) {
            return;
        }
        int row = customerTable.getSelectedRow();
        if (row < 0) {
            error("Select a customer.");
            return;
        }
        Long id = (Long) customerModel.getValueAt(row, 0);
        int ok = JOptionPane.showConfirmDialog(panel,
                "Delete customer id=" + id + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            customerService.delete(id);
            if (editingCustomerId != null && editingCustomerId.equals(id)) {
                clearCustomerForm();
            }
            refreshData();
            status("Customer deleted.");
        } catch (Exception ex) {
            error(ex.getMessage() + " (delete their orders first if constrained)");
        }
    }

    private void exportCustomerStatement() {
        if (customerStatementService == null) {
            error("Statement service not available.");
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
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(90);
            // Prefer order filter dates when both filled
            try {
                if (filterFrom != null && !filterFrom.getText().isBlank()
                        && filterTo != null && !filterTo.getText().isBlank()) {
                    from = LocalDate.parse(filterFrom.getText().trim());
                    to = LocalDate.parse(filterTo.getText().trim());
                }
            } catch (DateTimeParseException ignored) {
                // keep default 90-day window
            }
            CustomerStatementService.CustomerStatement statement =
                    customerStatementService.build(id, from, to);
            JTextArea area = new JTextArea(statement.plainText(), 22, 64);
            area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            area.setCaretPosition(0);
            JScrollPane scroll = new JScrollPane(area);
            scroll.setPreferredSize(new Dimension(640, 420));

            Object[] options = {"Export PDF…", "Close"};
            int choice = JOptionPane.showOptionDialog(panel, scroll,
                    "Statement — " + name + " (" + from + " → " + to + ")",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, options, options[1]);
            if (choice == 0) {
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(new File("statement-customer-" + id + ".pdf"));
                if (chooser.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION) {
                    byte[] pdf = customerStatementService.generatePdf(statement);
                    Files.write(chooser.getSelectedFile().toPath(), pdf);
                    status("Statement PDF → " + chooser.getSelectedFile().getName()
                            + " (" + statement.orderCount() + " orders)");
                }
            } else {
                status("Statement: " + statement.orderCount() + " order(s) · $"
                        + String.format("%.2f", statement.grandTotal()));
            }
        } catch (Exception ex) {
            error("Statement failed: " + ex.getMessage());
        }
    }

    private void createOrder() {
        if (!GuiPermissions.requireWrite(host, panel, "create orders")) {
            return;
        }
        CustomerItem ci = (CustomerItem) orderCustomer.getSelectedItem();
        if (ci == null) {
            error("Add a customer first.");
            return;
        }
        try {
            double qty = Double.parseDouble(orderQty.getText().trim());
            double price = Double.parseDouble(orderPrice.getText().trim());
            OrderLine line = new OrderLine(
                    orderProduct.getText().trim(), qty, orderUnit.getText().trim(), price);
            orderService.create(
                    ci.id(),
                    LocalDate.now(),
                    String.valueOf(orderStatus.getSelectedItem()),
                    orderNotes.getText().trim(),
                    List.of(line)
            );
            orderNotes.setText("");
            refreshData();
            status("Order created for " + ci.name());
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    private Long selectedOrderId() {
        int row = orderTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return (Long) orderModel.getValueAt(row, 0);
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
        try {
            orderService.confirm(id);
            refreshData();
            if (host != null) {
                host.refreshAll();
            }
            status("Order #" + id + " confirmed — in revenue pipeline.");
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    private void markFulfilled() {
        if (!GuiPermissions.requireWrite(host, panel, "fulfill orders")) {
            return;
        }
        Long id = selectedOrderId();
        if (id == null) {
            error("Select an order.");
            return;
        }
        String statusNow = String.valueOf(orderModel.getValueAt(orderTable.getSelectedRow(), 3));
        if ("FULFILLED".equalsIgnoreCase(statusNow)) {
            error("Order #" + id + " is already fulfilled.");
            return;
        }
        int ok = JOptionPane.showConfirmDialog(panel,
                "Fulfill order #" + id + "?\nInventory will be deducted for each line product name.",
                "Confirm fulfill", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            status("Fulfilling order #" + id + " and updating inventory…");
            orderService.fulfill(id);
            refreshData();
            if (host != null) {
                host.refreshAll();
            }
            status("Order #" + id + " fulfilled — inventory adjusted; audit logged under CRM.");
        } catch (Exception ex) {
            error(ex.getMessage());
        }
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
        try {
            orderService.cancel(id);
            refreshData();
            if (host != null) {
                host.refreshAll();
            }
            status("Order #" + id + " cancelled.");
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    private void saveOrderNotes() {
        if (!GuiPermissions.requireWrite(host, panel, "update order notes")) {
            return;
        }
        Long id = selectedOrderId();
        if (id == null) {
            error("Select an order to update notes.");
            return;
        }
        try {
            orderService.updateNotes(id, orderNotes.getText());
            refreshData();
            status("Order #" + id + " notes saved.");
        } catch (Exception ex) {
            error(ex.getMessage());
        }
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
        int ok = JOptionPane.showConfirmDialog(panel,
                "Delete order #" + id + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            orderService.delete(id);
            refreshData();
            status("Order deleted.");
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    private void exportInvoicePdf() {
        Long id = selectedOrderId();
        if (id == null) {
            error("Select an order to print an invoice.");
            return;
        }
        try {
            byte[] pdf = orderService.generateInvoicePdf(id);
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("invoice-order-" + id + ".pdf"));
            if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File file = chooser.getSelectedFile();
            Files.write(file.toPath(), pdf);
            status("Invoice PDF → " + file.getName() + " (" + pdf.length + " bytes)");
        } catch (Exception ex) {
            error("Invoice PDF failed: " + ex.getMessage());
            status("Invoice PDF failed: " + ex.getMessage());
        }
    }

    private void exportOrdersCsv(boolean filteredOnly) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(filteredOnly ? "orders_filtered.csv" : "orders_export.csv"));
        chooser.setDialogTitle(filteredOnly ? "Export filtered orders" : "Export all orders");
        if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            status("Exporting orders…");
            if (filteredOnly) {
                orderService.exportToCsv(file.getAbsolutePath(), lastOrderView);
            } else {
                orderService.exportToCsv(file.getAbsolutePath());
            }
            status("Orders exported → " + file.getName()
                    + (filteredOnly ? " (filtered view)" : " (all)"));
            JOptionPane.showMessageDialog(panel,
                    "Exported to:\n" + file.getAbsolutePath(),
                    "Export complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            error(ex.getMessage());
            status("Order export failed: " + ex.getMessage());
        }
    }

    private static String nullToEmpty(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private void status(String msg) {
        if (host != null) {
            host.setStatus(msg);
        }
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(panel, msg, "CRM", JOptionPane.ERROR_MESSAGE);
    }

    private record CustomerItem(Long id, String name) {
        @Override public String toString() { return name + " (#" + id + ")"; }
    }
}
