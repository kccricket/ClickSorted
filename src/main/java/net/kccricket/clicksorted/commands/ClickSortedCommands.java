package net.kccricket.clicksorted.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.clicksorted.gui.LockGuiHolder;
import net.kccricket.clicksorted.logging.DebugLevel;
import net.kccricket.clicksorted.logging.Log;
import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.text.MessageUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

public class ClickSortedCommands {

    public static LiteralCommandNode<CommandSourceStack> build(ClickSortedPlugin plugin) {
        return Commands.literal("clicksorted")
                .then(buildSort(plugin))
                .then(buildClick(plugin))
                .then(buildShiftClick(plugin))
                .then(buildLock(plugin))
                .then(buildBundle(plugin))
                .then(buildBundleCap(plugin))
                .then(buildReload(plugin))
                .then(buildGetcfg(plugin))
                .then(buildDebug(plugin))
                .build();
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildSort(ClickSortedPlugin plugin) {
        return Commands.literal("sort")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.sort"))
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

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildClick(ClickSortedPlugin plugin) {
        return Commands.literal("click")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.click"))
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
                                                Placeholder.unparsed("method", method.toString()),
                                                Placeholder.unparsed("instruction", method.getInstruction())));
                            } catch (IllegalArgumentException ignored) {
                                // invalid value → no-op
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildShiftClick(ClickSortedPlugin plugin) {
        return Commands.literal("shiftclick")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.shiftclick"))
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

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildBundle(ClickSortedPlugin plugin) {
        return Commands.literal("bundle")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.bundle"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
                        MessageUtil.errorMessage(ctx.getSource().getSender(),
                                plugin.getConfigManager().lang().getColoredMessage("notFromConsole"));
                        return Command.SINGLE_SUCCESS;
                    }
                    var prefs = plugin.getSortingPrefs();
                    int entryCap = prefs.getBundleCapEnabled(player)
                            ? plugin.getConfigManager().main().getBundleEntryCap()
                            : 0;
                    int packed = plugin.getSortService().packOnly(player, entryCap);
                    if (packed >= 0) {
                        MessageUtil.statusMessage(player,
                                plugin.getConfigManager().lang().getColoredMessage("bundlePacked",
                                        Placeholder.unparsed("count", String.valueOf(packed))));
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildBundleCap(ClickSortedPlugin plugin) {
        return Commands.literal("bundlecap")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.bundlecap"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
                        MessageUtil.errorMessage(ctx.getSource().getSender(),
                                plugin.getConfigManager().lang().getColoredMessage("notFromConsole"));
                        return Command.SINGLE_SUCCESS;
                    }
                    boolean current = plugin.getSortingPrefs().getBundleCapEnabled(player);
                    plugin.getSortingPrefs().setBundleCapEnabled(player, !current);
                    String status = current ? "DISABLED" : "ENABLED";
                    int cap = plugin.getConfigManager().main().getBundleEntryCap();
                    MessageUtil.statusMessage(player,
                            plugin.getConfigManager().lang().getColoredMessage("setBundleCapStatus",
                                    Placeholder.unparsed("status", status),
                                    Placeholder.unparsed("cap", String.valueOf(cap))));
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildLock(ClickSortedPlugin plugin) {
        return Commands.literal("lock")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.lock"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getExecutor() instanceof Player player)) {
                        MessageUtil.errorMessage(ctx.getSource().getSender(),
                                plugin.getConfigManager().lang().getColoredMessage("notFromConsole"));
                        return Command.SINGLE_SUCCESS;
                    }
                    player.openInventory(new LockGuiHolder(plugin, player).getInventory());
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildReload(ClickSortedPlugin plugin) {
        return Commands.literal("reload")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.reload"))
                .executes(ctx -> {
                    plugin.getConfigManager().reloadAll();
                    MessageUtil.statusMessage(ctx.getSource().getSender(),
                            plugin.getConfigManager().lang().getColoredMessage("configReloaded"));
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildGetcfg(ClickSortedPlugin plugin) {
        return Commands.literal("getcfg")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.getcfg"))
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

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildDebug(ClickSortedPlugin plugin) {
        return Commands.literal("debug")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.debug"))
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
