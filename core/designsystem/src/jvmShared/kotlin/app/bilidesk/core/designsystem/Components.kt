package app.bilidesk.core.designsystem

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import app.bilidesk.core.model.VideoItem
import app.bilidesk.core.model.formatCount
import coil3.compose.AsyncImage

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    noinline create: () -> VM,
): VM {
    return viewModel(key = key) { create() }
}

@Composable
fun LoadBox(
    loading: Boolean,
    error: String?,
    empty: Boolean,
    emptyText: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        when {
            loading && empty -> LoadingIndicator(Modifier.align(Alignment.Center))
            error != null && empty -> Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(error, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onRetry) { Text("重试") }
            }
            empty && !loading -> Text(
                emptyText,
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> content()
        }
        if (loading && !empty) {
            CircularProgressIndicator(Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

@Composable
fun VideoGrid(
    items: List<VideoItem>,
    onClick: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: (() -> Unit)? = null,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(12.dp),
) {
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
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.stableKey + it.title }) { item ->
            VideoCard(item, onClick, onAuthor, Modifier.animateItem())
        }
    }
}

@Composable
fun VideoCard(
    item: VideoItem,
    onClick: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
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
                        .aspectRatio(16f / 10f),
                    contentScale = ContentScale.Crop,
                )
                val badge = if (item.isLive) {
                    val n = formatCount(item.viewCount)
                    if (n.isBlank()) "直播" else "$n 人气"
                } else item.durationText
                if (badge.isNotBlank() && badge != "--:--") {
                    Text(
                        badge,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
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
                if (item.reason.isNotBlank()) {
                    Text(
                        item.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.author.isNotBlank()) {
                        Text(
                            item.author,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable(enabled = item.ownerMid > 0) { onAuthor(item) },
                        )
                    }
                }
                val meta = cardMeta(item)
                if (meta.isNotBlank()) {
                    Text(
                        meta,
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

private fun cardMeta(item: VideoItem): String {
    if (item.isLive || !item.statsReady) return ""
    return listOfNotNull(
        formatCount(item.viewCount).takeIf { it.isNotBlank() }?.let { "$it 播放" },
        formatCount(item.danmakuCount).takeIf { it.isNotBlank() }?.let { "$it 弹幕" },
        formatCount(item.likeCount).takeIf { it.isNotBlank() }?.let { "$it 点赞" },
    ).joinToString(" · ")
}
