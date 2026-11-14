package dev.companionai;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /horror ... — só existe para quem tem permissão. Para o amigo, o comando
 * nem aparece no TAB e o servidor responde "comando desconhecido".
 */
final class HorrorCommand {

    private static final Pattern RENAME = Pattern.compile("^(.+?)\\s+to\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    /** Ajuda organizada por categoria: {comando, descrição}. */
    private static final String[][] HELP_FORMA = {
            {"/horror turn cachorro", "vira um cachorro novo (selvagem)"},
            {"/horror turn cachorro <nome>", "entra num cachorro que já existe"},
            {"/horror sair", "volta ao normal (continua invisível)"},
            {"/horror sentar", "senta/levanta (ou aperte Shift)"},
            {"/horror ver", "mostra/esconde o cachorro na SUA tela (F5)"},
            {"/horror rename <atual> to <novo>", "renomeia um cachorro"},
    };
    private static final String[][] HELP_MONSTRO = {
            {"/horror monsterform [nome]", "liga/desliga a forma de monstro"},
            {"/horror rugir", "rugido + escuridão em quem está perto"},
            {"/horror perseguidor <jogador>", "monstro aparece ao longe e some quando ele olha"},
    };
    private static final String[][] HELP_JOGADOR = {
            {"/horror efeito <jogador> <efeito> [seg] [nível]", "dá um efeito (0 seg = infinito)"},
            {"/horror limpar <jogador>", "tira todos os efeitos"},
            {"/horror susto <jogador> <tipo>", "caverna, passos, rosnado, raio, jumpscare, porta, vidro, grito"},
            {"/horror titulo <jogador> <texto>", "texto grande na tela dele"},
            {"/horror sussurro <jogador> <texto>", "o cachorro fala só com ele no chat"},
            {"/horror olhar <jogador>", "vira a cabeça dele para o seu cachorro"},
            {"/horror tp <jogador>", "você vai até ele"},
            {"/horror trazer <jogador>", "traz ele para a sua frente"},
    };
    private static final String[][] HELP_MUNDO = {
            {"/horror clima <sol|chuva|tempestade>", "muda o clima"},
            {"/horror hora <manha|meiodia|pordosol|noite|meianoite>", "muda a hora"},
            {"/horror congelar", "congela/descongela a hora"},
    };
    private static final String[][] HELP_OUTROS = {
            {"/horror vanish", "some/aparece com \"saiu/entrou no jogo\" falso"},
            {"/horror painel", "coloca o botão do painel no último slot da hotbar"},
            {"/horror ajuda", "mostra esta lista"},
    };

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
        SuggestionProvider<CommandSourceStack> effectNames = (ctx, b) -> {
            for (Effects e : Effects.values()) if (e.key().startsWith(b.getRemainingLowerCase())) b.suggest(e.key());
            return b.buildFuture();
        };
        SuggestionProvider<CommandSourceStack> scareNames = (ctx, b) -> {
            for (Actions.Scare s : Actions.Scare.values()) if (s.key().startsWith(b.getRemainingLowerCase())) b.suggest(s.key());
            return b.buildFuture();
        };
        SuggestionProvider<CommandSourceStack> weatherNames = (ctx, b) -> {
            for (String s : List.of("sol", "chuva", "tempestade")) if (s.startsWith(b.getRemainingLowerCase())) b.suggest(s);
            return b.buildFuture();
        };
        SuggestionProvider<CommandSourceStack> timeNames = (ctx, b) -> {
            for (String s : List.of("manha", "meiodia", "pordosol", "noite", "meianoite")) {
                if (s.startsWith(b.getRemainingLowerCase())) b.suggest(s);
            }
            return b.buildFuture();
        };

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("horror")
                .requires(src -> CompanionAI.isAdmin(src.getSender()))
                .executes(ctx -> help(ctx.getSource().getSender()));

        // ---------------- Forma
        root.then(Commands.literal("turn")
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
                                })))));
        root.then(Commands.literal("sair")
                .executes(ctx -> player(ctx, p -> {
                    if (!plugin.possession().stop(p, true)) CompanionAI.error(p, "Você não está em um cachorro.");
                })));
        root.then(Commands.literal("sentar")
                .executes(ctx -> player(ctx, p -> {
                    if (!plugin.possession().toggleSit(p, null)) CompanionAI.error(p, "Você não está em um cachorro.");
                })));
        root.then(Commands.literal("ver")
                .executes(ctx -> player(ctx, p -> {
                    if (!plugin.possession().toggleSelfView(p)) CompanionAI.error(p, "Você não está em um cachorro.");
                })));
        root.then(Commands.literal("rename")
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
                        })));

        // ---------------- Monstro
        root.then(Commands.literal("monsterform")
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
                        }))));
        root.then(Commands.literal("rugir")
                .executes(ctx -> player(ctx, p -> {
                    if (!plugin.possession().roar(p)) CompanionAI.error(p, "Só funciona na forma de monstro.");
                })));
        root.then(Commands.literal("perseguidor")
                .then(Commands.argument("jogador", ArgumentTypes.player())
                        .executes(ctx -> {
                            Player t = target(ctx);
                            CommandSender s = ctx.getSource().getSender();
                            if (plugin.stalkers().spawn(t)) CompanionAI.info(s, "Perseguidor apareceu perto de " + t.getName() + ".");
                            else CompanionAI.error(s, "Não achei lugar para o monstro aparecer.");
                            return Command.SINGLE_SUCCESS;
                        })));

        // ---------------- Jogadores
        root.then(Commands.literal("efeito")
                .then(Commands.argument("jogador", ArgumentTypes.player())
                        .then(Commands.argument("efeito", StringArgumentType.word())
                                .suggests(effectNames)
                                .executes(ctx -> effect(plugin, ctx, 30, 1))
                                .then(Commands.argument("segundos", IntegerArgumentType.integer(0, 3600))
                                        .executes(ctx -> effect(plugin, ctx, IntegerArgumentType.getInteger(ctx, "segundos"), 1))
                                        .then(Commands.argument("nivel", IntegerArgumentType.integer(1, 10))
                                                .executes(ctx -> effect(plugin, ctx,
                                                        IntegerArgumentType.getInteger(ctx, "segundos"),
                                                        IntegerArgumentType.getInteger(ctx, "nivel"))))))));
        root.then(Commands.literal("limpar")
                .then(Commands.argument("jogador", ArgumentTypes.player())
                        .executes(ctx -> {
                            Player t = target(ctx);
                            Effects.clearAll(t);
                            CompanionAI.info(ctx.getSource().getSender(), "Efeitos de " + t.getName() + " removidos.");
                            return Command.SINGLE_SUCCESS;
                        })));
        root.then(Commands.literal("susto")
                .then(Commands.argument("jogador", ArgumentTypes.player())
                        .then(Commands.argument("tipo", StringArgumentType.word())
                                .suggests(scareNames)
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    Player t = target(ctx);
                                    Actions.Scare sc = Actions.Scare.byKey(StringArgumentType.getString(ctx, "tipo"));
                                    if (sc == null) {
                                        CompanionAI.error(s, "Tipos: caverna, passos, rosnado, raio, jumpscare, porta, vidro, grito");
                                        return 0;
                                    }
                                    plugin.actions().scare(t, sc);
                                    CompanionAI.info(s, sc.label + " em " + t.getName() + ".");
                                    return Command.SINGLE_SUCCESS;
                                }))));
        root.then(Commands.literal("titulo")
                .then(Commands.argument("jogador", ArgumentTypes.player())
                        .then(Commands.argument("texto", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    Player t = target(ctx);
                                    plugin.actions().title(t, StringArgumentType.getString(ctx, "texto"));
                                    CompanionAI.info(ctx.getSource().getSender(), "Mensagem mostrada na tela de " + t.getName() + ".");
                                    return Command.SINGLE_SUCCESS;
                                }))));
        root.then(Commands.literal("sussurro")
                .then(Commands.argument("jogador", ArgumentTypes.player())
                        .then(Commands.argument("texto", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    Player t = target(ctx);
                                    return player(ctx, p -> {
                                        plugin.actions().whisper(p, t, StringArgumentType.getString(ctx, "texto"));
                                        CompanionAI.info(p, "Sussurro enviado só para " + t.getName() + ".");
                                    });
                                }))));
        root.then(Commands.literal("olhar")
                .then(Commands.argument("jogador", ArgumentTypes.player())
                        .executes(ctx -> {
                            Player t = target(ctx);
                            return player(ctx, p -> {
                                if (!plugin.actions().forceLook(p, t)) CompanionAI.error(p, "Entre num cachorro perto dele primeiro.");
                            });
                        })));
        root.then(Commands.literal("tp")
                .then(Commands.argument("jogador", ArgumentTypes.player())
                        .executes(ctx -> {
                            Player t = target(ctx);
                            return player(ctx, p -> p.teleport(t.getLocation()));
                        })));
        root.then(Commands.literal("trazer")
                .then(Commands.argument("jogador", ArgumentTypes.player())
                        .executes(ctx -> {
                            Player t = target(ctx);
                            return player(ctx, p -> plugin.actions().bring(p, t));
                        })));

        // ---------------- Mundo
        root.then(Commands.literal("clima")
                .then(Commands.argument("tipo", StringArgumentType.word())
                        .suggests(weatherNames)
                        .executes(ctx -> player(ctx, p -> {
                            String kind = StringArgumentType.getString(ctx, "tipo").toLowerCase(Locale.ROOT);
                            if (!List.of("sol", "chuva", "tempestade").contains(kind)) {
                                CompanionAI.error(p, "Use: sol, chuva ou tempestade");
                                return;
                            }
                            plugin.actions().weather(p.getWorld(), kind);
                            CompanionAI.info(p, "Clima: " + kind);
                        }))));
        root.then(Commands.literal("hora")
                .then(Commands.argument("quando", StringArgumentType.word())
                        .suggests(timeNames)
                        .executes(ctx -> player(ctx, p -> {
                            String when = StringArgumentType.getString(ctx, "quando");
                            long t = Actions.timeByName(when);
                            if (t < 0) {
                                try {
                                    t = Long.parseLong(when);
                                } catch (NumberFormatException ex) {
                                    CompanionAI.error(p, "Use: manha, meiodia, pordosol, noite, meianoite ou um número");
                                    return;
                                }
                            }
                            p.getWorld().setTime(t);
                            CompanionAI.info(p, "Hora alterada.");
                        }))));
        root.then(Commands.literal("congelar")
                .executes(ctx -> player(ctx, p -> {
                    boolean frozen = plugin.actions().toggleFreezeTime(p.getWorld());
                    CompanionAI.info(p, frozen ? "Hora CONGELADA." : "Hora voltou a passar.");
                })));

        // ---------------- Outros
        root.then(Commands.literal("vanish")
                .executes(ctx -> player(ctx, p -> {
                    if (plugin.possession().isPossessing(p) && plugin.vanish().isHidden(p)) {
                        CompanionAI.error(p, "Saia do cachorro primeiro (/horror sair).");
                        return;
                    }
                    boolean hidden = plugin.vanish().toggle(p);
                    CompanionAI.info(p, hidden ? "Você \"saiu\" do servidor (invisível)." : "Você \"entrou\" no servidor.");
                })));
        root.then(Commands.literal("painel")
                .executes(ctx -> player(ctx, p -> plugin.panel().givePanelItem(p))));
        root.then(Commands.literal("ajuda")
                .executes(ctx -> help(ctx.getSource().getSender())));

        return root.build();
    }

    // ------------------------------------------------------------------ helpers

    private static Player target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getArgument("jogador", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).get(0);
    }

    private static int effect(CompanionAI plugin, CommandContext<CommandSourceStack> ctx, int seconds, int level)
            throws CommandSyntaxException {
        CommandSender s = ctx.getSource().getSender();
        Player t = target(ctx);
        String key = StringArgumentType.getString(ctx, "efeito");
        Effects fx = Effects.byKey(key);
        if (fx == null) {
            CompanionAI.error(s, "Efeito desconhecido: " + key);
            return 0;
        }
        fx.apply(t, seconds, level, plugin.getConfig().getBoolean("efeitos-mostrar-icone", false));
        CompanionAI.info(s, fx.label + " em " + t.getName() + " por " + AdminPanel.durationText(seconds) + " (nível " + level + ").");
        return Command.SINGLE_SUCCESS;
    }

    private static void toggleMonster(CompanionAI plugin, Player p, Wolf w) {
        boolean on = plugin.monsters().toggle(w);
        CompanionAI.info(p, Dogs.displayName(w, "Cachorro") + (on ? " virou MONSTRO." : " voltou a ser cachorro."));
    }

    private static int player(CommandContext<CommandSourceStack> ctx, Consumer<Player> action) {
        CommandSender s = ctx.getSource().getSender();
        if (!(s instanceof Player p)) {
            CompanionAI.error(s, "Só jogadores podem usar isso.");
            return 0;
        }
        action.accept(p);
        return Command.SINGLE_SUCCESS;
    }

    /** Ajuda organizada; clicar numa linha já escreve o comando no chat. */
    private static int help(CommandSender s) {
        s.sendMessage(Component.text("━━━━━━━━ Horror ━━━━━━━━", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        section(s, "Cachorro", HELP_FORMA);
        section(s, "Monstro", HELP_MONSTRO);
        section(s, "Jogadores", HELP_JOGADOR);
        section(s, "Mundo", HELP_MUNDO);
        section(s, "Outros", HELP_OUTROS);
        s.sendMessage(Component.text("Clique numa linha para escrever o comando.", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
        return Command.SINGLE_SUCCESS;
    }

    private static void section(CommandSender s, String title, String[][] lines) {
        s.sendMessage(Component.text("» " + title, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        for (String[] l : lines) {
            String suggest = l[0].replaceAll("\\s*[<\\[].*$", "") + " ";
            s.sendMessage(Component.text("  " + l[0], NamedTextColor.WHITE)
                    .append(Component.text(" - " + l[1], NamedTextColor.GRAY))
                    .clickEvent(ClickEvent.suggestCommand(suggest))
                    .hoverEvent(HoverEvent.showText(Component.text("Clique para escrever", NamedTextColor.YELLOW))));
        }
    }
}
