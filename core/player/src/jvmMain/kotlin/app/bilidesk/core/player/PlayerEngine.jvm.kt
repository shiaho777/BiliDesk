package app.bilidesk.core.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.bilidesk.core.model.PlayInfo
import app.bilidesk.core.model.USER_AGENT
import com.sun.jna.NativeLibrary
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.EventQueue
import java.io.File
import java.nio.ByteBuffer

actual class PlayerEngine actual constructor() {
    private val listeners = linkedSetOf<PlaybackListener>()
    private var backups: List<String> = emptyList()
    private var audioUrl: String? = null
    private var videoUrl: String? = null
    private var audioOnly = false
    private var speed = 1f
    private var volumeLevel = 1f

    @Volatile var statusMessage: String? = null
    private val pump = FramePump()
    private val clock = PlaybackClock()
    val frameGeneration: Int get() = pump.frameGeneration

    private val gate = Any()
    @Volatile private var dead = false
    private var factory: MediaPlayerFactory? = null
    private var media: EmbeddedMediaPlayer? = null

    actual val currentPosition: Long get() = clock.positionMs
    actual val duration: Long get() = clock.durationMs
    actual val isPlaying: Boolean get() = clock.playing
    actual val playbackSpeed: Float get() = clock.speed
    actual var volume: Float
        get() = volumeLevel
        set(value) {
            volumeLevel = value.coerceIn(0f, 1f)
            media?.audio()?.setVolume((volumeLevel * 100).toInt())
        }

    actual fun play() {
        media?.controls()?.play()
    }

    actual fun pause() {
        media?.controls()?.pause()
    }

    actual fun seekTo(positionMs: Long) {
        clock.acceptSeek(positionMs)
        media?.controls()?.setTime(positionMs)
        notify { it.onPositionJump() }
    }

    actual fun setPlaybackSpeed(speed: Float) {
        this.speed = speed
        clock.acceptSpeed(speed)
        media?.controls()?.setRate(speed)
    }

    actual fun addListener(listener: PlaybackListener) {
        listeners.add(listener)
    }

    actual fun removeListener(listener: PlaybackListener) {
        listeners.remove(listener)
    }

    actual fun prepare() {
        synchronized(gate) {
            if (dead || media != null) return
            val opened = runCatching { openPlayer() }
            factory = opened.getOrNull()?.first
            media = opened.getOrNull()?.second
            if (media == null) {
                statusMessage = VlcLocator.friendlyError(opened.exceptionOrNull()?.message)
            }
        }
    }

    actual fun freeze() {
        clock.acceptPlaying(false)
        pump.halt()
        runCatching { media?.controls()?.pause() }
    }

    actual fun play(info: PlayInfo, positionMs: Long, audioOnly: Boolean) {
        backups = info.backupUrls
        audioUrl = info.audioUrl
        videoUrl = info.videoUrl
        this.audioOnly = audioOnly && !info.audioUrl.isNullOrBlank()
        val url = if (this.audioOnly) info.audioUrl.orEmpty() else info.videoUrl
        start(url, if (this.audioOnly) null else info.audioUrl, positionMs)
    }

    actual fun playAudioOnly(positionMs: Long): Boolean {
        val audio = audioUrl?.takeIf { it.isNotBlank() } ?: return false
        audioOnly = true
        start(audio, null, positionMs)
        return true
    }

    actual fun restoreVideo(positionMs: Long) {
        val video = videoUrl?.takeIf { it.isNotBlank() } ?: return
        audioOnly = false
        start(video, audioUrl, positionMs)
    }

    actual fun failOver(positionMs: Long): Boolean {
        val next = backups.firstOrNull() ?: return false
        backups = backups.drop(1)
        videoUrl = next
        start(next, audioUrl, positionMs)
        return true
    }

    actual fun playLive(url: String) {
        audioOnly = false
        audioUrl = null
        videoUrl = url
        start(url, null, 0)
    }

    actual fun playFiles(videoPath: String, audioPath: String?) {
        audioOnly = false
        videoUrl = File(videoPath).toURI().toString()
        audioUrl = audioPath?.let { File(it).toURI().toString() }
        start(videoUrl.orEmpty(), audioUrl, 0)
    }

    actual fun release() {
        synchronized(gate) {
            dead = true
            runCatching { media?.release() }
            runCatching { factory?.release() }
            media = null
            factory = null
            pump.release()
        }
    }

    fun peekImage() = pump.peek()

    private fun start(url: String, slave: String?, positionMs: Long) {
        val player = media ?: return
        if (url.isBlank()) return
        statusMessage = null
        val options = buildList {
            add(":http-referrer=https://www.bilibili.com/")
            add(":http-user-agent=$USER_AGENT")
            if (!slave.isNullOrBlank()) add(":input-slave=$slave")
            if (positionMs > 0) add(":start-time=${positionMs / 1000.0}")
        }
        clock.acceptSeek(positionMs.coerceAtLeast(0L))
        clock.acceptSpeed(speed)
        player.media().play(url, *options.toTypedArray())
        player.audio().setVolume((volumeLevel * 100).toInt())
        player.controls().setRate(speed)
    }

    private fun notify(block: (PlaybackListener) -> Unit) {
        val copy = listeners.toList()
        EventQueue.invokeLater { copy.forEach(block) }
    }

    private fun openPlayer(): Pair<MediaPlayerFactory, EmbeddedMediaPlayer> {
        val created = openFactory()
        val player = created.mediaPlayers().newEmbeddedMediaPlayer()
        val surface = created.videoSurfaces().newVideoSurface(
            object : BufferFormatCallback {
                override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                    val (width, height) = pump.sizeFor(sourceWidth, sourceHeight)
                    return RV32BufferFormat(width, height)
                }

                override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit
            },
            object : RenderCallback {
                override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<out ByteBuffer>, bufferFormat: BufferFormat) {
                    val source = nativeBuffers.firstOrNull() ?: return
                    val width = bufferFormat.width
                    val height = bufferFormat.height
                    if (width <= 0 || height <= 0) return
                    val pitch = bufferFormat.pitches.firstOrNull()?.takeIf { it >= width * 4 } ?: (width * 4)
                    pump.publish(source, width, height, pitch)
                }
            },
            true,
        )
        player.videoSurface().set(surface)
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun error(mediaPlayer: MediaPlayer) {
                notify { it.onError() }
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                clock.acceptPlaying(false)
                notify { it.onEnded(true) }
            }

            override fun playing(mediaPlayer: MediaPlayer) {
                clock.acceptPlaying(true)
                clock.acceptTime(mediaPlayer.status().time())
                clock.acceptLength(mediaPlayer.status().length())
                notify {
                    it.onEnded(false)
                    it.onIsPlayingChanged(true)
                }
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                clock.acceptPlaying(false)
                notify { it.onIsPlayingChanged(false) }
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                clock.acceptPlaying(false)
                notify { it.onIsPlayingChanged(false) }
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                clock.acceptTime(newTime)
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                clock.acceptLength(newLength)
            }

            override fun forward(mediaPlayer: MediaPlayer) {
                notify { it.onPositionJump() }
            }

            override fun backward(mediaPlayer: MediaPlayer) {
                notify { it.onPositionJump() }
            }
        })
        return created to player
    }
}

@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier) {
    Box(
        modifier
            .background(Color.Black)
            .then(VideoFrameElement(engine)),
        contentAlignment = Alignment.Center,
    ) {
        engine.statusMessage?.let {
            Text(
                it,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp).fillMaxSize(),
            )
        }
    }
}

private class VideoFrameElement(
    private val engine: PlayerEngine,
) : ModifierNodeElement<VideoFrameNode>() {
    override fun create() = VideoFrameNode(engine)

    override fun update(node: VideoFrameNode) {
        node.rebind(engine)
    }

    override fun equals(other: Any?): Boolean =
        other is VideoFrameElement && other.engine === engine

    override fun hashCode(): Int = engine.hashCode()
}

private class VideoFrameNode(
    var engine: PlayerEngine,
) : Modifier.Node(), DrawModifierNode {
    private var clock: Job? = null
    private val sampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)

    override fun onAttach() {
        clock = coroutineScope.launch {
            var seen = -1
            while (isActive) {
                withFrameNanos {
                    val gen = engine.frameGeneration
                    if (gen != seen) {
                        seen = gen
                        invalidateDraw()
                    }
                }
            }
        }
    }

    override fun onDetach() {
        clock?.cancel()
        clock = null
    }

    fun rebind(next: PlayerEngine) {
        if (engine === next) return
        engine = next
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        val image = engine.peekImage() ?: return
        val srcW = image.width.toFloat()
        val srcH = image.height.toFloat()
        if (srcW < 1f || srcH < 1f) return
        val scale = minOf(size.width / srcW, size.height / srcH)
        val dw = srcW * scale
        val dh = srcH * scale
        val left = (size.width - dw) / 2f
        val top = (size.height - dh) / 2f
        drawIntoCanvas { canvas ->
            canvas.skiaCanvas.drawImageRect(
                image,
                Rect.makeXYWH(0f, 0f, srcW, srcH),
                Rect.makeXYWH(left, top, dw, dh),
                sampling,
                null,
                true,
            )
        }
    }
}

private fun openFactory(): MediaPlayerFactory {
    prepareNativeVlc()
    val discovery = NativeDiscovery()
    val mac = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
    val args = arrayOf(
        if (mac) "--avcodec-hw=videotoolbox" else "--avcodec-hw=any",
        "--network-caching=400",
        "--no-osd",
        "--drop-late-frames",
        "--skip-frames",
        "--no-video-title-show",
    )
    return runCatching { MediaPlayerFactory(discovery, *args) }
        .getOrElse { MediaPlayerFactory(NativeDiscovery()) }
}

private fun prepareNativeVlc() {
    val dir = VlcLocator.locate() ?: return
    NativeLibrary.addSearchPath("vlc", dir)
    NativeLibrary.addSearchPath("vlccore", dir)
    val current = System.getProperty("jna.library.path")
    System.setProperty(
        "jna.library.path",
        if (current.isNullOrBlank()) dir else "$dir${File.pathSeparator}$current",
    )
    val plugins = File(dir, "../plugins")
    if (plugins.isDirectory) {
        System.setProperty("VLC_PLUGIN_PATH", plugins.canonicalPath)
    }
}


