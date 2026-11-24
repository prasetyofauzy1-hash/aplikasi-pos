package id.kasir.pos

internal data class PinAttempts(
    val failures: Int = 0,
    val untilWall: Long = 0,
    val untilElapsed: Long = 0,
    val boot: Int = -1
) {
    fun remainingMillis(wall: Long, elapsed: Long, currentBoot: Int): Long =
        if (boot >= 0 && boot == currentBoot) {
            (untilElapsed - elapsed).coerceAtLeast(0)
        } else {
            (untilWall - wall).coerceIn(0, MAX_DELAY)
        }

    fun failed(wall: Long, elapsed: Long, currentBoot: Int): PinAttempts {
        val next = (failures + 1).coerceAtMost(10)
        val delay = if (next < 5) 0 else (30_000L shl (next - 5)).coerceAtMost(MAX_DELAY)
        return PinAttempts(next, wall + delay, elapsed + delay, currentBoot)
    }

    private companion object {
        const val MAX_DELAY = 15 * 60_000L
    }
}
