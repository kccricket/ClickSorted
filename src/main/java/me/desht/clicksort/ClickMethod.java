package me.desht.clicksort;

import me.desht.dhutils.CompatUtil;

public enum ClickMethod {
    MIDDLE, DOUBLE, SINGLE, SWAP, NONE;

    public ClickMethod next() {
        int o = (ordinal() + 1) % values().length;
        return values()[o];
    }

    public ClickMethod nextAvailable() {
        ClickMethod method = this;
        do {
            method = method.next();
        } while (method != this && !method.isAvailable());
        return method;
    }

    public boolean isAvailable() {
        return switch (this) {
            case MIDDLE -> CompatUtil.isMiddleClickAllowed();
            case SWAP -> CompatUtil.isSwapKeyAvailable();
            default -> true;
        };
    }

    public boolean shouldCancelEvent() {
        return this == SWAP;
    }

    public String getInstruction() {
        return switch (this) {
            case SINGLE -> LanguageLoader.getMessage("instructionSingle");
            case DOUBLE -> LanguageLoader.getMessage("instructionDouble");
            case MIDDLE -> LanguageLoader.getMessage("instructionMiddle");
            case SWAP -> LanguageLoader.getMessage("instructionSwap");
            default -> LanguageLoader.getMessage("instructionDisabled");
        };
    }

    public static ClickMethod preferredDefault() {
        if (SWAP.isAvailable()) {
            return SWAP;
        } else if (MIDDLE.isAvailable()) {
            return MIDDLE;
        }
        return DOUBLE;
    }

    public static ClickMethod parse(String clickMethod) {
        ClickSortPlugin inst = ClickSortPlugin.getInstance();
        return parse(clickMethod, inst != null ? inst.getDefaultClickMethod()
                                               : ClickMethod.preferredDefault());
    }

    /**
     * Returns the matching {@code ClickMethod} if {@code name} is a known, available enum
     * constant; returns {@code null} if the name is unrecognised or the method is not
     * available on this server version.
     */
    public static ClickMethod resolveAvailable(String name) {
        try {
            ClickMethod m = ClickMethod.valueOf(name);
            return m.isAvailable() ? m : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static ClickMethod parse(String clickMethod, ClickMethod defaultMethod) {
        ClickMethod m = resolveAvailable(clickMethod);
        return m != null ? m : defaultMethod;
    }

}
