package me.desht.clicksort;

public enum ClickMethod {
    DOUBLE, SINGLE, SWAP, NONE;

    public static final ClickMethod DEFAULT = SWAP;

    public ClickMethod cycle() {
        return values()[(ordinal() + 1) % values().length];
    }

    public boolean shouldCancelEvent() {
        return this == SWAP;
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
        return parse(clickMethod, inst != null ? inst.getDefaultClickMethod() : DEFAULT);
    }

    public static ClickMethod parse(String clickMethod, ClickMethod defaultMethod) {
        try {
            return ClickMethod.valueOf(clickMethod);
        } catch (IllegalArgumentException e) {
            return defaultMethod;
        }
    }

}
