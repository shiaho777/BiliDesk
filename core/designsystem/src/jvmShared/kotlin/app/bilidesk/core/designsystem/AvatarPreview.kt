package app.bilidesk.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.bilidesk.core.data.saveImageBytes
import app.bilidesk.core.model.USER_AGENT
import app.bilidesk.core.network.SharedHttp
import app.bilidesk.core.network.get
import coil3.compose.AsyncImage
import app.bilidesk.core.model.ioDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AvatarPreviewDialog(
    url: String,
    name: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name.ifBlank { "头像" }) },
        text = {
            Box(
                Modifier.fillMaxWidth().height(280.dp).background(androidx.compose.ui.graphics.Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    url,
                    name,
                    Modifier.fillMaxWidth().height(280.dp).padding(8.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val message = saveAvatar(url, name)
                    onSaved(message)
                    onDismiss()
                }
            }) { Text("保存到相册") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private suspend fun saveAvatar(url: String, name: String): String = withContext(ioDispatcher) {
    runCatching {
        val bytes = SharedHttp.default.get(
            url,
            mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "https://www.bilibili.com/",
            ),
        ).body
        saveImageBytes(bytes, name.ifBlank { "avatar" })
    }.getOrElse { "保存失败：${it.message}" }
}
