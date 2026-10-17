package app.bilidesk.core.network

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

actual class HttpEngine actual constructor(
    connectTimeoutMs: Long,
    readTimeoutMs: Long,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(readTimeoutMs + connectTimeoutMs, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    actual fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        form: Map<String, String>?,
        range: String?,
        maxBytes: Int?,
    ): HttpResponse {
        val builder = Request.Builder().url(url)
        headers.forEach { (key, value) -> builder.header(key, value) }
        if (range != null) builder.header("Range", range)
        if (form != null) {
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            builder.post(body)
        } else {
            builder.method(method, null)
        }
        client.newCall(builder.build()).execute().use { resp ->
            val bytes = if (maxBytes != null) {
                val stream = resp.body?.byteStream() ?: return HttpResponse(resp.code, ByteArray(0), headersOf(resp))
                val buf = ByteArray(maxBytes)
                var n = 0
                while (n < maxBytes) {
                    val read = stream.read(buf, n, maxBytes - n)
                    if (read <= 0) break
                    n += read
                }
                buf.copyOf(n)
            } else {
                resp.body?.bytes() ?: ByteArray(0)
            }
            return HttpResponse(resp.code, bytes, headersOf(resp))
        }
    }

    private fun headersOf(resp: okhttp3.Response): Map<String, List<String>> = buildMap {
        resp.headers.names().forEach { name -> put(name, resp.headers.values(name)) }
    }
}

actual fun md5Hex(text: String): String {
    val digest = java.security.MessageDigest.getInstance("MD5").digest(text.encodeToByteArray())
    return digest.joinToString("") { b ->
        val v = b.toInt() and 0xff
        v.toString(16).padStart(2, '0')
    }
}
