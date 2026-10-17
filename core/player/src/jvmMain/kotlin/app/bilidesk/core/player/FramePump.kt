package app.bilidesk.core.player

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.BufferUtil
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/**
 * VLC 回调线程把 RV32 拷进可复用 Bitmap，并在同一线程做成 Skia Image。
 * UI 线程只拿已经做好的 Image 去画，不再 makeFromBitmap。
 * 正在画的那块不写、不关。
 */
internal class FramePump {
    private val bitmaps = arrayOfNulls<Bitmap>(2)
    private val images = arrayOfNulls<Image>(2)
    private val published = AtomicInteger(-1)
    private val displayed = AtomicInteger(-1)
    private val generation = AtomicInteger(0)
    @Volatile private var live = true
    private var width = 0
    private var height = 0

    val frameGeneration: Int get() = generation.get()

    fun sizeFor(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> =
        VideoFrames.fit(sourceWidth, sourceHeight)

    fun publish(source: ByteBuffer, frameWidth: Int, frameHeight: Int, pitch: Int) {
        if (!live || frameWidth <= 0 || frameHeight <= 0) return
        ensure(frameWidth, frameHeight)
        val slot = if (displayed.get() == 0) 1 else 0
        val bitmap = bitmaps[slot] ?: return
        val pixmap = bitmap.peekPixels() ?: return
        val dest = BufferUtil.getByteBufferFromPointer(pixmap.addr, pixmap.computeByteSize())
        VideoFrames.copyRv32(source, pitch, frameWidth, frameHeight, dest, pixmap.rowBytes)
        bitmap.notifyPixelsChanged()
        val snapshot = Image.makeFromBitmap(bitmap)
        images[slot]?.close()
        images[slot] = snapshot
        published.set(slot)
        generation.incrementAndGet()
    }

    fun peek(): Image? {
        val slot = published.get()
        if (slot < 0) return null
        displayed.set(slot)
        return images[slot]
    }

    fun halt() {
        live = false
    }

    fun release() {
        halt()
        published.set(-1)
        displayed.set(-1)
        images.forEach { runCatching { it?.close() } }
        images.fill(null)
        bitmaps.forEach { runCatching { it?.close() } }
        bitmaps.fill(null)
    }

    private fun ensure(w: Int, h: Int) {
        val first = bitmaps[0]
        val second = bitmaps[1]
        if (width == w && height == h && first != null && !first.isNull && second != null && !second.isNull) return
        width = w
        height = h
        published.set(-1)
        displayed.set(-1)
        images.forEach { runCatching { it?.close() } }
        images.fill(null)
        repeat(2) { index ->
            val bitmap = bitmaps[index] ?: Bitmap().also { bitmaps[index] = it }
            bitmap.reset()
            bitmap.allocN32Pixels(w, h, true)
        }
    }
}
