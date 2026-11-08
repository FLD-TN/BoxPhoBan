package com.dex.boxphoban.gui;

import com.dex.boxphoban.BoxPhoBan;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** One-line chat prompts ("type the value in chat, 'huy' to cancel"). */
public final class ChatInput implements Listener {

    private record Prompt(Consumer<String> done, Runnable cancelled, long expires) {
    }

    private final BoxPhoBan plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public ChatInput(BoxPhoBan plugin) {
        this.plugin = plugin;
    }

    public void ask(Player p, String messageKey, Consumer<String> done, Runnable cancelled, String... kv) {
        p.closeInventory();
        prompts.put(p.getUniqueId(), new Prompt(done, cancelled, System.currentTimeMillis() + 60_000));
        plugin.messages().send(p, messageKey, kv);
        plugin.messages().send(p, "input.hint");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Prompt pr = prompts.remove(e.getPlayer().getUniqueId());
        if (pr == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (pr.expires < System.currentTimeMillis()) {
                plugin.messages().send(p, "input.expired");
                return;
            }
            if (text.equalsIgnoreCase("huy") || text.equalsIgnoreCase("hủy") || text.equalsIgnoreCase("cancel")) {
                plugin.messages().send(p, "input.cancelled");
                if (pr.cancelled != null) pr.cancelled.run();
                return;
            }
            pr.done.accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        prompts.remove(e.getPlayer().getUniqueId());
    }
}
