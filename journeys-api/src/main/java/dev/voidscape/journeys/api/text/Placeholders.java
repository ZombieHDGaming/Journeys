package dev.voidscape.journeys.api.text;

import java.util.function.BiFunction;
import org.bukkit.OfflinePlayer;
import org.jspecify.annotations.Nullable;

/**
 * Fills in external placeholders such as PlaceholderAPI's {@code %vault_eco_balance%} in player-facing text.
 *
 * <p>Journeys applies this to messages, quest and stage names, descriptions, choice labels and reward text before
 * parsing them as MiniMessage. Without a resolver, text is returned unchanged. The bundled PlaceholderAPI hook sets
 * the resolver automatically.</p>
 */
public interface Placeholders {

    /** Replaces placeholders for the player. Returns the text unchanged if there is no resolver or no player. */
    String apply(@Nullable OfflinePlayer player, String text);

    /** Sets the resolver, replacing any previous one; null removes it. */
    void setResolver(@Nullable BiFunction<OfflinePlayer, String, String> resolver);

    /** Whether a resolver is set. */
    boolean isAvailable();
}
