package app.bilidesk.core.player

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import app.bilidesk.core.data.AndroidHost
import app.bilidesk.core.model.PlayInfo
import app.bilidesk.core.model.USER_AGENT

actual class PlayerEngine actual constructor() {
    val player: ExoPlayer = ExoPlayer.Builder(AndroidHost.context)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 50_000, 1_500, 5_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build(),
        )
        .build()

    private val http = DefaultHttpDataSource.Factory()
        .setUserAgent(USER_AGENT)
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to "https://www.bilibili.com/",
                "Origin" to "https://www.bilibili.com",
            ),
        )
    private val progressive = ProgressiveMediaSource.Factory(http)
    private var backups: List<String> = emptyList()
    private var audioUrl: String? = null
    private var videoUrl: String? = null
    private val listeners = linkedSetOf<PlaybackListener>()
    private val bridge = object : Player.Listener {
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            listeners.toList().forEach { it.onError() }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val ended = playbackState == Player.STATE_ENDED
            listeners.toList().forEach { it.onEnded(ended) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listeners.toList().forEach { it.onIsPlayingChanged(isPlaying) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            listeners.toList().forEach { it.onPositionJump() }
        }
    }

    init {
        player.addListener(bridge)
    }

    actual val currentPosition: Long get() = player.currentPosition
    actual val duration: Long get() = player.duration
    actual val isPlaying: Boolean get() = player.isPlaying
    actual val playbackSpeed: Float get() = player.playbackParameters.speed
    actual var volume: Float
        get() = player.volume
        set(value) {
            player.volume = value
        }

    actual fun play() {
        player.play()
    }

    actual fun pause() {
        player.pause()
    }

    actual fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    actual fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    actual fun addListener(listener: PlaybackListener) {
        listeners.add(listener)
    }

    actual fun removeListener(listener: PlaybackListener) {
        listeners.remove(listener)
    }

    actual fun prepare() = Unit

    actual fun freeze() {
        player.pause()
    }

    actual fun play(info: PlayInfo, positionMs: Long, audioOnly: Boolean) {
        backups = info.backupUrls
        val audio = info.audioUrl
        audioUrl = audio
        videoUrl = info.videoUrl
        if (audioOnly && !audio.isNullOrBlank()) attachSingle(audio, positionMs) else attach(info.videoUrl, positionMs)
    }

    actual fun playAudioOnly(positionMs: Long): Boolean {
        val audio = audioUrl?.takeIf { it.isNotBlank() } ?: return false
        attachSingle(audio, positionMs)
        return true
    }

    actual fun restoreVideo(positionMs: Long) {
        val video = videoUrl?.takeIf { it.isNotBlank() } ?: return
        attach(video, positionMs)
    }

    actual fun failOver(positionMs: Long): Boolean {
        val next = backups.firstOrNull() ?: return false
        backups = backups.drop(1)
        attach(next, positionMs)
        return true
    }

    actual fun playLive(url: String) {
        val mime = when {
            url.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            url.contains(".flv") -> MimeTypes.VIDEO_FLV
            else -> null
        }
        val item = MediaItem.Builder().setUri(url).apply { if (mime != null) setMimeType(mime) }.build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    actual fun playFiles(videoPath: String, audioPath: String?) {
        val video = progressive.createMediaSource(MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(videoPath))))
        val source = if (audioPath.isNullOrBlank()) {
            video
        } else {
            MergingMediaSource(
                video,
                progressive.createMediaSource(MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(audioPath)))),
            )
        }
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }

    actual fun release() {
        player.release()
    }

    private fun attach(videoUrl: String, positionMs: Long) {
        val video = progressive.createMediaSource(MediaItem.fromUri(videoUrl))
        val audio = audioUrl
        val source = if (audio.isNullOrBlank()) {
            video
        } else {
            MergingMediaSource(video, progressive.createMediaSource(MediaItem.fromUri(audio)))
        }
        player.setMediaSource(source)
        player.prepare()
        if (positionMs > 0) player.seekTo(positionMs)
        player.playWhenReady = true
    }

    private fun attachSingle(url: String, positionMs: Long) {
        player.setMediaSource(progressive.createMediaSource(MediaItem.fromUri(url)))
        player.prepare()
        if (positionMs > 0) player.seekTo(positionMs)
        player.playWhenReady = true
    }
}

@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = engine.player
                useController = false
                setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                clearSubtitleBackdrop(this)
            }
        },
        update = { view ->
            if (view.player !== engine.player) view.player = engine.player
            clearSubtitleBackdrop(view)
        },
        modifier = modifier,
    )
}

private fun clearSubtitleBackdrop(view: PlayerView) {
    view.subtitleView?.apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setApplyEmbeddedStyles(false)
        setApplyEmbeddedFontSizes(false)
        setStyle(
            CaptionStyleCompat(
                AndroidColor.WHITE,
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_NONE,
                AndroidColor.TRANSPARENT,
                null,
            ),
        )
    }
}
