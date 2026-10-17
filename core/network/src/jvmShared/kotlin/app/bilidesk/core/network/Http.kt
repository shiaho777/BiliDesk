package app.bilidesk.core.network

class HttpResponse(
    val code: Int,
    val body: ByteArray,
    private val headerMap: Map<String, List<String>>,
) {
    val isSuccessful: Boolean get() = code in 200..299
    val bodyText: String get() = body.decodeToString()

    fun header(name: String): String? =
        headerMap.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

    fun headers(name: String): List<String> =
        headerMap.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value.orEmpty()
}

expect class HttpEngine(connectTimeoutMs: Long, readTimeoutMs: Long) {
    fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        form: Map<String, String>?,
        range: String?,
        maxBytes: Int?,
    ): HttpResponse
}

fun HttpEngine.get(
    url: String,
    headers: Map<String, String> = emptyMap(),
    range: String? = null,
    maxBytes: Int? = null,
): HttpResponse = execute("GET", url, headers, null, range, maxBytes)

fun HttpEngine.post(
    url: String,
    form: Map<String, String>,
    headers: Map<String, String> = emptyMap(),
): HttpResponse = execute("POST", url, headers, form, null, null)

object SharedHttp {
    val default by lazy { HttpEngine(15_000, 60_000) }
    val api by lazy { HttpEngine(12_000, 20_000) }
    val probe by lazy { HttpEngine(3_000, 3_000) }
}

internal expect fun md5Hex(text: String): String
