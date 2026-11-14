package dev.companionai.monster;

import dev.companionai.CompanionAI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Perseguidor": o monstro aparece parado ao longe, encarando o jogador.
 * Quando ele olha diretamente ou chega perto, o monstro some com um barulho.
 */
public final class StalkerManager {

    private static final class Stalk {
        final UUID wolf;
        final UUID target;
        int ticks;
        int seenTicks;

        Stalk(UUID wolf, UUID target) {
            this.wolf = wolf;
            this.target = target;
        }
    }

    private static final int MAX_TICKS = 20 * 90;

    private final CompanionAI plugin;
    private final Map<UUID, Stalk> stalks = new ConcurrentHashMap<>();

    public StalkerManager(CompanionAI plugin) {
        this.plugin = plugin;
    }

    /** @return false se não achou lugar para aparecer */
    public boolean spawn(Player target) {
        Location spot = findSpot(target);
        if (spot == null) return false;
        Wolf wolf = spot.getWorld().spawn(spot, Wolf.class, w -> {
            w.setAI(false);
            w.setSilent(true);
            w.setInvulnerable(true);
            w.setCollidable(false);
            w.setPersistent(false);
        });
        plugin.monsters().enable(wolf, false);
        stalks.put(wolf.getUniqueId(), new Stalk(wolf.getUniqueId(), target.getUniqueId()));
        return true;
    }

    public boolean isStalker(UUID wolf) {
        return stalks.containsKey(wolf);
    }

    public void tick() {
        Iterator<Stalk> it = stalks.values().iterator();
        while (it.hasNext()) {
            Stalk s = it.next();
            Wolf wolf = Bukkit.getEntity(s.wolf) instanceof Wolf w ? w : null;
            Player t = Bukkit.getPlayer(s.target);
            if (wolf == null || t == null || !t.getWorld().equals(wolf.getWorld()) || ++s.ticks > MAX_TICKS) {
                it.remove();
                vanish(wolf, false);
                continue;
            }
            // Sempre encarando o jogador
            Location wl = wolf.getLocation();
            Vector toPlayer = t.getLocation().toVector().subtract(wl.toVector());
            wl.setDirection(toPlayer);
            wl.setPitch(-10);
            wolf.teleport(wl);

            double dist = toPlayer.length();
            Vector look = t.getEyeLocation().getDirection();
            Vector toMonster = wl.clone().add(0, 2.2, 0).toVector().subtract(t.getEyeLocation().toVector()).normalize();
            boolean looking = look.dot(toMonster) > 0.96;
            if (looking) s.seenTicks++;
            if (dist < 9 || s.seenTicks > 12) {
                it.remove();
                vanish(wolf, true);
            }
        }
    }

    public void removeAll() {
        for (Stalk s : stalks.values()) {
            vanish(Bukkit.getEntity(s.wolf) instanceof Wolf w ? w : null, false);
        }
        stalks.clear();
    }

    private void vanish(Wolf wolf, boolean effects) {
        if (wolf == null) return;
        if (effects) {
            Location l = wolf.getLocation();
            World w = l.getWorld();
            w.spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 1.5, 0), 60, 0.4, 1.0, 0.8, 0.02);
            w.playSound(l, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1f, 0.5f);
            w.playSound(l, Sound.ENTITY_WOLF_GROWL, SoundCategory.HOSTILE, 1f, 0.4f);
        }
        plugin.monsters().disable(wolf, false);
        wolf.remove();
    }

    /** Lugar entre 18 e 30 blocos, no chão, de preferência atrás ou do lado dele. */
    private Location findSpot(Player t) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        World w = t.getWorld();
        for (int tries = 0; tries < 20; tries++) {
            double angle = Math.toRadians(t.getLocation().getYaw() + 90 + r.nextDouble(-120, 120));
            double dist = r.nextDouble(18, 30);
            double x = t.getLocation().getX() - Math.sin(angle) * dist;
            double z = t.getLocation().getZ() + Math.cos(angle) * dist;
            int y = w.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z));
            if (Math.abs(y - t.getLocation().getY()) > 12) continue;
            Location l = new Location(w, x, y + 1, z);
            if (!l.getBlock().isPassable() || !l.clone().add(0, 1, 0).getBlock().isPassable()) continue;
            return l;
        }
        return null;
    }
}
