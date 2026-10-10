package com.flowerfarm.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class LogValueTest {
    @Test
    void untrustedFieldsStayOnOnePhysicalLogLine() {
        assertEquals("crop%0D%0AFORGED%09record%00", LogValue.escape("crop\r\nFORGED\trecord\0"));
    }

    @Test
    void encodingIsUnambiguousAndLeavesOrdinarySpacesReadable() {
        assertEquals("red rose %2B %250A", LogValue.escape("red rose + %0A"));
        assertEquals("%C3%A9", LogValue.escape("é"));
    }

    @Test
    void typedIdsDatesAndNullRetainReadableValues() {
        assertEquals("42", LogValue.escape(42L));
        assertEquals("2026-10-03", LogValue.escape(LocalDate.of(2026, 10, 3)));
        assertEquals("null", LogValue.escape(null));
    }
}
