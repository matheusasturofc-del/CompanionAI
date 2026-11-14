package dev.companionai;

import dev.companionai.monster.MonsterRig;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Entrar" no cachorro: o admin fica invisível/escondido e um lobo copia cada passo dele.
 * O que o admin digita no chat sai como fala do cachorro (chat + balão).
 */
public final class PossessionManager implements Listener {

    /** Estado de uma possessão. */
    private static final class Session {
        final UUID controller;
        final UUID wolfId;
        boolean sitting;
        /** Se true, o próprio jogador vê o cachorro (bom para olhar no F5). */
        boolean selfVisible;
        // estado original do jogador
        double origScale;
        double origStep;
        boolean origInvulnerable;
        boolean origCollidable;
        boolean hadInvisibility;
        // estado original do lobo
        boolean wolfOrigAI;
        boolean wolfOrigInvulnerable;
        boolean wolfOrigCollidable;
        // balão de fala
        TextDisplay bubble;
        int bubbleTicks;

        Session(UUID controller, UUID wolfId) {
            this.controller = controller;
            this.wolfId = wolfId;
        }
    }

    private final CompanionAI plugin;
    private final Map<UUID, Session> byController = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> byWolf = new ConcurrentHashMap<>();

    PossessionManager(CompanionAI plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ API

    public boolean isPossessing(Player p) {
        return byController.containsKey(p.getUniqueId());
    }

    public boolean isPossessed(Entity wolf) {
        return byWolf.containsKey(wolf.getUniqueId());
    }

    public @Nullable Player controllerOf(Entity wolf) {
        UUID id = byWolf.get(wolf.getUniqueId());
        return id == null ? null : Bukkit.getPlayer(id);
    }

    /**
     * Para quem as partes da cabeça do monstro devem ficar escondidas
     * (o controlador, a menos que ele tenha ligado o modo "me ver").
     */
    public @Nullable Player hideHeadFor(Entity wolf) {
        UUID id = byWolf.get(wolf.getUniqueId());
        if (id == null) return null;
        Session s = byController.get(id);
        if (s == null || s.selfVisible) return null;
        return Bukkit.getPlayer(id);
    }

    /** Liga/desliga ver o próprio cachorro (no F5). */
    public boolean toggleSelfView(Player p) {
        Session s = byController.get(p.getUniqueId());
        if (s == null) return false;
        Wolf wolf = wolf(s);
        if (wolf == null) return false;
        s.selfVisible = !s.selfVisible;
        if (s.selfVisible) p.showEntity(plugin, wolf);
        else p.hideEntity(plugin, wolf);
        MonsterRig rig = plugin.monsters().rig(wolf);
        if (rig != null) rig.setController(s.selfVisible ? null : p);
        if (s.selfVisible) {
            CompanionAI.info(p, "Agora você SE VÊ como cachorro. Aperte F5 para olhar.");
            CompanionAI.info(p, "Em primeira pessoa a cabeça do cachorro pode tapar a visão: use /horror ver de novo para esconder.");
        } else {
            CompanionAI.info(p, "Cachorro escondido da sua tela de novo (seu amigo continua vendo).");
        }
        return true;
    }

    public @Nullable Wolf wolfOf(Player p) {
        Session s = byController.get(p.getUniqueId());
        return s == null ? null : wolf(s);
    }

    /** Vira um cachorro novo (selvagem) no lugar onde o jogador está. */
    public void turnNew(Player p) {
        Location loc = p.getLocation();
        Wolf wolf = loc.getWorld().spawn(loc, Wolf.class, w -> {
            w.setAI(false);
            w.setPersistent(true);
            w.setRemoveWhenFarAway(false);
        });
        start(p, wolf);
    }

    /** Entra num cachorro que já existe (ex.: o que o amigo domesticou). */
    public boolean turnInto(Player p, Wolf wolf) {
        UUID current = byWolf.get(wolf.getUniqueId());
        if (current != null && !current.equals(p.getUniqueId())) {
            CompanionAI.error(p, "Esse cachorro já está sendo controlado por outra pessoa.");
            return false;
        }
        start(p, wolf);
        return true;
    }

    private void start(Player p, Wolf wolf) {
        stop(p, false);

        Session s = new Session(p.getUniqueId(), wolf.getUniqueId());
        s.wolfOrigAI = wolf.hasAI();
        s.wolfOrigInvulnerable = wolf.isInvulnerable();
        s.wolfOrigCollidable = wolf.isCollidable();
        // Lobo recém-criado: depois de sair ele deve voltar a andar sozinho.
        if (!wolf.hasAI() && !wolf.getPersistentDataContainer().has(plugin.possessedKey())) s.wolfOrigAI = true;
        s.sitting = wolf.isSitting();

        AttributeInstance scale = p.getAttribute(Attribute.GENERIC_SCALE);
        AttributeInstance step = p.getAttribute(Attribute.GENERIC_STEP_HEIGHT);
        s.origScale = scale != null ? scale.getBaseValue() : 1.0;
        s.origStep = step != null ? step.getBaseValue() : 0.6;
        s.origInvulnerable = p.isInvulnerable();
        s.origCollidable = p.isCollidable();
        s.hadInvisibility = p.hasPotionEffect(PotionEffectType.INVISIBILITY);

        byController.put(s.controller, s);
        byWolf.put(s.wolfId, s.controller);

        // O lobo para de pensar sozinho e passa a copiar o jogador.
        wolf.setAI(false);
        wolf.setInvulnerable(true);
        wolf.setCollidable(false);
        wolf.setPersistent(true);
        wolf.setRemoveWhenFarAway(false);
        wolf.getPersistentDataContainer().set(plugin.possessedKey(), PersistentDataType.BYTE, (byte) 1);

        // O jogador vai até o cachorro (se entrou num cachorro existente).
        Location dest = wolf.getLocation().clone();
        dest.setYaw(p.getLocation().getYaw());
        dest.setPitch(p.getLocation().getPitch());
        p.teleport(dest);

        // "Sai" do servidor para o amigo.
        plugin.vanish().hide(p, true);

        p.setInvulnerable(true);
        p.setCollidable(false);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        p.hideEntity(plugin, wolf);

        applyForm(p, wolf);
        MonsterRig rig = plugin.monsters().rig(wolf);
        if (rig != null) rig.setController(p);

        String name = Dogs.displayName(wolf, plugin.getConfig().getString("nome-sem-nome", "???"));
        CompanionAI.info(p, "Você agora é o cachorro \"" + name + "\". Tudo que digitar no chat sai pela boca dele.");
        CompanionAI.info(p, "Para voltar: /horror sair (ou pelo painel).");
        CompanionAI.info(p, "Você não vê o cachorro na sua tela (seu amigo vê). Para se ver no F5: /horror ver");
        if (s.sitting) CompanionAI.info(p, "Ele está SENTADO: aperte Shift para levantar.");
        if (plugin.monsters().isMonster(wolf)) CompanionAI.info(p, "Forma de MONSTRO: você quebra blocos e machuca jogadores.");
    }

    /** Ajusta tamanho/altura do jogador conforme forma (cachorro ou monstro). */
    public void applyForm(Player p, Wolf wolf) {
        boolean monster = plugin.monsters().isMonster(wolf);
        double scale = monster
                ? plugin.getConfig().getDouble("escala-monstro", 1.73)
                : plugin.getConfig().getDouble("escala-cachorro", 0.42);
        AttributeInstance a = p.getAttribute(Attribute.GENERIC_SCALE);
        if (a != null) a.setBaseValue(scale);
        AttributeInstance step = p.getAttribute(Attribute.GENERIC_STEP_HEIGHT);
        if (step != null) step.setBaseValue(monster ? 1.1 : 0.6);

        // Monstro: quebra blocos rápido e corre um pouco mais.
        p.removePotionEffect(PotionEffectType.HASTE);
        p.removePotionEffect(PotionEffectType.SPEED);
        if (monster) {
            int haste = plugin.getConfig().getInt("monstro-pressa", 2);
            int speed = plugin.getConfig().getInt("monstro-velocidade", 1);
            if (haste > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, haste - 1, false, false, false));
            if (speed > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, speed - 1, false, false, false));
        }
    }

    /** O jogador está dentro de um cachorro que está na forma de monstro? */
    public boolean isMonsterController(Player p) {
        Wolf w = wolfOf(p);
        return w != null && plugin.monsters().isMonster(w);
    }

    /** Rugido do monstro: som alto e escuridão rápida em quem estiver perto. */
    public boolean roar(Player p) {
        Wolf wolf = wolfOf(p);
        if (wolf == null || !plugin.monsters().isMonster(wolf)) return false;
        Location l = wolf.getLocation();
        l.getWorld().playSound(l, Sound.ENTITY_WARDEN_ROAR, 2f, 0.8f);
        l.getWorld().playSound(l, Sound.ENTITY_WOLF_GROWL, 2f, 0.5f);
        for (Player o : l.getWorld().getPlayers()) {
            if (o.equals(p) || o.getLocation().distanceSquared(l) > 24 * 24) continue;
            o.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, false));
        }
        MonsterRig rig = plugin.monsters().rig(wolf);
        if (rig != null) {
            rig.attack();
            rig.talk(30);
        }
        return true;
    }

    /** Chamado quando o monstro liga/desliga num cachorro. */
    public void refreshForm(Wolf wolf) {
        Player p = controllerOf(wolf);
        if (p != null) applyForm(p, wolf);
    }

    public boolean stop(Player p, boolean feedback) {
        Session s = byController.remove(p.getUniqueId());
        if (s == null) return false;
        byWolf.remove(s.wolfId);
        removeBubble(s);

        Wolf wolf = wolf(s);
        if (wolf != null) {
            wolf.setAI(s.wolfOrigAI);
            wolf.setInvulnerable(s.wolfOrigInvulnerable);
            wolf.setCollidable(s.wolfOrigCollidable);
            wolf.setSitting(s.sitting);
            wolf.getPersistentDataContainer().remove(plugin.possessedKey());
            p.showEntity(plugin, wolf);
            MonsterRig rig = plugin.monsters().rig(wolf);
            if (rig != null) rig.setController(null);
        }

        AttributeInstance scale = p.getAttribute(Attribute.GENERIC_SCALE);
        if (scale != null) scale.setBaseValue(s.origScale);
        AttributeInstance step = p.getAttribute(Attribute.GENERIC_STEP_HEIGHT);
        if (step != null) step.setBaseValue(s.origStep);
        p.setInvulnerable(s.origInvulnerable);
        p.setCollidable(s.origCollidable);
        if (!s.hadInvisibility) p.removePotionEffect(PotionEffectType.INVISIBILITY);
        p.removePotionEffect(PotionEffectType.HASTE);
        p.removePotionEffect(PotionEffectType.SPEED);

        if (feedback) {
            CompanionAI.info(p, "Você voltou ao normal. Ainda está invisível para os outros.");
            CompanionAI.info(p, "Para \"entrar no servidor\" de novo: /horror vanish (ou pelo painel).");
        }
        return true;
    }

    public void stopAll() {
        for (Session s : List.copyOf(byController.values())) {
            Player p = Bukkit.getPlayer(s.controller);
            if (p != null) stop(p, false);
        }
    }

    public boolean toggleSit(Player controller, @Nullable String byWho) {
        Session s = byController.get(controller.getUniqueId());
        if (s == null) return false;
        Wolf wolf = wolf(s);
        if (wolf == null) return false;
        s.sitting = !s.sitting;
        wolf.setSitting(s.sitting);
        if (byWho == null) {
            CompanionAI.actionBar(controller, s.sitting ? "Você SENTOU (Shift para levantar)" : "Você LEVANTOU");
        } else {
            CompanionAI.actionBar(controller, s.sitting
                    ? byWho + " mandou você SENTAR (fique parado)"
                    : byWho + " mandou você LEVANTAR");
        }
        return true;
    }

    /** O cachorro fala: mensagem no chat + balão em cima dele. */
    public void speak(Player controller, Component message) {
        Session s = byController.get(controller.getUniqueId());
        if (s == null) return;
        Wolf wolf = wolf(s);
        if (wolf == null) return;

        String fallback = plugin.getConfig().getString("nome-sem-nome", "???");
        Component name = Component.text(Dogs.displayName(wolf, fallback));
        String format = plugin.getConfig().getString("formato-chat", "<{nome}> {mensagem}");
        Component line = Component.text(format)
                .replaceText(TextReplacementConfig.builder().matchLiteral("{nome}").replacement(name).build())
                .replaceText(TextReplacementConfig.builder().matchLiteral("{mensagem}").replacement(message).build());
        Bukkit.getServer().sendMessage(line);

        boolean monster = plugin.monsters().isMonster(wolf);
        removeBubble(s);
        Location at = bubbleLocation(wolf, monster);
        s.bubble = at.getWorld().spawn(at, TextDisplay.class, t -> {
            t.text(message.color(NamedTextColor.BLACK));
            t.setBillboard(Display.Billboard.CENTER);
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(230, 255, 255, 255));
            t.setLineWidth(170);
            t.setShadowed(false);
            t.setSeeThrough(false);
            t.setPersistent(false);
            t.setTeleportDuration(1);
        });
        controller.hideEntity(plugin, s.bubble);

        int len = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(message).length();
        int min = plugin.getConfig().getInt("balao-segundos-min", 3) * 20;
        int max = plugin.getConfig().getInt("balao-segundos-max", 12) * 20;
        s.bubbleTicks = Math.max(min, Math.min(max, 40 + len * 3));

        if (monster) {
            wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_GROWL, 0.7f, 0.5f);
            MonsterRig rig = plugin.monsters().rig(wolf);
            if (rig != null) rig.talk(Math.min(60, 10 + len * 2));
        } else {
            wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 0.5f, 1.0f);
        }
    }

    // ------------------------------------------------------------------ tick

    void tick() {
        for (Session s : List.copyOf(byController.values())) {
            Player p = Bukkit.getPlayer(s.controller);
            if (p == null) continue;
            Wolf wolf = wolf(s);
            if (wolf == null || wolf.isDead()) {
                stop(p, false);
                CompanionAI.error(p, "O cachorro sumiu, você voltou ao normal.");
                continue;
            }

            Location loc = p.getLocation();
            if (!s.sitting) {
                Location target = loc.clone();
                target.setPitch(Math.max(-60f, Math.min(60f, loc.getPitch())));
                Location cur = wolf.getLocation();
                boolean sameWorld = cur.getWorld().equals(target.getWorld());
                if (!sameWorld || cur.distanceSquared(target) > 1.0E-4
                        || Math.abs(cur.getYaw() - target.getYaw()) > 0.5f
                        || Math.abs(cur.getPitch() - target.getPitch()) > 0.5f) {
                    wolf.teleport(target);
                }
                wolf.setRotation(target.getYaw(), target.getPitch());
                wolf.setBodyYaw(target.getYaw());
            }

            // Coleira: se o dono puxar, o jogador é arrastado como um cachorro.
            if (wolf.isLeashed()) {
                Entity holder = wolf.getLeashHolder();
                if (holder.getWorld().equals(loc.getWorld())) {
                    double d = holder.getLocation().distance(loc);
                    if (d > 5.0) {
                        Vector pull = holder.getLocation().toVector().subtract(loc.toVector()).normalize()
                                .multiply(Math.min(0.5, (d - 5.0) * 0.15));
                        p.setVelocity(p.getVelocity().add(pull));
                    }
                }
            }

            if (s.bubble != null) {
                if (--s.bubbleTicks <= 0 || !s.bubble.isValid()) {
                    removeBubble(s);
                } else {
                    s.bubble.teleport(bubbleLocation(wolf, plugin.monsters().isMonster(wolf)));
                }
            }
        }
    }

    private Location bubbleLocation(Wolf wolf, boolean monster) {
        double y = monster ? 3.75 : (wolf.isSitting() ? 1.45 : 1.7);
        Location l = wolf.getLocation().clone().add(0, y, 0);
        l.setPitch(0);
        return l;
    }

    private void removeBubble(Session s) {
        if (s.bubble != null) {
            s.bubble.remove();
            s.bubble = null;
        }
    }

    private @Nullable Wolf wolf(Session s) {
        Entity e = Bukkit.getEntity(s.wolfId);
        return e instanceof Wolf w ? w : null;
    }

    /** Lobos que ficaram travados sem IA (servidor caiu durante a brincadeira). */
    void recover(Iterable<? extends Entity> entities) {
        for (Entity e : entities) {
            if (!(e instanceof Wolf w)) continue;
            if (!w.getPersistentDataContainer().has(plugin.possessedKey())) continue;
            if (byWolf.containsKey(w.getUniqueId())) continue;
            w.setAI(true);
            w.setInvulnerable(false);
            w.setCollidable(true);
            w.getPersistentDataContainer().remove(plugin.possessedKey());
        }
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        recover(e.getEntities());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Component msg = e.message();
        if (byController.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> speak(p, msg));
            return;
        }
        if (plugin.vanish().isHidden(p)) {
            // Proteção: escondido e fora do cachorro, sua mensagem entregaria tudo.
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                CompanionAI.error(p, "Você está invisível: essa mensagem NÃO foi enviada (só você vê).");
                p.sendMessage(Component.text("  > ", NamedTextColor.DARK_GRAY).append(msg.color(NamedTextColor.GRAY)));
            });
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractWolf(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Wolf wolf)) return;
        UUID controllerId = byWolf.get(wolf.getUniqueId());
        if (controllerId == null) return;
        if (e.getHand() != EquipmentSlot.HAND) {
            e.setCancelled(true);
            return;
        }
        Player controller = Bukkit.getPlayer(controllerId);
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        Material type = hand.getType();

        if (!wolf.isTamed()) {
            e.setCancelled(true);
            if (type != Material.BONE) return;
            consumeOne(p, hand);
            int chance = Math.max(1, plugin.getConfig().getInt("chance-domesticar", 3));
            if (ThreadLocalRandom.current().nextInt(chance) == 0) {
                wolf.setTamed(true);
                wolf.setOwner(p);
                wolf.getWorld().spawnParticle(Particle.HEART, wolf.getLocation().add(0, 0.8, 0), 7, 0.3, 0.3, 0.3);
                if (controller != null) CompanionAI.actionBar(controller, p.getName() + " te domesticou!");
                if (Dogs.plainName(wolf) == null) askDogName(p, wolf);
            } else {
                wolf.getWorld().spawnParticle(Particle.SMOKE, wolf.getLocation().add(0, 0.8, 0), 7, 0.3, 0.3, 0.3, 0.01);
            }
            return;
        }

        AnimalTamer owner = wolf.getOwner();
        if (owner == null || !owner.getUniqueId().equals(p.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        // Coleira e etiqueta de nome: deixa o Minecraft fazer o normal.
        if (type == Material.LEAD || type == Material.NAME_TAG) return;

        e.setCancelled(true);
        if (Tag.ITEMS_WOLF_FOOD.isTagged(type)) {
            consumeOne(p, hand);
            wolf.setHealth(Math.min(wolf.getHealth() + 4, maxHealth(wolf)));
            wolf.getWorld().spawnParticle(Particle.HEART, wolf.getLocation().add(0, 0.8, 0), 4, 0.3, 0.3, 0.3);
            wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);
            if (controller != null) CompanionAI.actionBar(controller, p.getName() + " te deu comida");
            return;
        }
        if (controller != null) toggleSit(controller, p.getName());
    }

    private static double maxHealth(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return a == null ? 20 : a.getValue();
    }

    private static void consumeOne(Player p, ItemStack hand) {
        if (p.getGameMode() == GameMode.CREATIVE) return;
        hand.setAmount(hand.getAmount() - 1);
    }

    /** Quando qualquer cachorro é domesticado normalmente, abre a tela de nome. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        if (!(e.getEntity() instanceof Wolf wolf)) return;
        if (!(e.getOwner() instanceof Player p)) return;
        if (Dogs.plainName(wolf) != null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (wolf.isValid() && p.isOnline() && Dogs.plainName(wolf) == null) askDogName(p, wolf);
        }, 2L);
    }

    private void askDogName(Player p, Wolf wolf) {
        plugin.prompts().ask(p, "Dê um nome ao seu cachorro", "", name -> {
            if (!wolf.isValid()) return;
            plugin.monsters().setDogName(wolf, name);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Session s = byController.get(e.getPlayer().getUniqueId());
        if (s == null || !s.sitting) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;
        Location fixed = from.clone();
        fixed.setYaw(to.getYaw());
        fixed.setPitch(to.getPitch());
        e.setTo(fixed);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity victim = e.getEntity();
        if (byWolf.containsKey(victim.getUniqueId()) || byController.containsKey(victim.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        Session s = byController.get(p.getUniqueId());
        if (s == null) return;
        Wolf wolf = wolf(s);
        if (wolf == null || e.getEntity().equals(wolf)) {
            e.setCancelled(true);
            return;
        }
        if (plugin.monsters().isMonster(wolf)) {
            // Monstro morde de verdade.
            e.setDamage(plugin.getConfig().getDouble("monstro-dano", 6.0));
            wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_GROWL, 1.2f, 0.5f);
            wolf.getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_EVOKER_FANGS_ATTACK, 1f, 0.6f);
            MonsterRig rig = plugin.monsters().rig(wolf);
            if (rig != null) rig.attack();
        } else if (plugin.getConfig().getBoolean("cachorro-morde", false)) {
            e.setDamage(plugin.getConfig().getDouble("cachorro-dano", 2.0));
            wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 1.1f);
        } else {
            // Cachorro normal não bate (um "golpe invisível" entregaria a brincadeira).
            e.setCancelled(true);
        }
    }

    /** Se você matar alguém, a mensagem de morte cita o cachorro/monstro, nunca você. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerKilled(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        Wolf wolf = wolfOf(killer);
        if (wolf == null) return;
        String name = Dogs.displayName(wolf, plugin.getConfig().getString("nome-sem-nome", "???"));
        e.deathMessage(Component.translatable("death.attack.mob", e.getEntity().displayName(), Component.text(name)));
    }

    /** Shift = sentar/levantar por conta própria. */
    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        Player p = e.getPlayer();
        if (!byController.containsKey(p.getUniqueId())) return;
        toggleSit(p, null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() != null && byController.containsKey(e.getTarget().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!byController.containsKey(p.getUniqueId())) return;
        boolean allowed = isMonsterController(p) && plugin.getConfig().getBoolean("monstro-quebra-blocos", true);
        if (!allowed) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (byController.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (byController.containsKey(e.getEntity().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (byController.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stop(e.getPlayer(), false);
    }

    @EventHandler
    public void onWolfDeath(EntityDeathEvent e) {
        UUID controllerId = byWolf.get(e.getEntity().getUniqueId());
        if (controllerId == null) return;
        Player p = Bukkit.getPlayer(controllerId);
        if (p != null) stop(p, true);
    }
}
