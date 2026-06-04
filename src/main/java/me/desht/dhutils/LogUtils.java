package me.desht.dhutils;

import org.bukkit.plugin.Plugin;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class LogUtils {

    private static Logger logger;
    private static DebugLevel debugLevel = DebugLevel.OFF;

    private LogUtils() {}

    public static void init(Plugin plugin) {
        logger = plugin.getLogger();
    }

    // -------------------------------------------------------------------------
    // Standard log levels
    // -------------------------------------------------------------------------

    public static void log(Level level, String message) {
        logger.log(level, message);
    }

    public static void info(String message) {
        logger.info(message);
    }

    public static void warning(String message) {
        logger.warning(message);
    }

    public static void warning(String message, Throwable t) {
        logger.log(Level.WARNING, message, t);
    }

    public static void severe(String message) {
        logger.severe(message);
    }

    public static void severe(String message, Throwable t) {
        logger.log(Level.SEVERE, message, t);
    }

    // -------------------------------------------------------------------------
    // Debug logging (gated by debugLevel; routed through the plugin logger)
    // -------------------------------------------------------------------------

    public static void setDebugLevel(DebugLevel level) {
        debugLevel = level;
    }

    public static DebugLevel getDebugLevel() {
        return debugLevel;
    }

    /** Emit {@code message} at {@link DebugLevel#DEBUG} verbosity. */
    public static void debug(String message) {
        logAt(DebugLevel.DEBUG, message);
    }

    /** Emit {@code message} at {@link DebugLevel#TRACE} verbosity (per-item verbose). */
    public static void trace(String message) {
        logAt(DebugLevel.TRACE, message);
    }

    private static void logAt(DebugLevel level, String message) {
        if (debugLevel != DebugLevel.OFF && debugLevel.ordinal() >= level.ordinal()) {
            logger.info("[debug] " + message);
        }
    }
}
