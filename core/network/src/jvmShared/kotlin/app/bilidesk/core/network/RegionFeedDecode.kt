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
