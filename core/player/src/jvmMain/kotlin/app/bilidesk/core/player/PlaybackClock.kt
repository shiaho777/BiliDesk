package app.bilidesk.core.player

/**
 * VLC 的 time()/JNI 不能在 UI 每帧去问，进度和播放态由事件写入，界面只读这几个字段。
 */
internal class PlaybackClock {
    @Volatile var positionMs: Long = 0L
        private set
    @Volatile var durationMs: Long = 0L
        private set
    @Volatile var playing: Boolean = false
        private set
    @Volatile var speed: Float = 1f
        private set

    fun acceptTime(ms: Long) {
        if (ms >= 0) positionMs = ms
    }

    fun acceptLength(ms: Long) {
        if (ms > 0) durationMs = ms
    }

    fun acceptPlaying(value: Boolean) {
        playing = value
    }

    fun acceptSpeed(value: Float) {
        speed = value.coerceIn(0.25f, 4f)
    }

    fun acceptSeek(ms: Long) {
        positionMs = ms.coerceAtLeast(0L)
    }
}
