package com.flowerfarm.telemetry;

import java.math.BigDecimal;
import java.time.Instant;

/** One adapter-reported observation; units and quality are not inferred by the store. */
public record FarmReading(long id, String sourceId, String readingKey, String deviceId,
                          String metric, String unit, BigDecimal value, String quality,
                          Instant observedAt, Instant receivedAt, String location,
                          String rawFrame) {
}
