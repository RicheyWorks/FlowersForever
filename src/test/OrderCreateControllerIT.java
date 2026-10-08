package com.flowerfarm.integration;

import com.flowerfarm.controller.OrderController;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Order handoff API")
class OrderCreateControllerIT {

    @Autowired MockMvc mockMvc;

    @MockitoBean OrderService orderService;

    @Test
    @DisplayName("passes the structured handoff into order creation")
    void createsWithStructuredHandoff() throws Exception {
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder saved = new CustomerOrder(customer, LocalDate.of(2026, 9, 12),
                "CONFIRMED", "Saturday stall", "MARKET");
        saved.setId(42L);
        when(orderService.create(eq(7L), eq(LocalDate.of(2026, 9, 12)),
                eq("CONFIRMED"), eq("Saturday stall"), eq("MARKET"), anyList()))
                .thenReturn(saved);

        mockMvc.perform(post("/api/orders")
                        .contentType("application/json")
                        .content("""
                                {"customerId":7,"orderDate":"2026-09-12",
                                 "status":"CONFIRMED","notes":"Saturday stall",
                                 "handoffType":"MARKET","lines":[
                                   {"productName":"Dahlia Mix","quantity":2,
                                    "unit":"bunches","unitPrice":18.0}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.handoffType").value("MARKET"));

        verify(orderService).create(eq(7L), eq(LocalDate.of(2026, 9, 12)),
                eq("CONFIRMED"), eq("Saturday stall"), eq("MARKET"), anyList());
    }

    @Test
    @DisplayName("corrects an open order handoff before confirmation")
    void correctsStructuredHandoff() throws Exception {
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder saved = new CustomerOrder(customer, LocalDate.of(2026, 9, 12),
                "DRAFT", "Saturday stall", "DELIVERY");
        saved.setId(43L);
        when(orderService.updateHandoffType(43L, "DELIVERY")).thenReturn(saved);

        mockMvc.perform(patch("/api/orders/43/handoff")
                        .contentType("application/json")
                        .content("{\"handoffType\":\"DELIVERY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(43))
                .andExpect(jsonPath("$.handoffType").value("DELIVERY"));

        verify(orderService).updateHandoffType(43L, "DELIVERY");
    }

    @Test
    @DisplayName("filters the open handoff-review queue")
    void filtersHandoffReviewQueue() throws Exception {
        when(orderService.filter("OPEN", null, null, null, "UNSPECIFIED"))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/orders/filter")
                        .queryParam("status", "OPEN")
                        .queryParam("handoff", "UNSPECIFIED"))
                .andExpect(status().isOk());

        verify(orderService).filter("OPEN", null, null, null, "UNSPECIFIED");
    }
}
