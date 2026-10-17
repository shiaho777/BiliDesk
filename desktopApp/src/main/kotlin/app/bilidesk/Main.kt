package app.bilidesk

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import app.bilidesk.core.data.AppGraph
import app.bilidesk.core.data.DesktopHost
import app.bilidesk.core.designsystem.BiliTheme
import app.bilidesk.core.designsystem.InstallBiliImages
import java.awt.Dimension
import java.awt.Frame
import java.awt.SystemTray

fun main() {
    if (!SingleInstance.tryAcquire()) return
    application {
        AppGraph.init()
        DesktopHost.exit = ::exitApplication
        val state = rememberWindowState(width = 1280.dp, height = 800.dp)
        val icon = painterResource("icon.png")
        val trayOk = remember { SystemTray.isSupported() }
        val trayState = rememberTrayState()
        var visible by remember { mutableStateOf(true) }
        var balloonShown by remember { mutableStateOf(false) }
        fun showMain() {
            visible = true
        }
        fun hideMain() {
            visible = false
            if (!balloonShown) {
                balloonShown = true
                trayState.sendNotification(
                    Notification(
                        "BiliDesk 仍在后台运行",
                        "双击托盘图标可以恢复主界面，右键菜单可退出。",
                        Notification.Type.Info,
                    ),
                )
            }
        }
        DesktopHost.showWindow = { showMain() }
        DesktopHost.hideWindow = { hideMain() }
        if (trayOk) {
            Tray(
                icon = icon,
                state = trayState,
                tooltip = "BiliDesk",
                onAction = { showMain() },
            ) {
                Item("显示主界面", onClick = { showMain() })
                Item("最小化到托盘", onClick = { hideMain() })
                Separator()
                Item("退出", onClick = { exitApplication() })
            }
        }
        Window(
            onCloseRequest = {
                val closeToTray = AppGraph.repo.settings.value.closeToTray
                if (closeToTray && trayOk) hideMain() else exitApplication()
            },
            state = state,
            visible = visible,
            title = "BiliDesk",
            icon = icon,
        ) {
            window.minimumSize = Dimension(720, 480)
            DesktopHost.fullscreen = { on ->
                state.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
            }
            LaunchedEffect(visible) {
                if (visible) {
                    window.extendedState = Frame.NORMAL
                    window.toFront()
                    window.requestFocus()
                }
            }
            InstallBiliImages()
            val settings by AppGraph.repo.settings.collectAsState()
            BiliTheme(settings.theme, settings.accentHex, settings.accentFollowSystem) {
                AppRoot()
                UpdatePrompt(settings.autoCheckUpdate, settings.skippedVersion)
            }
        }
    }
}
