# v1.3.0 第一批 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 分区页走 v2 tid + `region/feed/rcmd` 热门流并支持翻页/FAB；搜索记住类别并能搜番剧/纪录片后选集成播；播放器音量跨视频继承。

**Architecture:** 游标、类别映射、空结果、合并、选集过滤都做成 `core/model` / `core/network` 里的纯函数，jvmTest 用 JSON fixture 覆盖。`BiliClient` 只发 HTTP。UI 改现有 `feature/region`、`feature/search`、`feature/player`。选集是 `Dest.Bangumi` 普通 push 页，不是 player overlay。

**Tech Stack:** Kotlin 2.4.21, Compose Multiplatform 1.12.1, kotlin.test jvmTest, 现有 `JSONObject` 解析器。

**Spec:** `docs/superpowers/specs/2026-10-17-v130-region-search-volume-design.md`

## Global Constraints

- 平台只有 Android、Windows、macOS、Linux。禁止恢复 iOS 或 WPF。
- 禁止改 `FramePump`、player overlay、`PlayerEngine` VLC 初始化、进出动画。
- 禁止访问真实 B 站接口做测试。
- 每个逻辑任务：先写失败测试，再写最小实现。
- 界面文案用 spec 里的中文原句。
- 分区图标用首字 + `Partition.tint` 色块，不移植 SVG path。

---

## File map

| File | Responsibility |
|---|---|
| `core/model/.../Models.kt` | `ContentKind`、`BangumiItem`/`Episode`/`Detail`、`SearchPage.bangumiItems`、`AppSettings.playerVolume/lastSearchKind`、v2 `PartitionCatalog` |
| `core/model/.../RegionFeed.kt` | `REGION_PAGE_SIZE`、`afterRegionPage`、`RegionFeedPager` |
| `core/model/.../SearchKind.kt` | 请求元数据、`searchHasMore`、空结果文案 |
| `core/network/.../Parse.kt` | `owner` 回落嵌套 `author` |
| `core/network/.../RegionFeedDecode.kt` | `decodeRegionFeed` |
| `core/network/.../SearchDecode.kt` | 空成功、番剧解析、`mergeBangumi` |
| `core/network/.../BangumiDecode.kt` | season / episode，丢掉无 bvid |
| `core/network/.../BiliClient.kt` | `regionFeed`、`search(kind)`、`bangumiDetail`、`get` 可读 `result` |
| `core/data/.../Stores.kt` | 两字段编解码 |
| `core/data/.../BiliRepository.kt` | 新签名 |
| `feature/region/.../Region.kt` | 翻页 + FAB |
| `feature/search/.../Search.kt` | 三类别 + 海报卡 |
| `feature/search/.../Season.kt` | 薄选集页 |
| `feature/player/.../Player.kt` | `setVolume` 落盘 |
| `app/.../AppRoot.kt` | `Dest.Bangumi` encode/decode/route |

---

### Task 1: 模型、分区表、游标、搜索元数据

**Files:**
- Create: `core/model/src/jvmShared/kotlin/app/bilidesk/core/model/RegionFeed.kt`
- Create: `core/model/src/jvmShared/kotlin/app/bilidesk/core/model/SearchKind.kt`
- Create: `core/model/src/jvmTest/kotlin/app/bilidesk/core/model/RegionFeedTest.kt`
- Create: `core/model/src/jvmTest/kotlin/app/bilidesk/core/model/SearchKindTest.kt`
- Create: `core/model/src/jvmTest/kotlin/app/bilidesk/core/model/PartitionCatalogTest.kt`
- Modify: `core/model/build.gradle.kts` — 增加 `jvmTest`
- Modify: `core/model/src/jvmShared/kotlin/app/bilidesk/core/model/Models.kt`

**Interfaces:**
- Consumes: 现有 `VideoItem`、`AppSettings`
- Produces:
  - `const val REGION_PAGE_SIZE = 20`
  - `data class RegionPageOutcome(val nextDisplayId: Int, val noMore: Boolean, val addedBvids: List<String>)`
  - `fun afterRegionPage(displayId: Int, fetchedBvids: List<String>, seen: Set<String>): RegionPageOutcome`
  - `enum class ContentKind { Video, Live, Bangumi }`
  - `fun ContentKind.searchType(): String`
  - `fun ContentKind.signed(): Boolean`
  - `fun ContentKind.searchReferer(): String?` — Bangumi 返回 `https://search.bilibili.com/`，其它 `null`
  - `fun ContentKind.pageSize(): Int` — Live 40，其它 20
  - `fun searchHasMore(got: Int, page: Int, pageSize: Int, numResults: Int): Boolean`
  - `fun searchEmptyMessage(kind: ContentKind): String`
  - `data class Partition(val name: String, val tid: Int, val tint: Long = 0xFFFB7299)`
  - `AppSettings.playerVolume: Float = 0.8f`、`lastSearchKind: ContentKind = ContentKind.Video`
  - `SearchPage(items, bangumiItems = emptyList(), hasMore, blocked = false)`
  - `data class BangumiItem(seasonId, mediaId=0, title="", cover="", badge="", indexShow="", seasonTypeName="")`

- [ ] **Step 1: 给 core/model 加上 jvmTest**

在 `core/model/build.gradle.kts` 的 `sourceSets` 里 `jvmMain` 之后加入：

```kotlin
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
```

- [ ] **Step 2: 写失败测试**

`PartitionCatalogTest.kt`:

```kotlin
package app.bilidesk.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PartitionCatalogTest {
    @Test
    fun hasThirtyV2RegionsAndFoodIs1020() {
        assertEquals(30, PartitionCatalog.all.size)
        assertEquals(1020, PartitionCatalog.all.first { it.name == "美食" }.tid)
        assertNull(PartitionCatalog.all.find { it.tid == 211 })
        assertEquals(1005, PartitionCatalog.all.first { it.name == "动画" }.tid)
    }
}
```

`RegionFeedTest.kt`:

```kotlin
package app.bilidesk.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegionFeedTest {
    @Test
    fun fullPageAdvancesCursor() {
        val seen = emptySet<String>()
        val out = afterRegionPage(1, (1..20).map { "BV$it" }, seen)
        assertEquals(2, out.nextDisplayId)
        assertFalse(out.noMore)
        assertEquals(20, out.addedBvids.size)
    }

    @Test
    fun shortPageStops() {
        val out = afterRegionPage(3, listOf("BV1"), emptySet())
        assertEquals(4, out.nextDisplayId)
        assertTrue(out.noMore)
    }

    @Test
    fun allDuplicatesStop() {
        val out = afterRegionPage(5, listOf("BV1", "BV2"), setOf("BV1", "BV2"))
        assertEquals(6, out.nextDisplayId)
        assertTrue(out.noMore)
        assertEquals(emptyList(), out.addedBvids)
    }

    @Test
    fun emptyPageKeepsCursor() {
        val out = afterRegionPage(4, emptyList(), emptySet())
        assertEquals(4, out.nextDisplayId)
        assertTrue(out.noMore)
        assertEquals(emptyList(), out.addedBvids)
    }
}
```

`SearchKindTest.kt`:

```kotlin
package app.bilidesk.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchKindTest {
    @Test
    fun mapsEndpoints() {
        assertEquals("video", ContentKind.Video.searchType())
        assertEquals("live_room", ContentKind.Live.searchType())
        assertEquals("media_bangumi", ContentKind.Bangumi.searchType())
        assertTrue(ContentKind.Video.signed())
        assertTrue(ContentKind.Live.signed())
        assertFalse(ContentKind.Bangumi.signed())
        assertNull(ContentKind.Video.searchReferer())
        assertEquals("https://search.bilibili.com/", ContentKind.Bangumi.searchReferer())
        assertEquals(20, ContentKind.Video.pageSize())
        assertEquals(40, ContentKind.Live.pageSize())
        assertEquals(20, ContentKind.Bangumi.pageSize())
    }

    @Test
    fun hasMoreUsesPageSizeNotThisPageCountWhenTotalKnown() {
        assertTrue(searchHasMore(got = 20, page = 1, pageSize = 20, numResults = 50))
        assertFalse(searchHasMore(got = 20, page = 3, pageSize = 20, numResults = 50))
        assertFalse(searchHasMore(got = 0, page = 1, pageSize = 20, numResults = 0))
        assertTrue(searchHasMore(got = 20, page = 1, pageSize = 20, numResults = 0))
        assertFalse(searchHasMore(got = 5, page = 1, pageSize = 20, numResults = 0))
    }

    @Test
    fun emptyCopy() {
        assertEquals("没有找到相关视频", searchEmptyMessage(ContentKind.Video))
        assertEquals("没有找到相关直播间", searchEmptyMessage(ContentKind.Live))
        assertEquals("没有找到相关番剧", searchEmptyMessage(ContentKind.Bangumi))
    }
}
```

- [ ] **Step 3: 跑测试，确认失败**

Run: `./gradlew :core:model:jvmTest --tests app.bilidesk.core.model.PartitionCatalogTest --tests app.bilidesk.core.model.RegionFeedTest --tests app.bilidesk.core.model.SearchKindTest`

Expected: FAIL（`ContentKind` / `afterRegionPage` / 美食 1020 不存在）

- [ ] **Step 4: 最小实现**

`RegionFeed.kt`:

```kotlin
package app.bilidesk.core.model

const val REGION_PAGE_SIZE = 20

data class RegionPageOutcome(
    val nextDisplayId: Int,
    val noMore: Boolean,
    val addedBvids: List<String>,
)

fun afterRegionPage(displayId: Int, fetchedBvids: List<String>, seen: Set<String>): RegionPageOutcome {
    if (fetchedBvids.isEmpty()) {
        return RegionPageOutcome(nextDisplayId = displayId, noMore = true, addedBvids = emptyList())
    }
    val added = fetchedBvids.filter { it !in seen }
    val noMore = fetchedBvids.size < REGION_PAGE_SIZE || added.isEmpty()
    return RegionPageOutcome(nextDisplayId = displayId + 1, noMore = noMore, addedBvids = added)
}
```

`SearchKind.kt`:

```kotlin
package app.bilidesk.core.model

enum class ContentKind { Video, Live, Bangumi }

fun ContentKind.searchType(): String = when (this) {
    ContentKind.Video -> "video"
    ContentKind.Live -> "live_room"
    ContentKind.Bangumi -> "media_bangumi"
}

fun ContentKind.signed(): Boolean = this != ContentKind.Bangumi

fun ContentKind.searchReferer(): String? =
    if (this == ContentKind.Bangumi) "https://search.bilibili.com/" else null

fun ContentKind.pageSize(): Int = if (this == ContentKind.Live) 40 else 20

fun searchHasMore(got: Int, page: Int, pageSize: Int, numResults: Int): Boolean {
    if (got <= 0) return false
    return if (numResults <= 0) got >= pageSize else page * pageSize < numResults
}

fun searchEmptyMessage(kind: ContentKind): String = when (kind) {
    ContentKind.Video -> "没有找到相关视频"
    ContentKind.Live -> "没有找到相关直播间"
    ContentKind.Bangumi -> "没有找到相关番剧"
}
```

`Models.kt`：

- `Partition` 改为 `data class Partition(val name: String, val tid: Int, val tint: Long = 0xFFFB7299)`，删掉 `iconUrl` 和 `initial` 可保留 `val initial get() = name.take(1)`。
- `PartitionCatalog.all` 换成 spec 里 30 个 v2 tid。`tint` 用这些 ARGB：动画 `0xFF6D69E4`，游戏 `0xFF57D587`，鬼畜 `0xFFFB813A`，其余未列的用 `0xFFFB7299`。
- `SearchPage` 增加 `bangumiItems: List<BangumiItem> = emptyList()`。
- 增加 `BangumiItem` / `BangumiEpisode` / `BangumiDetail`（字段见 spec）。
- `AppSettings` 增加 `playerVolume: Float = 0.8f`、`lastSearchKind: ContentKind = ContentKind.Video`。

任何还引用 `Partition.iconUrl` 的调用点本任务先改成不读它（`Region.kt` 的 `AsyncImage` 先改成 `Text(part.initial)`，完整色块在 Task 7）。否则编译失败。

- [ ] **Step 5: 跑测试，确认通过**

Run: `./gradlew :core:model:jvmTest --tests app.bilidesk.core.model.PartitionCatalogTest --tests app.bilidesk.core.model.RegionFeedTest --tests app.bilidesk.core.model.SearchKindTest`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/model feature/region/src/jvmShared/kotlin/app/bilidesk/feature/region/Region.kt
git commit -m "$(cat <<'EOF'
feat: add v2 region catalog, search kinds, and feed cursor

EOF
)"
```

---

### Task 2: 分区 JSON 解码 + author 对象

**Files:**
- Create: `core/network/src/jvmShared/kotlin/app/bilidesk/core/network/RegionFeedDecode.kt`
- Create: `core/network/src/jvmTest/kotlin/app/bilidesk/core/network/RegionFeedDecodeTest.kt`
- Modify: `core/network/build.gradle.kts` — `jvmTest` 依赖 `kotlin("test")` 和 `jvmShared`（默认已有）
- Modify: `core/network/src/jvmShared/kotlin/app/bilidesk/core/network/Parse.kt`

**Interfaces:**
- Consumes: `parseVideo`、`JSONObject`、`afterRegionPage`
- Produces:
  - `data class RegionFeedDecode(val ok: Boolean, val error: String?, val items: List<VideoItem>, val ended: Boolean)`
  - `fun decodeRegionFeed(code: Int, message: String?, data: JSONObject?): RegionFeedDecode`
  - `parseVideo` 认 `obj("author")` 的 `name`/`mid`

- [ ] **Step 1: 写失败测试**

`RegionFeedDecodeTest.kt`:

```kotlin
package app.bilidesk.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegionFeedDecodeTest {
    @Test
    fun code62013IsEmptySuccess() {
        val page = decodeRegionFeed(62013, "暂时没有更多内容了", null)
        assertTrue(page.ok)
        assertTrue(page.ended)
        assertEquals(0, page.items.size)
    }

    @Test
    fun readsCoverAndNestedAuthor() {
        val json = """
            {"archives":[{"bvid":"BV1xx","aid":1,"title":"面","cover":"http://i0.hdslb.com/a.jpg",
              "duration":60,"pubdate":1,"stat":{"view":10,"like":2,"danmaku":3},
              "author":{"mid":9,"name":"厨子"}}]}
        """.trimIndent()
        val page = decodeRegionFeed(0, "ok", JSONObject(json))
        assertTrue(page.ok)
        assertEquals("BV1xx", page.items.single().bvid)
        assertEquals("厨子", page.items.single().author)
        assertEquals(9L, page.items.single().ownerMid)
        assertTrue(page.items.single().cover.contains("a.jpg"))
        assertTrue(page.items.single().cover.startsWith("https://"))
    }

    @Test
    fun otherCodesFail() {
        val page = decodeRegionFeed(-400, "请求错误", null)
        assertTrue(!page.ok)
        assertEquals("请求错误", page.error)
    }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :core:network:jvmTest --tests app.bilidesk.core.network.RegionFeedDecodeTest`

Expected: FAIL（`decodeRegionFeed` 未定义）

- [ ] **Step 3: 最小实现**

`Parse.kt` 里 `parseVideo`：

```kotlin
    val owner = e.obj("owner") ?: e.obj("author")
```

`e.str("author")` 只在 `owner` 为空或 name 为空时作回落。如果 `author` 是对象，`str("author")` 可能把对象打成字符串，所以先取对象：

```kotlin
    val owner = e.obj("owner") ?: e.obj("author")
    val authorName = owner?.str("name").orEmpty().ifBlank {
        if (e.obj("author") == null) e.str("author") else ""
    }
```

`RegionFeedDecode.kt`:

```kotlin
package app.bilidesk.core.network

import app.bilidesk.core.model.VideoItem

data class RegionFeedDecode(
    val ok: Boolean,
    val error: String?,
    val items: List<VideoItem>,
    val ended: Boolean,
)

fun decodeRegionFeed(code: Int, message: String?, data: JSONObject?): RegionFeedDecode {
    if (code == 62013) return RegionFeedDecode(true, null, emptyList(), ended = true)
    if (code != 0) return RegionFeedDecode(false, message ?: "请求失败 (code $code)", emptyList(), ended = false)
    val archives = data?.arr("archives")?.objects().orEmpty()
    val items = archives.mapNotNull { parseVideo(it) }
    return RegionFeedDecode(true, null, items, ended = items.isEmpty())
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :core:network:jvmTest --tests app.bilidesk.core.network.RegionFeedDecodeTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/network
git commit -m "$(cat <<'EOF'
feat: decode region feed archives and nested author

EOF
)"
```

---

### Task 3: 搜索解码、番剧解析、media_ft 合并

**Files:**
- Create: `core/network/src/jvmShared/kotlin/app/bilidesk/core/network/SearchDecode.kt`
- Create: `core/network/src/jvmTest/kotlin/app/bilidesk/core/network/SearchDecodeTest.kt`

**Interfaces:**
- Consumes: `ContentKind`、`BangumiItem`、`searchHasMore`、`searchEmptyMessage`、`parseSearch`、`parseLive`
- Produces:
  - `fun parseBangumiItem(e: JSONObject): BangumiItem?`
  - `fun mergeBangumi(primary: List<BangumiItem>, extra: List<BangumiItem>): List<BangumiItem>`
  - `fun decodeSearchSuccess(kind: ContentKind, page: Int, data: JSONObject?, extraFt: List<BangumiItem> = emptyList()): SearchPage`

`decodeSearchSuccess` 在 `code == 0` 时调用。`data == null` 或没有 `result` 数组时，视频/直播得到空 `items`；番剧把 `extraFt` 当结果。`hasMore` 用合并后的 `got`。

- [ ] **Step 1: 写失败测试**

```kotlin
package app.bilidesk.core.network

import app.bilidesk.core.model.ContentKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchDecodeTest {
    @Test
    fun nullDataIsEmptySuccess() {
        val page = decodeSearchSuccess(ContentKind.Video, 1, null)
        assertFalse(page.blocked)
        assertEquals(0, page.items.size)
        assertFalse(page.hasMore)
    }

    @Test
    fun parsesBangumiAndStripsHtml() {
        val e = JSONObject("""{"season_id":123,"title":"<em class=\"keyword\">咒术</em>回战","cover":"//i0.hdslb.com/c.jpg","badge":"番剧","index_show":"更新至第2话","ep_size":24}""")
        val item = parseBangumiItem(e)!!
        assertEquals(123L, item.seasonId)
        assertEquals("咒术回战", item.title)
        assertTrue(item.cover.startsWith("https://"))
        assertEquals("更新至第2话", item.indexShow)
    }

    @Test
    fun mergeDedupesBySeasonId() {
        val a = parseBangumiItem(JSONObject("""{"season_id":1,"title":"A","cover":"x"}"""))!!
        val b = parseBangumiItem(JSONObject("""{"season_id":1,"title":"A2","cover":"y"}"""))!!
        val c = parseBangumiItem(JSONObject("""{"season_id":2,"title":"纪录片","cover":"z"}"""))!!
        val merged = mergeBangumi(listOf(a), listOf(b, c))
        assertEquals(listOf(1L, 2L), merged.map { it.seasonId })
    }

    @Test
    fun documentaryOnlyLivesInFt() {
        val ft = parseBangumiItem(JSONObject("""{"season_id":9,"title":"人生一串","cover":"d","badge":"纪录片"}"""))!!
        val page = decodeSearchSuccess(ContentKind.Bangumi, 1, null, extraFt = listOf(ft))
        assertEquals(1, page.bangumiItems.size)
        assertEquals("人生一串", page.bangumiItems.single().title)
        assertFalse(page.hasMore)
    }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :core:network:jvmTest --tests app.bilidesk.core.network.SearchDecodeTest`

Expected: FAIL

- [ ] **Step 3: 最小实现**

`parseBangumiItem`：`season_id <= 0` 返回 null。`title.stripHtml()`。`cover` 用 `normalizeUrl`。`index_show` 缺则 `new_ep.index_show`，再缺且 `ep_size`/`total_count`/`formal_ep_count` > 0 则 `"全${n}话"`。`badge` 缺则 `badge_info.text`。

`mergeBangumi`：按 `seasonId` 保序，primary 在前。

`decodeSearchSuccess`：从 `data.result` 数组按 kind 解析；Bangumi 再 `mergeBangumi(parsed, extraFt)`；`numResults = data.int("numResults")`；`got` 番剧看 `bangumiItems.size` 否则 `items.size`；`hasMore = searchHasMore(got, page, kind.pageSize(), numResults)`。`blocked` 恒 false。

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :core:network:jvmTest --tests app.bilidesk.core.network.SearchDecodeTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/network
git commit -m "$(cat <<'EOF'
feat: decode bangumi search and merge media_ft

EOF
)"
```

---

### Task 4: 番剧选集解码

**Files:**
- Create: `core/network/src/jvmShared/kotlin/app/bilidesk/core/network/BangumiDecode.kt`
- Create: `core/network/src/jvmTest/kotlin/app/bilidesk/core/network/BangumiDecodeTest.kt`

**Interfaces:**
- Consumes: `BangumiDetail`、`BangumiEpisode`
- Produces: `fun parseBangumiDetail(seasonId: Long, result: JSONObject): BangumiDetail`、`fun parseBangumiEpisode(e: JSONObject): BangumiEpisode?`

- [ ] **Step 1: 写失败测试**

```kotlin
package app.bilidesk.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BangumiDecodeTest {
    @Test
    fun dropsPreviewWithoutBvid() {
        val preview = JSONObject("""{"id":1,"title":"预告","long_title":"","bvid":"","cid":0,"duration":60000}""")
        assertNull(parseBangumiEpisode(preview))
    }

    @Test
    fun durationMsBecomesSeconds() {
        val ep = JSONObject("""{"id":2,"title":"1","long_title":"起始","bvid":"BV1","cid":9,"duration":1500}""")
        val parsed = parseBangumiEpisode(ep)!!
        assertEquals(2, parsed.durationSec)
        assertEquals("BV1", parsed.bvid)
        assertEquals(9L, parsed.cid)
    }

    @Test
    fun detailKeepsOnlyPlayableEpisodes() {
        val result = JSONObject("""
            {"title":"剧","cover":"//i0.hdslb.com/c.jpg","evaluate":"简介",
             "episodes":[
               {"id":1,"title":"0","bvid":"","cid":1,"duration":1000},
               {"id":2,"title":"1","long_title":"第一集","bvid":"BV2","cid":2,"duration":1000}
             ]}
        """.trimIndent())
        val detail = parseBangumiDetail(88, result)
        assertEquals(88L, detail.seasonId)
        assertEquals("剧", detail.title)
        assertEquals(1, detail.episodes.size)
        assertEquals("BV2", detail.episodes.single().bvid)
        assertTrue(detail.cover.startsWith("https://"))
    }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :core:network:jvmTest --tests app.bilidesk.core.network.BangumiDecodeTest`

Expected: FAIL

- [ ] **Step 3: 最小实现**

`ep_id` 回落 `id`。`bvid.isBlank()` 返回 null。`durationSec = kotlin.math.round(durationMs / 1000.0).toInt()`。`parseBangumiDetail` 从 `result.arr("episodes")` mapNotNull。

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :core:network:jvmTest --tests app.bilidesk.core.network.BangumiDecodeTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/network
git commit -m "$(cat <<'EOF'
feat: parse bangumi season episodes and drop previews

EOF
)"
```

---

### Task 5: Settings 落盘音量和搜索类别

**Files:**
- Modify: `core/data/src/jvmShared/kotlin/app/bilidesk/core/data/Stores.kt`
- Create: `core/data/src/jvmTest/kotlin/app/bilidesk/core/data/SettingsCodecTest.kt`
- Modify: `core/data/build.gradle.kts` — `jvmTest` + `implementation(kotlin("test"))`

**Interfaces:**
- Consumes: `AppSettings.playerVolume`、`AppSettings.lastSearchKind`
- Produces: `internal fun decodeAppSettings(o: JSONObject): AppSettings`、`internal fun encodeAppSettings(next: AppSettings): JSONObject`。`load`/`update` 改走这两函数。`playerVolume` 读写时 `coerceIn(0f, 1f)`。缺字段：音量 0.8，类别 Video。非法枚举名回落 Video。

- [ ] **Step 1: 写失败测试**

```kotlin
package app.bilidesk.core.data

import app.bilidesk.core.model.ContentKind
import app.bilidesk.core.network.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsCodecTest {
    @Test
    fun roundTripsVolumeAndSearchKind() {
        val src = decodeAppSettings(JSONObject()).copy(playerVolume = 0f, lastSearchKind = ContentKind.Bangumi)
        val json = encodeAppSettings(src)
        val out = decodeAppSettings(json)
        assertEquals(0f, out.playerVolume)
        assertEquals(ContentKind.Bangumi, out.lastSearchKind)
    }

    @Test
    fun missingFieldsDefault() {
        val out = decodeAppSettings(JSONObject())
        assertEquals(0.8f, out.playerVolume)
        assertEquals(ContentKind.Video, out.lastSearchKind)
    }

    @Test
    fun clampsVolume() {
        val json = encodeAppSettings(decodeAppSettings(JSONObject()).copy(playerVolume = 3f))
        assertEquals(1f, json.optDouble("playerVolume"))
    }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :core:data:jvmTest --tests app.bilidesk.core.data.SettingsCodecTest`

Expected: FAIL

- [ ] **Step 3: 最小实现**

从现有 `load`/`update` 抽出 `decodeAppSettings` / `encodeAppSettings`，加上：

```kotlin
playerVolume = o.optDouble("playerVolume", 0.8).toFloat().coerceIn(0f, 1f)
lastSearchKind = runCatching { ContentKind.valueOf(o.optString("lastSearchKind", "Video")) }
    .getOrDefault(ContentKind.Video)
```

encode：

```kotlin
.put("playerVolume", next.playerVolume.coerceIn(0f, 1f).toDouble())
.put("lastSearchKind", next.lastSearchKind.name)
```

`update` 里写入前也 clamp：`val next = block(current).let { it.copy(playerVolume = it.playerVolume.coerceIn(0f, 1f)) }`。

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :core:data:jvmTest --tests app.bilidesk.core.data.SettingsCodecTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "$(cat <<'EOF'
feat: persist player volume and last search kind

EOF
)"
```

---

### Task 6: BiliClient 与 Repository

**Files:**
- Modify: `core/network/src/jvmShared/kotlin/app/bilidesk/core/network/BiliClient.kt`
- Modify: `core/data/src/jvmShared/kotlin/app/bilidesk/core/data/BiliRepository.kt`

**Interfaces:**
- Consumes: Task 2–4 的 decode 函数、`ContentKind`
- Produces:
  - `suspend fun regionFeed(tid: Int, displayId: Int): Result<RegionFeedDecode>`
  - `suspend fun search(keyword: String, page: Int, kind: ContentKind = ContentKind.Video): Result<SearchPage>`
  - `suspend fun bangumiDetail(seasonId: Long): Result<BangumiDetail>`
  - Repository 同名方法。删除 `search(..., live: Boolean)` 和分区页对 `regionRank` 的依赖（`regionRank` 方法可留）。

本任务没有新的纯函数测试。实现后跑：

`./gradlew :core:network:jvmTest :core:data:jvmTest :core:model:jvmTest`

编译 `BiliClient`/`BiliRepository` 时，`feature/search` 会因旧 `search(..., live)` 签名暂时失败。本任务同时把 `SearchViewModel.load` 改成 `search(q, page, if (live) ContentKind.Live else ContentKind.Video)`，完整三类 UI 留到 Task 8。

- [ ] **Step 1: 改 `get`，让番剧详情能读 `result`**

给 `get` 增加参数 `bodyKey: String = "data"`，用 `root.opt(bodyKey)` 替代写死的 `"data"`。其它调用不改。

- [ ] **Step 2: 实现 `regionFeed`**

```kotlin
suspend fun regionFeed(tid: Int, displayId: Int): Result<RegionFeedDecode> = api {
    ensureBuvid()
    val resp = get(
        "https://api.bilibili.com/x/web-interface/region/feed/rcmd",
        mapOf(
            "display_id" to displayId.coerceAtLeast(1).toString(),
            "request_cnt" to "20",
            "from_region" to tid.toString(),
            "device" to "web",
            "plat" to "30",
        ),
    )
    val page = decodeRegionFeed(resp.code, resp.message, resp.data)
    if (!page.ok) error(page.error ?: "加载失败")
    page
}
```

- [ ] **Step 3: 实现 `search(kind)`**

1. `ensureBuvid()`
2. `get(url, params, sign = kind.signed(), referer = kind.searchReferer() ?: REFERER)`。Bangumi URL 是非 wbi 的 `search/type`。
3. `code == 0`：若 kind 是 Bangumi，再打一次 `media_ft`（失败得到 `emptyList()`），然后 `decodeSearchSuccess(kind, page, resp.data, extraFt)`。`v_voucher` 分支保持现有 blocked 行为；Bangumi 在 voucher 分支也要尝试 merge（失败静默）。
4. `code != 0`：`-352/-412/-403` → `SearchPage(emptyList(), emptyList(), false, blocked = true)`，否则 `error(message)`。

把旧 `search(..., live: Boolean)` 删掉。

- [ ] **Step 4: 实现 `bangumiDetail`**

```kotlin
suspend fun bangumiDetail(seasonId: Long): Result<BangumiDetail> = api {
    if (seasonId <= 0) error("没有番剧号")
    val resp = get(
        "https://api.bilibili.com/pgc/view/web/season",
        mapOf("season_id" to seasonId.toString()),
        bodyKey = "result",
    )
    if (resp.code != 0 || resp.data == null) error(resp.message ?: "加载选集失败")
    parseBangumiDetail(seasonId, resp.data)
}
```

注意：`Resp.data` 在 `bodyKey=result` 时就是 season 对象。

- [ ] **Step 5: Repository 同步签名；SearchViewModel 暂时用 ContentKind 映射 live**

- [ ] **Step 6: 编译测试**

Run: `./gradlew :core:network:jvmTest :core:data:jvmTest :core:model:jvmTest :feature:search:compileKotlinJvm`

Expected: PASS / compile OK

- [ ] **Step 7: Commit**

```bash
git add core/network core/data feature/search
git commit -m "$(cat <<'EOF'
feat: wire region feed, typed search, and bangumi detail APIs

EOF
)"
```

---

### Task 7: 分区页翻页和 FAB

**Files:**
- Modify: `feature/region/src/jvmShared/kotlin/app/bilidesk/feature/region/Region.kt`
- Create: `core/model/src/jvmTest/kotlin/app/bilidesk/core/model/RegionFeedPagerTest.kt`（若把 pager 抽到 model）或直接在 ViewModel 里调用 `afterRegionPage`

**Interfaces:**
- Consumes: `repo.regionFeed`、`afterRegionPage`、`Partition.tint`
- Produces: 可翻页的 `RegionViewModel`；分区选择页用色块 + 首字

推荐在 `RegionViewModel` 内联游标，逻辑已由 `afterRegionPage` 覆盖。

- [ ] **Step 1: RegionViewModel**

字段：`items`、`loading`、`loadingMore`、`error`、`hasMore`、`displayId`（从 1 起）、`seen: MutableSet<String>`。

`reload()`：`displayId=1`，`seen.clear()`，`items=empty`，`loading=true`，调 `regionFeed(tid, 1)`。成功：用 `afterRegionPage` 推进；`items = page.items`；`error=null`。失败：`error=message`。

`loadMore()`：若 `loading || loadingMore || !hasMore || items.isEmpty()` return。`loadingMore=true`。成功则按 `addedBvids` 追加对应 `VideoItem`（用 bvid 过滤）。失败只设 `error`，不清列表。

空文案：`这个分区暂时没有内容`。`LoadBox` 的 `error` 在 `items.isNotEmpty()` 时传 `null`（底部另画一行 `error` + 文字按钮「重试」调 `loadMore`）。

- [ ] **Step 2: UI**

`RegionPickerRoute`：每个分区一个可点行，左侧 `Box(36.dp)` 背景 `Color(part.tint)` 圆角，中间 `Text(part.initial)`。

`RegionRoute`：`Box` 包内容。`VideoGrid(..., onLoadMore = if (vm.hasMore) vm::loadMore else null, state = gridState)`。右下角 `Column` 两个 `SmallFloatingActionButton`：上面刷新（`Icons.Filled.Refresh` → `vm.reload()`），下面回到顶部（`Icons.Filled.KeyboardArrowUp` → `scope.launch { gridState.animateScrollToItem(0) }`）。padding 16.dp。

- [ ] **Step 3: 编译**

Run: `./gradlew :feature:region:compileKotlinJvm :core:model:jvmTest`

Expected: compile OK，model 测试仍 PASS

- [ ] **Step 4: Commit**

```bash
git add feature/region core/model
git commit -m "$(cat <<'EOF'
feat: region hot feed with paging and corner actions

EOF
)"
```

---

### Task 8: 搜索三类 UI

**Files:**
- Modify: `feature/search/src/jvmShared/kotlin/app/bilidesk/feature/search/Search.kt`

**Interfaces:**
- Consumes: `ContentKind`、`SearchPage.bangumiItems`、`AppSettings.lastSearchKind`
- Produces: `SearchRoute(..., onBangumi: (BangumiItem) -> Unit)`；`SearchViewModel.kind`

- [ ] **Step 1: ViewModel**

用 `var kind by mutableStateOf(AppGraph.repo.settings.value.lastSearchKind)` 替换 `live`。`LaunchedEffect(kind)` 在 `searched` 时重搜。`selectKind(next)`：若相同 return；写 `updateSettings { it.copy(lastSearchKind = next) }`；`kind = next`；清空 `items`/`bangumiItems`。

`load`：调 `repo.search(q, page, kind)`。视频/直播把 `pageData.items` 追加到 `items`；番剧把 `bangumiItems` 追加到 `bangumiItems`（按 seasonId 去重）。空结果 `error = searchEmptyMessage(kind)`。blocked 文案保持 `搜索被风控拦截，过一会儿再试`。

- [ ] **Step 2: UI**

三个 `FilterChip`：视频 / 直播 / 番剧。空状态 hint：视频「视频、UP 主、直播，也可以粘贴链接」保持搜索框 label；类别旁可用 `searchEmptyMessage` 的句式改成「输入关键词，搜索你想看的…」——视频「视频」、直播「直播间」、番剧「番剧」。

`kind == Bangumi` 时用 `LazyVerticalGrid` 海报卡：`AsyncImage` 封面 `aspectRatio(3f/4f)`，标题两行，角标和 `indexShow`。点击 `onBangumi(item)`。`onLoadMore` 同样挂上。

- [ ] **Step 3: 编译**

Run: `./gradlew :feature:search:compileKotlinJvm`

Expected: compile OK。`AppRoot` 会缺 `onBangumi`，下一任务补；若本任务编译 app 失败，先给 `SearchRoute` 的 `onBangumi` 默认 `{ }`。

- [ ] **Step 4: Commit**

```bash
git add feature/search
git commit -m "$(cat <<'EOF'
feat: remember search category and show bangumi cards

EOF
)"
```

---

### Task 9: Dest.Bangumi 与选集页

**Files:**
- Create: `feature/search/src/jvmShared/kotlin/app/bilidesk/feature/search/Season.kt`
- Modify: `app/src/jvmShared/kotlin/app/bilidesk/AppRoot.kt`
- Modify: `app/src/jvmTest/kotlin/app/bilidesk/PlayerLayersTest.kt`

**Interfaces:**
- Consumes: `repo.bangumiDetail`、`BangumiDetail`
- Produces: `Dest.Bangumi(seasonId: Long, title: String)`；`encode` = `"bangumi\t$seasonId\t${title.oneLine()}"`；`isPlayerLayer()` 仍只有 Play/Live/Local

- [ ] **Step 1: 写失败测试**

在 `PlayerLayersTest.kt` 追加：

```kotlin
    @Test
    fun bangumiSeasonIsNotPlayerOverlay() {
        val season = Dest.Bangumi(8, "剧")
        assertFalse(season.isPlayerLayer())
        val (base, overlay) = playerLayers(listOf(Dest.Home, Dest.Search("咒术"), season))
        assertEquals(season, base)
        assertNull(overlay)
    }
```

把 `isPlayerLayer` 改成 `internal` 已存在，测试在同一模块可调用。

再加 encode/decode 测试：若 encode/decode 是 private，在 `AppRoot.kt` 旁已有 `decodeStack` 是 private。不要改可见性。本测试只覆盖 `isPlayerLayer` / `playerLayers`。encode 用手动读 `decode`——若无法测 encode，在 `AppRoot.kt` 把 `encode`/`decode` 保持 private，手工在实现时写 `"bangumi\t"` 分支，靠编译保证。

- [ ] **Step 2: 跑测试，确认失败**

Run: `./gradlew :app:jvmTest --tests app.bilidesk.PlayerLayersTest.bangumiSeasonIsNotPlayerOverlay`

Expected: FAIL（没有 `Dest.Bangumi`）

- [ ] **Step 3: Dest + encode/decode + route**

`Dest.Bangumi(val seasonId: Long, val title: String)`。

`SeasonRoute(seasonId, title, onBack, onPlay: (BangumiEpisode, seasonTitle: String) -> Unit)`：加载 `bangumiDetail`；顶栏 `BiliBackHeader(detail?.title ?: title)`；封面、简介、分集列表。展示名：`listOf(ep.title, ep.longTitle).filter { it.isNotBlank() }.joinToString(" ")`。点一集 `onPlay(ep, detail.title)`。错误 + 重试。`episodes.isEmpty()` 且加载完：`暂无选集`。

`AppRoot`：`is Dest.Search -> SearchRoute(..., onBangumi = { onPush(Dest.Bangumi(it.seasonId, it.title)) })`。`is Dest.Bangumi -> SeasonRoute(..., onPlay = { ep, seasonTitle -> onPush(Dest.Play(bvid = ep.bvid, cid = ep.cid, title = "$seasonTitle - ${ep.title}")) })`。

- [ ] **Step 4: 跑测试，确认通过**

Run: `./gradlew :app:jvmTest --tests app.bilidesk.PlayerLayersTest`

Expected: PASS（原 11 个 + 新 1 个）

- [ ] **Step 5: Commit**

```bash
git add app feature/search
git commit -m "$(cat <<'EOF'
feat: open bangumi season page from search

EOF
)"
```

---

### Task 10: 播放器音量继承

**Files:**
- Modify: `feature/player/src/jvmShared/kotlin/app/bilidesk/feature/player/Player.kt`

**Interfaces:**
- Consumes: `AppSettings.playerVolume`、`updateSettings`
- Produces: 所有改音量路径走 `setVolume`

Play / Live / Local 共用 `PlayerRoute`，改一处即可。禁止改 `PlayerEngine` 默认值以外的解码路径。

- [ ] **Step 1: 替换 remember 初值和写入**

把

```kotlin
var volume by remember { mutableFloatStateOf(1f) }
```

改成

```kotlin
var volume by remember { mutableFloatStateOf(settings.playerVolume.coerceIn(0f, 1f)) }
fun setVolume(next: Float) {
    val v = next.coerceIn(0f, 1f)
    volume = v
    engine.volume = v
    AppGraph.repo.updateSettings { it.copy(playerVolume = v) }
}
LaunchedEffect(Unit) { engine.volume = volume }
```

所有 `volume = ...; engine.volume = volume` 换成 `setVolume(...)`。滑杆 `onValueChange = { setVolume(it) }`。M 键：`setVolume(if (volume == 0f) 0.8f else 0f)`。

- [ ] **Step 2: 编译**

Run: `./gradlew :feature:player:compileKotlinJvm :feature:player:jvmTest`

Expected: PASS（现有 DanmakuRuntime 等测试不被这次改动破坏）

- [ ] **Step 3: Commit**

```bash
git add feature/player
git commit -m "$(cat <<'EOF'
feat: inherit player volume across videos

EOF
)"
```

---

### Task 11: 全量验证

**Files:** 无新文件

- [ ] **Step 1: 跑相关测试**

Run:

```bash
./gradlew :core:model:jvmTest :core:network:jvmTest :core:data:jvmTest :core:player:jvmTest :feature:player:jvmTest :app:jvmTest
```

Expected: 全部 PASS，0 FAIL

- [ ] **Step 2: 编译桌面与 Android 共享模块**

Run: `./gradlew :desktopApp:compileKotlin :app:compileDebugKotlinAndroid --offline`（若 offline 失败去掉 `--offline`）

Expected: compile OK

- [ ] **Step 3: 若有失败，修到绿再提交**

不要在本任务改 overlay / VLC。

---

## Self-review vs spec

| Spec 项 | Task |
|---|---|
| v2 tid + region/feed/rcmd + 62013 | 1, 2, 6, 7 |
| 30 分区、美食 1020、首字色块 | 1, 7 |
| 翻页游标、去重、FAB | 1, 7 |
| ranking/v2 首页不动 | 6 保留 ranking() |
| ContentKind + 记忆 | 1, 5, 8 |
| media_bangumi + media_ft + buvid + 空 data | 3, 6 |
| 海报卡 + 选集页 + 无 bvid 丢掉 | 4, 8, 9 |
| playerVolume 0..1 默认 0.8，三入口共用 | 5, 10 |
| 不改 overlay / FramePump / VLC | 全局约束 + Task 10 范围 |
| 不打真网测试 | 全部 jvmTest fixture |
