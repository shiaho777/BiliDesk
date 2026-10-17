package app.bilidesk.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import app.bilidesk.core.designsystem.BiliBackHeader
import app.bilidesk.core.designsystem.ContentFilter
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bilidesk.core.data.AppGraph
import app.bilidesk.core.designsystem.LoadBox
import app.bilidesk.core.designsystem.VideoGrid
import app.bilidesk.core.designsystem.appViewModel
import app.bilidesk.core.model.CacheEntry
import app.bilidesk.core.model.FavFolder
import app.bilidesk.core.model.VideoItem
import kotlinx.coroutines.launch

class ListViewModel(private val loader: suspend () -> Result<List<VideoItem>>) : ViewModel() {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    init { reload() }
    fun reload() {
        loading = true
        error = null
        viewModelScope.launch {
            loader().onSuccess { items = it; loading = false }.onFailure { loading = false; error = it.message }
        }
    }
    fun remove(item: VideoItem) { items = items.filterNot { it.stableKey == item.stableKey } }

    fun delete(item: VideoItem, remote: suspend () -> Unit) {
        remove(item)
        viewModelScope.launch { remote() }
    }

    fun deleteAll(remote: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            remote().onSuccess { items = emptyList() }.onFailure { error = it.message }
        }
    }
}

@Composable
private fun BackTitle(title: String, onBack: () -> Unit) {
    BiliBackHeader(title, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRoute(onBack: () -> Unit, onVideo: (VideoItem) -> Unit, onAuthor: (VideoItem) -> Unit) {
    var live by remember { mutableStateOf(false) }
    val vm = appViewModel(key = if (live) "history-live" else "history-video") {
        ListViewModel { if (live) AppGraph.repo.liveHistory(1) else AppGraph.repo.history(1) }
    }
    var editing by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var confirmClear by remember { mutableStateOf(false) }
    fun drop(item: VideoItem) {
        vm.delete(item) {
            when {
                item.isLive && item.roomId > 0 -> AppGraph.repo.deleteHistory(item.roomId, live = true)
                item.aid > 0 -> AppGraph.repo.deleteHistory(item.aid)
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空观看历史") },
            text = { Text("会删除账号下的全部云端历史，而且不能撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    editing = false
                    picked = emptySet()
                    vm.deleteAll { AppGraph.repo.clearHistory() }
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
    Scaffold(topBar = { BackTitle("历史", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !live,
                    onClick = {
                        live = false
                        editing = false
                        picked = emptySet()
                    },
                    label = { Text("视频") },
                )
                FilterChip(
                    selected = live,
                    onClick = {
                        live = true
                        editing = false
                        picked = emptySet()
                    },
                    label = { Text("直播") },
                )
            }
            val shown = vm.items.filter { ContentFilter.allows(it) }
            EditBar(
                editing = editing,
                allSelected = shown.isNotEmpty() && picked.size == shown.size,
                canDelete = picked.isNotEmpty(),
                onEdit = {
                    editing = !editing
                    if (!editing) picked = emptySet()
                },
                onToggleAll = {
                    picked = if (picked.size == shown.size) emptySet() else shown.map { it.stableKey }.toSet()
                },
                onDelete = {
                    shown.filter { it.stableKey in picked }.forEach(::drop)
                    picked = emptySet()
                },
                onClear = { confirmClear = true },
            )
            LoadBox(
                vm.loading,
                vm.error,
                vm.items.isEmpty(),
                if (live) "云端没有直播观看记录" else "还没有云端历史",
                vm::reload,
            ) {
                if (shown.isEmpty()) {
                    Text(
                        "这些内容已被屏蔽",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.stableKey }) { item ->
                        val state = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    drop(item)
                                    true
                                } else {
                                    false
                                }
                            },
                        )
                        SwipeToDismissBox(
                            state = state,
                            modifier = Modifier.animateItem(),
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Text("删除", color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            },
                        ) {
                            ListItem(
                                headlineContent = { Text(item.title) },
                                supportingContent = {
                                    Text(
                                        listOf(item.author, item.durationText)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · "),
                                    )
                                },
                                leadingContent = if (!editing) null else {
                                    {
                                        Checkbox(
                                            checked = item.stableKey in picked,
                                            onCheckedChange = {
                                                picked = if (item.stableKey in picked) picked - item.stableKey else picked + item.stableKey
                                            },
                                        )
                                    }
                                },
                                modifier = Modifier.clickable {
                                    if (editing) {
                                        picked = if (item.stableKey in picked) picked - item.stableKey else picked + item.stableKey
                                    } else {
                                        onVideo(item)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesRoute(onBack: () -> Unit, onOpen: (FavFolder) -> Unit) {
    var folders by remember { mutableStateOf<List<FavFolder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(attempt) {
        loading = true
        error = null
        AppGraph.repo.favFolders().onSuccess { folders = it; loading = false }.onFailure { error = it.message; loading = false }
    }
    Scaffold(topBar = { BackTitle("收藏", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadBox(loading, error, folders.isEmpty(), "没有收藏夹", { attempt++ }) {
                LazyColumn {
                    items(folders, key = { it.id }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.title) },
                            supportingContent = { Text("${folder.count} 个") },
                            modifier = Modifier.clickable { onOpen(folder) }.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FavVideosRoute(
    mediaId: Long,
    title: String,
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
) {
    val vm = appViewModel(key = "fav-$mediaId") { ListViewModel { AppGraph.repo.favVideos(mediaId, 1) } }
    var editing by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    Scaffold(topBar = { BackTitle(title, onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val shown = vm.items.filter { ContentFilter.allows(it) }
            EditBar(
                editing = editing,
                allSelected = shown.isNotEmpty() && picked.size == shown.size,
                canDelete = picked.isNotEmpty(),
                onEdit = {
                    editing = !editing
                    if (!editing) picked = emptySet()
                },
                onToggleAll = {
                    picked = if (picked.size == shown.size) emptySet() else shown.map { it.stableKey }.toSet()
                },
                onDelete = {
                    val aids = shown.filter { it.stableKey in picked }.map { it.aid }
                    shown.filter { it.stableKey in picked }.forEach { vm.remove(it) }
                    picked = emptySet()
                    vm.viewModelScope.launch {
                        AppGraph.repo.batchRemoveFav(mediaId, aids).onFailure { vm.reload() }
                    }
                },
            )
            LoadBox(vm.loading, vm.error, vm.items.isEmpty(), "这个收藏夹是空的", vm::reload) {
                if (shown.isEmpty()) {
                    Text(
                        "这些内容已被屏蔽",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else if (editing) {
                    LazyColumn {
                        items(shown, key = { it.stableKey }) { item ->
                            ListItem(
                                headlineContent = { Text(item.title) },
                                supportingContent = { Text(item.author) },
                                leadingContent = {
                                    Checkbox(
                                        checked = item.stableKey in picked,
                                        onCheckedChange = {
                                            picked = if (item.stableKey in picked) picked - item.stableKey else picked + item.stableKey
                                        },
                                    )
                                },
                                modifier = Modifier.clickable {
                                    picked = if (item.stableKey in picked) picked - item.stableKey else picked + item.stableKey
                                },
                            )
                        }
                    }
                } else VideoGrid(shown, onVideo, onAuthor)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchLaterRoute(onBack: () -> Unit, onVideo: (VideoItem) -> Unit, onAuthor: (VideoItem) -> Unit) {
    val vm = appViewModel(key = "later") { ListViewModel { AppGraph.repo.watchLater() } }
    var editing by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空稍后再看") },
            text = { Text("会删掉账号里的整个列表，而且不能撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    editing = false
                    picked = emptySet()
                    vm.deleteAll { AppGraph.repo.clearWatchLater() }
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
    Scaffold(topBar = { BackTitle("稍后再看", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val shown = vm.items.filter { ContentFilter.allows(it) }
            EditBar(
                editing = editing,
                allSelected = shown.isNotEmpty() && picked.size == shown.size,
                canDelete = picked.isNotEmpty(),
                onEdit = {
                    editing = !editing
                    if (!editing) picked = emptySet()
                },
                onToggleAll = {
                    picked = if (picked.size == shown.size) emptySet() else shown.map { it.stableKey }.toSet()
                },
                onDelete = {
                    shown.filter { it.stableKey in picked }.forEach { item ->
                        vm.delete(item) { if (item.aid > 0) AppGraph.repo.removeWatchLater(item.aid) }
                    }
                    picked = emptySet()
                },
                onClear = { confirmClear = true },
            )
            LoadBox(vm.loading, vm.error, vm.items.isEmpty(), "稍后再看是空的", vm::reload) {
                if (shown.isEmpty() && ContentFilter.active) {
                    Text("这些内容已被屏蔽", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurface)
                } else LazyColumn {
                    items(shown, key = { it.stableKey }) { item ->
                        ListItem(
                            headlineContent = { Text(item.title) },
                            supportingContent = { Text(item.author) },
                            leadingContent = if (!editing) null else {
                                {
                                    Checkbox(
                                        checked = item.stableKey in picked,
                                        onCheckedChange = {
                                            picked = if (item.stableKey in picked) picked - item.stableKey else picked + item.stableKey
                                        },
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                if (editing) {
                                    picked = if (item.stableKey in picked) picked - item.stableKey else picked + item.stableKey
                                } else {
                                    onVideo(item)
                                }
                            }.animateItem(),
                            trailingContent = if (editing) null else {
                                {
                                    Text(
                                        "移除",
                                        modifier = Modifier.clickable {
                                            vm.delete(item) { if (item.aid > 0) AppGraph.repo.removeWatchLater(item.aid) }
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditBar(
    editing: Boolean,
    allSelected: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onEdit) { Text(if (editing) "完成" else "编辑") }
        if (editing) {
            TextButton(onClick = onToggleAll) { Text(if (allSelected) "取消全选" else "全选") }
            TextButton(onClick = onDelete, enabled = canDelete) { Text("删除所选") }
            if (onClear != null) TextButton(onClick = onClear) { Text("清空") }
        }
    }
}

@Composable
fun CacheRoute(onBack: () -> Unit, onPlay: (CacheEntry) -> Unit) {
    var entries by androidx.compose.runtime.remember { mutableStateOf(AppGraph.cache.list()) }
    Scaffold(topBar = { BackTitle("离线缓存", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (entries.isEmpty()) {
                Text("还没有缓存。播放页可以把当前视频下载到这里。", modifier = Modifier.padding(24.dp))
            } else {
                LazyColumn {
                    items(entries, key = { it.bvid }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.title) },
                            supportingContent = { Text(app.bilidesk.core.model.qnLabel(entry.qn)) },
                            modifier = Modifier.clickable { onPlay(entry) },
                            trailingContent = {
                                Text("删除", modifier = Modifier.clickable {
                                    AppGraph.cache.delete(entry.bvid)
                                    entries = AppGraph.cache.list()
                                })
                            },
                        )
                    }
                }
            }
        }
    }
}
