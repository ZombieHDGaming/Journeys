package dev.voidscape.journeys.core.quest;

/** A quest file could not be read into a definition. */
public final class QuestFormatException extends Exception {

    public QuestFormatException(String path, String message) {
        super(path + ": " + message);
    }
}
