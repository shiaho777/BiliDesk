package app.bilidesk.core.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VlcLocatorTest {
    @Test
    fun macPrefersAppLibDirectory() {
        val present = setOf("/Applications/VLC.app/Contents/MacOS/lib/libvlc.dylib")
        val found = VlcLocator.locate(
            osName = "Mac OS X",
            userHome = "/Users/demo",
            exists = { it in present },
        )
        assertEquals("/Applications/VLC.app/Contents/MacOS/lib", found)
    }

    @Test
    fun macFallsBackToFrameworks() {
        val present = setOf("/Applications/VLC.app/Contents/Frameworks/libvlc.dylib")
        val found = VlcLocator.locate(
            osName = "Mac OS X",
            userHome = "/Users/demo",
            exists = { it in present },
        )
        assertEquals("/Applications/VLC.app/Contents/Frameworks", found)
    }

    @Test
    fun macFindsUserApplicationsCopy() {
        val present = setOf("/Users/demo/Applications/VLC.app/Contents/MacOS/lib/libvlc.dylib")
        val found = VlcLocator.locate(
            osName = "Mac OS X",
            userHome = "/Users/demo",
            exists = { it in present },
        )
        assertEquals("/Users/demo/Applications/VLC.app/Contents/MacOS/lib", found)
    }

    @Test
    fun windowsFindsProgramFilesInstall() {
        val present = setOf("""C:\Program Files\VideoLAN\VLC\libvlc.dll""")
        val found = VlcLocator.locate(
            osName = "Windows 11",
            userHome = """C:\Users\demo""",
            programFiles = """C:\Program Files""",
            programFilesX86 = """C:\Program Files (x86)""",
            exists = { it in present },
        )
        assertEquals("""C:\Program Files\VideoLAN\VLC""", found)
    }

    @Test
    fun linuxFindsUsrLib() {
        val present = setOf("/usr/lib/x86_64-linux-gnu/libvlc.so")
        val found = VlcLocator.locate(
            osName = "Linux",
            userHome = "/home/demo",
            exists = { it in present },
        )
        assertEquals("/usr/lib/x86_64-linux-gnu", found)
    }

    @Test
    fun missingLibraryReturnsNull() {
        val found = VlcLocator.locate(
            osName = "Mac OS X",
            userHome = "/Users/demo",
            exists = { false },
        )
        assertNull(found)
    }

    @Test
    fun jnaDumpBecomesInstallHint() {
        val dump = """
            Unable to load library 'vlc':
            Native library (darwin-aarch64/libvlc.dylib) not found in resource path
        """.trimIndent()
        assertEquals(VlcLocator.MISSING_MESSAGE, VlcLocator.friendlyError(dump))
    }

    @Test
    fun blankErrorBecomesInstallHint() {
        assertEquals(VlcLocator.MISSING_MESSAGE, VlcLocator.friendlyError(null))
        assertEquals(VlcLocator.MISSING_MESSAGE, VlcLocator.friendlyError("  "))
    }
}
