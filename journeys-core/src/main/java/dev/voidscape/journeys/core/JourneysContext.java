package dev.voidscape.journeys.core;

import dev.voidscape.journeys.api.holder.HolderService;
import dev.voidscape.journeys.core.config.Messages;
import dev.voidscape.journeys.core.editor.EditorSessions;
import dev.voidscape.journeys.core.engine.QuestEngine;
import dev.voidscape.journeys.core.gui.ChatPrompts;
import dev.voidscape.journeys.core.quest.YamlQuestRepository;
import dev.voidscape.journeys.core.type.Registries;
import org.bukkit.plugin.Plugin;

/** The services menus and commands need, passed around as one value instead of a long parameter list. */
public record JourneysContext(
        Plugin plugin,
        QuestEngine engine,
        YamlQuestRepository quests,
        Registries registries,
        HolderService holders,
        Messages messages,
        ChatPrompts prompts,
        EditorSessions editor
) {
}
