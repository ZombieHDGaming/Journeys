package dev.voidscape.journeys.core.quest;

import dev.voidscape.journeys.api.quest.ConfiguredCondition;
import dev.voidscape.journeys.api.quest.ConfiguredObjective;
import dev.voidscape.journeys.api.quest.ConfiguredReward;
import dev.voidscape.journeys.api.type.ElementType;
import dev.voidscape.journeys.api.type.ObjectiveType;
import dev.voidscape.journeys.api.type.ParameterValues;
import dev.voidscape.journeys.api.type.TypeRegistry;
import dev.voidscape.journeys.core.text.Text;
import dev.voidscape.journeys.core.type.Registries;
import java.util.Optional;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/** Turns configured quest elements into player-facing text. */
public final class QuestTexts {

    private final Registries registries;

    public QuestTexts(Registries registries) {
        this.registries = registries;
    }

    public Component objective(ConfiguredObjective objective) {
        if (objective.description() != null) {
            return Text.parse(objective.description());
        }
        return describe(registries.objectives(), objective.type(), objective.params());
    }

    public Component condition(ConfiguredCondition condition) {
        Component text = describe(registries.conditions(), condition.type(), condition.params());
        return condition.negate() ? Component.text("NOT ").append(text) : text;
    }

    public Component reward(ConfiguredReward reward) {
        return describe(registries.rewards(), reward.type(), reward.params());
    }

    /** The goal used for progress: the configured amount, or 1 for non-countable types. */
    public int goal(ConfiguredObjective objective) {
        Optional<ObjectiveType> type = registries.objectives().get(objective.type());
        return type.isPresent() && !type.get().countable() ? 1 : objective.amount();
    }

    public static <T extends ElementType> Component describe(TypeRegistry<T> registry, Key key, ParameterValues values) {
        Optional<T> type = registry.get(key);
        if (type.isEmpty()) {
            return Component.text(key.asString());
        }
        try {
            return type.get().describe(values);
        } catch (RuntimeException e) {
            // Incomplete parameters while editing; fall back to the type name.
            return type.get().displayName();
        }
    }
}
