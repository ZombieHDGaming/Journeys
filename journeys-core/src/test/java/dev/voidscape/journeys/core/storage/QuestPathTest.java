package dev.voidscape.journeys.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuestPathTest {

    @Test
    void roundTrips() {
        Map<String, String> taken = new LinkedHashMap<>();
        taken.put("gather", "sell");
        taken.put("sell", "@complete");
        String encoded = QuestPath.encode(List.of("gather", "sell"), taken);
        assertEquals("gather,sell|gather=sell,sell=@complete", encoded);

        QuestPath.Path path = QuestPath.decode(encoded);
        assertEquals(List.of("gather", "sell"), path.visited());
        assertEquals(taken, path.taken());
    }

    @Test
    void emptyAndLegacyValuesDecodeToNothing() {
        assertTrue(QuestPath.decode(null).visited().isEmpty(), "rows written before migration 2 have no path");
        assertTrue(QuestPath.decode("").taken().isEmpty());
        assertEquals(List.of(), QuestPath.decode("|").visited());
        assertEquals(List.of("a"), QuestPath.decode("a|").visited());
        assertEquals(Map.of(), QuestPath.decode("|broken,=x,y=").taken(), "malformed pairs are skipped");
    }
}
