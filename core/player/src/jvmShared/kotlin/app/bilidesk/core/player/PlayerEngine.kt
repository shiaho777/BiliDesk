package app.bilidesk.core.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.bilidesk.core.model.PlayInfo

interface PlaybackListener {
    fun onError() {}
    fun onEnded(ended: Boolean) {}
    fun onIsPlayingChanged(isPlaying: Boolean) {}
    fun onPositionJump() {}
}

/**
 * 播放器。Android 用 Media3，桌面用系统里的 VLC。界面只看见这一层。
 */
expect class PlayerEngine() {
    val currentPosition: Long
    val duration: Long
    val isPlaying: Boolean
    val playbackSpeed: Float
    var volume: Float

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun addListener(listener: PlaybackListener)
    fun removeListener(listener: PlaybackListener)
    fun prepare()
    fun freeze()

    fun play(info: PlayInfo, positionMs: Long = 0, audioOnly: Boolean = false)
    fun playAudioOnly(positionMs: Long): Boolean
    fun restoreVideo(positionMs: Long)
    fun failOver(positionMs: Long): Boolean
    fun playLive(url: String)
    fun playFiles(videoPath: String, audioPath: String?)
    fun release()
}

@Composable
expect fun VideoSurface(engine: PlayerEngine, modifier: Modifier = Modifier)
