package dev.voidscape.journeys.api.block;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;

/**
 * Remembers which blocks were placed by players, so objectives can ignore place-and-break farming.
 *
 * <p>Marks are stored in each chunk's persistent data and survive restarts. They follow blocks moved by pistons and
 * are cleared when a block is removed (broken, exploded, burnt, washed away, grown into a tree, and so on).</p>
 *
 * <h2>Timing</h2>
 * A broken block's mark is cleared at {@link org.bukkit.event.EventPriority#MONITOR}. Listeners that need to know
 * whether a broken block was placed must run at {@code HIGHEST} or earlier.
 */
public interface PlacedBlocks {

    /** Whether the block at this position was placed by a player. */
    boolean isPlayerPlaced(Block block);

    /** The player who placed the block, if it was placed by one. */
    Optional<UUID> placer(Block block);

    /**
     * Whether breaking this block should count as gathering a natural block: it was not placed by a player, or it is
     * a fully grown crop (players plant crops, but harvesting them is legitimate).
     */
    default boolean isNatural(Block block) {
        if (!isPlayerPlaced(block)) {
            return true;
        }
        return block.getBlockData() instanceof Ageable crop && crop.getAge() == crop.getMaximumAge();
    }

    /**
     * Whether a dropped item came from breaking a block that was not {@link #isNatural(Block) natural}, i.e. from
     * place-and-break recycling.
     */
    boolean isRecycledDrop(Item item);
}
