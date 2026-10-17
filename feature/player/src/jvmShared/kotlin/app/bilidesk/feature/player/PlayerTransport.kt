package app.bilidesk.feature.player

import app.bilidesk.core.designsystem.decodeImageBytes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.bilidesk.core.data.AppGraph
import app.bilidesk.core.model.ShotFrame
import app.bilidesk.core.model.SponsorSegment
import app.bilidesk.core.model.USER_AGENT
import app.bilidesk.core.model.VideoShot
import app.bilidesk.core.model.ViewChapter
import app.bilidesk.core.model.formatClock
import app.bilidesk.core.player.PlayerEngine
import app.bilidesk.core.model.ioDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import app.bilidesk.core.network.get
import kotlin.math.roundToInt

/**
 * 进度时钟只重组这一行。播放页其余部分不跟着 currentPosition 刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerTransport(
    engine: PlayerEngine,
    live: Boolean,
    playing: Boolean,
    onScrubbing: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onVolume: () -> Unit,
    muted: Boolean,
    marks: List<SponsorSegment> = emptyList(),
    chapters: List<ViewChapter> = emptyList(),
    shot: VideoShot? = null,
    modifier: Modifier = Modifier,
) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableFloatStateOf(0f) }
    val draggingState = remember { mutableStateOf(false) }
    var dragging by draggingState
    LaunchedEffect(engine) {
        while (isActive) {
            val pos = runCatching { engine.currentPosition }.getOrNull()
            val dur = runCatching { engine.duration }.getOrNull()
            if (pos != null && !draggingState.value && pos != position) position = pos
            if (dur != null && dur > 0 && dur != duration) duration = dur
            delay(200)
        }
    }
    val fraction = if (dragging) drag else if (duration > 0) position.toFloat() / duration else 0f
    Row(
        modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onTogglePlay) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "播放或暂停",
                tint = Color.White,
            )
        }
        IconButton(onClick = onVolume) {
            Icon(
                if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = "音量",
                tint = Color.White,
            )
        }
        Text(formatClock(position), color = Color.White, style = MaterialTheme.typography.labelMedium)
        Column(Modifier.weight(1f)) {
            if (dragging && !live && duration > 0) {
                val sec = (drag * duration / 1000f).toInt()
                val chapter = chapters.lastOrNull { sec >= it.fromSec && (it.toSec <= it.fromSec || sec < it.toSec) }
                val frame = shot?.frameAt(sec)
                SeekPreview(frame, formatClock(sec * 1000L), chapter?.title)
            }
            if (!live && duration > 0 && chapters.isNotEmpty()) {
                ChapterTicks(chapters, duration)
            }
            if (!live && duration > 0 && marks.isNotEmpty()) {
                SponsorMarks(marks, duration)
            }
            Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = {
                if (!dragging) onScrubbing(true)
                dragging = true
                drag = it
            },
            onValueChangeFinished = {
                if (!live && duration > 0) {
                    val ms = (drag * duration).toLong()
                    position = ms
                    onSeek(ms)
                }
                dragging = false
                onScrubbing(false)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !live && duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.35f),
            ),
        )
        }
        Text(
            if (live) "直播" else formatClock(duration),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}

@Composable
private fun SponsorMarks(marks: List<SponsorSegment>, durationMs: Long) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(4.dp),
    ) {
        marks.forEach { segment ->
            val start = (segment.startSec * 1000 / durationMs).toFloat().coerceIn(0f, 1f)
            val end = (segment.endSec * 1000 / durationMs).toFloat().coerceIn(start, 1f)
            Box(
                Modifier
                    .offset(x = maxWidth * start)
                    .width(maxWidth * (end - start).coerceAtLeast(0.008f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(sponsorMarkColor(segment.category)),
            )
        }
    }
}

@Composable
private fun ChapterTicks(chapters: List<ViewChapter>, durationMs: Long) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(6.dp),
    ) {
        chapters.forEach { chapter ->
            val x = (chapter.fromSec * 1000f / durationMs).coerceIn(0f, 1f)
            Box(
                Modifier
                    .offset(x = maxWidth * x)
                    .width(2.dp)
                    .height(6.dp)
                    .background(Color.White.copy(alpha = 0.9f)),
            )
        }
    }
}

@Composable
private fun SeekPreview(frame: ShotFrame?, clock: String, chapter: String?) {
    Column(
        Modifier
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (frame != null) ShotStill(frame)
        Text(clock, color = Color.White, style = MaterialTheme.typography.labelMedium)
        if (!chapter.isNullOrBlank()) {
            Text(
                chapter,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ShotStill(frame: ShotFrame) {
    val cache = remember { HashMap<String, ImageBitmap>() }
    var image by remember(frame.url) { mutableStateOf(cache[frame.url]) }
    LaunchedEffect(frame.url) {
        val cached = cache[frame.url]
        if (cached != null) {
            image = cached
            return@LaunchedEffect
        }
        val loaded = withContext(ioDispatcher) { loadShot(frame.url) } ?: return@LaunchedEffect
        cache[frame.url] = loaded
        image = loaded
    }
    val src = image ?: return
    if (src.width <= 0 || src.height <= 0) return
    val columns = frame.columns.coerceAtLeast(1)
    val rows = frame.rows.coerceAtLeast(1)
    val cellW = if (frame.cellWidth > 0) frame.cellWidth else src.width / columns
    val cellH = if (frame.cellHeight > 0) frame.cellHeight else src.height / rows
    val left = (frame.column * cellW).coerceIn(0, src.width - 1)
    val top = (frame.row * cellH).coerceIn(0, src.height - 1)
    val cropW = cellW.coerceAtMost(src.width - left).coerceAtLeast(1)
    val cropH = cellH.coerceAtMost(src.height - top).coerceAtLeast(1)
    Canvas(
        Modifier
            .size(width = 160.dp, height = 90.dp)
            .clip(RoundedCornerShape(6.dp)),
    ) {
        drawImage(
            src,
            srcOffset = IntOffset(left, top),
            srcSize = IntSize(cropW, cropH),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
    }
}

private fun loadShot(url: String): ImageBitmap? = runCatching {
    val resp = AppGraph.http.get(
        url,
        mapOf(
            "Referer" to "https://www.bilibili.com/",
            "User-Agent" to USER_AGENT,
        ),
    )
    if (!resp.isSuccessful) return null
    decodeImageBytes(resp.body)
}.getOrNull()

private fun sponsorMarkColor(category: String): Color = when (category) {
    "sponsor" -> Color(0xFFFB7299)
    "selfpromo" -> Color(0xFFFFB74D)
    "intro" -> Color(0xFF4FC3F7)
    "outro" -> Color(0xFF9575CD)
    "preview" -> Color(0xFF4DB6AC)
    "poi_highlight" -> Color(0xFFFFD54F)
    else -> Color(0xFFBDBDBD)
}
