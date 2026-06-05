package net.kccricket.clicksort.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kccricket.clicksort.ClickSortPlugin;
import net.kccricket.clicksort.logging.DebugLevel;
import net.kccricket.clicksort.logging.Log;
import net.kccricket.clicksort.model.ClickMethod;
import net.kccricket.clicksort.model.SortingMethod;
import net.kccricket.clicksort.text.MessageUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

public class ClickSortCommands {

    public static LiteralCommandNode<CommandSourceStack> build(ClickSortPlugin plugin) {
        return Commands.literal("clicksort")
                .then(buildSort(plugin))
                .then(buildClick(plugin))
                .then(buildShiftClick(plugin))
                .then(buildReload(plugin))
                .then(buildGetcfg(plugin))
                .then(buildDebug(plugin))
                .build();
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildSort(ClickSortPlugin plugin) {
        return Commands.literal("sort")
                .requires(src -> src.getSender().hasPermission("clicksort.commands.sort"))
                .then(Commands.argument("method", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String input = builder.getRemaining().toUpperCase();
                            for (SortingMethod m : SortingMethod.values()) {
                                if (m.isAvailable() && m.name().startsWith(input)) {
                                    builder.suggest(m.name().toLowerCase());
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            if (!(ctx.getSource().getExecutor() instanceof Player player)) {
                                MessageUtil.errorMessage(ctx.getSource().getSender(),
                                        plugin.getConfigManager().lang().getColoredMessage("notFromConsole"));
                                return Command.SINGLE_SUCCESS;
                            }
                            String arg = StringArgumentType.getString(ctx, "method");
                            try {
                                SortingMethod method = SortingMethod.valueOf(arg.toUpperCase());
                                if (!method.isAvailable()) {
                                    MessageUtil.errorMessage(player,
                                            plugin.getConfigManager().lang().getColoredMessage("sortingMethodNotAvailable",
                                                    Placeholder.unparsed("method", method.toString())));
                                    return Command.SINGLE_SUCCESS;
                                }
                                plugin.getSortingPrefs().setSortingMethod(player, method);
                                MessageUtil.statusMessage(player,
                                        plugin.getConfigManager().lang().getColoredMessage("setSortingMethodTo",
                                                Placeholder.unparsed("method", method.toString())));
                            } catch (IllegalArgumentException ignored) {
                                // invalid value → no-op
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildClick(ClickSortPlugin plugin) {
        return Commands.literal("click")
                .requires(src -> src.getSender().hasPermission("clicksort.commands.click"))
                .then(Commands.argument("method", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String input = builder.getRemaining().toUpperCase();
                            for (ClickMethod m : ClickMethod.values()) {
                                if (m.name().startsWith(input)) {
                                    builder.suggest(m.name().toLowerCase());
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            if (!(ctx.getSource().getExecutor() instanceof Player player)) {
                                MessageUtil.errorMessage(ctx.getSource().getSender(),
                                        plugin.getConfigManager().lang().getColoredMessage("notFromConsole"));
                                return Command.SINGLE_SUCCESS;
                            }
                            String arg = StringArgumentType.getString(ctx, "method");
                            try {
                                ClickMethod method = ClickMethod.valueOf(arg.toUpperCase());
                                plugin.getSortingPrefs().setClickMethod(player, method);
                                MessageUtil.statusMessage(player,
                                        plugin.getConfigManager().lang().getColoredMessage("setClickMethodTo",
                                                Placeholder.unparsed("method", method.toString())));
                            } catch (IllegalArgumentException ignored) {
                                // invalid value → no-op
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildShiftClick(ClickSortPlugin plugin) {
        return Commands.literal("shiftclick")
                .requires(src -> src.getSender().hasPermission("clicksort.commands.shiftclick"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
                        MessageUtil.errorMessage(ctx.getSource().getSender(),
                                plugin.getConfigManager().lang().getColoredMessage("notFromConsole"));
                        return Command.SINGLE_SUCCESS;
                    }
                    boolean current = plugin.getSortingPrefs().getShiftClickAllowed(player);
                    plugin.getSortingPrefs().setShiftClickAllowed(player, !current);
                    String status = current ? "DISABLED" : "ENABLED";
                    MessageUtil.statusMessage(player,
                            plugin.getConfigManager().lang().getColoredMessage("setShiftClickStatus",
                                    Placeholder.unparsed("status", status)));
                    if (current) {
                        MessageUtil.statusMessage(player, plugin.getConfigManager().lang().getColoredMessage("tipToReEnable"));
                        plugin.getMessenger().message(player, "shiftclick", 60,
                                plugin.getConfigManager().lang().getColoredMessage("tipToChangeMode"));
                    } else {
                        MessageUtil.statusMessage(player, plugin.getConfigManager().lang().getColoredMessage("tipToDisable"));
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildReload(ClickSortPlugin plugin) {
        return Commands.literal("reload")
                .requires(src -> src.getSender().hasPermission("clicksort.commands.reload"))
                .executes(ctx -> {
                    plugin.getConfigManager().reloadAll();
                    MessageUtil.statusMessage(ctx.getSource().getSender(),
                            plugin.getConfigManager().lang().getColoredMessage("configReloaded"));
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildGetcfg(ClickSortPlugin plugin) {
        return Commands.literal("getcfg")
                .requires(src -> src.getSender().hasPermission("clicksort.commands.getcfg"))
                .executes(ctx -> {
                    for (String key : plugin.getConfig().getKeys(true)) {
                        if (!plugin.getConfig().isConfigurationSection(key)) {
                            MessageUtil.rawMessage(ctx.getSource().getSender(),
                                    key + " = " + plugin.getConfig().get(key));
                        }
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildDebug(ClickSortPlugin plugin) {
        return Commands.literal("debug")
                .requires(src -> src.getSender().hasPermission("clicksort.commands.debug"))
                .executes(ctx -> {
                    DebugLevel next = Log.getDebugLevel() == DebugLevel.OFF ? DebugLevel.DEBUG : DebugLevel.OFF;
                    Log.setDebugLevel(next);
                    MessageUtil.statusMessage(ctx.getSource().getSender(),
                            plugin.getConfigManager().lang().getColoredMessage("setDebugLevelTo",
                                    Placeholder.unparsed("level", next.name())));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("level", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String input = builder.getRemaining().toUpperCase();
                            for (DebugLevel l : DebugLevel.values()) {
                                if (l.name().startsWith(input)) {
                                    builder.suggest(l.name().toLowerCase());
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String arg = StringArgumentType.getString(ctx, "level");
                            try {
                                DebugLevel level = DebugLevel.valueOf(arg.toUpperCase());
                                Log.setDebugLevel(level);
                                MessageUtil.statusMessage(ctx.getSource().getSender(),
                                        plugin.getConfigManager().lang().getColoredMessage("setDebugLevelTo",
                                                Placeholder.unparsed("level", level.name())));
                            } catch (IllegalArgumentException ignored) {
                                MessageUtil.errorMessage(ctx.getSource().getSender(),
                                        plugin.getConfigManager().lang().getColoredMessage("invalidDebugLevel",
                                                Placeholder.unparsed("level", arg)));
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
