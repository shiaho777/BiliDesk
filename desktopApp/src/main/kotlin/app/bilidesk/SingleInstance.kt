package app.bilidesk

import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import javax.swing.SwingUtilities
import app.bilidesk.core.data.DesktopHost

/**
 * 同一用户会话只跑一个桌面进程。第二个实例把「请显形」发给已在跑的那个后退出。
 */
object SingleInstance {
    private var lockFile: RandomAccessFile? = null
    private var lock: FileLock? = null
    private var server: ServerSocket? = null

    fun tryAcquire(): Boolean {
        val dir = File(System.getProperty("user.home"), ".bilidesk").apply { mkdirs() }
        val lockPath = File(dir, "instance.lock")
        val portPath = File(dir, "instance.port")
        return try {
            val raf = RandomAccessFile(lockPath, "rw")
            val got = raf.channel.tryLock()
            if (got == null) {
                raf.close()
                signal(portPath)
                false
            } else {
                lockFile = raf
                lock = got
                val ss = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
                server = ss
                portPath.writeText(ss.localPort.toString())
                Thread({
                    while (!ss.isClosed) {
                        try {
                            ss.accept().use { it.getInputStream().read() }
                            SwingUtilities.invokeLater { DesktopHost.showWindow?.invoke() }
                        } catch (_: Exception) {
                            try {
                                Thread.sleep(400)
                            } catch (_: InterruptedException) {
                                return@Thread
                            }
                        }
                    }
                }, "bilidesk-single").apply { isDaemon = true }.start()
                true
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun signal(portFile: File) {
        repeat(20) {
            val port = runCatching { portFile.readText().trim().toInt() }.getOrNull()
            if (port != null && port > 0) {
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress("127.0.0.1", port), 400)
                        socket.getOutputStream().write(1)
                    }
                    return
                } catch (_: Exception) {
                }
            }
            try {
                Thread.sleep(80)
            } catch (_: InterruptedException) {
                return
            }
        }
    }
}
