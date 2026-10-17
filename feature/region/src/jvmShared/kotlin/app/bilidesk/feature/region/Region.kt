package app.bilidesk.feature.region

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import app.bilidesk.core.designsystem.BiliBackHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch

class RegionViewModel(private val tid: Int) : ViewModel() {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    init { reload() }

    fun reload() {
        loading = true
        error = null
        viewModelScope.launch {
            AppGraph.repo.regionRank(tid).onSuccess {
                items = it
                loading = false
            }.onFailure {
                loading = false
                error = it.message
            }
        }
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
                        Text(part.initial)
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
    Scaffold(
        topBar = {
            BiliBackHeader(name, onBack)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadBox(vm.loading, vm.error, vm.items.isEmpty(), "这个分区暂时没有排行", vm::reload) {
                VideoGrid(vm.items, onVideo, onAuthor)
            }
        }
    }
}
