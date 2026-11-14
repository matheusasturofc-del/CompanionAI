package dev.companionai;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Ações reaproveitadas pelo painel e pelos comandos (sustos, mundo, teleporte). */
public final class Actions {

    /** Tipos de susto (nome usado no comando /horror susto). */
    public enum Scare {
        CAVERNA("Som de caverna"),
        PASSOS("Passos atrás dele"),
        ROSNADO("Rosnado atrás dele"),
        RAIO("Raio perto dele"),
        JUMPSCARE("Jumpscare"),
        PORTA("Porta batendo"),
        VIDRO("Vidro quebrando"),
        GRITO("Grito distante");

        public final String label;

        Scare(String label) {
            this.label = label;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Scare byKey(String key) {
            for (Scare s : values()) if (s.key().equalsIgnoreCase(key)) return s;
            return null;
        }
    }

    private final CompanionAI plugin;

    Actions(CompanionAI plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ sustos

    public void scare(Player t, Scare s) {
        switch (s) {
            case CAVERNA -> t.playSound(t.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 1f, 1f);
            case PASSOS -> footsteps(t);
            case ROSNADO -> t.playSound(behind(t, 2.5), Sound.ENTITY_WOLF_GROWL, SoundCategory.HOSTILE, 1f, 0.55f);
            case RAIO -> {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                Location l = t.getLocation().add(r.nextDouble(-8, 8), 0, r.nextDouble(-8, 8));
                l.setY(t.getWorld().getHighestBlockYAt(l) + 1);
                t.getWorld().strikeLightningEffect(l);
            }
            case JUMPSCARE -> jumpscare(t);
            case PORTA -> t.playSound(behind(t, 4), Sound.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 1f, 0.8f);
            case VIDRO -> t.playSound(behind(t, 5), Sound.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1f, 0.9f);
            case GRITO -> t.playSound(behind(t, 14), Sound.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 0.5f, 0.6f);
        }
    }

    public void jumpscare(Player t) {
        t.playSound(t.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 1f, 0.5f);
        t.playSound(t.getLocation(), Sound.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 1f, 1.2f);
        t.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0, false, false, false));
        t.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
        t.showTitle(Title.title(Component.text("▓▓▓▓▓▓", NamedTextColor.DARK_RED),
                Component.text("ele está aqui", NamedTextColor.RED),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(500))));
    }

    public void title(Player t, String msg) {
        t.showTitle(Title.title(Component.text(msg, NamedTextColor.DARK_RED), Component.empty(),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1))));
        t.playSound(t.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 0.6f);
    }

    /** Mensagem no chat que só ele vê, como se fosse o cachorro. */
    public void whisper(Player admin, Player t, String msg) {
        Wolf w = plugin.possession().wolfOf(admin);
        String name = w != null ? Dogs.displayName(w, "???") : plugin.getConfig().getString("nome-sem-nome", "???");
        t.sendMessage(Component.text("<" + name + "> ")
                .append(Component.text(msg, NamedTextColor.GRAY).decorate(TextDecoration.ITALIC)));
    }

    /** Vira a cabeça dele para o cachorro/monstro que o admin controla. */
    public boolean forceLook(Player admin, Player t) {
        Wolf wolf = plugin.possession().wolfOf(admin);
        if (wolf == null || !wolf.getWorld().equals(t.getWorld())) return false;
        double height = plugin.monsters().isMonster(wolf) ? 2.8 : 0.5;
        Location eye = t.getLocation();
        Vector dir = wolf.getLocation().add(0, height, 0).toVector().subtract(t.getEyeLocation().toVector());
        eye.setDirection(dir);
        t.teleport(eye);
        return true;
    }

    // ------------------------------------------------------------------ teleporte

    /** Traz o jogador para 3 blocos na frente do admin, olhando para ele. */
    public void bring(Player admin, Player t) {
        Vector fwd = admin.getLocation().getDirection().setY(0);
        if (fwd.lengthSquared() < 1e-4) fwd = new Vector(0, 0, 1);
        fwd.normalize();
        Location dest = admin.getLocation().add(fwd.clone().multiply(3));
        dest.setDirection(fwd.clone().multiply(-1));
        t.teleport(dest);
    }

    // ------------------------------------------------------------------ mundo

    public void weather(World w, String kind) {
        boolean rain = !kind.equals("sol");
        boolean thunder = kind.equals("tempestade");
        w.setStorm(rain);
        w.setThundering(thunder);
        w.setWeatherDuration(20 * 60 * 10);
        if (thunder) w.setThunderDuration(20 * 60 * 10);
    }

    /** @return ticks do dia para um nome, ou -1 se não reconhecer */
    public static long timeByName(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "manha", "dia" -> 1000;
            case "meiodia" -> 6000;
            case "pordosol", "tarde" -> 12500;
            case "noite" -> 14000;
            case "meianoite" -> 18000;
            default -> -1;
        };
    }

    public boolean toggleFreezeTime(World w) {
        Boolean cycle = w.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE);
        boolean running = cycle == null || cycle;
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, !running);
        return running; // true = acabou de congelar
    }

    // ------------------------------------------------------------------ helpers

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

    static Location behind(Player t, double dist) {
        Vector back = t.getLocation().getDirection().setY(0);
        if (back.lengthSquared() < 1e-4) back = new Vector(0, 0, 1);
        back.normalize().multiply(-dist);
        return t.getLocation().add(back);
    }
}
