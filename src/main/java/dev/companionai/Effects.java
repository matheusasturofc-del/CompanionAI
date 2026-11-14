package dev.companionai;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Efeitos que o admin pode jogar em outro jogador (painel e /horror efeito). */
public enum Effects {
    CEGUEIRA(PotionEffectType.BLINDNESS, Material.INK_SAC, "Cegueira", "Tela preta, quase não enxerga"),
    ESCURIDAO(PotionEffectType.DARKNESS, Material.SCULK, "Escuridão", "A tela escurece e pulsa"),
    NAUSEA(PotionEffectType.NAUSEA, Material.PUFFERFISH, "Náusea", "A tela fica girando"),
    LENTIDAO(PotionEffectType.SLOWNESS, Material.SOUL_SAND, "Lentidão", "Anda devagar"),
    CANSACO(PotionEffectType.MINING_FATIGUE, Material.WOODEN_PICKAXE, "Cansaço", "Quebra blocos bem devagar"),
    FRAQUEZA(PotionEffectType.WEAKNESS, Material.FERMENTED_SPIDER_EYE, "Fraqueza", "Bate mais fraco"),
    FOME(PotionEffectType.HUNGER, Material.ROTTEN_FLESH, "Fome", "A fome cai rápido"),
    VENENO(PotionEffectType.POISON, Material.SPIDER_EYE, "Veneno", "Perde vida (não mata)"),
    WITHER(PotionEffectType.WITHER, Material.WITHER_ROSE, "Wither", "Perde vida (pode matar)"),
    LEVITACAO(PotionEffectType.LEVITATION, Material.SHULKER_SHELL, "Levitação", "Flutua para cima"),
    BRILHO(PotionEffectType.GLOWING, Material.GLOWSTONE_DUST, "Brilho", "Fica com contorno brilhante"),
    PULO(PotionEffectType.JUMP_BOOST, Material.RABBIT_FOOT, "Super pulo", "Pula muito alto"),
    VELOCIDADE(PotionEffectType.SPEED, Material.SUGAR, "Velocidade", "Corre mais rápido"),
    VISAO(PotionEffectType.NIGHT_VISION, Material.GOLDEN_CARROT, "Visão noturna", "Enxerga no escuro");

    public final PotionEffectType type;
    public final Material icon;
    public final String label;
    public final String description;

    Effects(PotionEffectType type, Material icon, String label, String description) {
        this.type = type;
        this.icon = icon;
        this.label = label;
        this.description = description;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable Effects byKey(String key) {
        for (Effects e : values()) if (e.key().equalsIgnoreCase(key)) return e;
        return null;
    }

    /**
     * @param seconds duração; 0 ou menos = infinito
     * @param level   nível começando em 1
     */
    public void apply(Player target, int seconds, int level, boolean showIcon) {
        int ticks = seconds <= 0 ? PotionEffect.INFINITE_DURATION : seconds * 20;
        target.addPotionEffect(new PotionEffect(type, ticks, Math.max(0, level - 1), false, false, showIcon));
    }

    public static void clearAll(Player target) {
        for (PotionEffect pe : target.getActivePotionEffects()) target.removePotionEffect(pe.getType());
    }
}
