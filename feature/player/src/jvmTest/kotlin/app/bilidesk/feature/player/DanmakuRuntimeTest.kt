package app.bilidesk.feature.player

import kotlin.test.Test
import kotlin.test.assertEquals

class DanmakuRuntimeTest {
    @Test
    fun playingAdvancesWithWallClockWhenSampleStalls() {
        val runtime = DanmakuRuntime()
        val t0 = 1_000_000_000L
        assertEquals(10.0, runtime.presentedSec(10.0, true, t0, 1f), 0.001)
        val shown = runtime.presentedSec(10.0, true, t0 + 100_000_000L, 1f)
        assertEquals(10.1, shown, 0.005)
    }

    @Test
    fun seekSnapsWhenSampleJumps() {
        val runtime = DanmakuRuntime()
        val t0 = 1_000_000_000L
        runtime.presentedSec(10.0, true, t0, 1f)
        val shown = runtime.presentedSec(40.0, true, t0 + 16_000_000L, 1f)
        assertEquals(40.0, shown, 0.001)
    }

    @Test
    fun pauseHoldsLastPresentedEvenIfSampleCrawls() {
        val runtime = DanmakuRuntime()
        val t0 = 1_000_000_000L
        runtime.presentedSec(5.0, true, t0, 1f)
        val paused = runtime.presentedSec(5.05, false, t0 + 16_000_000L, 1f)
        val later = runtime.presentedSec(5.40, false, t0 + 200_000_000L, 1f)
        assertEquals(paused, later, 0.001)
    }

    @Test
    fun doubleSpeedAdvancesTwiceAsFast() {
        val runtime = DanmakuRuntime()
        val t0 = 1_000_000_000L
        runtime.presentedSec(1.0, true, t0, 2f)
        val shown = runtime.presentedSec(1.0, true, t0 + 100_000_000L, 2f)
        assertEquals(1.2, shown, 0.005)
    }

    @Test
    fun pinDuringPauseJumpsPresentedTime() {
        val runtime = DanmakuRuntime()
        runtime.presentedSec(3.0, true, 1_000_000_000L, 1f)
        runtime.presentedSec(3.0, false, 1_016_000_000L, 1f)
        runtime.pin(12.0)
        assertEquals(12.0, runtime.presentedSec(3.0, false, 1_032_000_000L, 1f), 0.001)
    }
}
