package dev.voidscape.journeys.core.type.builtin;

import dev.voidscape.journeys.api.block.PlacedBlocks;
import dev.voidscape.journeys.api.type.ObjectiveTracker;
import dev.voidscape.journeys.api.type.ObjectiveType;
import dev.voidscape.journeys.api.type.Parameter;
import dev.voidscape.journeys.api.type.ParameterTypes;
import dev.voidscape.journeys.api.type.ParameterValues;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.translation.Translatable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Built-in objective types. Each one is its own listener and reports to the {@link ObjectiveTracker}. */
public final class ObjectiveTypes {

    private ObjectiveTypes() {
    }

    public static List<ObjectiveType> all(ObjectiveTracker tracker, PlacedBlocks placed) {
        return List.of(
                new KillEntity(tracker),
                new BreakBlock(tracker, placed),
                new PlaceBlock(tracker, placed),
                new CollectItem(tracker, placed),
                new CraftItem(tracker),
                new Fish(tracker),
                new ReachLocation(tracker)
        );
    }

    /** "Kill zombie, husk" with translated names, or the fallback when no filter is set. */
    static Component names(@Nullable List<? extends Translatable> values, String any) {
        if (values == null || values.isEmpty()) {
            return Component.text(any);
        }
        return Component.join(JoinConfiguration.commas(true), values.stream().map(Component::translatable).toList());
    }

    abstract static class TrackedObjective extends BaseType implements ObjectiveType, Listener {
        final ObjectiveTracker tracker;

        TrackedObjective(ObjectiveTracker tracker, String id, String name, String icon, Parameter<?>... parameters) {
            super(id, name, icon, parameters);
            this.tracker = tracker;
        }
    }

    static final class KillEntity extends TrackedObjective {
        static final Parameter<List<EntityType>> ENTITY = Parameter.optional("entity", Component.text("Entity types (empty = any)"),
                ParameterTypes.listOf(ParameterTypes.ENTITY_TYPE), null);

        KillEntity(ObjectiveTracker tracker) {
            super(tracker, "kill_entity", "Kill entities", "minecraft:iron_sword", ENTITY);
        }

        @Override
        public Component describe(ParameterValues values) {
            return Component.text("Kill ").append(names(values.getOrNull(ENTITY), "any mob"));
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDeath(EntityDeathEvent event) {
            Player killer = event.getEntity().getKiller();
            if (killer != null) {
                EntityType type = event.getEntityType();
                tracker.progress(killer, this, v -> anyOrContains(v, ENTITY, type), 1);
            }
        }
    }

    static final class BreakBlock extends TrackedObjective {
        static final Parameter<List<Material>> BLOCK = Parameter.optional("block", Component.text("Blocks (empty = any)"),
                ParameterTypes.listOf(ParameterTypes.BLOCK), null);

        static final Parameter<Boolean> COUNT_PLACED = Parameter.optional("count-placed",
                Component.text("Count blocks placed by players"), ParameterTypes.BOOLEAN, "false");

        private final PlacedBlocks placed;

        BreakBlock(ObjectiveTracker tracker, PlacedBlocks placed) {
            super(tracker, "break_block", "Break blocks", "minecraft:iron_pickaxe", BLOCK, COUNT_PLACED);
            this.placed = placed;
        }

        @Override
        public Component describe(ParameterValues values) {
            return Component.text("Break ").append(names(values.getOrNull(BLOCK), "any block"));
        }

        /** HIGHEST, not MONITOR: the placed-block mark is cleared at MONITOR. Nothing here changes the event. */
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            Block block = event.getBlock();
            Material type = block.getType();
            boolean natural = placed.isNatural(block);
            tracker.progress(event.getPlayer(), this, v -> (natural || v.get(COUNT_PLACED)) && anyOrContains(v, BLOCK, type), 1);
        }
    }

    static final class PlaceBlock extends TrackedObjective {
        static final Parameter<List<Material>> BLOCK = Parameter.optional("block", Component.text("Blocks (empty = any)"),
                ParameterTypes.listOf(ParameterTypes.BLOCK), null);

        static final Parameter<Boolean> SUBTRACT_ON_BREAK = Parameter.optional("subtract-on-break",
                Component.text("Breaking a placed block takes progress back"), ParameterTypes.BOOLEAN, "true");

        private final PlacedBlocks placed;

        PlaceBlock(ObjectiveTracker tracker, PlacedBlocks placed) {
            super(tracker, "place_block", "Place blocks", "minecraft:bricks", BLOCK, SUBTRACT_ON_BREAK);
            this.placed = placed;
        }

        @Override
        public Component describe(ParameterValues values) {
            return Component.text("Place ").append(names(values.getOrNull(BLOCK), "any block"));
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPlace(BlockPlaceEvent event) {
            Material type = event.getBlockPlaced().getType();
            tracker.progress(event.getPlayer(), this, v -> anyOrContains(v, BLOCK, type), 1);
        }

        /**
         * Counts placements net of removals, so placing and breaking the same block repeatedly gains nothing. Progress
         * is taken from whoever placed the block, if they are online. Harvesting a fully grown crop is not a removal.
         */
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            Block block = event.getBlock();
            if (placed.isNatural(block)) {
                return;
            }
            Player placer = placed.placer(block).map(Bukkit::getPlayer).orElse(null);
            if (placer != null) {
                Material type = block.getType();
                tracker.regress(placer, this, v -> v.get(SUBTRACT_ON_BREAK) && anyOrContains(v, BLOCK, type), 1);
            }
        }
    }

    static final class CollectItem extends TrackedObjective {
        static final Parameter<List<Material>> ITEM = Parameter.optional("item", Component.text("Items (empty = any)"),
                ParameterTypes.listOf(ParameterTypes.ITEM), null);

        static final Parameter<Boolean> COUNT_DROPPED = Parameter.optional("count-dropped",
                Component.text("Count items dropped by players or from placed blocks"), ParameterTypes.BOOLEAN, "false");

        private final PlacedBlocks placed;

        CollectItem(ObjectiveTracker tracker, PlacedBlocks placed) {
            super(tracker, "collect_item", "Pick up items", "minecraft:hopper", ITEM, COUNT_DROPPED);
            this.placed = placed;
        }

        @Override
        public Component describe(ParameterValues values) {
            return Component.text("Collect ").append(names(values.getOrNull(ITEM), "any item"));
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPickup(EntityPickupItemEvent event) {
            if (event.getEntity() instanceof Player player) {
                Item item = event.getItem();
                ItemStack stack = item.getItemStack();
                Material type = stack.getType();
                // Thrown by any player (not just this one), so items cannot be passed back and forth between friends.
                boolean recycled = item.getThrower() != null || placed.isRecycledDrop(item);
                tracker.progress(player, this, v -> (!recycled || v.get(COUNT_DROPPED)) && anyOrContains(v, ITEM, type), stack.getAmount());
            }
        }
    }

    static final class CraftItem extends TrackedObjective {
        static final Parameter<List<Material>> ITEM = Parameter.optional("item", Component.text("Items (empty = any)"),
                ParameterTypes.listOf(ParameterTypes.ITEM), null);

        CraftItem(ObjectiveTracker tracker) {
            super(tracker, "craft_item", "Craft items", "minecraft:crafting_table", ITEM);
        }

        @Override
        public Component describe(ParameterValues values) {
            return Component.text("Craft ").append(names(values.getOrNull(ITEM), "any item"));
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onCraft(CraftItemEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            ItemStack result = event.getRecipe().getResult();
            int crafted = result.getAmount();
            if (event.isShiftClick()) {
                // Shift-click crafts as many times as the smallest ingredient stack allows.
                int times = Integer.MAX_VALUE;
                for (ItemStack ingredient : event.getInventory().getMatrix()) {
                    if (ingredient != null && !ingredient.getType().isAir()) {
                        times = Math.min(times, ingredient.getAmount());
                    }
                }
                crafted *= times == Integer.MAX_VALUE ? 1 : times;
            }
            Material type = result.getType();
            tracker.progress(player, this, v -> anyOrContains(v, ITEM, type), crafted);
        }
    }

    static final class Fish extends TrackedObjective {
        static final Parameter<List<Material>> ITEM = Parameter.optional("item", Component.text("Catch (empty = anything)"),
                ParameterTypes.listOf(ParameterTypes.ITEM), null);

        Fish(ObjectiveTracker tracker) {
            super(tracker, "fish", "Fish", "minecraft:fishing_rod", ITEM);
        }

        @Override
        public Component describe(ParameterValues values) {
            return Component.text("Fish up ").append(names(values.getOrNull(ITEM), "anything"));
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onFish(PlayerFishEvent event) {
            if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item item) {
                Material type = item.getItemStack().getType();
                tracker.progress(event.getPlayer(), this, v -> anyOrContains(v, ITEM, type), 1);
            }
        }
    }

    static final class ReachLocation extends TrackedObjective {
        static final Parameter<String> WORLD = Parameter.required("world", Component.text("World name"), ParameterTypes.STRING);
        static final Parameter<Double> X = Parameter.required("x", Component.text("X"), ParameterTypes.DECIMAL);
        static final Parameter<Double> Y = Parameter.required("y", Component.text("Y"), ParameterTypes.DECIMAL);
        static final Parameter<Double> Z = Parameter.required("z", Component.text("Z"), ParameterTypes.DECIMAL);
        static final Parameter<Double> RADIUS = Parameter.optional("radius", Component.text("Radius in blocks"), ParameterTypes.DECIMAL, "3");
        static final Parameter<String> NAME = Parameter.optional("name", Component.text("Location name shown to players"), ParameterTypes.TEXT, null);

        ReachLocation(ObjectiveTracker tracker) {
            super(tracker, "reach_location", "Reach a location", "minecraft:compass", WORLD, X, Y, Z, RADIUS, NAME);
        }

        @Override
        public boolean countable() {
            return false;
        }

        @Override
        public Component describe(ParameterValues values) {
            String name = values.getOrNull(NAME);
            if (name != null) {
                return Component.text("Reach ").append(dev.voidscape.journeys.core.text.Text.parse(name));
            }
            return Component.text("Reach %.0f, %.0f, %.0f".formatted(values.get(X), values.get(Y), values.get(Z)));
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onMove(PlayerMoveEvent event) {
            if (!event.hasChangedBlock()) {
                return;
            }
            Location to = event.getTo();
            String world = to.getWorld().getName();
            tracker.progress(event.getPlayer(), this, v -> {
                if (!v.get(WORLD).equals(world)) {
                    return false;
                }
                double dx = to.getX() - v.get(X);
                double dy = to.getY() - v.get(Y);
                double dz = to.getZ() - v.get(Z);
                double radius = v.get(RADIUS);
                return dx * dx + dy * dy + dz * dz <= radius * radius;
            }, 1);
        }
    }
}
