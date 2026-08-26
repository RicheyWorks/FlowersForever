package com.flowerfarm.cli;

import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.SyncHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Extracted CLI report command handlers")
class CliReportCommandsTest {

    @Test
    @DisplayName("price list handler preserves defaults and skips export when declined")
    void buildsDefaultPriceListWithoutExport() {
        InventoryService inventory = mock(InventoryService.class);
        InventoryService.PriceListReport report = new InventoryService.PriceListReport(
                LocalDate.of(2026, 8, 23), 0, 0, 0, 0, true, List.of(), "Price list");
        when(inventory.buildPriceListReport(true)).thenReturn(report);
        CliReportCommands commands = commands(inventory, mock(OrderService.class));

        commands.priceListPdf(new Scanner("\nn\n"));

        verify(inventory).buildPriceListReport(true);
        verify(inventory, never()).generatePriceListPdf(report);
    }

    @Test
    @DisplayName("invoice handler rejects nonnumeric ids before invoking the service")
    void rejectsInvalidInvoiceId() {
        OrderService orders = mock(OrderService.class);
        CliReportCommands commands = commands(mock(InventoryService.class), orders);

        commands.orderInvoice(new Scanner("not-an-id\n"));

        verify(orders, never()).formatInvoiceText(org.mockito.ArgumentMatchers.anyLong());
    }

    private static CliReportCommands commands(InventoryService inventory, OrderService orders) {
        return new CliReportCommands(inventory, mock(HarvestService.class),
                mock(MorningBriefingService.class), mock(DayCloseoutService.class), orders,
                mock(CustomerStatementService.class), mock(SyncHistoryService.class));
    }
}
