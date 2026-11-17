package dev.voidscape.journeys.api.quest;

import java.util.regex.Pattern;

/** Rules for quest, stage and objective ids. */
public final class QuestIds {

    /** Lowercase letters, digits, underscores and dashes, 1 to 64 characters. Safe as a file name and SQL value. */
    public static final Pattern PATTERN = Pattern.compile("[a-z0-9_-]{1,64}");

    private QuestIds() {
    }

    public static boolean isValid(String id) {
        return id != null && PATTERN.matcher(id).matches();
    }

    /** @throws IllegalArgumentException if the id is not valid */
    public static String requireValid(String id, String what) {
        if (!isValid(id)) {
            throw new IllegalArgumentException(what + " id '" + id + "' must match " + PATTERN.pattern());
        }
        return id;
    }
}
