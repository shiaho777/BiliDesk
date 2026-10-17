package app.bilidesk.core.network

import app.bilidesk.core.model.BangumiDetail
import app.bilidesk.core.model.BangumiEpisode
import app.bilidesk.core.model.normalizeUrl

fun parseBangumiEpisode(e: JSONObject): BangumiEpisode? {
    val bvid = e.str("bvid")
    if (bvid.isBlank()) return null
    val durationMs = e.dbl("duration")
    return BangumiEpisode(
        epId = e.lng("ep_id").takeIf { it > 0 } ?: e.lng("id"),
        bvid = bvid,
        cid = e.lng("cid"),
        title = e.str("title"),
        longTitle = e.str("long_title"),
        cover = normalizeUrl(e.str("cover")),
        durationSec = kotlin.math.round(durationMs / 1000.0).toInt(),
    )
}

fun parseBangumiDetail(seasonId: Long, result: JSONObject): BangumiDetail = BangumiDetail(
    seasonId = seasonId,
    title = result.str("title"),
    cover = normalizeUrl(result.str("cover")),
    evaluate = result.str("evaluate"),
    episodes = result.arr("episodes")?.objects().orEmpty().mapNotNull(::parseBangumiEpisode),
)
