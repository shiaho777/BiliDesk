package app.bilidesk.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

/**
 * 壳层有两种导航，页内的下划线、胶囊、简介/评论页签不走这里。
 * 默认是底部 ShortNavigationBar（图标在上、文字在下，标签常显）。
 * 左侧是 Windows 那套通高窄轨，在设置里切换。WideNavigationRail 展开后图标在左，不用。
 * 推入的页面两种都不显示。
 */

data class BiliRailItem(val label: String, val icon: ImageVector)

enum class BiliChrome { None, Rail, Bottom }

/**
 * 底部标签栏自己吃掉了系统导航条。为 true 时，页内不要再加 navigationBars 留白，
 * 否则内容和悬浮按钮会悬在栏上方一块空白里。
 */
val LocalShellBottomBar = compositionLocalOf { false }

@Composable
fun Modifier.shellBottomPadding(): Modifier =
    if (LocalShellBottomBar.current) this else this.navigationBarsPadding()

@Composable
fun shellNavigationBottom(): Dp =
    if (LocalShellBottomBar.current) 0.dp
    else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
fun BiliShell(
    chrome: BiliChrome,
    rail: @Composable (wide: Boolean) -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val latest = rememberUpdatedState(content)
    val page = remember { movableContentOf { latest.value.invoke() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (chrome) {
            BiliChrome.None -> page()
            BiliChrome.Rail -> RailFrame(rail, page)
            BiliChrome.Bottom -> BottomFrame(bottomBar, page)
        }
    }
}

@Composable
private fun RailFrame(
    rail: @Composable (wide: Boolean) -> Unit,
    page: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 手机上轨不能占到桌面那 96dp，但标签必须留下：窄档在桌面端靠悬停提示，触摸没有悬停。
        val wide = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .width(if (wide) 96.dp else 72.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                rail(wide)
            }
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Box(Modifier.weight(1f).fillMaxHeight()) { page() }
        }
    }
}

@Composable
private fun BottomFrame(
    bottomBar: @Composable () -> Unit,
    page: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) {
            CompositionLocalProvider(LocalShellBottomBar provides true) {
                page()
            }
        }
        bottomBar()
    }
}

@Composable
fun BiliBottomBar(
    items: List<BiliRailItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onLogo: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val colors = ShortNavigationBarItemDefaults.colors(
        selectedIconColor = primary,
        selectedTextColor = primary,
        selectedIndicatorColor = primary.copy(alpha = 0.11f),
        unselectedIconColor = muted,
        unselectedTextColor = muted,
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val arrangement = if (maxWidth >= 600.dp) {
            ShortNavigationBarArrangement.Centered
        } else {
            ShortNavigationBarArrangement.EqualWeight
        }
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            ShortNavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = muted,
                arrangement = arrangement,
            ) {
                ShortNavigationBarItem(
                    selected = false,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onLogo()
                    },
                    icon = { BrandMark(24.dp, 8.dp) },
                    label = { Text("分区") },
                    iconPosition = NavigationItemIconPosition.Top,
                    colors = colors,
                )
                items.forEachIndexed { index, item ->
                    val on = index == selected
                    ShortNavigationBarItem(
                        selected = on,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(index)
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        iconPosition = NavigationItemIconPosition.Top,
                        colors = colors,
                    )
                }
            }
        }
    }
}

@Composable
fun BiliRail(
    items: List<BiliRailItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onLogo: () -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 8.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onLogo()
                }
                .semantics {
                    role = Role.Button
                    contentDescription = "分区"
                },
            contentAlignment = Alignment.Center,
        ) {
            BrandMark(if (wide) 42.dp else 36.dp)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            items.forEachIndexed { index, item ->
                RailButton(
                    item = item,
                    selected = index == selected,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(index)
                    },
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 4.dp),
                )
            }
        }
    }
}

@Composable
private fun BrandMark(mark: Dp, corner: Dp = 12.dp) {
    val pink = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(mark).clip(RoundedCornerShape(corner))) {
        drawRoundRect(color = pink, cornerRadius = CornerRadius(corner.toPx(), corner.toPx()))
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.38f, h * 0.30f)
            lineTo(w * 0.38f, h * 0.70f)
            lineTo(w * 0.72f, h * 0.50f)
            close()
        }
        drawPath(path, Color.White)
    }
}

@Composable
private fun RailButton(
    item: BiliRailItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.11f) else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = item.label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(item.icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Text(
            item.label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
fun BiliPageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 34.sp,
    )
}

@Composable
fun BiliSectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text,
            modifier = Modifier.padding(start = 8.dp),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun BiliBackHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            title,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 首页那排：字重不变，选中只变粉，底下一根 20×3 的短线滑过去。 */
@Composable
fun BiliUnderlineTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val centers = remember(labels) { SnapshotStateMap<Int, Float>() }
    var row by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val motion = MaterialTheme.motionScheme
    val indicator = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val center = centers[selected]
    LaunchedEffect(center) {
        if (center == null) return@LaunchedEffect
        if (!placed) {
            indicator.snapTo(center)
            placed = true
        } else {
            indicator.animateTo(center, motion.fastSpatialSpec())
        }
    }
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier.onGloballyPositioned { row = it }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { index, label ->
                val on = index == selected
                Box(
                    Modifier
                        .padding(start = if (index == 0) 8.dp else 4.dp)
                        .heightIn(min = 48.dp)
                        .onGloballyPositioned { child ->
                            val parent = row ?: return@onGloballyPositioned
                            val at = parent.localPositionOf(child, Offset(child.size.width / 2f, 0f))
                            val prev = centers[index]
                            if (prev == null || abs(prev - at.x) > 0.5f) centers[index] = at.x
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(index)
                        }
                        .padding(horizontal = 12.dp)
                        .semantics {
                            role = Role.Tab
                            this.selected = on
                            contentDescription = label
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (on) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
        if (placed) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .graphicsLayer { translationX = indicator.value - 10.dp.toPx() }
                    .padding(bottom = 4.dp)
                    .size(width = 20.dp, height = 3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(accent),
            )
        }
    }
}

/** 播放器信息区：简介 / 评论是文字页签，合集是紧挨在右边的动作，不进同一组。 */
@Composable
fun BiliContentTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionOn: Boolean = false,
    onAction: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            ContentTab(
                label = label,
                on = index == selected,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(index)
                },
                modifier = Modifier.padding(end = 24.dp),
            )
        }
        if (actionLabel != null && onAction != null) {
            ContentTab(
                label = actionLabel,
                on = actionOn,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onAction()
                },
            )
        }
    }
}

@Composable
private fun ContentTab(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(IntrinsicSize.Max)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .semantics {
                role = Role.Tab
                selected = on
                contentDescription = label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

/** 设置页：淡强调色胶囊，不是实心粉块，也不是等分页签。 */
@Composable
fun BiliPillTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            labels.forEachIndexed { index, label ->
                val on = index == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(index)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics {
                            role = Role.Tab
                            this.selected = on
                            contentDescription = label
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
fun BiliFloatButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "float",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .size(48.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (dark) Color(0xFF3A3A3A) else Color(0xFFE8EAED),
        contentColor = if (dark) Color(0xFFF2F2F2) else Color(0xFF1A1A1A),
        shadowElevation = 4.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}
