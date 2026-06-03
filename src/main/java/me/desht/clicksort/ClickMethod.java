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
        return parse(clickMethod, ClickSortPlugin.getInstance().getDefaultClickMethod());
    }

    public static ClickMethod parse(String clickMethod, ClickMethod defaultMethod) {
        try {
            ClickMethod method = ClickMethod.valueOf(clickMethod);
            if (!method.isAvailable()) {
                method = defaultMethod;
            }
            return method;
        } catch (IllegalArgumentException e) {
            return defaultMethod;
        }
    }

    /** Returns the method if it is a known name but unavailable on this server version, otherwise null. */
    public static ClickMethod unavailableFor(String name) {
        try {
            ClickMethod m = ClickMethod.valueOf(name);
            return m.isAvailable() ? null : m;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns true if the given name is not a recognised ClickMethod constant.
     * Use this to detect corrupt or legacy stored preference values.
     */
    public static boolean isUnknownName(String name) {
        try {
            ClickMethod.valueOf(name);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}
