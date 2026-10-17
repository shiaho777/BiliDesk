package app.bilidesk.core.network

fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)

fun JSONObject.arr(key: String): JSONArray? = optJSONArray(key)

fun JSONObject.str(key: String): String {
    if (!has(key) || isNull(key)) return ""
    return optString(key, "")
}

fun JSONObject.lng(key: String): Long {
    if (!has(key) || isNull(key)) return 0
    val v = opt(key)
    return when (v) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: 0L
        else -> 0L
    }
}

fun JSONObject.int(key: String): Int = lng(key).toInt()

fun JSONObject.dbl(key: String): Double {
    if (!has(key) || isNull(key)) return 0.0
    val v = opt(key)
    return when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
}

fun JSONArray.objects(): List<JSONObject> = buildList {
    for (i in 0 until length()) {
        val v = opt(i)
        if (v is JSONObject) add(v)
    }
}

fun String.stripHtml(): String = replace(Regex("<[^>]+>"), "")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
