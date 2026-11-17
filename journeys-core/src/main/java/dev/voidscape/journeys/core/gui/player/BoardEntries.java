package dev.voidscape.journeys.core.gui.player;

import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.progress.StartResult;
import dev.voidscape.journeys.api.quest.BoardSettings;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.core.JourneysContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** Works out which quests a player sees on the board and in what state. */
final class BoardEntries {

    /** Board states, in display order. */
    enum Status {
        AVAILABLE,
        ACTIVE,
        COOLDOWN,
        LOCKED,
        COMPLETED,
        LOADING
    }

    record Entry(QuestDefinition quest, QuestHolder holder, Status status) {
    }

    private BoardEntries() {
    }

    /**
     * Quests listed on the board for the player, optionally in one category, sorted by status then id. Quests set to
     * hide while locked are left out until the player meets their requirements.
     */
    static List<Entry> visible(JourneysContext ctx, Player player, @Nullable String category) {
        List<Entry> entries = new ArrayList<>();
        for (QuestDefinition quest : ctx.quests().all()) {
            BoardSettings board = quest.settings().board();
            if (board.mode() == BoardSettings.BoardMode.HIDDEN || (category != null && !board.category().equals(category))) {
                continue;
            }
            QuestHolder holder = ctx.holders().holderFor(player.getUniqueId(), quest.settings().scope());
            Status status = status(ctx.engine().canStart(holder, quest.id(), player));
            if (status == Status.LOCKED && !board.showWhenLocked()) {
                continue;
            }
            entries.add(new Entry(quest, holder, status));
        }
        entries.sort(Comparator.comparing(Entry::status).thenComparing(e -> e.quest().id()));
        return entries;
    }

    static Status status(StartResult result) {
        return switch (result) {
            case STARTED -> Status.AVAILABLE;
            case ALREADY_ACTIVE -> Status.ACTIVE;
            case ON_COOLDOWN -> Status.COOLDOWN;
            case REQUIREMENTS_NOT_MET, CANCELLED -> Status.LOCKED;
            case ALREADY_COMPLETED, MAX_COMPLETIONS -> Status.COMPLETED;
            case NOT_LOADED, UNKNOWN_QUEST -> Status.LOADING;
        };
    }
}
