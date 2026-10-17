package app.bilidesk.core.designsystem

import androidx.compose.runtime.Composable
import app.bilidesk.core.model.USER_AGENT
import app.bilidesk.core.network.SharedHttp
import app.bilidesk.core.network.get
import coil3.ImageLoader
import coil3.Uri
import coil3.compose.setSingletonImageLoaderFactory
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import app.bilidesk.core.model.ioDispatcher
import kotlinx.coroutines.withContext
import okio.Buffer

@Composable
fun InstallBiliImages() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(BiliImageFetcher.Factory()) }
            .build()
    }
}

private class BiliImageFetcher(
    private val url: String,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(ioDispatcher) {
        val resp = SharedHttp.default.get(
            url,
            mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "https://www.bilibili.com/",
            ),
        )
        if (!resp.isSuccessful || resp.body.isEmpty()) return@withContext null
        val buffer = Buffer().write(resp.body)
        SourceFetchResult(
            source = ImageSource(source = buffer, fileSystem = options.fileSystem),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme ?: return null
            if (scheme != "http" && scheme != "https") return null
            return BiliImageFetcher(data.toString(), options)
        }
    }
}
