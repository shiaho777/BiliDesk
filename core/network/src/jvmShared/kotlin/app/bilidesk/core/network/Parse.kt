package app.bilidesk.core.network

import app.bilidesk.core.model.Comment
import app.bilidesk.core.model.Episode
import app.bilidesk.core.model.VideoDetail
import app.bilidesk.core.model.VideoItem
import app.bilidesk.core.model.VideoPage
import app.bilidesk.core.model.formatSeconds
import app.bilidesk.core.model.normalizeUrl
import app.bilidesk.core.model.formatMonthDayHour

internal fun parseVideo(e: JSONObject): VideoItem? {
    val bvid = e.str("bvid")
    if (bvid.isBlank()) return null
    val owner = e.obj("owner")
    val stat = e.obj("stat")
    val duration = e.int("duration")
    return VideoItem(
        bvid = bvid,
        aid = e.lng("aid").takeIf { it > 0 } ?: e.lng("id"),
        cid = e.cidOrHistory(),
        title = e.str("title").stripHtml(),
        cover = normalizeUrl(e.str("pic").ifBlank { e.str("cover") }),
        author = owner?.str("name").orEmpty().ifBlank { e.str("author") },
        ownerMid = owner?.lng("mid") ?: e.lng("mid"),
        ownerFace = normalizeUrl(owner?.str("face").orEmpty()),
        durationText = if (duration > 0) formatSeconds(duration) else e.str("duration").ifBlank { e.str("length") },
        durationSec = duration,
        viewCount = stat?.lng("view") ?: e.lng("play"),
        danmakuCount = stat?.lng("danmaku") ?: 0,
        likeCount = stat?.lng("like") ?: 0,
        coinCount = stat?.lng("coin") ?: 0,
        favoriteCount = stat?.lng("favorite") ?: 0,
        pubdate = e.lng("pubdate"),
        reason = e.reasonText(),
    )
}

internal fun parseSearch(e: JSONObject): VideoItem? {
    val bvid = e.str("bvid")
    if (bvid.isBlank()) return null
    return VideoItem(
        bvid = bvid,
        aid = e.lng("aid"),
        cid = e.cidOrHistory(),
        title = e.str("title").stripHtml(),
        cover = normalizeUrl(e.str("pic")),
        author = e.str("author"),
        ownerMid = e.lng("mid"),
        durationText = e.str("duration"),
        durationSec = e.int("duration"),
        viewCount = e.lng("play"),
        danmakuCount = e.lng("video_review"),
        pubdate = e.lng("pubdate"),
    )
}

internal fun parseLive(e: JSONObject): VideoItem? = parseLiveRoom(e)

internal fun parseLiveRoom(e: JSONObject): VideoItem? {
    val room = e.lng("roomid").takeIf { it > 0 } ?: e.lng("room_id")
    if (room <= 0) return null
    val cover = listOf("cover", "user_cover", "system_cover", "keyframe")
        .firstNotNullOfOrNull { e.str(it).takeIf(String::isNotBlank) }
        .orEmpty()
    return VideoItem(
        title = e.str("title").stripHtml(),
        cover = normalizeUrl(cover),
        author = e.str("uname").ifBlank { e.str("uname") },
        ownerMid = e.lng("uid"),
        viewCount = e.lng("online"),
        roomId = room,
    )
}

internal fun parseDetail(bvid: String, d: JSONObject): VideoDetail {
    val owner = d.obj("owner")
    val stat = d.obj("stat")
    val pages = d.arr("pages")?.objects().orEmpty().map {
        VideoPage(it.lng("cid"), it.int("page"), it.str("part"), it.int("duration"))
    }
    val season = d.obj("ugc_season")
    val episodes = buildList {
        var index = 0
        val sections = season?.arr("sections")?.objects().orEmpty()
        for (section in sections) {
            val sectionTitle = section.str("title")
            for (ep in section.arr("episodes")?.objects().orEmpty()) {
                val bv = ep.str("bvid")
                if (bv.isBlank()) continue
                index++
                add(
                    Episode(
                        bvid = bv,
                        cid = ep.lng("cid"),
                        title = ep.str("title"),
                        cover = normalizeUrl(ep.str("arc_cover").ifBlank { ep.str("cover") }),
                        section = sectionTitle,
                        index = index,
                    ),
                )
            }
        }
    }
    return VideoDetail(
        bvid = bvid,
        aid = d.lng("aid"),
        cid = d.lng("cid"),
        title = d.str("title"),
        pic = normalizeUrl(d.str("pic")),
        owner = owner?.str("name").orEmpty(),
        ownerMid = owner?.lng("mid") ?: 0,
        ownerFace = normalizeUrl(owner?.str("face").orEmpty()),
        desc = d.str("desc"),
        viewCount = stat?.lng("view") ?: 0,
        likeCount = stat?.lng("like") ?: 0,
        danmakuCount = stat?.lng("danmaku") ?: 0,
        coinCount = stat?.lng("coin") ?: 0,
        favoriteCount = stat?.lng("favorite") ?: 0,
        shareCount = stat?.lng("share") ?: 0,
        durationSec = d.int("duration"),
        pages = pages,
        episodes = episodes,
        seasonTitle = season?.str("title").orEmpty(),
        pubdate = d.lng("pubdate"),
        coined = d.int("coin") == 1,
    )
}

internal fun parseComment(e: JSONObject, upperMid: Long = 0, withReplies: Boolean = true): Comment {
    val member = e.obj("member")
    val content = e.obj("content")
    val control = e.obj("reply_control")
    val mid = member?.lng("mid") ?: 0
    val replies = if (withReplies) {
        e.arr("replies")?.objects().orEmpty().map { parseComment(it, upperMid, withReplies = false) }
    } else {
        emptyList()
    }
    val location = control?.str("location").orEmpty()
        .removePrefix("IP属地：")
        .removePrefix("IP属地:")
        .trim()
    val action = e.int("action")
    return Comment(
        rpid = e.lng("rpid"),
        name = member?.str("uname").orEmpty(),
        face = normalizeUrl(member?.str("avatar").orEmpty()),
        message = content?.str("message").orEmpty(),
        like = e.int("like"),
        timeText = control?.str("time_desc").orEmpty().ifBlank { commentTime(e.lng("ctime")) },
        replyCount = e.int("rcount"),
        mid = mid,
        location = location,
        level = member?.obj("level_info")?.int("current_level") ?: 0,
        up = upperMid > 0 && mid == upperMid,
        liked = action == 1,
        disliked = action == 2,
        replies = replies,
    )
}

internal fun parseDynamic(e: JSONObject): VideoItem? {
    val typeNum = e.int("type")
    if (typeNum == 1) {
        val orig = e.obj("orig") ?: return null
        return parseDynamic(orig)
    }
    val flatVideo = typeNum == 8 || (typeNum == 0 && e.str("title").isNotBlank() && e.str("cover").isNotBlank())
    if (flatVideo) {
        var bvid = bvidFromJump(e.str("jump_url"))
        if (bvid.isBlank()) bvid = e.str("bvid")
        if (bvid.isBlank()) return null
        val author = e.obj("author")
        return VideoItem(
            bvid = bvid,
            aid = e.str("rid").toLongOrNull() ?: 0,
            cid = e.cidOrHistory(),
            title = e.str("title").stripHtml(),
            cover = normalizeUrl(e.str("cover")),
            author = author?.str("name").orEmpty(),
            ownerMid = author?.lng("mid") ?: e.lng("uid"),
            durationText = e.str("duration_text"),
            durationSec = e.int("duration"),
        )
    }
    val major = e.obj("modules")?.obj("module_dynamic")?.obj("major") ?: return null
    val archive = when (major.str("type")) {
        "MAJOR_TYPE_ARCHIVE" -> major.obj("archive")
        "MAJOR_TYPE_FORWARD" -> major.obj("orig")?.let { parseDynamic(it); null }
        else -> null
    } ?: return null
    val bvid = archive.str("bvid")
    if (bvid.isBlank()) return null
    val author = e.obj("modules")?.obj("module_author")
    val stat = archive.obj("stat")
    return VideoItem(
        bvid = bvid,
        aid = archive.lng("aid"),
        cid = archive.cidOrHistory(),
        title = archive.str("title").stripHtml(),
        cover = normalizeUrl(archive.str("cover")),
        author = author?.str("name").orEmpty(),
        ownerMid = author?.lng("mid") ?: 0,
        durationText = archive.str("duration_text"),
        durationSec = archive.int("duration"),
        viewCount = stat?.lng("view") ?: 0,
        danmakuCount = stat?.lng("danmaku") ?: 0,
    )
}

internal fun JSONObject.reasonText(): String {
    val raw = opt("rcmd_reason") ?: return ""
    return when (raw) {
        is JSONObject -> raw.str("content")
        is String -> raw
        else -> ""
    }
}

internal fun JSONObject.cidOrHistory(): Long {
    val direct = lng("cid")
    if (direct > 0) return direct
    return obj("history")?.lng("cid") ?: 0L
}

private fun bvidFromJump(url: String): String {
    val i = url.indexOf("/video/")
    if (i < 0) return ""
    val rest = url.substring(i + "/video/".length)
    return rest.substringBefore('?').substringBefore('/')
}

private fun commentTime(epoch: Long): String = formatMonthDayHour(epoch)
