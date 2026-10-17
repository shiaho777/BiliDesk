package app.bilidesk.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.bilidesk.core.data.AppGraph
import app.bilidesk.core.data.canFollowSystemAccent
import app.bilidesk.core.data.hideAppWindow
import app.bilidesk.core.data.imageCacheDirs
import app.bilidesk.core.data.isDesktopHost
import app.bilidesk.core.data.openUrl
import app.bilidesk.core.data.traySupported
import app.bilidesk.core.model.SponsorCategories
import app.bilidesk.core.designsystem.BiliPageTitle
import app.bilidesk.core.designsystem.BiliPillTabs
import app.bilidesk.core.designsystem.BiliSectionTitle
import app.bilidesk.core.designsystem.shellBottomPadding
import app.bilidesk.core.model.CdnLine
import app.bilidesk.core.model.CdnMode
import app.bilidesk.core.model.ShellLayout
import app.bilidesk.core.model.ThemeMode
import app.bilidesk.core.model.qnLabel
import app.bilidesk.core.model.ioDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.bilidesk.core.network.JSONObject
import app.bilidesk.core.network.SharedHttp
import app.bilidesk.core.network.get

private val qualities = listOf(16, 32, 64, 80, 112, 116, 120)

private val settingsTabs = listOf("通用", "播放", "数据管理", "关于")

@Composable
fun SettingsRoute(
    onLogout: () -> Unit,
    onOpenCache: () -> Unit,
    onOpenAuthor: () -> Unit = {},
    onOpenDisclaimer: () -> Unit = {},
) {
    val settings by AppGraph.repo.settings.collectAsState()
    val session by AppGraph.repo.session.collectAsState()
    val scope = rememberCoroutineScope()
    var updateText by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var area by remember(settings.danmakuAreaPercent) { mutableFloatStateOf(settings.danmakuAreaPercent.toFloat()) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .shellBottomPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        BiliPageTitle("应用设置", Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp))
        Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
            BiliPillTabs(settingsTabs, tab, onSelect = { tab = it })
        }
        when (tab) {
            0 -> {
                BiliSectionTitle("外观", Modifier.padding(horizontal = 20.dp))
                SettingsCard {
                    Text("标签栏", modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
                    Text(
                        "自动：手机竖屏用底部，平板和电脑窗口用左侧。也可以固定某一种。",
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    RowChips {
                        FilterChip(
                            selected = settings.shellLayout == ShellLayout.Auto,
                            onClick = { AppGraph.repo.updateSettings { it.copy(shellLayout = ShellLayout.Auto) } },
                            label = { Text("自动") },
                        )
                        FilterChip(
                            selected = settings.shellLayout == ShellLayout.Bottom,
                            onClick = { AppGraph.repo.updateSettings { it.copy(shellLayout = ShellLayout.Bottom) } },
                            label = { Text("底部标签") },
                        )
                        FilterChip(
                            selected = settings.shellLayout == ShellLayout.Rail,
                            onClick = { AppGraph.repo.updateSettings { it.copy(shellLayout = ShellLayout.Rail) } },
                            label = { Text("左侧导航") },
                        )
                    }
                    Text("应用主题", modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
                    RowChips {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.theme == mode,
                                onClick = { AppGraph.repo.updateSettings { it.copy(theme = mode) } },
                                label = {
                                    Text(when (mode) {
                                        ThemeMode.System -> "跟随系统"
                                        ThemeMode.Light -> "浅色"
                                        ThemeMode.Dark -> "深色"
                                    })
                                },
                            )
                        }
                    }
                    Text("主题色", modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp))
                    Text(
                        if (canFollowSystemAccent()) {
                            "默认是 B 站粉。跟随系统用设备强调色，也可以自己填一个 6 位色值。"
                        } else {
                            "默认是 B 站粉。也可以自己填一个 6 位色值。"
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    RowChips {
                        if (canFollowSystemAccent()) {
                            FilterChip(
                                selected = settings.accentFollowSystem,
                                onClick = { AppGraph.repo.updateSettings { it.copy(accentFollowSystem = !it.accentFollowSystem) } },
                                label = { Text("跟随系统") },
                            )
                        }
                        listOf("" to "默认粉", "3B82F6" to "蓝", "10B981" to "绿", "F59E0B" to "琥珀").forEach { (hex, label) ->
                            FilterChip(
                                selected = !settings.accentFollowSystem && settings.accentHex.equals(hex, ignoreCase = true),
                                onClick = {
                                    AppGraph.repo.updateSettings { it.copy(accentFollowSystem = false, accentHex = hex) }
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    var accentDraft by remember(settings.accentHex) { mutableStateOf(settings.accentHex) }
                    OutlinedTextField(
                        value = accentDraft,
                        onValueChange = { accentDraft = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        singleLine = true,
                        label = { Text("自定义色值，例如 FB7299") },
                        trailingIcon = {
                            TextButton(onClick = {
                                AppGraph.repo.updateSettings {
                                    it.copy(accentFollowSystem = false, accentHex = accentDraft.trim().removePrefix("#"))
                                }
                            }) { Text("使用") }
                        },
                    )
                    if (isDesktopHost() && traySupported()) {
                        ListItem(
                            headlineContent = { Text("关闭时最小化到托盘") },
                            supportingContent = {
                                Text(
                                    if (settings.closeToTray) {
                                        "点右上角关闭键会收进系统托盘，双击托盘图标可恢复"
                                    } else {
                                        "点右上角关闭键将直接退出程序"
                                    },
                                )
                            },
                            trailingContent = {
                                Switch(settings.closeToTray, { on ->
                                    AppGraph.repo.updateSettings { it.copy(closeToTray = on) }
                                })
                            },
                        )
                        if (settings.closeToTray) {
                            ListItem(
                                headlineContent = { Text("立刻收到托盘") },
                                supportingContent = { Text("主窗口会藏起来，进程继续跑") },
                                trailingContent = {
                                    TextButton(onClick = { hideAppWindow() }) { Text("收起") }
                                },
                            )
                        }
                    }
                    if (session.loggedIn) {
                        ListItem(
                            headlineContent = { Text("退出登录") },
                            trailingContent = {
                                TextButton(onClick = {
                                    scope.launch {
                                        AppGraph.repo.logout()
                                        onLogout()
                                    }
                                }) { Text("退出") }
                            },
                        )
                    }
                }
            }
            1 -> {
                if (isDesktopHost()) {
                    BiliSectionTitle("电脑快捷键", Modifier.padding(horizontal = 20.dp))
                    SettingsCard {
                        Text(
                            "空格播放或暂停。左右方向键快进快退 5 秒，上下方向键调音量。F 全屏，D 开关弹幕，L 点赞，U 打开 UP 主页，Esc 退出全屏。滚轮调音量，双击画面全屏。",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                BiliSectionTitle("画质与线路", Modifier.padding(horizontal = 20.dp))
                SettingsCard {
                    Text("默认画质", modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
                    RowChips {
                        qualities.forEach { qn ->
                            FilterChip(
                                selected = settings.defaultQn == qn,
                                onClick = { AppGraph.repo.updateSettings { it.copy(defaultQn = qn) } },
                                label = { Text(qnLabel(qn)) },
                            )
                        }
                    }
                    ListItem(
                        headlineContent = { Text("免登录 1080P") },
                        supportingContent = { Text("未登录时附带试看参数，最高到 1080P") },
                        trailingContent = {
                            Switch(settings.noLogin1080, { on -> AppGraph.repo.updateSettings { it.copy(noLogin1080 = on) } })
                        },
                    )
                    Text("线路", modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp))
                    RowChips {
                        listOf(CdnMode.Auto to "自动测速", CdnMode.Server to "跟随服务端", CdnMode.Manual to "指定线路").forEach { (mode, label) ->
                            FilterChip(
                                selected = settings.cdnMode == mode,
                                onClick = {
                                    AppGraph.repo.updateSettings { it.copy(cdnMode = mode) }
                                    AppGraph.repo.invalidateCdn()
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    ListItem(
                        headlineContent = { Text("屏蔽 PCDN") },
                        supportingContent = { Text("不走点对点分发。关掉后可能用到 mcdn 线路") },
                        trailingContent = {
                            Switch(settings.blockPcdn, { on ->
                                AppGraph.repo.updateSettings { it.copy(blockPcdn = on) }
                                AppGraph.repo.invalidateCdn()
                            })
                        },
                    )
                    val cdnReport by AppGraph.repo.cdnReport.collectAsState()
                    Text(
                        cdnReport,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (settings.cdnMode == CdnMode.Manual) {
                        RowChips {
                            CdnLine.all.forEach { line ->
                                FilterChip(
                                    selected = settings.cdnManualId == line.id,
                                    onClick = {
                                        AppGraph.repo.updateSettings { it.copy(cdnManualId = line.id) }
                                        AppGraph.repo.invalidateCdn()
                                    },
                                    label = { Text(line.name) },
                                )
                            }
                        }
                    }
                }
                BiliSectionTitle("弹幕", Modifier.padding(horizontal = 20.dp))
                SettingsCard {
                    ListItem(
                        headlineContent = { Text("显示弹幕") },
                        trailingContent = {
                            Switch(settings.danmakuEnabled, { on -> AppGraph.repo.updateSettings { it.copy(danmakuEnabled = on) } })
                        },
                    )
                    ListItem(
                        headlineContent = { Text("彩色弹幕") },
                        trailingContent = {
                            Switch(settings.danmakuColorful, { on -> AppGraph.repo.updateSettings { it.copy(danmakuColorful = on) } })
                        },
                    )
                    ListItem(
                        headlineContent = { Text("智能屏蔽") },
                        supportingContent = { Text("过滤超长和刷屏弹幕") },
                        trailingContent = {
                            Switch(settings.danmakuSmartFilter, { on -> AppGraph.repo.updateSettings { it.copy(danmakuSmartFilter = on) } })
                        },
                    )
                    Text("弹幕显示区域 ${area.toInt()}%", modifier = Modifier.padding(horizontal = 16.dp))
                    Slider(
                        value = area,
                        onValueChange = { area = it },
                        onValueChangeFinished = {
                            AppGraph.repo.updateSettings { it.copy(danmakuAreaPercent = area.toInt()) }
                        },
                        valueRange = 25f..100f,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Text("按类型过滤", modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp))
                    Text(
                        if (
                            settings.danmakuFilterScroll && settings.danmakuFilterFixed &&
                            settings.danmakuFilterColorful && settings.danmakuFilterAdvanced
                        ) {
                            "当前显示全部类型; 关掉某一类即可把它从画面里去掉"
                        } else {
                            "只显示已选中的类型"
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    RowChips {
                        FilterChip(
                            selected = settings.danmakuFilterScroll,
                            onClick = { AppGraph.repo.updateSettings { it.copy(danmakuFilterScroll = !it.danmakuFilterScroll) } },
                            label = { Text("滚动") },
                        )
                        FilterChip(
                            selected = settings.danmakuFilterFixed,
                            onClick = { AppGraph.repo.updateSettings { it.copy(danmakuFilterFixed = !it.danmakuFilterFixed) } },
                            label = { Text("固定") },
                        )
                        FilterChip(
                            selected = settings.danmakuFilterColorful,
                            onClick = { AppGraph.repo.updateSettings { it.copy(danmakuFilterColorful = !it.danmakuFilterColorful) } },
                            label = { Text("彩色") },
                        )
                        FilterChip(
                            selected = settings.danmakuFilterAdvanced,
                            onClick = { AppGraph.repo.updateSettings { it.copy(danmakuFilterAdvanced = !it.danmakuFilterAdvanced) } },
                            label = { Text("高级") },
                        )
                    }
                    OutlinedTextField(
                        value = settings.danmakuBlockKeywords,
                        onValueChange = { text -> AppGraph.repo.updateSettings { it.copy(danmakuBlockKeywords = text) } },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        label = { Text("弹幕关键词") },
                        placeholder = { Text("逗号、分号或换行分隔，re: 开头是正则") },
                        minLines = 2,
                    )
                }
                BiliSectionTitle("赞助片段", Modifier.padding(horizontal = 20.dp))
                SettingsCard {
                    ListItem(
                        headlineContent = { Text("跳过赞助片段") },
                        supportingContent = { Text("播到被标注的片段时跳到末尾，可随时撤回。默认关闭") },
                        trailingContent = {
                            Switch(settings.sponsorBlock, { on -> AppGraph.repo.updateSettings { it.copy(sponsorBlock = on) } })
                        },
                    )
                    Text(
                        "跳过的类别。数据来自网友标注，没人标过的视频不会跳。至少保留一类。",
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RowChips {
                        val selected = SponsorCategories.sanitize(settings.sponsorCategories).split(',').toSet()
                        SponsorCategories.all.forEach { (id, label) ->
                            FilterChip(
                                selected = id in selected,
                                onClick = {
                                    val next = if (id in selected) selected - id else selected + id
                                    AppGraph.repo.updateSettings {
                                        it.copy(sponsorCategories = SponsorCategories.sanitize(next.joinToString(",")))
                                    }
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
            2 -> {
                BiliSectionTitle("离线缓存", Modifier.padding(horizontal = 20.dp))
                SettingsCard {
                    ListItem(
                        headlineContent = { Text("已下载的视频") },
                        supportingContent = { Text("缓存在这台设备上，不上传账号") },
                        trailingContent = { TextButton(onClick = onOpenCache) { Text("打开") } },
                    )
                    var coverCache by remember { mutableStateOf(coverCacheSize()) }
                    ListItem(
                        headlineContent = { Text("封面缓存") },
                        supportingContent = { Text(coverCache) },
                        trailingContent = {
                            TextButton(onClick = {
                                clearCoverCache()
                                coverCache = coverCacheSize()
                            }) { Text("清除") }
                        },
                    )
                    ListItem(
                        headlineContent = { Text("短视频播放缓存") },
                        supportingContent = {
                            Text("播放器直接合流，不另存一份短视频缓存。要腾出空间，清上面的封面，或打开已下载的视频删掉离线文件。")
                        },
                    )
                }
            }
            else -> {
                BiliSectionTitle("关于", Modifier.padding(horizontal = 20.dp))
                SettingsCard {
                    ListItem(
                        headlineContent = { Text("BiliDesk") },
                        supportingContent = { Text("版本 1.2.4 · Android、Windows、macOS、Linux 共用这一套界面") },
                    )
                    ListItem(
                        headlineContent = { Text("启动时检查更新") },
                        supportingContent = { Text("打开应用大约 6 秒后查一次。关掉后仍可手动检查") },
                        trailingContent = {
                            Switch(
                                settings.autoCheckUpdate,
                                { on -> AppGraph.repo.updateSettings { it.copy(autoCheckUpdate = on) } },
                            )
                        },
                    )
                    ListItem(
                        headlineContent = { Text("检查更新") },
                        supportingContent = { Text(updateText ?: "当前版本 1.2.4") },
                        trailingContent = {
                            TextButton(onClick = {
                                scope.launch {
                                    updateText = "正在检查…"
                                    updateText = checkUpdate()
                                }
                            }) { Text("检查") }
                        },
                    )
                    updateText?.lineSequence()?.firstOrNull { it.startsWith("http") }?.let { page ->
                        TextButton(
                            onClick = { openUrl(page) },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) { Text("打开发布页") }
                    }
                    ListItem(
                        headlineContent = { Text("作者 ABing") },
                        supportingContent = { Text("打开 B 站主页") },
                        modifier = Modifier.clickable(onClick = onOpenAuthor),
                    )
                    ListItem(
                        headlineContent = { Text("非官方声明") },
                        supportingContent = { Text("首次启动时已确认，可以再看一遍") },
                        modifier = Modifier.clickable(onClick = onOpenDisclaimer),
                    )
                    Text(
                        "非官方客户端。视频版权归哔哩哔哩所有。",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(bottom = 8.dp), content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowChips(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) { content() }
}

private fun coverCacheSize(): String {
    val bytes = imageCacheDirs().sumOf { dir ->
        if (!dir.exists()) 0L else dir.walkFiles().sumOf { it.length() }
    }
    return when {
        bytes >= 1024 * 1024 -> "${(bytes / (1024.0 * 1024.0)).let { v -> (kotlin.math.round(v * 10) / 10.0) }} MB"
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}

private fun clearCoverCache() {
    imageCacheDirs().forEach { it.deleteRecursively() }
}

private suspend fun checkUpdate(): String = withContext(ioDispatcher) {
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
        val tag = json.optString("tag_name")
        val page = json.optString("html_url")
        if (tag.isBlank()) "没有读到版本信息" else "最新发布 $tag\n$page"
    }.getOrElse { "检查失败：${it.message}" }
}
