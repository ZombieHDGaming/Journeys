package dev.voidscape.journeys.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationsTest {

    @Test
    void parsesCompoundDurations() {
        assertEquals(Duration.ofDays(1).plusHours(12), Durations.parse("1d12h"));
        assertEquals(Duration.ofMinutes(90), Durations.parse("90m"));
        assertEquals(Duration.ofSeconds(30), Durations.parse(" 30 s "));
        assertEquals(Duration.ZERO, Durations.parse("0"));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> Durations.parse("soon"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parse("1d banana"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parse("5"));
    }

    @Test
    void formatsCompactly() {
        assertEquals("1d12h", Durations.format(Duration.ofHours(36)));
        assertEquals("1h30m", Durations.format(Duration.ofMinutes(90)));
        assertEquals("0s", Durations.format(Duration.ZERO));
    }
}
