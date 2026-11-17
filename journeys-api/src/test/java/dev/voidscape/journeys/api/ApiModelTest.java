package dev.voidscape.journeys.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidscape.journeys.api.quest.QuestIds;
import dev.voidscape.journeys.api.quest.TransitionTarget;
import dev.voidscape.journeys.api.type.Parameter;
import dev.voidscape.journeys.api.type.ParameterTypes;
import dev.voidscape.journeys.api.type.ParameterValues;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class ApiModelTest {

    @Test
    void transitionTargetsRoundTrip() {
        assertSame(TransitionTarget.COMPLETE, TransitionTarget.parse("@complete"));
        assertSame(TransitionTarget.FAIL, TransitionTarget.parse("@fail"));
        TransitionTarget stage = TransitionTarget.parse("gather");
        assertInstanceOf(TransitionTarget.ToStage.class, stage);
        assertEquals("gather", stage.serialize());
        assertEquals("@complete", TransitionTarget.COMPLETE.serialize());
    }

    @Test
    void questIdsRejectReservedAndUnsafeCharacters() {
        assertTrue(QuestIds.isValid("the_woodcutter-2"));
        assertFalse(QuestIds.isValid("@complete"));
        assertFalse(QuestIds.isValid("../escape"));
        assertFalse(QuestIds.isValid("Upper"));
        assertFalse(QuestIds.isValid(""));
        assertFalse(QuestIds.isValid("x".repeat(65)));
    }

    @Test
    void parameterValuesParseLazilyAndFallBackToDefaults() {
        Parameter<Integer> amount = Parameter.optional("amount", Component.text("Amount"), ParameterTypes.POSITIVE_INTEGER, "3");
        Parameter<String> name = Parameter.optional("name", Component.text("Name"), ParameterTypes.STRING, null);
        Parameter<List<Integer>> list = Parameter.required("list", Component.text("List"), ParameterTypes.listOf(ParameterTypes.INTEGER));

        ParameterValues values = ParameterValues.of(Map.of("list", "1, 2,3"));
        assertEquals(3, values.get(amount));
        assertNull(values.getOrNull(name));
        assertEquals(List.of(1, 2, 3), values.get(list));
        assertThrows(IllegalStateException.class, () -> values.get(name));
    }

    @Test
    void invalidRawValuesAreReported() {
        Parameter<Integer> amount = Parameter.required("amount", Component.text("Amount"), ParameterTypes.POSITIVE_INTEGER);
        ParameterValues values = ParameterValues.of(Map.of("amount", "0"));
        assertThrows(IllegalStateException.class, () -> values.get(amount));
    }

    @Test
    void booleanParsingAcceptsCommonForms() {
        assertTrue(ParameterTypes.BOOLEAN.parse("yes"));
        assertFalse(ParameterTypes.BOOLEAN.parse("OFF"));
        assertThrows(IllegalArgumentException.class, () -> ParameterTypes.BOOLEAN.parse("maybe"));
    }
}
