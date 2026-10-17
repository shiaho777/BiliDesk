# v1.3.0 第一批：分区热门流、搜索番剧/纪录片、音量继承

Date: 2026-10-17

## 背景

原版 BiliDesk v1.3.0（ABING1111/BiliDesk `cf76cc6`）把分区从旧 `ranking/v2` 换成现行 `region/feed/rcmd`，搜索补上番剧/纪录片，播放器音量跨视频继承。CMP 四平台客户端（Android / Windows / macOS / Linux）要对齐这三块产品行为。

本 spec 只覆盖第一批。动态卡、追番 Tab、密码登录、下载路径、桌面右键铺满/藏详情、弹幕字号速度不在范围里。

## 目标

1. 分区页给出和 B 站网页 `/c/{slug}/` 同类的近期投稿，支持滚动翻页和右下角回到顶部 / 刷新。
2. 搜索有视频 / 直播 / 番剧三个类别，记住上次选择；番剧类别能搜到番剧和纪录片，点进去能选集成播。
3. 音量调一次后，之后打开的视频、直播、本地缓存都沿用，包括静音。

## 非目标

- 不改播放器 overlay、FramePump、VLC 初始化、进出动画。
- 不恢复 iOS、不恢复 WPF。
- 不把 171 条官网 SVG path 搬进 Compose；分区图标用首字 + 色块。
- 不实现密码登录（原版走 WebView2 + 极验，四平台没有同一套）。
- 不新增追番 Tab；选集页只服务搜索点入。
- 不打真实 B 站接口做自动化测试。

## 架构

纯逻辑放在 `core/model` 和 `core/network` 的可测函数里：分区游标、搜索类别映射、`data: null` 空结果、`media_ft` 按 `seasonId` 合并、选集丢掉无 `bvid` 预告、settings 编解码。`BiliClient` 只负责 HTTP 和把响应交给这些函数。UI 在现有 `feature/region`、`feature/search`、`feature/player` 上改。选集是普通 push 页 `Dest.Bangumi`，不是 player overlay。

```
RegionPicker → RegionRoute → repo.regionFeed(tid, displayId)
                                 └ decodeRegionFeed (code 62013 = 到头)

SearchRoute  → repo.search(keyword, page, kind)
                 ├ video/live: wbi/search/type
                 └ bangumi: search/type media_bangumi + media_ft merge
             → Dest.Bangumi(seasonId) → repo.bangumiDetail
             → Dest.Play(episode.bvid)

PlayerRoute  → AppSettings.playerVolume → engine.volume
```

## 分区

### 接口

`GET https://api.bilibili.com/x/web-interface/region/feed/rcmd`

| 参数 | 值 |
|---|---|
| `display_id` | 游标，从 1 递增 |
| `request_cnt` | 固定 `"20"`（大于 20 会 62013） |
| `from_region` | **v2 tid** |
| `device` | `"web"` |
| `plat` | `"30"` |

请求前调用现有 `ensureBuvid()`。不签名。Referer 用默认。

响应：

- `code == 0`，`data.archives[]`：条目列表。
- `code == 62013`：到头。成功、空列表，不当错误。
- 其他非 0：失败，错误文案用 `message`。

条目字段：`aid/bvid/cid/title/cover/duration/pubdate/stat{view,like,danmaku}/author{mid,name}`。`parseVideo` 必须认嵌套 `author` 对象和 `cover`（已认 `pic` 回落 `cover`，但 `author` 目前只当字符串）。

首页全站排行仍走 `ranking/v2?rid=0`。`regionRank` 可留着，分区页不再调用。

### 分区表

`PartitionCatalog.all` 换成原版 30 项，tid 全部是 v2：

| 名称 | tid | 名称 | tid |
|---|---|---|---|
| 动画 | 1005 | 游戏 | 1008 |
| 鬼畜 | 1007 | 音乐 | 1003 |
| 舞蹈 | 1004 | 影视 | 1001 |
| 娱乐 | 1002 | 知识 | 1010 |
| 科技数码 | 1012 | 资讯 | 1009 |
| 美食 | 1020 | 小剧场 | 1021 |
| 汽车 | 1013 | 时尚美妆 | 1014 |
| 体育运动 | 1018 | 动物 | 1024 |
| vlog | 1029 | 绘画 | 1006 |
| 人工智能 | 1011 | 家装房产 | 1015 |
| 户外潮流 | 1016 | 健身 | 1017 |
| 手工 | 1019 | 旅游出行 | 1022 |
| 三农 | 1023 | 亲子 | 1025 |
| 健康 | 1026 | 情感 | 1027 |
| 生活兴趣 | 1030 | 生活经验 | 1031 |

`Partition` 增加 `tint: Long`（ARGB，给首字色块用）。去掉对旧 PNG `iconUrl` 的依赖；`iconUrl` 可删。

入口仍是壳层左上角 logo → `Dest.Regions`。

### 游标

`REGION_PAGE_SIZE = 20`。

一次成功取页之后：

- 本页 0 条：`noMore = true`，游标不增加（重试还打同一页）。
- 本页 > 0：`displayId += 1`。若 `fetched < 20` 或去重后新增 0 条，则 `noMore = true`。

按 `bvid` 跨页去重。补页失败只设 `error`，不清 `items`。首屏失败才整页错误。

### UI

`RegionRoute`：现有 `VideoGrid` + `onLoadMore`。右下角两枚 `SmallFloatingActionButton`：刷新、回到顶部（`LazyGridState.animateScrollToItem(0)`）。FAB 不要挡底栏：`Modifier.align(Alignment.BottomEnd).padding(16.dp)`，外层是 `Box` 包 `LoadBox`。

空文案：`这个分区暂时没有内容`。

## 搜索

### 类别

```kotlin
enum class ContentKind { Video, Live, Bangumi }
```

`AppSettings.lastSearchKind: ContentKind = ContentKind.Video`。切 tab 立刻 `updateSettings`。新开搜索页读这个值，三个 FilterChip 高亮对应项。

仓库签名从 `search(keyword, page, live: Boolean)` 改为 `search(keyword, page, kind: ContentKind)`。

### 请求

| kind | URL | search_type | WBI | Referer |
|---|---|---|---|---|
| Video | `https://api.bilibili.com/x/web-interface/wbi/search/type` | `video` | 是 | 默认 |
| Live | 同上 | `live_room` | 是 | 默认 |
| Bangumi | `https://api.bilibili.com/x/web-interface/search/type` | `media_bangumi` | 否 | `https://search.bilibili.com/` |

三类都先 `ensureBuvid()`。页大小：Live 40，其余 20。这个数只用来算 `hasMore`，不进请求参数。

`code == 0` 且 `data == null`（或没有 `result` 数组）：空结果，不是失败。错误文案：

- Video：`没有找到相关视频`
- Live：`没有找到相关直播间`
- Bangumi：`没有找到相关番剧`（合并 `media_ft` 之后仍为 0 才用）

`v_voucher` 和 `-352/-412/-403` 仍是 `blocked = true`，`搜索被风控拦截`。

### 纪录片合并

kind 为 Bangumi 时，在每一个 `code == 0` 的出口（有 result、data 为 null、没有 result 键）都再请求一次同一 URL、`search_type=media_ft`、同样 Referer、不签名。失败静默。两条结果按 `seasonId` 去重，`media_bangumi` 在前。

### 模型

```kotlin
data class BangumiItem(
    val seasonId: Long,
    val mediaId: Long = 0,
    val title: String = "",
    val cover: String = "",
    val badge: String = "",
    val indexShow: String = "",
    val seasonTypeName: String = "",
)

data class SearchPage(
    val items: List<VideoItem>,
    val bangumiItems: List<BangumiItem> = emptyList(),
    val hasMore: Boolean,
    val blocked: Boolean = false,
)
```

`hasMore`：本累积结果条数 `got`（番剧看 `bangumiItems`，其它看 `items`）> 0，且（`numResults <= 0` 则 `got >= pageSize`，否则 `page * pageSize < numResults`）。`numResults` 取主端点；合并后再算 `got`。

解析番剧：`season_id` 必填。`title` 去 HTML。`cover` 走 `normalizeUrl`。`index_show` 缺则看 `new_ep.index_show`，再缺且 `ep_size/total_count/formal_ep_count > 0` 则 `全{n}话`。`badge` 缺则看 `badge_info.text`。

### UI

三个 FilterChip：视频、直播、番剧。切类别清空当前结果并重搜（已搜过关键词时）。空状态提示随类别变。

番剧类别用海报卡（封面约 3:4、标题、角标、更新说明），点卡 `onBangumi(BangumiItem)`。视频/直播继续 `VideoGrid`。

## 选集页

搜索点入后 push `Dest.Bangumi(seasonId: Long, title: String)`。这不是 `isPlayerLayer()`。壳层 chrome 跟随底下的 tab/搜索页（现有 `playerLayers` 已经把非 Play/Live/Local 当 base）。

`GET https://api.bilibili.com/pgc/view/web/season?season_id={id}`。这个接口内容在根级 `result`，没有 `data`。`BiliClient.get` 今天只解析 `data`，选集请求必须改成能读 `result`（仅这一处，或给 `get` 增加 `bodyKey`）。不需要登录。

```kotlin
data class BangumiEpisode(
    val epId: Long,
    val bvid: String,
    val cid: Long,
    val title: String,
    val longTitle: String = "",
    val cover: String = "",
    val durationSec: Int = 0,
)

data class BangumiDetail(
    val seasonId: Long,
    val title: String,
    val cover: String,
    val evaluate: String = "",
    val episodes: List<BangumiEpisode>,
)
```

`duration` 是毫秒，四舍五入成秒。`bvid` 为空的预告丢掉。展示标题：`longTitle` 非空则 `"title longTitle"`，否则 `title`。默认高亮最后一集。点一集 `Dest.Play(bvid, cid, title = seasonTitle + " - " + displayTitle)`。

失败：页内错误 + 重试。空正片：`暂无选集`。

UI 放在 `feature/search` 的 `Season.kt`，本批不新开 feature 模块。

## 音量

`AppSettings.playerVolume: Float = 0.8f`，写入前 `coerceIn(0f, 1f)`。`0f` 是静音，照样落盘。`SettingsStore` JSON 键 `playerVolume`。缺字段默认 `0.8`。

`Player.kt` 唯一音量状态：进入时 `remember { mutableFloatStateOf(settings.playerVolume.coerceIn(0f, 1f)) }`，立刻赋给 `engine.volume`。所有改音量路径（滑杆、方向键、M、手势）走同一个 `setVolume(next)`：更新 state、engine、`updateSettings { it.copy(playerVolume = v) }`。Play / Live / Local 共用 `PlayerRoute`，三处一起生效。

## 错误处理

| 情况 | 行为 |
|---|---|
| 分区首屏失败 | 整页错误 + 重试（重拉 display_id=1） |
| 分区补页失败 | 底部一行错误，列表保留，重试同一 display_id |
| 分区 62013 / 空 archives | 到头，无红字 |
| 搜索 code 0 且 data null | 空结果文案；番剧先合并 media_ft |
| media_ft 失败 | 静默，主结果照常 |
| 搜索风控 | `blocked`，现有文案 |
| 选集失败 | 页内错误 + 重试 |
| 无 bvid 分集 | 不进列表 |

## 测试

jvmTest，kotlin.test，不访问网络。

- `PartitionCatalog`：30 项，美食 tid=1020，无旧 tid 211。
- `decodeRegionFeed`：62013 → 空成功；`author{}`+`cover` 解析出名字和封面。
- 游标：20 条推进；不足 20 停；全重复停；空页游标不增加。
- 搜索映射：三个 kind 的 type / 是否签名 / referer / pageSize。
- `data: null` + code 0 → 空成功。
- `media_ft` 按 seasonId 去重合并。
- `searchHasMore` 按对应集合算。
- 选集：无 bvid 丢掉；duration 毫秒→秒。
- settings：`playerVolume` 0 / 0.8 / 1 读写；`lastSearchKind` 落盘。
- `Dest.Bangumi` encode/decode；`isPlayerLayer()` 为 false。

## 文件

新建：

- `core/model/.../RegionFeed.kt` — 页大小、游标
- `core/model/.../SearchKind.kt` — ContentKind 请求元数据、hasMore
- `core/network/.../RegionFeedDecode.kt` — 解码 archives
- `core/network/.../SearchDecode.kt` — 空结果、合并、番剧解析
- `core/network/.../BangumiDecode.kt` — season / episode
- `feature/search/.../Season.kt` — 选集 UI
- 各模块 `jvmTest` 源码

修改：

- `Models.kt` — Partition、AppSettings、SearchPage、Bangumi 类型
- `Parse.kt` — `author` 对象
- `BiliClient.kt` — regionFeed、search、bangumiDetail、get 读 result
- `BiliRepository.kt` — 签名
- `Stores.kt` — 两字段编解码
- `Region.kt`、`Search.kt`、`Player.kt`、`AppRoot.kt`
- `core/model`、`core/network`、`core/data`、`feature/search` 的 `build.gradle.kts`（补 `jvmTest`）

## 约束

- 平台：Android、Windows、macOS、Linux。
- 一套共享 UI。播放器 overlay / FramePump / VLC 本批不动。
- 界面文案用上面写死的中文。
- 实现走 TDD：先失败测试，再最小实现。
