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

/**
 * Capability probe for Paper's Dialog API ({@code io.papermc.paper.dialog.Dialog}), which {@link
 * PreferencesDialog} depends on directly and which post-dates this plugin's {@code api-version}
 * floor. Mirrors the {@code Class.forName} Folia-detection idiom in {@code InventorySortService}:
 * evaluated once at class-load time and cached, rather than probed per call.
 *
 * <p>{@link #AVAILABLE} gates every entry point into the dialog subsystem — the {@code
 * /clicksorted menu} command and {@link PreferencesDialogService}'s construction/registration —
 * so a server too old for the Dialog API gets a clean "not offered" instead of a
 * {@code NoClassDefFoundError}/{@code NoSuchMethodError} the moment {@link PreferencesDialog}
 * actually touches the class.
 */
public final class DialogSupport {

    public static final boolean AVAILABLE = classExists("io.papermc.paper.dialog.Dialog");

    private DialogSupport() {
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
