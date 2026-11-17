package dev.voidscape.journeys.integration.fancynpcs;

import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import dev.voidscape.journeys.api.JourneysApi;
import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.integration.JourneysIntegration;
import dev.voidscape.journeys.api.progress.StartResult;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.type.ObjectiveType;
import dev.voidscape.journeys.api.type.Parameter;
import dev.voidscape.journeys.api.type.ParameterTypes;
import dev.voidscape.journeys.api.type.ParameterValues;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * FancyNPCs hook.
 *
 * <ul>
 *   <li>Adds the {@code journeys:talk_to_npc} objective: interact with one of the listed NPCs.</li>
 *   <li>Quest givers: a quest whose {@value #GIVERS_PROPERTY} property lists an NPC's name is offered when a player
 *   interacts with that NPC. Several names are separated by commas.</li>
 * </ul>
 *
 * NPCs are matched by their FancyNPCs name (the one used in {@code /npc} commands), case-insensitively.
 */
public final class FancyNpcsIntegration implements JourneysIntegration, Listener {

    public static final String GIVERS_PROPERTY = "fancynpcs.givers";

    private JourneysApi api;
    private TalkToNpcObjective objective;

    @Override
    public String requiredPlugin() {
        return "FancyNpcs";
    }

    @Override
    public void enable(JourneysApi api, Plugin journeys) {
        this.api = api;
        this.objective = new TalkToNpcObjective(api);
        api.objectiveTypes().register(objective);
        journeys.getServer().getPluginManager().registerEvents(this, journeys);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        if (objective != null) {
            api.objectiveTypes().unregister(objective.key());
        }
    }

    /**
     * Offers quests given by the clicked NPC. Runs after the objective listener, so a click that starts a quest does
     * not also count towards that quest's first "talk to" objective.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(NpcInteractEvent event) {
        Player player = event.getPlayer();
        String npc = event.getNpc().getData().getName();
        for (QuestDefinition quest : api.quests().all()) {
            if (!isGiver(quest, npc)) {
                continue;
            }
            QuestHolder holder = api.holders().holderFor(player.getUniqueId(), quest.settings().scope());
            StartResult check = api.questService().canStart(holder, quest.id(), player);
            // Talking to a giver while on its quest is normal; do not spam the player about it.
            if (check == StartResult.ALREADY_ACTIVE) {
                continue;
            }
            api.questService().start(holder, quest.id(), player, true);
        }
    }

    static boolean isGiver(QuestDefinition quest, String npcName) {
        return quest.property(GIVERS_PROPERTY)
                .map(value -> Arrays.stream(value.split(",")).map(String::trim).anyMatch(name -> name.equalsIgnoreCase(npcName)))
                .orElse(false);
    }

    /** Interact with an NPC. */
    static final class TalkToNpcObjective implements ObjectiveType, Listener {

        static final Parameter<List<String>> NPC = Parameter.required("npc",
                Component.text("NPC names"), ParameterTypes.listOf(ParameterTypes.STRING));

        private final JourneysApi api;

        TalkToNpcObjective(JourneysApi api) {
            this.api = api;
        }

        @Override
        public Key key() {
            return Key.key("journeys", "talk_to_npc");
        }

        @Override
        public Component displayName() {
            return Component.text("Talk to an NPC");
        }

        @Override
        public List<Component> description() {
            return List.of(Component.text("Right-click a FancyNPCs NPC"));
        }

        @Override
        public Key icon() {
            return Key.key("minecraft:villager_spawn_egg");
        }

        @Override
        public List<Parameter<?>> parameters() {
            return List.of(NPC);
        }

        @Override
        public boolean countable() {
            return false;
        }

        @Override
        public Component describe(ParameterValues values) {
            List<String> names = values.getOrNull(NPC);
            return Component.text("Talk to " + (names == null ? "?" : String.join(" or ", names)));
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onInteract(NpcInteractEvent event) {
            String npc = event.getNpc().getData().getName().toLowerCase(Locale.ROOT);
            api.objectiveTracker().progress(event.getPlayer(), this,
                    values -> values.get(NPC).stream().anyMatch(name -> name.toLowerCase(Locale.ROOT).equals(npc)), 1);
        }
    }
}
