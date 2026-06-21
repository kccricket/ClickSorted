package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.ClickMethod;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link net.kccricket.clicksorted.migration.PreferenceRepair}: invalid stored enum values are
 * reset to the server default (and the player told), and the hover preference is coupled to the click
 * method at join.
 */
class PreferenceRepairTest extends AbstractClickSortedTest {

    private void join(PlayerMock player) {
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));
    }

    @Test
    void invalidStoredClickMethodResetToDefaultWithMessage() {
        PlayerMock player = server.addPlayer("Alice");
        NamespacedKey key = new NamespacedKey(plugin, "click");
        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, "BOGUS_VALUE");
        drainMessages(player);

        join(player);

        assertNull(player.getPersistentDataContainer().get(key, PersistentDataType.STRING),
                "Invalid stored click value must be removed so the read falls back to the default");
        assertEquals(plugin.getConfigManager().main().getDefaultClickMethod(),
                plugin.getSortingPrefs().getClickMethod(player));
        assertMessageSent(drainMessageList(player), "MSG.prefResetInvalid", "BOGUS_VALUE");
    }

    @Test
    void controlDropPlayerHasHoverForcedOnAtJoin() {
        PlayerMock player = server.addPlayer("Alice");
        plugin.getSortingPrefs().setClickMethod(player, ClickMethod.CONTROL_DROP);
        plugin.getSortingPrefs().setSortOverItems(player, false);
        drainMessages(player);

        join(player);

        assertTrue(plugin.getSortingPrefs().getSortOverItems(player),
                "CONTROL_DROP must have hover forced on at join");
        assertMessageSent(drainMessageList(player), "MSG.hoverForcedByClickMethod", "ENABLED");
    }

    @Test
    void cleanConfigProducesNoMessages() {
        PlayerMock player = server.addPlayer("Alice");
        plugin.getSortingPrefs().setClickMethod(player, ClickMethod.SWAP); // leaves hover to the player
        drainMessages(player);

        join(player);

        assertFalse(anyMessageContains(player, "MSG.prefResetInvalid", "MSG.hoverForcedByClickMethod"),
                "A clean, consistent config must produce no repair messages");
    }
}
