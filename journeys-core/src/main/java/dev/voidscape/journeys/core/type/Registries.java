package dev.voidscape.journeys.core.type;

import dev.voidscape.journeys.api.type.ConditionType;
import dev.voidscape.journeys.api.type.ElementType;
import dev.voidscape.journeys.api.type.ObjectiveType;
import dev.voidscape.journeys.api.type.RewardType;
import dev.voidscape.journeys.api.type.TypeRegistry;

/** The three type registries, grouped for convenience. */
public record Registries(
        TypeRegistry<ObjectiveType> objectives,
        TypeRegistry<ConditionType> conditions,
        TypeRegistry<RewardType> rewards
) {

    /** Which registry an element belongs to; used by the generic editor menus. */
    public enum Kind {
        OBJECTIVE,
        CONDITION,
        REWARD
    }

    /** The registry for a kind, viewed through the common supertype. Read-only use only. */
    @SuppressWarnings("unchecked")
    public TypeRegistry<? extends ElementType> of(Kind kind) {
        return switch (kind) {
            case OBJECTIVE -> objectives;
            case CONDITION -> conditions;
            case REWARD -> rewards;
        };
    }
}
