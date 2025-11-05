package org.koitharu.kotatsu.core.network.webview

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.annotation.WorkerThread
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.browser.BrowserCallback
import org.koitharu.kotatsu.browser.BrowserClient
import org.koitharu.kotatsu.core.network.webview.adblock.AdBlock
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebViewClient that intercepts and captures HTTP requests for VRF token extraction
 * and other dynamic data extraction from AJAX requests.
 */
class RequestInterceptorWebViewClient(
    callback: BrowserCallback,
    adBlock: AdBlock?,
    private val config: InterceptionConfig,
    private val interceptor: WebViewRequestInterceptor,
) : BrowserClient(callback, adBlock) {

    private val capturedRequests = Collections.synchronizedList(mutableListOf<InterceptedRequest>())
    private val mutex = Mutex()
    private val isCapturing = AtomicBoolean(true)
    private val startTime = System.currentTimeMillis()

    @WorkerThread
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        // Always call parent first for ad blocking and other functionality
        val parentResponse = super.shouldInterceptRequest(view, request)

        // Capture request if still within timeout and capturing
        if (isCapturing.get() && request != null && !isTimeoutReached()) {
            captureRequestIfMatches(request)
        }

        return parentResponse
    }

    override fun onPageFinished(webView: WebView, url: String) {
        super.onPageFinished(webView, url)

        // Give additional time for AJAX requests after page load
        webView.postDelayed({
            if (isCapturing.compareAndSet(true, false)) {
                completeInterception()
            }
        }, 2000) // Wait 2 seconds after page load for AJAX requests
    }

    private fun captureRequestIfMatches(request: WebResourceRequest) {
        try {
            val interceptedRequest = InterceptedRequest(
                url = request.url.toString(),
                method = request.method,
                headers = request.requestHeaders,
                timestamp = System.currentTimeMillis()
            )

            // Check if request matches filtering criteria
            val shouldCapture = when {
                capturedRequests.size >= config.maxRequests -> false
                config.urlPattern != null && !interceptedRequest.urlMatches(config.urlPattern) -> false
                else -> interceptor.shouldCaptureRequest(interceptedRequest)
            }

            if (shouldCapture) {
                synchronized(capturedRequests) {
                    capturedRequests.add(interceptedRequest)
                }
            }

        } catch (e: Exception) {
            // Don't let interception errors break the WebView
            interceptor.onInterceptionError(e)
        }
    }

    private fun isTimeoutReached(): Boolean {
        return System.currentTimeMillis() - startTime > config.timeoutMs
    }

    private fun completeInterception() {
        try {
            val finalRequests = synchronized(capturedRequests) {
                capturedRequests.toList()
            }
            interceptor.onInterceptionComplete(finalRequests)
        } catch (e: Exception) {
            interceptor.onInterceptionError(e)
        }
    }

    /**
     * Manually stop capturing requests before timeout
     */
    fun stopCapturing() {
        if (isCapturing.compareAndSet(true, false)) {
            completeInterception()
        }
    }

    /**
     * Get currently captured requests (thread-safe)
     */
    fun getCapturedRequests(): List<InterceptedRequest> {
        return synchronized(capturedRequests) {
            capturedRequests.toList()
        }
    }
}
