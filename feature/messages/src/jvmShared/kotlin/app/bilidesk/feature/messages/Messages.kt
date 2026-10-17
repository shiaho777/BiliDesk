package app.bilidesk.feature.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import app.bilidesk.core.designsystem.BiliBackHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bilidesk.core.data.AppGraph
import app.bilidesk.core.designsystem.LoadBox
import app.bilidesk.core.designsystem.appViewModel
import app.bilidesk.core.model.MsgItem
import app.bilidesk.core.model.MsgSession
import kotlinx.coroutines.launch

class InboxViewModel : ViewModel() {
    var items by mutableStateOf<List<MsgSession>>(emptyList())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    init { reload() }
    fun reload() {
        loading = true
        viewModelScope.launch {
            AppGraph.repo.messages().onSuccess { items = it; loading = false }.onFailure { error = it.message; loading = false }
        }
    }
}

class ChatViewModel(private val talkerId: Long) : ViewModel() {
    var items by mutableStateOf<List<MsgItem>>(emptyList())
    var draft by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var sending by mutableStateOf(false)
    init { reload() }
    fun reload() {
        viewModelScope.launch {
            AppGraph.repo.msgHistory(talkerId).onSuccess { items = it }.onFailure { error = it.message }
        }
    }
    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        sending = true
        viewModelScope.launch {
            AppGraph.repo.sendMsg(talkerId, text).onSuccess {
                draft = ""
                reload()
            }.onFailure { error = it.message }
            sending = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesRoute(onBack: () -> Unit, onOpen: (MsgSession) -> Unit, vm: InboxViewModel = appViewModel { InboxViewModel() }) {
    Scaffold(
        topBar = {
            BiliBackHeader("消息", onBack)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadBox(vm.loading, vm.error, vm.items.isEmpty(), "没有私信", vm::reload) {
                LazyColumn {
                    items(vm.items, key = { it.talkerId }) { session ->
                        ListItem(
                            headlineContent = { Text(session.name.ifBlank { "UID ${session.talkerId}" }) },
                            supportingContent = { Text(session.preview) },
                            trailingContent = { if (session.unread > 0) Text("${session.unread}") },
                            modifier = Modifier.clickable { onOpen(session) }.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoute(talkerId: Long, name: String, onBack: () -> Unit) {
    val vm = appViewModel(key = "chat-$talkerId") { ChatViewModel(talkerId) }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.items.size) {
        if (vm.items.isNotEmpty()) listState.animateScrollToItem(vm.items.lastIndex)
    }
    Scaffold(
        topBar = {
            BiliBackHeader(name.ifBlank { "私信" }, onBack)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.items, key = { it.seqno }) { msg ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start) {
                        Surface(
                            color = if (msg.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(msg.text.ifBlank { msg.imageUrl.ifBlank { "[消息]" } }, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = vm.draft,
                    onValueChange = { vm.draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("发消息") },
                )
                Button(onClick = vm::send, modifier = Modifier.padding(start = 8.dp), enabled = !vm.sending) { Text("发送") }
            }
        }
    }
}
