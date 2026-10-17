package app.bilidesk.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import app.bilidesk.core.designsystem.BiliBackHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import app.bilidesk.core.data.AppGraph
import app.bilidesk.core.model.QrStatus
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginRoute(onBack: () -> Unit, onSuccess: () -> Unit) {
    var message by remember { mutableStateOf("正在获取二维码…") }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var key by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(refresh) {
        message = "正在获取二维码…"
        bitmap = null
        val ticket = AppGraph.repo.qrGenerate().getOrElse {
            message = it.message ?: "二维码获取失败"
            return@LaunchedEffect
        }
        key = ticket.key
        bitmap = qrBitmap(ticket.url)
        message = "用哔哩哔哩 App 扫码"
        while (true) {
            delay(2000)
            when (AppGraph.repo.qrPoll(ticket.key).getOrNull()) {
                QrStatus.Scanned -> message = "已扫码，请在手机上确认"
                QrStatus.Success -> {
                    message = "登录成功"
                    onSuccess()
                    return@LaunchedEffect
                }
                QrStatus.Expired -> {
                    message = "二维码过期了"
                    return@LaunchedEffect
                }
                QrStatus.Failed -> message = "登录状态查询失败"
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            BiliBackHeader("扫码登录", onBack)
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            bitmap?.let { Image(it, contentDescription = "登录二维码", modifier = Modifier.size(240.dp)) }
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { refresh += 1 }) { Text("刷新二维码") }
        }
    }
}

internal expect fun qrBitmap(content: String): ImageBitmap
