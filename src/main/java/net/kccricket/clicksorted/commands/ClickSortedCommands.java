package net.kccricket.clicksorted.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.clicksorted.gui.LockGuiHolder;
import net.kccricket.clicksorted.logging.DebugLevel;
import net.kccricket.clicksorted.migration.PreferenceRepair;
import net.kccricket.clicksorted.logging.Log;
import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.model.StartCorner;
import net.kccricket.clicksorted.sort.BundleBenchmark;
import net.kccricket.clicksorted.text.MessageUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

public class ClickSortedCommands {

    /**
     * Shared per-player throttle gate for command execution. Console and other non-player senders
     * are always exempt. Returns {@code true} (and sends a rate-limited notice) when the command
     * should be dropped.
     */
    private static boolean throttled(ClickSortedPlugin plugin, CommandSourceStack src) {
        return src.getExecutor() instanceof Player player && plugin.getActionThrottle().throttled(player);
    }

    /** The {@code ENABLED}/{@code DISABLED} status label shown for a boolean setting. */
    private static String enabledLabel(boolean enabled) {
        return enabled ? "ENABLED" : "DISABLED";
    }

    public static LiteralCommandNode<CommandSourceStack> build(ClickSortedPlugin plugin) {
        return Commands.literal("clicksorted")
                .then(buildSet(plugin))
                .then(buildStatus(plugin))
                .then(buildReload(plugin))
                .then(buildGetcfg(plugin))
                .then(buildDebug(plugin))
                .then(buildBenchmark(plugin))
                .build();
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildSet(ClickSortedPlugin plugin) {
        return Commands.literal("set")
                .then(buildSortMethod(plugin))
                .then(buildClickMethod(plugin))
                .then(buildStartCorner(plugin))
                .then(buildFillAxis(plugin))
                .then(buildHover(plugin))
                .then(buildBundle(plugin))
                .then(buildLock(plugin));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildStartCorner(ClickSortedPlugin plugin) {
        return enumPref(plugin, "start-corner", "clicksorted.commands.sort", "corner",
                StartCorner.values(), "setStartCornerTo", "corner",
                (player, corner) -> plugin.getSortingPrefs().setStartCorner(player, corner));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildFillAxis(ClickSortedPlugin plugin) {
        return enumPref(plugin, "fill-axis", "clicksorted.commands.sort", "axis",
                FillAxis.values(), "setFillAxisTo", "axis",
                (player, axis) -> plugin.getSortingPrefs().setFillAxis(player, axis));
    }

    /**
     * A {@code /clicksorted set <literal> <value>} subcommand that parses {@code value} (case-insensitive)
     * into one of {@code values} and persists it via {@code setter}, echoing {@code langKey} with the
     * chosen value under the {@code placeholder} tag. Unrecognised input is a silent no-op, matching the
     * other per-player preference setters. Suited to plain enum preferences with no extra validation;
     * {@code sort-method} and {@code click-method} keep bespoke builders for their availability check and
     * instruction text.
     */
    private static <E extends Enum<E>> com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> enumPref(
            ClickSortedPlugin plugin, String literal, String permission, String argName, E[] values,
            String langKey, String placeholder, java.util.function.BiConsumer<Player, E> setter) {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(permission))
                .then(Commands.argument(argName, StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String input = builder.getRemaining().toUpperCase();
                            for (E value : values) {
                                if (value.name().startsWith(input)) {
                                    builder.suggest(value.name().toLowerCase());
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            Player player = requirePlayer(plugin, ctx);
                            if (player == null || throttled(plugin, ctx.getSource())) {
                                return Command.SINGLE_SUCCESS;
                            }
                            E value = parseEnum(values, StringArgumentType.getString(ctx, argName));
                            if (value == null) {
                                return Command.SINGLE_SUCCESS; // unrecognised → no-op
                            }
                            setter.accept(player, value);
                            MessageUtil.statusMessage(player,
                                    plugin.getConfigManager().lang().getColoredMessage(langKey,
                                            Placeholder.unparsed(placeholder, value.toString())));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    /** The matching enum constant for {@code raw} (case-insensitive), or {@code null} if none match. */
    private static <E extends Enum<E>> E parseEnum(E[] values, String raw) {
        for (E value : values) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return null;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildSortMethod(ClickSortedPlugin plugin) {
        return Commands.literal("sort-method")
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
                            Player player = requirePlayer(plugin, ctx);
                            if (player == null || throttled(plugin, ctx.getSource())) {
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

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildClickMethod(ClickSortedPlugin plugin) {
        return Commands.literal("click-method")
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
                            Player player = requirePlayer(plugin, ctx);
                            if (player == null || throttled(plugin, ctx.getSource())) {
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
                                // Methods that govern hover (SINGLE_CLICK, CONTROL_DROP) force the player's
                                // hover preference to the only usable value, messaging them on any change.
                                PreferenceRepair.enforceHover(plugin, player, method);
                            } catch (IllegalArgumentException ignored) {
                                // invalid value → no-op
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildHover(ClickSortedPlugin plugin) {
        return Commands.literal("hover")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.hover"))
                .executes(ctx -> {
                    Player player = requirePlayer(plugin, ctx);
                    if (player == null || throttled(plugin, ctx.getSource())) {
                        return Command.SINGLE_SUCCESS;
                    }
                    boolean current = plugin.getSortingPrefs().getSortOverItems(player);
                    applyHoverSetting(plugin, player, !current);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, b) -> { b.suggest("true"); b.suggest("false"); return b.buildFuture(); })
                        .executes(ctx -> {
                            Player player = requirePlayer(plugin, ctx);
                            if (player == null || throttled(plugin, ctx.getSource())) {
                                return Command.SINGLE_SUCCESS;
                            }
                            Boolean value = parseState(StringArgumentType.getString(ctx, "value"));
                            if (value == null) {
                                return Command.SINGLE_SUCCESS; // unrecognised → no-op
                            }
                            applyHoverSetting(plugin, player, value);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static void applyHoverSetting(ClickSortedPlugin plugin, Player player, boolean enabled) {
        ClickMethod clickMethod = plugin.getSortingPrefs().getClickMethod(player);
        if (clickMethod.requiredSortOverItems().isPresent()) {
            // The active click method governs hover; refuse to change the stored value and explain why.
            MessageUtil.statusMessage(player,
                    plugin.getConfigManager().lang().getColoredMessage("hoverGovernedByClickMethod",
                            Placeholder.unparsed("method", clickMethod.name())));
            return;
        }
        plugin.getSortingPrefs().setSortOverItems(player, enabled);
        MessageUtil.statusMessage(player,
                plugin.getConfigManager().lang().getColoredMessage("setSortOverItemsStatus",
                        Placeholder.unparsed("status", enabledLabel(enabled))));
    }

    private static final java.util.Set<String> ON_WORDS = java.util.Set.of("enable", "on", "true", "yes");
    private static final java.util.Set<String> OFF_WORDS = java.util.Set.of("disable", "off", "false", "no");

    /** {@code true}/{@code false} for recognised on/off words, or {@code null} if unrecognised. */
    private static Boolean parseState(String s) {
        String lower = s.toLowerCase();
        if (ON_WORDS.contains(lower)) return true;
        if (OFF_WORDS.contains(lower)) return false;
        return null;
    }

    /** Resolve the executing player, or {@code null} (after sending the console notice) if not a player. */
    private static Player requirePlayer(ClickSortedPlugin plugin,
                                        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getExecutor() instanceof Player player) {
            return player;
        }
        MessageUtil.errorMessage(ctx.getSource().getSender(),
                plugin.getConfigManager().lang().getColoredMessage("notFromConsole"));
        return null;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildBundle(ClickSortedPlugin plugin) {
        return Commands.literal("bundle")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.bundle"))
                .executes(ctx -> {
                    Player player = requirePlayer(plugin, ctx);
                    if (player == null || throttled(plugin, ctx.getSource())) {
                        return Command.SINGLE_SUCCESS;
                    }
                    sendBundleStatus(plugin, player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(bundleToggle(plugin, "inventory", "setBundlePackInventoryStatus",
                        (player, status) -> plugin.getSortingPrefs().setBundlePackInventory(player, status)))
                .then(bundleToggle(plugin, "others", "setBundlePackOthersStatus",
                        (player, status) -> plugin.getSortingPrefs().setBundlePackOthers(player, status)))
                .then(buildBundleStackLimit(plugin));
    }

    /** A {@code /clicksorted set bundle <literal> <on|off>} boolean toggle persisting via {@code setter}. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> bundleToggle(
            ClickSortedPlugin plugin, String literal, String langKey,
            java.util.function.BiConsumer<Player, Boolean> setter) {
        return Commands.literal(literal)
                .then(Commands.argument("state", StringArgumentType.word())
                        .suggests((ctx, b) -> { b.suggest("on"); b.suggest("off"); return b.buildFuture(); })
                        .executes(ctx -> {
                            Player player = requirePlayer(plugin, ctx);
                            if (player == null || throttled(plugin, ctx.getSource())) {
                                return Command.SINGLE_SUCCESS;
                            }
                            Boolean state = parseState(StringArgumentType.getString(ctx, "state"));
                            if (state == null) {
                                return Command.SINGLE_SUCCESS; // unrecognised → no-op
                            }
                            setter.accept(player, state);
                            MessageUtil.statusMessage(player,
                                    plugin.getConfigManager().lang().getColoredMessage(langKey,
                                            Placeholder.unparsed("status", enabledLabel(state))));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildBundleStackLimit(ClickSortedPlugin plugin) {
        return Commands.literal("stacklimit")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, b) -> { b.suggest("12"); b.suggest("32"); b.suggest("off"); return b.buildFuture(); })
                        .executes(ctx -> {
                            Player player = requirePlayer(plugin, ctx);
                            if (player == null || throttled(plugin, ctx.getSource())) {
                                return Command.SINGLE_SUCCESS;
                            }
                            String raw = StringArgumentType.getString(ctx, "value");
                            int limit;
                            if (Boolean.FALSE.equals(parseState(raw))) {
                                limit = 0; // an off-word disables the entry limit
                            } else {
                                try {
                                    // A bundle holds at most 64 weight-units (64 single non-stackable items),
                                    // so any entry cap above 64 can never bind; clamp so a huge value doesn't
                                    // silently behave as "no limit" while the status still reports the number.
                                    limit = Math.min(64, Math.max(0, Integer.parseInt(raw)));
                                } catch (NumberFormatException e) {
                                    return Command.SINGLE_SUCCESS; // unrecognised → no-op
                                }
                            }
                            plugin.getSortingPrefs().setBundleStackLimit(player, limit);
                            MessageUtil.statusMessage(player,
                                    plugin.getConfigManager().lang().getColoredMessage("setBundleStackLimitStatus",
                                            Placeholder.unparsed("limit", limit > 0 ? String.valueOf(limit) : "off")));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    /** Print the player's three current bundle-packing settings. */
    private static void sendBundleStatus(ClickSortedPlugin plugin, Player player) {
        var prefs = plugin.getSortingPrefs();
        var lang = plugin.getConfigManager().lang();
        MessageUtil.statusMessage(player, lang.getColoredMessage("setBundlePackInventoryStatus",
                Placeholder.unparsed("status", enabledLabel(prefs.getBundlePackInventory(player)))));
        MessageUtil.statusMessage(player, lang.getColoredMessage("setBundlePackOthersStatus",
                Placeholder.unparsed("status", enabledLabel(prefs.getBundlePackOthers(player)))));
        int limit = prefs.getBundleStackLimit(player);
        MessageUtil.statusMessage(player, lang.getColoredMessage("setBundleStackLimitStatus",
                Placeholder.unparsed("limit", limit > 0 ? String.valueOf(limit) : "off")));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildLock(ClickSortedPlugin plugin) {
        return Commands.literal("lock")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.lock"))
                .executes(ctx -> {
                    Player player = requirePlayer(plugin, ctx);
                    if (player == null || throttled(plugin, ctx.getSource())) {
                        return Command.SINGLE_SUCCESS;
                    }
                    player.openInventory(new LockGuiHolder(plugin, player).getInventory());
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildStatus(ClickSortedPlugin plugin) {
        return Commands.literal("status")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.status"))
                .executes(ctx -> {
                    Player player = requirePlayer(plugin, ctx);
                    if (player == null) {
                        return Command.SINGLE_SUCCESS;
                    }
                    var prefs = plugin.getSortingPrefs();
                    var lang = plugin.getConfigManager().lang();
                    ClickMethod clickMethod = prefs.getClickMethod(player);
                    SortingMethod sortMethod = prefs.getSortingMethod(player);
                    StartCorner startCorner = prefs.getStartCorner(player);
                    FillAxis fillAxis = prefs.getFillAxis(player);
                    boolean hover = prefs.getSortOverItems(player);
                    MessageUtil.statusMessage(player, lang.getColoredMessage("statusClickMethod",
                            Placeholder.unparsed("method", clickMethod.toString())));
                    MessageUtil.statusMessage(player, lang.getColoredMessage("statusSortMethod",
                            Placeholder.unparsed("method", sortMethod.toString())));
                    MessageUtil.statusMessage(player, lang.getColoredMessage("statusStartCorner",
                            Placeholder.unparsed("corner", startCorner.toString())));
                    MessageUtil.statusMessage(player, lang.getColoredMessage("statusFillAxis",
                            Placeholder.unparsed("axis", fillAxis.toString())));
                    MessageUtil.statusMessage(player, lang.getColoredMessage("statusHover",
                            Placeholder.unparsed("status", enabledLabel(hover))));
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildReload(ClickSortedPlugin plugin) {
        return Commands.literal("reload")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.reload"))
                .executes(ctx -> {
                    plugin.getConfigManager().reloadAll();
                    if (plugin.getConfigManager().main().getCheckForUpdates()) {
                        plugin.getUpdateChecker().check();
                    }
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

    private static final int BENCH_DEFAULT_ITERATIONS = 2000;
    private static final int BENCH_MIN_ITERATIONS = 100;
    private static final int BENCH_MAX_ITERATIONS = 50_000;

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildBenchmark(ClickSortedPlugin plugin) {
        return Commands.literal("benchmark")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.benchmark"))
                .executes(ctx -> runBenchmark(plugin, ctx.getSource(), BENCH_DEFAULT_ITERATIONS))
                .then(Commands.argument("iterations", IntegerArgumentType.integer(BENCH_MIN_ITERATIONS, BENCH_MAX_ITERATIONS))
                        .executes(ctx -> runBenchmark(plugin, ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "iterations"))));
    }

    /**
     * Runs the in-situ benchmark synchronously on the calling thread (the main thread for a command),
     * briefly pausing the server, and reports per-operation timings for the sort and repack paths.
     */
    private static int runBenchmark(ClickSortedPlugin plugin, CommandSourceStack src, int iterations) {
        var sender = src.getSender();
        MessageUtil.statusMessage(sender, "Running ClickSorted benchmark (" + iterations
                + " iterations)—the server will pause briefly…");

        BundleBenchmark.Result result = BundleBenchmark.run(iterations);

        reportStats(sender, "sortAndMerge", result.sort());
        reportStats(sender, "bundle repack", result.repack());
        return Command.SINGLE_SUCCESS;
    }

    private static void reportStats(org.bukkit.command.CommandSender sender, String label, BundleBenchmark.Stats s) {
        MessageUtil.statusMessage(sender, String.format(
                "%s: min %.1f / median %.1f / p95 %.1f / max %.1f µs/op (n=%d, %.1f ms total)",
                label, s.minUs(), s.medianUs(), s.p95Us(), s.maxUs(), s.iterations(),
                s.totalNanos() / 1_000_000.0));
    }
}
