package dev.companionai;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deixa o admin "fora do servidor": some da lista de jogadores (TAB), do /list,
 * da contagem no menu de servidores, e manda mensagens falsas de saiu/entrou.
 */
public final class VanishManager implements Listener {

    private final CompanionAI plugin;
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

    VanishManager(CompanionAI plugin) {
        this.plugin = plugin;
    }

    public boolean isHidden(Player p) {
        return hidden.contains(p.getUniqueId());
    }

    /** @return true se mudou de visível para escondido */
    public boolean hide(Player p, boolean fakeLeaveMessage) {
        if (!hidden.add(p.getUniqueId())) return false;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p) || CompanionAI.isAdmin(other)) continue;
            other.hidePlayer(plugin, p);
        }
        if (fakeLeaveMessage) {
            Bukkit.getServer().sendMessage(Component.translatable("multiplayer.player.left",
                    NamedTextColor.YELLOW, p.displayName()));
        }
        return true;
    }

    /** @return true se mudou de escondido para visível */
    public boolean show(Player p, boolean fakeJoinMessage) {
        if (!hidden.remove(p.getUniqueId())) return false;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            other.showPlayer(plugin, p);
        }
        if (fakeJoinMessage) {
            Bukkit.getServer().sendMessage(Component.translatable("multiplayer.player.joined",
                    NamedTextColor.YELLOW, p.displayName()));
        }
        return true;
    }

    public boolean toggle(Player p) {
        if (isHidden(p)) {
            show(p, true);
            return false;
        }
        hide(p, true);
        return true;
    }

    void shutdown() {
        for (UUID id : List.copyOf(hidden)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) show(p, false);
        }
    }

    private int hiddenOnline() {
        int n = 0;
        for (UUID id : hidden) if (Bukkit.getPlayer(id) != null) n++;
        return n;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player joined = e.getPlayer();
        if (isHidden(joined)) {
            e.joinMessage(null);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(joined) && !CompanionAI.isAdmin(other)) other.hidePlayer(plugin, joined);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    CompanionAI.info(joined, "Você continua INVISÍVEL para os outros (/horror vanish para aparecer)."), 20L);
        }
        if (!CompanionAI.isAdmin(joined)) {
            for (UUID id : hidden) {
                Player h = Bukkit.getPlayer(id);
                if (h != null && !h.equals(joined)) joined.hidePlayer(plugin, h);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        // Já mandamos o "saiu do jogo" falso; não pode aparecer um segundo.
        if (isHidden(e.getPlayer())) e.quitMessage(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (isHidden(e.getPlayer())) e.message(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        if (isHidden(e.getPlayer())) e.deathMessage(null);
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent e) {
        int h = hiddenOnline();
        if (h == 0) return;
        e.setNumPlayers(Math.max(0, e.getNumPlayers() - h));
        e.getListedPlayers().removeIf(info -> hidden.contains(info.id()));
    }

    /** O /list padrão mostraria o admin escondido. Respondemos com uma lista "limpa". */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onListCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (CompanionAI.isAdmin(p) || hidden.isEmpty()) return;
        String cmd = e.getMessage().toLowerCase(Locale.ROOT).trim();
        if (!(cmd.equals("/list") || cmd.equals("/minecraft:list"))) return;
        e.setCancelled(true);
        List<Component> names = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!hidden.contains(other.getUniqueId())) names.add(Component.text(other.getName()));
        }
        p.sendMessage(Component.translatable("commands.list.players",
                Component.text(names.size()),
                Component.text(Bukkit.getMaxPlayers()),
                Component.join(JoinConfiguration.commas(true), names)));
    }
}
