package app.bilidesk

import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.bilidesk.core.designsystem.BiliBottomBar
import app.bilidesk.core.designsystem.BiliChrome
import app.bilidesk.core.designsystem.BiliRail
import app.bilidesk.core.designsystem.BiliRailItem
import app.bilidesk.core.designsystem.BiliShell
import app.bilidesk.core.designsystem.LocalWindowForm
import app.bilidesk.core.designsystem.ProvideWindowForm
import app.bilidesk.core.designsystem.toChrome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import app.bilidesk.core.data.AppGraph
import app.bilidesk.core.data.exitApp
import app.bilidesk.core.model.DISCLAIMER_VERSION
import app.bilidesk.core.model.VideoItem
import app.bilidesk.core.model.bangumiPlayTitle
import app.bilidesk.feature.dynamic.DynamicRoute
import app.bilidesk.feature.home.HomeRoute
import app.bilidesk.feature.library.CacheRoute
import app.bilidesk.feature.library.FavVideosRoute
import app.bilidesk.feature.library.FavoritesRoute
import app.bilidesk.feature.library.HistoryRoute
import app.bilidesk.feature.library.WatchLaterRoute
import app.bilidesk.feature.login.LoginRoute
import app.bilidesk.feature.messages.ChatRoute
import app.bilidesk.feature.messages.MessagesRoute
import app.bilidesk.feature.mine.MineRoute
import app.bilidesk.feature.player.PlayerRoute
import app.bilidesk.feature.region.RegionPickerRoute
import app.bilidesk.feature.region.RegionRoute
import app.bilidesk.feature.search.SearchRoute
import app.bilidesk.feature.search.SeasonRoute
import app.bilidesk.feature.settings.SettingsRoute
import app.bilidesk.feature.space.SpaceRoute

sealed interface Dest {
    data object Home : Dest
    data object Dynamic : Dest
    data object Mine : Dest
    data object Settings : Dest
    data class Search(val keyword: String) : Dest
    data class Bangumi(val seasonId: Long, val title: String) : Dest
    data object Regions : Dest
    data class RegionRank(val tid: Int, val name: String) : Dest
    data class Space(val mid: Long) : Dest
    data object History : Dest
    data object Favorites : Dest
    data class FavList(val id: Long, val title: String) : Dest
    data object WatchLater : Dest
    data object Messages : Dest
    data class Chat(val talkerId: Long, val name: String) : Dest
    data object Cache : Dest
    data object Login : Dest
    data class Play(
        val bvid: String,
        val cid: Long = 0,
        val aid: Long = 0,
        val title: String = "",
        val cover: String = "",
        val author: String = "",
        val ownerMid: Long = 0,
        val durationSec: Int = 0,
        val viewCount: Long = 0,
        val likeCount: Long = 0,
        val danmakuCount: Long = 0,
        val coinCount: Long = 0,
        val favoriteCount: Long = 0,
        val ownerFace: String = "",
        val pubdate: Long = 0,
    ) : Dest
    data class Live(val roomId: Long, val title: String) : Dest
    data class Local(val bvid: String) : Dest
}

enum class NavKind { Push, Pop, Tab }

class ShellViewModel(private val saved: SavedStateHandle) : ViewModel() {
    var stack by mutableStateOf(decodeStack(saved.get<String>(KEY) ?: "home"))
        private set
    var kind by mutableStateOf(NavKind.Tab)
        private set

    val current: Dest get() = stack.last()

    fun tab(dest: Dest) {
        kind = NavKind.Tab
        stack = listOf(dest)
        persist()
    }

    fun push(dest: Dest) {
        kind = NavKind.Push
        stack = stack + dest
        persist()
    }

    fun pop() {
        if (stack.size <= 1) return
        kind = NavKind.Pop
        stack = stack.dropLast(1)
        persist()
    }

    private fun persist() {
        saved[KEY] = stack.joinToString("\n") { encode(it) }
    }

    private companion object {
        const val KEY = "stack"
    }
}

private fun encode(dest: Dest): String = when (dest) {
    Dest.Home -> "home"
    Dest.Dynamic -> "dynamic"
    Dest.Mine -> "mine"
    Dest.Settings -> "settings"
    is Dest.Search -> "search\t${dest.keyword.oneLine()}"
    is Dest.Bangumi -> "bangumi\t${dest.seasonId}\t${dest.title.oneLine()}"
    Dest.Regions -> "regions"
    is Dest.RegionRank -> "region\t${dest.tid}\t${dest.name.oneLine()}"
    is Dest.Space -> "space\t${dest.mid}"
    Dest.History -> "history"
    Dest.Favorites -> "favorites"
    is Dest.FavList -> "fav\t${dest.id}\t${dest.title.oneLine()}"
    Dest.WatchLater -> "later"
    Dest.Messages -> "messages"
    is Dest.Chat -> "chat\t${dest.talkerId}\t${dest.name.oneLine()}"
    Dest.Cache -> "cache"
    Dest.Login -> "login"
    is Dest.Play -> listOf(
        "play2",
        dest.bvid,
        dest.cid.toString(),
        dest.aid.toString(),
        dest.durationSec.toString(),
        dest.ownerMid.toString(),
        dest.viewCount.toString(),
        dest.likeCount.toString(),
        dest.danmakuCount.toString(),
        dest.coinCount.toString(),
        dest.favoriteCount.toString(),
        dest.pubdate.toString(),
        dest.title.oneLine(),
        dest.author.oneLine(),
        dest.cover.oneLine(),
        dest.ownerFace.oneLine(),
    ).joinToString("\t")
    is Dest.Live -> "live\t${dest.roomId}\t${dest.title.oneLine()}"
    is Dest.Local -> "local\t${dest.bvid}"
}

private fun decodeStack(raw: String): List<Dest> {
    val items = raw.lineSequence().map { decode(it) }.toList()
    return items.ifEmpty { listOf(Dest.Home) }
}

private fun decode(line: String): Dest {
    val parts = line.split('\t')
    return when (parts.firstOrNull()) {
        "dynamic" -> Dest.Dynamic
        "mine" -> Dest.Mine
        "settings" -> Dest.Settings
        "search" -> Dest.Search(parts.getOrElse(1) { "" })
        "bangumi" -> Dest.Bangumi(parts.getOrNull(1)?.toLongOrNull() ?: 0L, parts.getOrElse(2) { "" })
        "regions" -> Dest.Regions
        "region" -> Dest.RegionRank(parts.getOrNull(1)?.toIntOrNull() ?: 0, parts.getOrElse(2) { "分区" })
        "space" -> Dest.Space(parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        "history" -> Dest.History
        "favorites" -> Dest.Favorites
        "fav" -> Dest.FavList(parts.getOrNull(1)?.toLongOrNull() ?: 0L, parts.getOrElse(2) { "收藏" })
        "later" -> Dest.WatchLater
        "messages" -> Dest.Messages
        "chat" -> Dest.Chat(parts.getOrNull(1)?.toLongOrNull() ?: 0L, parts.getOrElse(2) { "" })
        "cache" -> Dest.Cache
        "login" -> Dest.Login
        "play" -> Dest.Play(
            bvid = parts.getOrElse(1) { "" },
            cid = parts.getOrNull(2)?.toLongOrNull() ?: 0L,
            aid = parts.getOrNull(3)?.toLongOrNull() ?: 0L,
            durationSec = parts.getOrNull(4)?.toIntOrNull() ?: 0,
            ownerMid = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
            title = parts.getOrElse(6) { "" },
            author = parts.getOrElse(7) { "" },
            cover = parts.getOrElse(8) { "" },
        )
        "play2" -> Dest.Play(
            bvid = parts.getOrElse(1) { "" },
            cid = parts.getOrNull(2)?.toLongOrNull() ?: 0L,
            aid = parts.getOrNull(3)?.toLongOrNull() ?: 0L,
            durationSec = parts.getOrNull(4)?.toIntOrNull() ?: 0,
            ownerMid = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
            viewCount = parts.getOrNull(6)?.toLongOrNull() ?: 0L,
            likeCount = parts.getOrNull(7)?.toLongOrNull() ?: 0L,
            danmakuCount = parts.getOrNull(8)?.toLongOrNull() ?: 0L,
            coinCount = parts.getOrNull(9)?.toLongOrNull() ?: 0L,
            favoriteCount = parts.getOrNull(10)?.toLongOrNull() ?: 0L,
            pubdate = parts.getOrNull(11)?.toLongOrNull() ?: 0L,
            title = parts.getOrElse(12) { "" },
            author = parts.getOrElse(13) { "" },
            cover = parts.getOrElse(14) { "" },
            ownerFace = parts.getOrElse(15) { "" },
        )
        "live" -> Dest.Live(parts.getOrNull(1)?.toLongOrNull() ?: 0L, parts.getOrElse(2) { "直播" })
        "local" -> Dest.Local(parts.getOrElse(1) { "" })
        else -> Dest.Home
    }
}

private fun String.oneLine(): String = replace('\n', ' ').replace('\t', ' ')

private fun Dest.isTab(): Boolean = this is Dest.Home || this is Dest.Dynamic || this is Dest.Mine || this is Dest.Settings

internal fun Dest.showsShellChrome(): Boolean = isTab()

internal fun railSelection(base: Dest): Int = when (base) {
    Dest.Dynamic -> 1
    Dest.Mine -> 2
    Dest.Settings -> 3
    else -> 0
}

internal fun Dest.isPlayerLayer(): Boolean =
    this is Dest.Play || this is Dest.Live || this is Dest.Local

internal fun composeOverlayLayer(overlay: Dest?, held: Dest?): Boolean =
    overlay != null || held != null

internal fun playerLayers(stack: List<Dest>): Pair<Dest, Dest?> {
    val top = stack.lastOrNull() ?: Dest.Home
    if (!top.isPlayerLayer()) return top to null
    val base = stack.dropLast(1).lastOrNull { !it.isPlayerLayer() } ?: Dest.Home
    return base to top
}

@Composable
fun AppRoot() {
    ProvideWindowForm {
        AppShell()
    }
}

@Composable
private fun AppShell() {
    val vm = rememberShellViewModel()
    val haptic = LocalHapticFeedback.current
    val settings by AppGraph.repo.settings.collectAsState()
    val form = LocalWindowForm.current
    var rereadDisclaimer by remember { mutableStateOf(false) }
    val (base, overlay) = playerLayers(vm.stack)
    val chrome = if (base.showsShellChrome()) settings.shellLayout.toChrome(form) else BiliChrome.None
    val railItems = listOf(
        BiliRailItem("首页", Icons.Filled.Home),
        BiliRailItem("动态", Icons.AutoMirrored.Filled.ViewList),
        BiliRailItem("我的", Icons.Filled.Person),
        BiliRailItem("设置", Icons.Filled.Settings),
    )
    val railIndex = railSelection(base)
    val motion = MaterialTheme.motionScheme
    var heldOverlay by remember { mutableStateOf<Dest?>(null) }
    var enterReady by remember { mutableStateOf(false) }
    LaunchedEffect(overlay) {
        if (overlay != null) {
            if (!enterReady) delay(32)
            heldOverlay = overlay
            enterReady = true
        } else {
            enterReady = false
        }
    }
    val liveOverlay = overlay.takeIf { enterReady }

    BackHandler(enabled = vm.stack.size > 1 && overlay == null) { vm.pop() }

    val body: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = base,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val spatial = motion.defaultSpatialSpec<IntOffset>()
                    val effects = motion.defaultEffectsSpec<Float>()
                    val fastSpatial = motion.fastSpatialSpec<IntOffset>()
                    val fastEffects = motion.fastEffectsSpec<Float>()
                    when (vm.kind) {
                        NavKind.Push -> (slideInHorizontally(spatial) { it / 4 } + fadeIn(effects))
                            .togetherWith(slideOutHorizontally(fastSpatial) { -it / 8 } + fadeOut(fastEffects))
                        NavKind.Pop -> (slideInHorizontally(spatial) { -it / 8 } + fadeIn(effects))
                            .togetherWith(slideOutHorizontally(fastSpatial) { it / 4 } + fadeOut(fastEffects))
                        NavKind.Tab -> fadeIn(effects).togetherWith(fadeOut(fastEffects))
                    }
                },
                label = "route",
            ) { dest ->
                Route(
                    dest,
                    onPush = vm::push,
                    onPop = vm::pop,
                    onRereadDisclaimer = { rereadDisclaimer = true },
                    onOpenDynamic = { vm.tab(Dest.Dynamic) },
                )
            }
            if (composeOverlayLayer(liveOverlay, heldOverlay)) {
                AnimatedVisibility(
                    visible = liveOverlay != null,
                    modifier = Modifier.fillMaxSize().zIndex(1f),
                    enter = slideInHorizontally(motion.defaultSpatialSpec()) { it / 4 } + fadeIn(motion.defaultEffectsSpec()),
                    exit = slideOutHorizontally(motion.fastSpatialSpec()) { it / 4 } + fadeOut(motion.fastEffectsSpec()),
                ) {
                    (liveOverlay ?: heldOverlay)?.let { dest ->
                        key(dest) {
                            Route(
                                dest,
                                onPush = vm::push,
                                onPop = vm::pop,
                                onRereadDisclaimer = { rereadDisclaimer = true },
                                onOpenDynamic = { vm.tab(Dest.Dynamic) },
                            )
                        }
                    }
                    DisposableEffect(Unit) {
                        onDispose { heldOverlay = null }
                    }
                }
            }
        }
    }

    val openTab: (Int) -> Unit = { index ->
        vm.tab(
            when (index) {
                1 -> Dest.Dynamic
                2 -> Dest.Mine
                3 -> Dest.Settings
                else -> Dest.Home
            },
        )
    }
    BiliShell(
        chrome = chrome,
        rail = { wide ->
            BiliRail(
                items = railItems,
                selected = railIndex,
                wide = wide,
                onLogo = { vm.push(Dest.Regions) },
                onSelect = openTab,
            )
        },
        bottomBar = {
            BiliBottomBar(
                items = railItems,
                selected = railIndex,
                onLogo = { vm.push(Dest.Regions) },
                onSelect = openTab,
            )
        },
        content = body,
    )

    if (settings.acceptedDisclaimerVersion < DISCLAIMER_VERSION || rereadDisclaimer) {
        val accepted = settings.acceptedDisclaimerVersion >= DISCLAIMER_VERSION
        AlertDialog(
            onDismissRequest = {
                if (accepted) rereadDisclaimer = false else exitApp()
            },
            title = { Text("免责声明") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(DISCLAIMER.trim(), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (!accepted) {
                        AppGraph.repo.updateSettings { it.copy(acceptedDisclaimerVersion = DISCLAIMER_VERSION) }
                    }
                    rereadDisclaimer = false
                }) { Text(if (accepted) "关闭" else "同意并继续") }
            },
            dismissButton = if (accepted) {
                null
            } else {
                {
                    TextButton(onClick = { exitApp() }) { Text("不同意") }
                }
            },
        )
    }
}

@Composable
private fun Route(
    dest: Dest,
    onPush: (Dest) -> Unit,
    onPop: () -> Unit,
    onRereadDisclaimer: () -> Unit,
    onOpenDynamic: () -> Unit,
) {
    fun openVideo(item: VideoItem) {
        when {
            item.isLive && item.roomId > 0 -> onPush(Dest.Live(item.roomId, item.title))
            item.bvid.isNotBlank() -> onPush(
                Dest.Play(
                    bvid = item.bvid,
                    cid = item.cid,
                    aid = item.aid,
                    title = item.title,
                    cover = item.cover,
                    author = item.author,
                    ownerMid = item.ownerMid,
                    durationSec = item.durationSec,
                    viewCount = item.viewCount,
                    likeCount = item.likeCount,
                    danmakuCount = item.danmakuCount,
                    coinCount = item.coinCount,
                    favoriteCount = item.favoriteCount,
                    ownerFace = item.ownerFace,
                    pubdate = item.pubdate,
                ),
            )
        }
    }
    fun openAuthor(item: VideoItem) {
        if (item.ownerMid > 0) onPush(Dest.Space(item.ownerMid))
    }
    when (dest) {
        Dest.Home -> HomeRoute(
            onVideo = ::openVideo,
            onAuthor = ::openAuthor,
            onSearch = { onPush(Dest.Search("")) },
            onMessages = { onPush(Dest.Messages) },
        )
        Dest.Dynamic -> DynamicRoute(
            onVideo = ::openVideo,
            onAuthor = ::openAuthor,
            onLogin = { onPush(Dest.Login) },
        )
        Dest.Mine -> MineRoute(
            onLogin = { onPush(Dest.Login) },
            onHistory = { onPush(Dest.History) },
            onFavorites = { onPush(Dest.Favorites) },
            onWatchLater = { onPush(Dest.WatchLater) },
            onCache = { onPush(Dest.Cache) },
            onSpace = { onPush(Dest.Space(it)) },
            onOpenFolder = { onPush(Dest.FavList(it.id, it.title)) },
            onOpenDynamic = onOpenDynamic,
        )
        Dest.Settings -> SettingsRoute(
            onLogout = {},
            onOpenCache = { onPush(Dest.Cache) },
            onOpenAuthor = { onPush(Dest.Space(697238372L)) },
            onOpenDisclaimer = onRereadDisclaimer,
        )
        is Dest.Search -> SearchRoute(
            dest.keyword,
            onPop,
            ::openVideo,
            ::openAuthor,
            onBangumi = { onPush(Dest.Bangumi(it.seasonId, it.title)) },
        )
        is Dest.Bangumi -> SeasonRoute(
            dest.seasonId,
            dest.title,
            onPop,
            onPlay = { ep, seasonTitle ->
                onPush(Dest.Play(bvid = ep.bvid, cid = ep.cid, title = bangumiPlayTitle(seasonTitle, ep.title, ep.longTitle)))
            },
        )
        Dest.Regions -> RegionPickerRoute(
            onPick = { onPush(Dest.RegionRank(it.tid, it.name)) },
            onBack = onPop,
        )
        is Dest.RegionRank -> RegionRoute(dest.tid, dest.name, onPop, ::openVideo, ::openAuthor)
        is Dest.Space -> SpaceRoute(
            dest.mid,
            onPop,
            ::openVideo,
            onOpenFolder = { onPush(Dest.FavList(it.id, it.title)) },
        )
        Dest.History -> HistoryRoute(onPop, ::openVideo, ::openAuthor)
        Dest.Favorites -> FavoritesRoute(onPop) { onPush(Dest.FavList(it.id, it.title)) }
        is Dest.FavList -> FavVideosRoute(dest.id, dest.title, onPop, ::openVideo, ::openAuthor)
        Dest.WatchLater -> WatchLaterRoute(onPop, ::openVideo, ::openAuthor)
        Dest.Messages -> MessagesRoute(onBack = onPop, onOpen = { onPush(Dest.Chat(it.talkerId, it.name)) })
        is Dest.Chat -> ChatRoute(dest.talkerId, dest.name, onPop)
        Dest.Cache -> CacheRoute(onPop) { onPush(Dest.Local(it.bvid)) }
        Dest.Login -> LoginRoute(onBack = onPop, onSuccess = onPop)
        is Dest.Play -> PlayerRoute(
            bvid = dest.bvid,
            liveRoom = null,
            liveTitle = "",
            local = null,
            onBack = onPop,
            onSpace = { onPush(Dest.Space(it)) },
            seedCid = dest.cid,
            seedAid = dest.aid,
            seedTitle = dest.title,
            seedCover = dest.cover,
            seedAuthor = dest.author,
            seedOwnerMid = dest.ownerMid,
            seedDurationSec = dest.durationSec,
            seedViewCount = dest.viewCount,
            seedLikeCount = dest.likeCount,
            seedDanmakuCount = dest.danmakuCount,
            seedCoinCount = dest.coinCount,
            seedFavoriteCount = dest.favoriteCount,
            seedOwnerFace = dest.ownerFace,
            seedPubdate = dest.pubdate,
            onOpenVideo = ::openVideo,
            onTag = { onPush(Dest.Search(it)) },
        )
        is Dest.Live -> PlayerRoute(
            bvid = null,
            liveRoom = dest.roomId,
            liveTitle = dest.title,
            local = null,
            onBack = onPop,
            onSpace = { onPush(Dest.Space(it)) },
        )
        is Dest.Local -> {
            val entry = AppGraph.cache.list().firstOrNull { it.bvid == dest.bvid }
            PlayerRoute(
                bvid = null,
                liveRoom = null,
                liveTitle = "",
                local = entry,
                onBack = onPop,
                onSpace = { onPush(Dest.Space(it)) },
            )
        }
    }
}
