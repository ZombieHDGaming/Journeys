package dev.voidscape.journeys.integration.placeholderapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ComparisonTest {

    @Test
    void parsesSymbolsAndAliases() {
        assertEquals(Comparison.GREATER_OR_EQUAL, Comparison.parse(">="));
        assertEquals(Comparison.EQUALS, Comparison.parse("=="));
        assertEquals(Comparison.NOT_EQUALS, Comparison.parse("ne"));
        assertEquals(Comparison.CONTAINS, Comparison.parse("CONTAINS"));
        assertThrows(IllegalArgumentException.class, () -> Comparison.parse("~"));
    }

    @Test
    void comparesNumbersNumerically() {
        assertTrue(Comparison.GREATER_OR_EQUAL.test("1,250.50", "1000"), "thousands separators are ignored");
        assertTrue(Comparison.EQUALS.test("5.0", "5"));
        assertFalse(Comparison.LESS.test("10", "9"));
        assertTrue(Comparison.NOT_EQUALS.test("3", "4"));
    }

    @Test
    void comparesTextIgnoringCase() {
        assertTrue(Comparison.EQUALS.test("Miner", "miner"));
        assertTrue(Comparison.CONTAINS.test("Group: Miner", "MINER"));
        assertFalse(Comparison.GREATER.test("abc", "abd"), "ordering needs numbers");
        assertTrue(Comparison.NOT_EQUALS.test("", "none"), "an unresolved placeholder is just text");
    }
}
