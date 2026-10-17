package app.bilidesk.feature.region

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.bilidesk.core.designsystem.BiliBackHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bilidesk.core.data.AppGraph
import app.bilidesk.core.designsystem.LoadBox
import app.bilidesk.core.designsystem.VideoGrid
import app.bilidesk.core.designsystem.appViewModel
import app.bilidesk.core.model.Partition
import app.bilidesk.core.model.PartitionCatalog
import app.bilidesk.core.model.VideoItem
import app.bilidesk.core.model.afterRegionPage
import app.bilidesk.core.network.RegionFeedDecode
import kotlinx.coroutines.launch

class RegionViewModel(private val tid: Int) : ViewModel() {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(true)
    var loadingMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hasMore by mutableStateOf(true)
    var displayId = 1
    val seen: MutableSet<String> = mutableSetOf()
    private var loadGen = 0

    init { reload() }

    fun reload() {
        val token = ++loadGen
        displayId = 1
        seen.clear()
        items = emptyList()
        loading = true
        loadingMore = false
        hasMore = true
        error = null
        viewModelScope.launch {
            val result = AppGraph.repo.regionFeed(tid, 1)
            if (token != loadGen) return@launch
            loading = false
            result.onSuccess { page -> applyPage(page, reset = true) }
                .onFailure { error = it.message }
        }
    }

    fun loadMore() {
        if (loading || loadingMore || !hasMore || items.isEmpty()) return
        val token = loadGen
        loadingMore = true
        viewModelScope.launch {
            val result = AppGraph.repo.regionFeed(tid, displayId)
            if (token != loadGen) return@launch
            loadingMore = false
            result.onSuccess { page -> applyPage(page, reset = false) }
                .onFailure { error = it.message }
        }
    }

    private fun applyPage(page: RegionFeedDecode, reset: Boolean) {
        val fetched = page.items
        val outcome = afterRegionPage(displayId, fetched.map { it.bvid }, seen)
        displayId = outcome.nextDisplayId
        seen.addAll(outcome.addedBvids)
        val added = outcome.addedBvids.toSet()
        items = if (reset) fetched else items + fetched.filter { it.bvid in added }
        hasMore = !page.ended && !outcome.noMore
        error = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionPickerRoute(onPick: (Partition) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            BiliBackHeader("分区", onBack)
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(148.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(PartitionCatalog.all, key = { it.tid }) { part ->
                ListItem(
                    headlineContent = { Text(part.name) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(part.tint), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(part.initial)
                        }
                    },
                    modifier = Modifier.clickable { onPick(part) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionRoute(
    tid: Int,
    name: String,
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    vm: RegionViewModel = appViewModel(key = "region-$tid") { RegionViewModel(tid) },
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            BiliBackHeader(name, onBack)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                LoadBox(
                    vm.loading || vm.loadingMore,
                    if (vm.items.isNotEmpty()) null else vm.error,
                    vm.items.isEmpty(),
                    "这个分区暂时没有内容",
                    vm::reload,
                    Modifier.weight(1f),
                ) {
                    VideoGrid(
                        vm.items,
                        onVideo,
                        onAuthor,
                        onLoadMore = if (vm.hasMore) vm::loadMore else null,
                        state = gridState,
                    )
                }
                val moreError = vm.error
                if (moreError != null && vm.items.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 72.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(moreError, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = vm::loadMore) { Text("重试") }
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallFloatingActionButton(onClick = vm::reload) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
                SmallFloatingActionButton(onClick = { scope.launch { gridState.animateScrollToItem(0) } }) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "回到顶部")
                }
            }
        }
    }
}
