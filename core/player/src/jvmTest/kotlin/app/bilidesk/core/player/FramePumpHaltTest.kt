package app.bilidesk.core.player

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals

class FramePumpHaltTest {
    @Test
    fun haltDropsLaterFrames() {
        val pump = FramePump()
        pump.halt()
        val source = ByteBuffer.allocate(16)
        pump.publish(source, 2, 2, 8)
        assertEquals(0, pump.frameGeneration)
    }
}
