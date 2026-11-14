package dev.companionai.monster;

import dev.companionai.CompanionAI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * O monstro de verdade: ~24 "block displays" brancos montados como um esqueleto
 * comprido, que anda junto com o lobo e mexe as pernas. Funciona sem mod no cliente.
 */
public final class MonsterRig {

    private static final double NAME_HEIGHT = 3.35;

    private final CompanionAI plugin;
    private final EnumMap<MonsterPose.Part, BlockDisplay> parts = new EnumMap<>(MonsterPose.Part.class);
    private TextDisplay nameplate;
    private @Nullable UUID controller;

    private Location last;
    private double phase;
    private double speed;
    private int ticks;
    private int twitchTicks;
    private double twitchYaw;
    private double twitchRoll;
    private int talkTicks;
    private int attackTicks;
    private double pitchDeg;

    MonsterRig(CompanionAI plugin) {
        this.plugin = plugin;
    }

    boolean isSpawned() {
        return !parts.isEmpty();
    }

    boolean isBroken() {
        if (nameplate == null || !nameplate.isValid()) return true;
        for (BlockDisplay d : parts.values()) if (!d.isValid()) return true;
        return false;
    }

    void spawn(Location base, Component name) {
        despawn();
        Location root = root(base);
        World world = root.getWorld();
        pitchDeg = base.getPitch();
        Map<MonsterPose.Part, Matrix4f> pose = currentPose();
        for (MonsterPose.Part part : MonsterPose.Part.values()) {
            Matrix4f m = pose.get(part);
            BlockDisplay d = world.spawn(root, BlockDisplay.class, e -> {
                e.setBlock((part.blood ? Material.RED_CONCRETE : Material.WHITE_CONCRETE).createBlockData());
                e.setPersistent(false);
                e.setTransformationMatrix(m);
                e.setTeleportDuration(2);
                e.setInterpolationDuration(2);
                e.setViewRange(1.5f);
            });
            parts.put(part, d);
        }
        nameplate = world.spawn(root.clone().add(0, NAME_HEIGHT, 0), TextDisplay.class, t -> {
            t.text(name);
            t.setBillboard(Display.Billboard.CENTER);
            t.setPersistent(false);
            t.setTeleportDuration(2);
            t.setSeeThrough(true);
        });
        last = root;
        applyControllerVisibility(true);
    }

    void despawn() {
        for (BlockDisplay d : parts.values()) d.remove();
        parts.clear();
        if (nameplate != null) {
            nameplate.remove();
            nameplate = null;
        }
    }

    /** Quem controla não vê a cabeça/pescoço (estariam na frente da câmera). */
    public void setController(@Nullable Player p) {
        UUID id = p == null ? null : p.getUniqueId();
        if (java.util.Objects.equals(id, controller)) return;
        applyControllerVisibility(false);
        controller = id;
        applyControllerVisibility(true);
    }

    private void applyControllerVisibility(boolean hide) {
        if (controller == null) return;
        Player p = Bukkit.getPlayer(controller);
        if (p == null) return;
        for (Map.Entry<MonsterPose.Part, BlockDisplay> e : parts.entrySet()) {
            if (!e.getKey().head) continue;
            if (hide) p.hideEntity(plugin, e.getValue());
            else p.showEntity(plugin, e.getValue());
        }
        if (nameplate != null) {
            if (hide) p.hideEntity(plugin, nameplate);
            else p.showEntity(plugin, nameplate);
        }
    }

    /** Bote: cabeça avança e a boca abre toda (ataque ou rugido). */
    public void attack() {
        attackTicks = 8;
    }

    /** Mexe a boca por alguns ticks (quando o cachorro "fala" na forma de monstro). */
    public void talk(int ticks) {
        talkTicks = Math.max(talkTicks, ticks);
    }

    void update(Location base, Component name) {
        Location root = root(base);
        if (last == null || !last.getWorld().equals(root.getWorld())) {
            spawn(base, name);
            return;
        }
        double dx = root.getX() - last.getX();
        double dz = root.getZ() - last.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 4) dist = 0; // teleporte, não passo
        speed = speed * 0.6 + dist * 0.4;
        phase += dist * 2.4;
        pitchDeg = base.getPitch();
        ticks++;

        ThreadLocalRandom r = ThreadLocalRandom.current();
        double amp = Math.min(1.2, speed / 0.2);
        if (twitchTicks > 0) {
            twitchTicks--;
        } else {
            twitchYaw *= 0.6;
            twitchRoll *= 0.6;
            if (amp < 0.15 && r.nextInt(50) == 0) {
                twitchTicks = 4 + r.nextInt(8);
                twitchYaw = r.nextDouble(-0.7, 0.7);
                twitchRoll = r.nextDouble(-0.6, 0.6);
            }
        }
        if (talkTicks > 0) talkTicks--;
        if (attackTicks > 0) attackTicks--;

        boolean moved = root.distanceSquared(last) > 1.0E-5 || Math.abs(root.getYaw() - last.getYaw()) > 0.1f;
        if (moved) {
            for (BlockDisplay d : parts.values()) d.teleport(root);
        }
        nameplate.teleport(root.clone().add(0, NAME_HEIGHT, 0));
        if (ticks % 20 == 0) nameplate.text(name);
        last = root;

        if (ticks % 2 == 0) {
            Map<MonsterPose.Part, Matrix4f> pose = currentPose();
            for (Map.Entry<MonsterPose.Part, BlockDisplay> e : parts.entrySet()) {
                BlockDisplay d = e.getValue();
                d.setInterpolationDelay(0);
                d.setTransformationMatrix(pose.get(e.getKey()));
            }
        }
    }

    private Map<MonsterPose.Part, Matrix4f> currentPose() {
        double amp = Math.min(1.2, speed / 0.2);
        double pitchRad = Math.toRadians(Math.max(-50, Math.min(55, pitchDeg)));
        double headPitch = 0.45 + pitchRad * 0.75;
        double jaw = talkTicks > 0 ? 0.15 + 0.4 * Math.abs(Math.sin(ticks * 0.9)) : (twitchTicks > 0 ? 0.45 : 0.15);
        if (attackTicks > 0) {
            double k = Math.sin(Math.PI * (8 - attackTicks) / 8.0);
            headPitch += 0.5 * k;
            jaw = 0.2 + 0.85 * k;
        }
        double tail = Math.sin(ticks * 0.15) * 0.25;
        return MonsterPose.compute(phase, amp, headPitch, twitchYaw, twitchRoll, jaw, tail);
    }

    private static Location root(Location base) {
        Location l = base.clone();
        l.setPitch(0);
        return l;
    }
}
