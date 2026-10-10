package com.flowerfarm.service;

import com.flowerfarm.model.FarmProfile;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FarmReportClockTest {
    @Test void weeklyReportUsesFarmDateAcrossUtcMidnight() {
        FarmProfile profile = new FarmProfile(Clock.fixed(Instant.parse("2026-10-10T00:30:00Z"), ZoneOffset.UTC));
        profile.setTimeZone("America/Los_Angeles");
        HarvestService harvest = mock(HarvestService.class);
        OrderService orders = mock(OrderService.class);
        LocalDate farmToday = LocalDate.of(2026, 10, 9);
        LocalDate from = farmToday.minusDays(6);
        when(harvest.findBetween(from, farmToday)).thenReturn(List.of());
        when(orders.findBetween(from, farmToday)).thenReturn(List.of());
        when(harvest.productionByBed(from, farmToday)).thenReturn(new HarvestService.BedProductionReport(
                from.toString(), farmToday.toString(), 0, 0, 0, List.of(), ""));
        ReportService reports = new ReportService(harvest, orders, profile);
        assertThat(reports.today()).isEqualTo(farmToday);
        assertThat(reports.generateWeeklyReportPdf()).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        verify(harvest).findBetween(from, farmToday);
        verify(orders).findBetween(from, farmToday);
        profile.setTimeZone("Pacific/Kiritimati");
        assertThat(reports.today()).isEqualTo(LocalDate.of(2026, 10, 10));
    }
}
