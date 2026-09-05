package com.flowerfarm.integration;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.main.headless=true",
        "spring.datasource.url=jdbc:h2:mem:report-downloads;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "flowerfarm.inventory.seed-csv=target/no-report-download-seed.csv",
        "flowerfarm.irrigation.live-weather=false",
        "flowerfarm.auth.enabled=false"
})
@AutoConfigureMockMvc
@DisplayName("Report download compatibility")
class ReportDownloadCompatibilityIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    HarvestService harvestService;

    @Autowired
    CustomerService customerService;

    @Autowired
    OrderService orderService;

    @Autowired
    FarmProfile farmProfile;

    @Test
    @DisplayName("real renderers preserve PDF routes, headers, and persisted aggregates")
    void actualPdfDownloads() throws Exception {
        LocalDate today = LocalDate.now(farmProfile.zoneId());
        HarvestEntry harvest = harvestService.add(new HarvestEntry(
                today, "Nootka Rose", 5, "stems", "Bed A", "download test"));
        Customer customer = customerService.add(new Customer(
                "Report Customer", "Sam", "sam@example.test", "", "FLORIST", ""));
        CustomerOrder order = orderService.create(
                customer.getId(), today, "CONFIRMED", "download test", "DELIVERY",
                List.of(new OrderLine("Nootka Rose", 1, "stems", 2.5)));

        assertPdf("/api/inventory/price-list/report.pdf?inStockOnly=true",
                "price-list-");
        assertPdf("/api/harvest/log/report.pdf?from=" + today + "&to=" + today,
                "harvest-log-");
        assertPdf("/api/orders/" + order.getId() + "/invoice.pdf",
                "invoice-order-" + order.getId() + ".pdf");

        assertThat(harvestService.findById(harvest.getId())).isPresent();
        assertThat(orderService.findById(order.getId()))
                .get()
                .extracting(CustomerOrder::getStatus)
                .isEqualTo("CONFIRMED");
        assertThat(customerService.findById(customer.getId())).isPresent();
    }

    private void assertPdf(String path, String filenameFragment) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString(filenameFragment)))
                .andExpect(result -> {
                    byte[] body = result.getResponse().getContentAsByteArray();
                    assertThat(body).hasSizeGreaterThan(100);
                    assertThat(new String(body, 0, 4)).isEqualTo("%PDF");
                });
    }
}
