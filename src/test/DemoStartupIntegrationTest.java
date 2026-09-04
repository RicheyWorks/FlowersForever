package com.flowerfarm.config;

import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SyncHistoryEntry;
import com.flowerfarm.service.SeasonPlanService;
import com.flowerfarm.service.SyncHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.headless=true",
                "spring.datasource.url=jdbc:h2:mem:demo-startup;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.flyway.baseline-on-migrate=false",
                "flowerfarm.inventory.seed-csv=target/no-demo-startup-seed.csv",
                "flowerfarm.irrigation.live-weather=false",
                "flowerfarm.demo.seed=true"
        })
@ActiveProfiles("demo")
@DisplayName("Demo desktop startup")
class DemoStartupIntegrationTest {

    @Autowired
    SeasonPlanService seasonPlans;

    @Autowired
    SyncHistoryService activity;

    @Test
    @DisplayName("seeds seasonal plans and records their full activity names")
    void seedsSeasonPlansWithoutTruncatingActivityOperations() {
        List<SeasonPlanEntry> plans = seasonPlans.listForSeason(LocalDate.now().getYear());
        List<SyncHistoryEntry> recent = activity.recent(100);

        assertThat(plans).hasSize(4);
        assertThat(recent).extracting(SyncHistoryEntry::getOperation)
                .contains("SEASON_PLAN_CREATE");
        assertThat(recent).filteredOn(entry -> "SEASON_PLAN_CREATE".equals(
                        entry.getOperation()))
                .hasSize(4);
    }
}
