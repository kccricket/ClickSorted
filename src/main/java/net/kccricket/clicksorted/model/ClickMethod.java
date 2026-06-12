package net.kccricket.clicksorted.model;

import net.kccricket.clicksorted.ClickSortedPlugin;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

public enum ClickMethod {
    DOUBLE, SINGLE, SWAP, DROP, NONE;

    public static final ClickMethod DEFAULT = SWAP;

    public ClickMethod cycle() {
        return values()[(ordinal() + 1) % values().length];
    }

    /**
     * @return true if triggering this method requires cancelling the originating click event
     *         (SWAP would swap the offhand item; DROP would drop the hovered item)
     */
    public boolean shouldCancelEvent() {
        return this == SWAP || this == DROP;
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
        return switch (this) {
            case SINGLE -> event.getClick() == ClickType.LEFT
                    && event.getCurrentItem().getType() == Material.AIR
                    && (event.getCursor() == null || event.getCursor().getType() == Material.AIR);
            case DOUBLE -> event.getClick() == ClickType.DOUBLE_CLICK;
            case SWAP -> event.getClick() == ClickType.SWAP_OFFHAND;
            case DROP -> event.getClick() == ClickType.CONTROL_DROP;
            default -> false;
        };
    }

    public String getInstruction() {
        var lang = ClickSortedPlugin.getInstance().getConfigManager().lang();
        return switch (this) {
            case SINGLE -> lang.getMessage("instructionSingle");
            case DOUBLE -> lang.getMessage("instructionDouble");
            case SWAP -> lang.getMessage("instructionSwap");
            case DROP -> lang.getMessage("instructionDrop");
            default -> lang.getMessage("instructionDisabled");
        };
    }

    public static ClickMethod parse(String clickMethod) {
        ClickSortedPlugin inst = ClickSortedPlugin.getInstance();
        return parse(clickMethod, inst != null ? inst.getConfigManager().main().getDefaultClickMethod() : DEFAULT);
    }

    public static ClickMethod parse(String clickMethod, ClickMethod defaultMethod) {
        try {
            return ClickMethod.valueOf(clickMethod);
        } catch (IllegalArgumentException e) {
            return defaultMethod;
        }
    }

}
