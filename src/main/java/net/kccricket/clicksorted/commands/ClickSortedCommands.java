package net.kccricket.clicksorted.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.clicksorted.gui.BlacklistGuiHolder;
import net.kccricket.clicksorted.gui.LockGuiHolder;
import net.kccricket.clicksorted.gui.PreferencesDialog;
import net.kccricket.kcmclib.logging.DebugLevel;
import net.kccricket.kcmclib.migration.MigrationException;
import net.kccricket.clicksorted.migration.PreferenceRepair;
import net.kccricket.kcmclib.logging.Log;
import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.PreferenceResult;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.model.StartCorner;
import net.kccricket.clicksorted.security.Permissions;
import net.kccricket.kcmclib.commands.Suggest;
import net.kccricket.clicksorted.sort.BundleBenchmark;
import net.kccricket.clicksorted.text.PreferenceMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
                .requires(src -> src.getSender().hasPermission("clicksorted.commands"))
                .executes(ctx -> {
                    Player player = requirePlayer(plugin, ctx);
                    if (player == null || throttled(plugin, ctx.getSource())) {
                        return Command.SINGLE_SUCCESS;
                    }
                    if (!player.hasPermission(Permissions.PERM_MASTER)
                            || !player.hasPermission("clicksorted.commands.sort.enabled")) {
                        plugin.messages().to(player).error().send("noPermission");
                        return Command.SINGLE_SUCCESS;
                    }
                    applyEnabledSetting(plugin, player, !plugin.getSortingPrefs().getEnabled(player));
                    return Command.SINGLE_SUCCESS;
                })
                .then(buildAdmin(plugin))
                .then(buildClick(plugin))
                .then(buildSort(plugin))
                .then(buildLockSlots(plugin))
                .then(buildBundle(plugin))
                .then(buildStatus(plugin))
                .then(buildMenu(plugin))
                .build();
    }

    // -------------------------------------------------------------------------
    // /clicksorted menu — opens the dialog-based preferences UI
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildMenu(ClickSortedPlugin plugin) {
        return Commands.literal("menu")
                .requires(src -> src.getSender().hasPermission(Permissions.PERM_MASTER)
                        && src.getSender().hasPermission("clicksorted.commands.menu"))
                .executes(ctx -> {
                    Player player = requirePlayer(plugin, ctx);
                    if (player == null || throttled(plugin, ctx.getSource())) {
                        return Command.SINGLE_SUCCESS;
                    }
                    PreferencesDialog.open(plugin, player);
                    return Command.SINGLE_SUCCESS;
                });
    }

    // -------------------------------------------------------------------------
    // /clicksorted admin — groups diagnostic/admin subcommands. Guarded by the OR of every
    // child's own .requires predicate (canReload/canGetcfg/canDebug/canBenchmark below): a
    // sender granted only one child node (e.g. clicksorted.admin.commands.reload) must still be
    // able to reach that child. Without this, Brigadier still syncs "admin" to every player
    // (it only prunes a literal whose own predicate fails, not one whose children all failed),
    // so a plain player would see "admin" offered in tab completion with nothing reachable
    // beneath it.
    // -------------------------------------------------------------------------

    private static boolean canReload(CommandSourceStack src) {
        return src.getSender().hasPermission(Permissions.PERM_ADMIN_RELOAD);
    }

    private static boolean canGetcfg(CommandSourceStack src) {
        return src.getSender().hasPermission(Permissions.PERM_ADMIN_CONFIG);
    }

    private static boolean canDebug(CommandSourceStack src) {
        return src.getSender().hasPermission(Permissions.PERM_ADMIN_DEBUG);
    }

    private static boolean canBenchmark(ClickSortedPlugin plugin, CommandSourceStack src) {
        return plugin.getConfigManager().main().getEnableBenchmark()
                && src.getSender().hasPermission(Permissions.PERM_ADMIN_BENCHMARK);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildAdmin(ClickSortedPlugin plugin) {
        return Commands.literal("admin")
                .requires(src -> canReload(src) || canGetcfg(src) || canDebug(src) || canBenchmark(plugin, src))
                .then(buildDebug(plugin))
                .then(buildGetcfg(plugin))
                .then(buildReload(plugin))
                .then(buildBenchmark(plugin));
    }

    // -------------------------------------------------------------------------
    // /clicksorted click — groups click-method and allow-on-hover
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildClick(ClickSortedPlugin plugin) {
        return Commands.literal("click")
                .requires(src -> src.getSender().hasPermission(Permissions.PERM_MASTER)
                        && src.getSender().hasPermission("clicksorted.commands.click"))
                .then(buildClickMethod(plugin))
                .then(buildHover(plugin));
    }

    // -------------------------------------------------------------------------
    // /clicksorted sort — groups enabled, method, start-corner, fill-axis
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildSort(ClickSortedPlugin plugin) {
        return Commands.literal("sort")
                .requires(src -> src.getSender().hasPermission(Permissions.PERM_MASTER)
                        && src.getSender().hasPermission("clicksorted.commands.sort"))
                .then(buildEnabled(plugin))
                .then(buildSortMethod(plugin))
                .then(buildStartCorner(plugin))
                .then(buildFillAxis(plugin));
    }

    // -------------------------------------------------------------------------
    // /clicksorted sort enabled [yes|no]
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildEnabled(ClickSortedPlugin plugin) {
        return Commands.literal("enabled")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.sort.enabled"))
                .executes(ctx -> {
                    Player player = requirePlayer(plugin, ctx);
                    if (player == null || throttled(plugin, ctx.getSource())) {
                        return Command.SINGLE_SUCCESS;
                    }
                    applyEnabledSetting(plugin, player, !plugin.getSortingPrefs().getEnabled(player));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("state", StringArgumentType.word())
                        .suggests((ctx, b) -> { b.suggest("yes"); b.suggest("no"); return b.buildFuture(); })
                        .executes(ctx -> {
                            Player player = requirePlayer(plugin, ctx);
                            if (player == null || throttled(plugin, ctx.getSource())) {
                                return Command.SINGLE_SUCCESS;
                            }
                            String raw = StringArgumentType.getString(ctx, "state");
                            Boolean state = parseState(raw);
                            if (state == null) {
                                sendInvalidValue(plugin, player, raw, BOOLEAN_VALUES);
                                return Command.SINGLE_SUCCESS;
                            }
                            applyEnabledSetting(plugin, player, state);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static void applyEnabledSetting(ClickSortedPlugin plugin, Player player, boolean enabled) {
        if (blockedAndReported(plugin, player, plugin.getSortingPrefs().setEnabled(player, enabled))) {
            return;
        }
        plugin.messages().to(player).status().send("setEnabledStatus",
                Placeholder.unparsed("status", enabledLabel(enabled)));
    }

    /**
     * Reports a listener-vetoed preference change and returns {@code true}; returns {@code false}
     * without messaging when the result was not cancelled. Owns the cancel-report half of every
     * setter call site so the idiom can't be half-applied.
     */
    private static boolean blockedAndReported(ClickSortedPlugin plugin, Player player, PreferenceResult result) {
        if (!result.cancelled()) return false;
        PreferenceMessages.preferenceBlocked(plugin, player, result);
        return true;
    }

    /**
     * Reports the outcome of a blacklist add/remove: {@code appliedKey} on APPLIED,
     * {@code unchangedKey} on UNCHANGED (both with the value under {@code placeholder}),
     * or the blocked-change message on CANCELLED.
     */
    private static void reportBlacklistResult(ClickSortedPlugin plugin, Player player, PreferenceResult result,
            String appliedKey, String unchangedKey, String placeholder, String value) {
        if (blockedAndReported(plugin, player, result)) return;
        plugin.messages().to(player).status().send(result.applied() ? appliedKey : unchangedKey,
                Placeholder.unparsed(placeholder, value));
    }

    // -------------------------------------------------------------------------
    // /clicksorted sort start-corner / fill-axis
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildStartCorner(ClickSortedPlugin plugin) {
        return enumPref(plugin, "start-corner", "clicksorted.commands.sort.start-corner", "corner",
                StartCorner.values(), "setStartCornerTo", "corner",
                (player, corner) -> plugin.getSortingPrefs().setStartCorner(player, corner));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildFillAxis(ClickSortedPlugin plugin) {
        return enumPref(plugin, "fill-axis", "clicksorted.commands.sort.fill-axis", "axis",
                FillAxis.values(), "setFillAxisTo", "axis",
                (player, axis) -> plugin.getSortingPrefs().setFillAxis(player, axis));
    }

    /** Comma-joined lower-cased names of {@code values} passing {@code include}, for error text. */
    private static <E extends Enum<E>> String validList(E[] values, java.util.function.Predicate<E> include) {
        return java.util.Arrays.stream(values).filter(include)
                .map(e -> e.name().toLowerCase()).collect(java.util.stream.Collectors.joining(", "));
    }

    /** Send the shared "invalid value" error naming {@code raw} and the {@code valid} options. */
    private static void sendInvalidValue(ClickSortedPlugin plugin, Player player, String raw, String valid) {
        plugin.messages().to(player).error().send("invalidValue",
                Placeholder.unparsed("value", raw),
                Placeholder.unparsed("valid", valid));
    }

    private static final String BOOLEAN_VALUES = "yes, no";

    /**
     * A {@code /clicksorted <domain> <literal> <value>} subcommand that parses {@code value} (case-insensitive)
     * into one of {@code values} and persists it via {@code setter}, echoing {@code langKey} with the
     * chosen value under the {@code placeholder} tag — or, if a listener cancelled the change, the
     * blocked-change message instead. Unrecognised input sends an error message naming the bad value
     * and valid options. Suited to plain enum preferences with no extra validation; {@code sort method}
     * and {@code click method} keep bespoke builders for their availability check and instruction text.
     */
    private static <E extends Enum<E>> com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> enumPref(
            ClickSortedPlugin plugin, String literal, String permission, String argName, E[] values,
            String langKey, String placeholder, java.util.function.BiFunction<Player, E, PreferenceResult> setter) {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(permission))
                .then(Commands.argument(argName, StringArgumentType.word())
                        .suggests((ctx, builder) -> Suggest.enumValues(builder, values, v -> true))
                        .executes(ctx -> {
                            Player player = requirePlayer(plugin, ctx);
                            if (player == null || throttled(plugin, ctx.getSource())) {
                                return Command.SINGLE_SUCCESS;
                            }
                            String raw = StringArgumentType.getString(ctx, argName);
                            E value = parseEnum(values, raw);
                            if (value == null) {
                                sendInvalidValue(plugin, player, raw, validList(values, v -> true));
                                return Command.SINGLE_SUCCESS;
                            }
                            PreferenceResult result = setter.apply(player, value);
                            if (blockedAndReported(plugin, player, result)) {
                                return Command.SINGLE_SUCCESS;
                            }
                            plugin.messages().to(player).status().send(langKey,
                                    Placeholder.unparsed(placeholder, value.toString()));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    /**
     * Materials offered as completions for the bundle-blacklist {@code add} argument: non-legacy
     * materials that have an item form (so a bare block-only material such as {@code WATER}, which
     * cannot be a bundle entry and has no {@link org.bukkit.inventory.meta.ItemMeta}, is excluded).
     * Note that block materials with an item form (e.g. {@code DIRT}) still qualify. Namespaced IDs
     * (e.g. {@code minecraft:dirt}) are produced and memoized by {@link Suggest#materialNames}.
     */
    static final java.util.function.Predicate<Material> SUGGESTABLE_MATERIAL =
            mat -> !mat.isLegacy() && mat.isItem();

    /** The matching enum constant for {@code raw} (case-insensitive), or {@code null} if none match. */
    private static <E extends Enum<E>> E parseEnum(E[] values, String raw) {
        for (E value : values) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // /clicksorted sort method <NAME|GROUP|TREEMAP>
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildSortMethod(ClickSortedPlugin plugin) {
        return Commands.literal("method")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.sort.method"))
                .then(Commands.argument("method", StringArgumentType.word())
                        .suggests((ctx, builder) -> Suggest.enumValues(builder, SortingMethod.values(), SortingMethod::isAvailable))
                        .executes(ctx -> {
                            Player player = requirePlayer(plugin, ctx);
                            if (player == null || throttled(plugin, ctx.getSource())) {
                                return Command.SINGLE_SUCCESS;
                            }
                            String arg = StringArgumentType.getString(ctx, "method");
                            try {
                                SortingMethod method = SortingMethod.valueOf(arg.toUpperCase());
                                if (!method.isAvailable()) {
                                    plugin.messages().to(player).error().send("sortingMethodNotAvailable",
                                            Placeholder.unparsed("method", method.toString()));
                                    return Command.SINGLE_SUCCESS;
                                }
                                PreferenceResult result = plugin.getSortingPrefs().setSortingMethod(player, method);
                                if (blockedAndReported(plugin, player, result)) {
                                    return Command.SINGLE_SUCCESS;
                                }
                                plugin.messages().to(player).status().send("setSortingMethodTo",
                                        Placeholder.unparsed("method", method.toString()));
                            } catch (IllegalArgumentException ignored) {
                                sendInvalidValue(plugin, player, arg,
                                        validList(SortingMethod.values(), SortingMethod::isAvailable));
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    // -------------------------------------------------------------------------
    // /clicksorted click method <…>
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildClickMethod(ClickSortedPlugin plugin) {
        return Commands.literal("method")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.click.method"))
                .then(Commands.argument("method", StringArgumentType.word())
                        .suggests((ctx, builder) -> Suggest.enumValues(builder, ClickMethod.values(), m -> true))
                        .executes(ctx -> {
                            Player player = requirePlayer(plugin, ctx);
                            if (player == null || throttled(plugin, ctx.getSource())) {
                                return Command.SINGLE_SUCCESS;
                            }
                            String arg = StringArgumentType.getString(ctx, "method");
                            try {
                                ClickMethod method = ClickMethod.valueOf(arg.toUpperCase());
                                PreferenceResult result = plugin.getSortingPrefs().setClickMethod(player, method);
                                if (blockedAndReported(plugin, player, result)) {
                                    return Command.SINGLE_SUCCESS;
                                }
                                plugin.messages().to(player).status().send("setClickMethodTo",
                                        Placeholder.unparsed("method", method.toString()),
                                        Placeholder.unparsed("instruction", method.getInstruction(player.locale())));
                                // Methods that govern hover (SINGLE_CLICK, CONTROL_DROP) force the player's
                                // hover preference to the only usable value, messaging them on any change.
                                PreferenceRepair.enforceHover(plugin, player, method);
                                // Re-push the command tree so allow-on-hover visibility updates immediately.
                                player.updateCommands();
                            } catch (IllegalArgumentException ignored) {
                                sendInvalidValue(plugin, player, arg,
                                        validList(ClickMethod.values(), m -> true));
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    // -------------------------------------------------------------------------
    // /clicksorted click allow-on-hover [yes|no]
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildHover(ClickSortedPlugin plugin) {
        return Commands.literal("allow-on-hover")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.click.hover")
                        // Hide when the player's current click method governs hover automatically.
                        && (!(src.getExecutor() instanceof Player p)
                                || !plugin.getSortingPrefs().getClickMethod(p).governsHover()))
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
                        .suggests((ctx, b) -> { b.suggest("yes"); b.suggest("no"); return b.buildFuture(); })
                        .executes(ctx -> {
                            Player player = requirePlayer(plugin, ctx);
                            if (player == null || throttled(plugin, ctx.getSource())) {
                                return Command.SINGLE_SUCCESS;
                            }
                            String raw = StringArgumentType.getString(ctx, "value");
                            Boolean value = parseState(raw);
                            if (value == null) {
                                sendInvalidValue(plugin, player, raw, BOOLEAN_VALUES);
                                return Command.SINGLE_SUCCESS;
                            }
                            applyHoverSetting(plugin, player, value);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static void applyHoverSetting(ClickSortedPlugin plugin, Player player, boolean enabled) {
        ClickMethod clickMethod = plugin.getSortingPrefs().getClickMethod(player);
        if (clickMethod.requiredSortOverItems().isPresent()) {
            // The active click method governs hover; refuse to change the stored value and explain why.
            plugin.messages().to(player).status().send("hoverGovernedByClickMethod",
                    Placeholder.unparsed("method", clickMethod.name()));
            return;
        }
        if (blockedAndReported(plugin, player, plugin.getSortingPrefs().setSortOverItems(player, enabled))) {
            return;
        }
        plugin.messages().to(player).status().send("setSortOverItemsStatus",
                Placeholder.unparsed("status", enabledLabel(enabled)));
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
                                        CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getExecutor() instanceof Player player) {
            return player;
        }
        plugin.messages().to(ctx.getSource().getSender()).error().send("notFromConsole");
        return null;
    }

    // -------------------------------------------------------------------------
    // /clicksorted lock-slots
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildLockSlots(ClickSortedPlugin plugin) {
        return Commands.literal("lock-slots")
                .requires(src -> src.getSender().hasPermission(Permissions.PERM_MASTER)
                        && src.getSender().hasPermission("clicksorted.commands.lock"))
                .executes(ctx -> {
                    Player player = requirePlayer(plugin, ctx);
                    if (player == null || throttled(plugin, ctx.getSource())) {
                        return Command.SINGLE_SUCCESS;
                    }
                    player.openInventory(new LockGuiHolder(plugin, player).getInventory());
                    return Command.SINGLE_SUCCESS;
                });
    }

    // -------------------------------------------------------------------------
    // /clicksorted bundle — groups all bundle-packing subcommands
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildBundle(ClickSortedPlugin plugin) {
        return Commands.literal("bundle")
                .requires(src -> src.getSender().hasPermission(Permissions.PERM_MASTER)
                        && src.getSender().hasPermission("clicksorted.commands.bundle"))
                .then(buildBundleEnabled(plugin))
                .then(buildBundleStackLimit(plugin))
                .then(buildBundleBlacklist(plugin));
    }

    // -------------------------------------------------------------------------
    // /clicksorted bundle enabled — combined toggle; in-inventory / in-containers below
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildBundleEnabled(ClickSortedPlugin plugin) {
        return Commands.literal("enabled")
                .executes(ctx -> {
                    Player player = requirePlayer(plugin, ctx);
                    if (player == null || throttled(plugin, ctx.getSource())) {
                        return Command.SINGLE_SUCCESS;
                    }
                    var prefs = plugin.getSortingPrefs();
                    boolean inv = prefs.getBundlePackInInventory(player);
                    boolean cont = prefs.getBundlePackInContainers(player);
                    // "On unless both already on" — mixed state (one on, one off) → both on.
                    boolean target = !(inv && cont);
                    applyBundleEnabled(plugin, player, target);
                    return Command.SINGLE_SUCCESS;
                })
                .then(boolStateArg(plugin, (player, state) -> applyBundleEnabled(plugin, player, state)))
                .then(bundleToggle(plugin, "in-inventory", "setBundlePackInInventoryStatus",
                        (player, status) -> plugin.getSortingPrefs().setBundlePackInInventory(player, status)))
                .then(bundleToggle(plugin, "in-containers", "setBundlePackInContainersStatus",
                        (player, status) -> plugin.getSortingPrefs().setBundlePackInContainers(player, status)));
    }

    private static void applyBundleEnabled(ClickSortedPlugin plugin, Player player, boolean enabled) {
        // Stop at the first veto: running the second setter after the first was cancelled would
        // fire a spurious event, and (worse) reporting "blocked" after one half already persisted
        // makes chat contradict the actual state.
        if (blockedAndReported(plugin, player, plugin.getSortingPrefs().setBundlePackInInventory(player, enabled))) {
            return;
        }
        if (blockedAndReported(plugin, player, plugin.getSortingPrefs().setBundlePackInContainers(player, enabled))) {
            return;
        }
        plugin.messages().to(player).status().send("setBundlePackEnabledStatus",
                Placeholder.unparsed("status", enabledLabel(enabled)));
    }

    /** A {@code /clicksorted bundle <literal> <yes|no>} boolean toggle persisting via {@code setter}. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> bundleToggle(
            ClickSortedPlugin plugin, String literal, String langKey,
            java.util.function.BiFunction<Player, Boolean, PreferenceResult> setter) {
        return Commands.literal(literal)
                .then(boolStateArg(plugin, (player, state) -> {
                    if (blockedAndReported(plugin, player, setter.apply(player, state))) {
                        return;
                    }
                    plugin.messages().to(player).status().send(langKey,
                            Placeholder.unparsed("status", enabledLabel(state)));
                }));
    }

    /** A {@code <yes|no>} argument node that parses and validates a boolean state, then calls {@code applyFn}. */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> boolStateArg(
            ClickSortedPlugin plugin, java.util.function.BiConsumer<Player, Boolean> applyFn) {
        return Commands.argument("state", StringArgumentType.word())
                .suggests((ctx, b) -> { b.suggest("yes"); b.suggest("no"); return b.buildFuture(); })
                .executes(ctx -> {
                    Player player = requirePlayer(plugin, ctx);
                    if (player == null || throttled(plugin, ctx.getSource())) {
                        return Command.SINGLE_SUCCESS;
                    }
                    String raw = StringArgumentType.getString(ctx, "state");
                    Boolean state = parseState(raw);
                    if (state == null) {
                        sendInvalidValue(plugin, player, raw, BOOLEAN_VALUES);
                        return Command.SINGLE_SUCCESS;
                    }
                    applyFn.accept(player, state);
                    return Command.SINGLE_SUCCESS;
                });
    }

    // -------------------------------------------------------------------------
    // /clicksorted bundle stack-limit <n|off>
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildBundleStackLimit(ClickSortedPlugin plugin) {
        return Commands.literal("stack-limit")
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
                                    sendInvalidValue(plugin, player, raw, "a number from 0 to 64, or off");
                                    return Command.SINGLE_SUCCESS;
                                }
                            }
                            PreferenceResult result = plugin.getSortingPrefs().setBundleStackLimit(player, limit);
                            if (blockedAndReported(plugin, player, result)) {
                                return Command.SINGLE_SUCCESS;
                            }
                            plugin.messages().to(player).status().send("setBundleStackLimitStatus",
                                    Placeholder.unparsed("limit", limit > 0 ? String.valueOf(limit) : "off"));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    /** Print the player's current bundle-packing settings, including the blacklist. */
    static void sendBundleStatus(ClickSortedPlugin plugin, Player player) {
        var prefs = plugin.getSortingPrefs();
        plugin.messages().to(player).status().send("statusBundleInInventory",
                Placeholder.unparsed("status", enabledLabel(prefs.getBundlePackInInventory(player))));
        plugin.messages().to(player).status().send("statusBundleInContainers",
                Placeholder.unparsed("status", enabledLabel(prefs.getBundlePackInContainers(player))));
        int limit = prefs.getBundleStackLimit(player);
        plugin.messages().to(player).status().send("statusBundleStackLimit",
                Placeholder.unparsed("limit", limit > 0 ? String.valueOf(limit) : "off"));
        printBlacklistSection(plugin, player, prefs.getBundleBlacklist(player), prefs.getBundleBlacklistNames(player),
                "statusBundleBlacklistEmpty", "statusBundleBlacklistMaterials", "statusBundleBlacklistNames");
    }

    private static int openBlacklistGui(ClickSortedPlugin plugin, CommandContext<CommandSourceStack> ctx) {
        Player player = requirePlayer(plugin, ctx);
        if (player == null || throttled(plugin, ctx.getSource())) {
            return Command.SINGLE_SUCCESS;
        }
        player.openInventory(new BlacklistGuiHolder(plugin, player).getInventory());
        return Command.SINGLE_SUCCESS;
    }

    static void sendBlacklistStatus(ClickSortedPlugin plugin, Player player) {
        var prefs = plugin.getSortingPrefs();
        printBlacklistSection(plugin, player, prefs.getBundleBlacklist(player), prefs.getBundleBlacklistNames(player),
                "setBundleBlacklistEmpty", "setBundleBlacklistMaterialsList", "setBundleBlacklistNamesList");
    }

    private static void printBlacklistSection(ClickSortedPlugin plugin, Player player,
            Set<Material> materials, Set<String> names,
            String emptyKey, String materialsKey, String namesKey) {
        if (materials.isEmpty() && names.isEmpty()) {
            plugin.messages().to(player).status().send(emptyKey);
            return;
        }
        if (!materials.isEmpty()) {
            String list = materials.stream().map(Material::name).sorted().collect(Collectors.joining(", "));
            plugin.messages().to(player).status().send(materialsKey,
                    Placeholder.unparsed("list", list));
        }
        if (!names.isEmpty()) {
            String list = names.stream().sorted().collect(Collectors.joining(", "));
            plugin.messages().to(player).status().send(namesKey,
                    Placeholder.unparsed("list", list));
        }
    }

    // -------------------------------------------------------------------------
    // /clicksorted bundle blacklist — GUI and text subcommands
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildBundleBlacklist(ClickSortedPlugin plugin) {
        return Commands.literal("blacklist")
                .executes(ctx -> openBlacklistGui(plugin, ctx))
                .then(Commands.literal("gui")
                        .executes(ctx -> openBlacklistGui(plugin, ctx)))
                .then(Commands.literal("add")
                        .then(Commands.literal("material")
                                // greedyString, not word(): namespaced IDs (minecraft:dirt) contain
                                // a colon, which Brigadier's word()/unquoted string() do not accept.
                                .then(Commands.argument("material", StringArgumentType.greedyString())
                                        .suggests((ctx, b) -> Suggest.prefixed(b, Suggest.materialNames(SUGGESTABLE_MATERIAL)))
                                        .executes(ctx -> {
                                            Player player = requirePlayer(plugin, ctx);
                                            if (player == null || throttled(plugin, ctx.getSource())) {
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String raw = StringArgumentType.getString(ctx, "material");
                                            Material mat = Material.matchMaterial(raw);
                                            // Reject non-item materials (e.g. WATER): they can never be a bundle
                                            // entry and yield a null ItemMeta that would NPE the blacklist GUI.
                                            if (mat == null || mat.isLegacy() || !mat.isItem()) {
                                                sendInvalidValue(plugin, player, raw, "a material name");
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            reportBlacklistResult(plugin, player,
                                                    plugin.getSortingPrefs().addToBundleBlacklist(player, mat),
                                                    "setBundleBlacklistAdded", "setBundleBlacklistAlreadyPresent",
                                                    "material", mat.name());
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(Commands.literal("item-name")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            Player player = requirePlayer(plugin, ctx);
                                            if (player == null || throttled(plugin, ctx.getSource())) {
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String name = StringArgumentType.getString(ctx, "name").trim();
                                            if (name.isEmpty()) {
                                                sendInvalidValue(plugin, player, name, "a display name");
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            reportBlacklistResult(plugin, player,
                                                    plugin.getSortingPrefs().addToBundleBlacklistName(player, name),
                                                    "setBundleBlacklistNameAdded", "setBundleBlacklistNameAlreadyPresent",
                                                    "name", name);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("remove")
                        .then(Commands.literal("material")
                                // greedyString to match "add material" — see the comment there.
                                .then(Commands.argument("material", StringArgumentType.greedyString())
                                        .suggests((ctx, b) -> {
                                            if (ctx.getSource().getExecutor() instanceof Player player) {
                                                List<String> namespaced = plugin.getSortingPrefs().getBundleBlacklist(player).stream()
                                                        .map(mat -> mat.getKey().toString())
                                                        .toList();
                                                return Suggest.prefixed(b, namespaced);
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            Player player = requirePlayer(plugin, ctx);
                                            if (player == null || throttled(plugin, ctx.getSource())) {
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String raw = StringArgumentType.getString(ctx, "material");
                                            Material mat = Material.matchMaterial(raw);
                                            if (mat == null || mat.isLegacy()) {
                                                sendInvalidValue(plugin, player, raw, "a material name");
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            reportBlacklistResult(plugin, player,
                                                    plugin.getSortingPrefs().removeFromBundleBlacklist(player, mat),
                                                    "setBundleBlacklistRemoved", "setBundleBlacklistNotPresent",
                                                    "material", mat.name());
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(Commands.literal("item-name")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests((ctx, b) -> {
                                            if (ctx.getSource().getExecutor() instanceof Player player) {
                                                String input = b.getRemaining().toLowerCase();
                                                plugin.getSortingPrefs().getBundleBlacklistNames(player)
                                                        .stream()
                                                        .filter(n -> n.toLowerCase().startsWith(input))
                                                        .forEach(b::suggest);
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            Player player = requirePlayer(plugin, ctx);
                                            if (player == null || throttled(plugin, ctx.getSource())) {
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            String name = StringArgumentType.getString(ctx, "name").trim();
                                            reportBlacklistResult(plugin, player,
                                                    plugin.getSortingPrefs().removeFromBundleBlacklistName(player, name),
                                                    "setBundleBlacklistNameRemoved", "setBundleBlacklistNameNotPresent",
                                                    "name", name);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            Player player = requirePlayer(plugin, ctx);
                            if (player == null || throttled(plugin, ctx.getSource())) {
                                return Command.SINGLE_SUCCESS;
                            }
                            sendBlacklistStatus(plugin, player);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            Player player = requirePlayer(plugin, ctx);
                            if (player == null || throttled(plugin, ctx.getSource())) {
                                return Command.SINGLE_SUCCESS;
                            }
                            PreferenceResult result = plugin.getSortingPrefs().clearBundleBlacklist(player);
                            if (blockedAndReported(plugin, player, result)) {
                                return Command.SINGLE_SUCCESS;
                            }
                            // APPLIED and UNCHANGED (already empty) both report "cleared" — matches the
                            // prior unconditional behavior, which never distinguished the two.
                            plugin.messages().to(player).status().send("setBundleBlacklistCleared");
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    // -------------------------------------------------------------------------
    // /clicksorted status
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildStatus(ClickSortedPlugin plugin) {
        return Commands.literal("status")
                .requires(src -> src.getSender().hasPermission(Permissions.PERM_MASTER)
                        && src.getSender().hasPermission("clicksorted.commands.status"))
                .executes(ctx -> {
                    Player player = requirePlayer(plugin, ctx);
                    if (player == null) {
                        return Command.SINGLE_SUCCESS;
                    }
                    var prefs = plugin.getSortingPrefs();
                    boolean enabled = prefs.getEnabled(player);
                    ClickMethod clickMethod = prefs.getClickMethod(player);
                    SortingMethod sortMethod = prefs.getSortingMethod(player);
                    StartCorner startCorner = prefs.getStartCorner(player);
                    FillAxis fillAxis = prefs.getFillAxis(player);
                    boolean hover = prefs.getSortOverItems(player);
                    plugin.messages().to(player).status().send("statusEnabled",
                            Placeholder.unparsed("status", enabledLabel(enabled)));
                    plugin.messages().to(player).status().send("statusClickMethod",
                            Placeholder.unparsed("method", clickMethod.toString()));
                    plugin.messages().to(player).status().send("statusSortMethod",
                            Placeholder.unparsed("method", sortMethod.toString()));
                    plugin.messages().to(player).status().send("statusStartCorner",
                            Placeholder.unparsed("corner", startCorner.toString()));
                    plugin.messages().to(player).status().send("statusFillAxis",
                            Placeholder.unparsed("axis", fillAxis.toString()));
                    plugin.messages().to(player).status().send("statusHover",
                            Placeholder.unparsed("status", enabledLabel(hover)));
                    sendBundleStatus(plugin, player);
                    return Command.SINGLE_SUCCESS;
                });
    }

    // -------------------------------------------------------------------------
    // /clicksorted admin reload
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildReload(ClickSortedPlugin plugin) {
        return Commands.literal("reload")
                .requires(ClickSortedCommands::canReload)
                .executes(ctx -> {
                    try {
                        plugin.getConfigManager().reloadAll();
                    } catch (MigrationException e) {
                        Log.severe("Config migration failed on reload; keeping previous configuration.", e);
                        Throwable root = e.getCause() != null ? e.getCause() : e;
                        plugin.messages().to(ctx.getSource().getSender()).status().send("configReloadFailed",
                                Placeholder.unparsed("reason", String.valueOf(root.getMessage())));
                        return Command.SINGLE_SUCCESS;
                    }
                    plugin.getUpdateChecker().restart();
                    plugin.messages().to(ctx.getSource().getSender()).status().send("configReloaded");
                    return Command.SINGLE_SUCCESS;
                });
    }

    // -------------------------------------------------------------------------
    // /clicksorted admin config  (was getcfg)
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildGetcfg(ClickSortedPlugin plugin) {
        return Commands.literal("config")
                .requires(ClickSortedCommands::canGetcfg)
                .executes(ctx -> {
                    for (String key : plugin.getConfig().getKeys(true)) {
                        if (!plugin.getConfig().isConfigurationSection(key)) {
                            plugin.messages().to(ctx.getSource().getSender()).raw()
                                    .send(Component.text(key + " = " + plugin.getConfig().get(key)));
                        }
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }

    // -------------------------------------------------------------------------
    // /clicksorted admin debug [level]
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildDebug(ClickSortedPlugin plugin) {
        return Commands.literal("debug")
                .requires(ClickSortedCommands::canDebug)
                .executes(ctx -> {
                    DebugLevel next = Log.getDebugLevel() == DebugLevel.OFF ? DebugLevel.DEBUG : DebugLevel.OFF;
                    Log.setDebugLevel(next);
                    plugin.messages().to(ctx.getSource().getSender()).status().send("setDebugLevelTo",
                            Placeholder.unparsed("level", next.name()));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("level", StringArgumentType.word())
                        .suggests((ctx, builder) -> Suggest.enumValues(builder, DebugLevel.values(), l -> true))
                        .executes(ctx -> {
                            String arg = StringArgumentType.getString(ctx, "level");
                            try {
                                DebugLevel level = DebugLevel.valueOf(arg.toUpperCase());
                                Log.setDebugLevel(level);
                                plugin.messages().to(ctx.getSource().getSender()).status().send("setDebugLevelTo",
                                        Placeholder.unparsed("level", level.name()));
                            } catch (IllegalArgumentException ignored) {
                                plugin.messages().to(ctx.getSource().getSender()).error().send("invalidDebugLevel",
                                        Placeholder.unparsed("level", arg));
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    // -------------------------------------------------------------------------
    // /clicksorted admin benchmark [iterations]
    // -------------------------------------------------------------------------

    private static final int BENCH_DEFAULT_ITERATIONS = 2000;
    private static final int BENCH_MIN_ITERATIONS = 100;
    private static final int BENCH_MAX_ITERATIONS = 50_000;

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildBenchmark(ClickSortedPlugin plugin) {
        return Commands.literal("benchmark")
                .requires(src -> canBenchmark(plugin, src))
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
        plugin.messages().to(sender).status().send(Component.text("Running ClickSorted benchmark (" + iterations
                + " iterations)—the server will pause briefly…"));

        BundleBenchmark.Result result = BundleBenchmark.run(iterations);

        reportStats(plugin, sender, "sortAndMerge", result.sort());
        reportStats(plugin, sender, "bundle repack", result.repack());
        return Command.SINGLE_SUCCESS;
    }

    private static void reportStats(ClickSortedPlugin plugin, org.bukkit.command.CommandSender sender, String label, BundleBenchmark.Stats s) {
        plugin.messages().to(sender).status().send(Component.text(String.format(
                "%s: min %.1f / median %.1f / p95 %.1f / max %.1f µs/op (n=%d, %.1f ms total)",
                label, s.minUs(), s.medianUs(), s.p95Us(), s.maxUs(), s.iterations(),
                s.totalNanos() / 1_000_000.0)));
    }
}
