package app.bilidesk.core.model

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed class UrlHit {
    data class Video(val bvid: String) : UrlHit()
    data class Space(val mid: Long) : UrlHit()
    data class Live(val roomId: Long) : UrlHit()
    data class Keyword(val word: String) : UrlHit()
}

/**
 * 搜索框里粘贴的 B 站链接：视频 BV / av、UP 空间、直播间、搜索词。
 * 算法常量和 av→bv 换算与网页端一致。
 */
object UrlParser {
    private const val BV_XOR = 23442827791579L
    private const val BV_MASK = 2251799813685247L
    private const val BV_MAX_AID = 1L shl 51
    private const val BV_ALPHABET = "FcwAPNKTMug3GV5Lj7EJnHpWsx4tb8haYeviqBz6rkCy12mUSDQX9RdoZf"
    private val BV_ENCODE_MAP = intArrayOf(8, 7, 0, 5, 1, 3, 2, 4, 6)

    private val bvidExact = Regex("""^BV[0-9A-Za-z]{10}$""", RegexOption.IGNORE_CASE)
    private val bvidFind = Regex("""BV[0-9A-Za-z]{10}""")
    private val avExact = Regex("""^av(\d+)$""", RegexOption.IGNORE_CASE)
    private val avFind = Regex("""(?:^|/)av(\d+)""", RegexOption.IGNORE_CASE)
    private val spaceFind = Regex("""(?:space\.bilibili\.com/|/space/)(\d+)""")
    private val liveFind = Regex("""live\.bilibili\.com/(\d+)""")
    private val keywordFind = Regex("""[?&]keyword=([^&]+)""")

    fun avToBv(aid: Long): String {
        if (aid <= 0) return ""
        val body = CharArray(9)
        var tmp = (BV_MAX_AID or aid) xor BV_XOR
        for (i in BV_ENCODE_MAP.indices) {
            body[BV_ENCODE_MAP[i]] = BV_ALPHABET[(tmp % 58).toInt()]
            tmp /= 58
        }
        return "BV1" + body.concatToString()
    }

    fun bvToAv(bvid: String): Long {
        if (bvid.length < 12 || !bvid.startsWith("BV1")) return 0
        val body = bvid.substring(3)
        var tmp = 0L
        for (i in BV_ENCODE_MAP.lastIndex downTo 0) {
            val idx = BV_ALPHABET.indexOf(body[BV_ENCODE_MAP[i]])
            if (idx < 0) return 0
            tmp = tmp * 58 + idx
        }
        return (tmp and BV_MASK) xor BV_XOR
    }

    fun parse(raw: String): UrlHit {
        val text = raw.trim()
        if (text.isEmpty()) return UrlHit.Keyword("")
        if (bvidExact.matches(text)) return UrlHit.Video(normalizeBvid(text))
        avExact.matchEntire(text)?.let { m ->
            val bv = avToBv(m.groupValues[1].toLongOrNull() ?: 0)
            if (bv.isNotEmpty()) return UrlHit.Video(bv)
        }
        val looksUrl = text.contains("://") ||
            text.startsWith("//") ||
            text.contains("bilibili.com", ignoreCase = true) ||
            text.contains("b23.tv", ignoreCase = true)
        if (!looksUrl) return UrlHit.Keyword(text)
        bvidFind.find(text)?.let { return UrlHit.Video(normalizeBvid(it.value)) }
        liveFind.find(text)?.let { return UrlHit.Live(it.groupValues[1].toLong()) }
        spaceFind.find(text)?.let { return UrlHit.Space(it.groupValues[1].toLong()) }
        avFind.find(text)?.let { m ->
            val bv = avToBv(m.groupValues[1].toLongOrNull() ?: 0)
            if (bv.isNotEmpty()) return UrlHit.Video(bv)
        }
        keywordFind.find(text)?.let { m ->
            val word = runCatching {
                URLDecoder.decode(m.groupValues[1].replace('+', ' '), StandardCharsets.UTF_8)
            }.getOrDefault(m.groupValues[1])
            if (word.isNotBlank()) return UrlHit.Keyword(word)
        }
        return UrlHit.Keyword(text)
    }

    private fun normalizeBvid(bvid: String): String =
        if (bvid.length >= 2) "BV" + bvid.substring(2) else bvid
}
