package id.kasir.pos

import org.junit.Assert.assertEquals
import org.junit.Test

class PinAttemptsTest {
    @Test fun locksFifthAttemptAndIncreasesDelayAfterExpiry() {
        var state = PinAttempts()
        repeat(4) {
            state = state.failed(wall = 1_000_000, elapsed = 10_000, currentBoot = 5)
            assertEquals(0L, state.remainingMillis(1_000_000, 10_000, 5))
        }
        state = state.failed(1_000_000, 10_000, 5)
        assertEquals(30_000L, state.remainingMillis(1_000_000, 10_000, 5))
        assertEquals(0L, state.remainingMillis(1_030_000, 40_000, 5))
        state = state.failed(1_030_000, 40_000, 5)
        assertEquals(60_000L, state.remainingMillis(1_030_000, 40_000, 5))
    }

    @Test fun persistedDeadlineUsesElapsedClockWithinBootAndWallClockAfterReboot() {
        val state = PinAttempts(failures = 4).failed(1_000_000, 10_000, 5)
        val restored = PinAttempts(state.failures, state.untilWall, state.untilElapsed, state.boot)
        assertEquals(20_000L, restored.remainingMillis(2_000_000, 20_000, 5))
        assertEquals(10_000L, restored.remainingMillis(1_020_000, 1_000, 6))
        assertEquals(0L, restored.remainingMillis(1_030_000, 1_000, 6))
    }

    @Test fun capsRepeatedFailuresAtFifteenMinutes() {
        var state = PinAttempts()
        repeat(15) { state = state.failed(1_000_000, 10_000, 5) }
        assertEquals(900_000L, state.remainingMillis(1_000_000, 10_000, 5))
    }
}
