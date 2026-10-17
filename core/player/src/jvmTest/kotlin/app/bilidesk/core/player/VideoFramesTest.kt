package app.bilidesk.core.player

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class VideoFramesTest {
    @Test
    fun fitKeeps720() {
        assertEquals(1280 to 720, VideoFrames.fit(1280, 720, 1920))
    }

    @Test
    fun fitDownscales4kTo1080() {
        assertEquals(1920 to 1080, VideoFrames.fit(3840, 2160, 1920))
    }

    @Test
    fun copiesTightRows() {
        val src = ByteBuffer.wrap(
            byteArrayOf(
                1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 16,
            ),
        )
        val dest = ByteBuffer.allocate(16)
        VideoFrames.copyRv32(src, srcPitch = 8, width = 2, height = 2, dest = dest, destPitch = 8)
        dest.clear()
        val out = ByteArray(16)
        dest.get(out)
        assertContentEquals(
            byteArrayOf(
                1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 16,
            ),
            out,
        )
    }

    @Test
    fun skipsSourcePaddingAndKeepsDestPacked() {
        val src = ByteBuffer.wrap(
            byteArrayOf(
                1, 2, 3, 4, 9, 9, 9, 9,
                5, 6, 7, 8, 9, 9, 9, 9,
            ),
        )
        val dest = ByteBuffer.allocate(8)
        VideoFrames.copyRv32(src, srcPitch = 8, width = 1, height = 2, dest = dest, destPitch = 4)
        dest.clear()
        val out = ByteArray(8)
        dest.get(out)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), out)
    }
}
