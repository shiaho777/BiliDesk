package app.bilidesk.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bilidesk.core.data.AppGraph
import app.bilidesk.core.designsystem.BiliBackHeader
import app.bilidesk.core.designsystem.LoadBox
import app.bilidesk.core.designsystem.appViewModel
import app.bilidesk.core.model.BangumiDetail
import app.bilidesk.core.model.BangumiEpisode
import app.bilidesk.core.model.bangumiEpisodeDisplayTitle
import app.bilidesk.core.model.formatSeconds
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

class SeasonViewModel(private val seasonId: Long) : ViewModel() {
    var detail by mutableStateOf<BangumiDetail?>(null)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    init { reload() }

    fun reload() {
        loading = true
        error = null
        viewModelScope.launch {
            AppGraph.repo.bangumiDetail(seasonId)
                .onSuccess { detail = it }
                .onFailure { error = it.message }
            loading = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonRoute(
    seasonId: Long,
    title: String,
    onBack: () -> Unit,
    onPlay: (BangumiEpisode, seasonTitle: String) -> Unit,
    vm: SeasonViewModel = appViewModel(key = "season-$seasonId") { SeasonViewModel(seasonId) },
) {
    val detail = vm.detail
    val episodes = detail?.episodes.orEmpty()
    Scaffold(
        topBar = { BiliBackHeader(detail?.title ?: title, onBack) },
    ) { padding ->
        LoadBox(
            vm.loading,
            vm.error,
            detail == null || episodes.isEmpty(),
            "暂无选集",
            vm::reload,
            Modifier.padding(padding),
        ) {
            val shown = detail ?: return@LoadBox
            LazyColumn(Modifier.fillMaxSize()) {
                if (shown.cover.isNotBlank() || shown.evaluate.isNotBlank()) {
                    item {
                        Row(
                            Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (shown.cover.isNotBlank()) {
                                AsyncImage(
                                    model = shown.cover,
                                    contentDescription = shown.title,
                                    modifier = Modifier
                                        .width(120.dp)
                                        .aspectRatio(3f / 4f)
                                        .clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            if (shown.evaluate.isNotBlank()) {
                                Text(
                                    shown.evaluate,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                itemsIndexed(shown.episodes, key = { _, ep -> "${ep.epId}-${ep.bvid}-${ep.cid}" }) { index, ep ->
                    val latest = index == shown.episodes.lastIndex
                    ListItem(
                        headlineContent = {
                            Text(
                                bangumiEpisodeDisplayTitle(ep.title, ep.longTitle),
                                color = if (latest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = if (ep.durationSec > 0) {
                            { Text(formatSeconds(ep.durationSec)) }
                        } else {
                            null
                        },
                        modifier = Modifier.clickable { onPlay(ep, shown.title) },
                    )
                }
            }
        }
    }
}
