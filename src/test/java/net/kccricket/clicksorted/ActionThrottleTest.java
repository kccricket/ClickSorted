package net.kccricket.clicksorted;

import net.kccricket.clicksorted.security.ActionThrottle;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ActionThrottle}: the per-player cooldown decision, independent per-player
 * clocks, the disabled (cooldown ≤ 0) case, and the bypass permission. A fake clock is injected via
 * the package-private {@code setClock} seam so the tests never sleep.
 */
class ActionThrottleTest extends AbstractClickSortedTest {

    /** Drive the throttle's clock manually so cooldown windows are deterministic. */
    private final AtomicLong now = new AtomicLong(1_000_000L);

    private ActionThrottle newThrottle() {
        ActionThrottle throttle = new ActionThrottle(plugin);
        setClock(throttle, now::get);
        return throttle;
    }

    /** Reflectively invoke the package-private {@code allow(Player, int)} core. */
    private boolean allow(ActionThrottle throttle, PlayerMock player, int cooldownMs) {
        try {
            Method m = ActionThrottle.class.getDeclaredMethod("allow",
                    org.bukkit.entity.Player.class, int.class);
            m.setAccessible(true);
            return (boolean) m.invoke(throttle, player, cooldownMs);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void setClock(ActionThrottle throttle, LongSupplier clock) {
        try {
            Method m = ActionThrottle.class.getDeclaredMethod("setClock", LongSupplier.class);
            m.setAccessible(true);
            m.invoke(throttle, clock);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void firstActionAllowed_immediateSecondDenied() {
        ActionThrottle throttle = newThrottle();
        PlayerMock player = server.addPlayer("Throttled"); // non-op: no bypass

        assertTrue(allow(throttle, player, 500), "first action should be allowed");
        assertFalse(allow(throttle, player, 500), "immediate second action should be denied");
    }

    @Test
    void allowedAgainAfterCooldownElapses() {
        ActionThrottle throttle = newThrottle();
        PlayerMock player = server.addPlayer("Throttled");

        assertTrue(allow(throttle, player, 500));
        now.addAndGet(499);
        assertFalse(allow(throttle, player, 500), "still within the window");
        now.addAndGet(1);
        assertTrue(allow(throttle, player, 500), "cooldown elapsed → allowed");
    }

    @Test
    void deniedActionDoesNotSlideTheWindow() {
        ActionThrottle throttle = newThrottle();
        PlayerMock player = server.addPlayer("Flooder");

        assertTrue(allow(throttle, player, 500));
        now.addAndGet(200);
        assertFalse(allow(throttle, player, 500)); // denied, must not reset the clock
        now.addAndGet(300);                         // 500ms since the *allowed* action
        assertTrue(allow(throttle, player, 500), "window measured from last allowed action");
    }

    @Test
    void playersTrackedIndependently() {
        ActionThrottle throttle = newThrottle();
        PlayerMock p1 = server.addPlayer("One");
        PlayerMock p2 = server.addPlayer("Two");

        assertTrue(allow(throttle, p1, 500));
        assertTrue(allow(throttle, p2, 500), "second player has its own clock");
        assertFalse(allow(throttle, p1, 500));
        assertFalse(allow(throttle, p2, 500));
    }

    @Test
    void zeroCooldownDisablesThrottle() {
        ActionThrottle throttle = newThrottle();
        PlayerMock player = server.addPlayer("Unthrottled");

        assertTrue(allow(throttle, player, 0));
        assertTrue(allow(throttle, player, 0), "cooldown 0 → always allowed");
    }

    @Test
    void bypassPermissionAlwaysAllows() {
        ActionThrottle throttle = newThrottle();
        PlayerMock op = addOpPlayer("Admin"); // op → has clicksorted.throttle.bypass

        assertTrue(allow(throttle, op, 500));
        assertTrue(allow(throttle, op, 500), "bypass permission is never throttled");
    }
}
