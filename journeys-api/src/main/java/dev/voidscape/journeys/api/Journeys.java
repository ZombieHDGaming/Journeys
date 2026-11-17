package dev.voidscape.journeys.api;

import java.util.Objects;

/**
 * Static access point for the Journeys API.
 *
 * <p>The API is also registered with Bukkit's {@link org.bukkit.plugin.ServicesManager}, so either
 * {@code Journeys.get()} or {@code Bukkit.getServicesManager().load(JourneysApi.class)} works.</p>
 */
public final class Journeys {

    private static volatile JourneysApi instance;

    private Journeys() {
    }

    /**
     * Returns the running API instance.
     *
     * @throws IllegalStateException if Journeys is not enabled
     */
    public static JourneysApi get() {
        JourneysApi api = instance;
        if (api == null) {
            throw new IllegalStateException("Journeys is not enabled yet");
        }
        return api;
    }

    /** Returns true when the API can be used. */
    public static boolean isAvailable() {
        return instance != null;
    }

    /** Internal: called by the Journeys plugin on enable. */
    public static void register(JourneysApi api) {
        instance = Objects.requireNonNull(api, "api");
    }

    /** Internal: called by the Journeys plugin on disable. */
    public static void unregister() {
        instance = null;
    }
}
