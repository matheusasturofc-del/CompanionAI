package dev.companionai.monster;

import dev.companionai.CompanionAI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * O monstro de verdade: ~75 "block displays" montados como um esqueleto comprido,
 * com sangue, dentes e olhos que brilham. Anda junto com o lobo, mexe as pernas,
 * respira, pinga sangue e faz barulho. Funciona sem mod no cliente.
 */
public final class MonsterRig {

    private static final double NAME_HEIGHT = 3.45;
    private static final BlockData BLOOD = Material.RED_CONCRETE.createBlockData();
    private static final Particle.DustOptions BLOOD_DUST = new Particle.DustOptions(Color.fromRGB(120, 0, 0), 1.2f);

    private final CompanionAI plugin;
    private final EnumMap<MonsterPose.Part, BlockDisplay> parts = new EnumMap<>(MonsterPose.Part.class);
    private TextDisplay nameplate;
    private @Nullable UUID controller;

    private Location last;
    private MonsterPose.Result lastPose;
    private double phase;
    private double speed;
    private int ticks;
    private int twitchTicks;
    private double twitchYaw;
    private double twitchRoll;
    private int talkTicks;
    private int attackTicks;
    private double pitchDeg;
    private long lastStep;

    MonsterRig(CompanionAI plugin) {
        this.plugin = plugin;
    }

    static Material material(MonsterPose.Skin skin) {
        return switch (skin) {
            case SKIN -> Material.WHITE_CONCRETE_POWDER;
            case LIMB -> Material.CALCITE;
            case BONE -> Material.BONE_BLOCK;
            case TOOTH -> Material.QUARTZ_BLOCK;
            case DARK -> Material.BLACK_CONCRETE;
            case BLOOD -> Material.RED_CONCRETE;
            case DRIED_BLOOD -> Material.RED_TERRACOTTA;
            case GUMS -> Material.NETHER_WART_BLOCK;
            case EYE -> Material.RED_CONCRETE;
        };
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
        MonsterPose.Result pose = currentPose();
        lastPose = pose;
        for (MonsterPose.Part part : MonsterPose.Part.values()) {
            Matrix4f m = pose.parts().get(part);
            BlockDisplay d = world.spawn(root, BlockDisplay.class, e -> {
                e.setBlock(material(part.skin).createBlockData());
                e.setPersistent(false);
                e.setTransformationMatrix(m);
                e.setTeleportDuration(2);
                e.setInterpolationDuration(2);
                e.setViewRange(1.5f);
                // Olhos vermelhos brilham no escuro.
                if (part.skin == MonsterPose.Skin.EYE) e.setBrightness(new Display.Brightness(15, 15));
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
        if (Objects.equals(id, controller)) return;
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

    /** Bote: cabeça avança, boca abre toda e espirra sangue. */
    public void attack() {
        attackTicks = 8;
        if (bloodEnabled() && last != null && lastPose != null) {
            Location mouth = toWorld(lastPose.mouth());
            mouth.getWorld().spawnParticle(Particle.BLOCK, mouth, 25, 0.15, 0.15, 0.15, 0.1, BLOOD);
            mouth.getWorld().spawnParticle(Particle.DUST, mouth, 15, 0.2, 0.2, 0.2, 0, BLOOD_DUST);
        }
    }

    /** Mexe a boca por alguns ticks (quando o cachorro "fala" na forma de monstro). */
    public void talk(int ticks) {
        talkTicks = Math.max(talkTicks, ticks);
    }

    /** Sangue espirrando em alguém que foi mordido. */
    public static void splatter(Location at) {
        at.getWorld().spawnParticle(Particle.BLOCK, at, 30, 0.25, 0.4, 0.25, 0.1, BLOOD);
        at.getWorld().spawnParticle(Particle.DUST, at, 20, 0.3, 0.4, 0.3, 0, BLOOD_DUST);
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
            MonsterPose.Result pose = currentPose();
            lastPose = pose;
            for (Map.Entry<MonsterPose.Part, BlockDisplay> e : parts.entrySet()) {
                BlockDisplay d = e.getValue();
                d.setInterpolationDelay(0);
                d.setTransformationMatrix(pose.parts().get(e.getKey()));
            }
        }

        effects(root, amp, r);
    }

    /** Sangue pingando, rastro de sangue, passos e respiração. */
    private void effects(Location root, double amp, ThreadLocalRandom r) {
        World w = root.getWorld();
        if (bloodEnabled() && lastPose != null) {
            if (ticks % 7 == 0) {
                w.spawnParticle(Particle.FALLING_DUST, toWorld(lastPose.mouth()), 1, 0.03, 0, 0.03, 0, BLOOD);
            }
            if (r.nextInt(10) == 0) {
                Vector3f tip = lastPose.dripTips()[r.nextInt(lastPose.dripTips().length)];
                w.spawnParticle(Particle.FALLING_DUST, toWorld(tip), 1, 0, 0, 0, 0, BLOOD);
            }
            if (amp > 0.3 && ticks % 8 == 0) {
                // Rastro de sangue no chão
                Location drop = toWorld(new Vector3f((float) r.nextDouble(-0.2, 0.2), 0.08f, (float) r.nextDouble(-1.8, -0.3)));
                w.spawnParticle(Particle.BLOCK, drop, 3, 0.05, 0, 0.05, 0, BLOOD);
            }
        }
        // Passos de osso quando a pata toca o chão
        long step = (long) Math.floor(phase / Math.PI);
        if (step != lastStep) {
            lastStep = step;
            if (amp > 0.2) w.playSound(root, Sound.ENTITY_SKELETON_STEP, 0.7f, 0.5f);
        }
        // Respiração ofegante e grave quando parado
        if (amp < 0.15 && r.nextInt(110) == 0) {
            w.playSound(root, Sound.ENTITY_WOLF_PANT, 0.6f, 0.4f);
        }
    }

    private boolean bloodEnabled() {
        return plugin.getConfig().getBoolean("monstro-sangue", true);
    }

    private Location toWorld(Vector3f local) {
        Vector3f v = new Vector3f(local);
        new Matrix4f().rotateY((float) Math.toRadians(-last.getYaw())).transformPosition(v);
        return last.clone().add(v.x, v.y, v.z);
    }

    private MonsterPose.Result currentPose() {
        double amp = Math.min(1.2, speed / 0.2);
        double pitchRad = Math.toRadians(Math.max(-50, Math.min(55, pitchDeg)));
        double headPitch = 0.45 + pitchRad * 0.75;
        double jaw = talkTicks > 0 ? 0.15 + 0.4 * Math.abs(Math.sin(ticks * 0.9)) : (twitchTicks > 0 ? 0.45 : 0.18);
        if (attackTicks > 0) {
            double k = Math.sin(Math.PI * (8 - attackTicks) / 8.0);
            headPitch += 0.5 * k;
            jaw = 0.2 + 0.6 * k;
        }
        return MonsterPose.compute(phase, amp, headPitch, twitchYaw, twitchRoll, jaw, ticks);
    }

    private static Location root(Location base) {
        Location l = base.clone();
        l.setPitch(0);
        return l;
    }
}
