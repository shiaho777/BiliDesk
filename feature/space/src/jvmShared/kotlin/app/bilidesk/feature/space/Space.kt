package app.bilidesk.feature.space

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bilidesk.core.data.AppGraph
import app.bilidesk.core.designsystem.AvatarPreviewDialog
import app.bilidesk.core.designsystem.BiliBackHeader
import app.bilidesk.core.designsystem.VideoCard
import app.bilidesk.core.designsystem.appViewModel
import app.bilidesk.core.model.FavFolder
import app.bilidesk.core.model.SpaceCollection
import app.bilidesk.core.model.SpaceInfo
import app.bilidesk.core.model.SpacePost
import app.bilidesk.core.model.VideoItem
import app.bilidesk.core.model.formatCount
import coil3.compose.AsyncImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val PageSize = 30

enum class SpaceSection(val label: String) {
    Home("主页"),
    Dynamic("动态"),
    Videos("投稿"),
    Collections("合集"),
    Favs("收藏"),
}

class SpaceViewModel(private val mid: Long) : ViewModel() {
    var info by mutableStateOf<SpaceInfo?>(null)
    var section by mutableStateOf(SpaceSection.Home)
    var items by mutableStateOf<List<VideoItem>>(emptyList())
    var videosReady by mutableStateOf(false)
    var videoError by mutableStateOf<String?>(null)
    var posts by mutableStateOf<List<SpacePost>?>(null)
    var postError by mutableStateOf<String?>(null)
    var postOffset by mutableStateOf<String?>(null)
    var postHasMore by mutableStateOf(false)
    var postLoadingMore by mutableStateOf(false)
    var collections by mutableStateOf<List<SpaceCollection>?>(null)
    var collectionError by mutableStateOf<String?>(null)
    var opened by mutableStateOf<SpaceCollection?>(null)
    var collectionVideos by mutableStateOf<List<VideoItem>>(emptyList())
    var collectionPage by mutableStateOf(1)
    var collectionHasMore by mutableStateOf(false)
    var collectionLoading by mutableStateOf(false)
    var folders by mutableStateOf<List<FavFolder>?>(null)
    var folderError by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var refreshing by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var followed by mutableStateOf(false)
    var followBusy by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    var page by mutableStateOf(1)
    var hasMore by mutableStateOf(true)
    var order by mutableStateOf("pubdate")
    var collectionListPage by mutableStateOf(1)
    var collectionsHasMore by mutableStateOf(false)
    var collectionsLoadingMore by mutableStateOf(false)
    var folderPage by mutableStateOf(1)
    var foldersHasMore by mutableStateOf(false)
    var foldersLoadingMore by mutableStateOf(false)
    private val enriched = mutableSetOf<String>()

    init { reload() }

    fun ownerName(): String = info?.name.orEmpty()

    fun dismissToast() {
        toast = null
    }

    fun select(next: SpaceSection) {
        if (next != SpaceSection.Collections) opened = null
        section = next
    }

    fun showCollection(item: SpaceCollection) {
        section = SpaceSection.Collections
        opened = item
        collectionVideos = item.videos
        enrich(item.videos)
        collectionPage = 1
        collectionHasMore = item.count > item.videos.size
        if (item.videos.isEmpty() || item.count > item.videos.size) {
            collectionPage = 0
            collectionHasMore = true
            loadMoreCollection()
        }
    }

    fun closeCollection() {
        opened = null
    }

    fun reload() {
        if (refreshing || (loading && info == null)) return
        val first = info == null
        if (first) loading = true else refreshing = true
        enriched.clear()
        viewModelScope.launch {
            coroutineScope {
                launch {
                    AppGraph.repo.space(mid).onSuccess { info = it }.onFailure { if (info == null) error = it.message }
                }
                launch {
                    AppGraph.repo.spaceVideos(mid, 1, order).onSuccess {
                        items = it
                        page = 1
                        hasMore = it.size >= PageSize
                        videoError = null
                        enrich(it)
                    }.onFailure { videoError = it.message ?: "投稿加载失败" }
                    videosReady = true
                }
                launch {
                    AppGraph.repo.spaceCollections(mid, 1).onSuccess {
                        collections = it.items
                        collectionListPage = 1
                        collectionsHasMore = it.hasMore
                        collectionError = null
                    }.onFailure {
                        if (collections == null) collections = emptyList()
                        collectionError = it.message
                    }
                }
                launch {
                    AppGraph.repo.spaceFolders(mid, 1).onSuccess {
                        folders = it.items
                        folderPage = 1
                        foldersHasMore = it.hasMore
                        folderError = null
                    }.onFailure {
                        if (folders == null) folders = emptyList()
                        folderError = it.message
                    }
                }
                launch {
                    AppGraph.repo.spaceDynamics(mid, null).onSuccess { feed ->
                        posts = feed.posts
                        postOffset = feed.nextOffset
                        postHasMore = feed.hasMore && feed.posts.isNotEmpty()
                        postError = null
                        enrich(feed.posts.mapNotNull { it.video })
                    }.onFailure {
                        if (posts == null) posts = emptyList()
                        postError = it.message
                    }
                }
                launch {
                    val mine = AppGraph.repo.session.value.dedeUserId?.toLongOrNull()
                    if (AppGraph.repo.session.value.loggedIn && mine != mid) {
                        followed = AppGraph.repo.relation(mid).getOrDefault(followed)
                    }
                }
            }
            loading = false
            refreshing = false
        }
    }

    fun loadMore() {
        if (loadingMore || refreshing || loading || !hasMore || items.isEmpty()) return
        val next = page + 1
        loadingMore = true
        viewModelScope.launch {
            AppGraph.repo.spaceVideos(mid, next, order).onSuccess { pageItems ->
                page = next
                val known = items.map { it.bvid }.toSet()
                val fresh = pageItems.filter { it.bvid !in known }
                items = items + fresh
                hasMore = pageItems.size >= PageSize
                enrich(fresh)
            }.onFailure {
                toast = it.message ?: "投稿加载失败"
            }
            loadingMore = false
        }
    }

    fun loadMorePosts() {
        if (postLoadingMore || posts == null || !postHasMore) return
        postLoadingMore = true
        val offset = postOffset
        viewModelScope.launch {
            AppGraph.repo.spaceDynamics(mid, offset).onSuccess { feed ->
                val known = posts.orEmpty().map { it.id }.toSet()
                val fresh = feed.posts.filter { it.id !in known }
                posts = posts.orEmpty() + fresh
                postOffset = feed.nextOffset
                postHasMore = feed.hasMore && feed.posts.isNotEmpty()
                enrich(fresh.mapNotNull { it.video })
            }.onFailure {
                toast = it.message ?: "动态加载失败"
            }
            postLoadingMore = false
        }
    }

    fun loadMoreCollection() {
        val current = opened ?: return
        if (collectionLoading || !collectionHasMore) return
        val next = collectionPage + 1
        collectionLoading = true
        viewModelScope.launch {
            AppGraph.repo.spaceCollectionVideos(mid, current.id, current.series, next).onSuccess { pageItems ->
                collectionPage = next
                collectionVideos = if (next == 1) {
                    pageItems
                } else {
                    val known = collectionVideos.map { it.bvid }.toSet()
                    collectionVideos + pageItems.filter { it.bvid !in known }
                }
                collectionHasMore = pageItems.size >= PageSize
                enrich(pageItems)
            }.onFailure {
                toast = it.message ?: "合集加载失败"
            }
            collectionLoading = false
        }
    }

    private fun enrich(videos: List<VideoItem>) {
        val todo = videos.map { it.bvid }.filter { it.isNotBlank() && it !in enriched }.distinct()
        if (todo.isEmpty()) return
        viewModelScope.launch {
            val gate = Semaphore(4)
            coroutineScope {
                todo.forEach { bvid ->
                    launch {
                        gate.withPermit {
                            AppGraph.repo.videoStats(bvid).onSuccess { (view, danmaku, like) ->
                                enriched += bvid
                                applyStat(bvid, view, danmaku, like)
                            }.onFailure {
                                enriched += bvid
                                applyStat(bvid, 0, 0, 0)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun applyStat(bvid: String, view: Long, danmaku: Long, like: Long) {
        fun VideoItem.patched() = if (this.bvid != bvid) {
            this
        } else {
            copy(
                viewCount = view.takeIf { it > 0 } ?: viewCount,
                danmakuCount = danmaku,
                likeCount = like,
                statsReady = true,
            )
        }
        items = items.map { it.patched() }
        collectionVideos = collectionVideos.map { it.patched() }
        posts = posts?.map { post -> post.copy(video = post.video?.patched()) }
        collections = collections?.map { collection -> collection.copy(videos = collection.videos.map { it.patched() }) }
    }

    fun chooseOrder(next: String) {
        if (next == order) return
        val previous = order
        order = next
        viewModelScope.launch {
            videosReady = false
            AppGraph.repo.spaceVideos(mid, 1, next).onSuccess {
                items = it
                page = 1
                hasMore = it.size >= PageSize
                videoError = null
                enrich(it)
            }.onFailure {
                order = previous
                toast = it.message ?: "这个排序暂时拿不到"
            }
            videosReady = true
        }
    }

    fun loadMoreCollections() {
        if (collectionsLoadingMore || !collectionsHasMore || collections == null || opened != null) return
        val next = collectionListPage + 1
        collectionsLoadingMore = true
        viewModelScope.launch {
            AppGraph.repo.spaceCollections(mid, next).onSuccess { page ->
                collectionListPage = next
                val known = collections.orEmpty().map { it.series to it.id }.toSet()
                collections = collections.orEmpty() + page.items.filter { (it.series to it.id) !in known }
                collectionsHasMore = page.hasMore
            }.onFailure { toast = it.message ?: "合集加载失败" }
            collectionsLoadingMore = false
        }
    }

    fun loadMoreFolders() {
        if (foldersLoadingMore || !foldersHasMore || folders == null) return
        val next = folderPage + 1
        foldersLoadingMore = true
        viewModelScope.launch {
            AppGraph.repo.spaceFolders(mid, next).onSuccess { page ->
                folderPage = next
                val known = folders.orEmpty().map { it.id }.toSet()
                folders = folders.orEmpty() + page.items.filter { it.id !in known }
                foldersHasMore = page.hasMore
            }.onFailure { toast = it.message ?: "收藏夹加载失败" }
            foldersLoadingMore = false
        }
    }

    fun toggleFollow() {
        if (followBusy) return
        if (!AppGraph.repo.session.value.loggedIn) {
            toast = "关注需要先登录"
            return
        }
        followBusy = true
        viewModelScope.launch {
            val next = !followed
            AppGraph.repo.follow(mid, next).onSuccess {
                followed = next
                toast = if (next) "已关注" else "已取消关注"
            }.onFailure {
                toast = it.message ?: "操作失败"
            }
            followBusy = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceRoute(
    mid: Long,
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onOpenFolder: (FavFolder) -> Unit,
    vm: SpaceViewModel = appViewModel(key = "space-$mid") { SpaceViewModel(mid) },
) {
    val snackbar = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    val session by AppGraph.repo.session.collectAsState()
    val self = session.dedeUserId?.toLongOrNull() == mid
    var avatarPreview by remember { mutableStateOf<String?>(null) }
    avatarPreview?.let { face ->
        AvatarPreviewDialog(
            url = face,
            name = vm.info?.name.orEmpty(),
            onDismiss = { avatarPreview = null },
            onSaved = { vm.toast = it },
        )
    }
    LaunchedEffect(vm.toast) {
        val message = vm.toast ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        if (vm.toast == message) vm.dismissToast()
    }
    LaunchedEffect(gridState, vm.section, vm.items.size, vm.posts?.size, vm.collectionVideos.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                val total = gridState.layoutInfo.totalItemsCount
                if (total <= 0 || last < total - 4) return@collect
                when (vm.section) {
                    SpaceSection.Videos -> vm.loadMore()
                    SpaceSection.Dynamic -> vm.loadMorePosts()
                    SpaceSection.Collections -> if (vm.opened != null) vm.loadMoreCollection() else vm.loadMoreCollections()
                    SpaceSection.Favs -> vm.loadMoreFolders()
                    else -> Unit
                }
            }
    }
    Scaffold(
        topBar = { BiliBackHeader(vm.info?.name ?: "UP 主", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = vm::reload,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                vm.loading && vm.info == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
                vm.info == null && vm.items.isEmpty() && !vm.loading -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(vm.error ?: "空间信息获取失败", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                        Button(onClick = vm::reload) { Text("重试") }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(168.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "profile", span = { GridItemSpan(maxLineSpan) }) {
                            ProfileHeader(
                                info = vm.info,
                                videoCount = vm.info?.videoCount?.takeIf { it > 0 } ?: vm.items.size.toLong(),
                                followed = vm.followed,
                                followBusy = vm.followBusy,
                                showFollow = !self,
                                onFollow = vm::toggleFollow,
                                onAvatar = { avatarPreview = it },
                            )
                        }
                        item(key = "tabs", span = { GridItemSpan(maxLineSpan) }) {
                            PrimaryScrollableTabRow(
                                selectedTabIndex = vm.section.ordinal,
                                edgePadding = 0.dp,
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ) {
                                SpaceSection.entries.forEach { section ->
                                    Tab(
                                        selected = vm.section == section,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                            vm.select(section)
                                        },
                                        selectedContentColor = MaterialTheme.colorScheme.onSurface,
                                        unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                                        text = {
                                            Text(
                                                section.label,
                                                fontSize = 15.sp,
                                                fontWeight = if (vm.section == section) FontWeight.SemiBold else FontWeight.Normal,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        when (vm.section) {
                            SpaceSection.Home -> homeSections(vm, onVideo, onOpenFolder)
                            SpaceSection.Dynamic -> dynamicSections(vm, onVideo)
                            SpaceSection.Videos -> videoSections(vm, onVideo)
                            SpaceSection.Collections -> collectionSections(vm, onVideo)
                            SpaceSection.Favs -> folderSections(vm, onOpenFolder)
                        }
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.homeSections(
    vm: SpaceViewModel,
    onVideo: (VideoItem) -> Unit,
    onOpenFolder: (FavFolder) -> Unit,
) {
    val posts = vm.posts
    val collections = vm.collections.orEmpty()
    val folders = vm.folders.orEmpty()
    if (!posts.isNullOrEmpty()) {
        item(key = "home-post-title", span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader("最近动态", "全部") { vm.select(SpaceSection.Dynamic) }
        }
        item(key = "home-post", span = { GridItemSpan(maxLineSpan) }) {
            PostCard(posts.first(), vm.ownerName(), onVideo)
        }
    }
    if (collections.isNotEmpty()) {
        item(key = "home-col-title", span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader("合集", "全部") {
                vm.closeCollection()
                vm.select(SpaceSection.Collections)
            }
        }
        item(key = "home-col-row", span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(collections, key = { it.id.toString() + it.series }) { item ->
                    CollectionCard(item, Modifier.width(168.dp)) { vm.showCollection(item) }
                }
            }
        }
    }
    item(key = "home-video-title", span = { GridItemSpan(maxLineSpan) }) {
        SectionHeader(
            title = "最新投稿",
            action = if (vm.items.size > 4) "全部" else null,
            onAction = { vm.select(SpaceSection.Videos) },
        )
    }
    when {
        !vm.videosReady -> item(key = "home-video-loading", span = { GridItemSpan(maxLineSpan) }) { CenterProgress() }
        vm.items.isEmpty() -> item(key = "home-video-empty", span = { GridItemSpan(maxLineSpan) }) {
            EmptyLine(vm.videoError ?: "该 UP 主暂无投稿", onRetry = if (vm.videoError != null) vm::reload else null)
        }
        else -> gridItems(vm.items.take(4), key = { "home-${it.stableKey}" }) { item ->
            VideoCard(item.withOwner(vm.ownerName()), onVideo, onAuthor = {}, Modifier.animateItem())
        }
    }
    if (folders.isNotEmpty()) {
        item(key = "home-fav-title", span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader("收藏夹", "全部") { vm.select(SpaceSection.Favs) }
        }
        folders.take(4).forEach { folder ->
            item(key = "home-fav-${folder.id}", span = { GridItemSpan(maxLineSpan) }) {
                FolderRow(folder) { onOpenFolder(folder) }
            }
        }
    }
}

private fun LazyGridScope.dynamicSections(vm: SpaceViewModel, onVideo: (VideoItem) -> Unit) {
    val posts = vm.posts
    when {
        posts == null -> item(key = "dyn-loading", span = { GridItemSpan(maxLineSpan) }) { CenterProgress() }
        posts.isEmpty() -> item(key = "dyn-empty", span = { GridItemSpan(maxLineSpan) }) {
            EmptyLine(vm.postError ?: "还没有动态", onRetry = if (vm.postError != null) vm::reload else null)
        }
        else -> {
            posts.forEach { post ->
                item(key = "dyn-${post.id}", span = { GridItemSpan(maxLineSpan) }) {
                    PostCard(post, vm.ownerName(), onVideo)
                }
            }
            if (vm.postLoadingMore) {
                item(key = "dyn-more", span = { GridItemSpan(maxLineSpan) }) { CenterProgress() }
            } else if (!vm.postHasMore) {
                item(key = "dyn-end", span = { GridItemSpan(maxLineSpan) }) { EndLine() }
            }
        }
    }
}

private fun LazyGridScope.videoSections(vm: SpaceViewModel, onVideo: (VideoItem) -> Unit) {
    item(key = "order", span = { GridItemSpan(maxLineSpan) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("pubdate" to "最新", "click" to "最多播放", "stow" to "最多收藏").forEach { (id, label) ->
                FilterChip(
                    selected = vm.order == id,
                    onClick = { vm.chooseOrder(id) },
                    label = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
                )
            }
        }
    }
    when {
        !vm.videosReady -> item(key = "video-loading", span = { GridItemSpan(maxLineSpan) }) { CenterProgress() }
        vm.items.isEmpty() -> item(key = "video-empty", span = { GridItemSpan(maxLineSpan) }) {
            EmptyLine(vm.videoError ?: "该 UP 主暂无投稿", onRetry = if (vm.videoError != null) vm::reload else null)
        }
        else -> {
            gridItems(vm.items, key = { it.stableKey + it.title }) { item ->
                VideoCard(item.withOwner(vm.ownerName()), onVideo, onAuthor = {}, Modifier.animateItem())
            }
            if (vm.loadingMore) {
                item(key = "video-more", span = { GridItemSpan(maxLineSpan) }) { CenterProgress() }
            } else if (!vm.hasMore) {
                item(key = "video-end", span = { GridItemSpan(maxLineSpan) }) { EndLine() }
            }
        }
    }
}

private fun LazyGridScope.collectionSections(vm: SpaceViewModel, onVideo: (VideoItem) -> Unit) {
    val opened = vm.opened
    if (opened != null) {
        item(key = "col-back", span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(opened.title, "全部合集", vm::closeCollection)
        }
        if (vm.collectionVideos.isEmpty() && vm.collectionLoading) {
            item(key = "col-loading", span = { GridItemSpan(maxLineSpan) }) { CenterProgress() }
        } else if (vm.collectionVideos.isEmpty()) {
            item(key = "col-empty", span = { GridItemSpan(maxLineSpan) }) { EmptyLine("这个合集还没有视频") }
        } else {
            gridItems(vm.collectionVideos, key = { "col-${it.stableKey}" }) { item ->
                VideoCard(item.withOwner(vm.ownerName()), onVideo, onAuthor = {}, Modifier.animateItem())
            }
            if (vm.collectionLoading) {
                item(key = "col-more", span = { GridItemSpan(maxLineSpan) }) { CenterProgress() }
            } else if (!vm.collectionHasMore) {
                item(key = "col-end", span = { GridItemSpan(maxLineSpan) }) { EndLine() }
            }
        }
        return
    }
    val collections = vm.collections
    when {
        collections == null -> item(key = "cols-loading", span = { GridItemSpan(maxLineSpan) }) { CenterProgress() }
        collections.isEmpty() -> item(key = "cols-empty", span = { GridItemSpan(maxLineSpan) }) {
            EmptyLine(vm.collectionError ?: "还没有合集", onRetry = if (vm.collectionError != null) vm::reload else null)
        }
        else -> {
            collections.forEach { collection ->
                item(key = "col-${collection.series}-${collection.id}", span = { GridItemSpan(maxLineSpan) }) {
                    CollectionCard(collection, Modifier.fillMaxWidth()) { vm.showCollection(collection) }
                }
            }
            if (vm.collectionsLoadingMore) {
                item(key = "cols-more", span = { GridItemSpan(maxLineSpan) }) { CenterProgress() }
            } else if (!vm.collectionsHasMore) {
                item(key = "cols-end", span = { GridItemSpan(maxLineSpan) }) { EndLine() }
            }
        }
    }
}

private fun LazyGridScope.folderSections(vm: SpaceViewModel, onOpenFolder: (FavFolder) -> Unit) {
    val folders = vm.folders
    when {
        folders == null -> item(key = "fav-loading", span = { GridItemSpan(maxLineSpan) }) { CenterProgress() }
        folders.isEmpty() -> item(key = "fav-empty", span = { GridItemSpan(maxLineSpan) }) {
            EmptyLine(vm.folderError ?: "没有公开的收藏夹", onRetry = if (vm.folderError != null) vm::reload else null)
        }
        else -> {
            folders.forEach { folder ->
                item(key = "fav-${folder.id}", span = { GridItemSpan(maxLineSpan) }) {
                    FolderRow(folder) { onOpenFolder(folder) }
                }
            }
            if (vm.foldersLoadingMore) {
                item(key = "fav-more", span = { GridItemSpan(maxLineSpan) }) { CenterProgress() }
            } else if (!vm.foldersHasMore) {
                item(key = "fav-end", span = { GridItemSpan(maxLineSpan) }) { EndLine() }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String?, onAction: (() -> Unit)? = null) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!action.isNullOrBlank() && onAction != null) {
            Text(
                action,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onAction()
                    }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
            )
        }
    }
}

private fun VideoItem.withOwner(name: String): VideoItem =
    if (author.isBlank() && name.isNotBlank()) copy(author = name) else this

@Composable
private fun PostCard(post: SpacePost, ownerName: String, onVideo: (VideoItem) -> Unit) {
    val video = post.video?.withOwner(ownerName)
    val body = post.text.trim()
    val showText = body.isNotBlank() && body != video?.title
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (post.timeText.isNotBlank()) {
            Text(post.timeText, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        }
        if (showText && video == null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    body,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
        } else if (showText) {
            Text(
                body,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
        }
        if (video != null) {
            VideoCard(video, onVideo, onAuthor = {})
        }
    }
}

@Composable
private fun CollectionCard(item: SpaceCollection, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            if (item.cover.isBlank()) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            } else {
                AsyncImage(
                    item.cover,
                    item.title,
                    Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${item.count} 个视频",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun FolderRow(folder: FavFolder, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (folder.cover.isBlank()) {
                Box(
                    Modifier.width(112.dp).aspectRatio(16f / 10f).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            } else {
                AsyncImage(
                    folder.cover,
                    folder.title,
                    Modifier.width(112.dp).aspectRatio(16f / 10f).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    folder.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${folder.count} 个视频",
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun CenterProgress() {
    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun EmptyLine(text: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, textAlign = TextAlign.Center)
        if (onRetry != null) Button(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun EndLine() {
    Text(
        "已经到底啦",
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ProfileHeader(
    info: SpaceInfo?,
    videoCount: Long,
    followed: Boolean,
    followBusy: Boolean,
    showFollow: Boolean,
    onFollow: () -> Unit,
    onAvatar: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val sign = info?.sign?.takeIf { it.isNotBlank() } ?: "这个 UP 主很懒，什么都没写"
    val level = info?.level ?: 0
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (info?.face.isNullOrBlank()) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    AsyncImage(
                        info.face,
                        info.name,
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable { onAvatar(info.face) },
                        contentScale = ContentScale.Crop,
                    )
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            info?.name?.ifBlank { "UP 主" } ?: "UP 主",
                            modifier = Modifier.weight(1f, fill = false),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (level > 0) {
                            Text(
                                "LV$level",
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Text(
                        sign,
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Stat(formatCount(info?.follower ?: 0).ifBlank { "0" }, "粉丝", Modifier.weight(1f))
                Stat(formatCount(info?.following ?: 0).ifBlank { "0" }, "关注", Modifier.weight(1f))
                Stat(formatCount(videoCount).ifBlank { "0" }, "投稿", Modifier.weight(1f))
            }
            if (showFollow && followed) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onFollow()
                    },
                    enabled = !followBusy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("已关注") }
            } else if (showFollow) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onFollow()
                    },
                    enabled = !followBusy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("+ 关注") }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            label,
            modifier = Modifier.padding(top = 2.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
        )
    }
}
