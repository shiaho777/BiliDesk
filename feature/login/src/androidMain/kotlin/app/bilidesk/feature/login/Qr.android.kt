package app.bilidesk.feature.login

import androidx.compose.ui.graphics.ImageBitmap
import app.bilidesk.core.designsystem.argbImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

internal actual fun qrBitmap(content: String): ImageBitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512)
    val pixels = IntArray(512 * 512)
    for (y in 0 until 512) {
        for (x in 0 until 512) {
            pixels[y * 512 + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    return argbImage(512, 512, pixels)
}
