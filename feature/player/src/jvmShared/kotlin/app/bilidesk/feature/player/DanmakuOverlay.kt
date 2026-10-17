package app.bilidesk.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.bilidesk.core.model.AppSettings
import app.bilidesk.core.model.Danmaku
import app.bilidesk.core.player.PlaybackListener
import app.bilidesk.core.player.PlayerEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 弹幕画在视频整幅上，控制栏叠在它上面。位置每帧只改绘制，不触发布局或整页重组。
 */
@Composable
internal fun DanmakuOverlay(
    player: PlayerEngine,
    comments: List<Danmaku>,
    settings: AppSettings,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val runtime = remember { DanmakuRuntime() }
    SideEffect {
        runtime.apply(settings)
        val sec = runCatching { player.currentPosition / 1000.0 }.getOrDefault(0.0)
        runtime.offer(comments, sec)
    }
    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .clearAndSetSemantics {}
            .then(DanmakuElement(runtime, player, measurer)),
    )
}

private class DanmakuElement(
    private val runtime: DanmakuRuntime,
    private val player: PlayerEngine,
    private val measurer: TextMeasurer,
) : ModifierNodeElement<DanmakuNode>() {
    override fun create() = DanmakuNode(runtime, player, measurer)

    override fun update(node: DanmakuNode) {
        node.runtime = runtime
        node.rebindPlayer(player)
        node.measurer = measurer
    }

    override fun equals(other: Any?): Boolean {
        if (other !is DanmakuElement) return false
        return runtime === other.runtime && player === other.player && measurer === other.measurer
    }

    override fun hashCode(): Int {
        var hash = runtime.hashCode()
        hash = 31 * hash + player.hashCode()
        hash = 31 * hash + measurer.hashCode()
        return hash
    }
}

private class DanmakuNode(
    var runtime: DanmakuRuntime,
    var player: PlayerEngine,
    var measurer: TextMeasurer,
) : Modifier.Node(), DrawModifierNode {
    private var clock: Job? = null
    @Volatile private var frameNanos = 0L
    private val playerEvents = object : PlaybackListener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            invalidateDraw()
        }

        override fun onPositionJump() {
            if (!player.isPlaying) pinNow()
            invalidateDraw()
        }
    }

    override fun onAttach() {
        bindPlayer()
        clock = coroutineScope.launch {
            while (isActive) {
                withFrameNanos { nanos ->
                    frameNanos = nanos
                    if (runtime.wantsFrames && player.isPlaying) invalidateDraw()
                }
            }
        }
    }

    override fun onDetach() {
        unbindPlayer()
        clock?.cancel()
        clock = null
    }

    fun rebindPlayer(next: PlayerEngine) {
        if (player === next) return
        unbindPlayer()
        player = next
        if (isAttached) bindPlayer()
    }

    private fun bindPlayer() {
        runCatching { player.addListener(playerEvents) }
    }

    private fun unbindPlayer() {
        runCatching { player.removeListener(playerEvents) }
    }

    private fun pinNow() {
        val sec = runCatching { player.currentPosition / 1000.0 }.getOrDefault(Double.NaN)
        runtime.pin(sec)
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val durationMs = runCatching { player.duration }.getOrDefault(0L)
        val sample = if (durationMs <= 0L) {
            Double.NaN
        } else {
            runCatching { player.currentPosition / 1000.0 }.getOrDefault(0.0)
        }
        val playing = runCatching { player.isPlaying }.getOrDefault(false)
        val speed = runCatching { player.playbackSpeed }.getOrDefault(1f)
        val now = frameNanos.takeIf { it > 0L } ?: System.nanoTime()
        val videoSec = runtime.presentedSec(sample, playing, now, speed)
        if (videoSec.isNaN()) return
        runtime.step(
            videoSec = videoSec,
            widthPx = size.width,
            heightPx = size.height,
            laneHeightPx = 28.dp.toPx(),
            topInsetPx = 6.dp.toPx(),
            gapPx = 12.dp.toPx(),
            density = this,
            direction = layoutDirection,
            measurer = measurer,
        )
        for (shot in runtime.shots) {
            val fade = shot.alpha(videoSec)
            if (fade <= 0f) continue
            drawText(
                textLayoutResult = shot.layout,
                topLeft = Offset(shot.x(videoSec), shot.y),
                alpha = fade,
            )
        }
    }
}
