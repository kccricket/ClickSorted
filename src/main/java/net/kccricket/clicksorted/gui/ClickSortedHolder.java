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

import org.bukkit.inventory.InventoryHolder;

/**
 * Marker interface for every inventory ClickSorted itself opens (the lock GUI today, any future
 * GUIs). Bukkit exposes no way to ask which plugin created an inventory, so the sort path can't
 * distinguish our own GUIs from third-party plugin GUIs by holder package alone — and the
 * {@code ignore_plugin_inventory} option deliberately governs whether <em>other</em> plugins' GUIs
 * are sortable. This marker lets the sort listener exclude <em>our</em> GUIs unconditionally,
 * independent of that option, without enumerating each holder class.
 */
public interface ClickSortedHolder extends InventoryHolder {
}
