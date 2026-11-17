package dev.voidscape.journeys.core.block;

import dev.voidscape.journeys.api.block.PlacedBlocks;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Stores player-placed block marks in chunk persistent data.
 *
 * <p>Marks are read lazily the first time a chunk is touched and cached until it unloads. Changes are written back
 * when the chunk unloads, when the world autosaves, and on plugin disable, never on every block event.</p>
 *
 * <p>All methods run on the server thread.</p>
 */
public final class PlacedBlockTracker implements PlacedBlocks, Listener {

    private static final int FORMAT_VERSION = 1;

    private final Plugin plugin;
    private final NamespacedKey versionKey;
    private final NamespacedKey positionsKey;
    private final NamespacedKey ownersKey;
    private final NamespacedKey ownerIdsKey;
    private final NamespacedKey recycledKey;
    private final Map<UUID, Long2ObjectMap<ChunkMarks>> cache = new HashMap<>();

    /** Placed, non-natural blocks broken this tick, so their drops can be flagged in BlockDropItemEvent. */
    private final Set<Block> brokenThisTick = new HashSet<>();

    public PlacedBlockTracker(Plugin plugin) {
        this.plugin = plugin;
        this.versionKey = new NamespacedKey(plugin, "placed_version");
        this.positionsKey = new NamespacedKey(plugin, "placed_positions");
        this.ownersKey = new NamespacedKey(plugin, "placed_owners");
        this.ownerIdsKey = new NamespacedKey(plugin, "placed_owner_ids");
        this.recycledKey = new NamespacedKey(plugin, "recycled_drop");
    }

    // ------------------------------------------------------------------ PlacedBlocks

    @Override
    public boolean isPlayerPlaced(Block block) {
        return marks(block).contains(position(block));
    }

    @Override
    public Optional<UUID> placer(Block block) {
        return Optional.ofNullable(marks(block).placer(position(block)));
    }

    @Override
    public boolean isRecycledDrop(Item item) {
        return item.getPersistentDataContainer().has(recycledKey);
    }

    // ------------------------------------------------------------------ marking

    private void mark(Block block, UUID placer) {
        marks(block).mark(position(block), placer);
    }

    private @Nullable UUID unmark(Block block) {
        return marks(block).remove(position(block));
    }

    private static int position(Block block) {
        return ChunkMarks.pack(block.getX(), block.getY(), block.getZ(), block.getWorld().getMinHeight());
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
    }

    private ChunkMarks marks(Block block) {
        Long2ObjectMap<ChunkMarks> world = cache.computeIfAbsent(block.getWorld().getUID(), id -> new Long2ObjectOpenHashMap<>());
        long key = chunkKey(block.getX() >> 4, block.getZ() >> 4);
        ChunkMarks marks = world.get(key);
        if (marks == null) {
            marks = read(block.getChunk().getPersistentDataContainer());
            world.put(key, marks);
        }
        return marks;
    }

    private ChunkMarks read(PersistentDataContainer pdc) {
        Integer version = pdc.get(versionKey, PersistentDataType.INTEGER);
        if (version == null) {
            return new ChunkMarks();
        }
        if (version != FORMAT_VERSION) {
            plugin.getLogger().warning("Unknown placed-block data version " + version + "; ignoring it for this chunk");
            return new ChunkMarks();
        }
        int[] positions = pdc.getOrDefault(positionsKey, PersistentDataType.INTEGER_ARRAY, new int[0]);
        int[] owners = pdc.getOrDefault(ownersKey, PersistentDataType.INTEGER_ARRAY, new int[0]);
        long[] ownerIds = pdc.getOrDefault(ownerIdsKey, PersistentDataType.LONG_ARRAY, new long[0]);
        return ChunkMarks.decode(new ChunkMarks.Encoded(positions, owners, ownerIds));
    }

    private void write(Chunk chunk, ChunkMarks marks) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (marks.isEmpty()) {
            pdc.remove(versionKey);
            pdc.remove(positionsKey);
            pdc.remove(ownersKey);
            pdc.remove(ownerIdsKey);
        } else {
            ChunkMarks.Encoded encoded = marks.encode();
            pdc.set(versionKey, PersistentDataType.INTEGER, FORMAT_VERSION);
            pdc.set(positionsKey, PersistentDataType.INTEGER_ARRAY, encoded.positions());
            pdc.set(ownersKey, PersistentDataType.INTEGER_ARRAY, encoded.owners());
            pdc.set(ownerIdsKey, PersistentDataType.LONG_ARRAY, encoded.ownerIds());
        }
        marks.dirty = false;
    }

    private void flush(World world) {
        Long2ObjectMap<ChunkMarks> chunks = cache.get(world.getUID());
        if (chunks == null) {
            return;
        }
        for (Long2ObjectMap.Entry<ChunkMarks> entry : chunks.long2ObjectEntrySet()) {
            ChunkMarks marks = entry.getValue();
            int chunkX = (int) entry.getLongKey();
            int chunkZ = (int) (entry.getLongKey() >> 32);
            if (marks.dirty && world.isChunkLoaded(chunkX, chunkZ)) {
                write(world.getChunkAt(chunkX, chunkZ), marks);
            }
        }
    }

    /** Writes every pending change. Called on plugin disable, while chunks are still loaded. */
    public void flushAll() {
        for (World world : Bukkit.getWorlds()) {
            flush(world);
        }
        cache.clear();
    }

    // ------------------------------------------------------------------ placing and breaking

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        UUID placer = event.getPlayer().getUniqueId();
        if (event instanceof BlockMultiPlaceEvent multi) {
            // Beds, doors and other multi-block placements.
            for (BlockState state : multi.getReplacedBlockStates()) {
                mark(state.getBlock(), placer);
            }
        } else {
            mark(event.getBlockPlaced(), placer);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isNatural(block)) {
            if (brokenThisTick.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, brokenThisTick::clear);
            }
            brokenThisTick.add(block);
        }
        unmark(block);
    }

    /** Flags drops of placed blocks so collect objectives can ignore them. Fires right after the break. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        if (brokenThisTick.remove(event.getBlock())) {
            for (Item item : event.getItems()) {
                item.getPersistentDataContainer().set(recycledKey, PersistentDataType.BOOLEAN, true);
            }
        }
    }

    // ------------------------------------------------------------------ blocks that move

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        move(event.getBlocks(), facing(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        BlockFace facing = facing(event.getBlock());
        if (facing != null) {
            move(event.getBlocks(), facing.getOppositeFace());
        }
    }

    private static @Nullable BlockFace facing(Block piston) {
        return piston.getBlockData() instanceof Directional directional ? directional.getFacing() : null;
    }

    /** Moves marks with blocks pushed or pulled by a piston. Unmark everything first, as blocks may shift into each other. */
    private void move(List<Block> blocks, @Nullable BlockFace direction) {
        if (direction == null || blocks.isEmpty()) {
            return;
        }
        List<Block> targets = new ArrayList<>();
        List<UUID> placers = new ArrayList<>();
        for (Block block : blocks) {
            UUID placer = unmark(block);
            if (placer != null) {
                targets.add(block.getRelative(direction));
                placers.add(placer);
            }
        }
        for (int i = 0; i < targets.size(); i++) {
            mark(targets.get(i), placers.get(i));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFallingBlock(EntityChangeBlockEvent event) {
        // A placed block turning into a falling block, or an enderman taking it. The block that lands is new.
        if (!event.getTo().equals(event.getBlock().getType())) {
            unmark(event.getBlock());
        }
    }

    // ------------------------------------------------------------------ blocks removed without a player breaking them

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::unmark);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::unmark);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        unmark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        unmark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        unmark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        // Water or lava washing away a placed torch, flower, rail and so on.
        unmark(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent event) {
        // A player-planted sapling or mushroom becomes a natural tree: its logs should count when chopped.
        for (BlockState state : event.getBlocks()) {
            unmark(state.getBlock());
        }
    }

    // ------------------------------------------------------------------ persistence

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Long2ObjectMap<ChunkMarks> world = cache.get(event.getWorld().getUID());
        if (world == null) {
            return;
        }
        ChunkMarks marks = world.remove(chunkKey(event.getChunk().getX(), event.getChunk().getZ()));
        if (marks != null && marks.dirty) {
            write(event.getChunk(), marks);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        flush(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        flush(event.getWorld());
        cache.remove(event.getWorld().getUID());
    }
}
