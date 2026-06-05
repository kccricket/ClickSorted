package me.desht.clicksort;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockbukkit.mockbukkit.matcher.command.MessageTargetReceivedAnyMessageMatcher.hasNotReceivedAny;

class CooldownMessengerTest {

    private ServerMock server;
    private CooldownMessenger messenger;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        messenger = new CooldownMessenger();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private String nextText(PlayerMock player) {
        String msg = player.nextMessage();
        assertNotNull(msg, "expected a message but none was sent");
        // Strip legacy color codes (§X) produced when MockBukkit serializes the
        // Adventure Component returned by MessageUtil.statusMessage (colorIfAbsent AQUA).
        return msg.replaceAll("§.", "");
    }

    @Test
    void firstCall_sendsMessage() {
        PlayerMock player = server.addPlayer("Player1");
        messenger.message(player, "test", 60, "hello");
        assertEquals("hello", nextText(player));
        assertThat(player, hasNotReceivedAny());
    }

    @Test
    void withinCooldown_doesNotSendAgain() {
        PlayerMock player = server.addPlayer("Player1");
        messenger.message(player, "test", 60, "hello");
        messenger.message(player, "test", 60, "hello");
        assertEquals("hello", nextText(player));
        assertThat(player, hasNotReceivedAny());
    }

    @Test
    void zeroCooldown_sendsEveryTime() throws InterruptedException {
        // secs=0 means cooldown is 0ms; elapsed > 0 is satisfied after at least 1ms.
        // NOTE: two calls within the same millisecond would suppress the second —
        // "0-second cooldown" is not truly "no cooldown".
        PlayerMock player = server.addPlayer("Player1");
        messenger.message(player, "test", 0, "first");
        Thread.sleep(2);
        messenger.message(player, "test", 0, "second");
        assertEquals("first", nextText(player));
        assertEquals("second", nextText(player));
        assertThat(player, hasNotReceivedAny());
    }

    @Test
    void differentKeys_trackedIndependently() {
        PlayerMock player = server.addPlayer("Player1");
        messenger.message(player, "key1", 60, "msg1");
        messenger.message(player, "key2", 60, "msg2");
        assertEquals("msg1", nextText(player));
        assertEquals("msg2", nextText(player));
        assertThat(player, hasNotReceivedAny());
    }

    @Test
    void differentSenders_trackedIndependently() {
        PlayerMock p1 = server.addPlayer("Player1");
        PlayerMock p2 = server.addPlayer("Player2");
        messenger.message(p1, "test", 60, "msg");
        messenger.message(p2, "test", 60, "msg");
        assertEquals("msg", nextText(p1));
        assertEquals("msg", nextText(p2));
        assertThat(p1, hasNotReceivedAny());
        assertThat(p2, hasNotReceivedAny());
    }
}
