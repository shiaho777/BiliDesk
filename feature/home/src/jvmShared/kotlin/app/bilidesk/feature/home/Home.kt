package app.bilidesk.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bilidesk.core.data.AppGraph
import app.bilidesk.core.designsystem.BiliFloatButton
import app.bilidesk.core.designsystem.ContentFilter
import app.bilidesk.core.designsystem.BiliPageTitle
import app.bilidesk.core.designsystem.BiliUnderlineTabs
import app.bilidesk.core.designsystem.LoadBox
import app.bilidesk.core.designsystem.shellBottomPadding
import app.bilidesk.core.designsystem.shellNavigationBottom
import app.bilidesk.core.designsystem.VideoGrid
import app.bilidesk.core.designsystem.appViewModel
import app.bilidesk.core.model.ThemeMode
import app.bilidesk.core.model.VideoItem
import kotlinx.coroutines.launch

enum class HomeTab(val label: String) { Recommend("推荐"), Popular("热门"), Ranking("排行榜"), Live("直播") }

class HomeViewModel : ViewModel() {
    var tab by mutableStateOf(HomeTab.Recommend)
    var items by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var page by mutableIntStateOf(1)
    var hasMore by mutableStateOf(true)

    init { refresh() }

    fun select(next: HomeTab) {
        if (tab == next) return
        tab = next
        refresh()
    }

    fun refresh() {
        page = 1
        hasMore = true
        refreshing = true
        load(reset = true)
    }

    fun loadMore() {
        if (loading || !hasMore || tab == HomeTab.Ranking) return
        page += 1
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        val current = tab
        loading = true
        error = null
        viewModelScope.launch {
            val result = when (current) {
                HomeTab.Recommend -> AppGraph.repo.recommend(page)
                HomeTab.Popular -> AppGraph.repo.popular(page)
                HomeTab.Ranking -> AppGraph.repo.ranking()
                HomeTab.Live -> AppGraph.repo.liveList(page)
            }
            if (tab != current) return@launch
            loading = false
            refreshing = false
            result.onSuccess { list ->
                val merged = if (reset) list else (items + list).distinctBy { it.stableKey }
                val grew = reset || merged.size > items.size
                items = merged
                hasMore = when (current) {
                    HomeTab.Ranking -> false
                    HomeTab.Recommend -> list.isNotEmpty() && grew
                    else -> list.isNotEmpty()
                }
                if (list.isEmpty() && reset) error = null
            }.onFailure {
                if (reset) items = emptyList()
                error = it.message ?: "加载失败"
                hasMore = false
                refreshing = false
            }
        }
    }
}

@Composable
fun HomeRoute(
    onVideo: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onSearch: () -> Unit,
    onMessages: () -> Unit,
    vm: HomeViewModel = appViewModel { HomeViewModel() },
) {
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val settings by AppGraph.repo.settings.collectAsState()
    val greeting = remember { greetingNow() }
    val away by remember {
        derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 240 }
    }
    val navBottom = shellNavigationBottom()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                BiliPageTitle("首页")
                Text(
                    greeting.first,
                    modifier = Modifier.padding(start = 14.dp, bottom = 6.dp),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                )
                Text(
                    greeting.second,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                )
            }
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Row(
                    Modifier
                        .width(minOf(maxWidth, 440.dp))
                        .align(Alignment.Center)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .clickable(onClick = onSearch)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "搜索你感兴趣的视频",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 14.sp,
                    )
                    Icon(Icons.Filled.Search, contentDescription = "搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            BiliUnderlineTabs(
                labels = HomeTab.entries.map { it.label },
                selected = vm.tab.ordinal,
                onSelect = { vm.select(HomeTab.entries[it]) },
            )
            var filterOpen by remember { mutableStateOf(false) }
            if (!filterOpen && !ContentFilter.active) {
                TextButton(onClick = { filterOpen = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("屏蔽", color = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { filterOpen = !filterOpen }) {
                        Text(if (ContentFilter.active) "屏蔽中" else "屏蔽", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        ContentFilter.summary(),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                    if (ContentFilter.active) {
                        TextButton(onClick = {
                            ContentFilter.clear()
                            filterOpen = false
                        }) {
                            Text("清除", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            if (filterOpen) {
                OutlinedTextField(
                    value = ContentFilter.title,
                    onValueChange = { ContentFilter.title = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true,
                    label = { Text("屏蔽标题，多个词用空格或逗号分开") },
                )
                OutlinedTextField(
                    value = ContentFilter.up,
                    onValueChange = { ContentFilter.up = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    label = { Text("屏蔽 UP 主") },
                )
            }
            val shown = vm.items.filter { ContentFilter.allows(it) }
            val blockedOut = vm.items.isNotEmpty() && shown.isEmpty() && ContentFilter.active
            PullToRefreshBox(
                isRefreshing = vm.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LoadBox(vm.loading, vm.error, shown.isEmpty() && !blockedOut, "这里还没有内容", vm::refresh) {
                    if (blockedOut) {
                        Text(
                            "这些内容已被屏蔽",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else VideoGrid(
                        items = shown,
                        onClick = onVideo,
                        onAuthor = onAuthor,
                        state = grid,
                        onLoadMore = if (vm.hasMore) vm::loadMore else null,
                        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp + navBottom),
                    )
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .shellBottomPadding()
                .padding(end = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BiliFloatButton(
                icon = Icons.Filled.KeyboardArrowUp,
                label = "回到顶部",
                enabled = away,
                onClick = { scope.launch { grid.scrollToItem(0) } },
            )
            BiliFloatButton(Icons.Filled.Refresh, "刷新", onClick = vm::refresh)
            BiliFloatButton(Icons.Filled.WbSunny, "切换主题") {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                val next = when (settings.theme) {
                    ThemeMode.System -> ThemeMode.Light
                    ThemeMode.Light -> ThemeMode.Dark
                    ThemeMode.Dark -> ThemeMode.System
                }
                AppGraph.repo.updateSettings { it.copy(theme = next) }
            }
            BiliFloatButton(Icons.AutoMirrored.Filled.Message, "消息", onClick = onMessages)
        }
    }
}

private fun greetingNow(): Pair<String, String> {
    val hour = app.bilidesk.core.model.currentHourOfDay()
    val (text, faces) = when (hour) {
        in 5..10 -> "早上好" to arrayOf("(｡･ω･｡)", "(＾▽＾)", "(・ω・)")
        in 11..12 -> "中午好" to arrayOf("(￣▽￣)", "(＾ω＾)", "(・ω・)")
        in 13..17 -> "下午好" to arrayOf("(´・ω・`)", "(・ω・)ノ", "(￣ω￣)")
        in 18..22 -> "晚上好" to arrayOf("(￣▽￣)ノ", "(・ω・)ﾉ", "(＾▽＾)")
        else -> "夜深了" to arrayOf("(。-ω-)zzz", "(-ω-)zZ", "(´-ω-`)")
    }
    return text to faces.random()
}
