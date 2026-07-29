package net.kccricket.clicksorted.gui;

/*
 * This file is part of ClickSorted
 *
 * ClickSorted is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * ClickSorted is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with ClickSorted. If not, see <http://www.gnu.org/licenses/>.
 */

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.clicksorted.migration.PreferenceRepair;
import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.PendingPrefs;
import net.kccricket.clicksorted.model.PlayerSortingPrefs;
import net.kccricket.clicksorted.model.PreferenceResult;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.model.StartCorner;
import net.kccricket.clicksorted.text.MessageUtil;
import net.kccricket.kcmclib.text.lang.Localized;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and shows the primary preferences UI: a single Paper Dialog listing every scalar sorting
 * preference (enabled, click/sort method, start corner, fill axis, allow-on-hover, bundle
 * in-inventory/in-containers, bundle stack-limit) plus buttons to launch the two slot/item-shaped
 * GUIs ({@link LockGuiHolder}, {@link BlacklistGuiHolder}) that don't map to dialog inputs.
 *
 * <p>Every input is permission-scoped to the exact node the equivalent {@code /clicksorted}
 * subcommand requires (see {@link net.kccricket.clicksorted.commands.ClickSortedCommands}), so the
 * dialog can never set something the player couldn't set by command.
 *
 * <p>{@link #applyResponse} is a plain-value entry point deliberately separate from dialog
 * extraction so it's unit-testable without a real {@code DialogResponseView} (which MockBukkit has
 * no way to construct).
 *
 * <p>{@link #planInputs} and {@link #planButtons} likewise separate "which elements appear, with
 * what values" from "how to render them as Paper dialog objects": every Paper dialog builder
 * ({@code DialogInput}, {@code ActionButton}, {@code Dialog} itself) is backed by a
 * {@code ServiceLoader}-provided implementation that only exists inside a running Paper server, so
 * constructing one under MockBukkit throws. The plan methods return plain {@link InputSpec}/
 * {@link ButtonSpec} descriptors instead, so the permission-gating and value-selection logic —
 * previously untestable — can be asserted directly; {@link #open} does nothing but translate those
 * descriptors into real Paper objects and show them.
 */
public final class PreferencesDialog {

    /** A seed with every field absent — {@link #open(ClickSortedPlugin, Player)} uses this. */
    private static final PendingPrefs EMPTY =
            new PendingPrefs(null, null, null, null, null, null, null, null, null);

    private PreferencesDialog() {
    }

    /** Which flavor of {@link DialogInput} an {@link InputSpec} describes. */
    public enum InputKind { BOOL, SINGLE_OPTION, NUMBER_RANGE }

    /**
     * Stable identity for every dialog element (input or button), paired with the lang key that
     * resolves its on-screen label. {@code key()} is the Paper dialog input key and is {@code null}
     * for buttons, which have no response value.
     */
    public enum DialogElement {
        ENABLED("enabled", "dialogEnabledLabel"),
        CLICK_METHOD("click_method", "dialogClickMethodLabel"),
        SORT_METHOD("sort_method", "dialogSortMethodLabel"),
        START_CORNER("start_corner", "dialogStartCornerLabel"),
        FILL_AXIS("fill_axis", "dialogFillAxisLabel"),
        HOVER("hover", "dialogHoverLabel"),
        BUNDLE_IN_INVENTORY("bundle_in_inventory", "dialogBundleInventoryLabel"),
        BUNDLE_IN_CONTAINERS("bundle_in_containers", "dialogBundleContainersLabel"),
        BUNDLE_STACK_LIMIT("bundle_stack_limit", "dialogStackLimitLabel"),
        LOCK_BUTTON(null, "dialogOpenLockGui"),
        BLACKLIST_BUTTON(null, "dialogOpenBlacklistGui"),
        SAVE_BUTTON(null, "dialogSave"),
        CANCEL_BUTTON(null, "dialogCancel");

        private final String key;
        private final String langKey;

        DialogElement(String key, String langKey) {
            this.key = key;
            this.langKey = langKey;
        }

        /** The Paper dialog input key this element reads/writes; {@code null} for buttons. */
        public String key() {
            return key;
        }

        /** The {@code lang.yml} key for this element's on-screen label. */
        public String langKey() {
            return langKey;
        }
    }

    /** One selectable entry in a {@link InputKind#SINGLE_OPTION} input. */
    public record OptionSpec(String id, boolean selected) {
    }

    /**
     * One planned dialog input. Only the field matching {@code kind} is populated: {@code boolInitial}
     * for {@link InputKind#BOOL}, {@code options} (already filtered and selection-marked) for
     * {@link InputKind#SINGLE_OPTION}, {@code numberInitial} (already clamped to [0, 64]) for
     * {@link InputKind#NUMBER_RANGE}.
     */
    public record InputSpec(DialogElement element, InputKind kind,
                             Boolean boolInitial, List<OptionSpec> options, Integer numberInitial) {
    }

    /** One planned action button. */
    public record ButtonSpec(DialogElement element) {
    }

    /** Shows the dialog seeded from the player's currently stored preferences. */
    public static void open(ClickSortedPlugin plugin, Player player) {
        open(plugin, player, EMPTY);
    }

    /**
     * Shows the dialog, seeding each input from {@code seed} when present, else from the player's
     * currently stored preference. Used both for a fresh {@code /clicksorted menu} and to restore
     * unsaved edits after a "Locked Slots…" / "Bundle Blacklist…" GUI round trip.
     */
    public static void open(ClickSortedPlugin plugin, Player player, PendingPrefs seed) {
        Localized lang = plugin.getConfigManager().lang(player.locale());

        List<DialogInput> inputs = planInputs(plugin, player, seed).stream()
                .map(spec -> toPaperInput(spec, lang))
                .toList();

        List<ActionButton> buttons = planButtons(player).stream()
                .map(spec -> toPaperButton(spec, plugin, lang))
                .toList();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(lang.getColoredMessage("dialogTitle"))
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(buttons).build()));
        player.showDialog(dialog);
    }

    /**
     * Computes which dialog inputs {@code player} may see and their initial values, without
     * touching any Paper dialog/registry API. This is the pure, unit-testable half of {@link #open}:
     * every {@code clicksorted.commands.*} gate, the seed-over-stored precedence for each value, the
     * {@code sort_method} option filtering by {@link SortingMethod#isAvailable()}, the hover dual-gate,
     * and the bundle stack-limit clamp all live here.
     */
    public static List<InputSpec> planInputs(ClickSortedPlugin plugin, Player player, PendingPrefs seed) {
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        List<InputSpec> inputs = new ArrayList<>();

        if (player.hasPermission("clicksorted.commands.sort.enabled")) {
            boolean enabled = seed.enabled() != null ? seed.enabled() : prefs.getEnabled(player);
            inputs.add(new InputSpec(DialogElement.ENABLED, InputKind.BOOL, enabled, null, null));
        }

        // Click method is resolved once up front: the hover input's visibility (below) depends on
        // whichever click method is in effect for this dialog (seeded or stored).
        ClickMethod effectiveClickMethod = seed.clickMethod() != null ? seed.clickMethod() : prefs.getClickMethod(player);

        if (player.hasPermission("clicksorted.commands.click.method")) {
            inputs.add(new InputSpec(DialogElement.CLICK_METHOD, InputKind.SINGLE_OPTION, null,
                    optionsFor(ClickMethod.values(), m -> true, effectiveClickMethod), null));
        }

        if (player.hasPermission("clicksorted.commands.sort.method")) {
            SortingMethod current = seed.sortingMethod() != null ? seed.sortingMethod() : prefs.getSortingMethod(player);
            inputs.add(new InputSpec(DialogElement.SORT_METHOD, InputKind.SINGLE_OPTION, null,
                    optionsFor(SortingMethod.values(), SortingMethod::isAvailable, current), null));
        }

        if (player.hasPermission("clicksorted.commands.sort.start-corner")) {
            StartCorner current = seed.startCorner() != null ? seed.startCorner() : prefs.getStartCorner(player);
            inputs.add(new InputSpec(DialogElement.START_CORNER, InputKind.SINGLE_OPTION, null,
                    optionsFor(StartCorner.values(), c -> true, current), null));
        }

        if (player.hasPermission("clicksorted.commands.sort.fill-axis")) {
            FillAxis current = seed.fillAxis() != null ? seed.fillAxis() : prefs.getFillAxis(player);
            inputs.add(new InputSpec(DialogElement.FILL_AXIS, InputKind.SINGLE_OPTION, null,
                    optionsFor(FillAxis.values(), a -> true, current), null));
        }

        // Omitted entirely when the (seeded or stored) click method governs hover — mirrors the
        // command tree hiding /clicksorted click allow-on-hover in the same situation. Because the
        // dialog is static, changing click method and hover in the same submission is resolved by
        // apply order in applyResponse, not by this input reactively hiding.
        if (player.hasPermission("clicksorted.commands.click.hover") && !effectiveClickMethod.governsHover()) {
            boolean hover = seed.sortOverItems() != null ? seed.sortOverItems() : prefs.getSortOverItems(player);
            inputs.add(new InputSpec(DialogElement.HOVER, InputKind.BOOL, hover, null, null));
        }

        if (player.hasPermission("clicksorted.commands.bundle")) {
            boolean bundleInv = seed.bundleInInventory() != null ? seed.bundleInInventory() : prefs.getBundlePackInInventory(player);
            inputs.add(new InputSpec(DialogElement.BUNDLE_IN_INVENTORY, InputKind.BOOL, bundleInv, null, null));

            boolean bundleCont = seed.bundleInContainers() != null ? seed.bundleInContainers() : prefs.getBundlePackInContainers(player);
            inputs.add(new InputSpec(DialogElement.BUNDLE_IN_CONTAINERS, InputKind.BOOL, bundleCont, null, null));

            // Clamp the seed to the input's declared [0, 64] range: prefs.getBundleStackLimit can
            // return an unclamped config default (defaults.bundle_stack_limit is read raw), and an
            // out-of-range initial value is rejected by the numberRange builder / breaks the dialog.
            int stackLimit = Math.min(64, Math.max(0,
                    seed.bundleStackLimit() != null ? seed.bundleStackLimit() : prefs.getBundleStackLimit(player)));
            inputs.add(new InputSpec(DialogElement.BUNDLE_STACK_LIMIT, InputKind.NUMBER_RANGE, null, null, stackLimit));
        }

        return inputs;
    }

    /**
     * Computes which action buttons {@code player} may see, without touching any Paper dialog API.
     * Save and Cancel are always present; the two GUI-launch buttons are gated the same as their
     * matching input group above.
     */
    public static List<ButtonSpec> planButtons(Player player) {
        List<ButtonSpec> buttons = new ArrayList<>();

        if (player.hasPermission("clicksorted.commands.lock")) {
            buttons.add(new ButtonSpec(DialogElement.LOCK_BUTTON));
        }

        if (player.hasPermission("clicksorted.commands.bundle")) {
            buttons.add(new ButtonSpec(DialogElement.BLACKLIST_BUTTON));
        }

        buttons.add(new ButtonSpec(DialogElement.SAVE_BUTTON));
        buttons.add(new ButtonSpec(DialogElement.CANCEL_BUTTON));

        return buttons;
    }

    /** Translates one plain-value {@link InputSpec} into the Paper dialog input it describes. */
    private static DialogInput toPaperInput(InputSpec spec, Localized lang) {
        Component label = lang.getColoredMessage(spec.element().langKey());
        return switch (spec.kind()) {
            case BOOL -> DialogInput.bool(spec.element().key(), label, spec.boolInitial(), "true", "false");
            case SINGLE_OPTION -> {
                List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
                for (OptionSpec option : spec.options()) {
                    entries.add(SingleOptionDialogInput.OptionEntry.create(
                            option.id(), Component.text(option.id()), option.selected()));
                }
                yield DialogInput.singleOption(spec.element().key(), label, entries).build();
            }
            case NUMBER_RANGE -> DialogInput.numberRange(spec.element().key(), label, 0f, 64f)
                    .step(1f)
                    .initial((float) spec.numberInitial())
                    .build();
        };
    }

    /** Translates one {@link ButtonSpec} into the Paper action button, wiring its click callback. */
    private static ActionButton toPaperButton(ButtonSpec spec, ClickSortedPlugin plugin, Localized lang) {
        Component label = lang.getColoredMessage(spec.element().langKey());
        return switch (spec.element()) {
            case LOCK_BUTTON -> ActionButton.builder(label)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (!(audience instanceof Player p)) {
                            return;
                        }
                        plugin.getPreferencesDialogService().stash(p, extract(view));
                        p.openInventory(new LockGuiHolder(plugin, p).getInventory());
                    }, ClickCallback.Options.builder().build()))
                    .build();
            case BLACKLIST_BUTTON -> ActionButton.builder(label)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (!(audience instanceof Player p)) {
                            return;
                        }
                        plugin.getPreferencesDialogService().stash(p, extract(view));
                        p.openInventory(new BlacklistGuiHolder(plugin, p).getInventory());
                    }, ClickCallback.Options.builder().build()))
                    .build();
            case SAVE_BUTTON -> ActionButton.builder(label)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (!(audience instanceof Player p)) {
                            return;
                        }
                        plugin.getPreferencesDialogService().clearStash(p);
                        applyResponse(plugin, p, extract(view));
                    }, ClickCallback.Options.builder().build()))
                    .build();
            case CANCEL_BUTTON -> ActionButton.builder(label)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            plugin.getPreferencesDialogService().clearStash(p);
                        }
                    }, ClickCallback.Options.builder().build()))
                    .build();
            default -> throw new IllegalArgumentException("Not a button element: " + spec.element());
        };
    }

    /** One {@code OptionSpec} per {@code values} constant passing {@code include}, current pre-selected. */
    private static <E extends Enum<E>> List<OptionSpec> optionsFor(
            E[] values, java.util.function.Predicate<E> include, E current) {
        List<OptionSpec> entries = new ArrayList<>();
        for (E value : values) {
            if (!include.test(value)) {
                continue;
            }
            entries.add(new OptionSpec(value.name(), value == current));
        }
        return entries;
    }

    /** Reads every possible input key out of {@code view}; a missing key yields a {@code null} field. */
    private static PendingPrefs extract(DialogResponseView view) {
        Boolean enabled = view.getBoolean("enabled");
        ClickMethod clickMethod = parseEnum(ClickMethod.values(), view.getText("click_method"));
        SortingMethod sortingMethod = parseEnum(SortingMethod.values(), view.getText("sort_method"));
        StartCorner startCorner = parseEnum(StartCorner.values(), view.getText("start_corner"));
        FillAxis fillAxis = parseEnum(FillAxis.values(), view.getText("fill_axis"));
        Boolean hover = view.getBoolean("hover");
        Boolean bundleInInventory = view.getBoolean("bundle_in_inventory");
        Boolean bundleInContainers = view.getBoolean("bundle_in_containers");
        Float stackLimitRaw = view.getFloat("bundle_stack_limit");
        Integer stackLimit = stackLimitRaw != null ? stackLimitRaw.intValue() : null;
        return new PendingPrefs(enabled, clickMethod, sortingMethod, startCorner, fillAxis, hover,
                bundleInInventory, bundleInContainers, stackLimit);
    }

    private static <E extends Enum<E>> E parseEnum(E[] values, String raw) {
        if (raw == null) {
            return null;
        }
        for (E value : values) {
            if (value.name().equals(raw)) {
                return value;
            }
        }
        return null;
    }

    /**
     * Applies every present field in {@code values} via the same {@link PlayerSortingPrefs} setters
     * the Brigadier command handlers use, in the same order and with the same veto/messaging
     * behavior — so a dialog Save and the equivalent chain of commands behave identically. Click
     * method is applied first (and hover-enforced) so a hover value submitted in the same batch is
     * evaluated against the resulting method, matching {@code ClickSortedCommands#buildClickMethod}.
     * Stops at the first listener-vetoed change, matching {@code applyBundleEnabled}'s reasoning:
     * continuing after a veto risks a second spurious event and a chat message that contradicts the
     * partially-applied state.
     */
    public static void applyResponse(ClickSortedPlugin plugin, Player player, PendingPrefs values) {
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        Localized lang = plugin.getConfigManager().lang(player.locale());

        if (values.clickMethod() != null) {
            if (reportIfBlocked(player, prefs.setClickMethod(player, values.clickMethod()))) {
                return;
            }
            MessageUtil.statusMessage(player, lang.getColoredMessage("setClickMethodTo",
                    Placeholder.unparsed("method", values.clickMethod().toString()),
                    Placeholder.unparsed("instruction", values.clickMethod().getInstruction(player.locale()))));
            PreferenceRepair.enforceHover(plugin, player, values.clickMethod());
            player.updateCommands();
        }

        if (values.enabled() != null) {
            if (reportIfBlocked(player, prefs.setEnabled(player, values.enabled()))) {
                return;
            }
            MessageUtil.statusMessage(player, lang.getColoredMessage("setEnabledStatus",
                    Placeholder.unparsed("status", label(values.enabled()))));
        }

        if (values.sortingMethod() != null) {
            if (reportIfBlocked(player, prefs.setSortingMethod(player, values.sortingMethod()))) {
                return;
            }
            MessageUtil.statusMessage(player, lang.getColoredMessage("setSortingMethodTo",
                    Placeholder.unparsed("method", values.sortingMethod().toString())));
        }

        if (values.startCorner() != null) {
            if (reportIfBlocked(player, prefs.setStartCorner(player, values.startCorner()))) {
                return;
            }
            MessageUtil.statusMessage(player, lang.getColoredMessage("setStartCornerTo",
                    Placeholder.unparsed("corner", values.startCorner().toString())));
        }

        if (values.fillAxis() != null) {
            if (reportIfBlocked(player, prefs.setFillAxis(player, values.fillAxis()))) {
                return;
            }
            MessageUtil.statusMessage(player, lang.getColoredMessage("setFillAxisTo",
                    Placeholder.unparsed("axis", values.fillAxis().toString())));
        }

        if (values.sortOverItems() != null) {
            // The (possibly just-changed) click method may govern hover; refuse silently exactly
            // like ClickSortedCommands#applyHoverSetting — the dialog already omits this input
            // whenever the *seeded* click method governs hover, but a click-method change applied
            // just above in this same batch can newly govern it.
            if (prefs.getClickMethod(player).requiredSortOverItems().isEmpty()) {
                if (reportIfBlocked(player, prefs.setSortOverItems(player, values.sortOverItems()))) {
                    return;
                }
                MessageUtil.statusMessage(player, lang.getColoredMessage("setSortOverItemsStatus",
                        Placeholder.unparsed("status", label(values.sortOverItems()))));
            }
        }

        if (values.bundleInInventory() != null) {
            if (reportIfBlocked(player, prefs.setBundlePackInInventory(player, values.bundleInInventory()))) {
                return;
            }
            MessageUtil.statusMessage(player, lang.getColoredMessage("setBundlePackInInventoryStatus",
                    Placeholder.unparsed("status", label(values.bundleInInventory()))));
        }

        if (values.bundleInContainers() != null) {
            if (reportIfBlocked(player, prefs.setBundlePackInContainers(player, values.bundleInContainers()))) {
                return;
            }
            MessageUtil.statusMessage(player, lang.getColoredMessage("setBundlePackInContainersStatus",
                    Placeholder.unparsed("status", label(values.bundleInContainers()))));
        }

        if (values.bundleStackLimit() != null) {
            // A bundle holds at most 64 weight-units; clamp exactly like
            // ClickSortedCommands#buildBundleStackLimit so a huge value can't behave as "no limit"
            // while the status message still reports the raw number.
            int clamped = Math.min(64, Math.max(0, values.bundleStackLimit()));
            if (reportIfBlocked(player, prefs.setBundleStackLimit(player, clamped))) {
                return;
            }
            MessageUtil.statusMessage(player, lang.getColoredMessage("setBundleStackLimitStatus",
                    Placeholder.unparsed("limit", clamped > 0 ? String.valueOf(clamped) : "off")));
        }
    }

    private static String label(boolean enabled) {
        return enabled ? "ENABLED" : "DISABLED";
    }

    /** Reports a listener-vetoed change and returns {@code true}; {@code false} if not cancelled. */
    private static boolean reportIfBlocked(Player player, PreferenceResult result) {
        if (!result.cancelled()) {
            return false;
        }
        MessageUtil.preferenceBlocked(player, result);
        return true;
    }
}
