package app.bilidesk

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.bilidesk.core.data.AppGraph
import app.bilidesk.core.data.openUrl
import app.bilidesk.core.network.JSONObject
import app.bilidesk.core.network.SharedHttp
import app.bilidesk.core.network.get
import app.bilidesk.core.model.ioDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val CURRENT_VERSION = "1.2.4"

private data class UpdateNote(val tag: String, val page: String, val message: String)

@Composable
fun UpdatePrompt(enabled: Boolean, skippedVersion: String) {
    var note by remember { mutableStateOf<UpdateNote?>(null) }
    LaunchedEffect(enabled, skippedVersion) {
        if (!enabled) return@LaunchedEffect
        delay(6_000)
        note = newerRelease(skippedVersion)
    }
    val message = note ?: return
    AlertDialog(
        onDismissRequest = { note = null },
        title = { Text("有新版本") },
        text = { Text(message.message) },
        confirmButton = {
            TextButton(onClick = {
                if (message.page.isNotBlank()) openUrl(message.page)
                note = null
            }) { Text("前往下载") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { note = null }) { Text("以后再说") }
                TextButton(
                    onClick = {
                        AppGraph.repo.updateSettings { it.copy(skippedVersion = message.tag) }
                        note = null
                    },
                ) { Text("跳过此版本") }
            }
        },
    )
}

private suspend fun newerRelease(skipped: String): UpdateNote? = withContext(ioDispatcher) {
    runCatching {
        val json = JSONObject(
            SharedHttp.api.get(
                "https://api.github.com/repos/ABING1111/BiliDesk/releases/latest",
                mapOf(
                    "Accept" to "application/vnd.github+json",
                    "User-Agent" to "BiliDesk",
                ),
            ).bodyText,
        )
        val tag = json.optString("tag_name").removePrefix("v")
        val page = json.optString("html_url")
        if (tag.isBlank() || tag == CURRENT_VERSION) return@runCatching null
        if (tag.equals(skipped.removePrefix("v"), ignoreCase = true)) return@runCatching null
        UpdateNote(tag, page, "最新发布 $tag\n$page")
    }.getOrNull()
}
