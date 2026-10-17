package app.bilidesk.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.bilidesk.core.designsystem.BiliBackHeader
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bilidesk.core.data.AppGraph
import app.bilidesk.core.designsystem.LoadBox
import app.bilidesk.core.designsystem.LocalWindowForm
import app.bilidesk.core.designsystem.VideoGrid
import app.bilidesk.core.designsystem.appViewModel
import app.bilidesk.core.model.BangumiItem
import app.bilidesk.core.model.ContentKind
import app.bilidesk.core.model.UrlHit
import app.bilidesk.core.model.UrlParser
import app.bilidesk.core.model.VideoItem
import app.bilidesk.core.model.searchEmptyMessage
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

class SearchViewModel(initial: String) : ViewModel() {
    var keyword by mutableStateOf(initial)
    var kind by mutableStateOf(AppGraph.repo.settings.value.lastSearchKind)
    var items by mutableStateOf<List<VideoItem>>(emptyList())
    var bangumiItems by mutableStateOf<List<BangumiItem>>(emptyList())
    var hot by mutableStateOf<List<String>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var page by mutableIntStateOf(1)
    var hasMore by mutableStateOf(false)
    var searched by mutableStateOf(initial.isNotBlank())
    private var loadGen = 0

    init {
        val hit = UrlParser.parse(initial)
        if (hit is UrlHit.Keyword && hit.word.isNotBlank()) search(hit.word)
        viewModelScope.launch { hot = AppGraph.repo.hotSearch().getOrDefault(emptyList()) }
    }

    fun search(word: String = keyword) {
        val q = word.trim()
        if (q.isEmpty()) return
        keyword = q
        searched = true
        page = 1
        AppGraph.searchHistory.add(q)
        load(reset = true)
    }

    fun selectKind(next: ContentKind) {
        if (next == kind) return
        AppGraph.repo.updateSettings { it.copy(lastSearchKind = next) }
        kind = next
        items = emptyList()
        bangumiItems = emptyList()
        error = null
        hasMore = false
        if (searched) loading = true
    }

    fun loadMore() {
        if (loading || !hasMore) return
        page += 1
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        val token = ++loadGen
        loading = true
        error = null
        val q = keyword
        val searchKind = kind
        viewModelScope.launch {
            val result = AppGraph.repo.search(q, page, searchKind)
            if (token != loadGen) return@launch
            loading = false
            result.onSuccess { pageData ->
                when (searchKind) {
                    ContentKind.Bangumi -> {
                        bangumiItems = if (reset) {
                            pageData.bangumiItems
                        } else {
                            mergeBangumiBySeason(bangumiItems, pageData.bangumiItems)
                        }
                        if (reset) items = emptyList()
                    }
                    ContentKind.Video, ContentKind.Live -> {
                        items = if (reset) pageData.items else items + pageData.items
                        if (reset) bangumiItems = emptyList()
                    }
                }
                hasMore = pageData.hasMore
                val empty = if (searchKind == ContentKind.Bangumi) {
                    bangumiItems.isEmpty()
                } else {
                    items.isEmpty()
                }
                error = when {
                    pageData.blocked -> "搜索被风控拦截，过一会儿再试"
                    empty -> searchEmptyMessage(searchKind)
                    else -> null
                }
            }.onFailure { error = it.message }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchRoute(
    initial: String,
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onBangumi: (BangumiItem) -> Unit = {},
    vm: SearchViewModel = appViewModel(key = "search-$initial") { SearchViewModel(initial) },
) {
    val history by AppGraph.searchHistory.items.collectAsState()
    fun submit(raw: String) {
        when (val hit = UrlParser.parse(raw)) {
            is UrlHit.Video -> onVideo(VideoItem(bvid = hit.bvid))
            is UrlHit.Space -> onAuthor(VideoItem(ownerMid = hit.mid))
            is UrlHit.Live -> onVideo(VideoItem(roomId = hit.roomId, title = "直播"))
            is UrlHit.Keyword -> vm.search(hit.word)
        }
    }
    LaunchedEffect(initial) {
        val hit = UrlParser.parse(initial)
        if (hit !is UrlHit.Keyword) submit(initial)
    }
    LaunchedEffect(vm.kind) { if (vm.searched) vm.search() }
    Scaffold(
        topBar = {
            BiliBackHeader("搜索", onBack)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = vm.keyword,
                onValueChange = { vm.keyword = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                label = { Text("视频、UP 主、直播，也可以粘贴链接") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(vm.keyword) }),
                trailingIcon = { TextButton(onClick = { submit(vm.keyword) }) { Text("搜索") } },
            )
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = vm.kind == ContentKind.Video,
                    onClick = { vm.selectKind(ContentKind.Video) },
                    label = { Text("视频") },
                )
                FilterChip(
                    selected = vm.kind == ContentKind.Live,
                    onClick = { vm.selectKind(ContentKind.Live) },
                    label = { Text("直播") },
                )
                FilterChip(
                    selected = vm.kind == ContentKind.Bangumi,
                    onClick = { vm.selectKind(ContentKind.Bangumi) },
                    label = { Text("番剧") },
                )
            }
            if (!vm.searched) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        searchIdleHint(vm.kind),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (history.isNotEmpty()) {
                        Text("最近搜索")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            history.forEach { word ->
                                AssistChip(onClick = { vm.search(word) }, label = { Text(word) })
                            }
                        }
                        TextButton(onClick = { AppGraph.searchHistory.clear() }) { Text("清空") }
                    }
                    if (vm.hot.isNotEmpty()) {
                        Text("热搜")
                        vm.hot.forEach { word ->
                            Text(
                                word,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.search(word) }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            } else {
                val empty = if (vm.kind == ContentKind.Bangumi) vm.bangumiItems.isEmpty() else vm.items.isEmpty()
                LoadBox(vm.loading, vm.error, empty, searchEmptyMessage(vm.kind), { vm.search() }) {
                    if (vm.kind == ContentKind.Bangumi) {
                        BangumiGrid(
                            vm.bangumiItems,
                            onBangumi,
                            onLoadMore = if (vm.hasMore) vm::loadMore else null,
                        )
                    } else {
                        VideoGrid(vm.items, onVideo, onAuthor, onLoadMore = if (vm.hasMore) vm::loadMore else null)
                    }
                }
            }
        }
    }
}

@Composable
private fun BangumiGrid(
    items: List<BangumiItem>,
    onClick: (BangumiItem) -> Unit,
    onLoadMore: (() -> Unit)? = null,
) {
    val state = rememberLazyGridState()
    if (onLoadMore != null) {
        LaunchedEffect(state, items.size) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .collect { last ->
                    if (items.isNotEmpty() && last >= items.size - 4) onLoadMore()
                }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(LocalWindowForm.current.gridMin),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.seasonId }) { item ->
            BangumiCard(item, onClick, Modifier.animateItem())
        }
    }
}

@Composable
private fun BangumiCard(
    item: BangumiItem,
    onClick: (BangumiItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.clickable { onClick(item) }) {
            Box {
                AsyncImage(
                    model = item.cover,
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f),
                    contentScale = ContentScale.Crop,
                )
                if (item.badge.isNotBlank()) {
                    Text(
                        item.badge,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.indexShow.isNotBlank()) {
                    Text(
                        item.indexShow,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun searchIdleHint(kind: ContentKind): String = when (kind) {
    ContentKind.Video -> "输入关键词，搜索你想看的视频"
    ContentKind.Live -> "输入关键词，搜索你想看的直播间"
    ContentKind.Bangumi -> "输入关键词，搜索你想看的番剧"
}

private fun mergeBangumiBySeason(
    current: List<BangumiItem>,
    extra: List<BangumiItem>,
): List<BangumiItem> {
    val seen = current.mapTo(mutableSetOf()) { it.seasonId }
    return current + extra.filter { seen.add(it.seasonId) }
}
