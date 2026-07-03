package net.kccricket.clicksorted.update;

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

import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.clicksorted.security.Permissions;
import net.kccricket.clicksorted.text.MessageUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Notifies admins in chat, on join, when {@link UpdateChecker}'s cached result indicates a newer
 * release is available. Gated by the {@code notify_admins_on_update} config toggle and the
 * {@link Permissions#PERM_NOTIFY_UPDATE} permission, so it costs nothing beyond a couple of field
 * reads for players who don't hold the permission or when no update is pending — the underlying
 * Modrinth check itself runs on its own schedule, never per-join.
 */
public class UpdateNotifyListener implements Listener {

    private final ClickSortedPlugin plugin;

    public UpdateNotifyListener(ClickSortedPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfigManager().main().getNotifyAdminsOnUpdate()) {
            return;
        }
        UpdateChecker checker = plugin.getUpdateChecker();
        if (checker == null || !checker.isUpdateAvailable()) {
            return;
        }
        String latest = checker.getLatestVersion();
        if (latest == null) {
            return;
        }
        var player = event.getPlayer();
        if (!Permissions.isAllowedTo(player, Permissions.PERM_NOTIFY_UPDATE)) {
            return;
        }
        var lang = plugin.getConfigManager().lang();
        MessageUtil.statusMessage(player, lang.getColoredMessage("updateAvailableNotify",
                Placeholder.unparsed("version", latest),
                Placeholder.unparsed("current", plugin.getPluginMeta().getVersion())));
    }
}
