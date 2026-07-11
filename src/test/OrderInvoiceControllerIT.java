package com.flowerfarm.integration;

import com.flowerfarm.controller.OrderController;
import com.flowerfarm.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GET /api/orders/{id}/invoice")
class OrderInvoiceControllerIT {

    @Autowired MockMvc mockMvc;

    @MockBean OrderService orderService;

    @Test
    @DisplayName("invoice PDF")
    void pdf() throws Exception {
        when(orderService.generateInvoicePdf(eq(7L))).thenReturn("%PDF-1.4".getBytes());
        mockMvc.perform(get("/api/orders/7/invoice.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("invoice-order-7")))
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("application/pdf")));
    }

    @Test
    @DisplayName("invoice plain text")
    void text() throws Exception {
        when(orderService.formatInvoiceText(eq(7L))).thenReturn("INVOICE / ORDER #7\n");
        mockMvc.perform(get("/api/orders/7/invoice.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("INVOICE / ORDER #7")));
    }

    @Test
    @DisplayName("missing order → 404")
    void missing() throws Exception {
        when(orderService.generateInvoicePdf(eq(99L)))
                .thenThrow(new IndexOutOfBoundsException("No order with id=99"));
        mockMvc.perform(get("/api/orders/99/invoice.pdf"))
                .andExpect(status().isNotFound());
    }
}
