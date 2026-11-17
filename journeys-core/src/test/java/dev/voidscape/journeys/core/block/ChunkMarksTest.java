package dev.voidscape.journeys.core.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkMarksTest {

    private static final int MIN_Y = -64;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void packingIsUniqueAcrossTheWholeChunkHeight() {
        Set<Integer> seen = new HashSet<>();
        for (int y = MIN_Y; y < 320; y += 7) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    assertTrue(seen.add(ChunkMarks.pack(x, y, z, MIN_Y)));
                }
            }
        }
        // World coordinates outside 0..15, including negatives, map to the same in-chunk slot.
        assertEquals(ChunkMarks.pack(3, 10, 5, MIN_Y), ChunkMarks.pack(-13, 10, 21, MIN_Y));
    }

    @Test
    void marksRecordPlacersAndCanBeRemoved() {
        ChunkMarks marks = new ChunkMarks();
        int a = ChunkMarks.pack(1, 70, 2, MIN_Y);
        int b = ChunkMarks.pack(1, 71, 2, MIN_Y);
        marks.mark(a, alice);
        marks.mark(b, bob);

        assertEquals(alice, marks.placer(a));
        assertTrue(marks.dirty);
        assertEquals(bob, marks.remove(b));
        assertNull(marks.remove(b), "removing twice is a no-op");
        assertFalse(marks.contains(b));
    }

    @Test
    void encodeDecodeRoundTripsAndDropsUnusedOwners() {
        ChunkMarks marks = new ChunkMarks();
        for (int y = 0; y < 50; y++) {
            marks.mark(ChunkMarks.pack(y % 16, y, 0, MIN_Y), y % 2 == 0 ? alice : bob);
        }
        int lonely = ChunkMarks.pack(15, 200, 15, MIN_Y);
        UUID carol = UUID.randomUUID();
        marks.mark(lonely, carol);
        marks.remove(lonely);

        ChunkMarks.Encoded encoded = marks.encode();
        assertEquals(4, encoded.ownerIds().length, "carol has no blocks left, so only alice and bob are stored");

        ChunkMarks decoded = ChunkMarks.decode(encoded);
        assertEquals(50, decoded.size());
        assertFalse(decoded.dirty, "freshly loaded marks have nothing to save");
        for (int y = 0; y < 50; y++) {
            assertEquals(y % 2 == 0 ? alice : bob, decoded.placer(ChunkMarks.pack(y % 16, y, 0, MIN_Y)));
        }
    }

    @Test
    void corruptEntriesAreSkipped() {
        long[] owners = {alice.getMostSignificantBits(), alice.getLeastSignificantBits()};
        ChunkMarks decoded = ChunkMarks.decode(new ChunkMarks.Encoded(new int[]{1, 2, 3}, new int[]{0, 5}, owners));
        assertEquals(1, decoded.size());
        assertEquals(alice, decoded.placer(1));
    }
}
