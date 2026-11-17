package dev.voidscape.journeys.core.type.builtin;

import dev.voidscape.journeys.api.holder.HolderService;
import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.progress.QuestProgress;
import dev.voidscape.journeys.api.progress.QuestService;
import dev.voidscape.journeys.api.quest.QuestDefinition;
import dev.voidscape.journeys.api.quest.QuestRepository;
import dev.voidscape.journeys.api.type.ConditionType;
import dev.voidscape.journeys.api.type.Parameter;
import dev.voidscape.journeys.api.type.ParameterTypes;
import dev.voidscape.journeys.api.type.ParameterValues;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Built-in condition types. */
public final class ConditionTypes {

    private ConditionTypes() {
    }

    public static List<ConditionType> all(Supplier<QuestService> questService, HolderService holders, QuestRepository quests) {
        ProgressLookup lookup = new ProgressLookup(questService, holders);
        return List.of(
                new Permission(),
                new QuestCompleted(lookup),
                new CompletedCount(lookup, quests),
                new QuestStageReached(lookup),
                new QuestBranch(lookup),
                new MinLevel());
    }

    /**
     * Reads quest progress across every holder the player belongs to, so progress made solo still counts for a party
     * quest's requirements and the other way round.
     */
    record ProgressLookup(Supplier<QuestService> questService, HolderService holders) {

        List<QuestProgress> all(Player player) {
            List<QuestProgress> progress = new ArrayList<>();
            for (QuestHolder holder : holders.holdersOf(player.getUniqueId())) {
                progress.addAll(questService.get().allProgress(holder));
            }
            return progress;
        }

        boolean any(Player player, String questId, Predicate<QuestProgress> test) {
            for (QuestHolder holder : holders.holdersOf(player.getUniqueId())) {
                Optional<QuestProgress> progress = questService.get().progress(holder, questId);
                if (progress.isPresent() && test.test(progress.get())) {
                    return true;
                }
            }
            return false;
        }
    }

    static final class Permission extends BaseType implements ConditionType {
        static final Parameter<String> NODE = Parameter.required("permission", Component.text("Permission node"), ParameterTypes.STRING);

        Permission() {
            super("permission", "Has permission", "minecraft:tripwire_hook", NODE);
        }

        @Override
        public boolean test(Player player, QuestHolder holder, ParameterValues values) {
            return player.hasPermission(values.get(NODE));
        }

        @Override
        public Component describe(ParameterValues values) {
            return Component.text("Has permission " + values.raw(NODE.id()).orElse("?"));
        }
    }

    static final class QuestCompleted extends BaseType implements ConditionType {
        static final Parameter<String> QUEST = Parameter.required("quest", Component.text("Quest id"), ParameterTypes.STRING);
        static final Parameter<Integer> TIMES = Parameter.optional("times", Component.text("Minimum completions"), ParameterTypes.POSITIVE_INTEGER, "1");

        private final ProgressLookup lookup;

        QuestCompleted(ProgressLookup lookup) {
            super("quest_completed", "Completed quest", "minecraft:writable_book", QUEST, TIMES);
            this.lookup = lookup;
        }

        @Override
        public boolean test(Player player, QuestHolder holder, ParameterValues values) {
            int times = values.get(TIMES);
            return lookup.any(player, values.get(QUEST), p -> p.completions() >= times);
        }

        @Override
        public Component describe(ParameterValues values) {
            return Component.text("Complete " + values.raw(QUEST.id()).orElse("?"));
        }
    }

    static final class CompletedCount extends BaseType implements ConditionType {
        static final Parameter<Integer> AMOUNT = Parameter.required("amount", Component.text("Number of quests"), ParameterTypes.POSITIVE_INTEGER);
        static final Parameter<String> CATEGORY = Parameter.optional("category", Component.text("Only quests in this board category"), ParameterTypes.STRING, null);
        static final Parameter<Boolean> COUNT_REPEATS = Parameter.optional("count-repeats",
                Component.text("Count each repeat of a repeatable quest"), ParameterTypes.BOOLEAN, "false");

        private final ProgressLookup lookup;
        private final QuestRepository quests;

        CompletedCount(ProgressLookup lookup, QuestRepository quests) {
            super("completed_count", "Completed N quests", "minecraft:bookshelf", AMOUNT, CATEGORY, COUNT_REPEATS);
            this.lookup = lookup;
            this.quests = quests;
        }

        @Override
        public boolean test(Player player, QuestHolder holder, ParameterValues values) {
            String category = values.getOrNull(CATEGORY);
            boolean repeats = values.get(COUNT_REPEATS);
            int total = 0;
            for (QuestProgress progress : lookup.all(player)) {
                if (progress.completions() == 0) {
                    continue;
                }
                if (category != null) {
                    Optional<QuestDefinition> quest = quests.get(progress.questId());
                    if (quest.isEmpty() || !quest.get().settings().board().category().equals(category)) {
                        continue;
                    }
                }
                total += repeats ? progress.completions() : 1;
            }
            return total >= values.get(AMOUNT);
        }

        @Override
        public Component describe(ParameterValues values) {
            String category = values.raw(CATEGORY.id()).map(c -> " " + c).orElse("");
            return Component.text("Complete " + values.raw(AMOUNT.id()).orElse("?") + category + " quests");
        }
    }

    static final class QuestStageReached extends BaseType implements ConditionType {
        static final Parameter<String> QUEST = Parameter.required("quest", Component.text("Quest id"), ParameterTypes.STRING);
        static final Parameter<String> STAGE = Parameter.required("stage", Component.text("Stage id"), ParameterTypes.STRING);
        static final Parameter<Boolean> CURRENT_ONLY = Parameter.optional("current-only",
                Component.text("Only while the quest is on that stage right now"), ParameterTypes.BOOLEAN, "false");

        private final ProgressLookup lookup;

        QuestStageReached(ProgressLookup lookup) {
            super("quest_stage", "Reached quest stage", "minecraft:lectern", QUEST, STAGE, CURRENT_ONLY);
            this.lookup = lookup;
        }

        @Override
        public boolean test(Player player, QuestHolder holder, ParameterValues values) {
            String stage = values.get(STAGE);
            if (values.get(CURRENT_ONLY)) {
                return lookup.any(player, values.get(QUEST),
                        p -> p.status().isActive() && p.currentStage().filter(stage::equals).isPresent());
            }
            return lookup.any(player, values.get(QUEST), p -> p.visitedStages().contains(stage));
        }

        @Override
        public Component describe(ParameterValues values) {
            return Component.text("Reach " + values.raw(QUEST.id()).orElse("?") + " → " + values.raw(STAGE.id()).orElse("?"));
        }
    }

    static final class QuestBranch extends BaseType implements ConditionType {
        static final Parameter<String> QUEST = Parameter.required("quest", Component.text("Quest id"), ParameterTypes.STRING);
        static final Parameter<String> STAGE = Parameter.required("stage", Component.text("Stage the branch leaves from"), ParameterTypes.STRING);
        static final Parameter<String> CHOICE = Parameter.required("choice",
                Component.text("Where it led: a stage id, @complete or @fail"), ParameterTypes.STRING);

        private final ProgressLookup lookup;

        QuestBranch(ProgressLookup lookup) {
            super("quest_branch", "Took a quest branch", "minecraft:oak_sign", QUEST, STAGE, CHOICE);
            this.lookup = lookup;
        }

        @Override
        public boolean test(Player player, QuestHolder holder, ParameterValues values) {
            String stage = values.get(STAGE);
            String choice = values.get(CHOICE);
            return lookup.any(player, values.get(QUEST), p -> choice.equals(p.transitionsTaken().get(stage)));
        }

        @Override
        public Component describe(ParameterValues values) {
            return Component.text("In " + values.raw(QUEST.id()).orElse("?") + ", went from "
                    + values.raw(STAGE.id()).orElse("?") + " to " + values.raw(CHOICE.id()).orElse("?"));
        }
    }

    static final class MinLevel extends BaseType implements ConditionType {
        static final Parameter<Integer> LEVEL = Parameter.required("level", Component.text("Minimum experience level"), ParameterTypes.INTEGER);

        MinLevel() {
            super("min_level", "Experience level", "minecraft:experience_bottle", LEVEL);
        }

        @Override
        public boolean test(Player player, QuestHolder holder, ParameterValues values) {
            return player.getLevel() >= values.get(LEVEL);
        }

        @Override
        public Component describe(ParameterValues values) {
            return Component.text("Level " + values.raw(LEVEL.id()).orElse("?") + " or higher");
        }
    }
}
