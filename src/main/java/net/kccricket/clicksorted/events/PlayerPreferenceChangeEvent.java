package net.kccricket.clicksorted.events;

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

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired before a per-player ClickSorted preference is changed — click/sort method, start corner,
 * fill axis, the {@code enabled} flag, sort-over-items, bundle-packing toggles and stack limit,
 * a locked-slot toggle, or a bundle blacklist add/remove/clear.
 * <p>
 * {@code key} identifies which preference changed (e.g. {@code "click_mode"}, {@code "enabled"},
 * {@code "locked_slot:9"}, {@code "bundle_blacklist_material"}); {@code oldValue}/{@code newValue}
 * carry the preference's before/after value (types vary by key: {@code Boolean}, {@code Integer},
 * enum constants, {@code Material}, or {@code String}). Only fired when the new value actually
 * differs from the old one.
 * <p>
 * Cancelling prevents the preference from being applied or persisted; the caller that triggered
 * the change (command or GUI) still runs to completion and may report success regardless.
 */
public class PlayerPreferenceChangeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String key;
    private final Object oldValue;
    private final Object newValue;
    private boolean cancelled;

    public PlayerPreferenceChangeEvent(Player player, String key, Object oldValue, Object newValue) {
        this.player = player;
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public Player getPlayer() {
        return player;
    }

    /** Which preference changed, e.g. {@code "click_mode"} or {@code "locked_slot:9"}. */
    public String getKey() {
        return key;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public Object getNewValue() {
        return newValue;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
