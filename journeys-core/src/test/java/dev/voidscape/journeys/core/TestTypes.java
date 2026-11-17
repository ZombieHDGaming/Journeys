package dev.voidscape.journeys.core;

import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.type.ConditionType;
import dev.voidscape.journeys.api.type.ElementType;
import dev.voidscape.journeys.api.type.ObjectiveType;
import dev.voidscape.journeys.api.type.Parameter;
import dev.voidscape.journeys.api.type.ParameterTypes;
import dev.voidscape.journeys.api.type.ParameterValues;
import dev.voidscape.journeys.api.type.RewardType;
import dev.voidscape.journeys.core.type.Registries;
import dev.voidscape.journeys.core.type.SimpleTypeRegistry;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Server-free stand-ins for real types, using only parameter types that do not touch Bukkit registries. */
public final class TestTypes {

    public static final Parameter<String> TARGET = Parameter.required("target", Component.text("Target"), ParameterTypes.STRING);
    public static final Parameter<Integer> LEVEL = Parameter.required("level", Component.text("Level"), ParameterTypes.INTEGER);

    private TestTypes() {
    }

    public static Registries registries() {
        Registries registries = new Registries(
                new SimpleTypeRegistry<>("objective", t -> {}, t -> {}),
                new SimpleTypeRegistry<>("condition", t -> {}, t -> {}),
                new SimpleTypeRegistry<>("reward", t -> {}, t -> {}));
        registries.objectives().register(new TestObjective("journeys:test_objective", true));
        registries.objectives().register(new TestObjective("journeys:test_once", false));
        registries.conditions().register(new TestCondition());
        registries.rewards().register(new TestReward());
        return registries;
    }

    abstract static class Base implements ElementType {
        private final Key key;

        Base(String key) {
            this.key = Key.key(key);
        }

        @Override
        public Key key() {
            return key;
        }

        @Override
        public Component displayName() {
            return Component.text(key.value());
        }

        @Override
        public Key icon() {
            return Key.key("minecraft:stone");
        }
    }

    static final class TestObjective extends Base implements ObjectiveType {
        private final boolean countable;

        TestObjective(String key, boolean countable) {
            super(key);
            this.countable = countable;
        }

        @Override
        public List<Parameter<?>> parameters() {
            return List.of(TARGET);
        }

        @Override
        public boolean countable() {
            return countable;
        }
    }

    static final class TestCondition extends Base implements ConditionType {
        TestCondition() {
            super("journeys:test_condition");
        }

        @Override
        public List<Parameter<?>> parameters() {
            return List.of(LEVEL);
        }

        @Override
        public boolean test(Player player, QuestHolder holder, ParameterValues values) {
            return true;
        }
    }

    static final class TestReward extends Base implements RewardType {
        TestReward() {
            super("journeys:test_reward");
        }

        @Override
        public List<Parameter<?>> parameters() {
            return List.of();
        }

        @Override
        public void grant(Player player, ParameterValues values) {
        }
    }
}
