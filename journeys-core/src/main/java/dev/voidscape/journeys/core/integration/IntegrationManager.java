package dev.voidscape.journeys.core.integration;

import dev.voidscape.journeys.api.JourneysApi;
import dev.voidscape.journeys.api.integration.JourneysIntegration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Enables the bundled integrations whose target plugin is installed.
 *
 * <p>Integration classes are looked up by name and only loaded when their plugin is enabled, so the core never
 * touches another plugin's classes unless that plugin is present.</p>
 */
public final class IntegrationManager {

    /** Target plugin name to integration class. Add new bundled hooks here. */
    private static final Map<String, String> BUNDLED = Map.of(
            "FancyNpcs", "dev.voidscape.journeys.integration.fancynpcs.FancyNpcsIntegration",
            "PlaceholderAPI", "dev.voidscape.journeys.integration.placeholderapi.PlaceholderApiIntegration"
    );

    private final Plugin plugin;
    private final List<JourneysIntegration> enabled = new ArrayList<>();

    public IntegrationManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void enableAll(JourneysApi api) {
        BUNDLED.forEach((pluginName, className) -> {
            if (!Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
                return;
            }
            try {
                JourneysIntegration integration = Class.forName(className, true, plugin.getClass().getClassLoader())
                        .asSubclass(JourneysIntegration.class)
                        .getDeclaredConstructor()
                        .newInstance();
                integration.enable(api, plugin);
                enabled.add(integration);
                plugin.getLogger().info("Hooked into " + pluginName);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                // LinkageError covers an installed version whose API differs from the one we compiled against.
                plugin.getLogger().log(Level.WARNING, "Could not hook into " + pluginName + "; the integration is disabled", e);
            }
        });
    }

    public void disableAll() {
        for (JourneysIntegration integration : enabled) {
            try {
                integration.disable();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Error disabling " + integration.requiredPlugin() + " integration", e);
            }
        }
        enabled.clear();
    }
}
