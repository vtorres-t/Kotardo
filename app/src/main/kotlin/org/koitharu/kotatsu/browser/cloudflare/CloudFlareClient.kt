package org.koitharu.kotatsu.browser.cloudflare

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import org.koitharu.kotatsu.browser.BrowserClient
import org.koitharu.kotatsu.core.network.cookies.MutableCookieJar
import org.koitharu.kotatsu.core.network.webview.adblock.AdBlock
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val LOOP_COUNTER = 3

class CloudFlareClient(
	private val cookieJar: MutableCookieJar,
	private val callback: CloudFlareCallback,
	adBlock: AdBlock,
	private val targetUrl: String,
) : BrowserClient(callback, adBlock) {

	// Headers we want to keep
	private val allowedHeaders = setOf(
		"upgrade-insecure-requests",
		"user-agent",
		"accept",
		"sec-fetch-dest",
		"sec-fetch-site",
		"accept-language",
		"sec-fetch-mode",
		"cookie",
		"referer",
		"origin"
	)

	private val oldClearance = getClearance()
	private var counter = 0

	override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
		super.onPageStarted(view, url, favicon)
		checkClearance()
	}

	override fun onPageCommitVisible(view: WebView, url: String) {
		super.onPageCommitVisible(view, url)
		callback.onPageLoaded()
	}

	override fun onPageFinished(webView: WebView, url: String) {
		super.onPageFinished(webView, url)
		callback.onPageLoaded()
	}

	override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
		if (request == null) return null

		try {
			// Skip POST requests
			if (request.method == "POST") {
				return super.shouldInterceptRequest(view, request)
			}

			val client = OkHttpClient.Builder()
				.connectTimeout(15, TimeUnit.SECONDS)
				.readTimeout(15, TimeUnit.SECONDS)
				.build()

			val requestBuilder = Request.Builder()
				.url(request.url.toString())
				.method(request.method, null)

			// Filter headers using allowlist
			val filteredHeaders = mutableMapOf<String, String>()
			for ((key, value) in request.requestHeaders) {
				val lowerKey = key.lowercase(Locale.ROOT)
				if (allowedHeaders.contains(lowerKey)) {
					filteredHeaders[key] = value
				}
			}

			// Add standard headers that WebView doesn't expose but normally sends
			if (!filteredHeaders.containsKey("accept-language")) {
				filteredHeaders["accept-language"] = "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7"
			}
			if (!filteredHeaders.containsKey("sec-fetch-dest")) {
				filteredHeaders["sec-fetch-dest"] = "document"
			}
			if (!filteredHeaders.containsKey("sec-fetch-user")) {
				filteredHeaders["sec-fetch-user"] = "?1"
			}
			if (!filteredHeaders.containsKey("sec-fetch-mode")) {
				filteredHeaders["sec-fetch-mode"] = "navigate"
			}
			if (!filteredHeaders.containsKey("sec-fetch-site")) {
				filteredHeaders["sec-fetch-site"] = "none"
			}

			// Add filtered headers to request
			filteredHeaders.forEach { (key, value) ->
				requestBuilder.addHeader(key, value)
			}

			val response = client.newCall(requestBuilder.build()).execute()

			val contentType = response.header("Content-Type", "text/html")
			val mimeType = contentType?.split(";")?.get(0)?.trim() ?: "text/html"
			val charset = contentType?.substringAfter("charset=", "UTF-8")?.trim() ?: "UTF-8"

			return WebResourceResponse(mimeType, charset, response.body?.byteStream()).apply {
				val headers = mutableMapOf<String, String>()
				response.headers.forEach { headers[it.first] = it.second }
				setResponseHeaders(headers)
			}
		} catch (e: Exception) {
			return null
		}
	}

	fun reset() {
		counter = 0
	}

	private fun checkClearance() {
		val clearance = getClearance()
		if (clearance != null && clearance != oldClearance) {
			callback.onCheckPassed()
		} else {
			counter++
			if (counter >= LOOP_COUNTER) {
				reset()
				callback.onLoopDetected()
			}
		}
	}

	private fun getClearance() = CloudFlareHelper.getClearanceCookie(cookieJar, targetUrl)
}
