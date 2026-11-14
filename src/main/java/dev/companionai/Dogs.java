package dev.companionai;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Wolf;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/** Utilidades para achar e nomear cachorros. */
public final class Dogs {

    private static volatile List<String> nameCache = List.of();

    private Dogs() {}

    public static @Nullable String plainName(Entity e) {
        Component c = e.customName();
        if (c == null) return null;
        String s = PlainTextComponentSerializer.plainText().serialize(c).trim();
        return s.isEmpty() ? null : s;
    }

    public static String displayName(Wolf w, String fallback) {
        String n = plainName(w);
        return n == null ? fallback : n;
    }

    public static List<Wolf> all() {
        List<Wolf> list = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) list.addAll(world.getEntitiesByClass(Wolf.class));
        return list;
    }

    /** Cachorros domesticados ou com nome, mais perto primeiro. */
    public static List<Wolf> interesting(@Nullable Location near) {
        List<Wolf> list = new ArrayList<>();
        for (Wolf w : all()) if (w.isTamed() || plainName(w) != null) list.add(w);
        if (near != null) list.sort(Comparator.comparingDouble(w -> distance(w.getLocation(), near)));
        return list;
    }

    public static @Nullable Wolf findByName(String name, @Nullable Location near) {
        String target = name.trim();
        Wolf best = null;
        double bestDist = Double.MAX_VALUE;
        for (Wolf w : all()) {
            String n = plainName(w);
            if (n == null || !n.equalsIgnoreCase(target)) continue;
            double d = near == null ? 0 : distance(w.getLocation(), near);
            if (best == null || d < bestDist) {
                best = w;
                bestDist = d;
            }
        }
        return best;
    }

    public static String ownerName(Wolf w) {
        AnimalTamer owner = w.getOwner();
        if (owner == null) return "ninguém (selvagem)";
        String n = owner.getName();
        return n == null ? "?" : n;
    }

    private static double distance(Location a, Location b) {
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return 1e12;
        return a.distanceSquared(b);
    }

    /** Roda no main thread a cada segundo; as sugestões de comando leem daqui com segurança. */
    public static void refreshNameCache() {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Wolf w : all()) {
            String n = plainName(w);
            if (n != null) names.add(n);
        }
        nameCache = List.copyOf(names);
    }

    public static List<String> cachedNames() {
        return nameCache;
    }
}
