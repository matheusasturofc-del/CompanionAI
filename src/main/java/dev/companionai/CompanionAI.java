package dev.companionai;

import dev.companionai.monster.MonsterManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CompanionAI extends JavaPlugin {

    public static final String PERMISSION = "companionai.admin";

    private VanishManager vanish;
    private PossessionManager possession;
    private MonsterManager monsters;
    private TextPrompt prompts;
    private AdminPanel panel;

    private NamespacedKey panelKey;
    private NamespacedKey possessedKey;
    private NamespacedKey monsterKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        panelKey = new NamespacedKey(this, "painel");
        possessedKey = new NamespacedKey(this, "possuido");
        monsterKey = new NamespacedKey(this, "monstro");

        vanish = new VanishManager(this);
        monsters = new MonsterManager(this);
        possession = new PossessionManager(this);
        prompts = new TextPrompt(this);
        panel = new AdminPanel(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(vanish, this);
        pm.registerEvents(monsters, this);
        pm.registerEvents(possession, this);
        pm.registerEvents(prompts, this);
        pm.registerEvents(panel, this);

        HorrorCommand.register(this);

        // Ordem importa: primeiro o cachorro segue o jogador, depois o monstro segue o cachorro.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            possession.tick();
            monsters.tick();
        }, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, Dogs::refreshNameCache, 1L, 20L);

        // Recupera cachorros que ficaram "presos" (ex.: servidor caiu no meio da brincadeira)
        Bukkit.getScheduler().runTask(this, () -> {
            for (var world : Bukkit.getWorlds()) {
                monsters.recover(world.getEntities());
                possession.recover(world.getEntities());
            }
        });
    }

    @Override
    public void onDisable() {
        if (possession != null) possession.stopAll();
        if (monsters != null) monsters.shutdown();
        if (vanish != null) vanish.shutdown();
    }

    public static boolean isAdmin(CommandSender sender) {
        return sender.hasPermission(PERMISSION);
    }

    /** Mensagem que so o admin ve. */
    public static void info(CommandSender to, String text) {
        to.sendMessage(Component.text("[Horror] ", NamedTextColor.DARK_PURPLE)
                .append(Component.text(text, NamedTextColor.LIGHT_PURPLE)));
    }

    public static void error(CommandSender to, String text) {
        to.sendMessage(Component.text("[Horror] ", NamedTextColor.DARK_PURPLE)
                .append(Component.text(text, NamedTextColor.RED)));
    }

    public static void actionBar(Player to, String text) {
        to.sendActionBar(Component.text(text, NamedTextColor.LIGHT_PURPLE));
    }

    public VanishManager vanish() { return vanish; }
    public PossessionManager possession() { return possession; }
    public MonsterManager monsters() { return monsters; }
    public TextPrompt prompts() { return prompts; }
    public AdminPanel panel() { return panel; }
    public NamespacedKey panelKey() { return panelKey; }
    public NamespacedKey possessedKey() { return possessedKey; }
    public NamespacedKey monsterKey() { return monsterKey; }
}
