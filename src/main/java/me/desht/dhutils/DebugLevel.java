package me.desht.dhutils;

import java.util.Locale;

/**
 * Ordered debug verbosity levels for {@link Log}.
 *
 * <ul>
 *   <li>{@link #OFF} — no debug output (default)</li>
 *   <li>{@link #DEBUG} — high-level flow messages</li>
 *   <li>{@link #TRACE} — per-item verbose messages</li>
 * </ul>
 */
public enum DebugLevel {
    OFF, DEBUG, TRACE;

    /**
     * Parse a level name (case-insensitive); warn and fall back to {@code def} on
     * an unrecognised value.
     */
    public static DebugLevel parse(String value, DebugLevel def) {
        if (value == null) return def;
        try {
            return DebugLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            Log.warning("invalid debug level '" + value + "' - defaulting to " + def);
            return def;
        }
    }
}
