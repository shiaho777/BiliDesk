package app.bilidesk.core.data

import app.bilidesk.core.model.AppSettings
import app.bilidesk.core.model.CacheEntry
import app.bilidesk.core.model.CdnMode
import app.bilidesk.core.model.ContentKind
import app.bilidesk.core.model.PlayInfo
import app.bilidesk.core.model.Session
import app.bilidesk.core.model.ShellLayout
import app.bilidesk.core.model.SponsorCategories
import app.bilidesk.core.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import app.bilidesk.core.network.HttpEngine
import app.bilidesk.core.network.JSONArray
import app.bilidesk.core.network.JSONObject
import app.bilidesk.core.network.get

class SessionStore(private val file: AppFile) {
    private val _session = MutableStateFlow(Session())
    val session: StateFlow<Session> = _session
    var current: Session
        get() = _session.value
        private set(value) {
            _session.value = value
        }

    fun load() {
        current = runCatching {
            if (!file.exists()) return@runCatching Session()
            val o = JSONObject(file.readText())
            Session(
                sessData = o.optString("sessData").ifBlank { null },
                biliJct = o.optString("biliJct").ifBlank { null },
                dedeUserId = o.optString("dedeUserId").ifBlank { null },
                dedeUserIdCkMd5 = o.optString("dedeUserIdCkMd5").ifBlank { null },
                buvid3 = o.optString("buvid3").ifBlank { null },
                buvid4 = o.optString("buvid4").ifBlank { null },
                bNut = o.optString("bNut").ifBlank { null },
            )
        }.getOrDefault(Session())
    }

    fun save(session: Session) {
        current = session
        runCatching {
            file.parentFile?.mkdirs()
            val o = JSONObject()
                .put("sessData", session.sessData)
                .put("biliJct", session.biliJct)
                .put("dedeUserId", session.dedeUserId)
                .put("dedeUserIdCkMd5", session.dedeUserIdCkMd5)
                .put("buvid3", session.buvid3)
                .put("buvid4", session.buvid4)
                .put("bNut", session.bNut)
            file.writeText(o.toString(2))
        }
    }
}

class SettingsStore(private val file: AppFile) {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings
    val current: AppSettings get() = _settings.value

    fun load() {
        _settings.value = runCatching {
            if (!file.exists()) return@runCatching AppSettings()
            decodeAppSettings(JSONObject(file.readText()))
        }.getOrDefault(AppSettings())
    }

    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(current).let { it.copy(playerVolume = it.playerVolume.coerceIn(0f, 1f)) }
        _settings.value = next
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(encodeAppSettings(next).toString(2))
        }
    }
}

internal fun decodeAppSettings(o: JSONObject): AppSettings = AppSettings(
    theme = runCatching { ThemeMode.valueOf(o.optString("theme", "System")) }.getOrDefault(ThemeMode.System),
    shellLayout = runCatching { ShellLayout.valueOf(o.optString("shellLayout", "Auto")) }.getOrDefault(ShellLayout.Auto),
    danmakuEnabled = o.optBoolean("danmakuEnabled", true),
    danmakuAreaPercent = o.optInt("danmakuAreaPercent", 25).coerceIn(25, 100),
    danmakuSmartFilter = o.optBoolean("danmakuSmartFilter", true),
    danmakuColorful = o.optBoolean("danmakuColorful", true),
    danmakuBlockKeywords = o.optString("danmakuBlockKeywords"),
    danmakuFilterScroll = o.optBoolean("danmakuFilterScroll", true),
    danmakuFilterFixed = o.optBoolean("danmakuFilterFixed", true),
    danmakuFilterColorful = o.optBoolean("danmakuFilterColorful", true),
    danmakuFilterAdvanced = o.optBoolean("danmakuFilterAdvanced", true),
    defaultQn = o.optInt("defaultQn", 80),
    noLogin1080 = o.optBoolean("noLogin1080", true),
    sponsorBlock = o.optBoolean("sponsorBlock", false),
    sponsorCategories = SponsorCategories.sanitize(o.optString("sponsorCategories")),
    cdnMode = runCatching { CdnMode.valueOf(o.optString("cdnMode", "Auto")) }.getOrDefault(CdnMode.Auto),
    cdnManualId = o.optString("cdnManualId", "ali").ifBlank { "ali" },
    blockPcdn = o.optBoolean("blockPcdn", true),
    accentHex = o.optString("accentHex"),
    accentFollowSystem = o.optBoolean("accentFollowSystem", false),
    autoCheckUpdate = o.optBoolean("autoCheckUpdate", true),
    skippedVersion = o.optString("skippedVersion"),
    acceptedDisclaimerVersion = run {
        val stored = o.optInt("acceptedDisclaimerVersion", 0)
        when {
            stored > 0 -> stored
            o.optBoolean("disclaimerAccepted", false) -> 1
            else -> 0
        }
    },
    closeToTray = o.optBoolean("closeToTray", true),
    playerVolume = o.optDouble("playerVolume", 0.8).toFloat().coerceIn(0f, 1f),
    lastSearchKind = runCatching { ContentKind.valueOf(o.optString("lastSearchKind", "Video")) }
        .getOrDefault(ContentKind.Video),
)

internal fun encodeAppSettings(next: AppSettings): JSONObject = JSONObject()
    .put("theme", next.theme.name)
    .put("shellLayout", next.shellLayout.name)
    .put("danmakuEnabled", next.danmakuEnabled)
    .put("danmakuAreaPercent", next.danmakuAreaPercent)
    .put("danmakuSmartFilter", next.danmakuSmartFilter)
    .put("danmakuColorful", next.danmakuColorful)
    .put("danmakuBlockKeywords", next.danmakuBlockKeywords)
    .put("danmakuFilterScroll", next.danmakuFilterScroll)
    .put("danmakuFilterFixed", next.danmakuFilterFixed)
    .put("danmakuFilterColorful", next.danmakuFilterColorful)
    .put("danmakuFilterAdvanced", next.danmakuFilterAdvanced)
    .put("defaultQn", next.defaultQn)
    .put("noLogin1080", next.noLogin1080)
    .put("sponsorBlock", next.sponsorBlock)
    .put("sponsorCategories", SponsorCategories.sanitize(next.sponsorCategories))
    .put("cdnMode", next.cdnMode.name)
    .put("cdnManualId", next.cdnManualId)
    .put("blockPcdn", next.blockPcdn)
    .put("accentHex", next.accentHex)
    .put("accentFollowSystem", next.accentFollowSystem)
    .put("autoCheckUpdate", next.autoCheckUpdate)
    .put("skippedVersion", next.skippedVersion)
    .put("acceptedDisclaimerVersion", next.acceptedDisclaimerVersion)
    .put("closeToTray", next.closeToTray)
    .put("playerVolume", next.playerVolume.coerceIn(0f, 1f).toDouble())
    .put("lastSearchKind", next.lastSearchKind.name)

class SearchHistory(private val file: AppFile) {
    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items

    fun load() {
        _items.value = runCatching {
            if (!file.exists()) return@runCatching emptyList()
            val arr = JSONArray(file.readText())
            buildList { for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) } }
        }.getOrDefault(emptyList())
    }

    fun add(keyword: String) {
        val word = keyword.trim()
        if (word.isEmpty()) return
        val next = (listOf(word) + _items.value.filter { it != word }).take(20)
        _items.value = next
        persist()
    }

    fun clear() {
        _items.value = emptyList()
        persist()
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val arr = JSONArray()
            _items.value.forEach { arr.put(it) }
            file.writeText(arr.toString())
        }
    }
}

class OfflineCache(private val root: AppFile) {
    fun list(): List<CacheEntry> {
        val dir = root
        if (!dir.exists()) return emptyList()
        return dir.listFiles().mapNotNull { folder ->
            val meta = AppFile(folder, "meta.json")
            if (!meta.exists()) return@mapNotNull null
            runCatching {
                val o = JSONObject(meta.readText())
                val video = AppFile(folder, o.optString("video", "video.m4s"))
                if (!video.exists()) return@runCatching null
                val audio = AppFile(folder, o.optString("audio", "audio.m4s")).takeIf { it.exists() }
                CacheEntry(
                    bvid = o.optString("bvid"),
                    title = o.optString("title"),
                    cover = o.optString("cover"),
                    videoPath = video.absolutePath,
                    audioPath = audio?.absolutePath,
                    qn = o.optInt("qn"),
                )
            }.getOrNull()
        }.sortedByDescending { AppFile(it.videoPath).lastModified() }
    }

    fun download(bvid: String, title: String, cover: String, info: PlayInfo, client: HttpEngine): CacheEntry {
        val folder = AppFile(root, bvid.ifBlank { "local" })
        folder.mkdirs()
        val video = AppFile(folder, "video.m4s")
        val audio = AppFile(folder, "audio.m4s")
        fetch(client, info.videoUrl, video)
        val audioUrl = info.audioUrl
        if (audioUrl != null) fetch(client, audioUrl, audio)
        val meta = JSONObject()
            .put("bvid", bvid)
            .put("title", title)
            .put("cover", cover)
            .put("qn", info.actualQn)
            .put("video", "video.m4s")
            .put("audio", "audio.m4s")
        AppFile(folder, "meta.json").writeText(meta.toString())
        return CacheEntry(bvid, title, cover, video.absolutePath, audio.takeIf { it.exists() }?.absolutePath, info.actualQn)
    }

    fun delete(bvid: String) {
        AppFile(root, bvid).deleteRecursively()
    }

    private fun fetch(client: HttpEngine, url: String, dest: AppFile) {
        val resp = client.get(
            url,
            mapOf(
                "Referer" to "https://www.bilibili.com/",
                "User-Agent" to app.bilidesk.core.model.USER_AGENT,
            ),
        )
        if (!resp.isSuccessful) error("下载失败 ${resp.code}")
        dest.writeBytes(resp.body)
    }
}

fun appFiles(): AppFile = appStorageDir()
