package dev.voidscape.journeys.core.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and formats compact durations such as {@code 1d12h}, {@code 90m} or {@code 30s}. */
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([dhms])");

    private Durations() {
    }

    /**
     * @throws IllegalArgumentException if the text is not a valid duration
     */
    public static Duration parse(String text) {
        String input = text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (input.isEmpty() || input.equals("0")) {
            return Duration.ZERO;
        }
        Matcher matcher = PART.matcher(input);
        Duration total = Duration.ZERO;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                break;
            }
            long value = Long.parseLong(matcher.group(1));
            total = total.plus(switch (matcher.group(2)) {
                case "d" -> Duration.ofDays(value);
                case "h" -> Duration.ofHours(value);
                case "m" -> Duration.ofMinutes(value);
                default -> Duration.ofSeconds(value);
            });
            end = matcher.end();
        }
        if (end != input.length()) {
            throw new IllegalArgumentException("'" + text + "' is not a duration. Use e.g. 1d12h, 90m or 30s");
        }
        return total;
    }

    public static String format(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return "0s";
        }
        StringBuilder out = new StringBuilder();
        long days = duration.toDays();
        int hours = duration.toHoursPart();
        int minutes = duration.toMinutesPart();
        int seconds = duration.toSecondsPart();
        if (days > 0) {
            out.append(days).append('d');
        }
        if (hours > 0) {
            out.append(hours).append('h');
        }
        if (minutes > 0) {
            out.append(minutes).append('m');
        }
        if (seconds > 0) {
            out.append(seconds).append('s');
        }
        return out.isEmpty() ? "0s" : out.toString();
    }
}
