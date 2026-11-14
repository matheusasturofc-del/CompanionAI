package dev.companionai.monster;

import dev.companionai.CompanionAI;
import dev.companionai.Dogs;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Liga/desliga a forma de monstro em qualquer cachorro (controlado por você ou não). */
public final class MonsterManager implements Listener {

    private final CompanionAI plugin;
    private final Map<UUID, MonsterRig> rigs = new ConcurrentHashMap<>();

    public MonsterManager(CompanionAI plugin) {
        this.plugin = plugin;
    }

    public boolean isMonster(Entity wolf) {
        return rigs.containsKey(wolf.getUniqueId());
    }

    public @Nullable MonsterRig rig(Entity wolf) {
        return rigs.get(wolf.getUniqueId());
    }

    /** @return true se virou monstro, false se voltou a ser cachorro */
    public boolean toggle(Wolf wolf) {
        if (isMonster(wolf)) {
            disable(wolf, true);
            return false;
        }
        enable(wolf, true);
        return true;
    }

    public void enable(Wolf wolf, boolean effects) {
        if (isMonster(wolf)) return;
        MonsterRig rig = new MonsterRig(plugin);
        rigs.put(wolf.getUniqueId(), rig);
        wolf.getPersistentDataContainer().set(plugin.monsterKey(), PersistentDataType.BYTE, (byte) 1);
        wolf.setInvisible(true);
        wolf.setSilent(true);
        wolf.setCustomNameVisible(false);
        rig.spawn(wolf.getLocation(), nameOf(wolf));
        rig.setController(plugin.possession().hideHeadFor(wolf));
        plugin.possession().refreshForm(wolf);
        if (effects) {
            Location l = wolf.getLocation();
            l.getWorld().spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 1, 0), 60, 0.5, 1.0, 0.5, 0.02);
            l.getWorld().playSound(l, Sound.ENTITY_WOLF_GROWL, 1.2f, 0.5f);
            l.getWorld().playSound(l, Sound.ENTITY_WARDEN_EMERGE, 0.8f, 1.3f);
        }
    }

    public void disable(Wolf wolf, boolean effects) {
        MonsterRig rig = rigs.remove(wolf.getUniqueId());
        if (rig == null) return;
        rig.setController(null);
        rig.despawn();
        wolf.getPersistentDataContainer().remove(plugin.monsterKey());
        restoreLook(wolf);
        plugin.possession().refreshForm(wolf);
        if (effects) {
            Location l = wolf.getLocation();
            l.getWorld().spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 0.5, 0), 40, 0.4, 0.4, 0.4, 0.02);
            l.getWorld().playSound(l, Sound.ENTITY_WOLF_WHINE, 1f, 0.8f);
        }
    }

    private static void restoreLook(Wolf wolf) {
        wolf.setInvisible(false);
        wolf.setSilent(false);
        wolf.setCustomNameVisible(Dogs.plainName(wolf) != null);
    }

    /** Troca o nome mantendo a placa certa (monstro usa placa própria lá em cima). */
    public void setDogName(Wolf wolf, String name) {
        wolf.customName(Component.text(name));
        wolf.setCustomNameVisible(!isMonster(wolf));
        Dogs.refreshNameCache();
    }

    private Component nameOf(Wolf wolf) {
        String n = Dogs.plainName(wolf);
        return n == null ? Component.empty() : Component.text(n);
    }

    public void tick() {
        int growlChance = plugin.getConfig().getInt("monstro-rosnado-chance", 300);
        for (Map.Entry<UUID, MonsterRig> e : rigs.entrySet()) {
            MonsterRig rig = e.getValue();
            Entity ent = Bukkit.getEntity(e.getKey());
            if (!(ent instanceof Wolf wolf) || !wolf.isValid()) {
                // Lobo descarregado (longe): esconde o monstro até ele voltar.
                if (rig.isSpawned()) rig.despawn();
                continue;
            }
            if (!rig.isSpawned() || rig.isBroken()) {
                rig.spawn(wolf.getLocation(), nameOf(wolf));
                rig.setController(null);
                rig.setController(plugin.possession().hideHeadFor(wolf));
            }
            rig.update(wolf.getLocation(), nameOf(wolf));
            if (growlChance > 0 && ThreadLocalRandom.current().nextInt(growlChance) == 0) {
                wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_GROWL, 0.9f, 0.45f);
            }
        }
    }

    /** Desliga tudo visualmente, mas deixa a marca no lobo para voltar depois de reiniciar. */
    public void shutdown() {
        for (Map.Entry<UUID, MonsterRig> e : rigs.entrySet()) {
            e.getValue().despawn();
            if (Bukkit.getEntity(e.getKey()) instanceof Wolf wolf) restoreLook(wolf);
        }
        rigs.clear();
    }

    public void recover(Iterable<? extends Entity> entities) {
        for (Entity e : entities) {
            if (e instanceof Wolf w && w.getPersistentDataContainer().has(plugin.monsterKey()) && !isMonster(w)) {
                enable(w, false);
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        recover(e.getEntities());
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        MonsterRig rig = rigs.remove(e.getEntity().getUniqueId());
        if (rig != null) rig.despawn();
    }
}
