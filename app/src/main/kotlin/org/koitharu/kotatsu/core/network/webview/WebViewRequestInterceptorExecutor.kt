package org.koitharu.kotatsu.core.network.webview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.annotation.MainThread
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koitharu.kotatsu.browser.BrowserCallback
import org.koitharu.kotatsu.core.network.webview.adblock.AdBlock
import org.koitharu.kotatsu.core.util.ext.configureForParser
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Executor for WebView request interception operations.
 * Manages WebView lifecycle and provides high-level API for capturing HTTP requests.
 */
@Singleton
class WebViewRequestInterceptorExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adBlock: AdBlock?,
) {

    private var webViewCached: WeakReference<WebView>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Intercept WebView requests with custom filtering logic
     */
    suspend fun interceptRequests(
        url: String,
        config: InterceptionConfig,
        filterLogic: (InterceptedRequest) -> Boolean = { true }
    ): List<InterceptedRequest> = withTimeout(config.timeoutMs + 5000) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val resultDeferred = CompletableDeferred<List<InterceptedRequest>>()

                val interceptor = object : WebViewRequestInterceptor {
                    override fun shouldCaptureRequest(request: InterceptedRequest): Boolean {
                        return filterLogic(request)
                    }

                    override fun onInterceptionComplete(capturedRequests: List<InterceptedRequest>) {
                        if (!resultDeferred.isCompleted) {
                            resultDeferred.complete(capturedRequests)
                        }
                    }

                    override fun onInterceptionError(error: Throwable) {
                        if (!resultDeferred.isCompleted) {
                            resultDeferred.completeExceptionally(error)
                        }
                    }
                }

                val callback = object : BrowserCallback {
                    override fun onLoadingStateChanged(isLoading: Boolean) {}
                    override fun onTitleChanged(title: CharSequence, subtitle: CharSequence?) {}

                    override fun onHistoryChanged() {}
                }

                try {
                    val webView = obtainWebView()
                    val client = RequestInterceptorWebViewClient(callback, adBlock, config, interceptor)

                    webView.webViewClient = client
                    webView.loadUrl(url)

                    // Set up timeout handler
                    val timeoutRunnable = Runnable {
                        client.stopCapturing()
                    }
                    mainHandler.postDelayed(timeoutRunnable, config.timeoutMs)

                    // Handle result
                    resultDeferred.invokeOnCompletion { exception ->
                        mainHandler.removeCallbacks(timeoutRunnable)
                        if (exception != null) {
                            continuation.resumeWithException(exception)
                        } else {
                            continuation.resume(resultDeferred.getCompleted())
                        }
                    }

                    // Handle cancellation
                    continuation.invokeOnCancellation {
                        client.stopCapturing()
                        mainHandler.removeCallbacks(timeoutRunnable)
                        if (!resultDeferred.isCompleted) {
                            resultDeferred.cancel()
                        }
                    }

                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    /**
     * Simplified API for capturing requests matching a URL pattern
     */
    suspend fun captureWebViewUrls(
        pageUrl: String,
        urlPattern: Regex,
        timeout: Long = 30000L
    ): List<String> {
        val config = InterceptionConfig(
            timeoutMs = timeout,
            urlPattern = urlPattern,
            maxRequests = 50
        )

        val requests = interceptRequests(pageUrl, config) { request ->
            request.urlMatches(urlPattern)
        }

        return requests.map { it.url }
    }

    /**
     * Extract VRF token from MangaFire-style AJAX requests
     */
    suspend fun extractVrfToken(
        pageUrl: String,
        timeout: Long = 15000L
    ): String? {
        val vrfPattern = Regex("/ajax/read/.*[?&]vrf=([^&]+)")

        val config = InterceptionConfig(
            timeoutMs = timeout,
            urlPattern = vrfPattern,
            maxRequests = 10
        )

        val requests = interceptRequests(pageUrl, config) { request ->
            request.url.contains("/ajax/read/") && request.url.contains("vrf=")
        }

        return requests.firstOrNull()
            ?.getQueryParameter("vrf")
    }

    @MainThread
    private fun obtainWebView(): WebView {
        webViewCached?.get()?.let { cached ->
            return cached
        }

        val webView = WebView(context).apply {
            configureForParser(null)
        }

        webViewCached = WeakReference(webView)
        return webView
    }
}
