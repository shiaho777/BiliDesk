package app.bilidesk.core.network

import app.bilidesk.core.model.BangumiItem
import app.bilidesk.core.model.ContentKind
import app.bilidesk.core.model.SearchPage
import app.bilidesk.core.model.VideoItem
import app.bilidesk.core.model.normalizeUrl
import app.bilidesk.core.model.pageSize
import app.bilidesk.core.model.searchHasMore

fun parseBangumiItem(e: JSONObject): BangumiItem? {
    val seasonId = e.lng("season_id")
    if (seasonId <= 0) return null
    val indexShow = e.str("index_show").ifBlank {
        e.obj("new_ep")?.str("index_show").orEmpty()
    }.ifBlank {
        val n = listOf(e.int("ep_size"), e.int("total_count"), e.int("formal_ep_count")).firstOrNull { it > 0 } ?: 0
        if (n > 0) "全${n}话" else ""
    }
    val badge = e.str("badge").ifBlank { e.obj("badge_info")?.str("text").orEmpty() }
    return BangumiItem(
        seasonId = seasonId,
        mediaId = e.lng("media_id"),
        title = e.str("title").stripHtml(),
        cover = normalizeUrl(e.str("cover")),
        badge = badge,
        indexShow = indexShow,
        seasonTypeName = e.str("season_type_name"),
    )
}

fun mergeBangumi(primary: List<BangumiItem>, extra: List<BangumiItem>): List<BangumiItem> {
    val seen = mutableSetOf<Long>()
    return buildList {
        for (item in primary + extra) {
            if (seen.add(item.seasonId)) add(item)
        }
    }
}

fun decodeSearchSuccess(
    kind: ContentKind,
    page: Int,
    data: JSONObject?,
    extraFt: List<BangumiItem> = emptyList(),
): SearchPage {
    val results = data?.arr("result")?.objects().orEmpty()
    val items: List<VideoItem>
    val bangumiItems: List<BangumiItem>
    when (kind) {
        ContentKind.Video -> {
            items = results.mapNotNull(::parseSearch)
            bangumiItems = emptyList()
        }
        ContentKind.Live -> {
            items = results.mapNotNull(::parseLive)
            bangumiItems = emptyList()
        }
        ContentKind.Bangumi -> {
            items = emptyList()
            bangumiItems = mergeBangumi(results.mapNotNull(::parseBangumiItem), extraFt)
        }
    }
    val numResults = data?.int("numResults") ?: 0
    val got = if (kind == ContentKind.Bangumi) bangumiItems.size else items.size
    return SearchPage(
        items = items,
        bangumiItems = bangumiItems,
        hasMore = searchHasMore(got, page, kind.pageSize(), numResults),
        blocked = false,
    )
}
