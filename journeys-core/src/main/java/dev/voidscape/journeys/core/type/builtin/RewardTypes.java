package dev.voidscape.journeys.core.type.builtin;

import dev.voidscape.journeys.api.type.Parameter;
import dev.voidscape.journeys.api.type.ParameterTypes;
import dev.voidscape.journeys.api.type.ParameterValues;
import dev.voidscape.journeys.api.type.RewardType;
import dev.voidscape.journeys.core.text.PlaceholderService;
import dev.voidscape.journeys.core.text.Text;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Built-in reward types. */
public final class RewardTypes {

    private RewardTypes() {
    }

    /** Command and message rewards fill in external placeholders (PlaceholderAPI) for the rewarded player. */
    public static List<RewardType> all(PlaceholderService placeholders) {
        return List.of(new Command(placeholders), new Experience(), new Item(), new Message(placeholders));
    }

    static final class Command extends BaseType implements RewardType {
        static final Parameter<String> COMMAND = Parameter.required("command",
                Component.text("Console command, without /. {player} is replaced"), ParameterTypes.STRING);

        private final PlaceholderService placeholders;

        Command(PlaceholderService placeholders) {
            super("command", "Run console command", "minecraft:command_block", COMMAND);
            this.placeholders = placeholders;
        }

        @Override
        public void grant(Player player, ParameterValues values) {
            String command = placeholders.apply(player, values.get(COMMAND).replace("{player}", player.getName()));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.startsWith("/") ? command.substring(1) : command);
        }

        @Override
        public Component describe(ParameterValues values) {
            return Component.text("/" + values.raw(COMMAND.id()).orElse("?"));
        }
    }

    static final class Experience extends BaseType implements RewardType {
        static final Parameter<Integer> AMOUNT = Parameter.required("amount", Component.text("Amount"), ParameterTypes.POSITIVE_INTEGER);
        static final Parameter<Boolean> LEVELS = Parameter.optional("levels", Component.text("Give levels instead of points"), ParameterTypes.BOOLEAN, "false");

        Experience() {
            super("experience", "Experience", "minecraft:experience_bottle", AMOUNT, LEVELS);
        }

        @Override
        public void grant(Player player, ParameterValues values) {
            if (values.get(LEVELS)) {
                player.giveExpLevels(values.get(AMOUNT));
            } else {
                player.giveExp(values.get(AMOUNT), true);
            }
        }

        @Override
        public Component describe(ParameterValues values) {
            return Component.text(values.raw(AMOUNT.id()).orElse("?") + (values.get(LEVELS) ? " levels" : " experience"));
        }
    }

    static final class Item extends BaseType implements RewardType {
        static final Parameter<Material> ITEM = Parameter.required("item", Component.text("Item"), ParameterTypes.ITEM);
        static final Parameter<Integer> AMOUNT = Parameter.optional("amount", Component.text("Amount"), ParameterTypes.POSITIVE_INTEGER, "1");
        static final Parameter<String> NAME = Parameter.optional("name", Component.text("Custom name"), ParameterTypes.TEXT, null);

        Item() {
            super("item", "Item", "minecraft:chest", ITEM, AMOUNT, NAME);
        }

        @Override
        public void grant(Player player, ParameterValues values) {
            Material material = values.get(ITEM);
            int remaining = values.get(AMOUNT);
            String name = values.getOrNull(NAME);
            while (remaining > 0) {
                int size = Math.min(remaining, material.getMaxStackSize());
                ItemStack stack = ItemStack.of(material, size);
                if (name != null) {
                    stack.editMeta(meta -> meta.displayName(Text.item(name)));
                }
                // Anything that does not fit is dropped at the player's feet rather than lost.
                player.getInventory().addItem(stack).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                remaining -= size;
            }
        }

        @Override
        public Component describe(ParameterValues values) {
            return Component.text(values.get(AMOUNT) + "x ").append(Component.translatable(values.get(ITEM)));
        }
    }

    static final class Message extends BaseType implements RewardType {
        static final Parameter<String> MESSAGE = Parameter.required("message", Component.text("MiniMessage text"), ParameterTypes.TEXT);

        private final PlaceholderService placeholders;

        Message(PlaceholderService placeholders) {
            super("message", "Send message", "minecraft:oak_sign", MESSAGE);
            this.placeholders = placeholders;
        }

        @Override
        public void grant(Player player, ParameterValues values) {
            player.sendMessage(placeholders.parse(player, values.get(MESSAGE), Text.unparsed("player", player.getName())));
        }

        @Override
        public Component describe(ParameterValues values) {
            return Component.text("Message: ").append(Text.parse(values.raw(MESSAGE.id()).orElse("")));
        }
    }
}
