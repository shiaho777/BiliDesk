package app.bilidesk.feature.dynamic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bilidesk.core.data.AppGraph
import app.bilidesk.core.designsystem.BiliPageTitle
import app.bilidesk.core.designsystem.LoadBox
import app.bilidesk.core.designsystem.VideoGrid
import app.bilidesk.core.designsystem.appViewModel
import app.bilidesk.core.designsystem.shellNavigationBottom
import app.bilidesk.core.model.FollowUser
import app.bilidesk.core.model.VideoItem
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

class DynamicViewModel : ViewModel() {
    var users by mutableStateOf<List<FollowUser>>(emptyList())
    var usersHasMore by mutableStateOf(false)
    var selected by mutableStateOf<FollowUser?>(null)
    var items by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hasMore by mutableStateOf(false)
    private var usersPage by mutableIntStateOf(1)
    private var usersLoading = false
    private var offset: String? = null
    private var upPage = 1
    private var selfMid = 0L
    private var ticket = 0
    private val seen = mutableSetOf<String>()

    fun refresh() {
        if (!AppGraph.repo.session.value.loggedIn) {
            users = emptyList()
            items = emptyList()
            error = "请先登录后查看动态"
            return
        }
        viewModelScope.launch {
            val mid = AppGraph.repo.session.value.dedeUserId?.toLongOrNull()
                ?: AppGraph.repo.userState().getOrNull()?.mid
                ?: 0L
            if (mid <= 0) {
                error = "请先登录后查看动态"
                return@launch
            }
            selfMid = mid
            AppGraph.repo.followings(mid, 1).onSuccess { page ->
                users = page.items
                usersPage = 1
                usersHasMore = page.hasMore
                if (selected != null && page.items.none { it.mid == selected?.mid }) selected = null
            }
            loadFeed(reset = true)
        }
    }

    fun selectAll() {
        if (selected == null) return
        selected = null
        loadFeed(reset = true)
    }

    fun select(user: FollowUser) {
        if (selected?.mid == user.mid) return
        selected = user
        loadFeed(reset = true)
    }

    fun loadMoreUsers() {
        if (usersLoading || !usersHasMore || selfMid <= 0) return
        val next = usersPage + 1
        usersLoading = true
        viewModelScope.launch {
            AppGraph.repo.followings(selfMid, next).onSuccess { page ->
                usersPage = next
                val known = users.map { it.mid }.toSet()
                users = users + page.items.filter { it.mid !in known }
                usersHasMore = page.hasMore
            }
            usersLoading = false
        }
    }

    fun loadMore() {
        if (loading || !hasMore) return
        loadFeed(reset = false)
    }

    private fun loadFeed(reset: Boolean) {
        val current = if (reset) ++ticket else ticket
        val up = selected
        if (!reset && !hasMore) return
        loading = true
        if (reset) {
            error = null
            items = emptyList()
            seen.clear()
            offset = null
            upPage = 1
            hasMore = false
        }
        viewModelScope.launch {
            if (up == null) {
                val result = AppGraph.repo.dynamics(if (reset) null else offset)
                if (current != ticket) return@launch
                loading = false
                result.onSuccess { page ->
                    val previous = offset
                    offset = page.nextOffset
                    val fresh = page.items.filter { it.bvid.isNotBlank() && seen.add(it.bvid) }
                    items = if (reset) fresh else items + fresh
                    hasMore = !page.nextOffset.isNullOrBlank() && page.nextOffset != previous
                    if (items.isEmpty()) error = "关注的 UP 最近没有视频投稿"
                }.onFailure {
                    if (items.isEmpty()) error = it.message
                    hasMore = false
                }
            } else {
                val page = if (reset) 1 else upPage
                val result = AppGraph.repo.spaceVideos(up.mid, page)
                if (current != ticket) return@launch
                loading = false
                result.onSuccess { list ->
                    upPage = page + 1
                    val fresh = list.filter { it.bvid.isNotBlank() && seen.add(it.bvid) }
                    items = if (reset) fresh else items + fresh
                    hasMore = list.size >= 30
                    if (items.isEmpty()) error = "该 UP 暂时没有投稿视频"
                }.onFailure {
                    if (items.isEmpty()) error = it.message
                    hasMore = false
                }
            }
        }
    }
}

@Composable
fun DynamicRoute(
    onVideo: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onLogin: () -> Unit,
    vm: DynamicViewModel = appViewModel { DynamicViewModel() },
) {
    val session by AppGraph.repo.session.collectAsState()
    val navBottom = shellNavigationBottom()
    val haptic = LocalHapticFeedback.current
    val userRow = rememberLazyListState()
    LaunchedEffect(session.loggedIn) { vm.refresh() }
    LaunchedEffect(userRow, vm.users.size, vm.usersHasMore) {
        snapshotFlow { userRow.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                val total = userRow.layoutInfo.totalItemsCount
                if (total > 0 && last >= total - 3) vm.loadMoreUsers()
            }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BiliPageTitle("动态", Modifier.weight(1f))
            IconButton(onClick = vm::refresh, enabled = session.loggedIn) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
        }
        if (!session.loggedIn) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "登录后可查看你关注的 UP 主的动态",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                )
                Button(onClick = onLogin) { Text("登录哔哩哔哩") }
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 720.dp && vm.users.isNotEmpty()
            Row(Modifier.fillMaxSize()) {
            if (wide) {
                LazyColumn(Modifier.width(220.dp).fillMaxHeight().padding(8.dp)) {
                    item {
                        FilterChip(
                            selected = vm.selected == null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                vm.selectAll()
                            },
                            label = { Text("全部动态", color = MaterialTheme.colorScheme.onSurface) },
                        )
                    }
                    items(vm.users, key = { it.mid }) { user ->
                        FilterChip(
                            selected = vm.selected?.mid == user.mid,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                vm.select(user)
                            },
                            label = { Text(user.name, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) },
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
            if (!wide && (vm.users.isNotEmpty() || vm.selected != null)) {
                LazyRow(
                    state = userRow,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "all") {
                        FilterChip(
                            selected = vm.selected == null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                vm.selectAll()
                            },
                            label = { Text("全部", color = MaterialTheme.colorScheme.onSurface) },
                        )
                    }
                    items(vm.users, key = { it.mid }) { user ->
                        FilterChip(
                            selected = vm.selected?.mid == user.mid,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                vm.select(user)
                            },
                            leadingIcon = {
                                if (user.face.isNotBlank()) {
                                    AsyncImage(
                                        user.face,
                                        user.name,
                                        Modifier.size(22.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            },
                            label = {
                                Text(
                                    user.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (vm.selected?.mid == user.mid) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (vm.selected == null) "全部动态" else "${vm.selected?.name} 的视频",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val picked = vm.selected
                    if (picked != null) {
                        Text(
                            "主页",
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onAuthor(VideoItem(ownerMid = picked.mid, author = picked.name))
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            PullToRefreshBox(
                isRefreshing = vm.loading && vm.items.isNotEmpty(),
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LoadBox(vm.loading, vm.error, vm.items.isEmpty(), "关注的 UP 还没有新动态", vm::refresh) {
                    VideoGrid(
                        vm.items,
                        onVideo,
                        onAuthor,
                        onLoadMore = if (vm.hasMore) vm::loadMore else null,
                        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 16.dp + navBottom),
                    )
                }
            }
            }
            }
            }
        }
    }
}
