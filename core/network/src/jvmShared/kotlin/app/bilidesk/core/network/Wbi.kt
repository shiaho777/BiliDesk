package app.bilidesk.core.network

import app.bilidesk.core.model.USER_AGENT
import kotlin.io.encoding.Base64
import kotlin.random.Random

internal val MIXIN_TAB = intArrayOf(
    46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
    33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61,
    26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36,
    20, 34, 44, 52,
)

internal fun urlEncode(value: String): String = buildString(value.length + 8) {
    val hex = "0123456789ABCDEF"
    for (b in value.encodeToByteArray()) {
        val c = b.toInt() and 0xff
        val keep = c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
            c == '-'.code || c == '_'.code || c == '.'.code || c == '*'.code
        when {
            keep -> append(c.toChar())
            c == ' '.code -> append('+')
            else -> {
                append('%')
                append(hex[c shr 4])
                append(hex[c and 0x0F])
            }
        }
    }
}

/** 与 B 站前端一致：URL 编码后去掉 ! ' ( ) * */
internal fun wbiEnc(value: String): String {
    val encoded = urlEncode(value)
        .replace("+", "%20")
        .replace("%21", "!")
        .replace("%27", "'")
        .replace("%28", "(")
        .replace("%29", ")")
        .replace("%2A", "*")
    return encoded.filter { it != '!' && it != '\'' && it != '(' && it != ')' && it != '*' }
}

internal fun signParams(params: MutableMap<String, String>, mixin: String) {
    params["wts"] = (app.bilidesk.core.model.epochMs() / 1000).toString()
    val query = params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${wbiEnc(it.value)}" }
    params["w_rid"] = md5Hex(query + mixin)
}

internal fun mixinKey(img: String, sub: String): String {
    val raw = img + sub
    return buildString(32) {
        for (i in MIXIN_TAB) if (i < raw.length) append(raw[i])
    }
}

internal fun fileNameOnly(url: String): String {
    val file = url.substringAfterLast('/')
    val dot = file.lastIndexOf('.')
    return if (dot > 0) file.substring(0, dot) else file
}

internal fun randomBase64(minLen: Int, maxLen: Int): String {
    val n = Random.nextInt(minLen, maxLen + 1)
    val bytes = ByteArray(n)
    Random.nextBytes(bytes)
    return Base64.encode(bytes)
}

internal fun randomHex(len: Int): String {
    val hex = "0123456789ABCDEF"
    return buildString(len) {
        repeat(len) { append(hex[Random.nextInt(16)]) }
    }
}

internal const val REFERER = "https://www.bilibili.com/"

internal fun defaultHeaders(referer: String, cookie: String): Map<String, String> = buildMap {
    put("User-Agent", USER_AGENT)
    put("Referer", referer)
    put("Accept", "application/json, text/plain, */*")
    put("Accept-Language", "zh-CN,zh;q=0.9")
    if (cookie.isNotEmpty()) put("Cookie", cookie)
}
