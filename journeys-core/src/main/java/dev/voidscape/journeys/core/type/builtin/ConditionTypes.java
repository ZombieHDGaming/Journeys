package dev.voidscape.journeys.core.type.builtin;

import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.progress.QuestProgress;
import dev.voidscape.journeys.api.progress.QuestService;
import dev.voidscape.journeys.api.type.ConditionType;
import dev.voidscape.journeys.api.type.Parameter;
import dev.voidscape.journeys.api.type.ParameterTypes;
import dev.voidscape.journeys.api.type.ParameterValues;
import java.util.List;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Built-in condition types. */
public final class ConditionTypes {

    private ConditionTypes() {
    }

    public static List<ConditionType> all(Supplier<QuestService> questService) {
        return List.of(new Permission(), new QuestCompleted(questService), new MinLevel());
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

        private final Supplier<QuestService> questService;

        QuestCompleted(Supplier<QuestService> questService) {
            super("quest_completed", "Completed quest", "minecraft:writable_book", QUEST, TIMES);
            this.questService = questService;
        }

        @Override
        public boolean test(Player player, QuestHolder holder, ParameterValues values) {
            int completions = questService.get().progress(holder, values.get(QUEST))
                    .map(QuestProgress::completions)
                    .orElse(0);
            return completions >= values.get(TIMES);
        }

        @Override
        public Component describe(ParameterValues values) {
            return Component.text("Completed quest " + values.raw(QUEST.id()).orElse("?"));
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
