package app.bilidesk.core.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackClockTest {
    @Test
    fun remembersTimeLengthPlayingAndSpeed() {
        val clock = PlaybackClock()
        clock.acceptTime(1_250)
        clock.acceptLength(90_000)
        clock.acceptPlaying(true)
        clock.acceptSpeed(1.5f)
        assertEquals(1_250L, clock.positionMs)
        assertEquals(90_000L, clock.durationMs)
        assertTrue(clock.playing)
        assertEquals(1.5f, clock.speed)
    }

    @Test
    fun seekOverwritesPositionImmediately() {
        val clock = PlaybackClock()
        clock.acceptTime(8_000)
        clock.acceptSeek(2_000)
        assertEquals(2_000L, clock.positionMs)
    }

    @Test
    fun ignoresNegativeTimeAndZeroLength() {
        val clock = PlaybackClock()
        clock.acceptTime(500)
        clock.acceptLength(1_000)
        clock.acceptTime(-1)
        clock.acceptLength(0)
        assertEquals(500L, clock.positionMs)
        assertEquals(1_000L, clock.durationMs)
    }

    @Test
    fun pauseClearsPlayingFlag() {
        val clock = PlaybackClock()
        clock.acceptPlaying(true)
        clock.acceptPlaying(false)
        assertFalse(clock.playing)
    }
}
