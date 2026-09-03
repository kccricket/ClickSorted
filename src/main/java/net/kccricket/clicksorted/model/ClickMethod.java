package net.kccricket.clicksorted.model;

import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.kcmclib.util.EnumParse;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Locale;
import java.util.Optional;

public enum ClickMethod {
    DOUBLE_CLICK, SINGLE_CLICK, SWAP, CONTROL_DROP, SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK;

    public static final ClickMethod DEFAULT = SWAP;

    /** The hover (sort-over-items) value this method requires, or empty if it leaves it to the player. */
    public Optional<Boolean> requiredSortOverItems() {
        return switch (this) {
            case SINGLE_CLICK -> Optional.of(false); // hover on would hijack every click → unusable
            case CONTROL_DROP -> Optional.of(true);  // ctrl-drop only fires on occupied slots → needs hover
            default -> Optional.empty();
        };
    }

    /**
     * The hover setting actually in force for this method: its own override if it has one
     * ({@link #requiredSortOverItems()}), else the player's preference. The runtime gate
     * ({@code InventoryClickListener}) and {@link #getInstruction} both read this, so the
     * instruction text can never drift from what actually sorts.
     */
    public boolean effectiveSortOverItems(boolean playerPref) {
        return requiredSortOverItems().orElse(playerPref);
    }

    /** Returns true when this method forces a specific hover setting, hiding the allow-on-hover command. */
    public boolean governsHover() {
        return this == SINGLE_CLICK || this == CONTROL_DROP;
    }

    /**
     * @return true if triggering this method requires cancelling the originating click event
     *         (SWAP would swap the offhand item; CONTROL_DROP would drop the hovered item;
     *         the shift-click methods would shift-move it)
     */
    public boolean shouldCancelEvent() {
        return this == SWAP || this == CONTROL_DROP || this == DOUBLE_CLICK
                || this == SHIFT_LEFT_CLICK || this == SHIFT_RIGHT_CLICK;
    }

    /**
     * @return true if {@code event} matches the trigger for this click method
     */
    public boolean matchesSortTrigger(InventoryClickEvent event) {
        return switch (this) {
            // The empty-cursor requirement is SINGLE_CLICK-only: with sort-over-items on, a plain LEFT
            // click while holding an item must place that item normally rather than sort, or the
            // inventory becomes unusable. The other methods use dedicated keys/clicks that don't
            // conflict with placing a held item, so they may trigger regardless of cursor state.
            case SINGLE_CLICK -> event.getClick() == ClickType.LEFT && event.getCursor().isEmpty();
            case DOUBLE_CLICK -> event.getClick() == ClickType.DOUBLE_CLICK;
            case SWAP -> event.getClick() == ClickType.SWAP_OFFHAND;
            case CONTROL_DROP -> event.getClick() == ClickType.CONTROL_DROP;
            case SHIFT_LEFT_CLICK -> event.getClick() == ClickType.SHIFT_LEFT;
            case SHIFT_RIGHT_CLICK -> event.getClick() == ClickType.SHIFT_RIGHT;
            default -> false;
        };
    }

    /**
     * @param playerSortOverItems the player's own allow-on-hover preference; ignored for methods
     *                            that force a specific hover setting ({@link #requiredSortOverItems()})
     */
    public String getInstruction(Locale locale, boolean playerSortOverItems) {
        var lang = ClickSortedPlugin.getInstance().getConfigManager().lang(locale);
        boolean hover = effectiveSortOverItems(playerSortOverItems);
        return switch (this) {
            case SINGLE_CLICK -> lang.raw("instructionSingle");
            case CONTROL_DROP -> lang.raw("instructionControlDrop");
            case DOUBLE_CLICK -> lang.raw(hover ? "instructionDoubleHover" : "instructionDouble");
            case SWAP -> lang.raw(hover ? "instructionSwapHover" : "instructionSwap");
            case SHIFT_LEFT_CLICK -> lang.raw(hover ? "instructionShiftLeftClickHover" : "instructionShiftLeftClick");
            case SHIFT_RIGHT_CLICK -> lang.raw(hover ? "instructionShiftRightClickHover" : "instructionShiftRightClick");
        };
    }

    public static ClickMethod parse(String clickMethod, ClickMethod defaultMethod) {
        return EnumParse.parse(ClickMethod.class, clickMethod, defaultMethod);
    }

}
