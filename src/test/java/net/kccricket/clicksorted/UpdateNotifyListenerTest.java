package net.kccricket.clicksorted;

import net.kccricket.clicksorted.security.Permissions;
import net.kccricket.clicksorted.update.UpdateChecker;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link net.kccricket.clicksorted.update.UpdateNotifyListener}: an admin holding
 * {@link Permissions#PERM_NOTIFY_UPDATE} is told in chat, on join, when the cached
 * {@link UpdateChecker} result indicates a newer release — but only when both the permission and
 * the {@code notify_admins_on_update} config toggle allow it. The real Modrinth check never runs in
 * these tests (nothing awaits the async scheduler), so the cache is seeded directly via reflection
 * to simulate "a check already completed and found an update."
 */
class UpdateNotifyListenerTest extends AbstractClickSortedTest {

    private void join(PlayerMock player) {
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));
    }

    /** Seeds UpdateChecker's cached result directly — no network call, no scheduler wait needed. */
    private void seedUpdateAvailable(boolean available, String version) {
        try {
            UpdateChecker checker = plugin.getUpdateChecker();
            Field availableField = UpdateChecker.class.getDeclaredField("updateAvailable");
            availableField.setAccessible(true);
            availableField.set(checker, available);
            Field versionField = UpdateChecker.class.getDeclaredField("latestVersion");
            versionField.setAccessible(true);
            versionField.set(checker, version);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void adminWithPermissionNotifiedWhenUpdateAvailable() {
        seedUpdateAvailable(true, "99.0.0");
        PlayerMock player = addOpPlayer("Alice");
        drainMessages(player);

        join(player);

        assertMessageSent(drainMessageList(player), "MSG.updateAvailableNotify", "99.0.0");
    }

    @Test
    void playerWithoutPermissionNotNotified() {
        seedUpdateAvailable(true, "99.0.0");
        PlayerMock player = server.addPlayer("Bob"); // not op, no explicit grant
        drainMessages(player);

        join(player);

        assertFalse(anyMessageContains(player, "MSG.updateAvailableNotify"),
                "A player without the permission must not be notified");
    }

    @Test
    void noNotificationWhenNoUpdateAvailable() {
        seedUpdateAvailable(false, null);
        PlayerMock player = addOpPlayer("Alice");
        drainMessages(player);

        join(player);

        assertFalse(anyMessageContains(player, "MSG.updateAvailableNotify"),
                "Nothing to notify about when the cache says no update is available");
    }

    @Test
    void configToggleOffSuppressesNotificationEvenWithPermission() {
        seedUpdateAvailable(true, "99.0.0");
        plugin.getConfig().set("notify_admins_on_update", false);
        PlayerMock player = addOpPlayer("Alice");
        drainMessages(player);

        join(player);

        assertFalse(anyMessageContains(player, "MSG.updateAvailableNotify"),
                "notify_admins_on_update: false must suppress the notification regardless of permission");
    }
}
