package com.flowerfarm.service;

import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.service.IrrigationAdvisorService.IrrigationAdvice;
import com.flowerfarm.service.IrrigationAdvisorService.Priority;
import com.flowerfarm.service.IrrigationAdvisorService.SeasonBand;
import com.flowerfarm.service.IrrigationAdvisorService.WeekForecast;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IrrigationAdvisorService (Kitsap)")
class IrrigationAdvisorServiceTest {

    @Mock
    private HarvestService harvestService;

    @Test
    @DisplayName("season bands map months for maritime PNW")
    void seasonBands() {
        IrrigationAdvisorService svc = new IrrigationAdvisorService(
                harvestService, false, 47.54, -122.64, (lat, lon) -> Optional.empty());
        assertThat(svc.seasonFor(Month.JANUARY)).isEqualTo(SeasonBand.WET_WINTER);
        assertThat(svc.seasonFor(Month.JULY)).isEqualTo(SeasonBand.PEAK_DRY_SUMMER);
        assertThat(svc.seasonFor(Month.SEPTEMBER)).isEqualTo(SeasonBand.FALL_TRANSITION);
        assertThat(svc.seasonFor(Month.MAY)).isEqualTo(SeasonBand.LATE_SPRING);
    }

    @Test
    @DisplayName("climatology peak dry summer is HIGH priority")
    void climatologyPeakDry() {
        IrrigationAdvisorService svc = new IrrigationAdvisorService(
                harvestService, false, 47.54, -122.64, (lat, lon) -> Optional.empty());

        IrrigationAdvice advice = svc.buildAdvice(
                LocalDate.of(2026, 7, 15),
                SeasonBand.PEAK_DRY_SUMMER,
                List.of(),
                Optional.empty());

        assertThat(advice.mode()).isEqualTo("CLIMATOLOGY");
        assertThat(advice.priority()).isEqualTo(Priority.HIGH);
        assertThat(advice.headline()).containsIgnoringCase("dry");
        assertThat(advice.actions()).isNotEmpty();
        assertThat(advice.weekPrecipInches()).isNull();
    }

    @Test
    @DisplayName("live dry deficit raises HIGH and exposes forecast numbers")
    void liveDryDeficit() {
        when(harvestService.filter(isNull(), isNull(), isNull(), any(), any()))
                .thenReturn(List.of());
        WeekForecast dry = new WeekForecast(0.1, 1.4, 88, 55, "test");
        IrrigationAdvisorService svc = new IrrigationAdvisorService(
                harvestService, true, 47.54, -122.64, (lat, lon) -> Optional.of(dry));

        IrrigationAdvice advice = svc.advise(true);

        assertThat(advice.mode()).isEqualTo("LIVE");
        assertThat(advice.priority()).isEqualTo(Priority.HIGH);
        assertThat(advice.weekPrecipInches()).isEqualTo(0.1);
        assertThat(advice.weekEtInches()).isEqualTo(1.4);
        assertThat(advice.moistureDeficitInches()).isEqualTo(1.3);
        assertThat(advice.maxTempF()).isEqualTo(88.0);
    }

    @Test
    @DisplayName("wet winter live forecast can be NONE priority")
    void wetWinterLive() {
        WeekForecast wet = new WeekForecast(2.0, 0.3, 48, 38, "test");
        IrrigationAdvisorService svc = new IrrigationAdvisorService(
                harvestService, true, 47.54, -122.64, (lat, lon) -> Optional.of(wet));

        IrrigationAdvice advice = svc.buildAdvice(
                LocalDate.of(2026, 1, 10),
                SeasonBand.WET_WINTER,
                List.of("Tunnel 1"),
                Optional.of(wet));

        assertThat(advice.priority()).isEqualTo(Priority.NONE);
        assertThat(advice.headline()).containsIgnoringCase("wet");
        assertThat(advice.actions().stream().anyMatch(a -> a.contains("Tunnel 1"))).isTrue();
    }

    @Test
    @DisplayName("active beds from harvest filter are listed")
    void activeBedsFromHarvest() {
        when(harvestService.filter(isNull(), isNull(), isNull(), any(), any()))
                .thenReturn(List.of(
                        new HarvestEntry(LocalDate.now(), "Nootka Rose", 40, "stems", "Bed A", ""),
                        new HarvestEntry(LocalDate.now(), "Dahlia", 20, "stems", "Bed C", "")
                ));
        IrrigationAdvisorService svc = new IrrigationAdvisorService(
                harvestService, false, 47.54, -122.64, (lat, lon) -> Optional.empty());

        IrrigationAdvice advice = svc.adviseClimatology();
        assertThat(advice.activeBeds()).contains("Bed A", "Bed C");
    }

    @Test
    @DisplayName("Open-Meteo JSON parser sums daily precip and ET")
    void parseOpenMeteo() {
        String json = """
                {
                  "daily": {
                    "precipitation_sum": [0.1, 0.0, 0.2, 0.0, 0.0, 0.1, 0.0],
                    "et0_fao_evapotranspiration": [0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2],
                    "temperature_2m_max": [70, 72, 75, 74, 71, 69, 68],
                    "temperature_2m_min": [50, 51, 52, 53, 50, 49, 48]
                  }
                }
                """;
        Optional<WeekForecast> f = IrrigationAdvisorService.parseOpenMeteo(json);
        assertThat(f).isPresent();
        assertThat(f.get().precipInches7d()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(0.001));
        assertThat(f.get().etInches7d()).isCloseTo(1.4, org.assertj.core.data.Offset.offset(0.001));
        assertThat(f.get().maxTempF()).isEqualTo(75.0);
        assertThat(f.get().minTempF()).isEqualTo(48.0);
    }

    @Test
    @DisplayName("toMap includes stable API keys")
    void toMapKeys() {
        IrrigationAdvisorService svc = new IrrigationAdvisorService(
                harvestService, false, 47.54, -122.64, (lat, lon) -> Optional.empty());
        when(harvestService.filter(isNull(), isNull(), isNull(), any(), any()))
                .thenReturn(List.of());
        var map = svc.adviseClimatology().toMap();
        assertThat(map).containsKeys(
                "location", "mode", "asOfDate", "season", "priority",
                "headline", "actions", "activeBeds", "climateNotes");
    }
}
