package me.desht.clicksort;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

public enum ClickMethod {
    DOUBLE, SINGLE, SWAP, NONE;

    public static final ClickMethod DEFAULT = SWAP;

    public ClickMethod cycle() {
        return values()[(ordinal() + 1) % values().length];
    }

    public boolean shouldCancelEvent() {
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
            default -> false;
        };
    }

    public String getInstruction() {
        var lang = ClickSortPlugin.getInstance().getConfigManager().lang();
        return switch (this) {
            case SINGLE -> lang.getMessage("instructionSingle");
            case DOUBLE -> lang.getMessage("instructionDouble");
            case SWAP -> lang.getMessage("instructionSwap");
            default -> lang.getMessage("instructionDisabled");
        };
    }

    public static ClickMethod parse(String clickMethod) {
        ClickSortPlugin inst = ClickSortPlugin.getInstance();
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
