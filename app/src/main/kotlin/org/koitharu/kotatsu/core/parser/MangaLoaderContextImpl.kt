package org.koitharu.kotatsu.core.parser

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.koitharu.kotatsu.core.exceptions.InteractiveActionRequiredException
import org.koitharu.kotatsu.core.image.BitmapDecoderCompat
import org.koitharu.kotatsu.core.network.MangaHttpClient
import org.koitharu.kotatsu.core.network.cookies.MutableCookieJar
import org.koitharu.kotatsu.core.network.webview.WebViewExecutor
import org.koitharu.kotatsu.core.prefs.SourceSettings
import org.koitharu.kotatsu.core.util.ext.toList
import org.koitharu.kotatsu.core.util.ext.toMimeType
import org.koitharu.kotatsu.core.util.ext.use
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.core.network.webview.WebViewRequestInterceptorExecutor
import org.koitharu.kotatsu.parsers.webview.InterceptedRequest
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.map
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.use

@Singleton
class MangaLoaderContextImpl @Inject constructor(
    @MangaHttpClient override val httpClient: OkHttpClient,
    override val cookieJar: MutableCookieJar,
    @ApplicationContext private val androidContext: Context,
    private val webViewExecutor: WebViewExecutor,
    private val webViewRequestInterceptorExecutor: WebViewRequestInterceptorExecutor,
) : MangaLoaderContext() {

    private val webViewUserAgent by lazy { obtainWebViewUserAgent() }
    private val jsTimeout = TimeUnit.SECONDS.toMillis(4)

    @Deprecated("Provide a base url")
    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun evaluateJs(script: String): String? = evaluateJs("", script)

    override suspend fun evaluateJs(baseUrl: String, script: String): String? = withTimeout(jsTimeout) {
        webViewExecutor.evaluateJs(baseUrl, script)
    }

    override fun getDefaultUserAgent(): String = webViewUserAgent

    override fun getConfig(source: MangaSource): MangaSourceConfig {
        return SourceSettings(androidContext, source)
    }

    override fun encodeBase64(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    override fun decodeBase64(data: String): ByteArray {
        return Base64.decode(data, Base64.DEFAULT)
    }

    override fun getPreferredLocales(): List<Locale> {
        return LocaleListCompat.getAdjustedDefault().toList()
    }

    override fun requestBrowserAction(
        parser: MangaParser,
        url: String,
    ): Nothing = throw InteractiveActionRequiredException(parser.source, url)

    override fun redrawImageResponse(response: Response, redraw: (image: Bitmap) -> Bitmap): Response {
        return response.map { body ->
            BitmapDecoderCompat.decode(body.byteStream(), body.contentType()?.toMimeType(), isMutable = true)
                .use { bitmap ->
                    (redraw(BitmapWrapper.create(bitmap)) as BitmapWrapper).use { result ->
                        Buffer().also {
                            result.compressTo(it.outputStream())
                        }.asResponseBody("image/jpeg".toMediaType())
                    }
                }
        }
    }

    override fun createBitmap(width: Int, height: Int): Bitmap = BitmapWrapper.create(width, height)

    override suspend fun interceptWebViewRequests(
        url: String,
        interceptorScript: String,
        timeout: Long
    ): List<InterceptedRequest> {
        val config = org.koitharu.kotatsu.core.network.webview.InterceptionConfig(
            timeoutMs = timeout,
            maxRequests = 100,
            filterScript = interceptorScript
        )

        return webViewRequestInterceptorExecutor.interceptRequests(url, config) { request ->
            // TODO: Evaluate the interceptorScript with request data
            // For now, capture all requests and let the script filter later
            true
        }.map { appRequest ->
            // Convert from app InterceptedRequest to parsers InterceptedRequest
            InterceptedRequest(
                url = appRequest.url,
                method = appRequest.method,
                headers = appRequest.headers,
                timestamp = appRequest.timestamp,
                body = appRequest.body
            )
        }
    }

    override suspend fun captureWebViewUrls(
        pageUrl: String,
        urlPattern: Regex,
        timeout: Long
    ): List<String> {
        return webViewRequestInterceptorExecutor.captureWebViewUrls(pageUrl, urlPattern, timeout)
    }

    override suspend fun extractVrfToken(
        pageUrl: String,
        timeout: Long
    ): String? {
        return webViewRequestInterceptorExecutor.extractVrfToken(pageUrl, timeout)
    }

    private fun obtainWebViewUserAgent(): String {
        val mainDispatcher = Dispatchers.Main.immediate
        return if (!mainDispatcher.isDispatchNeeded(EmptyCoroutineContext)) {
            webViewExecutor.getDefaultUserAgentSync()
        } else {
            runBlocking(mainDispatcher) {
                webViewExecutor.getDefaultUserAgentSync()
            }
        } ?: UserAgents.FIREFOX_MOBILE
    }
}
