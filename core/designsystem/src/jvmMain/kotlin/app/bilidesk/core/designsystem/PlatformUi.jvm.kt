package app.bilidesk.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Composable
actual fun platformDynamicAccent(dark: Boolean, enabled: Boolean): Color? = null

@Composable
actual fun PlatformThemeEffects(dark: Boolean) = Unit

actual fun decodeImageBytes(bytes: ByteArray): ImageBitmap? = runCatching {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

actual fun argbImage(width: Int, height: Int, pixels: IntArray): ImageBitmap {
    val info = ImageInfo.makeN32Premul(width, height)
    val bitmap = Bitmap()
    bitmap.allocPixels(info)
    val buffer = ByteBuffer.allocate(pixels.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (pixel in pixels) {
        val a = (pixel ushr 24) and 0xFF
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        buffer.put(b.toByte())
        buffer.put(g.toByte())
        buffer.put(r.toByte())
        buffer.put(a.toByte())
    }
    buffer.flip()
    bitmap.installPixels(buffer.array())
    return Image.makeFromBitmap(bitmap).toComposeImageBitmap()
}
