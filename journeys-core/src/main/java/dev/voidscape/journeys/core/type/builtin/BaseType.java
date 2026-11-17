package dev.voidscape.journeys.core.type.builtin;

import dev.voidscape.journeys.api.type.ElementType;
import dev.voidscape.journeys.api.type.Parameter;
import dev.voidscape.journeys.api.type.ParameterValues;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/** Shared plumbing for the built-in types. */
abstract class BaseType implements ElementType {

    static final String NAMESPACE = "journeys";

    private final Key key;
    private final Component displayName;
    private final Key icon;
    private final List<Parameter<?>> parameters;

    BaseType(String id, String displayName, String icon, Parameter<?>... parameters) {
        this.key = Key.key(NAMESPACE, id);
        this.displayName = Component.text(displayName);
        this.icon = Key.key(icon);
        this.parameters = List.of(parameters);
    }

    @Override
    public Key key() {
        return key;
    }

    @Override
    public Component displayName() {
        return displayName;
    }

    @Override
    public Key icon() {
        return icon;
    }

    @Override
    public List<Parameter<?>> parameters() {
        return parameters;
    }

    /** Matches when the parameter is unset (meaning "any") or the list contains the value. */
    static <T> boolean anyOrContains(ParameterValues values, Parameter<List<T>> parameter, T value) {
        List<T> allowed = values.getOrNull(parameter);
        return allowed == null || allowed.contains(value);
    }
}
