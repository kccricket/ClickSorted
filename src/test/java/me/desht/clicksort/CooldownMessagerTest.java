package me.desht.clicksort;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

class CooldownMessagerTest {

    private ServerMock server;
    private CooldownMessager messager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        messager = new CooldownMessager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private String nextText(PlayerMock player) {
        String msg = player.nextMessage();
        assertNotNull(msg, "expected a message but none was sent");
        // Strip legacy color codes (§X) added by MiscUtil.statusMessage
        return msg.replaceAll("§.", "");
    }

    @Test
    void firstCall_sendsMessage() {
        PlayerMock player = server.addPlayer("Player1");
        messager.message(player, "test", 60, "hello");
        assertEquals("hello", nextText(player));
        player.assertNoMoreSaid();
    }

    @Test
    void withinCooldown_doesNotSendAgain() {
        PlayerMock player = server.addPlayer("Player1");
        messager.message(player, "test", 60, "hello");
        messager.message(player, "test", 60, "hello");
        assertEquals("hello", nextText(player));
        player.assertNoMoreSaid();
    }

    @Test
    void zeroCooldown_sendsEveryTime() throws InterruptedException {
        // secs=0 means cooldown is 0ms; elapsed > 0 is satisfied after at least 1ms.
        // NOTE: two calls within the same millisecond would suppress the second —
        // "0-second cooldown" is not truly "no cooldown".
        PlayerMock player = server.addPlayer("Player1");
        messager.message(player, "test", 0, "first");
        Thread.sleep(2);
        messager.message(player, "test", 0, "second");
        assertEquals("first", nextText(player));
        assertEquals("second", nextText(player));
        player.assertNoMoreSaid();
    }

    @Test
    void differentKeys_trackedIndependently() {
        PlayerMock player = server.addPlayer("Player1");
        messager.message(player, "key1", 60, "msg1");
        messager.message(player, "key2", 60, "msg2");
        assertEquals("msg1", nextText(player));
        assertEquals("msg2", nextText(player));
        player.assertNoMoreSaid();
    }

    @Test
    void differentSenders_trackedIndependently() {
        PlayerMock p1 = server.addPlayer("Player1");
        PlayerMock p2 = server.addPlayer("Player2");
        messager.message(p1, "test", 60, "msg");
        messager.message(p2, "test", 60, "msg");
        assertEquals("msg", nextText(p1));
        assertEquals("msg", nextText(p2));
        p1.assertNoMoreSaid();
        p2.assertNoMoreSaid();
    }
}
