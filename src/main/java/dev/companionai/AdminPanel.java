package dev.companionai;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Painel secreto: segure o item do painel e clique com o botão ESQUERDO.
 * Só quem tem permissão (op) consegue abrir.
 */
public final class AdminPanel implements Listener {

    /** Uma tela do painel; cada slot tem uma ação. */
    private static final class Menu implements InventoryHolder {
        final Map<Integer, Runnable> actions = new HashMap<>();
        Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private final CompanionAI plugin;

    AdminPanel(CompanionAI plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ item do painel

    public ItemStack panelItem() {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text("Painel do Horror", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
        meta.lore(List.of(text("Clique ESQUERDO para abrir", NamedTextColor.GRAY)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(plugin.panelKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPanelItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(plugin.panelKey());
    }

    public void givePanelItem(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isPanelItem(it)) {
                CompanionAI.info(p, "Você já tem o item do painel. Segure e clique com o botão esquerdo.");
                return;
            }
        }
        p.getInventory().addItem(panelItem());
        CompanionAI.info(p, "Item do painel no seu inventário. Segure e clique com o botão ESQUERDO.");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onLeftClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (!isPanelItem(e.getItem())) return;
        e.setCancelled(true);
        if (CompanionAI.isAdmin(e.getPlayer())) openMain(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHitWithPanel(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && isPanelItem(p.getInventory().getItemInMainHand())) {
            e.setCancelled(true);
            if (CompanionAI.isAdmin(p)) openMain(p);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !CompanionAI.isAdmin(p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        Runnable action = menu.actions.get(e.getRawSlot());
        if (action != null) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 1.2f);
            action.run();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ telas

    public void openMain(Player p) {
        Menu m = menu(54, "Painel do Horror");
        PossessionManager pos = plugin.possession();
        Wolf myWolf = pos.wolfOf(p);
        boolean hidden = plugin.vanish().isHidden(p);
        boolean monster = myWolf != null && plugin.monsters().isMonster(myWolf);

        String forma = myWolf == null ? "humano"
                : (monster ? "MONSTRO" : "cachorro") + " (" + Dogs.displayName(myWolf, "sem nome") + ")";
        set(m, 4, Material.BOOK, "Status", NamedTextColor.GOLD,
                "Forma: " + forma,
                "Invisível: " + (hidden ? "SIM" : "não"), null);

        // --- Cachorro
        set(m, 10, Material.WOLF_SPAWN_EGG, "Virar cachorro novo", NamedTextColor.GREEN,
                "Vira um cachorro selvagem aqui.", "Seu amigo pode te domesticar com ossos.", () -> {
                    p.closeInventory();
                    pos.turnNew(p);
                });
        set(m, 11, Material.BONE, "Entrar num cachorro", NamedTextColor.GREEN,
                "Escolha um cachorro que já existe", "(ex.: o que ele domesticou)", () ->
                        openDogs(p, "Entrar em qual cachorro?", w -> {
                            p.closeInventory();
                            pos.turnInto(p, w);
                        }));
        set(m, 12, Material.BONE_BLOCK, monster ? "Sair da monsterform" : "Monsterform", NamedTextColor.RED,
                myWolf != null ? "Transforma o cachorro que você controla" : "Escolha qual cachorro vira monstro",
                "Clique de novo para voltar ao normal", () -> {
                    if (myWolf != null) {
                        toggleMonster(p, myWolf);
                        openMain(p);
                    } else {
                        openDogs(p, "Qual vira monstro?", w -> {
                            toggleMonster(p, w);
                            openMain(p);
                        });
                    }
                });
        set(m, 13, Material.NAME_TAG, "Renomear cachorro", NamedTextColor.AQUA,
                myWolf != null ? "Renomeia o cachorro que você controla" : "Escolha qual cachorro renomear",
                null, () -> {
                    if (myWolf != null) askRename(p, myWolf);
                    else openDogs(p, "Renomear qual cachorro?", w -> askRename(p, w));
                });
        set(m, 14, Material.OAK_STAIRS, "Sentar / levantar", NamedTextColor.AQUA,
                "Só funciona dentro do cachorro", null, () -> {
                    if (!pos.toggleSit(p, null)) CompanionAI.error(p, "Você não está em um cachorro.");
                    openMain(p);
                });
        set(m, 15, Material.BARRIER, "Voltar ao normal", NamedTextColor.RED,
                "Sai do cachorro (continua invisível)", null, () -> {
                    p.closeInventory();
                    if (!pos.stop(p, true)) CompanionAI.error(p, "Você não está em um cachorro.");
                });
        set(m, 16, hidden ? Material.ENDER_EYE : Material.GLASS,
                hidden ? "Aparecer (entrar no servidor)" : "Ficar invisível (sair do servidor)", NamedTextColor.LIGHT_PURPLE,
                hidden ? "Manda \"entrou no jogo\" falso" : "Manda \"saiu do jogo\" falso", "e some da lista de jogadores", () -> {
                    if (pos.isPossessing(p) && hidden) {
                        CompanionAI.error(p, "Saia do cachorro primeiro.");
                        return;
                    }
                    plugin.vanish().toggle(p);
                    openMain(p);
                });

        // --- Clima e tempo
        World w = p.getWorld();
        set(m, 19, Material.SUNFLOWER, "Sol", NamedTextColor.YELLOW, null, null, () -> weather(p, false, false));
        set(m, 20, Material.WATER_BUCKET, "Chuva", NamedTextColor.BLUE, null, null, () -> weather(p, true, false));
        set(m, 21, Material.LIGHTNING_ROD, "Tempestade", NamedTextColor.DARK_BLUE, null, null, () -> weather(p, true, true));
        Boolean cycle = w.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE);
        boolean running = cycle == null || cycle;
        set(m, 22, Material.DAYLIGHT_DETECTOR, running ? "Congelar o tempo" : "Descongelar o tempo", NamedTextColor.GOLD,
                running ? "A hora para de passar" : "A hora volta a passar", null, () -> {
                    w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, !running);
                    openMain(p);
                });
        set(m, 23, Material.CLOCK, "Dia", NamedTextColor.YELLOW, null, null, () -> time(p, 1000));
        set(m, 24, Material.ORANGE_DYE, "Pôr do sol", NamedTextColor.GOLD, null, null, () -> time(p, 12500));
        set(m, 25, Material.BLACK_DYE, "Meia-noite", NamedTextColor.DARK_GRAY, null, null, () -> time(p, 18000));

        // --- Teleporte
        set(m, 28, Material.ENDER_PEARL, "TP até um jogador", NamedTextColor.DARK_AQUA, null, null, () ->
                pickTarget(p, "TP até quem?", t -> {
                    p.closeInventory();
                    p.teleport(t.getLocation());
                    CompanionAI.info(p, "Teleportado até " + t.getName() + ".");
                }));
        set(m, 29, Material.LEAD, "TP até um cachorro", NamedTextColor.DARK_AQUA, null, null, () ->
                openDogs(p, "TP até qual cachorro?", d -> {
                    p.closeInventory();
                    p.teleport(d.getLocation());
                }));

        // --- Sustos
        set(m, 37, Material.SCULK, "Som de caverna", NamedTextColor.DARK_GREEN,
                "Só ele escuta", null, () -> pickTarget(p, "Assustar quem?", t ->
                        t.playSound(t.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 1f, 1f)));
        set(m, 38, Material.LEATHER_BOOTS, "Passos atrás dele", NamedTextColor.DARK_GREEN,
                "Só ele escuta", null, () -> pickTarget(p, "Assustar quem?", this::footsteps));
        set(m, 39, Material.BONE_MEAL, "Rosnado atrás dele", NamedTextColor.DARK_GREEN,
                "Só ele escuta", null, () -> pickTarget(p, "Assustar quem?", t ->
                        t.playSound(behind(t, 2.5), Sound.ENTITY_WOLF_GROWL, SoundCategory.HOSTILE, 1f, 0.55f)));
        set(m, 40, Material.INK_SAC, "Escuridão (7s)", NamedTextColor.DARK_GREEN,
                "A tela dele escurece e pulsa", null, () -> pickTarget(p, "Assustar quem?", t ->
                        t.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 140, 0, false, false, false))));
        set(m, 41, Material.TRIDENT, "Raio perto dele", NamedTextColor.DARK_GREEN,
                "Só o efeito, não machuca", null, () -> pickTarget(p, "Assustar quem?", t -> {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    Location l = t.getLocation().add(r.nextDouble(-8, 8), 0, r.nextDouble(-8, 8));
                    l.setY(t.getWorld().getHighestBlockYAt(l) + 1);
                    t.getWorld().strikeLightningEffect(l);
                }));
        set(m, 42, Material.OAK_SIGN, "Mensagem na tela dele", NamedTextColor.DARK_GREEN,
                "Você digita, aparece grande", "na tela só dele", () -> pickTarget(p, "Mensagem para quem?", t ->
                        plugin.prompts().ask(p, "Mensagem na tela de " + t.getName(), "", msg -> {
                            t.showTitle(Title.title(Component.text(msg, NamedTextColor.DARK_RED), Component.empty(),
                                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1))));
                            t.playSound(t.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 0.6f);
                        })));
        set(m, 43, Material.ENDER_EYE, "Olhar fixo", NamedTextColor.DARK_GREEN,
                "Ele vira a cabeça para o", "cachorro que você controla", () -> pickTarget(p, "Quem olha?", t -> {
                    Wolf wolf = pos.wolfOf(p);
                    if (wolf == null || !wolf.getWorld().equals(t.getWorld())) {
                        CompanionAI.error(p, "Entre num cachorro perto dele primeiro.");
                        return;
                    }
                    Location eye = t.getLocation();
                    Vector dir = wolf.getLocation().add(0, 0.5, 0).toVector().subtract(t.getEyeLocation().toVector());
                    eye.setDirection(dir);
                    t.teleport(eye);
                }));

        set(m, 49, Material.SPRUCE_DOOR, "Fechar", NamedTextColor.GRAY, null, null, p::closeInventory);
        p.openInventory(m.inventory);
    }

    private void openDogs(Player p, String title, Consumer<Wolf> onPick) {
        List<Wolf> dogs = Dogs.interesting(p.getLocation());
        if (dogs.isEmpty()) {
            CompanionAI.error(p, "Nenhum cachorro domesticado ou com nome encontrado (precisa estar carregado).");
            return;
        }
        Menu m = menu(54, title);
        int slot = 0;
        for (Wolf w : dogs) {
            if (slot >= 45) break;
            Location l = w.getLocation();
            String where = l.getWorld().getName() + " " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
            String extra = plugin.possession().isPossessed(w) ? "(controlado agora)"
                    : plugin.monsters().isMonster(w) ? "(monstro)" : null;
            set(m, slot++, Material.WOLF_SPAWN_EGG, Dogs.displayName(w, "sem nome"), NamedTextColor.GREEN,
                    "Dono: " + Dogs.ownerName(w), extra == null ? where : where + " " + extra, () -> onPick.accept(w));
        }
        set(m, 49, Material.ARROW, "Voltar", NamedTextColor.GRAY, null, null, () -> openMain(p));
        p.openInventory(m.inventory);
    }

    /** Escolhe o alvo; se só tem um jogador além de você, vai direto nele. */
    private void pickTarget(Player p, String title, Consumer<Player> action) {
        List<Player> targets = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p) && !CompanionAI.isAdmin(o)) targets.add(o);
        if (targets.isEmpty()) {
            for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p)) targets.add(o);
        }
        if (targets.isEmpty()) {
            CompanionAI.error(p, "Não tem mais ninguém online.");
            return;
        }
        if (targets.size() == 1) {
            action.accept(targets.get(0));
            CompanionAI.actionBar(p, "Feito em " + targets.get(0).getName());
            return;
        }
        Menu m = menu(54, title);
        int slot = 0;
        for (Player t : targets) {
            if (slot >= 45) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(t);
            meta.displayName(text(t.getName(), NamedTextColor.YELLOW));
            head.setItemMeta(meta);
            m.inventory.setItem(slot, head);
            UUID id = t.getUniqueId();
            m.actions.put(slot++, () -> {
                Player target = Bukkit.getPlayer(id);
                if (target != null) action.accept(target);
                openMain(p);
            });
        }
        set(m, 49, Material.ARROW, "Voltar", NamedTextColor.GRAY, null, null, () -> openMain(p));
        p.openInventory(m.inventory);
    }

    // ------------------------------------------------------------------ ações

    private void toggleMonster(Player p, Wolf w) {
        boolean on = plugin.monsters().toggle(w);
        CompanionAI.info(p, Dogs.displayName(w, "Cachorro") + (on ? " virou MONSTRO." : " voltou a ser cachorro."));
    }

    private void askRename(Player p, Wolf w) {
        plugin.prompts().ask(p, "Novo nome do cachorro", Dogs.displayName(w, ""), name -> {
            if (!w.isValid()) return;
            plugin.monsters().setDogName(w, name);
            CompanionAI.info(p, "Cachorro renomeado para \"" + name + "\".");
        });
    }

    private void weather(Player p, boolean rain, boolean thunder) {
        World w = p.getWorld();
        w.setStorm(rain);
        w.setThundering(thunder);
        w.setWeatherDuration(20 * 60 * 10);
        if (thunder) w.setThunderDuration(20 * 60 * 10);
        CompanionAI.actionBar(p, rain ? (thunder ? "Tempestade" : "Chuva") : "Sol");
    }

    private void time(Player p, long t) {
        p.getWorld().setTime(t);
        CompanionAI.actionBar(p, "Hora alterada");
    }

    private void footsteps(Player t) {
        for (int i = 0; i < 6; i++) {
            int step = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!t.isOnline()) return;
                Location l = behind(t, 3.5 - step * 0.35);
                Block below = l.clone().subtract(0, 1, 0).getBlock();
                Sound s = below.getType().isAir() ? Sound.BLOCK_GRASS_STEP
                        : below.getBlockData().getSoundGroup().getStepSound();
                t.playSound(l, s, SoundCategory.PLAYERS, 0.9f, 0.9f);
            }, i * 7L);
        }
    }

    private static Location behind(Player t, double dist) {
        Vector back = t.getLocation().getDirection().setY(0);
        if (back.lengthSquared() < 1e-4) back = new Vector(0, 0, 1);
        back.normalize().multiply(-dist);
        return t.getLocation().add(back);
    }

    // ------------------------------------------------------------------ helpers

    private static Menu menu(int size, String title) {
        Menu m = new Menu();
        m.inventory = Bukkit.createInventory(m, size, text(title, NamedTextColor.DARK_PURPLE));
        return m;
    }

    private static void set(Menu m, int slot, Material mat, String name, NamedTextColor color,
                            String lore1, String lore2, Runnable action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name, color).decorate(TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        if (lore1 != null) lore.add(text(lore1, NamedTextColor.GRAY));
        if (lore2 != null) lore.add(text(lore2, NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        m.inventory.setItem(slot, item);
        if (action != null) m.actions.put(slot, action);
    }

    private static Component text(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }
}
