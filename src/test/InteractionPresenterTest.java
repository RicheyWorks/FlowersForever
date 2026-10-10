package com.flowerfarm.gui.presenter;

import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Extracted desktop interaction presenters")
class InteractionPresenterTest {

    @Test
    @DisplayName("CRM presenter projects customers without exposing Swing")
    void projectsCustomers() {
        CustomerService customers = mock(CustomerService.class);
        Customer customer = new Customer("Kitsap Florist", "Ana", "ana@example.com",
                "555-0100", "FLORIST", "Weekly");
        customer.setId(7L);
        when(customers.search("kitsap")).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        CrmPresenter presenter = new CrmPresenter(customers, mock(OrderService.class), null);

        CrmPresenter.CustomerRow row = presenter.searchCustomers("kitsap").get(0);

        assertThat(row.id()).isEqualTo(7L);
        assertThat(row.cells()).containsExactly(7L, "Kitsap Florist", "Ana", "555-0100",
                "ana@example.com", "Florist", "Weekly");
        assertThat(presenter.customerChoices()).singleElement().satisfies(choice -> {
            assertThat(choice.id()).isEqualTo(7L);
            assertThat(choice.name()).isEqualTo("Kitsap Florist");
            assertThat(choice.contact()).isEqualTo("Ana");
            assertThat(choice.phone()).isEqualTo("555-0100");
            assertThat(choice.email()).isEqualTo("ana@example.com");
            assertThat(choice.type()).isEqualTo("FLORIST");
            assertThat(choice.notes()).isEqualTo("Weekly");
            assertThat(choice.toString()).isEqualTo("Kitsap Florist");
        });
    }

    @Test
    @DisplayName("CRM presenter validates range and summarizes filtered orders")
    void summarizesOrders() {
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer("Market", "", "", "", "MARKET", "");
        CustomerOrder order = new CustomerOrder(customer, LocalDate.of(2026, 8, 20),
                "CONFIRMED", "Pickup");
        order.setId(42L);
        order.setHandoffType("MARKET");
        order.addLine(new OrderLine("Dahlia", 3, "bunch", 12.5));
        when(orders.filter("ALL", "", LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31))).thenReturn(List.of(order));
        CrmPresenter presenter = new CrmPresenter(mock(CustomerService.class), orders, null);

        CrmPresenter.OrderView view = presenter.filterOrders(
                "ALL", "", "2026-08-01", "2026-08-31");

        assertThat(view.total()).isEqualTo(37.5);
        assertThat(view.pipelineCount()).isEqualTo(1);
        assertThat(view.rows()).singleElement().extracting(CrmPresenter.OrderRow::id)
                .isEqualTo(42L);
        assertThat(view.rows().get(0).handoff()).isEqualTo("Market");
        assertThat(view.rows().get(0).cells()).containsExactly(
                42L, LocalDate.of(2026, 8, 20), "Market", "Confirmed", "Market",
                1, "37.50", "Pickup");
        assertThat(presenter.filterOrders(
                "ALL", "", "2026-08-01", "2026-08-31", "MARKET").rows())
                .hasSize(1);
        assertThat(presenter.filterOrders(
                "ALL", "", "2026-08-01", "2026-08-31", "PICKUP").rows())
                .isEmpty();
        assertThatThrownBy(() -> presenter.filterOrders(
                "ALL", "", "2026-08-31", "2026-08-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("on or after");
    }

    @Test
    @DisplayName("CRM presenter delegates mutation workflows")
    void delegatesCrmMutations() {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        CrmPresenter presenter = new CrmPresenter(customers, orders, null);
        CrmPresenter.CustomerDraft customer = new CrmPresenter.CustomerDraft(
                "Studio", "Lee", "", "", "WHOLESALE", "");

        presenter.addCustomer(customer);
        presenter.confirmOrder(9L);
        presenter.updateOrderNotes(9L, "Dock pickup");
        presenter.updateOrderDate(9L, "2026-09-12");
        presenter.updateOrderHandoffType(9L, "Delivery");
        presenter.updateOrderHandoffType(9L, "Pickup", "Market");
        presenter.addOrderLine(9L, new CrmPresenter.LineDraft(
                "Nootka Rose", "2", "stems", "4.50"));
        OrderService.OrderReviewSnapshot reviewed = new OrderService.OrderReviewSnapshot(
                9L, 3L, "Studio", "Lee", "", "", "DRAFT",
                LocalDate.of(2026, 9, 12), "Dock pickup", List.of());
        presenter.addOrderLine(9L, reviewed, new CrmPresenter.LineDraft(
                "Dahlia Mix", "4", "bunches", "12.00"));
        presenter.updateOrderLine(9L, 12L, new CrmPresenter.LineDraft(
                "Nootka Rose", "3", "stems", "5.00"));

        verify(customers).add(org.mockito.ArgumentMatchers.any(Customer.class));
        verify(orders).confirm(9L);
        verify(orders).updateNotes(9L, "Dock pickup");
        verify(orders).updateOrderDate(9L, LocalDate.of(2026, 9, 12));
        verify(orders).updateHandoffType(9L, "DELIVERY");
        verify(orders).updateHandoffTypeReviewed(9L, "PICKUP", "MARKET");
        verify(orders).addLine(org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.any(com.flowerfarm.model.OrderLine.class));
        verify(orders).addLineReviewed(eq(9L), eq(reviewed),
                argThat(line -> line.getProductName().equals("Dahlia Mix")
                        && line.getQuantity() == 4 && line.getUnitPrice() == 12));
        verify(orders).updateLine(org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.argThat(line -> line.getQuantity() == 3
                        && line.getUnitPrice() == 5.0));
        assertThat(presenter.statementsAvailable()).isFalse();

        assertThatThrownBy(() -> presenter.addOrderLine(9L,
                new CrmPresenter.LineDraft("", "2", "stems", "4.50")))
                .isInstanceOf(CrmPresenter.InputValidationException.class)
                .satisfies(exception -> assertThat(
                        ((CrmPresenter.InputValidationException) exception).field())
                        .isEqualTo("product"));
        assertThatThrownBy(() -> presenter.validateOrderLine(
                new CrmPresenter.LineDraft("Dahlia Mix", "twelve", "", "free")))
                .isInstanceOfSatisfying(CrmPresenter.InputValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("quantity"));
        assertThatThrownBy(() -> presenter.addCustomer(new CrmPresenter.CustomerDraft(
                "", "", "", "", "Wholesale", "")))
                .isInstanceOfSatisfying(CrmPresenter.InputValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("customerName"));
        assertThatThrownBy(() -> presenter.addCustomer(new CrmPresenter.CustomerDraft(
                "Kitsap Blooms", "", "not-an-email", "", "Wholesale", "")))
                .isInstanceOfSatisfying(CrmPresenter.InputValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("customerEmail"));
        assertThat(presenter.statementRange("", "", LocalDate.of(2026, 8, 23)))
                .isEqualTo(new CrmPresenter.StatementRange(
                        LocalDate.of(2026, 5, 25), LocalDate.of(2026, 8, 23)));
        assertThatThrownBy(() -> presenter.statementRange(
                "2026-08-24", "2026-08-23", LocalDate.of(2026, 8, 23)))
                .isInstanceOfSatisfying(CrmPresenter.InputValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("statementTo"));
    }

    @Test
    @DisplayName("CRM order line preview reports total, exact stock, and shortfall")
    void previewsOrderLineStock() {
        InventoryService inventory = mock(InventoryService.class);
        when(inventory.findByNameIgnoreCase("Dahlia Mix")).thenReturn(java.util.Optional.of(
                new com.flowerfarm.model.Item("Dahlia Mix", "Flowers/Plants",
                        3.5, "Per Stem", 1.0, 4, "")));
        when(inventory.getAllItems()).thenReturn(List.of(
                new com.flowerfarm.model.Item("Dahlia Mix", "Flowers/Plants",
                        3.5, "Per Stem", 1.0, 4, "")));
        OrderService orders = mock(OrderService.class);
        CrmPresenter presenter = new CrmPresenter(
                mock(CustomerService.class), orders, null, inventory);

        CrmPresenter.LinePreview available = presenter.previewLine(
                new CrmPresenter.LineDraft("Dahlia Mix", "3", "stems", "3.50"));
        CrmPresenter.LinePreview shortfall = presenter.previewLine(
                new CrmPresenter.LineDraft("Dahlia Mix", "6", "stems", "3.50"));
        CrmPresenter.LinePreview missing = presenter.previewLine(
                new CrmPresenter.LineDraft("Unknown", "2", "stems", "5.00"));
        CrmPresenter.LinePreview complimentary = presenter.previewLine(
                new CrmPresenter.LineDraft("Dahlia Mix", "1", "stems", "0"));
        CrmPresenter.LinePreview subUnit = presenter.previewLine(
                new CrmPresenter.LineDraft("Dahlia Mix", "0.4", "stems", "3.50"));
        CrmPresenter.LinePreview wrongUnit = presenter.previewLine(
                new CrmPresenter.LineDraft("Dahlia Mix", "1", "bunches", "3.50"));
        CrmPresenter.LinePreview oversizedPrice = presenter.previewLine(
                new CrmPresenter.LineDraft("Dahlia Mix", "1", "stems", "1000000001"));
        OrderLine saved = new OrderLine("dahlia mix", 3, "stems", 3.5);
        saved.setId(10L);
        CrmPresenter.LinePreview cumulative = presenter.previewLine(
                new CrmPresenter.LineDraft("Dahlia Mix", "3", "stems", "3.50"),
                presenter.inventoryChoices(), List.of(saved), null);
        CrmPresenter.LinePreview replacement = presenter.previewLine(
                new CrmPresenter.LineDraft("Dahlia Mix", "4", "stems", "3.50"),
                presenter.inventoryChoices(), List.of(saved), 10L);

        assertThat(available.valid()).isTrue();
        assertThat(available.enoughStock()).isTrue();
        assertThat(available.lineTotal()).isEqualTo(10.5);
        assertThat(available.message()).contains("$10.50", "4 on hand");
        assertThat(shortfall.enoughStock()).isFalse();
        assertThat(shortfall.message()).contains("Short by 2");
        assertThat(missing.inventoryMatched()).isFalse();
        assertThat(missing.message()).contains("No exact inventory match");
        assertThat(complimentary.valid()).isTrue();
        assertThat(complimentary.lineTotal()).isZero();
        assertThat(complimentary.message()).contains("$0.00", "4 on hand");
        assertThat(subUnit.valid()).isFalse();
        assertThat(subUnit.message()).contains("counted products require whole quantities");
        assertThat(wrongUnit.valid()).isFalse();
        assertThat(wrongUnit.message()).contains("Unit must match Inventory", "Per Stem");
        assertThat(oversizedPrice.valid()).isFalse();
        assertThat(oversizedPrice.message()).contains("Unit price", "1000000000");
        assertThat(cumulative.enoughStock()).isFalse();
        assertThat(cumulative.duplicateSavedLines()).isEqualTo(1);
        assertThat(cumulative.message()).contains(
                "Already saved on this order in 1 line", "Short by 2",
                "3 already saved + 3 entered = 6 required");
        assertThat(replacement.enoughStock()).isTrue();
        assertThat(replacement.duplicateSavedLines()).isZero();
        assertThat(replacement.message()).contains("4 on hand")
                .doesNotContain("Already saved on this order", "Short by");
        presenter.createOrder(new CrmPresenter.OrderDraft(
                7L, "2026-09-12", "Dahlia Mix", "1", "stems", "0", "DRAFT", "sample"));
        verify(orders).create(eq(7L), eq(LocalDate.of(2026, 9, 12)),
                eq("DRAFT"), eq("sample"), eq("PICKUP"),
                argThat(lines -> lines.size() == 1 && lines.get(0).getUnitPrice() == 0));
        assertThatThrownBy(() -> presenter.createOrder(new CrmPresenter.OrderDraft(
                7L, "September 12", "Dahlia Mix", "1", "stems", "4.50",
                "DRAFT", "pickup")))
                .isInstanceOfSatisfying(CrmPresenter.InputValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("orderDate"))
                .hasMessageContaining("YYYY-MM-DD");
        assertThatThrownBy(() -> presenter.createOrder(null))
                .isInstanceOfSatisfying(CrmPresenter.InputValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("orderForm"))
                .hasMessageContaining("order details");
        assertThat(presenter.inventoryChoices()).singleElement()
                .satisfies(choice -> assertThat(choice.toString())
                        .isEqualTo("Dahlia Mix — 4 on hand"));
    }

    @Test
    @DisplayName("CRM desktop creation always persists Draft before saved-order review")
    void desktopOrderCreationCannotBypassDraftReview() {
        OrderService orders = mock(OrderService.class);
        CrmPresenter presenter = new CrmPresenter(
                mock(CustomerService.class), orders, null);

        presenter.createOrder(new CrmPresenter.OrderDraft(
                7L, "2026-09-12", "Dahlia Mix", "2", "stems", "3.50",
                "CONFIRMED", "Call before delivery"));

        verify(orders).create(eq(7L), eq(LocalDate.of(2026, 9, 12)), eq("DRAFT"),
                eq("Call before delivery"), eq("PICKUP"),
                argThat(lines -> lines.size() == 1
                        && "Dahlia Mix".equals(lines.get(0).getProductName())));
    }

    @Test
    @DisplayName("CRM reviewed creation carries the exact duplicate decision to the service")
    void reviewedOrderCreationCarriesDuplicateDecision() {
        OrderService orders = mock(OrderService.class);
        CrmPresenter presenter = new CrmPresenter(
                mock(CustomerService.class), orders, null);
        CrmPresenter.OrderDraft draft = new CrmPresenter.OrderDraft(
                7L, "2026-09-12", "Dahlia Mix", "2", "stems", "3.50",
                "DRAFT", "Separate market load", "MARKET");
        Map<Long, OrderService.OrderReviewSnapshot> reviewed = Map.of(
                41L, mock(OrderService.OrderReviewSnapshot.class),
                42L, mock(OrderService.OrderReviewSnapshot.class));

        presenter.createOrder(draft, reviewed, true);

        verify(orders).createReviewed(eq(7L), eq(LocalDate.of(2026, 9, 12)),
                eq("DRAFT"), eq("Separate market load"), eq("MARKET"),
                argThat(lines -> lines.size() == 1), eq(reviewed), eq(true));
        assertThatThrownBy(() -> presenter.createOrder(null, reviewed, true))
                .isInstanceOfSatisfying(CrmPresenter.InputValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("orderForm"))
                .hasMessageContaining("order details");
    }

    @Test
    @DisplayName("harvest presenter parses batch defaults and form values")
    void parsesHarvestInput() {
        HarvestLogPresenter presenter = new HarvestLogPresenter(mock(HarvestService.class));

        List<HarvestEntry> batch = presenter.parseBatch(new HarvestLogPresenter.BatchDraft(
                "2026-08-23", "stems", "Bed A", "morning",
                "# comment\nNootka Rose,12\nDahlia,4,bunches,Bed B"));
        HarvestEntry single = presenter.fromForm(new HarvestLogPresenter.EntryDraft(
                "2026-08-22", " Cosmos ", "8", "stems", "North", "cut"));

        assertThat(batch).hasSize(2);
        assertThat(batch.get(0).getUnit()).isEqualTo("stems");
        assertThat(batch.get(0).getBedOrField()).isEqualTo("Bed A");
        assertThat(batch.get(1).getUnit()).isEqualTo("bunches");
        HarvestLogPresenter.BatchPreview preview = presenter.previewBatch(
                new HarvestLogPresenter.BatchDraft(
                        "2026-08-23", "stems", "Bed A", "morning",
                        "Nootka Rose,12\nDahlia,4,bunches,Bed B"));
        assertThat(preview.valid()).isTrue();
        assertThat(preview.rowCount()).isEqualTo(2);
        assertThat(preview.message()).contains("2 rows ready", "12 stems", "4 bunches");
        assertThat(presenter.previewBatch(new HarvestLogPresenter.BatchDraft(
                "2026-08-23", "stems", "", "", "Lavender,1.5,lbs")))
                .satisfies(decimal -> {
                    assertThat(decimal.valid()).isTrue();
                    assertThat(decimal.message()).contains(
                            "1 row ready", "1.5 lbs");
                });
        assertThat(presenter.previewBatch(new HarvestLogPresenter.BatchDraft(
                "2026-08-23", "stems", "", "", "Lavender,0.1,lbs")))
                .satisfies(tooSmall -> {
                    assertThat(tooSmall.valid()).isTrue();
                    assertThat(tooSmall.message()).contains(
                            "1 row ready", "0.1 lbs");
                });
        assertThat(presenter.previewBatch(new HarvestLogPresenter.BatchDraft(
                "2026-08-23", "stems", "", "", "Rose,4\nDahlia,nope")))
                .satisfies(invalid -> {
                    assertThat(invalid.valid()).isFalse();
                    assertThat(invalid.message()).contains("Line 2", "quantity must be a number");
                });
        assertThat(presenter.previewBatch(new HarvestLogPresenter.BatchDraft(
                "2026-08-23", "stems", "", "", "# comment only")))
                .satisfies(empty -> {
                    assertThat(empty.valid()).isFalse();
                    assertThat(empty.message()).contains("at least one harvest line");
                });
        assertThat(single.getCropName()).isEqualTo("Cosmos");
        assertThatThrownBy(() -> presenter.fromForm(new HarvestLogPresenter.EntryDraft(
                "2026-08-23", "", "1", "stems", "", "")))
                .isInstanceOfSatisfying(HarvestLogPresenter.InputValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("crop"));
        assertThatThrownBy(() -> presenter.fromForm(new HarvestLogPresenter.EntryDraft(
                "2026-08-23", "Dahlia", "0", "stems", "", "")))
                .isInstanceOfSatisfying(HarvestLogPresenter.InputValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("quantity"));
        assertThatThrownBy(() -> presenter.fromForm(new HarvestLogPresenter.EntryDraft(
                "2026-08-23", "Dahlia", "0.1", "stems", "", "")))
                .isInstanceOfSatisfying(HarvestLogPresenter.InputValidationException.class,
                        error -> {
                            assertThat(error.field()).isEqualTo("quantity");
                            assertThat(error.getMessage()).contains("whole number");
                        });
        String tomorrow = LocalDate.now().plusDays(1).toString();
        assertThatThrownBy(() -> presenter.fromForm(new HarvestLogPresenter.EntryDraft(
                tomorrow, "Dahlia", "4", "stems", "", "")))
                .isInstanceOfSatisfying(HarvestLogPresenter.InputValidationException.class,
                        error -> {
                            assertThat(error.field()).isEqualTo("date");
                            assertThat(error.getMessage()).contains("cannot be after today");
                        });
        assertThat(presenter.previewBatch(new HarvestLogPresenter.BatchDraft(
                tomorrow, "stems", "", "", "Dahlia,4")))
                .satisfies(future -> {
                    assertThat(future.valid()).isFalse();
                    assertThat(future.message()).contains("cannot be after today");
                });
    }

    @Test
    @DisplayName("harvest presenter projects filters and rejects reversed dates")
    void projectsHarvestFilter() {
        HarvestService service = mock(HarvestService.class);
        HarvestEntry entry = new HarvestEntry(LocalDate.of(2026, 8, 23),
                "Rose", 15, "stems", "A", "market");
        entry.setId(3L);
        when(service.filter("rose", "a", "market",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
                .thenReturn(List.of(entry));
        when(service.totalsByCrop()).thenReturn(Map.of("Rose", 15.0));
        HarvestLogPresenter presenter = new HarvestLogPresenter(service);

        HarvestLogPresenter.HarvestView view = presenter.filter(
                new HarvestLogPresenter.FilterDraft(
                        " rose ", " a ", " market ", "2026-08-01", "2026-08-31"));

        assertThat(view.quantity()).isEqualTo(15);
        assertThat(view.rows()).singleElement().extracting(HarvestLogPresenter.HarvestRow::id)
                .isEqualTo(3L);
        assertThatThrownBy(() -> presenter.filter(new HarvestLogPresenter.FilterDraft(
                "", "", "", "2026-08-31", "2026-08-01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("connector presenter normalizes import, export, sync, and failure feedback")
    void presentsConnectorFeedback() {
        ConnectorRegistry registry = mock(ConnectorRegistry.class);
        InventoryService inventory = mock(InventoryService.class);
        Item item = new Item("Rose", "Flower", 3, "stem", 1, 4, "");
        when(registry.runImport("csv")).thenReturn(
                ConnectorResult.ok(List.of(item), "ok", "csv"));
        when(registry.runExport("csv")).thenReturn(
                ConnectorResult.fail("offline", "try later", "csv"));
        when(registry.runSync("csv")).thenReturn(
                ConnectorResult.ok(new SyncSummary(1, 2, 0, 3, 0), "ok", "csv"));
        ConnectorActionPresenter presenter = new ConnectorActionPresenter(inventory, registry);

        ConnectorActionPresenter.Feedback imported = presenter.importFrom("csv", "CSV");
        ConnectorActionPresenter.Feedback failed = presenter.exportTo("csv", "CSV");
        ConnectorActionPresenter.Feedback synced = presenter.syncWith("csv", "CSV");
        presenter.exportInventory("inventory.csv");

        assertThat(imported.success()).isTrue();
        assertThat(imported.refreshRequired()).isTrue();
        assertThat(imported.message()).contains("Imported: 1");
        assertThat(failed.success()).isFalse();
        assertThat(failed.message()).contains("offline", "try later");
        assertThat(synced.status()).contains("created=1", "updated=2");
        verify(inventory).exportToCsv("inventory.csv");
    }
}
