package app.bilidesk.feature.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bilidesk.core.data.AppGraph
import app.bilidesk.core.designsystem.BiliPageTitle
import app.bilidesk.core.designsystem.shellBottomPadding
import app.bilidesk.core.model.FavFolder
import app.bilidesk.core.model.UserState
import coil3.compose.AsyncImage
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MineRoute(
    onLogin: () -> Unit,
    onHistory: () -> Unit,
    onFavorites: () -> Unit,
    onWatchLater: () -> Unit,
    onCache: () -> Unit,
    onSpace: (Long) -> Unit,
    onOpenFolder: (FavFolder) -> Unit,
    onOpenDynamic: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val session by AppGraph.repo.session.collectAsState()
    var user by remember { mutableStateOf<UserState?>(null) }
    var counts by remember { mutableStateOf(MineCounts()) }
    var folders by remember { mutableStateOf<List<FavFolder>>(emptyList()) }
    var folderLoading by remember { mutableStateOf(false) }
    var folderError by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var awaitingProfile by remember(session.sessData) { mutableStateOf(session.loggedIn) }

    LaunchedEffect(session.loggedIn, session.sessData, refreshTick) {
        refreshing = refreshTick > 0
        if (!session.loggedIn) {
            user = null
            counts = MineCounts()
            folders = emptyList()
            folderError = null
            folderLoading = false
            refreshing = false
            awaitingProfile = false
            return@LaunchedEffect
        }
        folderLoading = folders.isEmpty()
        val state = AppGraph.repo.userState().getOrNull()?.takeIf { it.loggedIn }
        user = state
        awaitingProfile = false
        if (state == null) {
            counts = MineCounts()
            folders = emptyList()
            folderError = null
            folderLoading = false
            refreshing = false
            return@LaunchedEffect
        }
        val nav = AppGraph.repo.navStat().getOrNull()
        counts = if (nav != null) {
            MineCounts(nav.dynamicCount.toString(), nav.following.toString(), nav.follower.toString())
        } else {
            val relation = AppGraph.repo.relationStat(state.mid).getOrNull()
            MineCounts(
                dynamic = "--",
                following = relation?.first?.toString() ?: "--",
                follower = relation?.second?.toString() ?: "--",
            )
        }
        AppGraph.repo.favFolderCards()
            .onSuccess { got ->
                folders = got
                folderError = if (got.isEmpty()) "还没有创建收藏夹" else null
            }
            .onFailure { error ->
                if (folders.isEmpty()) folderError = error.message ?: "收藏夹加载失败"
            }
        folderLoading = false
        refreshing = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().shellBottomPadding()) {
        Row(
            Modifier.padding(start = 20.dp, top = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 10.dp).size(22.dp),
            )
            BiliPageTitle("我的")
        }
        val profile = user
        if (awaitingProfile && profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (profile == null) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Text(
                    if (session.loggedIn) "登录状态已过期" else "登录后查看你的收藏、历史与硬币经验",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                )
                Button(onClick = onLogin) { Text("去登录") }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshTick++ },
                modifier = Modifier.fillMaxSize(),
            ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Row(
                    Modifier
                        .padding(top = 12.dp)
                        .clickable {
                            if (profile.mid > 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onSpace(profile.mid)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (profile.face.isNotBlank()) {
                        AsyncImage(
                            profile.face,
                            profile.name,
                            Modifier.size(64.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(Modifier.padding(start = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                profile.name,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (profile.level > 0) {
                                Text(
                                    "LV${profile.level}",
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
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("硬币", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                            Text(
                                formatCoins(profile.coins),
                                modifier = Modifier.padding(start = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                            )
                            val exp = expLabel(profile)
                            if (exp != null) {
                                Text("经验", modifier = Modifier.padding(start = 18.dp), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                                Text(
                                    exp,
                                    modifier = Modifier.padding(start = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                        val fraction = expFraction(profile)
                        if (fraction != null) {
                            Box(
                                Modifier
                                    .padding(top = 6.dp)
                                    .width(120.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 28.dp)) {
                    Stat(counts.dynamic, "动态", Modifier.weight(1f)) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onOpenDynamic()
                    }
                    Stat(counts.following, "关注", Modifier.weight(1f))
                    Stat(counts.follower, "粉丝", Modifier.weight(1f))
                }

                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 22.dp)) {
                    val columns = if (maxWidth >= 520.dp) 4 else 2
                    val entries = listOf(
                        Triple(Icons.Filled.Download, "离线缓存", onCache),
                        Triple(Icons.Filled.History, "观看记录", onHistory),
                        Triple(Icons.Filled.Star, "我的收藏", onFavorites),
                        Triple(Icons.Filled.Schedule, "稍后再看", onWatchLater),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        entries.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { (icon, label, action) ->
                                    Shortcut(icon, label, Modifier.weight(1f)) {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        action()
                                    }
                                }
                                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(top = 18.dp))

                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onFavorites()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("我的收藏", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (folders.isNotEmpty()) {
                            Text(
                                folders.size.toString(),
                                modifier = Modifier.padding(start = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "打开收藏",
                            modifier = Modifier.padding(start = 2.dp).size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Box(Modifier.weight(1f))
                    if (folderLoading && folders.isNotEmpty()) {
                        CircularProgressIndicator(Modifier.padding(end = 14.dp).size(18.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            refreshTick++
                        }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    }
                }

                if (folderLoading && folders.isEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 24.dp).size(26.dp).align(Alignment.CenterHorizontally),
                        strokeWidth = 2.dp,
                    )
                }
                folderError?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                    )
                }
                if (folders.isNotEmpty()) {
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
                        val columns = if (maxWidth >= 560.dp) 3 else 2
                        val cardWidth = (maxWidth - 14.dp * (columns - 1)) / columns
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            val alone = folders.size == 1
                            folders.forEach { folder ->
                                FolderCard(folder, if (alone) Modifier.fillMaxWidth() else Modifier.width(cardWidth)) {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onOpenFolder(folder)
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

private data class MineCounts(
    val dynamic: String = "--",
    val following: String = "--",
    val follower: String = "--",
)

@Composable
private fun Stat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, modifier = Modifier.padding(top = 3.dp), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
    }
}

@Composable
private fun Shortcut(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .padding(horizontal = 4.dp)
            .height(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Text(
            label,
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun FolderCard(folder: FavFolder, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        if (folder.cover.isNotBlank()) {
            AsyncImage(
                folder.cover,
                folder.title,
                Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
        Text(
            folder.title.ifBlank { "收藏夹" },
            modifier = Modifier.padding(start = 2.dp, top = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
        )
        Text(
            "共${folder.count}条视频 · ${if (folder.privateFolder) "私密" else "公开"}",
            modifier = Modifier.padding(start = 2.dp, top = 3.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
        )
    }
}

private fun formatCoins(value: Double): String {
    val whole = value.toLong()
    if (abs(value - whole) < 0.05) return whole.toString()
    val scaled = kotlin.math.round(value * 10.0).toLong()
    return "${scaled / 10}.${abs(scaled % 10)}"
}

private fun expLabel(user: UserState): String? = when {
    user.expNext > 0 -> "${user.expCurrent}/${user.expNext}"
    user.expCurrent > 0 -> user.expCurrent.toString()
    else -> null
}

private fun expFraction(user: UserState): Float? = when {
    user.expNext > 0 -> (user.expCurrent.toFloat() / user.expNext.toFloat()).coerceIn(0f, 1f)
    user.expCurrent > 0 -> 1f
    else -> null
}
