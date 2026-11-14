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
import org.bukkit.inventory.ItemFlag;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Painel secreto organizado em categorias:
 * Início → Cachorro & Monstro / Jogadores (efeitos, sustos, teleporte) / Mundo.
 * Segure o item do painel e clique com o botão ESQUERDO. Só op consegue abrir.
 */
public final class AdminPanel implements Listener {

    private static final int[] DURATIONS = {5, 10, 30, 60, 300, 0}; // 0 = infinito
    private static final int MAX_LEVEL = 5;

    // Slots fixos de navegação (última linha)
    private static final int SLOT_BACK = 45;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_HOME = 53;

    /** Uma tela do painel; cada slot tem uma ação. */
    private static final class Menu implements InventoryHolder {
        final Map<Integer, Runnable> actions = new HashMap<>();
        Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    /** Escolhas de duração/nível de efeito de cada admin. */
    private static final class FxSettings {
        int durationIndex = 2; // 30s
        int level = 1;
    }

    private final CompanionAI plugin;
    private final Map<UUID, FxSettings> fxSettings = new ConcurrentHashMap<>();

    AdminPanel(CompanionAI plugin) {
        this.plugin = plugin;
    }

    // ================================================================== item do painel

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
        if (CompanionAI.isAdmin(e.getPlayer())) openHome(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHitWithPanel(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && isPanelItem(p.getInventory().getItemInMainHand())) {
            e.setCancelled(true);
            if (CompanionAI.isAdmin(p)) openHome(p);
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
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.4f, 1.3f);
            action.run();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ================================================================== INÍCIO

    public void openHome(Player p) {
        Menu m = menu("Painel do Horror");
        PossessionManager pos = plugin.possession();
        Wolf myWolf = pos.wolfOf(p);
        boolean hidden = plugin.vanish().isHidden(p);
        boolean monster = myWolf != null && plugin.monsters().isMonster(myWolf);

        statusItem(m, 4, p);

        // Categorias
        button(m, 20, Material.WOLF_SPAWN_EGG, "Cachorro & Monstro", NamedTextColor.GREEN,
                List.of("Virar cachorro, monsterform,", "sentar, renomear, rugir..."), () -> openForms(p));
        button(m, 22, Material.PLAYER_HEAD, "Jogadores", NamedTextColor.YELLOW,
                List.of("Efeitos (cegueira, náusea...),", "sustos e teleporte"), () -> openPlayers(p));
        button(m, 24, Material.GRASS_BLOCK, "Mundo", NamedTextColor.AQUA,
                List.of("Clima, hora do dia", "e congelar o tempo"), () -> openWorld(p));

        // Atalhos rápidos
        label(m, 31, "Atalhos rápidos");
        if (myWolf == null) {
            button(m, 38, Material.BONE, "Virar cachorro novo", NamedTextColor.GREEN,
                    List.of("Vira um cachorro selvagem aqui"), () -> {
                        p.closeInventory();
                        pos.turnNew(p);
                    });
        } else {
            button(m, 38, Material.BARRIER, "Voltar ao normal", NamedTextColor.RED,
                    List.of("Sai do cachorro", "(continua invisível)"), () -> {
                        p.closeInventory();
                        pos.stop(p, true);
                    });
        }
        if (myWolf != null) {
            button(m, 39, Material.BONE_BLOCK, monster ? "Sair da monsterform" : "Monsterform", NamedTextColor.DARK_RED,
                    List.of("Liga/desliga o monstro"), () -> {
                        toggleMonster(p, myWolf);
                        openHome(p);
                    });
        }
        if (monster) {
            button(m, 40, Material.GOAT_HORN, "Rugir", NamedTextColor.DARK_RED,
                    List.of("Rugido alto + escuridão", "em quem estiver perto"), () -> {
                        pos.roar(p);
                        p.closeInventory();
                    });
        }
        button(m, 41, Material.OAK_STAIRS, "Sentar / levantar", NamedTextColor.AQUA,
                List.of("Ou aperte Shift"), () -> {
                    if (!pos.toggleSit(p, null)) CompanionAI.error(p, "Você não está em um cachorro.");
                    openHome(p);
                });
        vanishButton(m, 42, p, () -> openHome(p));

        button(m, SLOT_CLOSE, Material.SPRUCE_DOOR, "Fechar", NamedTextColor.GRAY, List.of(), p::closeInventory);
        finish(m);
        p.openInventory(m.inventory);
    }

    // ================================================================== CACHORRO & MONSTRO

    private void openForms(Player p) {
        Menu m = menu("Cachorro & Monstro");
        PossessionManager pos = plugin.possession();
        Wolf myWolf = pos.wolfOf(p);
        boolean monster = myWolf != null && plugin.monsters().isMonster(myWolf);
        statusItem(m, 4, p);

        // --- Linha: virar cachorro
        label(m, 9, "Ser cachorro");
        button(m, 10, Material.WOLF_SPAWN_EGG, "Virar cachorro novo", NamedTextColor.GREEN,
                List.of("Vira um cachorro selvagem aqui.", "Ele pode te domesticar com ossos."), () -> {
                    p.closeInventory();
                    pos.turnNew(p);
                });
        button(m, 11, Material.BONE, "Entrar num cachorro", NamedTextColor.GREEN,
                List.of("Escolha um que já existe", "(ex.: o que ele domesticou)"), () ->
                        openDogs(p, "Entrar em qual cachorro?", () -> openForms(p), w -> {
                            p.closeInventory();
                            pos.turnInto(p, w);
                        }));
        button(m, 12, Material.BARRIER, "Voltar ao normal", NamedTextColor.RED,
                List.of("Sai do cachorro", "(continua invisível)"), () -> {
                    p.closeInventory();
                    if (!pos.stop(p, true)) CompanionAI.error(p, "Você não está em um cachorro.");
                });
        button(m, 14, Material.OAK_STAIRS, "Sentar / levantar", NamedTextColor.AQUA,
                List.of("Atalho: aperte Shift"), () -> {
                    if (!pos.toggleSit(p, null)) CompanionAI.error(p, "Você não está em um cachorro.");
                    openForms(p);
                });
        button(m, 15, Material.SPYGLASS, "Me ver (F5)", NamedTextColor.AQUA,
                List.of("Mostra/esconde o cachorro", "na SUA tela"), () -> {
                    if (!pos.toggleSelfView(p)) CompanionAI.error(p, "Você não está em um cachorro.");
                    openForms(p);
                });
        button(m, 16, Material.NAME_TAG, "Renomear meu cachorro", NamedTextColor.AQUA,
                List.of("Muda o nome que aparece", "em cima de você e no chat"), () -> {
                    if (myWolf == null) CompanionAI.error(p, "Você não está em um cachorro.");
                    else askRename(p, myWolf, () -> openForms(p));
                });

        // --- Linha: monstro
        label(m, 27, "Monstro");
        button(m, 28, Material.BONE_BLOCK, monster ? "Sair da monsterform" : "Virar monstro", NamedTextColor.DARK_RED,
                List.of("No cachorro que você controla.",
                        "Monstro quebra blocos e machuca",
                        "jogadores (" + hearts(plugin.getConfig().getDouble("monstro-dano", 6.0)) + " por mordida)."), () -> {
                    if (myWolf == null) CompanionAI.error(p, "Entre num cachorro primeiro.");
                    else toggleMonster(p, myWolf);
                    openForms(p);
                });
        button(m, 29, Material.GOAT_HORN, "Rugir", NamedTextColor.DARK_RED,
                List.of("Rugido alto + escuridão", "em quem estiver perto"), () -> {
                    if (!pos.roar(p)) CompanionAI.error(p, "Só funciona na forma de monstro.");
                    else p.closeInventory();
                });
        button(m, 31, Material.SKELETON_SKULL, "Monstro em outro cachorro", NamedTextColor.RED,
                List.of("Transforma um cachorro que", "anda sozinho (sem você dentro)"), () ->
                        openDogs(p, "Qual vira monstro?", () -> openForms(p), w -> {
                            toggleMonster(p, w);
                            openForms(p);
                        }));
        button(m, 32, Material.WRITABLE_BOOK, "Renomear outro cachorro", NamedTextColor.AQUA,
                List.of("Escolha qual cachorro"), () ->
                        openDogs(p, "Renomear qual cachorro?", () -> openForms(p), w -> askRename(p, w, () -> openForms(p))));
        button(m, 33, Material.LEAD, "TP até um cachorro", NamedTextColor.DARK_AQUA,
                List.of("Vai até onde ele está"), () ->
                        openDogs(p, "TP até qual cachorro?", () -> openForms(p), w -> {
                            p.closeInventory();
                            p.teleport(w.getLocation());
                        }));

        nav(m, p, this::openHome);
        finish(m);
        p.openInventory(m.inventory);
    }

    private void openDogs(Player p, String title, Runnable back, Consumer<Wolf> onPick) {
        List<Wolf> dogs = Dogs.interesting(p.getLocation());
        if (dogs.isEmpty()) {
            CompanionAI.error(p, "Nenhum cachorro domesticado ou com nome encontrado (precisa estar carregado).");
            return;
        }
        Menu m = menu(title);
        int slot = 0;
        for (Wolf w : dogs) {
            if (slot >= 45) break;
            Location l = w.getLocation();
            List<String> lore = new ArrayList<>();
            lore.add("Dono: " + Dogs.ownerName(w));
            lore.add(l.getWorld().getName() + "  " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ());
            if (plugin.possession().isPossessed(w)) lore.add("» controlado agora");
            if (plugin.monsters().isMonster(w)) lore.add("» é um MONSTRO");
            Material icon = plugin.monsters().isMonster(w) ? Material.SKELETON_SPAWN_EGG : Material.WOLF_SPAWN_EGG;
            button(m, slot++, icon, Dogs.displayName(w, "sem nome"), NamedTextColor.GREEN, lore, () -> onPick.accept(w));
        }
        nav(m, p, pl -> back.run());
        finish(m);
        p.openInventory(m.inventory);
    }

    // ================================================================== JOGADORES

    private void openPlayers(Player p) {
        List<Player> targets = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p)) targets.add(o);
        if (targets.isEmpty()) {
            CompanionAI.error(p, "Não tem mais ninguém online.");
            return;
        }
        Menu m = menu("Escolha um jogador");
        int slot = 10;
        for (Player t : targets) {
            if (slot >= 44) break;
            if (slot % 9 == 8) slot += 2; // mantém borda
            UUID id = t.getUniqueId();
            m.inventory.setItem(slot, head(t, List.of(
                    "Vida: " + hearts(t.getHealth()),
                    "Clique para efeitos, sustos e TP")));
            m.actions.put(slot, () -> {
                Player target = Bukkit.getPlayer(id);
                if (target == null) {
                    CompanionAI.error(p, "Esse jogador saiu.");
                    openPlayers(p);
                } else {
                    openPlayer(p, target);
                }
            });
            slot++;
        }
        nav(m, p, this::openHome);
        finish(m);
        p.openInventory(m.inventory);
    }

    private void openPlayer(Player p, Player target) {
        UUID tid = target.getUniqueId();
        Menu m = menu("Jogador: " + target.getName());
        FxSettings fx = fxSettings.computeIfAbsent(p.getUniqueId(), k -> new FxSettings());
        boolean icon = plugin.getConfig().getBoolean("efeitos-mostrar-icone", false);

        Location tl = target.getLocation();
        m.inventory.setItem(4, head(target, List.of(
                "Vida: " + hearts(target.getHealth()),
                "Mundo: " + tl.getWorld().getName(),
                "Posição: " + tl.getBlockX() + ", " + tl.getBlockY() + ", " + tl.getBlockZ())));

        Consumer<Player> reopen = pl -> {
            Player t = Bukkit.getPlayer(tid);
            if (t != null) openPlayer(pl, t);
            else openPlayers(pl);
        };

        // --- Efeitos (2 linhas)
        label(m, 9, "Efeitos");
        int[] fxSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        Effects[] all = Effects.values();
        for (int i = 0; i < all.length && i < fxSlots.length; i++) {
            Effects e = all[i];
            boolean has = target.hasPotionEffect(e.type);
            List<String> lore = new ArrayList<>();
            lore.add(e.description);
            lore.add("");
            lore.add("Clique: aplicar por " + durationText(DURATIONS[fx.durationIndex]) + " (nível " + roman(fx.level) + ")");
            if (has) lore.add("» Ele está com esse efeito agora");
            button(m, fxSlots[i], e.icon, e.label, has ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.WHITE, lore, () -> {
                Player t = Bukkit.getPlayer(tid);
                if (t == null) return;
                e.apply(t, DURATIONS[fx.durationIndex], fx.level, icon);
                CompanionAI.actionBar(p, e.label + " em " + t.getName() + " (" + durationText(DURATIONS[fx.durationIndex]) + ")");
                reopen.accept(p);
            });
            if (has) glow(m, fxSlots[i]);
        }

        // --- Controles de efeito
        button(m, 28, Material.CLOCK, "Duração: " + durationText(DURATIONS[fx.durationIndex]), NamedTextColor.GOLD,
                List.of("Clique para trocar", "5s → 10s → 30s → 1min → 5min → infinito"), () -> {
                    fx.durationIndex = (fx.durationIndex + 1) % DURATIONS.length;
                    reopen.accept(p);
                });
        button(m, 29, Material.EXPERIENCE_BOTTLE, "Nível: " + roman(fx.level), NamedTextColor.GOLD,
                List.of("Clique para trocar (I a " + roman(MAX_LEVEL) + ")", "Mais alto = mais forte"), () -> {
                    fx.level = fx.level % MAX_LEVEL + 1;
                    reopen.accept(p);
                });
        button(m, 30, Material.MILK_BUCKET, "Tirar todos os efeitos", NamedTextColor.WHITE,
                List.of("Limpa tudo dele"), () -> {
                    Player t = Bukkit.getPlayer(tid);
                    if (t != null) Effects.clearAll(t);
                    reopen.accept(p);
                });

        // --- Teleporte
        button(m, 32, Material.ENDER_PEARL, "TP até ele", NamedTextColor.DARK_AQUA,
                List.of("Você vai até ele"), () -> {
                    Player t = Bukkit.getPlayer(tid);
                    if (t == null) return;
                    p.closeInventory();
                    p.teleport(t.getLocation());
                });
        button(m, 33, Material.CHORUS_FRUIT, "Trazer ele até mim", NamedTextColor.DARK_AQUA,
                List.of("Ele aparece onde você está", "(ex.: na frente do monstro)"), () -> {
                    Player t = Bukkit.getPlayer(tid);
                    if (t == null) return;
                    p.closeInventory();
                    Vector fwd = p.getLocation().getDirection().setY(0);
                    if (fwd.lengthSquared() < 1e-4) fwd = new Vector(0, 0, 1);
                    fwd.normalize();
                    Location dest = p.getLocation().add(fwd.clone().multiply(3));
                    dest.setDirection(fwd.clone().multiply(-1));
                    t.teleport(dest);
                });
        button(m, 34, Material.ENDER_EYE, "Olhar fixo", NamedTextColor.DARK_AQUA,
                List.of("Vira a cabeça dele para o", "cachorro que você controla"), () -> {
                    Player t = Bukkit.getPlayer(tid);
                    if (t != null) forceLook(p, t);
                });

        // --- Sustos
        label(m, 36, "Sustos");
        button(m, 37, Material.SCULK_SENSOR, "Som de caverna", NamedTextColor.DARK_GREEN,
                List.of("Só ele escuta"), () -> withTarget(tid, t ->
                        t.playSound(t.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 1f, 1f)));
        button(m, 38, Material.LEATHER_BOOTS, "Passos atrás dele", NamedTextColor.DARK_GREEN,
                List.of("Só ele escuta"), () -> withTarget(tid, this::footsteps));
        button(m, 39, Material.BONE_MEAL, "Rosnado atrás dele", NamedTextColor.DARK_GREEN,
                List.of("Só ele escuta"), () -> withTarget(tid, t ->
                        t.playSound(behind(t, 2.5), Sound.ENTITY_WOLF_GROWL, SoundCategory.HOSTILE, 1f, 0.55f)));
        button(m, 40, Material.LIGHTNING_ROD, "Raio perto dele", NamedTextColor.DARK_GREEN,
                List.of("Só o efeito, não machuca"), () -> withTarget(tid, t -> {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    Location l = t.getLocation().add(r.nextDouble(-8, 8), 0, r.nextDouble(-8, 8));
                    l.setY(t.getWorld().getHighestBlockYAt(l) + 1);
                    t.getWorld().strikeLightningEffect(l);
                }));
        button(m, 41, Material.CARVED_PUMPKIN, "Jumpscare", NamedTextColor.DARK_GREEN,
                List.of("Som alto + tela tremendo", "+ texto vermelho"), () -> withTarget(tid, this::jumpscare));
        button(m, 42, Material.OAK_SIGN, "Mensagem na tela", NamedTextColor.DARK_GREEN,
                List.of("Você digita, aparece grande", "só na tela dele"), () -> withTarget(tid, t ->
                        plugin.prompts().ask(p, "Mensagem na tela de " + t.getName(), "", msg -> {
                            t.showTitle(Title.title(Component.text(msg, NamedTextColor.DARK_RED), Component.empty(),
                                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1))));
                            t.playSound(t.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 0.6f);
                            reopen.accept(p);
                        })));
        button(m, 43, Material.PAPER, "Sussurro no chat", NamedTextColor.DARK_GREEN,
                List.of("O cachorro manda uma mensagem", "que SÓ ele vê no chat"), () -> withTarget(tid, t ->
                        plugin.prompts().ask(p, "Sussurro para " + t.getName(), "", msg -> {
                            Wolf w = plugin.possession().wolfOf(p);
                            String name = w != null ? Dogs.displayName(w, "???")
                                    : plugin.getConfig().getString("nome-sem-nome", "???");
                            t.sendMessage(Component.text("<" + name + "> ").append(Component.text(msg, NamedTextColor.GRAY)
                                    .decorate(TextDecoration.ITALIC)));
                            CompanionAI.info(p, "Sussurro enviado só para " + t.getName() + ".");
                            reopen.accept(p);
                        })));

        nav(m, p, this::openPlayers);
        finish(m);
        p.openInventory(m.inventory);
    }

    // ================================================================== MUNDO

    private void openWorld(Player p) {
        Menu m = menu("Mundo");
        World w = p.getWorld();

        label(m, 9, "Clima");
        button(m, 11, Material.SUNFLOWER, "Sol", NamedTextColor.YELLOW, List.of(), () -> weather(p, false, false));
        button(m, 12, Material.WATER_BUCKET, "Chuva", NamedTextColor.BLUE, List.of(), () -> weather(p, true, false));
        button(m, 13, Material.LIGHTNING_ROD, "Tempestade", NamedTextColor.DARK_BLUE, List.of(), () -> weather(p, true, true));

        label(m, 27, "Hora");
        button(m, 29, Material.CLOCK, "Manhã", NamedTextColor.YELLOW, List.of(), () -> time(p, 1000));
        button(m, 30, Material.ORANGE_DYE, "Pôr do sol", NamedTextColor.GOLD, List.of(), () -> time(p, 12500));
        button(m, 31, Material.BLACK_DYE, "Meia-noite", NamedTextColor.DARK_GRAY, List.of(), () -> time(p, 18000));
        Boolean cycle = w.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE);
        boolean running = cycle == null || cycle;
        button(m, 33, Material.DAYLIGHT_DETECTOR, running ? "Congelar a hora" : "Descongelar a hora", NamedTextColor.GOLD,
                List.of(running ? "A hora para de passar" : "A hora volta a passar",
                        "Dica: meia-noite + congelar = noite eterna"), () -> {
                    w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, !running);
                    openWorld(p);
                });

        nav(m, p, this::openHome);
        finish(m);
        p.openInventory(m.inventory);
    }

    // ================================================================== ações

    private void toggleMonster(Player p, Wolf w) {
        boolean on = plugin.monsters().toggle(w);
        CompanionAI.info(p, Dogs.displayName(w, "Cachorro") + (on ? " virou MONSTRO." : " voltou a ser cachorro."));
    }

    private void askRename(Player p, Wolf w, Runnable after) {
        plugin.prompts().ask(p, "Novo nome do cachorro", Dogs.displayName(w, ""), name -> {
            if (!w.isValid()) return;
            plugin.monsters().setDogName(w, name);
            CompanionAI.info(p, "Cachorro renomeado para \"" + name + "\".");
            after.run();
        });
    }

    private void withTarget(UUID id, Consumer<Player> action) {
        Player t = Bukkit.getPlayer(id);
        if (t != null) action.accept(t);
    }

    private void forceLook(Player p, Player t) {
        Wolf wolf = plugin.possession().wolfOf(p);
        if (wolf == null || !wolf.getWorld().equals(t.getWorld())) {
            CompanionAI.error(p, "Entre num cachorro perto dele primeiro.");
            return;
        }
        double height = plugin.monsters().isMonster(wolf) ? 2.8 : 0.5;
        Location eye = t.getLocation();
        Vector dir = wolf.getLocation().add(0, height, 0).toVector().subtract(t.getEyeLocation().toVector());
        eye.setDirection(dir);
        t.teleport(eye);
    }

    private void jumpscare(Player t) {
        t.playSound(t.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 1f, 0.5f);
        t.playSound(t.getLocation(), Sound.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 1f, 1.2f);
        t.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0, false, false, false));
        t.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
        t.showTitle(Title.title(Component.text("▓▓▓▓▓▓", NamedTextColor.DARK_RED),
                Component.text("ele está aqui", NamedTextColor.RED),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(500))));
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

    // ================================================================== helpers de tela

    private static Menu menu(String title) {
        Menu m = new Menu();
        m.inventory = Bukkit.createInventory(m, 54, text(title, NamedTextColor.DARK_PURPLE));
        return m;
    }

    /** Voltar / Fechar / Início na última linha. */
    private void nav(Menu m, Player p, Consumer<Player> back) {
        button(m, SLOT_BACK, Material.ARROW, "Voltar", NamedTextColor.GRAY, List.of(), () -> back.accept(p));
        button(m, SLOT_CLOSE, Material.SPRUCE_DOOR, "Fechar", NamedTextColor.GRAY, List.of(), p::closeInventory);
        button(m, SLOT_HOME, Material.COMPASS, "Início", NamedTextColor.GRAY, List.of(), () -> openHome(p));
    }

    /** Preenche espaços vazios com vidro e borda escura. */
    private static void finish(Menu m) {
        ItemStack border = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack inner = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) {
            if (m.inventory.getItem(i) != null) continue;
            int row = i / 9;
            int col = i % 9;
            boolean edge = row == 0 || row == 5 || col == 0 || col == 8;
            m.inventory.setItem(i, edge ? border : inner);
        }
    }

    private static ItemStack pane(Material mat) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(" "));
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    /** Placa de seção (vidro colorido com nome). */
    private static void label(Menu m, int slot, String name) {
        ItemStack it = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(text("» " + name, NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
        it.setItemMeta(meta);
        m.inventory.setItem(slot, it);
    }

    private void statusItem(Menu m, int slot, Player p) {
        PossessionManager pos = plugin.possession();
        Wolf w = pos.wolfOf(p);
        boolean monster = w != null && plugin.monsters().isMonster(w);
        String forma = w == null ? "humano" : (monster ? "MONSTRO" : "cachorro");
        List<String> lore = new ArrayList<>();
        lore.add("Forma: " + forma);
        if (w != null) {
            lore.add("Cachorro: " + Dogs.displayName(w, "sem nome"));
            lore.add("Dono: " + Dogs.ownerName(w));
        }
        lore.add("Invisível: " + (plugin.vanish().isHidden(p) ? "SIM" : "não"));
        ItemStack it = new ItemStack(monster ? Material.SKELETON_SKULL : (w != null ? Material.BONE : Material.BOOK));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(text("Status", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        meta.lore(lore.stream().map(s -> text(s, NamedTextColor.GRAY)).toList());
        it.setItemMeta(meta);
        m.inventory.setItem(slot, it);
    }

    private void vanishButton(Menu m, int slot, Player p, Runnable after) {
        boolean hidden = plugin.vanish().isHidden(p);
        button(m, slot, hidden ? Material.ENDER_EYE : Material.GLASS,
                hidden ? "Aparecer" : "Ficar invisível", NamedTextColor.LIGHT_PURPLE,
                List.of(hidden ? "Manda \"entrou no jogo\" falso" : "Manda \"saiu do jogo\" falso",
                        "e some/aparece na lista (TAB)"), () -> {
                    if (plugin.possession().isPossessing(p) && hidden) {
                        CompanionAI.error(p, "Saia do cachorro primeiro.");
                        return;
                    }
                    plugin.vanish().toggle(p);
                    after.run();
                });
    }

    private static ItemStack head(Player t, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(t);
        meta.displayName(text(t.getName(), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
        meta.lore(lore.stream().map(s -> text(s, NamedTextColor.GRAY)).toList());
        head.setItemMeta(meta);
        return head;
    }

    private static void button(Menu m, int slot, Material mat, String name, NamedTextColor color,
                               List<String> lore, Runnable action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name, color).decorate(TextDecoration.BOLD));
        meta.lore(lore.stream().map(s -> text(s, NamedTextColor.GRAY)).toList());
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        m.inventory.setItem(slot, item);
        if (action != null) m.actions.put(slot, action);
    }

    private static void glow(Menu m, int slot) {
        ItemStack it = m.inventory.getItem(slot);
        if (it == null) return;
        ItemMeta meta = it.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
    }

    private static Component text(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    static String durationText(int seconds) {
        if (seconds <= 0) return "infinito";
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "min";
    }

    static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    private static String hearts(double hp) {
        return String.format(java.util.Locale.ROOT, "%.1f ❤", hp / 2.0);
    }
}
