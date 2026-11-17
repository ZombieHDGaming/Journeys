package dev.voidscape.journeys.api.integration;

import dev.voidscape.journeys.api.JourneysApi;
import org.bukkit.plugin.Plugin;

/**
 * A bundled hook into another plugin. Journeys only loads the implementing class when {@link #requiredPlugin()} is
 * enabled, so the class may reference that plugin's API freely.
 */
public interface JourneysIntegration {

    /** Name of the plugin this integration hooks into, as in its plugin.yml. */
    String requiredPlugin();

    /**
     * Enables the hook: register types and listeners here.
     *
     * @param journeys the Journeys plugin, for scheduling and listener registration
     */
    void enable(JourneysApi api, Plugin journeys);

    /** Releases resources. Listeners registered for the Journeys plugin are removed automatically. */
    default void disable() {
    }
}
