package dev.companionai;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /horror ... — só existe para quem tem permissão. Para o amigo, o comando
 * nem aparece no TAB e o servidor responde "comando desconhecido".
 */
final class HorrorCommand {

    private static final Pattern RENAME = Pattern.compile("^(.+?)\\s+to\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    private HorrorCommand() {}

    static void register(CompanionAI plugin) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(build(plugin), "CompanionAI", List.of()));
    }

    private static LiteralCommandNode<CommandSourceStack> build(CompanionAI plugin) {
        SuggestionProvider<CommandSourceStack> dogNames = (ctx, b) -> {
            String typed = b.getRemainingLowerCase();
            for (String n : Dogs.cachedNames()) if (n.toLowerCase(Locale.ROOT).startsWith(typed)) b.suggest(n);
            return b.buildFuture();
        };
        SuggestionProvider<CommandSourceStack> renameHint = (ctx, b) -> {
            String typed = b.getRemaining();
            if (!typed.toLowerCase(Locale.ROOT).contains(" to ")) {
                for (String n : Dogs.cachedNames()) {
                    if (n.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) b.suggest(n + " to ");
                }
            }
            return b.buildFuture();
        };

        return Commands.literal("horror")
                .requires(src -> CompanionAI.isAdmin(src.getSender()))
                .executes(ctx -> help(ctx.getSource().getSender()))
                .then(Commands.literal("turn")
                        .then(Commands.literal("cachorro")
                                .executes(ctx -> player(ctx, p -> plugin.possession().turnNew(p)))
                                .then(Commands.argument("nome", StringArgumentType.greedyString())
                                        .suggests(dogNames)
                                        .executes(ctx -> player(ctx, p -> {
                                            String name = StringArgumentType.getString(ctx, "nome");
                                            Wolf w = Dogs.findByName(name, p.getLocation());
                                            if (w == null) {
                                                CompanionAI.error(p, "Nenhum cachorro chamado \"" + name + "\" (precisa estar carregado).");
                                                return;
                                            }
                                            plugin.possession().turnInto(p, w);
                                        })))))
                .then(Commands.literal("sair")
                        .executes(ctx -> player(ctx, p -> {
                            if (!plugin.possession().stop(p, true)) CompanionAI.error(p, "Você não está em um cachorro.");
                        })))
                .then(Commands.literal("rename")
                        .then(Commands.argument("texto", StringArgumentType.greedyString())
                                .suggests(renameHint)
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    Matcher m = RENAME.matcher(StringArgumentType.getString(ctx, "texto").trim());
                                    if (!m.matches()) {
                                        CompanionAI.error(s, "Use: /horror rename <nome atual> to <nome novo>");
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String from = m.group(1).trim();
                                    String to = m.group(2).trim();
                                    Wolf w = Dogs.findByName(from, s instanceof Player p ? p.getLocation() : null);
                                    if (w == null) {
                                        CompanionAI.error(s, "Nenhum cachorro chamado \"" + from + "\".");
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    plugin.monsters().setDogName(w, to);
                                    CompanionAI.info(s, "\"" + from + "\" agora se chama \"" + to + "\".");
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("monsterform")
                        .executes(ctx -> player(ctx, p -> {
                            Wolf w = plugin.possession().wolfOf(p);
                            if (w == null) {
                                CompanionAI.error(p, "Entre num cachorro ou use /horror monsterform <nome do cachorro>.");
                                return;
                            }
                            toggleMonster(plugin, p, w);
                        }))
                        .then(Commands.argument("nome", StringArgumentType.greedyString())
                                .suggests(dogNames)
                                .executes(ctx -> player(ctx, p -> {
                                    String name = StringArgumentType.getString(ctx, "nome");
                                    Wolf w = Dogs.findByName(name, p.getLocation());
                                    if (w == null) {
                                        CompanionAI.error(p, "Nenhum cachorro chamado \"" + name + "\".");
                                        return;
                                    }
                                    toggleMonster(plugin, p, w);
                                }))))
                .then(Commands.literal("sentar")
                        .executes(ctx -> player(ctx, p -> {
                            if (!plugin.possession().toggleSit(p, null)) CompanionAI.error(p, "Você não está em um cachorro.");
                        })))
                .then(Commands.literal("vanish")
                        .executes(ctx -> player(ctx, p -> {
                            if (plugin.possession().isPossessing(p) && plugin.vanish().isHidden(p)) {
                                CompanionAI.error(p, "Saia do cachorro primeiro (/horror sair).");
                                return;
                            }
                            boolean hidden = plugin.vanish().toggle(p);
                            CompanionAI.info(p, hidden ? "Você \"saiu\" do servidor (invisível)." : "Você \"entrou\" no servidor.");
                        })))
                .then(Commands.literal("painel")
                        .executes(ctx -> player(ctx, p -> plugin.panel().givePanelItem(p))))
                .build();
    }

    private static void toggleMonster(CompanionAI plugin, Player p, Wolf w) {
        boolean on = plugin.monsters().toggle(w);
        CompanionAI.info(p, Dogs.displayName(w, "Cachorro") + (on ? " virou MONSTRO." : " voltou a ser cachorro."));
    }

    private static int player(CommandContext<CommandSourceStack> ctx, java.util.function.Consumer<Player> action) {
        CommandSender s = ctx.getSource().getSender();
        if (!(s instanceof Player p)) {
            CompanionAI.error(s, "Só jogadores podem usar isso.");
            return 0;
        }
        action.accept(p);
        return Command.SINGLE_SUCCESS;
    }

    private static int help(CommandSender s) {
        String[] lines = {
                "/horror turn cachorro  - vira um cachorro novo",
                "/horror turn cachorro <nome>  - entra num cachorro que já existe",
                "/horror sair  - volta ao normal (continua invisível)",
                "/horror rename <atual> to <novo>  - renomeia um cachorro",
                "/horror monsterform [nome]  - liga/desliga a forma de monstro",
                "/horror sentar  - senta/levanta (dentro do cachorro)",
                "/horror vanish  - some/aparece com mensagem falsa de saiu/entrou",
                "/horror painel  - pega o item do painel (clique esquerdo)",
        };
        CompanionAI.info(s, "Comandos:");
        for (String l : lines) CompanionAI.info(s, " " + l);
        return Command.SINGLE_SUCCESS;
    }
}
