package app.bilidesk.core.data

enum class HostKind { Android, Desktop }

expect fun hostKind(): HostKind

fun isDesktopHost(): Boolean = hostKind() == HostKind.Desktop

expect fun canControlBrightness(): Boolean

expect fun canFollowSystemAccent(): Boolean

/** 各系统自己的文件、窗口和分享。界面代码只调这些函数。 */
expect fun appStorageDir(): AppFile

expect fun imageCacheDirs(): List<AppFile>

expect fun openUrl(url: String)

/** 返回要给用户看的一句说明。空字符串表示系统自己已经弹出了分享界面。 */
expect fun shareText(title: String, text: String): String

expect fun saveImageBytes(bytes: ByteArray, displayName: String): String

expect fun exitApp()

expect fun windowBrightness(): Float

expect fun setWindowBrightness(value: Float)

expect fun setFullscreen(on: Boolean)

expect fun traySupported(): Boolean

expect fun hideAppWindow()
