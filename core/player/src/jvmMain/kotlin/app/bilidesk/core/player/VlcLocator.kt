package app.bilidesk.core.player

import java.io.File

internal object VlcLocator {
    const val MISSING_MESSAGE = "没有找到 VLC。请先安装 VLC 媒体播放器（https://www.videolan.org/vlc/），然后重新打开 BiliDesk。"

    fun libraryFileName(osName: String = System.getProperty("os.name").orEmpty()): String = when {
        osName.contains("win", ignoreCase = true) -> "libvlc.dll"
        osName.contains("mac", ignoreCase = true) -> "libvlc.dylib"
        else -> "libvlc.so"
    }

    fun candidateDirs(
        osName: String = System.getProperty("os.name").orEmpty(),
        userHome: String = System.getProperty("user.home").orEmpty(),
        programFiles: String? = System.getenv("ProgramFiles"),
        programFilesX86: String? = System.getenv("ProgramFiles(x86)"),
    ): List<String> {
        val home = userHome.trimEnd('/', '\\')
        return when {
            osName.contains("mac", ignoreCase = true) -> listOf(
                "/Applications/VLC.app/Contents/MacOS/lib",
                "/Applications/VLC.app/Contents/Frameworks",
                "$home/Applications/VLC.app/Contents/MacOS/lib",
                "$home/Applications/VLC.app/Contents/Frameworks",
                "/opt/homebrew/lib",
                "/usr/local/lib",
            )
            osName.contains("win", ignoreCase = true) -> listOfNotNull(
                programFiles?.let { "$it\\VideoLAN\\VLC" },
                programFilesX86?.let { "$it\\VideoLAN\\VLC" },
            )
            else -> listOf(
                "/usr/lib/x86_64-linux-gnu",
                "/usr/lib/aarch64-linux-gnu",
                "/usr/lib64",
                "/usr/local/lib64",
                "/usr/lib/i386-linux-gnu",
                "/usr/lib",
                "/usr/local/lib",
            )
        }
    }

    fun locate(
        osName: String = System.getProperty("os.name").orEmpty(),
        userHome: String = System.getProperty("user.home").orEmpty(),
        programFiles: String? = System.getenv("ProgramFiles"),
        programFilesX86: String? = System.getenv("ProgramFiles(x86)"),
        exists: (String) -> Boolean = { File(it).isFile },
    ): String? {
        val library = libraryFileName(osName)
        val separator = if (osName.contains("win", ignoreCase = true)) "\\" else "/"
        return candidateDirs(osName, userHome, programFiles, programFilesX86).firstOrNull { dir ->
            exists("$dir$separator$library")
        }
    }

    fun friendlyError(raw: String?): String {
        val blob = raw.orEmpty().lowercase()
        if (raw.isNullOrBlank() ||
            "unable to load library" in blob ||
            "libvlc" in blob ||
            "unsatisfiedlink" in blob
        ) {
            return MISSING_MESSAGE
        }
        return raw.lineSequence().firstOrNull()?.trim()?.take(120) ?: MISSING_MESSAGE
    }
}
