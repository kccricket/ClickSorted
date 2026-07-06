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
import net.kccricket.clicksorted.config.LangConfig;
import net.kccricket.clicksorted.migration.PreferenceRepair;
import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.PendingPrefs;
import net.kccricket.clicksorted.model.PlayerSortingPrefs;
import net.kccricket.clicksorted.model.PreferenceResult;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.model.StartCorner;
import net.kccricket.clicksorted.text.MessageUtil;
import net.kyori.adventure.audience.Audience;
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
 */
public final class PreferencesDialog {

    /** A seed with every field absent — {@link #open(ClickSortedPlugin, Player)} uses this. */
    private static final PendingPrefs EMPTY =
            new PendingPrefs(null, null, null, null, null, null, null, null, null);

    private PreferencesDialog() {
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
        LangConfig lang = plugin.getConfigManager().lang();
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        List<DialogInput> inputs = new ArrayList<>();

        if (player.hasPermission("clicksorted.commands.sort.enabled")) {
            boolean enabled = seed.enabled() != null ? seed.enabled() : prefs.getEnabled(player);
            inputs.add(DialogInput.bool("enabled", lang.getColoredMessage("dialogEnabledLabel"),
                    enabled, "true", "false"));
        }

        // Click method is resolved once up front: the hover input's visibility (below) depends on
        // whichever click method is in effect for this dialog (seeded or stored).
        ClickMethod effectiveClickMethod = seed.clickMethod() != null ? seed.clickMethod() : prefs.getClickMethod(player);

        if (player.hasPermission("clicksorted.commands.click.method")) {
            inputs.add(DialogInput.singleOption("click_method", lang.getColoredMessage("dialogClickMethodLabel"),
                    optionsFor(ClickMethod.values(), m -> true, effectiveClickMethod)).build());
        }

        if (player.hasPermission("clicksorted.commands.sort.method")) {
            SortingMethod current = seed.sortingMethod() != null ? seed.sortingMethod() : prefs.getSortingMethod(player);
            inputs.add(DialogInput.singleOption("sort_method", lang.getColoredMessage("dialogSortMethodLabel"),
                    optionsFor(SortingMethod.values(), SortingMethod::isAvailable, current)).build());
        }

        if (player.hasPermission("clicksorted.commands.sort.start-corner")) {
            StartCorner current = seed.startCorner() != null ? seed.startCorner() : prefs.getStartCorner(player);
            inputs.add(DialogInput.singleOption("start_corner", lang.getColoredMessage("dialogStartCornerLabel"),
                    optionsFor(StartCorner.values(), c -> true, current)).build());
        }

        if (player.hasPermission("clicksorted.commands.sort.fill-axis")) {
            FillAxis current = seed.fillAxis() != null ? seed.fillAxis() : prefs.getFillAxis(player);
            inputs.add(DialogInput.singleOption("fill_axis", lang.getColoredMessage("dialogFillAxisLabel"),
                    optionsFor(FillAxis.values(), a -> true, current)).build());
        }

        // Omitted entirely when the (seeded or stored) click method governs hover — mirrors the
        // command tree hiding /clicksorted click allow-on-hover in the same situation. Because the
        // dialog is static, changing click method and hover in the same submission is resolved by
        // apply order in applyResponse, not by this input reactively hiding.
        if (player.hasPermission("clicksorted.commands.click.hover") && !effectiveClickMethod.governsHover()) {
            boolean hover = seed.sortOverItems() != null ? seed.sortOverItems() : prefs.getSortOverItems(player);
            inputs.add(DialogInput.bool("hover", lang.getColoredMessage("dialogHoverLabel"), hover, "true", "false"));
        }

        if (player.hasPermission("clicksorted.commands.bundle")) {
            boolean bundleInv = seed.bundleInInventory() != null ? seed.bundleInInventory() : prefs.getBundlePackInInventory(player);
            inputs.add(DialogInput.bool("bundle_in_inventory", lang.getColoredMessage("dialogBundleInventoryLabel"),
                    bundleInv, "true", "false"));

            boolean bundleCont = seed.bundleInContainers() != null ? seed.bundleInContainers() : prefs.getBundlePackInContainers(player);
            inputs.add(DialogInput.bool("bundle_in_containers", lang.getColoredMessage("dialogBundleContainersLabel"),
                    bundleCont, "true", "false"));

            int stackLimit = seed.bundleStackLimit() != null ? seed.bundleStackLimit() : prefs.getBundleStackLimit(player);
            inputs.add(DialogInput.numberRange("bundle_stack_limit", lang.getColoredMessage("dialogStackLimitLabel"),
                            0f, 64f)
                    .step(1f)
                    .initial((float) stackLimit)
                    .build());
        }

        List<ActionButton> buttons = new ArrayList<>();

        if (player.hasPermission("clicksorted.commands.lock")) {
            buttons.add(ActionButton.builder(lang.getColoredMessage("dialogOpenLockGui"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (!(audience instanceof Player p)) {
                            return;
                        }
                        plugin.getPreferencesDialogService().stash(p, extract(view));
                        p.openInventory(new LockGuiHolder(plugin, p).getInventory());
                    }, ClickCallback.Options.builder().build()))
                    .build());
        }

        if (player.hasPermission("clicksorted.commands.bundle")) {
            buttons.add(ActionButton.builder(lang.getColoredMessage("dialogOpenBlacklistGui"))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (!(audience instanceof Player p)) {
                            return;
                        }
                        plugin.getPreferencesDialogService().stash(p, extract(view));
                        p.openInventory(new BlacklistGuiHolder(plugin, p).getInventory());
                    }, ClickCallback.Options.builder().build()))
                    .build());
        }

        buttons.add(ActionButton.builder(lang.getColoredMessage("dialogSave"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player p)) {
                        return;
                    }
                    plugin.getPreferencesDialogService().clearStash(p);
                    applyResponse(plugin, p, extract(view));
                }, ClickCallback.Options.builder().build()))
                .build());

        buttons.add(ActionButton.builder(lang.getColoredMessage("dialogCancel"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        plugin.getPreferencesDialogService().clearStash(p);
                    }
                }, ClickCallback.Options.builder().build()))
                .build());

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(lang.getColoredMessage("dialogTitle"))
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(buttons).build()));
        player.showDialog(dialog);
    }

    /** One {@code OptionEntry} per {@code values} constant passing {@code include}, current pre-selected. */
    private static <E extends Enum<E>> List<SingleOptionDialogInput.OptionEntry> optionsFor(
            E[] values, java.util.function.Predicate<E> include, E current) {
        List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
        for (E value : values) {
            if (!include.test(value)) {
                continue;
            }
            entries.add(SingleOptionDialogInput.OptionEntry.create(
                    value.name(), Component.text(value.name()), value == current));
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
        LangConfig lang = plugin.getConfigManager().lang();

        if (values.clickMethod() != null) {
            if (reportIfBlocked(player, prefs.setClickMethod(player, values.clickMethod()))) {
                return;
            }
            MessageUtil.statusMessage(player, lang.getColoredMessage("setClickMethodTo",
                    Placeholder.unparsed("method", values.clickMethod().toString()),
                    Placeholder.unparsed("instruction", values.clickMethod().getInstruction())));
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
