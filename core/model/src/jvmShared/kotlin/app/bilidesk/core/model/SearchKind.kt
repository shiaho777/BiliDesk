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
