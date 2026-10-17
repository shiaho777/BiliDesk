package app.bilidesk.feature.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import app.bilidesk.core.model.AppSettings
import app.bilidesk.core.model.Danmaku

/**
 * 滚动弹幕按固定高度分轨。位移跟播放进度走，播放中用墙钟把稀疏采样补平滑。
 * 暂停时钉住上一次播放进度，不再跟着播放器暂停后仍可能往前爬的读数。
 * 暂停中拖动进度条会重新钉住新位置。
 *
 * 一条弹幕的横坐标由「当前进度 − 它自己的时间戳」决定，加载晚了或拖动进度也不会堆在右边缘。
 * 轨道按播放时间占用 8 秒。更快的后一条会追上前一条时换轨；画面上已经重叠的也直接丢掉。
 * 顶部/底部固定弹幕和滚动弹幕共用同一组行，行被占着就不再往上放。
 * 进度一次跳过 1.5 秒，清掉在途弹幕，从「还能看见的窗口」重新放，避免一帧补出整段。
 */
internal class DanmakuRuntime {
    val shots = ArrayList<Shot>(MAX_ON_SCREEN)

    private val config = Config()
    private var raw: List<Danmaku> = emptyList()
    private var filtered: List<Danmaku> = emptyList()
    private var appliedKey = FilterKey()
    private var cursor = 0
    private var laneFreeAt = DoubleArray(0)
    private var laneWidth = FloatArray(0)
    private var laneCanvasW = 0f
    private var laneCanvasH = 0f
    private var laneArea = -1f
    private var lanePitch = 0f
    private val topSlots = ArrayList<FixedSlot>(8)
    private val bottomSlots = ArrayList<FixedSlot>(8)
    private var offered: List<Danmaku>? = null
    private var offeredSec = 0.0
    private var lastVideoSec = Double.NaN
    private var pinnedSec = Double.NaN
    private var pinned = false
    private var clockSec = Double.NaN
    private var clockNanos = 0L
    private var clockSpeed = 1f
    private var cachedLineH = 0f
    private var cachedLineKey = 0

    val wantsFrames: Boolean get() = config.enabled || shots.isNotEmpty()

    /**
     * 播放中用墙钟把采样进度补平滑。桌面 VLC 的 time() 往往几十到几百毫秒才跳一格，
     * 直接拿采样画弹幕会一顿一顿；安卓采样已经够密，墙钟步进和它几乎重合。
     * 暂停后只返回记住的进度，采样再往前爬也不跟。
     * 拖动进度条时由 [pin] 改掉记住的值。
     */
    fun presentedSec(
        sample: Double,
        playing: Boolean,
        nowNanos: Long = 0L,
        speed: Float = 1f,
    ): Double {
        if (playing) {
            pinned = false
            val shown = advanceClock(sample, nowNanos, speed)
            if (!shown.isNaN()) pinnedSec = shown
            return shown
        }
        if (!pinned) {
            pinned = true
            if (pinnedSec.isNaN()) {
                pinnedSec = when {
                    !clockSec.isNaN() -> clockSec
                    !sample.isNaN() -> sample
                    else -> Double.NaN
                }
            }
        }
        clockNanos = 0L
        return pinnedSec
    }

    /** 暂停中的跳转。播放中忽略，避免和实时采样抢进度。 */
    fun pin(sec: Double) {
        if (sec.isNaN()) return
        pinnedSec = sec
        clockSec = sec
        pinned = true
        clockNanos = 0L
    }

    private fun advanceClock(sample: Double, nowNanos: Long, speed: Float): Double {
        val rate = speed.coerceAtLeast(0.01f)
        if (nowNanos <= 0L) {
            if (!sample.isNaN()) clockSec = sample
            return if (sample.isNaN()) clockSec else sample
        }
        if (clockNanos == 0L || clockSec.isNaN()) {
            val start = when {
                !pinnedSec.isNaN() -> pinnedSec
                !sample.isNaN() -> sample
                else -> Double.NaN
            }
            clockSec = start
            clockNanos = nowNanos
            clockSpeed = rate
            return start
        }
        val elapsed = (nowNanos - clockNanos).coerceAtLeast(0L) / 1_000_000_000.0
        val predicted = clockSec + elapsed * clockSpeed
        clockNanos = nowNanos
        clockSpeed = rate
        if (!sample.isNaN() && abs(sample - predicted) > SNAP_SEC) {
            clockSec = sample
            return sample
        }
        clockSec = predicted
        return predicted
    }

    fun apply(settings: AppSettings) {
        config.enabled = settings.danmakuEnabled
        config.areaPercent = settings.danmakuAreaPercent.coerceIn(25, 100)
        config.paintColor = settings.danmakuColorful
        config.smart = settings.danmakuSmartFilter
        config.keywords = settings.danmakuBlockKeywords
        config.allowScroll = settings.danmakuFilterScroll
        config.allowFixed = settings.danmakuFilterFixed
        config.allowColorful = settings.danmakuFilterColorful
        config.allowAdvanced = settings.danmakuFilterAdvanced
    }

    fun offer(list: List<Danmaku>, videoSec: Double) {
        offered = list
        offeredSec = videoSec
    }

    fun step(
        videoSec: Double,
        widthPx: Float,
        heightPx: Float,
        laneHeightPx: Float,
        topInsetPx: Float,
        gapPx: Float,
        density: Density,
        direction: LayoutDirection,
        measurer: TextMeasurer,
    ) {
        val pending = offered
        if (pending != null) {
            offered = null
            sync(pending, offeredSec)
        }
        if (!config.enabled || widthPx < 1f || heightPx < 1f || laneHeightPx < 1f) {
            if (shots.isNotEmpty()) {
                shots.clear()
                resetOccupancy()
            }
            lastVideoSec = Double.NaN
            return
        }
        if (videoSec.isNaN()) return
        val pitch = linePitch(laneHeightPx, density, direction, measurer)
        retire(videoSec)
        val area = config.areaPercent / 100f
        if (ensureLanes(widthPx, heightPx, area, pitch)) {
            shots.clear()
            cursor = lowerBound(filtered, windowStart(videoSec))
        }
        if (!lastVideoSec.isNaN() && abs(videoSec - lastVideoSec) > CURSOR_TOLERANCE_SEC) {
            shots.clear()
            resetOccupancy()
            cursor = lowerBound(filtered, windowStart(videoSec))
        }
        lastVideoSec = videoSec
        val start = windowStart(videoSec)
        if (cursor < filtered.size && filtered[cursor].timeSec < start) {
            cursor = lowerBound(filtered, start)
        }
        var budget = SPAWN_BUDGET
        while (cursor < filtered.size && filtered[cursor].timeSec <= videoSec) {
            val item = filtered[cursor]
            val life = if (item.mode == 4 || item.mode == 5) FIXED_LIFE_SEC else SCROLL_SEC
            if (videoSec - item.timeSec >= life) {
                cursor++
                continue
            }
            if (budget == 0 || shots.size >= MAX_ON_SCREEN) break
            budget--
            cursor++
            spawn(
                item, videoSec, widthPx, heightPx, area, pitch,
                topInsetPx, gapPx, density, direction, measurer,
            )
        }
    }

    private fun sync(incoming: List<Danmaku>, videoSec: Double) {
        val key = config.filterKey()
        if (incoming === raw && key == appliedKey) return
        val appending = isAppend(raw, incoming)
        raw = incoming
        appliedKey = key
        val next = filterLibrary(incoming)
        if (appending) {
            filtered = next
            if (cursor > filtered.size) cursor = filtered.size
            return
        }
        filtered = next
        cursor = lowerBound(filtered, windowStart(videoSec))
        shots.clear()
        resetOccupancy()
        lastVideoSec = Double.NaN
        pinnedSec = Double.NaN
        pinned = false
    }

    /** 只在末尾接上更晚的弹幕时，已经在画面上的那些不用清掉重放。 */
    private fun isAppend(previous: List<Danmaku>, incoming: List<Danmaku>): Boolean {
        if (previous.isEmpty() || incoming.size <= previous.size) return false
        val last = previous.lastIndex
        if (incoming[0] !== previous[0] || incoming[last] !== previous[last]) return false
        val tail = previous[last].timeSec
        for (i in last + 1 until incoming.size) {
            if (incoming[i].timeSec < tail) return false
        }
        return true
    }

    private fun filterLibrary(list: List<Danmaku>): List<Danmaku> {
        val rules = KeywordRules.build(config.keywords)
        val typeFilter = !(config.allowScroll && config.allowFixed && config.allowColorful && config.allowAdvanced)
        if (!config.smart && rules == null && !typeFilter) return list
        val seen = if (config.smart) HashMap<String, Int>(64) else null
        val out = ArrayList<Danmaku>(list.size.coerceAtMost(4096))
        for (item in list) {
            val text = item.text
            if (text.isBlank()) continue
            if (typeFilter && !typeAllowed(item)) continue
            if (rules != null && rules.blocked(text)) continue
            if (config.smart) {
                if (text.length > 30) continue
                val count = seen!![text] ?: 0
                if (count >= 4) continue
                seen[text] = count + 1
            }
            out += item
        }
        return out
    }

    private fun typeAllowed(item: Danmaku): Boolean {
        val colorful = item.color != 0 && (item.color and 0xFFFFFF) != 0xFFFFFF
        return when (item.mode) {
            1, 2, 3 -> config.allowScroll || (colorful && config.allowColorful)
            7, 8, 9 -> config.allowAdvanced || (colorful && config.allowColorful)
            else -> config.allowFixed || (colorful && config.allowColorful)
        }
    }

    private fun retire(videoSec: Double) {
        if (shots.isEmpty()) return
        shots.removeAll { videoSec >= it.expireVideoSec }
    }

    private fun ensureLanes(widthPx: Float, heightPx: Float, area: Float, pitch: Float): Boolean {
        if (
            laneFreeAt.isNotEmpty() &&
            abs(widthPx - laneCanvasW) < 1f &&
            abs(heightPx - laneCanvasH) < 1f &&
            abs(area - laneArea) < 0.001f &&
            abs(pitch - lanePitch) < 0.5f
        ) {
            return false
        }
        val count = ((heightPx * area) / pitch).toInt().coerceAtLeast(1)
        laneFreeAt = DoubleArray(count)
        laneWidth = FloatArray(count)
        laneCanvasW = widthPx
        laneCanvasH = heightPx
        laneArea = area
        lanePitch = pitch
        topSlots.clear()
        bottomSlots.clear()
        return true
    }

    private fun resetOccupancy() {
        laneFreeAt.fill(0.0)
        laneWidth.fill(0f)
        topSlots.clear()
        bottomSlots.clear()
    }

    private fun linePitch(
        minPitch: Float,
        density: Density,
        direction: LayoutDirection,
        measurer: TextMeasurer,
    ): Float {
        val key = (density.density * 1000f).toInt() * 31 +
            (density.fontScale * 1000f).toInt() * 17 +
            direction.ordinal
        if (cachedLineH <= 0f || cachedLineKey != key) {
            val sample = measurer.measure(
                text = "国",
                style = commentStyle(Color.White),
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(),
                layoutDirection = direction,
                density = density,
                skipCache = true,
            )
            cachedLineH = sample.size.height.toFloat().coerceAtLeast(1f)
            cachedLineKey = key
        }
        return maxOf(minPitch, cachedLineH)
    }

    private fun spawn(
        item: Danmaku,
        videoSec: Double,
        widthPx: Float,
        heightPx: Float,
        area: Float,
        pitch: Float,
        topInsetPx: Float,
        gapPx: Float,
        density: Density,
        direction: LayoutDirection,
        measurer: TextMeasurer,
    ) {
        if (item.text.isEmpty()) return
        val layout = measurer.measure(
            text = item.text,
            style = commentStyle(danmakuPaint(item.color, config.paintColor)),
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(),
            layoutDirection = direction,
            density = density,
            skipCache = true,
        )
        val textW = layout.size.width.toFloat().coerceAtLeast(1f)
        val textH = layout.size.height.toFloat().coerceAtLeast(1f)
        val at = item.timeSec
        if (item.mode == 4 || item.mode == 5) {
            val areaH = heightPx * area
            val slotCount = (areaH / pitch).toInt().coerceAtLeast(1).coerceAtMost(laneFreeAt.size)
            val bottom = item.mode == 4
            val table = if (bottom) bottomSlots else topSlots
            val lane = allocateFixed(table, at, slotCount, bottom) { candidate ->
                val y = rowY(candidate, pitch, topInsetPx, textH)
                val x = ((widthPx - textW) / 2f).coerceAtLeast(0f)
                overlaps(x, y, textW, textH, videoSec, gapPx)
            }
            if (lane < 0) return
            shots += Shot(
                layout = layout,
                spawnVideoSec = at,
                expireVideoSec = at + FIXED_LIFE_SEC,
                y = rowY(lane, pitch, topInsetPx, textH),
                textW = textW,
                fixed = true,
                fixedX = ((widthPx - textW) / 2f).coerceAtLeast(0f),
                fromX = 0f,
            )
        } else {
            val lane = allocateRolling(
                width = textW,
                textH = textH,
                atSec = at,
                videoSec = videoSec,
                canvasWidth = widthPx,
                pitch = pitch,
                topInsetPx = topInsetPx,
                gapPx = gapPx,
            )
            if (lane < 0) return
            laneFreeAt[lane] = at + SCROLL_SEC
            laneWidth[lane] = textW
            shots += Shot(
                layout = layout,
                spawnVideoSec = at,
                expireVideoSec = at + SCROLL_SEC,
                y = rowY(lane, pitch, topInsetPx, textH),
                textW = textW,
                fixed = false,
                fixedX = 0f,
                fromX = widthPx,
            )
        }
    }

    private fun rowY(lane: Int, pitch: Float, topInsetPx: Float, textH: Float): Float {
        val top = topInsetPx + lane * pitch
        return top + ((pitch - textH) / 2f).coerceAtLeast(0f)
    }

    /**
     * 空轨道直接用。还占着的轨道，只有前一条的右边缘已经让开，
     * 并且更快的后一条在它离场前追不上，才复用最早腾空的那条。
     * 这一行如果还挂着固定弹幕，滚动弹幕不进。
     */
    private fun allocateRolling(
        width: Float,
        textH: Float,
        atSec: Double,
        videoSec: Double,
        canvasWidth: Float,
        pitch: Float,
        topInsetPx: Float,
        gapPx: Float,
    ): Int {
        if (laneFreeAt.isEmpty() || canvasWidth <= 0f) return -1
        val mySpeed = (canvasWidth + width) / SCROLL_SEC
        var best = -1
        var bestFree = Double.MAX_VALUE
        for (i in laneFreeAt.indices) {
            if (fixedBlocks(i, atSec)) continue
            val y = rowY(i, pitch, topInsetPx, textH)
            val xNow = rollingX(videoSec, atSec, width, canvasWidth)
            if (overlaps(xNow, y, width, textH, videoSec, gapPx)) continue
            val freeAt = laneFreeAt[i]
            if (freeAt <= atSec) return i
            if (freeAt >= bestFree) continue
            val prevWidth = if (i < laneWidth.size) laneWidth[i] else width
            val prevSpeed = (canvasWidth + prevWidth) / SCROLL_SEC
            val remaining = freeAt - atSec
            val prevElapsed = SCROLL_SEC - remaining
            val prevRight = canvasWidth - prevElapsed * prevSpeed + prevWidth
            val gapNow = canvasWidth - prevRight
            if (gapNow < gapPx) continue
            if (mySpeed > prevSpeed && gapNow < gapPx + (mySpeed - prevSpeed) * remaining) continue
            best = i
            bestFree = freeAt
        }
        return best
    }

    /** 新弹幕此刻的矩形若和屏幕上已有的任何一条相交，这条轨道不能用。 */
    private fun overlaps(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        videoSec: Double,
        gapPx: Float,
    ): Boolean {
        val right = x + w
        val bottom = y + h
        for (shot in shots) {
            val sx = shot.x(videoSec)
            val sy = shot.y
            val sh = shot.layout.size.height.toFloat()
            if (right + gapPx <= sx || sx + shot.textW + gapPx <= x) continue
            if (bottom <= sy || sy + sh <= y) continue
            return true
        }
        return false
    }

    private fun rollingX(videoSec: Double, spawn: Double, textW: Float, canvasWidth: Float): Float {
        val travel = ((videoSec - spawn) / SCROLL_SEC).toFloat().coerceIn(0f, 1f)
        return canvasWidth + (-textW - canvasWidth) * travel
    }

    private fun fixedBlocks(lane: Int, atSec: Double): Boolean {
        for (slot in topSlots) if (slot.lane == lane && slot.untilSec > atSec) return true
        for (slot in bottomSlots) if (slot.lane == lane && slot.untilSec > atSec) return true
        return false
    }

    private fun allocateFixed(
        table: MutableList<FixedSlot>,
        atSec: Double,
        slotCount: Int,
        bottom: Boolean,
        visuallyBlocked: (Int) -> Boolean,
    ): Int {
        table.removeAll { it.untilSec <= atSec }
        val lanes = laneFreeAt.size
        for (slot in 0 until slotCount) {
            if (table.any { it.slot == slot }) continue
            val lane = if (bottom) lanes - 1 - slot else slot
            if (lane !in laneFreeAt.indices) continue
            if (laneFreeAt[lane] > atSec) continue
            if (fixedBlocks(lane, atSec)) continue
            if (visuallyBlocked(lane)) continue
            table += FixedSlot(atSec + FIXED_LIFE_SEC, slot, lane)
            return lane
        }
        return -1
    }

    private fun commentStyle(color: Color) = TextStyle(
        color = color,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.9f),
            offset = Offset(0f, 1f),
            blurRadius = 2f,
        ),
        platformStyle = danmakuPlatformStyle(),
    )

    private class Config {
        var enabled = true
        var areaPercent = 25
        var paintColor = true
        var smart = true
        var keywords = ""
        var allowScroll = true
        var allowFixed = true
        var allowColorful = true
        var allowAdvanced = true

        fun filterKey() = FilterKey(smart, keywords, allowScroll, allowFixed, allowColorful, allowAdvanced)
    }

    private data class FilterKey(
        val smart: Boolean = true,
        val keywords: String = "",
        val scroll: Boolean = true,
        val fixed: Boolean = true,
        val colorful: Boolean = true,
        val advanced: Boolean = true,
    )

    private data class FixedSlot(val untilSec: Double, val slot: Int, val lane: Int)
}

internal class Shot(
    val layout: TextLayoutResult,
    val spawnVideoSec: Double,
    val expireVideoSec: Double,
    val y: Float,
    val textW: Float,
    val fixed: Boolean,
    val fixedX: Float,
    val fromX: Float,
) {
    fun alpha(videoSec: Double): Float {
        val elapsed = videoSec - spawnVideoSec
        if (!fixed) return if (elapsed >= SCROLL_SEC) 0f else 1f
        return when {
            elapsed <= HOLD_SEC -> 1f
            elapsed >= HOLD_SEC + FADE_SEC -> 0f
            else -> ((HOLD_SEC + FADE_SEC - elapsed) / FADE_SEC).toFloat()
        }
    }

    fun x(videoSec: Double): Float {
        if (fixed) return fixedX
        val travel = ((videoSec - spawnVideoSec) / SCROLL_SEC).toFloat().coerceIn(0f, 1f)
        return fromX + (-textW - fromX) * travel
    }
}

internal fun isDanmakuCursorStale(currentSeconds: Double, lastSentTime: Double, nextTime: Double): Boolean =
    lastSentTime > currentSeconds + CURSOR_TOLERANCE_SEC || nextTime < currentSeconds - CURSOR_TOLERANCE_SEC

internal fun lowerBound(list: List<Danmaku>, target: Double): Int {
    var lo = 0
    var hi = list.size
    while (lo < hi) {
        val mid = lo + (hi - lo) / 2
        if (list[mid].timeSec < target) lo = mid + 1 else hi = mid
    }
    return lo
}

internal fun danmakuPaint(color: Int, colorful: Boolean): Color {
    val rgb = color and 0xFFFFFF
    if (!colorful || rgb == 0 || rgb == 0xFFFFFF) return Color.White
    return Color(0xFF000000.toInt() or rgb)
}

private class KeywordRules(private val plain: List<String>, private val regex: List<Regex>) {
    fun blocked(text: String): Boolean {
        for (word in plain) {
            if (text.contains(word, ignoreCase = true)) return true
        }
        for (rule in regex) {
            if (runCatching { rule.containsMatchIn(text) }.getOrDefault(false)) return true
        }
        return false
    }

    companion object {
        fun build(raw: String): KeywordRules? {
            if (raw.isBlank()) return null
            val plain = ArrayList<String>()
            val regex = ArrayList<Regex>()
            for (part in raw.split('\n', '\r', ',', '，', ';', '；')) {
                val token = part.trim()
                if (token.isEmpty()) continue
                if (token.startsWith("re:", ignoreCase = true)) {
                    val pattern = token.substring(3).trim()
                    if (pattern.isEmpty()) continue
                    val compiled = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull() ?: continue
                    regex += compiled
                } else {
                    plain += token
                }
            }
            if (plain.isEmpty() && regex.isEmpty()) return null
            return KeywordRules(plain, regex)
        }
    }
}

private const val SNAP_SEC = 0.4
private const val SCROLL_SEC = 8.0
private const val HOLD_SEC = 4.0
private const val FADE_SEC = 0.6
private const val FIXED_LIFE_SEC = HOLD_SEC + FADE_SEC
private const val CURSOR_TOLERANCE_SEC = 1.5
private const val MAX_ON_SCREEN = 80
private const val SPAWN_BUDGET = 48

private fun windowStart(videoSec: Double): Double {
    if (videoSec.isNaN()) return 0.0
    return (videoSec - SCROLL_SEC).coerceAtLeast(0.0)
}
