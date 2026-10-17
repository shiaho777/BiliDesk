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

fun searchPageError(blocked: Boolean): String? =
    if (blocked) "搜索被风控拦截，过一会儿再试" else null

fun searchKindSwitchShouldLoad(keyword: String): Boolean = keyword.trim().isNotBlank()

data class SearchKindSwitchState(
    val loadGen: Int,
    val loading: Boolean,
    val searched: Boolean,
)

fun searchKindSwitch(keyword: String, loadGen: Int, searched: Boolean): SearchKindSwitchState {
    val reload = searched && searchKindSwitchShouldLoad(keyword)
    return SearchKindSwitchState(
        loadGen = loadGen + 1,
        loading = reload,
        searched = reload,
    )
}
