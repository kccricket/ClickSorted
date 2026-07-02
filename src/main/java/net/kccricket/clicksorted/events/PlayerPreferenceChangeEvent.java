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

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.Nullable;

/**
 * Fired before a per-player ClickSorted preference is changed — click/sort method, start corner,
 * fill axis, the {@code enabled} flag, sort-over-items, bundle-packing toggles and stack limit,
 * a locked-slot toggle, or a bundle blacklist add/remove/clear.
 * <p>
 * {@link #getChange()} carries which preference changed and its before/after values. Use
 * {@link #getChange(Preference)} for a typed accessor scoped to one preference, or pattern-match
 * on {@link Change} subtypes (e.g. {@link LockedSlotChange}) for shape-specific data. Only fired
 * when the new value actually differs from the old one.
 * <p>
 * Cancelling prevents the preference from being applied or persisted; the caller that triggered
 * the change (command or GUI) reports the block to the player instead of a success message. A
 * cancelling listener may call {@link #setCancelReason(Component)} to explain why — the caller
 * shows that reason verbatim, falling back to a generic message when none is set. Setting a
 * reason has no effect unless the event is also cancelled.
 */
public class PlayerPreferenceChangeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Change<?> change;
    private boolean cancelled;
    private @Nullable Component cancelReason;

    public PlayerPreferenceChangeEvent(Player player, Change<?> change) {
        this.player = player;
        this.change = change;
    }

    public Player getPlayer() {
        return player;
    }

    public Change<?> getChange() {
        return change;
    }

    /**
     * The reason a cancelling listener gave for blocking this change, or {@code null} if none was
     * set (or the event wasn't cancelled). Callers reporting a cancellation to the player should
     * fall back to a generic message when this is {@code null}.
     */
    public @Nullable Component getCancelReason() {
        return cancelReason;
    }

    /**
     * Lets a listener explain why it cancelled this change. Only meaningful alongside
     * {@link #setCancelled(boolean) setCancelled(true)} — the reason is not surfaced otherwise.
     */
    public void setCancelReason(@Nullable Component reason) {
        this.cancelReason = reason;
    }

    /**
     * Typed filter-accessor: returns the change narrowed to {@code T} when this event is for
     * {@code pref}, or {@code null} otherwise. Safe because {@link Preference} constants are the
     * only instances of that class, so reference equality proves the type parameter.
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable Change<T> getChange(Preference<T> pref) {
        return change.preference() == pref ? (Change<T>) change : null;
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

    /** Base payload: which preference changed and its before/after values. */
    public abstract static sealed class Change<T> permits ValueChange, LockedSlotChange {
        private final Preference<T> preference;
        private final T oldValue;
        private final T newValue;

        private Change(Preference<T> preference, T oldValue, T newValue) {
            this.preference = preference;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public Preference<T> preference() {
            return preference;
        }

        /** Nullable: a blacklist add has no old value, a remove has no new value. */
        public @Nullable T oldValue() {
            return oldValue;
        }

        /** Nullable: a blacklist add has no old value, a remove has no new value. */
        public @Nullable T newValue() {
            return newValue;
        }
    }

    /** The common case: a preference's value changed with no additional data. */
    public static final class ValueChange<T> extends Change<T> {
        public ValueChange(Preference<T> preference, T oldValue, T newValue) {
            super(preference, oldValue, newValue);
        }
    }

    /** A locked-slot toggle; carries the slot index in addition to the before/after locked state. */
    public static final class LockedSlotChange extends Change<Boolean> {
        private final int slot;

        public LockedSlotChange(int slot, boolean oldValue, boolean newValue) {
            super(Preference.LOCKED_SLOT, oldValue, newValue);
            this.slot = slot;
        }

        public int slot() {
            return slot;
        }
    }
}
