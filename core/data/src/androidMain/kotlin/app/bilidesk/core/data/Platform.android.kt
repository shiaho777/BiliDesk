package app.bilidesk.core.data

import android.app.Activity
import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import java.io.File

object AndroidHost {
    lateinit var context: Application
    var activity: Activity? = null

    fun install(app: Application) {
        context = app
    }
}

actual fun hostKind(): HostKind = HostKind.Android

actual fun canControlBrightness(): Boolean = true

actual fun canFollowSystemAccent(): Boolean = Build.VERSION.SDK_INT >= 31

actual fun appStorageDir(): AppFile = AppFile(File(AndroidHost.context.filesDir, "bilidesk").also { it.mkdirs() }.absolutePath)

actual fun imageCacheDirs(): List<AppFile> {
    val cache = AndroidHost.context.cacheDir
    return listOf(
        AppFile(File(cache, "image_cache").absolutePath),
        AppFile(File(cache, "coil").absolutePath),
        AppFile(File(cache, "coil3_disk_cache").absolutePath),
    )
}

actual fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val host = AndroidHost.activity
    if (host != null) host.startActivity(intent) else AndroidHost.context.startActivity(intent)
}

actual fun shareText(title: String, text: String): String {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val host = AndroidHost.activity
    if (host != null) host.startActivity(chooser) else AndroidHost.context.startActivity(chooser)
    return ""
}

actual fun saveImageBytes(bytes: ByteArray, displayName: String): String {
    val resolver = AndroidHost.context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BiliDesk")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return "没有写入相册的权限"
    resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return "没有写入相册的权限"
    if (Build.VERSION.SDK_INT >= 29) {
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
    return "头像已保存到相册"
}

actual fun exitApp() {
    AndroidHost.activity?.finish()
}

actual fun windowBrightness(): Float {
    val activity = AndroidHost.activity ?: return 0.5f
    val current = activity.window.attributes.screenBrightness
    if (current >= 0f) return current
    val system = runCatching {
        Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrNull()
    return system?.coerceIn(0.02f, 1f) ?: 0.5f
}

actual fun setWindowBrightness(value: Float) {
    val window = AndroidHost.activity?.window ?: return
    val attrs = window.attributes
    attrs.screenBrightness = value
    window.attributes = attrs
}

actual fun setFullscreen(on: Boolean) {
    AndroidHost.activity?.requestedOrientation = if (on) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

actual fun traySupported(): Boolean = false

actual fun hideAppWindow() = Unit
