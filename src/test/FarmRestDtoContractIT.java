package com.flowerfarm.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.controller.*;
import com.flowerfarm.controller.dto.FarmApiDtos.*;
import com.flowerfarm.config.SecurityConfig;
import com.flowerfarm.model.*;
import com.flowerfarm.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP contracts with the real default-open security filter chain and mocked storage services. */
@WebMvcTest(controllers = {InventoryController.class, CustomerController.class, HarvestController.class,
        OrderController.class, ConnectorController.class}, properties = "flowerfarm.auth.enabled=false")
@Import(SecurityConfig.class)
@AutoConfigureMockMvc
class FarmRestDtoContractIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean InventoryService inventory;
    @MockBean CustomerService customers;
    @MockBean CustomerStatementService statements;
    @MockBean HarvestService harvest;
    @MockBean OrderService orders;
    @MockBean ConnectorRegistry connectors;
    @MockBean SyncHistoryService history;
    @MockBean ServerCsvExportService serverCsvExport;

    private static Item item() {
        return new Item(11L, "Rose", "Flowers", 4.25, "stem", 1.5, 8, "Cold room");
    }

    private static Customer customer() {
        return new Customer(12L, "Market", "Ada", "ada@example.test", "555-0100", "MARKET", "Saturday");
    }

    private static HarvestEntry entry() {
        return new HarvestEntry(13L, LocalDate.of(2026, 7, 10), "Rose", 20, "stems", "Bed A", "Morning");
    }

    private static CustomerOrder order() {
        CustomerOrder order = new CustomerOrder(customer(), LocalDate.of(2026, 7, 11), "CONFIRMED", "Pickup");
        order.setId(14L);
        OrderLine line = new OrderLine("Rose", 3, "bunch", 12);
        line.setId(15L);
        order.addLine(line);
        return order;
    }

    private JsonNode json(String content) throws Exception {
        return mapper.readTree(content);
    }

    private void assertGet(String path, Object expected) throws Exception {
        String response = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(json(mapper.writeValueAsString(expected)), json(response), path);
    }

    @Test
    void allResponseDtosPreserveExistingEntityJsonIncludingNestedDatesAndNullableHistoryFields() {
        Item item = item();
        Customer customer = customer();
        HarvestEntry entry = entry();
        CustomerOrder order = order();
        SyncHistoryEntry audit = new SyncHistoryEntry("csv", "IMPORT", true, "Imported", "detail", null);
        assertEquals(mapper.valueToTree(item), mapper.valueToTree(ItemResponse.from(item)));
        assertEquals(mapper.valueToTree(customer), mapper.valueToTree(CustomerResponse.from(customer)));
        assertEquals(mapper.valueToTree(entry), mapper.valueToTree(HarvestResponse.from(entry)));
        assertEquals(mapper.valueToTree(order.getLines().get(0)), mapper.valueToTree(OrderLineResponse.from(order.getLines().get(0))));
        assertEquals(mapper.valueToTree(order), mapper.valueToTree(OrderResponse.from(order)));
        assertEquals(mapper.valueToTree(audit), mapper.valueToTree(HistoryResponse.from(audit)));
        assertFalse(mapper.valueToTree(OrderResponse.from(order)).path("lines").get(0).has("order"));
        assertEquals("2026-07-11", mapper.valueToTree(OrderResponse.from(order)).path("orderDate").asText());
    }

    @Test
    void everyListFamilyRetainsItsExistingWireShape() throws Exception {
        when(inventory.getAllItems()).thenReturn(List.of(item()));
        when(customers.getAll()).thenReturn(List.of(customer()));
        when(harvest.getAll()).thenReturn(List.of(entry()));
        when(orders.getAll()).thenReturn(List.of(order()));
        SyncHistoryEntry audit = new SyncHistoryEntry("csv", "IMPORT", true, "Imported", "details", 2);
        when(history.recentForConnector(null, 100)).thenReturn(List.of(audit));
        assertGet("/api/inventory", List.of(item()));
        assertGet("/api/customers", List.of(customer()));
        assertGet("/api/harvest", List.of(entry()));
        assertGet("/api/orders", List.of(order()));
        assertGet("/api/connectors/history", List.of(audit));
    }

    @Test
    void lookupSearchRangeAndFilterBranchesAlsoProjectResponses() throws Exception {
        when(inventory.findById(11L)).thenReturn(Optional.of(item()));
        when(inventory.searchItems("Rose")).thenReturn(List.of(item()));
        when(customers.findById(12L)).thenReturn(Optional.of(customer()));
        when(customers.search("Market")).thenReturn(List.of(customer()));
        when(harvest.findById(13L)).thenReturn(Optional.of(entry()));
        when(harvest.searchByCrop("Rose")).thenReturn(List.of(entry()));
        when(harvest.findBetween(any(), any())).thenReturn(List.of(entry()));
        when(harvest.filter(any(), any(), any(), any(), any())).thenReturn(List.of(entry()));
        when(orders.findById(14L)).thenReturn(Optional.of(order()));
        when(orders.findByCustomer(12L)).thenReturn(List.of(order()));
        when(orders.findBetween(any(), any())).thenReturn(List.of(order()));
        when(orders.filter(any(), any(), any(), any())).thenReturn(List.of(order()));
        SyncHistoryEntry audit = new SyncHistoryEntry("csv", "IMPORT", false, "Failed", "details", null);
        when(history.filter(eq("csv"), any(), any(), any(), eq(100))).thenReturn(List.of(audit));
        assertGet("/api/inventory/by-id/11", item());
        assertGet("/api/inventory/search?q=Rose", List.of(item()));
        assertGet("/api/customers/12", customer());
        assertGet("/api/customers/search?q=Market", List.of(customer()));
        assertGet("/api/harvest/13", entry());
        assertGet("/api/harvest/search?crop=Rose", List.of(entry()));
        assertGet("/api/harvest/range?from=2026-07-01&to=2026-07-31", List.of(entry()));
        assertGet("/api/harvest/filter?crop=Rose", List.of(entry()));
        assertGet("/api/orders/14", order());
        assertGet("/api/orders/by-customer/12", List.of(order()));
        assertGet("/api/orders/range?from=2026-07-01&to=2026-07-31", List.of(order()));
        assertGet("/api/orders/filter?status=CONFIRMED", List.of(order()));
        assertGet("/api/connectors/history?connector=csv", List.of(audit));
    }

    /** Extra persistence getters must never silently become API fields. */
    public static final class ExtendedItem extends Item {
        ExtendedItem() { super("Rose", "Flowers", 4.25, "stem", 1.5, 8, "Cold room"); }
        public String getInternalMemo() { return "not an API field"; }
    }

    @Test
    void newPersistenceGetterIsExcludedFromInventoryAndNestedConnectorImport() throws Exception {
        ExtendedItem expanded = new ExtendedItem();
        assertTrue(mapper.valueToTree(expanded).has("internalMemo"));
        when(inventory.getAllItems()).thenReturn(List.of(expanded));
        when(connectors.runImport("csv")).thenReturn(ConnectorResult.ok(List.of(expanded), "Imported"));
        mvc.perform(get("/api/inventory"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].internalMemo").doesNotExist())
                .andExpect(jsonPath("$[0].cost").value(1.5));
        mvc.perform(post("/api/connectors/csv/import"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.items[0].internalMemo").doesNotExist())
                .andExpect(jsonPath("$.items[0].name").value("Rose"));
    }

    @Test
    void inventoryWritesIgnoreBodyIdentityAndKeepAllBusinessFieldsAndPathTargets() throws Exception {
        String body = """
                {"id":999,"name":" Rose ","category":" Flowers ","price":4.25,"unit":"stem",
                 "cost":1.5,"quantity":8,"notes":" Cold room ","internalMemo":"injected"}
                """;
        when(inventory.addItem(any())).thenAnswer(call -> {
            Item value = call.getArgument(0);
            assertNull(value.getId(), "request identity must not reach the mutation service");
            value.setId(11L);
            return value;
        });
        when(inventory.updateById(eq(11L), any())).thenReturn(item());
        when(inventory.editItem(eq(0), any())).thenReturn(item());
        mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(11));
        mvc.perform(put("/api/inventory/by-id/11").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(11));
        mvc.perform(put("/api/inventory/0").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        ArgumentCaptor<Item> created = ArgumentCaptor.forClass(Item.class);
        verify(inventory).addItem(created.capture());
        assertEquals(mapper.valueToTree(item()), mapper.valueToTree(created.getValue()));
        ArgumentCaptor<Item> updated = ArgumentCaptor.forClass(Item.class);
        verify(inventory).updateById(eq(11L), updated.capture());
        assertNull(updated.getValue().getId());
        assertEquals("Rose", updated.getValue().getName());
        assertEquals(1.5, updated.getValue().getCost());
        verify(inventory).editItem(eq(0), argThat(value -> value.getId() == null && value.getQuantity() == 8));
    }

    @Test
    void customerWritesIgnoreIdentityAndPreserveContactAndTypeFields() throws Exception {
        String body = """
                {"id":999,"name":" Market ","contactName":"Ada","email":"ada@example.test",
                 "phone":"555-0100","customerType":"market","notes":"Saturday"}
                """;
        when(customers.add(any())).thenReturn(customer());
        when(customers.update(eq(12L), any())).thenReturn(customer());
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("ada@example.test"));
        mvc.perform(put("/api/customers/12").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(12));
        ArgumentCaptor<Customer> incoming = ArgumentCaptor.forClass(Customer.class);
        verify(customers).add(incoming.capture());
        Customer value = incoming.getValue();
        assertNull(value.getId());
        assertEquals("MARKET", value.getCustomerType());
        value.setId(12L);
        assertEquals(mapper.valueToTree(customer()), mapper.valueToTree(value));
        verify(customers).update(eq(12L), argThat(c -> c.getId() == null && c.getPhone().equals("555-0100")));
    }

    @Test
    void harvestWritesKeepDateBedNotesAndBatchNullAndZeroRowsWithoutAcceptingIds() throws Exception {
        String body = """
                {"id":999,"harvestDate":"2026-07-10","cropName":"Rose","quantity":20,
                 "unit":"stems","bedOrField":"Bed A","notes":"Morning"}
                """;
        when(harvest.add(any())).thenReturn(entry());
        when(harvest.update(eq(13L), any())).thenReturn(entry());
        when(harvest.addBatch(any())).thenReturn(List.of(entry()));
        mvc.perform(post("/api/harvest").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.harvestDate").value("2026-07-10"));
        mvc.perform(put("/api/harvest/13").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(13));
        mvc.perform(post("/api/harvest/batch").contentType(MediaType.APPLICATION_JSON)
                .content("[null," + body + "," + body.replace("\"quantity\":20", "\"quantity\":0") + "]"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].id").value(13));
        verify(harvest).add(argThat(e -> e.getId() == null && e.getBedOrField().equals("Bed A") && e.getQuantity() == 20));
        verify(harvest).update(eq(13L), argThat(e -> e.getId() == null && e.getNotes().equals("Morning")));
        ArgumentCaptor<List<HarvestEntry>> batch = ArgumentCaptor.forClass(List.class);
        verify(harvest).addBatch(batch.capture());
        assertEquals(3, batch.getValue().size());
        assertNull(batch.getValue().get(0));
        assertNull(batch.getValue().get(1).getId());
        assertEquals(0, batch.getValue().get(2).getQuantity());
    }

    @Test
    void orderCreateAndAddLineIgnoreNestedIdentityAndBackReferenceWithoutChangingResponse() throws Exception {
        String line = """
                {"id":999,"order":{"id":888},"productName":"Rose","quantity":3,"unit":"bunch","unitPrice":12}
                """;
        when(orders.create(eq(12L), eq(LocalDate.of(2026, 7, 11)), eq("CONFIRMED"), eq("Pickup"), any()))
                .thenReturn(order());
        when(orders.addLine(eq(14L), any())).thenReturn(order());
        String created = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":12,\"orderDate\":\"2026-07-11\",\"status\":\"CONFIRMED\",\"notes\":\"Pickup\",\"lines\":[" + line + "]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertEquals(json(mapper.writeValueAsString(order())), json(created));
        mvc.perform(post("/api/orders/14/lines").contentType(MediaType.APPLICATION_JSON).content(line))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].order").doesNotExist());
        ArgumentCaptor<List<OrderLine>> lines = ArgumentCaptor.forClass(List.class);
        verify(orders).create(eq(12L), any(), eq("CONFIRMED"), eq("Pickup"), lines.capture());
        OrderLine supplied = lines.getValue().get(0);
        assertNull(supplied.getId());
        assertNull(supplied.getOrder());
        assertEquals(12, supplied.getUnitPrice());
        verify(orders).addLine(eq(14L), argThat(l -> l.getId() == null && l.getOrder() == null && l.getQuantity() == 3));
    }

    @Test
    void orderMutationResponsesRemainNestedOrders() throws Exception {
        when(orders.confirm(14L)).thenReturn(order());
        when(orders.cancel(14L)).thenReturn(order());
        when(orders.fulfill(14L)).thenReturn(order());
        when(orders.updateNotes(14L, "Pickup")).thenReturn(order());
        when(orders.updateStatus(14L, "CONFIRMED")).thenReturn(order());
        for (String action : List.of("confirm", "cancel", "fulfill")) {
            String response = mvc.perform(post("/api/orders/14/" + action)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertEquals(json(mapper.writeValueAsString(order())), json(response));
        }
        for (Map.Entry<String, String> field : Map.of("notes", "Pickup", "status", "CONFIRMED").entrySet()) {
            String response = mvc.perform(patch("/api/orders/14/" + field.getKey()).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of(field.getKey(), field.getValue()))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertEquals(json(mapper.writeValueAsString(order())), json(response));
        }
    }

    @Test
    void minimalBusinessRequestsRetainExistingConstructorDefaults() throws Exception {
        when(inventory.addItem(any())).thenAnswer(call -> call.getArgument(0));
        when(customers.add(any())).thenAnswer(call -> call.getArgument(0));
        when(harvest.add(any())).thenAnswer(call -> call.getArgument(0));
        when(orders.addLine(eq(14L), any())).thenReturn(order());
        mvc.perform(post("/api/inventory").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" Rose \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Rose"))
                .andExpect(jsonPath("$.category").value("Other")).andExpect(jsonPath("$.unit").value("Per Unit"))
                .andExpect(jsonPath("$.cost").value(0)).andExpect(jsonPath("$.notes").value(""));
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" Market \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Market"))
                .andExpect(jsonPath("$.customerType").value("OTHER")).andExpect(jsonPath("$.email").value(""));
        mvc.perform(post("/api/harvest").contentType(MediaType.APPLICATION_JSON)
                .content("{\"harvestDate\":\"2026-07-10\",\"cropName\":\" Rose \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.cropName").value("Rose"))
                .andExpect(jsonPath("$.unit").value("stems")).andExpect(jsonPath("$.bedOrField").value(""));
        mvc.perform(post("/api/orders/14/lines").contentType(MediaType.APPLICATION_JSON).content("{\"productName\":\" Rose \"}"))
                .andExpect(status().isOk());
        verify(orders).addLine(eq(14L), argThat(line -> line.getProductName().equals("Rose")
                && line.getUnit().equals("bunch") && line.getQuantity() == 0 && line.getUnitPrice() == 0));
    }

    @Test
    void orderCreateKeepsOptionalDateAndAbsentNullOrEmptyLineLists() throws Exception {
        when(orders.create(eq(12L), any(), any(), any(), any())).thenReturn(order());
        LocalDate before = LocalDate.now();
        for (String body : List.of("{\"customerId\":12}", "{\"customerId\":12,\"orderDate\":null,\"lines\":null}",
                "{\"customerId\":12,\"lines\":[]}")) {
            mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }
        LocalDate after = LocalDate.now();
        ArgumentCaptor<LocalDate> dates = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<List<OrderLine>> lines = ArgumentCaptor.forClass(List.class);
        verify(orders, times(3)).create(eq(12L), dates.capture(), isNull(), isNull(), lines.capture());
        dates.getAllValues().forEach(date -> assertTrue(!date.isBefore(before) && !date.isAfter(after)));
        assertNull(lines.getAllValues().get(0));
        assertNull(lines.getAllValues().get(1));
        assertTrue(lines.getAllValues().get(2).isEmpty());
    }

    @Test
    void invalidBusinessValuesStillReturnBadRequestBeforeReachingServices() throws Exception {
        Map<String, String> badBodies = Map.of(
                "/api/inventory", "{\"name\":\"Rose\",\"price\":-1}",
                "/api/customers", "{\"name\":\" \"}",
                "/api/harvest", "{\"cropName\":\"Rose\",\"quantity\":1}",
                "/api/orders/14/lines", "{\"productName\":\"Rose\",\"quantity\":-1}");
        for (Map.Entry<String, String> body : badBodies.entrySet()) {
            mvc.perform(post(body.getKey()).contentType(MediaType.APPLICATION_JSON).content(body.getValue()))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(inventory, customers, harvest, orders);
    }

    @Test
    void missingRowsRemainNotFoundAndDefaultOpenDeleteRemainsAllowed() throws Exception {
        for (String path : List.of("/api/inventory/by-id/999", "/api/customers/999", "/api/harvest/999", "/api/orders/999")) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
        mvc.perform(delete("/api/inventory/by-id/11")).andExpect(status().isNoContent());
        verify(inventory).deleteById(11L);
    }
}
