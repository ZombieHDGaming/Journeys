package dev.voidscape.journeys.integration.placeholderapi;

import dev.voidscape.journeys.api.JourneysApi;
import dev.voidscape.journeys.api.integration.JourneysIntegration;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.plugin.Plugin;

/**
 * PlaceholderAPI hook:
 * <ul>
 *   <li>fills {@code %placeholders%} in Journeys' messages, quest text and reward text;</li>
 *   <li>adds the {@code journeys:placeholder} condition, to gate quests and branches on any placeholder;</li>
 *   <li>registers the {@code %journeys_...%} placeholders for scoreboards, tab lists and other plugins.</li>
 * </ul>
 */
public final class PlaceholderApiIntegration implements JourneysIntegration {

    private JourneysApi api;
    private PlaceholderCondition condition;
    private JourneysExpansion expansion;

    @Override
    public String requiredPlugin() {
        return "PlaceholderAPI";
    }

    @Override
    public void enable(JourneysApi api, Plugin journeys) {
        this.api = api;
        api.placeholders().setResolver(PlaceholderAPI::setPlaceholders);
        condition = new PlaceholderCondition();
        api.conditionTypes().register(condition);
        expansion = new JourneysExpansion(api, journeys);
        expansion.register();
        expansion.startCacheRefresh();
    }

    @Override
    public void disable() {
        api.placeholders().setResolver(null);
        if (condition != null) {
            api.conditionTypes().unregister(condition.key());
        }
        if (expansion != null) {
            expansion.stopCacheRefresh();
            expansion.unregister();
        }
    }
}
