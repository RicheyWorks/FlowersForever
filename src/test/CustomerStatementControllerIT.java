package com.flowerfarm.integration;

import com.flowerfarm.controller.CustomerController;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.CustomerStatementService.CustomerStatement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GET /api/customers/{id}/statement")
class CustomerStatementControllerIT {

    @Autowired MockMvc mockMvc;

    @MockBean CustomerService customerService;
    @MockBean CustomerStatementService customerStatementService;

    private CustomerStatement sample() {
        return new CustomerStatement(
                LocalDate.of(2026, 7, 12),
                LocalDate.of(2026, 4, 13),
                LocalDate.of(2026, 7, 12),
                3L, "Kitsap Blooms", "Sam", "sam@example.com", "360", "FLORIST",
                1, 50.0, 0, 0, 0, 50.0,
                List.of(new CustomerStatementService.StatementLine(
                        1L, LocalDate.of(2026, 7, 10), "FULFILLED", 1, 50.0, "")),
                "CUSTOMER STATEMENT"
        );
    }

    @Test
    @DisplayName("JSON statement")
    void json() throws Exception {
        when(customerStatementService.build(eq(3L), any(), any())).thenReturn(sample());
        mockMvc.perform(get("/api/customers/3/statement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Kitsap Blooms"))
                .andExpect(jsonPath("$.orderCount").value(1));
    }

    @Test
    @DisplayName("plain text")
    void text() throws Exception {
        when(customerStatementService.build(eq(3L), any(), any())).thenReturn(sample());
        mockMvc.perform(get("/api/customers/3/statement.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CUSTOMER STATEMENT")));
    }

    @Test
    @DisplayName("PDF")
    void pdf() throws Exception {
        when(customerStatementService.build(eq(3L), any(), any())).thenReturn(sample());
        when(customerStatementService.generatePdf(any())).thenReturn("%PDF-1.4".getBytes());
        mockMvc.perform(get("/api/customers/3/statement.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("statement-customer-3")))
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("application/pdf")));
    }

    @Test
    @DisplayName("missing customer → 404")
    void missing() throws Exception {
        when(customerStatementService.build(eq(99L), any(), any()))
                .thenThrow(new IndexOutOfBoundsException("No customer with id=99"));
        mockMvc.perform(get("/api/customers/99/statement"))
                .andExpect(status().isNotFound());
    }
}
