package dev.voidscape.journeys.core.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Compact text form of a run's path through a quest, stored in one column: {@code gather,deliver|gather=deliver}.
 * Visited stages come before the bar, taken transitions after it. Stage ids cannot contain {@code , | =}, so no
 * escaping is needed.
 */
public final class QuestPath {

    public record Path(List<String> visited, Map<String, String> taken) {
    }

    private QuestPath() {
    }

    public static String encode(List<String> visited, Map<String, String> taken) {
        StringBuilder out = new StringBuilder(String.join(",", visited)).append('|');
        boolean first = true;
        for (Map.Entry<String, String> entry : taken.entrySet()) {
            if (!first) {
                out.append(',');
            }
            out.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return out.toString();
    }

    public static Path decode(@Nullable String text) {
        List<String> visited = new ArrayList<>();
        Map<String, String> taken = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) {
            return new Path(visited, taken);
        }
        int bar = text.indexOf('|');
        String visitedPart = bar < 0 ? text : text.substring(0, bar);
        String takenPart = bar < 0 ? "" : text.substring(bar + 1);
        for (String stage : visitedPart.split(",")) {
            if (!stage.isEmpty()) {
                visited.add(stage);
            }
        }
        for (String pair : takenPart.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && eq < pair.length() - 1) {
                taken.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return new Path(visited, taken);
    }
}
