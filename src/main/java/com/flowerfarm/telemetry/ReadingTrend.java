package com.flowerfarm.telemetry;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded chart geometry, with exact decimal/time differences before conversion to pixels. */
public final class ReadingTrend {
    private final List<FarmReading> readings;
    private final List<Point> points;
    private final BigDecimal minimum;
    private final BigDecimal maximum;

    public ReadingTrend(List<FarmReading> history) {
        readings = List.copyOf(history);
        if (readings.isEmpty() || readings.size() > FarmReadingService.MAX_PAGE) {
            throw new IllegalArgumentException("Choose between 1 and 200 saved readings.");
        }
        FarmReading first = readings.get(0);
        BigDecimal low = null;
        BigDecimal high = null;
        FarmReading previous = null;
        for (FarmReading reading : readings) {
            if (!sameChannel(first, reading) || (previous != null
                    && (reading.observedAt().isBefore(previous.observedAt())
                    || (reading.observedAt().equals(previous.observedAt()) && reading.id() <= previous.id())))) {
                throw new IllegalArgumentException("Keep one measurement in measured-time order.");
            }
            if (plottable(reading)) {
                low = low == null ? reading.value() : low.min(reading.value());
                high = high == null ? reading.value() : high.max(reading.value());
            }
            previous = reading;
        }
        minimum = low;
        maximum = high;
        BigDecimal span = seconds(first.observedAt(), readings.get(readings.size() - 1).observedAt());
        List<Point> geometry = new ArrayList<>();
        previous = null;
        for (FarmReading reading : readings) {
            double x = fraction(seconds(first.observedAt(), reading.observedAt()), span);
            Double y = plottable(reading) ? fraction(reading.value().subtract(low), high.subtract(low)) : null;
            boolean connected = previous != null && good(previous) && good(reading);
            geometry.add(new Point(x, y, reading.quality(), connected));
            previous = reading;
        }
        points = List.copyOf(geometry);
    }

    private static boolean sameChannel(FarmReading a, FarmReading b) {
        return Objects.equals(a.sourceId(), b.sourceId()) && Objects.equals(a.deviceId(), b.deviceId())
                && Objects.equals(a.metric(), b.metric()) && Objects.equals(a.unit(), b.unit())
                && Objects.equals(a.location(), b.location());
    }

    private static boolean good(FarmReading reading) { return "GOOD".equals(reading.quality()) && reading.value() != null; }
    private static boolean plottable(FarmReading reading) {
        return reading.value() != null && ("GOOD".equals(reading.quality()) || "SUSPECT".equals(reading.quality()));
    }
    private static BigDecimal seconds(Instant from, Instant to) {
        Duration duration = Duration.between(from, to);
        return BigDecimal.valueOf(duration.getSeconds()).add(BigDecimal.valueOf(duration.getNano(), 9));
    }
    private static double fraction(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? 0.5 : part.divide(whole, MathContext.DECIMAL128).doubleValue();
    }

    public List<FarmReading> readings() { return readings; }
    public List<Point> points() { return points; }
    public BigDecimal minimum() { return minimum; }
    public BigDecimal maximum() { return maximum; }
    public record Point(double timeFraction, Double valueFraction, String condition, boolean connectsPrevious) { }
}
