package app.bilidesk.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.bilidesk.core.designsystem.BiliBackHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bilidesk.core.data.AppGraph
import app.bilidesk.core.designsystem.LoadBox
import app.bilidesk.core.designsystem.VideoGrid
import app.bilidesk.core.designsystem.appViewModel
import app.bilidesk.core.model.ContentKind
import app.bilidesk.core.model.UrlHit
import app.bilidesk.core.model.UrlParser
import app.bilidesk.core.model.VideoItem
import kotlinx.coroutines.launch

class SearchViewModel(initial: String) : ViewModel() {
    var keyword by mutableStateOf(initial)
    var live by mutableStateOf(false)
    var items by mutableStateOf<List<VideoItem>>(emptyList())
    var hot by mutableStateOf<List<String>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var page by mutableIntStateOf(1)
    var hasMore by mutableStateOf(false)
    var searched by mutableStateOf(initial.isNotBlank())

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

    fun loadMore() {
        if (loading || !hasMore) return
        page += 1
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        loading = true
        error = null
        val q = keyword
        val kind = if (live) ContentKind.Live else ContentKind.Video
        viewModelScope.launch {
            val result = AppGraph.repo.search(q, page, kind)
            loading = false
            result.onSuccess { pageData ->
                items = if (reset) pageData.items else items + pageData.items
                hasMore = pageData.hasMore
                if (pageData.blocked) error = "搜索被风控拦截，过一会儿再试"
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
    LaunchedEffect(vm.live) { if (vm.searched) vm.search() }
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
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !vm.live, onClick = { vm.live = false }, label = { Text("视频") })
                FilterChip(selected = vm.live, onClick = { vm.live = true }, label = { Text("直播") })
            }
            if (!vm.searched) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                LoadBox(vm.loading, vm.error, vm.items.isEmpty(), "没有找到相关内容", { vm.search() }) {
                    VideoGrid(vm.items, onVideo, onAuthor, onLoadMore = if (vm.hasMore) vm::loadMore else null)
                }
            }
        }
    }
}
