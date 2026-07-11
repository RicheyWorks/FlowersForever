package com.flowerfarm.service;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.HarvestService.BedProduction;
import com.flowerfarm.service.HarvestService.BedProductionReport;
import com.flowerfarm.service.IrrigationAdvisorService.IrrigationAdvice;
import com.flowerfarm.service.IrrigationAdvisorService.Priority;
import com.flowerfarm.service.IrrigationAdvisorService.SeasonBand;
import com.flowerfarm.service.MarketDayPackingService.MarketDayPlan;
import com.flowerfarm.service.MarketDayPackingService.ProductNeed;
import com.flowerfarm.service.MorningBriefingService.MorningBriefing;
import com.flowerfarm.service.OrderService.WeekRevenueSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MorningBriefingService")
class MorningBriefingServiceTest {

    @Mock InventoryService inventoryService;
    @Mock HarvestService harvestService;
    @Mock OrderService orderService;
    @Mock MarketDayPackingService marketDayPackingService;
    @Mock IrrigationAdvisorService irrigationAdvisorService;

    MorningBriefingService service;

    @BeforeEach
    void setUp() {
        service = new MorningBriefingService(
                inventoryService, harvestService, orderService,
                marketDayPackingService, irrigationAdvisorService);
    }

    @Test
    @DisplayName("buildOffline aggregates pack, beds, water, stock into actions + PDF")
    void buildAndPdf() {
        LocalDate today = LocalDate.now();
        MarketDayPlan pack = new MarketDayPlan(
                today, today, today, "CONFIRMED", 1, 100.0,
                List.of(),
                List.of(new ProductNeed("Nootka Rose", "stems", 50, 10, true, 40)),
                1,
                "MARKET DAY"
        );
        when(marketDayPackingService.planForDay(any())).thenReturn(pack);

        BedProductionReport beds = new BedProductionReport(
                today.minusDays(6).toString(), today.toString(),
                1, 2, 80.0,
                List.of(new BedProduction("Bed A", 80.0, 2,
                        Map.of("Nootka Rose", 80.0), today.toString(), today.toString())),
                "BEDS"
        );
        when(harvestService.productionByBedLast7Days()).thenReturn(beds);
        when(harvestService.totalQuantityLast7Days()).thenReturn(80.0);

        IrrigationAdvice water = new IrrigationAdvice(
                "Port Orchard / Kitsap County, WA", "CLIMATOLOGY", today.toString(),
                SeasonBand.PEAK_DRY_SUMMER, Priority.HIGH, "Peak dry summer",
                List.of("Deep soak"), List.of(), null, null, null, null, null, "notes");
        when(irrigationAdvisorService.advise(false)).thenReturn(water);

        when(inventoryService.inventoryKpis(10)).thenReturn(
                new InventoryService.InventoryKpiSnapshot(4, 100, 50, 1, 60, 10));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Low SKU", "Other", 1, "each", 0.5, 3, ""),
                new Item("OK SKU", "Other", 1, "each", 0.5, 50, "")
        ));

        when(orderService.weekRevenueSummary()).thenReturn(
                new WeekRevenueSummary(today.minusDays(6), today, 200.0, 100.0, 0.0, 1, 1, 0));

        MorningBriefing briefing = service.buildOffline();
        assertThat(briefing.plainText()).contains("MORNING BRIEFING");
        assertThat(briefing.actionItems()).isNotEmpty();
        assertThat(briefing.lowStock()).extracting(MorningBriefingService.LowStockLine::name)
                .contains("Low SKU");
        assertThat(briefing.toMap()).containsKeys("actionItems", "marketDay", "irrigation");

        byte[] pdf = service.generatePdf(briefing);
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        assertThatIllegalArgumentException().isThrownBy(() -> service.generatePdf(null));
    }
}
