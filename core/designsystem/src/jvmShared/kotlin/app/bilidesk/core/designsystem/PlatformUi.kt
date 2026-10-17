package app.bilidesk.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

@Composable
expect fun platformDynamicAccent(dark: Boolean, enabled: Boolean): Color?

@Composable
expect fun PlatformThemeEffects(dark: Boolean)

expect fun decodeImageBytes(bytes: ByteArray): ImageBitmap?

expect fun argbImage(width: Int, height: Int, pixels: IntArray): ImageBitmap
