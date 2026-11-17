package dev.voidscape.journeys.core.gui;

import dev.voidscape.journeys.core.config.Messages;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Asks a player to type a value in chat, used by the editor for text input. The typed message is captured raw (so
 * MiniMessage tags survive) and never broadcast. Typing {@code cancel} aborts.
 */
public final class ChatPrompts implements Listener {

    private static final long TIMEOUT_MILLIS = 5 * 60 * 1000;

    private final Plugin plugin;
    private final Messages messages;
    private final Map<UUID, Prompt> pending = new ConcurrentHashMap<>();

    private record Prompt(Consumer<String> onInput, Runnable onCancel, long expiresAt) {
    }

    public ChatPrompts(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    /**
     * Closes the player's inventory and waits for chat input. Both callbacks run on the server thread.
     *
     * @param question shown to the player along with how to cancel
     * @param onInput  receives the typed text
     * @param onCancel runs if the player types cancel, times out, or quits
     */
    public void ask(Player player, Component question, Consumer<String> onInput, Runnable onCancel) {
        player.closeInventory();
        player.sendMessage(question);
        messages.send(player, "editor.prompt-cancel-hint");
        Prompt previous = pending.put(player.getUniqueId(), new Prompt(onInput, onCancel, System.currentTimeMillis() + TIMEOUT_MILLIS));
        if (previous != null) {
            previous.onCancel().run();
        }
    }

    public boolean isPrompting(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Prompt prompt = pending.remove(event.getPlayer().getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.originalMessage()).trim();
        boolean expired = System.currentTimeMillis() > prompt.expiresAt();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (expired || input.equalsIgnoreCase("cancel")) {
                prompt.onCancel().run();
            } else {
                prompt.onInput().accept(input);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
