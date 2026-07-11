package com.flowerfarm.service;

import com.flowerfarm.model.HarvestEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kitsap / Port Orchard irrigation guidance for maritime PNW flower beds.
 *
 * <p><b>Offline:</b> month-based climatology (always available for demos/CI).<br>
 * <b>Live:</b> optional Open-Meteo forecast (no API key) when network is up.
 * Harvest log beds from the last 14 days surface as "active" care targets.
 */
@Service
public class IrrigationAdvisorService {

    private static final Logger log = LoggerFactory.getLogger(IrrigationAdvisorService.class);

    /** Port Orchard, Kitsap County, WA (approx. downtown / waterfront). */
    public static final double DEFAULT_LAT = 47.5407;
    public static final double DEFAULT_LON = -122.6362;
    public static final ZoneId PNW_ZONE = ZoneId.of("America/Los_Angeles");

    private final HarvestService harvestService;
    private final boolean liveWeatherEnabled;
    private final double latitude;
    private final double longitude;
    private final HttpClient httpClient;
    private final LiveForecastFetcher forecastFetcher;

    public IrrigationAdvisorService(
            HarvestService harvestService,
            @Value("${flowerfarm.irrigation.live-weather:true}") boolean liveWeatherEnabled,
            @Value("${flowerfarm.irrigation.latitude:47.5407}") double latitude,
            @Value("${flowerfarm.irrigation.longitude:-122.6362}") double longitude) {
        this(harvestService, liveWeatherEnabled, latitude, longitude, null);
    }

    /** Test / custom-fetch constructor. */
    IrrigationAdvisorService(HarvestService harvestService,
                             boolean liveWeatherEnabled,
                             double latitude,
                             double longitude,
                             LiveForecastFetcher forecastFetcher) {
        this.harvestService = harvestService;
        this.liveWeatherEnabled = liveWeatherEnabled;
        this.latitude = latitude;
        this.longitude = longitude;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
        this.forecastFetcher = forecastFetcher != null
                ? forecastFetcher
                : this::fetchOpenMeteo;
    }

    /** Functional hook so unit tests can inject forecasts without HTTP. */
    @FunctionalInterface
    interface LiveForecastFetcher {
        Optional<WeekForecast> fetch(double lat, double lon);
    }

    public record WeekForecast(
            double precipInches7d,
            double etInches7d,
            double maxTempF,
            double minTempF,
            String source
    ) {}

    public enum SeasonBand {
        WET_WINTER,
        EARLY_SPRING,
        LATE_SPRING,
        EARLY_SUMMER,
        PEAK_DRY_SUMMER,
        FALL_TRANSITION
    }

    public enum Priority {
        NONE, LOW, MEDIUM, HIGH
    }

    public record IrrigationAdvice(
            String location,
            String mode,
            String asOfDate,
            SeasonBand season,
            Priority priority,
            String headline,
            List<String> actions,
            List<String> activeBeds,
            Double weekPrecipInches,
            Double weekEtInches,
            Double moistureDeficitInches,
            Double maxTempF,
            Double minTempF,
            String climateNotes
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("location", location);
            m.put("mode", mode);
            m.put("asOfDate", asOfDate);
            m.put("season", season.name());
            m.put("priority", priority.name());
            m.put("headline", headline);
            m.put("actions", actions);
            m.put("activeBeds", activeBeds);
            m.put("weekPrecipInches", weekPrecipInches);
            m.put("weekEtInches", weekEtInches);
            m.put("moistureDeficitInches", moistureDeficitInches);
            m.put("maxTempF", maxTempF);
            m.put("minTempF", minTempF);
            m.put("climateNotes", climateNotes);
            return m;
        }
    }

    /**
     * Full advisory for today (system date in America/Los_Angeles).
     *
     * @param preferLive when true and live weather is enabled, try Open-Meteo first
     */
    public IrrigationAdvice advise(boolean preferLive) {
        LocalDate today = LocalDate.now(PNW_ZONE);
        SeasonBand season = seasonFor(today.getMonth());
        List<String> activeBeds = activeBedsLastDays(14);
        Optional<WeekForecast> live = Optional.empty();
        if (preferLive && liveWeatherEnabled) {
            live = forecastFetcher.fetch(latitude, longitude);
        }
        return buildAdvice(today, season, activeBeds, live);
    }

    /** Climatology-only (never hits the network). */
    public IrrigationAdvice adviseClimatology() {
        return advise(false);
    }

    public SeasonBand seasonFor(Month month) {
        return switch (month) {
            case NOVEMBER, DECEMBER, JANUARY, FEBRUARY, MARCH -> SeasonBand.WET_WINTER;
            case APRIL -> SeasonBand.EARLY_SPRING;
            case MAY -> SeasonBand.LATE_SPRING;
            case JUNE -> SeasonBand.EARLY_SUMMER;
            case JULY, AUGUST -> SeasonBand.PEAK_DRY_SUMMER;
            case SEPTEMBER, OCTOBER -> SeasonBand.FALL_TRANSITION;
        };
    }

    IrrigationAdvice buildAdvice(LocalDate today,
                                 SeasonBand season,
                                 List<String> activeBeds,
                                 Optional<WeekForecast> live) {
        List<String> actions = new ArrayList<>();
        Priority priority;
        String headline;
        String mode;
        Double precip = null;
        Double et = null;
        Double deficit = null;
        Double maxF = null;
        Double minF = null;

        if (live.isPresent()) {
            WeekForecast f = live.get();
            mode = "LIVE";
            precip = round1(f.precipInches7d());
            et = round1(f.etInches7d());
            deficit = round1(Math.max(0, f.etInches7d() - f.precipInches7d()));
            maxF = round1(f.maxTempF());
            minF = round1(f.minTempF());
            LiveDecision d = decideFromLive(season, f);
            priority = d.priority();
            headline = d.headline();
            actions.addAll(d.actions());
        } else {
            mode = "CLIMATOLOGY";
            ClimateDecision d = decideFromClimate(season);
            priority = d.priority();
            headline = d.headline();
            actions.addAll(d.actions());
        }

        if (!activeBeds.isEmpty()) {
            actions.add("Active harvest beds (14d): " + String.join(", ", activeBeds)
                    + " — prioritize even moisture after cuts.");
        } else {
            actions.add("No harvest beds logged in 14 days — still water inventory crop rows on schedule.");
        }

        actions.addAll(alwaysPnWTips(season));

        return new IrrigationAdvice(
                "Port Orchard / Kitsap County, WA",
                mode,
                today.toString(),
                season,
                priority,
                headline,
                List.copyOf(actions),
                List.copyOf(activeBeds),
                precip,
                et,
                deficit,
                maxF,
                minF,
                climateNotes(season)
        );
    }

    private List<String> activeBedsLastDays(int days) {
        LocalDate from = LocalDate.now(PNW_ZONE).minusDays(days - 1L);
        LocalDate to = LocalDate.now(PNW_ZONE);
        List<HarvestEntry> entries = harvestService.filter(null, null, null, from, to);
        Set<String> beds = new LinkedHashSet<>();
        for (HarvestEntry e : entries) {
            if (e.getBedOrField() != null && !e.getBedOrField().isBlank()) {
                beds.add(e.getBedOrField().trim());
            }
        }
        return beds.stream().limit(12).collect(Collectors.toList());
    }

    private record LiveDecision(Priority priority, String headline, List<String> actions) {}

    private LiveDecision decideFromLive(SeasonBand season, WeekForecast f) {
        double precip = f.precipInches7d();
        double et = f.etInches7d();
        double deficit = Math.max(0, et - precip);
        List<String> actions = new ArrayList<>();

        // Target ~1.0–1.5" effective water / week for cut flowers in peak dry months
        double target = switch (season) {
            case PEAK_DRY_SUMMER -> 1.25;
            case EARLY_SUMMER -> 1.0;
            case LATE_SPRING, FALL_TRANSITION -> 0.75;
            case EARLY_SPRING -> 0.5;
            case WET_WINTER -> 0.25;
        };

        Priority priority;
        String headline;

        if (season == SeasonBand.WET_WINTER && precip >= 0.75) {
            priority = Priority.NONE;
            headline = String.format(Locale.US,
                    "Wet week ahead (%.1f\" rain) — hold irrigation; watch clay root rot.",
                    precip);
            actions.add("Skip outdoor drip this week unless tunnel/hoophouse crops are dry.");
            actions.add("Improve drainage paths; avoid walking saturated beds.");
        } else if (deficit >= target * 0.85 || (precip < 0.25 && season == SeasonBand.PEAK_DRY_SUMMER)) {
            priority = Priority.HIGH;
            headline = String.format(Locale.US,
                    "Dry stress risk — ~%.1f\" moisture deficit vs ET (precip %.1f\", ET %.1f\").",
                    deficit, precip, et);
            actions.add("Deep soak 1–2\" this week via drip (split over 2–3 cycles).");
            actions.add("Mulch bare soil; water early morning to cut fungal pressure.");
            if (f.maxTempF() >= 85) {
                actions.add(String.format(Locale.US,
                        "Heat spike (max ~%.0f°F) — extra pass on new transplants and pots.",
                        f.maxTempF()));
            }
        } else if (deficit >= target * 0.45) {
            priority = Priority.MEDIUM;
            headline = String.format(Locale.US,
                    "Moderate dry-down (deficit ~%.1f\") — keep scheduled drip.",
                    deficit);
            actions.add("Run normal drip every 3–5 days; check soil 4–6\" deep before skipping a day.");
            actions.add(String.format(Locale.US,
                    "Forecast precip %.1f\" / ET %.1f\" over 7 days.", precip, et));
        } else {
            priority = Priority.LOW;
            headline = String.format(Locale.US,
                    "Moisture looking OK (precip %.1f\", ET %.1f\") — light maintenance only.",
                    precip, et);
            actions.add("Spot-water only wilt-prone rows and containers.");
            if (precip >= 1.0) {
                actions.add("Rainy stretch — pause timers; resume after soils drain.");
            }
        }
        return new LiveDecision(priority, headline, actions);
    }

    private record ClimateDecision(Priority priority, String headline, List<String> actions) {}

    private ClimateDecision decideFromClimate(SeasonBand season) {
        return switch (season) {
            case WET_WINTER -> new ClimateDecision(
                    Priority.NONE,
                    "Kitsap wet season — irrigation mostly off outdoors.",
                    List.of(
                            "Rely on winter rain; protect from standing water on clay.",
                            "Covered crops only: hand-water when media is dry 1\" down."
                    ));
            case EARLY_SPRING -> new ClimateDecision(
                    Priority.LOW,
                    "Early spring transition — light supplemental water as growth starts.",
                    List.of(
                            "Begin drip on new plantings if a dry week shows up.",
                            "Harden off starts; avoid evening overhead that invites mildew."
                    ));
            case LATE_SPRING -> new ClimateDecision(
                    Priority.MEDIUM,
                    "Late spring — beds fill in; don't let new plantings dry out.",
                    List.of(
                            "Aim ~0.75–1\" total water/week including rain.",
                            "Drip preferred; check filter screens after first algal flush."
                    ));
            case EARLY_SUMMER -> new ClimateDecision(
                    Priority.MEDIUM,
                    "Early summer dry-down begins west of the Cascades.",
                    List.of(
                            "Schedule drip every 3–5 days; deep soak over frequent sprinkle.",
                            "Mulch paths and rose understory before July heat."
                    ));
            case PEAK_DRY_SUMMER -> new ClimateDecision(
                    Priority.HIGH,
                    "Peak dry summer (Jul–Aug) — plan 1–2\" water per week.",
                    List.of(
                            "Primary window for stress: keep cut-flower beds even-moist.",
                            "Roses: deep soak preferred; avoid wet foliage overnight.",
                            "Clay soils: slow drip longer rather than flooding surface."
                    ));
            case FALL_TRANSITION -> new ClimateDecision(
                    Priority.LOW,
                    "Fall rains return — taper irrigation as fronts arrive.",
                    List.of(
                            "Cut back timers weekly; watch late plantings for dry spells.",
                            "Clean and winterize lines before first hard freeze inland."
                    ));
        };
    }

    private List<String> alwaysPnWTips(SeasonBand season) {
        List<String> tips = new ArrayList<>();
        tips.add("Prefer drip over overhead to reduce fungal pressure in humid maritime air.");
        if (season == SeasonBand.PEAK_DRY_SUMMER || season == SeasonBand.EARLY_SUMMER) {
            tips.add("Target roughly 1–2 inches/week in dry summers for most cut flowers.");
        }
        tips.add("Heavy Kitsap clay: deep soak, then wait — shallow daily mist promotes weak roots.");
        return tips;
    }

    private String climateNotes(SeasonBand season) {
        return switch (season) {
            case WET_WINTER -> "Typical Port Orchard winters are cool and wet; outdoor irrigation is rarely needed.";
            case EARLY_SPRING -> "April averages still moist; frost risk can linger on clear nights.";
            case LATE_SPRING -> "May growth push — balance soil moisture without waterlogging clay.";
            case EARLY_SUMMER -> "June often drier than spring; high-pressure gaps become common.";
            case PEAK_DRY_SUMMER -> "July–August are the driest months west of the Cascades; ET often exceeds rain.";
            case FALL_TRANSITION -> "September–October: rains return; reduce runtime as storms establish.";
        };
    }

    private Optional<WeekForecast> fetchOpenMeteo(double lat, double lon) {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast"
                        + "?latitude=%.4f&longitude=%.4f"
                        + "&daily=precipitation_sum,et0_fao_evapotranspiration,"
                        + "temperature_2m_max,temperature_2m_min"
                        + "&temperature_unit=fahrenheit"
                        + "&precipitation_unit=inch"
                        + "&timezone=America%%2FLos_Angeles"
                        + "&forecast_days=7",
                lat, lon);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                log.debug("Open-Meteo non-OK status {}", response.statusCode());
                return Optional.empty();
            }
            return parseOpenMeteo(response.body());
        } catch (Exception ex) {
            log.info("Live weather unavailable (using climatology): {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Minimal JSON parse without a full binding dependency — daily arrays only.
     */
    static Optional<WeekForecast> parseOpenMeteo(String json) {
        try {
            double precip = sumArray(json, "precipitation_sum");
            double et = sumArray(json, "et0_fao_evapotranspiration");
            double maxT = maxArray(json, "temperature_2m_max");
            double minT = minArray(json, "temperature_2m_min");
            // Open-Meteo returns mm if unit not honored — convert if values look like mm
            // With precipitation_unit=inch we expect inches; if sum > 20 treat as mm → inches
            if (precip > 20) {
                precip = precip / 25.4;
            }
            if (et > 20) {
                et = et / 25.4;
            }
            return Optional.of(new WeekForecast(precip, et, maxT, minT, "open-meteo"));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static double sumArray(String json, String key) {
        List<Double> vals = readNumberArray(json, key);
        return vals.stream().mapToDouble(Double::doubleValue).sum();
    }

    private static double maxArray(String json, String key) {
        List<Double> vals = readNumberArray(json, key);
        return vals.stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }

    private static double minArray(String json, String key) {
        List<Double> vals = readNumberArray(json, key);
        return vals.stream().mapToDouble(Double::doubleValue).min().orElse(0);
    }

    private static List<Double> readNumberArray(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyIdx = json.indexOf(needle);
        if (keyIdx < 0) {
            return List.of();
        }
        int bracket = json.indexOf('[', keyIdx);
        int end = json.indexOf(']', bracket);
        if (bracket < 0 || end < 0) {
            return List.of();
        }
        String body = json.substring(bracket + 1, end);
        List<Double> out = new ArrayList<>();
        for (String part : body.split(",")) {
            String t = part.trim();
            if (t.isEmpty() || "null".equalsIgnoreCase(t)) {
                continue;
            }
            out.add(Double.parseDouble(t));
        }
        return out;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
