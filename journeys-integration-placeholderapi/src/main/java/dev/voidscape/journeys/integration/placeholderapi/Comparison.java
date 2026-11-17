package dev.voidscape.journeys.integration.placeholderapi;

import java.util.Arrays;
import java.util.Locale;

/** How a placeholder's value is compared. Numeric when both sides are numbers, text otherwise. */
enum Comparison {
    EQUALS("="),
    NOT_EQUALS("!="),
    GREATER(">"),
    GREATER_OR_EQUAL(">="),
    LESS("<"),
    LESS_OR_EQUAL("<="),
    CONTAINS("contains");

    final String symbol;

    Comparison(String symbol) {
        this.symbol = symbol;
    }

    static Comparison parse(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "==", "eq", "equals" -> EQUALS;
            case "<>", "ne", "not" -> NOT_EQUALS;
            default -> Arrays.stream(values()).filter(c -> c.symbol.equals(value)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown operator '" + raw + "'. Use one of = != > >= < <= contains"));
        };
    }

    /**
     * Compares the resolved placeholder against the expected value. Text comparisons ignore case. Ordering operators
     * are false unless both sides are numbers. Numbers may use thousands separators (1,000.50).
     */
    boolean test(String actual, String expected) {
        Double a = number(actual);
        Double b = number(expected);
        if (a != null && b != null) {
            int cmp = Double.compare(a, b);
            return switch (this) {
                case EQUALS -> cmp == 0;
                case NOT_EQUALS -> cmp != 0;
                case GREATER -> cmp > 0;
                case GREATER_OR_EQUAL -> cmp >= 0;
                case LESS -> cmp < 0;
                case LESS_OR_EQUAL -> cmp <= 0;
                case CONTAINS -> actual.contains(expected);
            };
        }
        return switch (this) {
            case EQUALS -> actual.trim().equalsIgnoreCase(expected.trim());
            case NOT_EQUALS -> !actual.trim().equalsIgnoreCase(expected.trim());
            case CONTAINS -> actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
            default -> false;
        };
    }

    private static Double number(String text) {
        String cleaned = text.trim().replace(",", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
