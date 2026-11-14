package dev.companionai;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tela para digitar um texto (usa a bigorna do Minecraft, sem gastar XP).
 * Usada para o amigo dar nome ao cachorro e para o painel do admin.
 */
public final class TextPrompt implements Listener {

    private static final class Session {
        final Consumer<String> onDone;
        final Inventory top;
        String result;

        Session(Consumer<String> onDone, Inventory top) {
            this.onDone = onDone;
            this.top = top;
        }
    }

    private final CompanionAI plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    TextPrompt(CompanionAI plugin) {
        this.plugin = plugin;
    }

    public void ask(Player p, String title, String initial, Consumer<String> onDone) {
        // A janela da bigorna só cabe ~18 letras no título.
        String shortTitle = title.length() > 18 ? title.substring(0, 17).trim() + "…" : title;
        AnvilView view = MenuType.ANVIL.create(p, Component.text(shortTitle));
        p.openInventory(view);
        Inventory top = view.getTopInventory();
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.displayName(Component.text(initial));
        paper.setItemMeta(meta);
        top.setItem(0, paper);
        sessions.put(p.getUniqueId(), new Session(onDone, top));
    }

    private Session session(Player p, Inventory top) {
        Session s = sessions.get(p.getUniqueId());
        return s != null && s.top.equals(top) ? s : null;
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent e) {
        if (!(e.getView().getPlayer() instanceof Player p)) return;
        if (session(p, e.getView().getTopInventory()) == null) return;
        String text = e.getView().getRenameText();
        e.getView().setRepairCost(0);
        if (text == null || text.isBlank()) {
            e.setResult(null);
            return;
        }
        ItemStack ok = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = ok.getItemMeta();
        meta.displayName(Component.text(text.trim()));
        meta.lore(java.util.List.of(Component.text("Clique aqui para confirmar")));
        ok.setItemMeta(meta);
        e.setResult(ok);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Session s = session(p, e.getView().getTopInventory());
        if (s == null) return;
        e.setCancelled(true);
        if (e.getRawSlot() != 2 || !(e.getView() instanceof AnvilView view)) return;
        String text = view.getRenameText();
        if (text == null || text.isBlank()) return;
        s.result = text.trim();
        Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && session(p, e.getView().getTopInventory()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Session s = session(p, e.getView().getTopInventory());
        if (s == null) return;
        sessions.remove(p.getUniqueId());
        String result = s.result;
        if (result == null && e.getView() instanceof AnvilView view) {
            String text = view.getRenameText();
            if (text != null && !text.isBlank()) result = text.trim();
        }
        // Não deixa o papel cair no inventário do jogador.
        s.top.clear();
        if (result != null) {
            String r = result;
            Bukkit.getScheduler().runTask(plugin, () -> s.onDone.accept(r));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sessions.remove(e.getPlayer().getUniqueId());
    }
}
