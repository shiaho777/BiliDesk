package app.bilidesk.core.data

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

object DesktopHost {
    var exit: (() -> Unit)? = null
    var fullscreen: ((Boolean) -> Unit)? = null
    var showWindow: (() -> Unit)? = null
    var hideWindow: (() -> Unit)? = null
}

actual fun hostKind(): HostKind = HostKind.Desktop

actual fun canControlBrightness(): Boolean = false

actual fun canFollowSystemAccent(): Boolean = false

actual fun appStorageDir(): AppFile = AppFile(File(System.getProperty("user.home"), ".bilidesk").also { it.mkdirs() }.absolutePath)

actual fun imageCacheDirs(): List<AppFile> {
    val cache = AppFile(appStorageDir(), "cache")
    return listOf(AppFile(cache, "image_cache"), AppFile(cache, "coil"))
}

actual fun openUrl(url: String) {
    Desktop.getDesktop().browse(URI(url))
}

actual fun shareText(title: String, text: String): String {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
    val page = text.lineSequence().firstOrNull { it.startsWith("http") }
    if (page != null && Desktop.isDesktopSupported()) {
        runCatching { Desktop.getDesktop().browse(URI(page.trim())) }
    }
    return "链接已复制"
}

actual fun saveImageBytes(bytes: ByteArray, displayName: String): String {
    val dir = File(System.getProperty("user.home"), "Pictures/BiliDesk").also { it.mkdirs() }
    val safe = displayName.ifBlank { "avatar" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
    val file = File(dir, "$safe.jpg")
    file.writeBytes(bytes)
    return "已保存到 ${file.absolutePath}"
}

actual fun exitApp() {
    DesktopHost.exit?.invoke()
}

actual fun windowBrightness(): Float = 0.5f

actual fun setWindowBrightness(value: Float) = Unit

actual fun setFullscreen(on: Boolean) {
    DesktopHost.fullscreen?.invoke(on)
}

actual fun traySupported(): Boolean = java.awt.SystemTray.isSupported()

actual fun hideAppWindow() {
    DesktopHost.hideWindow?.invoke()
}
