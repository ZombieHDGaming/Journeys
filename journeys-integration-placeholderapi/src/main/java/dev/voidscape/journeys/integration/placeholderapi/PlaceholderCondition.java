package dev.voidscape.journeys.integration.placeholderapi;

import dev.voidscape.journeys.api.holder.QuestHolder;
import dev.voidscape.journeys.api.type.ConditionType;
import dev.voidscape.journeys.api.type.Parameter;
import dev.voidscape.journeys.api.type.ParameterType;
import dev.voidscape.journeys.api.type.ParameterTypes;
import dev.voidscape.journeys.api.type.ParameterValues;
import java.util.List;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Compares any PlaceholderAPI placeholder against a value, e.g. {@code %vault_eco_balance% >= 1000}. */
final class PlaceholderCondition implements ConditionType {

    static final ParameterType<Comparison> OPERATOR = ParameterTypes.simple("operator",
            "one of = != > >= < <= contains", Key.key("minecraft:comparator"), Comparison::parse);

    static final Parameter<String> PLACEHOLDER = Parameter.required("placeholder",
            Component.text("Placeholder, e.g. %vault_eco_balance%"), ParameterTypes.STRING);
    static final Parameter<Comparison> OPERATION = Parameter.optional("operator", Component.text("Comparison"), OPERATOR, "=");
    static final Parameter<String> VALUE = Parameter.required("value", Component.text("Value to compare with"), ParameterTypes.STRING);

    @Override
    public Key key() {
        return Key.key("journeys", "placeholder");
    }

    @Override
    public Component displayName() {
        return Component.text("Placeholder check");
    }

    @Override
    public List<Component> description() {
        return List.of(Component.text("Compare a PlaceholderAPI value"));
    }

    @Override
    public Key icon() {
        return Key.key("minecraft:name_tag");
    }

    @Override
    public List<Parameter<?>> parameters() {
        return List.of(PLACEHOLDER, OPERATION, VALUE);
    }

    @Override
    public boolean test(Player player, QuestHolder holder, ParameterValues values) {
        String actual = PlaceholderAPI.setPlaceholders(player, values.get(PLACEHOLDER));
        String expected = PlaceholderAPI.setPlaceholders(player, values.get(VALUE));
        return values.get(OPERATION).test(actual, expected);
    }

    @Override
    public Component describe(ParameterValues values) {
        return Component.text(values.raw(PLACEHOLDER.id()).orElse("?") + " " + values.get(OPERATION).symbol + " "
                + values.raw(VALUE.id()).orElse("?"));
    }
}
