package com.flowerfarm.integration;

import com.flowerfarm.auth.FarmUserDirectory;
import com.flowerfarm.config.SecurityConfig;
import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.controller.*;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Authorization through the real SecurityConfig; fixture principals do not test password login. */
@WebMvcTest(controllers = {InventoryController.class, CustomerController.class, HarvestController.class,
        OrderController.class, ConnectorController.class}, properties = "flowerfarm.auth.enabled=true")
@Import(SecurityConfig.class)
@AutoConfigureMockMvc
class FarmRestAuthorizationIT {
    @Autowired MockMvc mvc;
    @MockBean InventoryService inventory;
    @MockBean CustomerService customers;
    @MockBean CustomerStatementService statements;
    @MockBean HarvestService harvest;
    @MockBean OrderService orders;
    @MockBean ConnectorRegistry connectors;
    @MockBean SyncHistoryService history;
    @MockBean ServerCsvExportService serverCsvExport;
    @MockBean FarmUserDirectory directory;
    @MockBean UserDetailsService userDetailsService;

    private void assertNoBusinessCalls() {
        verifyNoInteractions(inventory, customers, statements, harvest, orders, connectors, history, serverCsvExport);
    }

    @Test
    void enabledModeRejectsAnonymousAccessToAllFiveFamilies() throws Exception {
        for (String path : List.of("/api/inventory", "/api/customers", "/api/harvest", "/api/orders", "/api/connectors/history")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        assertNoBusinessCalls();
    }

    @Test
    void everyFarmRoleCanReadButViewerCannotReachMutationServices() throws Exception {
        for (String role : List.of("VIEWER", "HAND", "OWNER")) {
            for (String path : List.of("/api/inventory", "/api/customers", "/api/harvest", "/api/orders", "/api/connectors/history")) {
                mvc.perform(get(path).with(user("fixture").roles(role))).andExpect(status().isOk());
            }
        }
        clearInvocations(inventory, customers, statements, harvest, orders, connectors, history, serverCsvExport);
        mvc.perform(post("/api/inventory").with(user("fixture").roles("VIEWER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rose\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/customers/12").with(user("fixture").roles("VIEWER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Market\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/harvest/13").with(user("fixture").roles("VIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/orders/14/status").with(user("fixture").roles("VIEWER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/connectors/csv/import").with(user("fixture").roles("VIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/harvest/export").with(user("fixture").roles("VIEWER")))
                .andExpect(status().isForbidden());
        assertNoBusinessCalls();
    }

    @Test
    void handCanWriteAllBusinessFamiliesButCannotClearHistory() throws Exception {
        when(inventory.addItem(any())).thenReturn(new Item("Rose", "Other", 0, "stem", 0, 0, ""));
        when(connectors.runExport("csv")).thenReturn(ConnectorResult.ok(1, "Exported"));
        mvc.perform(post("/api/inventory").with(user("fixture").roles("HAND"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rose\"}"))
                .andExpect(status().isCreated());
        for (String path : List.of("/api/customers/12", "/api/harvest/13", "/api/orders/14")) {
            mvc.perform(delete(path).with(user("fixture").roles("HAND"))).andExpect(status().isNoContent());
        }
        mvc.perform(post("/api/connectors/csv/export").with(user("fixture").roles("HAND")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.exported").value(1));
        mvc.perform(delete("/api/connectors/history").with(user("fixture").roles("HAND")))
                .andExpect(status().isForbidden());
        verify(inventory).addItem(any());
        verify(customers).delete(12L);
        verify(harvest).delete(13L);
        verify(orders).delete(14L);
        verify(connectors).runExport("csv");
        verify(history, never()).clearAll();
    }

    @Test
    void ownerCanClearHistory() throws Exception {
        mvc.perform(delete("/api/connectors/history").with(user("fixture").roles("OWNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Sync history cleared."));
        verify(history).clearAll();
    }
}
