package dev.voidscape.journeys.core.block;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Player-placed block positions within one chunk, with who placed each one.
 *
 * <p>Positions are packed into an int: {@code (y - minY) << 8 | x << 4 | z}, with x and z relative to the chunk.
 * Placers are stored once per chunk in an owner table, so a large build costs two ints per block plus one UUID per
 * builder.</p>
 */
final class ChunkMarks {

    /** Serialized form, stored as three arrays in the chunk's persistent data. */
    record Encoded(int[] positions, int[] owners, long[] ownerIds) {
    }

    private final Int2IntOpenHashMap placers = new Int2IntOpenHashMap();
    private final List<UUID> ownerTable = new ArrayList<>();
    private final Object2IntOpenHashMap<UUID> ownerIndex = new Object2IntOpenHashMap<>();
    boolean dirty;

    ChunkMarks() {
        placers.defaultReturnValue(-1);
        ownerIndex.defaultReturnValue(-1);
    }

    static int pack(int x, int y, int z, int minY) {
        return ((y - minY) << 8) | ((x & 15) << 4) | (z & 15);
    }

    boolean isEmpty() {
        return placers.isEmpty();
    }

    int size() {
        return placers.size();
    }

    boolean contains(int position) {
        return placers.containsKey(position);
    }

    @Nullable UUID placer(int position) {
        int index = placers.get(position);
        return index < 0 ? null : ownerTable.get(index);
    }

    void mark(int position, UUID placer) {
        int index = ownerIndex.getInt(placer);
        if (index < 0) {
            index = ownerTable.size();
            ownerTable.add(placer);
            ownerIndex.put(placer, index);
        }
        if (placers.put(position, index) != index) {
            dirty = true;
        }
    }

    /** Removes a mark and returns who placed the block, or null if it was not marked. */
    @Nullable UUID remove(int position) {
        int index = placers.remove(position);
        if (index < 0) {
            return null;
        }
        dirty = true;
        return ownerTable.get(index);
    }

    /** Serializes the marks, dropping owners that no longer have any blocks. */
    Encoded encode() {
        int[] positions = new int[placers.size()];
        int[] owners = new int[placers.size()];
        Int2IntOpenHashMap remap = new Int2IntOpenHashMap();
        remap.defaultReturnValue(-1);
        List<UUID> usedOwners = new ArrayList<>();
        int i = 0;
        for (Int2IntMap.Entry entry : placers.int2IntEntrySet()) {
            int newIndex = remap.get(entry.getIntValue());
            if (newIndex < 0) {
                newIndex = usedOwners.size();
                usedOwners.add(ownerTable.get(entry.getIntValue()));
                remap.put(entry.getIntValue(), newIndex);
            }
            positions[i] = entry.getIntKey();
            owners[i] = newIndex;
            i++;
        }
        long[] ownerIds = new long[usedOwners.size() * 2];
        for (int o = 0; o < usedOwners.size(); o++) {
            ownerIds[o * 2] = usedOwners.get(o).getMostSignificantBits();
            ownerIds[o * 2 + 1] = usedOwners.get(o).getLeastSignificantBits();
        }
        return new Encoded(positions, owners, ownerIds);
    }

    /**
     * Rebuilds marks from their serialized form. Corrupt entries (mismatched lengths or bad owner indexes) are skipped
     * rather than failing the whole chunk.
     */
    static ChunkMarks decode(Encoded encoded) {
        ChunkMarks marks = new ChunkMarks();
        long[] ids = encoded.ownerIds();
        List<UUID> owners = new ArrayList<>();
        for (int o = 0; o + 1 < ids.length; o += 2) {
            owners.add(new UUID(ids[o], ids[o + 1]));
        }
        int count = Math.min(encoded.positions().length, encoded.owners().length);
        for (int i = 0; i < count; i++) {
            int owner = encoded.owners()[i];
            if (owner >= 0 && owner < owners.size()) {
                marks.mark(encoded.positions()[i], owners.get(owner));
            }
        }
        marks.dirty = false;
        return marks;
    }
}
