package net.kccricket.clicksorted.model;

import net.kccricket.clicksorted.ClickSortedPlugin;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

public enum ClickMethod {
    DOUBLE_CLICK, SINGLE_CLICK, SWAP, CONTROL_DROP, SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK, NONE;

    public static final ClickMethod DEFAULT = SWAP;

    public ClickMethod cycle() {
        return values()[(ordinal() + 1) % values().length];
    }

    /**
     * @return true if triggering this method requires cancelling the originating click event
     *         (SWAP would swap the offhand item; CONTROL_DROP would drop the hovered item;
     *         the shift-click methods would shift-move it)
     */
    public boolean shouldCancelEvent() {
        return this == SWAP || this == CONTROL_DROP
                || this == SHIFT_LEFT_CLICK || this == SHIFT_RIGHT_CLICK;
    }

    /**
     * @return true if this method needs the post-sort offhand reset (SWAP only); the offhand item is
     *         momentarily consumed by the swap key and must be restored on the next tick.
     */
    public boolean needsOffhandReset() {
        return this == SWAP;
    }

    /**
     * @return true if {@code event} matches the trigger for this click method
     */
    public boolean matchesSortTrigger(InventoryClickEvent event) {
        if (!event.getCursor().isEmpty()) {
            // Prevent sorting when the player is holding an item with the cursor, to avoid accidental sorts and potential dupes.
            return false; 
        }
        return switch (this) {
            case SINGLE_CLICK -> event.getClick() == ClickType.LEFT;
            case DOUBLE_CLICK -> event.getClick() == ClickType.DOUBLE_CLICK;
            case SWAP -> event.getClick() == ClickType.SWAP_OFFHAND;
            case CONTROL_DROP -> event.getClick() == ClickType.CONTROL_DROP;
            case SHIFT_LEFT_CLICK -> event.getClick() == ClickType.SHIFT_LEFT;
            case SHIFT_RIGHT_CLICK -> event.getClick() == ClickType.SHIFT_RIGHT;
            default -> false;
        };
    }

    public String getInstruction() {
        var lang = ClickSortedPlugin.getInstance().getConfigManager().lang();
        return switch (this) {
            case SINGLE_CLICK -> lang.getMessage("instructionSingle");
            case DOUBLE_CLICK -> lang.getMessage("instructionDouble");
            case SWAP -> lang.getMessage("instructionSwap");
            case CONTROL_DROP -> lang.getMessage("instructionControlDrop");
            case SHIFT_LEFT_CLICK -> lang.getMessage("instructionShiftLeftClick");
            case SHIFT_RIGHT_CLICK -> lang.getMessage("instructionShiftRightClick");
            default -> lang.getMessage("instructionDisabled");
        };
    }

    public static ClickMethod parse(String clickMethod) {
        ClickSortedPlugin inst = ClickSortedPlugin.getInstance();
        return parse(clickMethod, inst != null ? inst.getConfigManager().main().getDefaultClickMethod() : DEFAULT);
    }

    public static ClickMethod parse(String clickMethod, ClickMethod defaultMethod) {
        if (clickMethod == null) {
            return defaultMethod;
        }
        try {
            return ClickMethod.valueOf(clickMethod);
        } catch (IllegalArgumentException e) {
            return defaultMethod;
        }
    }

}
