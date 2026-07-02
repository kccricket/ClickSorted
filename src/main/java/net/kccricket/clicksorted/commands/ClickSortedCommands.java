package net.kccricket.clicksorted.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.clicksorted.config.LangConfig;
import net.kccricket.clicksorted.gui.BlacklistGuiHolder;
import net.kccricket.clicksorted.gui.LockGuiHolder;
import net.kccricket.clicksorted.logging.DebugLevel;
import net.kccricket.clicksorted.migration.MigrationException;
import net.kccricket.clicksorted.migration.PreferenceRepair;
import net.kccricket.clicksorted.logging.Log;
import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.model.StartCorner;
import net.kccricket.clicksorted.security.Permissions;
import net.kccricket.clicksorted.sort.BundleBenchmark;
import net.kccricket.clicksorted.text.MessageUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
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
                        MessageUtil.errorMessage(player,
                                plugin.getConfigManager().lang().getColoredMessage("noPermission"));
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
                .build();
    }

    // -------------------------------------------------------------------------
    // /clicksorted admin — groups diagnostic/admin subcommands
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildAdmin(ClickSortedPlugin plugin) {
        return Commands.literal("admin")
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
        plugin.getSortingPrefs().setEnabled(player, enabled);
        MessageUtil.statusMessage(player,
                plugin.getConfigManager().lang().getColoredMessage("setEnabledStatus",
                        Placeholder.unparsed("status", enabledLabel(enabled))));
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
        MessageUtil.errorMessage(player,
                plugin.getConfigManager().lang().getColoredMessage("invalidValue",
                        Placeholder.unparsed("value", raw),
                        Placeholder.unparsed("valid", valid)));
    }

    private static final String BOOLEAN_VALUES = "yes, no";

    /**
     * A {@code /clicksorted <domain> <literal> <value>} subcommand that parses {@code value} (case-insensitive)
     * into one of {@code values} and persists it via {@code setter}, echoing {@code langKey} with the
     * chosen value under the {@code placeholder} tag. Unrecognised input sends an error message naming
     * the bad value and valid options. Suited to plain enum preferences with no extra validation;
     * {@code sort method} and {@code click method} keep bespoke builders for their availability check and
     * instruction text.
     */
    private static <E extends Enum<E>> com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> enumPref(
            ClickSortedPlugin plugin, String literal, String permission, String argName, E[] values,
            String langKey, String placeholder, java.util.function.BiConsumer<Player, E> setter) {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(permission))
                .then(Commands.argument(argName, StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestEnum(builder, values, v -> true))
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
                            setter.accept(player, value);
                            MessageUtil.statusMessage(player,
                                    plugin.getConfigManager().lang().getColoredMessage(langKey,
                                            Placeholder.unparsed(placeholder, value.toString())));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    /**
     * Materials offered as completions for the bundle-blacklist {@code add} argument: non-legacy
     * materials that have an item form (so a bare block-only material such as {@code WATER}, which
     * cannot be a bundle entry and has no {@link org.bukkit.inventory.meta.ItemMeta}, is excluded).
     * Note that block materials with an item form (e.g. {@code DIRT}) still qualify.
     */
    static final java.util.function.Predicate<Material> SUGGESTABLE_MATERIAL =
            mat -> !mat.isLegacy() && mat.isItem();

    private static final List<String> SUGGESTABLE_MATERIAL_NAMES = java.util.Arrays.stream(Material.values())
            .filter(SUGGESTABLE_MATERIAL)
            .map(m -> m.name().toLowerCase(Locale.ROOT))
            .toList();

    /**
     * Suggests the lower-cased names of {@code values} that pass {@code include} and prefix-match the
     * current (case-insensitive) input. Shared by every enum-valued argument's {@code suggests} hook.
     */
    static <E extends Enum<E>> java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestEnum(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder, E[] values, java.util.function.Predicate<E> include) {
        String input = builder.getRemaining().toUpperCase();
        for (E value : values) {
            if (include.test(value) && value.name().startsWith(input)) {
                builder.suggest(value.name().toLowerCase());
            }
        }
        return builder.buildFuture();
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

    // -------------------------------------------------------------------------
    // /clicksorted sort method <NAME|GROUP|TREEMAP>
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildSortMethod(ClickSortedPlugin plugin) {
        return Commands.literal("method")
                .requires(src -> src.getSender().hasPermission("clicksorted.commands.sort.method"))
                .then(Commands.argument("method", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestEnum(builder, SortingMethod.values(), SortingMethod::isAvailable))
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
                        .suggests((ctx, builder) -> suggestEnum(builder, ClickMethod.values(), m -> true))
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
                                        CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getExecutor() instanceof Player player) {
            return player;
        }
        MessageUtil.errorMessage(ctx.getSource().getSender(),
                plugin.getConfigManager().lang().getColoredMessage("notFromConsole"));
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
        plugin.getSortingPrefs().setBundlePackInInventory(player, enabled);
        plugin.getSortingPrefs().setBundlePackInContainers(player, enabled);
        MessageUtil.statusMessage(player,
                plugin.getConfigManager().lang().getColoredMessage("setBundlePackEnabledStatus",
                        Placeholder.unparsed("status", enabledLabel(enabled))));
    }

    /** A {@code /clicksorted bundle <literal> <yes|no>} boolean toggle persisting via {@code setter}. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> bundleToggle(
            ClickSortedPlugin plugin, String literal, String langKey,
            java.util.function.BiConsumer<Player, Boolean> setter) {
        return Commands.literal(literal)
                .then(boolStateArg(plugin, (player, state) -> {
                    setter.accept(player, state);
                    MessageUtil.statusMessage(player,
                            plugin.getConfigManager().lang().getColoredMessage(langKey,
                                    Placeholder.unparsed("status", enabledLabel(state))));
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
                            plugin.getSortingPrefs().setBundleStackLimit(player, limit);
                            MessageUtil.statusMessage(player,
                                    plugin.getConfigManager().lang().getColoredMessage("setBundleStackLimitStatus",
                                            Placeholder.unparsed("limit", limit > 0 ? String.valueOf(limit) : "off")));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    /** Print the player's current bundle-packing settings, including the blacklist. */
    static void sendBundleStatus(ClickSortedPlugin plugin, Player player) {
        var prefs = plugin.getSortingPrefs();
        var lang = plugin.getConfigManager().lang();
        MessageUtil.statusMessage(player, lang.getColoredMessage("statusBundleInInventory",
                Placeholder.unparsed("status", enabledLabel(prefs.getBundlePackInInventory(player)))));
        MessageUtil.statusMessage(player, lang.getColoredMessage("statusBundleInContainers",
                Placeholder.unparsed("status", enabledLabel(prefs.getBundlePackInContainers(player)))));
        int limit = prefs.getBundleStackLimit(player);
        MessageUtil.statusMessage(player, lang.getColoredMessage("statusBundleStackLimit",
                Placeholder.unparsed("limit", limit > 0 ? String.valueOf(limit) : "off")));
        printBlacklistSection(player, lang, prefs.getBundleBlacklist(player), prefs.getBundleBlacklistNames(player),
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
        var lang = plugin.getConfigManager().lang();
        var prefs = plugin.getSortingPrefs();
        printBlacklistSection(player, lang, prefs.getBundleBlacklist(player), prefs.getBundleBlacklistNames(player),
                "setBundleBlacklistEmpty", "setBundleBlacklistMaterialsList", "setBundleBlacklistNamesList");
    }

    private static void printBlacklistSection(Player player, LangConfig lang,
            Set<Material> materials, Set<String> names,
            String emptyKey, String materialsKey, String namesKey) {
        if (materials.isEmpty() && names.isEmpty()) {
            MessageUtil.statusMessage(player, lang.getColoredMessage(emptyKey));
            return;
        }
        if (!materials.isEmpty()) {
            String list = materials.stream().map(Material::name).sorted().collect(Collectors.joining(", "));
            MessageUtil.statusMessage(player, lang.getColoredMessage(materialsKey,
                    Placeholder.unparsed("list", list)));
        }
        if (!names.isEmpty()) {
            String list = names.stream().sorted().collect(Collectors.joining(", "));
            MessageUtil.statusMessage(player, lang.getColoredMessage(namesKey,
                    Placeholder.unparsed("list", list)));
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
                                .then(Commands.argument("material", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            String prefix = b.getRemaining().toLowerCase(Locale.ROOT);
                                            SUGGESTABLE_MATERIAL_NAMES.forEach(name -> {
                                                if (name.startsWith(prefix)) b.suggest(name);
                                            });
                                            return b.buildFuture();
                                        })
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
                                            var lang = plugin.getConfigManager().lang();
                                            boolean added = plugin.getSortingPrefs().addToBundleBlacklist(player, mat);
                                            if (added) {
                                                MessageUtil.statusMessage(player, lang.getColoredMessage("setBundleBlacklistAdded",
                                                        Placeholder.unparsed("material", mat.name())));
                                            } else if (plugin.getSortingPrefs().getBundleBlacklist(player).contains(mat)) {
                                                MessageUtil.statusMessage(player, lang.getColoredMessage("setBundleBlacklistAlreadyPresent",
                                                        Placeholder.unparsed("material", mat.name())));
                                            }
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
                                            var lang = plugin.getConfigManager().lang();
                                            boolean added = plugin.getSortingPrefs().addToBundleBlacklistName(player, name);
                                            if (added) {
                                                MessageUtil.statusMessage(player, lang.getColoredMessage("setBundleBlacklistNameAdded",
                                                        Placeholder.unparsed("name", name)));
                                            } else if (plugin.getSortingPrefs().getBundleBlacklistNames(player).contains(name)) {
                                                MessageUtil.statusMessage(player, lang.getColoredMessage("setBundleBlacklistNameAlreadyPresent",
                                                        Placeholder.unparsed("name", name)));
                                            }
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("remove")
                        .then(Commands.literal("material")
                                .then(Commands.argument("material", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            if (ctx.getSource().getExecutor() instanceof Player player) {
                                                plugin.getSortingPrefs().getBundleBlacklist(player)
                                                        .forEach(mat -> b.suggest(mat.name().toLowerCase()));
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
                                            var lang = plugin.getConfigManager().lang();
                                            boolean removed = plugin.getSortingPrefs().removeFromBundleBlacklist(player, mat);
                                            if (removed) {
                                                MessageUtil.statusMessage(player, lang.getColoredMessage("setBundleBlacklistRemoved",
                                                        Placeholder.unparsed("material", mat.name())));
                                            } else if (!plugin.getSortingPrefs().getBundleBlacklist(player).contains(mat)) {
                                                MessageUtil.statusMessage(player, lang.getColoredMessage("setBundleBlacklistNotPresent",
                                                        Placeholder.unparsed("material", mat.name())));
                                            }
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
                                            var lang = plugin.getConfigManager().lang();
                                            boolean removed = plugin.getSortingPrefs().removeFromBundleBlacklistName(player, name);
                                            if (removed) {
                                                MessageUtil.statusMessage(player, lang.getColoredMessage("setBundleBlacklistNameRemoved",
                                                        Placeholder.unparsed("name", name)));
                                            } else if (!plugin.getSortingPrefs().getBundleBlacklistNames(player).contains(name)) {
                                                MessageUtil.statusMessage(player, lang.getColoredMessage("setBundleBlacklistNameNotPresent",
                                                        Placeholder.unparsed("name", name)));
                                            }
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
                            plugin.getSortingPrefs().clearBundleBlacklist(player);
                            MessageUtil.statusMessage(player,
                                    plugin.getConfigManager().lang().getColoredMessage("setBundleBlacklistCleared"));
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
                    var lang = plugin.getConfigManager().lang();
                    boolean enabled = prefs.getEnabled(player);
                    ClickMethod clickMethod = prefs.getClickMethod(player);
                    SortingMethod sortMethod = prefs.getSortingMethod(player);
                    StartCorner startCorner = prefs.getStartCorner(player);
                    FillAxis fillAxis = prefs.getFillAxis(player);
                    boolean hover = prefs.getSortOverItems(player);
                    MessageUtil.statusMessage(player, lang.getColoredMessage("statusEnabled",
                            Placeholder.unparsed("status", enabledLabel(enabled))));
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
                    sendBundleStatus(plugin, player);
                    return Command.SINGLE_SUCCESS;
                });
    }

    // -------------------------------------------------------------------------
    // /clicksorted admin reload
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildReload(ClickSortedPlugin plugin) {
        return Commands.literal("reload")
                .requires(src -> src.getSender().hasPermission("clicksorted.admin.commands.reload"))
                .executes(ctx -> {
                    try {
                        plugin.getConfigManager().reloadAll();
                    } catch (MigrationException e) {
                        Log.severe("Config migration failed on reload; keeping previous configuration.", e);
                        Throwable root = e.getCause() != null ? e.getCause() : e;
                        MessageUtil.statusMessage(ctx.getSource().getSender(),
                                plugin.getConfigManager().lang().getColoredMessage("configReloadFailed",
                                        Placeholder.unparsed("reason", String.valueOf(root.getMessage()))));
                        return Command.SINGLE_SUCCESS;
                    }
                    if (plugin.getConfigManager().main().getCheckForUpdates()) {
                        plugin.getUpdateChecker().check();
                    }
                    plugin.getUpdateChecker().reschedule();
                    MessageUtil.statusMessage(ctx.getSource().getSender(),
                            plugin.getConfigManager().lang().getColoredMessage("configReloaded"));
                    return Command.SINGLE_SUCCESS;
                });
    }

    // -------------------------------------------------------------------------
    // /clicksorted admin config  (was getcfg)
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildGetcfg(ClickSortedPlugin plugin) {
        return Commands.literal("config")
                .requires(src -> src.getSender().hasPermission("clicksorted.admin.commands.config"))
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

    // -------------------------------------------------------------------------
    // /clicksorted admin debug [level]
    // -------------------------------------------------------------------------

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildDebug(ClickSortedPlugin plugin) {
        return Commands.literal("debug")
                .requires(src -> src.getSender().hasPermission("clicksorted.admin.commands.debug"))
                .executes(ctx -> {
                    DebugLevel next = Log.getDebugLevel() == DebugLevel.OFF ? DebugLevel.DEBUG : DebugLevel.OFF;
                    Log.setDebugLevel(next);
                    MessageUtil.statusMessage(ctx.getSource().getSender(),
                            plugin.getConfigManager().lang().getColoredMessage("setDebugLevelTo",
                                    Placeholder.unparsed("level", next.name())));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("level", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestEnum(builder, DebugLevel.values(), l -> true))
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

    // -------------------------------------------------------------------------
    // /clicksorted admin benchmark [iterations]
    // -------------------------------------------------------------------------

    private static final int BENCH_DEFAULT_ITERATIONS = 2000;
    private static final int BENCH_MIN_ITERATIONS = 100;
    private static final int BENCH_MAX_ITERATIONS = 50_000;

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildBenchmark(ClickSortedPlugin plugin) {
        return Commands.literal("benchmark")
                .requires(src -> src.getSender().hasPermission("clicksorted.admin.commands.benchmark"))
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
