package com.ushareit.ui.browser

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.nextai.sharenow.R
import com.ushareit.core.analytics.FirebaseAnalyticsManager
import com.ushareit.core.downloader.DetectedVideoItem
import com.ushareit.core.downloader.DownloadEngine
import com.ushareit.core.downloader.DownloadStatus
import com.ushareit.core.downloader.MediaStoreExporter
import com.ushareit.core.downloader.MonetizedDomainRegistry
import com.ushareit.core.downloader.SnifferEngine
import com.ushareit.core.downloader.TabManager
import com.ushareit.core.downloader.VideoInfo
import com.ushareit.core.downloader.VideoQuality
import com.ushareit.core.p2p.P2PTransferManager
import com.ushareit.ui.transfer.ReceivedFilesActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import java.io.File
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class BrowserTabController(
    private val activity: AppCompatActivity,
    private val rootView: View
) {
    private val snifferEngine = SnifferEngine()
    private val tabManager = TabManager()
    val downloadEngine = DownloadEngine(
        downloadDir = File(activity.applicationContext.getExternalFilesDir(null) ?: activity.applicationContext.filesDir, "Videos").apply {
            if (!exists()) mkdirs()
        },
        context = activity.applicationContext
    )

    // UI Elements from layout_tab_browser.xml
    private val btnBrowserBack: View = rootView.findViewById(R.id.btn_browser_back)
    private val btnBrowserHome: View = rootView.findViewById(R.id.btn_browser_home)
    private val searchBarContainer: View = rootView.findViewById(R.id.search_bar_container)
    private val tabCounterBox: TextView = rootView.findViewById(R.id.btn_tab_counter)
    private val btnBrowserDownloadsBox: View = rootView.findViewById(R.id.btn_browser_downloads_box)
    private val ivBrowserDownloadsIcon: ImageView = rootView.findViewById(R.id.iv_browser_downloads_icon)
    private val btnBrowserDownloads: TextView = rootView.findViewById(R.id.btn_browser_downloads)

    private var downloadIconAnimator: android.animation.ObjectAnimator? = null

    private val etUrlInput: EditText = rootView.findViewById(R.id.et_url_input)
    private val btnClearUrl: TextView = rootView.findViewById(R.id.btn_clear_url)
    private val btnGoUrl: TextView = rootView.findViewById(R.id.btn_go_url)
    private val webProgressBar: ProgressBar = rootView.findViewById(R.id.web_progress_bar)

    private val webView: WebView = rootView.findViewById(R.id.web_view)
    private val dashboardScroll: View = rootView.findViewById(R.id.scroll_browser_dashboard)
    private val fabSniffer: ImageView = rootView.findViewById(R.id.fab_sniffer)

    private val btnPasteDetect: TextView = rootView.findViewById(R.id.btn_paste_detect)
    private val cardOpenDownloads: View = rootView.findViewById(R.id.card_open_downloads_dashboard)
    private val tvBrowserDownloadsSubtitle: TextView = rootView.findViewById(R.id.tv_browser_downloads_summary)

    // Sniffer state matching AI-downloader MainActivity + HomeFragment + Multi-Video List
    @Volatile
    private var detectedVideoUrl: String? = null
    @Volatile
    private var detectedVideoTitle: String? = null
    @Volatile
    private var detectedVideoInfo: VideoInfo? = null
    @Volatile
    private var isMasterStreamLocked: Boolean = false
    @Volatile
    private var currentWebTitle: String? = null
    @Volatile
    private var lastVisitedSpaUrl: String? = null
    @Volatile
    private var lastValidHttpUrl: String? = null
    @Volatile
    private var lastExtractedCanonicalUrl: String? = null
    @Volatile
    private var pendingAutoOpenModal: Boolean = false
    @Volatile
    private var pendingAutoStartDownload: Boolean = false
    @Volatile
    private var isPageReelsBatchMode: Boolean = false
    private var urlExtractJob: Job? = null
    @Volatile
    private var detectedVideoSequenceCounter: Int = 0
    @Volatile
    private var lastActiveMetaVideoId: String = ""

    private fun canonicalPageKey(rawUrl: String): String {
        val u = rawUrl.trim()
        if (u.contains("youtube.com", true) || u.contains("youtu.be", true)) {
            val vid = when {
                u.contains("v=") -> u.substringAfter("v=").substringBefore('&').substringBefore('#')
                u.contains("youtu.be/") -> u.substringAfter("youtu.be/").substringBefore('?').substringBefore('/')
                u.contains("/shorts/") -> u.substringAfter("/shorts/").substringBefore('?').substringBefore('/')
                else -> ""
            }
            if (vid.isNotEmpty()) return "yt:$vid"
        }
        if (u.contains("facebook.com", true) || u.contains("fb.watch", true)) {
            val fbId = SnifferEngine.extractFacebookVideoId(u)
            if (fbId.isNotEmpty()) return "fb:$fbId"
        }
        return u.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removePrefix("m.")
            .trimEnd('/')
    }

    private fun isFeedPlatformHost(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.contains("facebook.com") || lower.contains("fb.watch") ||
            lower.contains("tiktok.com") || lower.contains("instagram.com") ||
            lower.contains("youtube.com") || lower.contains("youtu.be") ||
            lower.contains("x.com") || lower.contains("twitter.com")
    }

    private val detectedVideosList = CopyOnWriteArrayList<DetectedVideoItem>()
    private val probingUrls = ConcurrentHashMap.newKeySet<String>()
    private var activeModalRefreshCallback: (() -> Unit)? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val modalRefreshRunnable = Runnable {
        activeModalRefreshCallback?.invoke()
    }

    private fun scheduleModalRefresh() {
        if (activeModalRefreshCallback == null) return
        mainHandler.removeCallbacks(modalRefreshRunnable)
        mainHandler.postDelayed(modalRefreshRunnable, 180L)
    }

    fun onResume() {
        downloadEngine.syncExistingDownloads()
    }

    init {
        MonetizedDomainRegistry.init(activity.applicationContext)
        setupWebView()
        setupTopBarAndControls()
        setupQuickSites()
        observeDownloads()
        updateTabBadge()
    }

    inner class AndroidSnifferBridge {
        @JavascriptInterface
        fun onVideoFound(streamUrl: String, title: String?) {
            activity.runOnUiThread {
                if (streamUrl.isNotEmpty() && streamUrl.startsWith("http")) {
                    val sanitized = SnifferEngine.stripByteRangeParams(streamUrl)
                    val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("http") } ?: getPageTitle()
                    notifyVideoDetected(sanitized, cleanTitle, isMaster = true, forceUpdate = true, promoteToTop = false)
                }
            }
        }

        @JavascriptInterface
        fun onVideoPlayed(streamUrl: String, title: String?, platformUrl: String?) {
            activity.runOnUiThread {
                val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("http") }
                if (!cleanTitle.isNullOrBlank()) {
                    detectedVideoTitle = cleanTitle
                }
                if (streamUrl.isNotEmpty() && streamUrl.startsWith("http")) {
                    val sanitized = SnifferEngine.stripByteRangeParams(streamUrl)
                    val efg = SnifferEngine.parseMetaEfgInfo(sanitized)
                    if (efg.videoId.isNotEmpty()) {
                        lastActiveMetaVideoId = efg.videoId
                    }
                    notifyVideoDetected(
                        url = sanitized,
                        title = cleanTitle ?: getPageTitle(),
                        isMaster = true,
                        forceUpdate = true,
                        promoteToTop = true
                    )
                }
                if (!platformUrl.isNullOrBlank() && platformUrl.startsWith("http")) {
                    val fbId = SnifferEngine.extractFacebookVideoId(platformUrl)
                    if (fbId.isNotEmpty()) {
                        lastActiveMetaVideoId = fbId
                    }
                    val cKey = canonicalPageKey(platformUrl)
                    if (cKey != lastExtractedCanonicalUrl) {
                        extractVideoDirectly(platformUrl, showToast = false)
                    }
                }
            }
        }

        @JavascriptInterface
        fun onTikTokVideoFound(tiktokVideoUrl: String, title: String?) {
            onPlatformVideoFound(tiktokVideoUrl, title)
        }

        @JavascriptInterface
        fun onPlatformVideoFound(platformVideoUrl: String, title: String?) {
            activity.runOnUiThread {
                if (platformVideoUrl.startsWith("http")) {
                    if (!title.isNullOrBlank() && !title.startsWith("http")) {
                        detectedVideoTitle = title.trim()
                    }
                    val fbId = SnifferEngine.extractFacebookVideoId(platformVideoUrl)
                    if (fbId.isNotEmpty()) {
                        lastActiveMetaVideoId = fbId
                    }
                    val cKey = canonicalPageKey(platformVideoUrl)
                    if (cKey == lastExtractedCanonicalUrl && (urlExtractJob?.isActive == true || detectedVideosList.isNotEmpty())) {
                        return@runOnUiThread
                    }
                    extractVideoDirectly(platformVideoUrl, showToast = false)
                }
            }
        }

        @JavascriptInterface
        fun onVideoStreamDetected(streamUrl: String, qualityHint: String, pageTitle: String) {
            onVideoFound(streamUrl, pageTitle)
        }

        @JavascriptInterface
        fun onAudioStreamHarvested(audioUrl: String) {
            if (audioUrl.isNotEmpty() && audioUrl.startsWith("http")) {
                SnifferEngine.registerMetaStream(audioUrl)
            }
        }

        @JavascriptInterface
        fun onBatchReelDiscovered(reelId: String, streamUrl: String, title: String?) {
            activity.runOnUiThread {
                if (streamUrl.startsWith("http")) {
                    val sanitized = SnifferEngine.stripByteRangeParams(streamUrl)
                    val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: "Reel_$reelId"
                    addOrUpdateDetectedVideoItem(
                        url = sanitized,
                        title = cleanTitle,
                        qualityLabel = "720p / 1080p HD MP4",
                        format = "mp4",
                        referer = "https://www.facebook.com/reel/$reelId/",
                        userAgent = SnifferEngine.DESKTOP_USER_AGENT,
                        promoteToTop = false
                    )
                }
            }
        }

        @JavascriptInterface
        fun onPageReelsHarvestComplete(totalCount: Int) {
            activity.runOnUiThread {
                val nonAudioCount = detectedVideosList.count { !it.isAudioOnly && !it.isLikelyAd }
                if (pendingAutoStartDownload && nonAudioCount > 0) {
                    pendingAutoStartDownload = false
                    pendingAutoOpenModal = false
                    enqueueAllDetectedVideos()
                } else if (pendingAutoOpenModal && nonAudioCount > 0) {
                    pendingAutoOpenModal = false
                    showDownloadModal()
                } else if (nonAudioCount > 1) {
                    Toast.makeText(
                        activity,
                        "Đã thu thập $nonAudioCount Reels của Page — bấm nút Tải để tải toàn bộ!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.setDownloadListener { url, _, _, mimetype, _ ->
            if (!url.isNullOrBlank() && url.startsWith("http")) {
                val lower = url.lowercase()
                val isMedia = lower.contains(".mp4") || lower.contains(".m3u8") ||
                    lower.contains(".webm") || lower.contains(".mp3") ||
                    (mimetype != null && (mimetype.startsWith("video/") || mimetype.startsWith("audio/")))
                if (isMedia) {
                    val sanitized = SnifferEngine.sanitizeMediaUrl(url)
                    val fileName = sanitized.substringBefore('?').substringAfterLast('/').ifBlank { "Video_Download.mp4" }
                    activity.runOnUiThread {
                        notifyVideoDetected(sanitized, fileName, isMaster = true)
                        showDownloadModal()
                    }
                }
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            defaultTextEncodingName = "UTF-8"
            userAgentString = SnifferEngine.MOBILE_USER_AGENT
        }

        webView.addJavascriptInterface(AndroidSnifferBridge(), "AndroidSniffer")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                return false
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    webProgressBar.visibility = View.VISIBLE
                    webProgressBar.progress = newProgress
                } else {
                    webProgressBar.visibility = View.GONE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank() && !title.startsWith("http")) {
                    currentWebTitle = title
                    val currentUrl = view?.url ?: tabManager.activeTab?.currentUrl ?: "about:blank"
                    tabManager.updateCurrentTabUrl(currentUrl, title)
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: ""
                return handleUrlOverride(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handleUrlOverride(url ?: "")
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (!url.isNullOrBlank() && url != "about:blank") {
                    if (isAppDeepLinkOrRedirectUrl(url) || isBlockedAdUrl(url)) {
                        Log.d("BrowserWebView", "Intercepted deep-link/ad in onPageStarted: $url")
                        view?.stopLoading()
                        handleDeepLinkVideoExtraction(url)
                        val fallback = lastValidHttpUrl
                        if (view?.canGoBack() == true) {
                            view.goBack()
                        } else if (!fallback.isNullOrBlank()) {
                            view?.loadUrl(fallback)
                        }
                        return
                    }
                    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                        lastValidHttpUrl = url
                    }
                }
                super.onPageStarted(view, url, favicon)
                if (!url.isNullOrBlank() && url != "about:blank") {
                    val prevUrl = lastVisitedSpaUrl
                    val prevKey = prevUrl?.let { canonicalPageKey(it) }
                    val newKey = canonicalPageKey(url)
                    val prevHost = MonetizedDomainRegistry.extractHost(prevUrl ?: "")
                    val newHost = MonetizedDomainRegistry.extractHost(url)
                    lastVisitedSpaUrl = url
                    etUrlInput.setText(url)
                    btnClearUrl.visibility = View.VISIBLE
                    tabManager.updateCurrentTabUrl(url, view?.title ?: url)
                    if (prevKey != newKey) {
                        if (prevHost.isNotEmpty() && prevHost == newHost && isFeedPlatformHost(url)) {
                            // Giữ danh sách chờ tải khi lướt tiếp các video cùng nền tảng (FB Reels/Watch, TikTok, Shorts...)
                            lastExtractedCanonicalUrl = null
                        } else {
                            resetDetectionState()
                        }
                    }
                    view?.evaluateJavascript(ANTI_POPUP_JS, null)
                    view?.evaluateJavascript(MOBILE_RESPONSIVE_JS, null)
                    view?.evaluateJavascript(MULTI_LAYER_SNIFFER_JS, null)
                    if (shouldDirectExtract(url)) {
                        extractVideoDirectly(url, showToast = false)
                    }
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (!url.isNullOrBlank() && url != "about:blank" && url != lastVisitedSpaUrl) {
                    if (isAppDeepLinkOrRedirectUrl(url) || isBlockedAdUrl(url)) {
                        handleDeepLinkVideoExtraction(url)
                        return
                    }
                    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                        lastValidHttpUrl = url
                    }
                    val prevUrl = lastVisitedSpaUrl
                    val prevKey = prevUrl?.let { canonicalPageKey(it) }
                    val newKey = canonicalPageKey(url)
                    val prevHost = MonetizedDomainRegistry.extractHost(prevUrl ?: "")
                    val newHost = MonetizedDomainRegistry.extractHost(url)
                    lastVisitedSpaUrl = url
                    etUrlInput.setText(url)
                    btnClearUrl.visibility = View.VISIBLE
                    tabManager.updateCurrentTabUrl(url, view?.title ?: url)
                    if (prevKey != newKey) {
                        if (prevHost.isNotEmpty() && prevHost == newHost) {
                            // Điều hướng SPA khi cuộn list video (FB Reels, Watch, TikTok, Shorts):
                            // KHÔNG xóa detectedVideosList để cộng dồn các video đã play vào danh sách chờ tải
                            lastExtractedCanonicalUrl = null
                        } else {
                            resetDetectionState()
                        }
                    }
                    view?.evaluateJavascript(MULTI_LAYER_SNIFFER_JS, null)
                    if (shouldDirectExtract(url)) {
                        extractVideoDirectly(url, showToast = false)
                    }
                }
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)
                if (url != null && !isBlockedAdUrl(url) && !isAppDeepLinkOrRedirectUrl(url)) {
                    inspectUrlForVideo(url, currentWebTitle)
                }
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: ""
                if (reqUrl.isNotEmpty()) {
                    if (isAppDeepLinkOrRedirectUrl(reqUrl)) {
                        activity.runOnUiThread {
                            handleDeepLinkVideoExtraction(reqUrl)
                        }
                        return createEmptyAdResponse()
                    }
                    if (isBlockedAdUrl(reqUrl)) {
                        return createEmptyAdResponse()
                    }
                    inspectUrlForVideo(reqUrl, currentWebTitle)
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                val isMainFrame = request?.isForMainFrame == true
                val failingUrl = request?.url?.toString() ?: ""
                val errCode = error?.errorCode ?: 0
                val errDesc = error?.description?.toString() ?: ""
                if (isMainFrame) {
                    Log.w("BrowserWebView", "Main-frame error code=$errCode desc=$errDesc url=$failingUrl")
                    if (isAppDeepLinkOrRedirectUrl(failingUrl) ||
                        isBlockedAdUrl(failingUrl) ||
                        errCode == ERROR_UNSUPPORTED_SCHEME ||
                        errDesc.contains("ERR_UNKNOWN_URL_SCHEME", ignoreCase = true) ||
                        errDesc.contains("ERR_BLOCKED_BY_RESPONSE", ignoreCase = true)
                    ) {
                        handleDeepLinkVideoExtraction(failingUrl)
                        view?.stopLoading()
                        val fallback = lastValidHttpUrl
                        if (view?.canGoBack() == true) {
                            view.goBack()
                        } else if (!fallback.isNullOrBlank()) {
                            view?.loadUrl(fallback)
                        }
                        return
                    }
                }
                super.onReceivedError(view, request, error)
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                val url = failingUrl ?: ""
                val desc = description ?: ""
                if (isAppDeepLinkOrRedirectUrl(url) ||
                    isBlockedAdUrl(url) ||
                    errorCode == ERROR_UNSUPPORTED_SCHEME ||
                    desc.contains("ERR_UNKNOWN_URL_SCHEME", ignoreCase = true)
                ) {
                    handleDeepLinkVideoExtraction(url)
                    view?.stopLoading()
                    val fallback = lastValidHttpUrl
                    if (view?.canGoBack() == true) {
                        view.goBack()
                    } else if (!fallback.isNullOrBlank()) {
                        view?.loadUrl(fallback)
                    }
                    return
                }
                super.onReceivedError(view, errorCode, description, failingUrl)
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: android.webkit.RenderProcessGoneDetail?
            ): Boolean {
                Log.w("BrowserWebView", "WebView renderer process gone (didCrash=${detail?.didCrash()}); recovering without killing host app")
                activity.runOnUiThread {
                    try {
                        webProgressBar.visibility = View.GONE
                        view?.stopLoading()
                    } catch (_: Exception) {}
                }
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url.isNullOrBlank() || url == "about:blank") return
                val pageTitle = view?.title ?: ""
                if (pageTitle.isNotEmpty() && !pageTitle.startsWith("http")) {
                    currentWebTitle = pageTitle
                }
                tabManager.updateCurrentTabUrl(
                    url,
                    if (pageTitle.isNotEmpty() && !pageTitle.startsWith("http")) pageTitle else (currentWebTitle ?: "Web Page")
                )

                val pageHost = MonetizedDomainRegistry.extractHost(url).ifEmpty { "unknown" }
                FirebaseAnalyticsManager.logBrowserPageLoaded(
                    url = url,
                    host = pageHost,
                    title = if (pageTitle.isNotEmpty() && !pageTitle.startsWith("http")) pageTitle else (currentWebTitle ?: "Web Page"),
                    isMonetized = MonetizedDomainRegistry.isMonetizedDomain(url)
                )

                view?.evaluateJavascript(ANTI_POPUP_JS, null)
                view?.evaluateJavascript(MOBILE_RESPONSIVE_JS, null)

                // 1. Tiêm Javascript quét tìm video streams đa tầng (HTML5 player, script blocks, media tags, prototypes)
                view?.evaluateJavascript(MULTI_LAYER_SNIFFER_JS, null)
                if (isPageReelsBatchMode && (
                        url.contains("/reels", ignoreCase = true) ||
                            url.contains("reels_tab", ignoreCase = true) ||
                            url.contains("/shorts", ignoreCase = true) ||
                            url.contains("/videos", ignoreCase = true)
                        )
                ) {
                    view?.evaluateJavascript(FACEBOOK_PAGE_REELS_AUTOSCROLL_JS, null)
                }

                // 2. Chỉ phân tích HTML khi chưa bắt được luồng trực tiếp trên các trang web thường (bỏ qua feed SPA lớn để tránh khựng UI khi chuyển tab)
                if (!url.contains("google.com/", ignoreCase = true) &&
                    !isFeedPlatformHost(url) &&
                    detectedVideosList.isEmpty()
                ) {
                    view?.evaluateJavascript(LIGHTWEIGHT_DOM_EXTRACT_JS) { rawHtmlJson ->
                        if (!rawHtmlJson.isNullOrBlank() && rawHtmlJson != "null" && detectedVideosList.isEmpty()) {
                            activity.lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val cleanHtml = JSONTokener(rawHtmlJson).nextValue().toString()
                                    val videoInfo = snifferEngine.extractVideoFromUrl(url, cleanHtml)
                                    if (videoInfo != null && videoInfo.qualities.isNotEmpty()) {
                                        withContext(Dispatchers.Main) {
                                            notifyVideoInfoDetected(videoInfo)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w("BrowserSniffer", "Lỗi phân tích DOM HTML", e)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isBlockedAdUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (AD_BLOCK_DOMAINS_AND_PATTERNS.any { lower.contains(it) }) {
            return true
        }
        return false
    }

    private fun createEmptyAdResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            204,
            "No Content",
            mapOf("Access-Control-Allow-Origin" to "*"),
            java.io.ByteArrayInputStream(ByteArray(0))
        )
    }

    private fun isAppDeepLinkOrRedirectUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase()
        if (lower == "about:blank" || lower.startsWith("javascript:") ||
            lower.startsWith("data:") || lower.startsWith("blob:")
        ) {
            return false
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return true
        }
        return lower.contains(".onelink.me") ||
            lower.contains("//onelink.me") ||
            lower.contains(".app.link") ||
            lower.contains("//bnc.lt") ||
            lower.contains(".page.link") ||
            lower.contains("play.google.com/store") ||
            lower.contains("market.android.com") ||
            lower.contains("apps.apple.com") ||
            lower.contains("itunes.apple.com") ||
            lower.contains("af_dp=snssdk") ||
            lower.contains("snssdk1180") ||
            lower.contains("snssdk1233") ||
            lower.contains("l.facebook.com/l.php?u=fb%3a") ||
            lower.contains("l.facebook.com/l.php?u=intent%3a")
    }

    private fun handleDeepLinkVideoExtraction(url: String) {
        if (url.isBlank()) return
        try {
            val decoded = try {
                java.net.URLDecoder.decode(url, "UTF-8")
            } catch (_: Exception) {
                url
            }
            if (decoded.contains("fullscreen_video/")) {
                val videoId = decoded.substringAfter("fullscreen_video/").substringBefore('?').substringBefore('&').substringBefore('/')
                if (videoId.isNotBlank()) {
                    extractVideoDirectly("https://www.facebook.com/reel/$videoId", showToast = false)
                    return
                }
            }
            val fbId = SnifferEngine.extractFacebookVideoId(decoded)
            if (fbId.isNotEmpty()) {
                extractVideoDirectly("https://www.facebook.com/reel/$fbId", showToast = false)
                return
            }
            val ttMatch = Regex("""(?:aweme/detail/|/video/|/v/)(\d{15,22})""").find(decoded)
            if (ttMatch != null) {
                val ttId = ttMatch.groupValues[1]
                extractVideoDirectly("https://www.tiktok.com/@tiktok/video/$ttId", showToast = false)
                return
            }
        } catch (_: Exception) {}
    }

    private fun extractWebFallbackFromDeepLink(url: String): String? {
        if (url.isBlank()) return null
        try {
            if (url.contains("S.browser_fallback_url=", ignoreCase = true)) {
                val rawFallback = url.substringAfter("S.browser_fallback_url=", "")
                    .substringAfter("s.browser_fallback_url=", "")
                    .substringBefore(';')
                    .substringBefore('#')
                if (rawFallback.isNotBlank()) {
                    val decoded = try { java.net.URLDecoder.decode(rawFallback, "UTF-8") } catch (_: Exception) { rawFallback }
                    if ((decoded.startsWith("http://", true) || decoded.startsWith("https://", true)) &&
                        !isAppDeepLinkOrRedirectUrl(decoded) && !isBlockedAdUrl(decoded)
                    ) {
                        return decoded
                    }
                }
            }
            if (url.startsWith("fb://faceweb/f?href=", ignoreCase = true)) {
                val rawHref = url.substringAfter("href=").substringBefore('&')
                val decoded = try { java.net.URLDecoder.decode(rawHref, "UTF-8") } catch (_: Exception) { rawHref }
                if (decoded.startsWith("http://", true) || decoded.startsWith("https://", true)) {
                    return decoded
                } else if (decoded.startsWith("/")) {
                    return "https://m.facebook.com$decoded"
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun handleUrlOverride(url: String): Boolean {
        if (url.isEmpty()) return false

        // 1. Chặn các scheme mở ứng dụng ngoài (fb://, intent://, snssdk1180://, market://) và deep-link redirector (onelink.me, app.link)
        if (isAppDeepLinkOrRedirectUrl(url)) {
            Log.w("BrowserWebView", "Blocked app deep-link/redirect: $url")
            handleDeepLinkVideoExtraction(url)
            val fallbackWebUrl = extractWebFallbackFromDeepLink(url)
            if (!fallbackWebUrl.isNullOrBlank()) {
                val current = webView.url ?: ""
                if (fallbackWebUrl.contains("/login", ignoreCase = true) ||
                    (canonicalPageKey(fallbackWebUrl) != canonicalPageKey(current))
                ) {
                    webView.loadUrl(fallbackWebUrl)
                }
            }
            return true
        }

        // 2. Chặn toàn bộ các domain quảng cáo popup/cờ bạc/cam site/redirect quảng cáo
        if (isBlockedAdUrl(url)) {
            Log.d("AntiPopup", "Blocked ad redirect/popup to: $url")
            FirebaseAnalyticsManager.logBrowserAdBlocked(
                adUrlOrHost = MonetizedDomainRegistry.extractHost(url).ifEmpty { url.take(60) },
                blockType = "redirect_popup"
            )
            return true
        }

        return false
    }

    private fun inspectUrlForVideo(url: String, pageTitle: String? = null) {
        val lower = url.lowercase()
        val isMetaVideo = (lower.contains("cdninstagram.com") || lower.contains(".fbcdn.net"))

        // Bỏ qua các phân đoạn chunk byte range, segment files, init files và static assets
        if (!isMetaVideo && (lower.contains("bytestart") || lower.contains("byteend"))) {
            return
        }
        if (lower.contains(".m4s") || lower.contains(".ts") || lower.contains("seg-") ||
            lower.contains("-seg") || lower.contains("/frag/") || lower.contains("init-") ||
            lower.contains("init.mp4") || lower.contains(".aac")
        ) {
            return
        }

        // Bỏ qua analytics, trackers, ads và thumbnail preview
        if (isBlockedAdUrl(lower) || SnifferEngine.isAdOrBannerVideoUrl(lower) ||
            lower.contains("google-analytics") || lower.contains("doubleclick") ||
            lower.contains("googlesyndication") || lower.contains("facebook.com/tr") ||
            lower.contains("telemetry") || lower.contains("beacon") ||
            lower.contains("modusygunaciro.com") || lower.contains("448x250") ||
            lower.contains("250x250") || lower.contains("160x120") || lower.contains("320x180") ||
            lower.contains("/preview") || lower.contains("preview.") || lower.contains("thumb-") ||
            lower.contains("flixcdn.com") || lower.contains("/thumbs/") ||
            lower.contains("thumb.") || lower.contains(".sprite.") ||
            lower.contains("/trailer") || lower.contains("_trailer")
        ) {
            return
        }

        // QUY TẮC CỐT LÕI: TUYỆT ĐỐI KHÔNG BẮT GÓI ĐIỀU KHIỂN SABR/UMP HOẶC CHUNK RANGE CỦA YOUTUBE
        if (lower.contains("googlevideo.com") && (lower.contains("alr=yes") || lower.contains("sabr=1") ||
                lower.contains("range=") || lower.contains("live=1") || lower.contains("ump=1"))
        ) {
            return
        }

        val isTikTokVideo = lower.contains("/video/tos/") ||
            lower.contains("mime_type=video_mp4") ||
            lower.contains("/aweme/v1/play") ||
            ((lower.contains("tiktokcdn") || lower.contains("tiktokv.com") || lower.contains("byteoversea") || lower.contains("ibytedtos")) &&
                (lower.contains(".mp4") || lower.contains("/video/") || lower.contains("video_mp4") || lower.contains("mime_type=video")))

        val isVideoUrl = (lower.contains(".m3u8") || lower.contains(".mpd") ||
            (lower.contains(".mp4") && !lower.contains(".m4s")) ||
            isTikTokVideo ||
            (isMetaVideo && (lower.contains(".mp4") || lower.contains("/v/") || lower.contains("/o1/v/") || lower.contains("oe="))) ||
            (lower.contains("video.twimg.com") && (lower.contains(".mp4") || lower.contains(".m3u8"))) ||
            (lower.contains("v.redd.it") && (lower.contains(".mp4") || lower.contains(".m3u8"))) ||
            (lower.contains("vimeocdn.com") && (lower.contains(".mp4") || lower.contains(".m3u8"))) ||
            (lower.contains("dmcdn.net") && (lower.contains(".mp4") || lower.contains(".m3u8"))) ||
            (lower.contains("xhcdn.com") && (lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains("/hls/"))) ||
            (lower.contains("xvideos-cdn.com") && (lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains("/hls/"))) ||
            (lower.contains("phncdn.com") && (lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains("/hls/")))) &&
            !lower.contains(".jpg") && !lower.contains(".png") && !lower.contains(".webp") &&
            !lower.contains(".js") && !lower.contains(".css") && !lower.contains(".svg") &&
            !lower.contains(".woff") && !lower.contains(".woff2") && !lower.contains(".ttf") &&
            !lower.contains(".gif")

        if (isVideoUrl) {
            val isActivePlaybackRange = isMetaVideo && lower.contains("bytestart=") && !lower.contains("bytestart=0&") && !lower.endsWith("bytestart=0")
            val sanitized = if (isMetaVideo) {
                SnifferEngine.stripByteRangeParams(url)
            } else {
                SnifferEngine.sanitizeMediaUrl(url)
            }
            val metaEfg = if (isMetaVideo) SnifferEngine.registerMetaStream(sanitized) else SnifferEngine.Companion.MetaEfgInfo()
            if (isMetaVideo && metaEfg.isAudioOnly) {
                // Đã đăng ký luồng âm thanh kèm theo (companion audio stream) theo videoId/xpvAssetId để tự động ghép tiếng (mux) khi tải video
                return
            }
            val cleanTitle = pageTitle?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("http") }
            val resolvedTitle = if (metaEfg.videoId.isNotEmpty()) {
                val base = cleanTitle ?: "Facebook Video"
                if (base.contains(metaEfg.videoId.takeLast(6))) base else "$base (#${metaEfg.videoId.takeLast(6)})"
            } else {
                cleanTitle
            }
            val shouldPromote = isTikTokVideo || isActivePlaybackRange ||
                (isMetaVideo && !metaEfg.isAudioOnly && (metaEfg.videoId.isEmpty() || metaEfg.videoId == lastActiveMetaVideoId || lastActiveMetaVideoId.isEmpty()))
            activity.runOnUiThread {
                notifyVideoDetected(
                    url = sanitized,
                    title = resolvedTitle,
                    forceUpdate = isTikTokVideo || isMetaVideo,
                    promoteToTop = shouldPromote,
                    qualityLabel = metaEfg.qualityHint,
                    isAudioOnly = false
                )
            }
        }
    }

    private fun updateBubbleState(isAvailable: Boolean) {
        fabSniffer.visibility = View.VISIBLE
        fabSniffer.clearAnimation()
        if (isAvailable) {
            fabSniffer.setBackgroundResource(R.drawable.bg_fab_bubble_active)
            fabSniffer.alpha = 1.0f
        } else {
            fabSniffer.setBackgroundResource(R.drawable.bg_fab_bubble_inactive)
            fabSniffer.alpha = 0.88f
        }
    }

    private fun resetDetectionState() {
        detectedVideoUrl = null
        detectedVideoTitle = null
        detectedVideoInfo = null
        isMasterStreamLocked = false
        detectedVideoSequenceCounter = 0
        lastActiveMetaVideoId = ""
        lastExtractedCanonicalUrl = null
        detectedVideosList.clear()
        probingUrls.clear()
        updateBubbleState(false)
    }

    private fun normalizeStreamKey(url: String): String {
        val clean = SnifferEngine.stripByteRangeParams(url).trim()
        val lower = clean.lowercase()
        if (lower.contains(".fbcdn.net") || lower.contains("cdninstagram.com") ||
            lower.contains("tiktokcdn") || lower.contains("tiktokv.com") ||
            lower.contains("byteoversea") || lower.contains("ibytedtos") ||
            lower.contains("video.twimg.com") || lower.contains("xhcdn.com") ||
            lower.contains("xvideos-cdn.com") || lower.contains("phncdn.com")
        ) {
            try {
                val u = Uri.parse(clean)
                val host = u.host?.lowercase() ?: ""
                val path = u.path ?: ""
                if (host.isNotEmpty() && path.isNotEmpty() && path != "/") {
                    return "$host$path"
                }
            } catch (_: Exception) {}
        }
        return clean
    }

    private fun inferQualityLabelFromUrl(url: String, defaultFormat: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("multi=") || lower.contains("_tpl_") -> "Master Adaptive HLS (.M3U8)"
            lower.contains("2160p") || lower.contains("4k") -> "2160p 4K ${defaultFormat.uppercase()}"
            lower.contains("1440p") || lower.contains("2k") -> "1440p 2K ${defaultFormat.uppercase()}"
            lower.contains("1080p") || lower.contains("_hd") || lower.contains("high") -> "1080p Full HD ${defaultFormat.uppercase()}"
            lower.contains("720p") -> "720p HD ${defaultFormat.uppercase()}"
            lower.contains("480p") -> "480p SD ${defaultFormat.uppercase()}"
            lower.contains("360p") || lower.contains("low") -> "360p Standard ${defaultFormat.uppercase()}"
            lower.contains("240p") -> "240p Low ${defaultFormat.uppercase()}"
            lower.contains("144p") -> "144p Data Saver ${defaultFormat.uppercase()}"
            defaultFormat.equals("m3u8", ignoreCase = true) -> "Adaptive HLS Stream (.M3U8)"
            defaultFormat.equals("mp3", ignoreCase = true) -> "Audio Only (.MP3)"
            else -> "HD Video (.MP4)"
        }
    }

    private fun sortDetectedVideos() {
        val sorted = detectedVideosList.sortedWith(
            compareBy<DetectedVideoItem> { it.isLikelyAd }
                .thenBy { it.isAudioOnly }
                .thenByDescending { it.detectedAt / 2500L }
                .thenByDescending { if (it.sizeBytes > 0L) it.sizeBytes else 15_000_000L }
                .thenByDescending { it.detectedAt }
        )
        detectedVideosList.clear()
        detectedVideosList.addAll(sorted)
    }

    private fun addOrUpdateDetectedVideoItem(
        url: String,
        title: String?,
        qualityLabel: String? = null,
        format: String? = null,
        sizeBytes: Long = 0L,
        durationSeconds: Int = 0,
        referer: String? = null,
        userAgent: String? = null,
        isAudioOnly: Boolean = false,
        isLikelyAd: Boolean = false,
        promoteToTop: Boolean = true
    ) {
        val cleanUrl = SnifferEngine.stripByteRangeParams(url).trim()
        if (!cleanUrl.startsWith("http")) return
        if (SnifferEngine.isAdOrBannerVideoUrl(cleanUrl)) return

        val lower = cleanUrl.lowercase()
        if (lower.contains("250x250") || lower.contains("160x120") ||
            lower.contains("448x250") || lower.contains("320x180") ||
            lower.contains("modusygunaciro") || lower.contains("preview") ||
            lower.contains("thumb") || lower.contains("flixcdn.com") || lower.contains(".sprite.")
        ) {
            return
        }

        val isMetaStream = lower.contains(".fbcdn.net") || lower.contains("cdninstagram.com")
        val metaEfg = if (isMetaStream) {
            SnifferEngine.registerMetaStream(cleanUrl)
        } else {
            SnifferEngine.Companion.MetaEfgInfo()
        }
        if (isMetaStream && metaEfg.isAudioOnly && !isAudioOnly) {
            return
        }
        val effectiveAudioOnly = isAudioOnly || metaEfg.isAudioOnly

        val resolvedFormat = format ?: when {
            effectiveAudioOnly || lower.contains(".mp3") || lower.contains(".m4a") -> "mp3"
            lower.contains(".m3u8") -> "m3u8"
            else -> "mp4"
        }
        val resolvedQuality = qualityLabel?.takeIf { it.isNotBlank() }
            ?: metaEfg.qualityHint
            ?: inferQualityLabelFromUrl(cleanUrl, resolvedFormat)
        val rawTitle = title?.trim()?.takeIf {
            it.isNotEmpty() && !it.startsWith("http") && !it.equals("file.mp4", ignoreCase = true)
        } ?: detectedVideoTitle?.takeIf { it.isNotBlank() }
            ?: getPageTitle()?.takeIf { it.isNotBlank() && !it.startsWith("http") }
            ?: "Video"

        val key = normalizeStreamKey(cleanUrl)
        val existingIdx = detectedVideosList.indexOfFirst {
            (normalizeStreamKey(it.url) == key && it.isAudioOnly == effectiveAudioOnly) ||
                (!effectiveAudioOnly && !it.isAudioOnly && metaEfg.videoId.isNotEmpty() && SnifferEngine.parseMetaEfgInfo(it.url).videoId == metaEfg.videoId)
        }
        val alreadyProbed = sizeBytes > 0L
        val now = System.currentTimeMillis()

        val item = if (existingIdx >= 0) {
            val old = detectedVideosList[existingIdx]
            val oldEfg = if (isMetaStream) SnifferEngine.parseMetaEfgInfo(old.url) else SnifferEngine.Companion.MetaEfgInfo()
            val shouldKeepOldStream = isMetaStream &&
                oldEfg.videoId.isNotEmpty() &&
                oldEfg.videoId == metaEfg.videoId &&
                normalizeStreamKey(old.url) != key &&
                (oldEfg.qualityRank > metaEfg.qualityRank || (oldEfg.qualityRank == metaEfg.qualityRank && oldEfg.bitrate > metaEfg.bitrate))
            val chosenUrl = if (shouldKeepOldStream) old.url else cleanUrl
            val chosenQuality = if (shouldKeepOldStream) {
                old.qualityLabel
            } else if (qualityLabel != null || metaEfg.qualityHint != null) {
                resolvedQuality
            } else {
                old.qualityLabel
            }
            val shouldUpdateTimestamp = promoteToTop && (now - old.detectedAt > 1800L)
            old.copy(
                title = if ((old.title.startsWith("Video_") || old.title.equals("Facebook", true)) && !rawTitle.startsWith("Video_")) rawTitle else old.title,
                url = chosenUrl,
                qualityLabel = chosenQuality,
                sizeBytes = if (!shouldKeepOldStream && sizeBytes > 0L) sizeBytes else old.sizeBytes,
                durationSeconds = if (durationSeconds > 0) durationSeconds else if (metaEfg.durationSec > 0) metaEfg.durationSec else old.durationSeconds,
                referer = referer ?: old.referer,
                userAgent = userAgent ?: old.userAgent,
                isSizeProbed = if (shouldKeepOldStream) old.isSizeProbed else (old.isSizeProbed || alreadyProbed),
                detectedAt = if (shouldUpdateTimestamp) now else old.detectedAt
            )
        } else {
            val seqNum = if (!effectiveAudioOnly) {
                detectedVideoSequenceCounter += 1
                detectedVideoSequenceCounter
            } else {
                detectedVideoSequenceCounter.coerceAtLeast(1)
            }
            val numberedTitle = if (rawTitle.startsWith("Video #")) {
                rawTitle
            } else if (metaEfg.videoId.isNotEmpty() && !rawTitle.contains(metaEfg.videoId.takeLast(6))) {
                "Video #$seqNum • $rawTitle (#${metaEfg.videoId.takeLast(6)})"
            } else {
                "Video #$seqNum • $rawTitle"
            }
            DetectedVideoItem(
                id = UUID.randomUUID().toString(),
                title = numberedTitle,
                url = cleanUrl,
                qualityLabel = resolvedQuality,
                format = resolvedFormat,
                sizeBytes = sizeBytes,
                durationSeconds = if (durationSeconds > 0) durationSeconds else metaEfg.durationSec,
                referer = referer ?: getCurrentUrl(),
                userAgent = userAgent,
                isAudioOnly = effectiveAudioOnly,
                isLikelyAd = isLikelyAd,
                isSizeProbed = alreadyProbed && !resolvedFormat.equals("m3u8", ignoreCase = true),
                detectedAt = now
            )
        }

        if (existingIdx >= 0) {
            detectedVideosList[existingIdx] = item
        } else {
            val maxItemsCap = if (isPageReelsBatchMode) 500 else 35
            while (detectedVideosList.size >= maxItemsCap) {
                detectedVideosList.removeAt(detectedVideosList.lastIndex)
            }
            detectedVideosList.add(item)
            val pageRef = item.referer ?: getCurrentUrl() ?: cleanUrl
            val detectedHost = MonetizedDomainRegistry.extractHost(pageRef).ifEmpty { "direct" }
            FirebaseAnalyticsManager.logBrowserVideoDetected(
                host = detectedHost,
                format = resolvedFormat,
                qualityLabel = resolvedQuality,
                detectedCount = detectedVideosList.size,
                isMonetized = MonetizedDomainRegistry.isMonetizedDomain(pageRef)
            )
        }

        sortDetectedVideos()
        updateBubbleState(detectedVideosList.isNotEmpty())
        scheduleModalRefresh()

        if (probingUrls.size > 80) {
            probingUrls.clear()
        }
        val probeKey = normalizeStreamKey(item.url)
        val targetItemId = item.id
        if (!item.isSizeProbed && probingUrls.add(probeKey)) {
            val probeUrl = item.url
            val probeReferer = item.referer ?: getCurrentUrl()
            activity.lifecycleScope.launch(Dispatchers.IO) {
                val probed = snifferEngine.probeStreamInfo(probeUrl, probeReferer, item.userAgent)
                withContext(Dispatchers.Main) {
                    val idx = detectedVideosList.indexOfFirst {
                        it.id == targetItemId ||
                            (normalizeStreamKey(it.url) == probeKey && it.isAudioOnly == effectiveAudioOnly) ||
                            (!effectiveAudioOnly && !it.isAudioOnly && metaEfg.videoId.isNotEmpty() && SnifferEngine.parseMetaEfgInfo(it.url).videoId == metaEfg.videoId)
                    }
                    if (idx >= 0) {
                        val current = detectedVideosList[idx]
                        val finalSize = if (probed.sizeBytes > 0L) probed.sizeBytes else current.sizeBytes
                        val finalDur = if (probed.durationSeconds > 0) probed.durationSeconds else current.durationSeconds
                        val finalAd = current.isLikelyAd || probed.isLikelyAd || (finalSize in 1L..1_800_000L && !current.isAudioOnly && detectedVideosList.size > 1)
                        detectedVideosList[idx] = current.copy(
                            sizeBytes = finalSize,
                            durationSeconds = finalDur,
                            isLikelyAd = finalAd,
                            isSizeProbed = true
                        )
                    }
                    if (probed.hlsVariants.isNotEmpty()) {
                        for (variant in probed.hlsVariants) {
                            if (detectedVideosList.size >= 35) break
                            val vKey = normalizeStreamKey(variant.downloadUrl)
                            if (detectedVideosList.none { normalizeStreamKey(it.url) == vKey && it.isAudioOnly == variant.isAudioOnly }) {
                                detectedVideosList.add(
                                    DetectedVideoItem(
                                        id = UUID.randomUUID().toString(),
                                        title = item.title,
                                        url = variant.downloadUrl,
                                        qualityLabel = variant.label,
                                        format = variant.format,
                                        sizeBytes = variant.approximateSize,
                                        durationSeconds = probed.durationSeconds,
                                        referer = probeReferer,
                                        userAgent = item.userAgent,
                                        isAudioOnly = variant.isAudioOnly,
                                        isLikelyAd = false,
                                        isSizeProbed = true,
                                        detectedAt = item.detectedAt
                                    )
                                )
                            }
                        }
                    }
                    sortDetectedVideos()
                    scheduleModalRefresh()
                }
            }
        }
    }

    fun notifyVideoDetected(
        url: String,
        title: String? = null,
        isMaster: Boolean = false,
        forceUpdate: Boolean = false,
        promoteToTop: Boolean = true,
        qualityLabel: String? = null,
        isAudioOnly: Boolean = false
    ) {
        if (SnifferEngine.isAdOrBannerVideoUrl(url)) {
            return
        }
        val lower = url.lowercase()
        if (lower.contains("250x250") || lower.contains("160x120") ||
            lower.contains("448x250") || lower.contains("modusygunaciro") ||
            lower.contains("preview") || lower.contains("thumb")
        ) {
            return
        }

        if (!isAudioOnly) {
            detectedVideoUrl = url
        }
        if (!title.isNullOrEmpty() && !title.startsWith("http")) {
            detectedVideoTitle = title
        }
        addOrUpdateDetectedVideoItem(
            url = url,
            title = title ?: detectedVideoTitle ?: getPageTitle(),
            qualityLabel = qualityLabel,
            referer = getCurrentUrl(),
            isAudioOnly = isAudioOnly,
            promoteToTop = promoteToTop
        )
        updateBubbleState(true)
    }

    fun notifyVideoInfoDetected(videoInfo: VideoInfo) {
        detectedVideoInfo = videoInfo
        detectedVideoUrl = videoInfo.qualities.firstOrNull { !it.isAudioOnly }?.downloadUrl
            ?: videoInfo.qualities.firstOrNull()?.downloadUrl
            ?: detectedVideoUrl
        if (videoInfo.title.isNotBlank() && !videoInfo.title.startsWith("http")) {
            detectedVideoTitle = videoInfo.title
        }
        val referer = videoInfo.extractedHeaders["Referer"] ?: videoInfo.sourcePageUrl.ifBlank { getCurrentUrl() }
        val ua = videoInfo.extractedHeaders["User-Agent"]
        for (q in videoInfo.qualities) {
            addOrUpdateDetectedVideoItem(
                url = q.downloadUrl,
                title = videoInfo.title,
                qualityLabel = q.label,
                format = q.format,
                sizeBytes = q.approximateSize,
                durationSeconds = videoInfo.durationSeconds,
                referer = referer,
                userAgent = ua,
                isAudioOnly = q.isAudioOnly,
                promoteToTop = true
            )
        }
        updateBubbleState(true)
    }

    private fun shouldDirectExtract(targetUrl: String): Boolean {
        val lower = targetUrl.lowercase()
        val pathOnly = lower.substringBefore('?').substringBefore('#')
        return pathOnly.endsWith(".mp4") || pathOnly.endsWith(".m3u8") || pathOnly.endsWith(".webm") ||
            lower.contains("youtube.com") || lower.contains("youtu.be") ||
            lower.contains("instagram.com") ||
            lower.contains("facebook.com") || lower.contains("fb.watch") ||
            lower.contains("tiktok.com") ||
            lower.contains("vimeo.com") || lower.contains("dailymotion.com") ||
            lower.contains("x.com") || lower.contains("twitter.com") ||
            MonetizedDomainRegistry.isMonetizedDomain(targetUrl)
    }

    private fun extractVideoDirectly(
        url: String,
        showToast: Boolean = true,
        autoOpenModal: Boolean = false,
        autoStartDownload: Boolean = false
    ) {
        if (showToast) {
            Toast.makeText(activity, R.string.browser_toast_analyzing_url, Toast.LENGTH_SHORT).show()
        }
        pendingAutoOpenModal = pendingAutoOpenModal || autoOpenModal
        pendingAutoStartDownload = pendingAutoStartDownload || autoStartDownload

        val cKey = canonicalPageKey(url)
        if (cKey == lastExtractedCanonicalUrl && urlExtractJob?.isActive == true) {
            return
        }
        lastExtractedCanonicalUrl = cKey

        urlExtractJob?.cancel()
        urlExtractJob = activity.lifecycleScope.launch(Dispatchers.IO) {
            val videoInfo = snifferEngine.extractVideoFromUrl(url)
            if (videoInfo != null && videoInfo.qualities.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    notifyVideoInfoDetected(videoInfo)
                    val shouldStart = pendingAutoStartDownload
                    val shouldOpen = pendingAutoOpenModal
                    pendingAutoStartDownload = false
                    pendingAutoOpenModal = false
                    if (shouldStart) {
                        val best = videoInfo.qualities.firstOrNull { !it.isAudioOnly } ?: videoInfo.qualities.first()
                        val referer = videoInfo.extractedHeaders["Referer"] ?: url
                        if (!MonetizedDomainRegistry.canDownloadMonetizedVideo(
                                refererUrl = referer,
                                downloadUrl = best.downloadUrl,
                                activePageUrl = url
                            )
                        ) {
                            MonetizedDomainRegistry.launchPaywallForMonetizedLimit(activity)
                            return@withContext
                        }
                        downloadEngine.enqueueDownload(
                            title = videoInfo.title,
                            url = best.downloadUrl,
                            qualityLabel = best.label,
                            format = best.format,
                            referer = referer,
                            activePageUrl = url
                        )
                        showDownloadsManagerBottomSheet()
                    } else if (shouldOpen) {
                        showDownloadModal()
                    }
                }
            }
        }
    }

    private fun getCurrentUrl(): String? {
        val inputUrl = etUrlInput.text.toString().trim().takeIf { it.startsWith("http") }
        return webView.url?.takeIf { it != "about:blank" } ?: inputUrl
    }

    private fun getPageTitle(): String? {
        return currentWebTitle ?: webView.title
    }

    private fun setUrlBarExpanded(expanded: Boolean) {
        val lp = searchBarContainer.layoutParams as? LinearLayout.LayoutParams
        if (expanded) {
            btnBrowserHome.visibility = View.GONE
            tabCounterBox.visibility = View.GONE
            btnBrowserDownloadsBox.visibility = View.GONE
            if (lp != null) {
                lp.marginStart = 0
                lp.marginEnd = 0
                searchBarContainer.layoutParams = lp
            }
            etUrlInput.isCursorVisible = true
            btnClearUrl.visibility = if (etUrlInput.text.isNotEmpty()) View.VISIBLE else View.GONE
            etUrlInput.post {
                etUrlInput.setSelection(etUrlInput.text.length)
            }
        } else {
            btnBrowserHome.visibility = View.VISIBLE
            tabCounterBox.visibility = View.VISIBLE
            btnBrowserDownloadsBox.visibility = View.VISIBLE
            if (lp != null) {
                val marginPx = (8f * activity.resources.displayMetrics.density).toInt()
                lp.marginStart = marginPx
                lp.marginEnd = marginPx
                searchBarContainer.layoutParams = lp
            }
            btnClearUrl.visibility = if (etUrlInput.text.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTopBarAndControls() {
        btnBrowserBack.setOnClickListener {
            FirebaseAnalyticsManager.logBrowserTopBarAction("back_click", getCurrentUrl() ?: "")
            onBackPressed()
        }

        btnBrowserHome.setOnClickListener {
            FirebaseAnalyticsManager.logBrowserTopBarAction("home_click", getCurrentUrl() ?: "")
            etUrlInput.clearFocus()
            hideKeyboard()
            showDashboard()
        }

        tabCounterBox.setOnClickListener {
            FirebaseAnalyticsManager.logBrowserTopBarAction("open_tabs_sheet", getCurrentUrl() ?: "")
            etUrlInput.clearFocus()
            hideKeyboard()
            showTabManagerBottomSheet()
        }

        btnBrowserDownloadsBox.setOnClickListener {
            FirebaseAnalyticsManager.logBrowserTopBarAction("open_downloads_topbar", getCurrentUrl() ?: "")
            etUrlInput.clearFocus()
            hideKeyboard()
            showDownloadsManagerBottomSheet()
        }

        btnBrowserDownloads.setOnClickListener {
            FirebaseAnalyticsManager.logBrowserTopBarAction("open_downloads_topbar", getCurrentUrl() ?: "")
            etUrlInput.clearFocus()
            hideKeyboard()
            showDownloadsManagerBottomSheet()
        }

        cardOpenDownloads.setOnClickListener {
            FirebaseAnalyticsManager.logBrowserTopBarAction("open_downloads_dashboard", "")
            etUrlInput.clearFocus()
            hideKeyboard()
            showDownloadsManagerBottomSheet()
        }

        etUrlInput.setOnFocusChangeListener { _, hasFocus ->
            setUrlBarExpanded(hasFocus)
            if (hasFocus) {
                FirebaseAnalyticsManager.logBrowserTopBarAction("url_focus", getCurrentUrl() ?: "")
            }
        }

        etUrlInput.setOnClickListener {
            setUrlBarExpanded(true)
            etUrlInput.requestFocus()
            etUrlInput.isCursorVisible = true
        }

        webView.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN && etUrlInput.hasFocus()) {
                etUrlInput.clearFocus()
                hideKeyboard()
            }
            false
        }

        dashboardScroll.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN && etUrlInput.hasFocus()) {
                etUrlInput.clearFocus()
                hideKeyboard()
            }
            false
        }

        btnClearUrl.setOnClickListener {
            FirebaseAnalyticsManager.logBrowserTopBarAction("url_clear", etUrlInput.text.toString())
            etUrlInput.setText("")
            btnClearUrl.visibility = View.GONE
            etUrlInput.requestFocus()
            etUrlInput.isCursorVisible = true
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(etUrlInput, InputMethodManager.SHOW_IMPLICIT)
        }

        btnGoUrl.setOnClickListener {
            val input = etUrlInput.text.toString().trim()
            if (input.isNotEmpty()) {
                etUrlInput.clearFocus()
                hideKeyboard()
                loadUrlOrSearch(input, source = "address_bar_go")
            }
        }

        etUrlInput.setOnEditorActionListener { _, actionId, event ->
            val isEnterKey = event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || isEnterKey) {
                val input = etUrlInput.text.toString().trim()
                if (input.isNotEmpty()) {
                    etUrlInput.clearFocus()
                    hideKeyboard()
                    loadUrlOrSearch(input, source = "address_bar_enter")
                }
                true
            } else {
                false
            }
        }

        btnPasteDetect.setOnClickListener {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString()?.trim()
            if (clipText.isNullOrBlank()) {
                FirebaseAnalyticsManager.logBrowserPasteDetectClick(hasClipboardUrl = false)
                Toast.makeText(activity, R.string.browser_toast_clipboard_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val extractedUrl = extractFirstUrl(clipText) ?: clipText
            val clipHost = MonetizedDomainRegistry.extractHost(extractedUrl)
            FirebaseAnalyticsManager.logBrowserPasteDetectClick(hasClipboardUrl = true, host = clipHost)
            etUrlInput.setText(extractedUrl)
            btnClearUrl.visibility = View.VISIBLE
            loadUrlOrSearch(extractedUrl, autoOpenModalWhenReady = true, source = "paste_detect")
        }
        com.ushareit.core.utils.ButtonLightSweep.attachTo(btnPasteDetect)

        setupDraggableBubble()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDraggableBubble() {
        updateBubbleState(false)
        var dX = 0f
        var dY = 0f
        var downRawX = 0f
        var downRawY = 0f
        val touchSlopPx = 12f * activity.resources.displayMetrics.density

        fabSniffer.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    downRawX = event.rawX
                    downRawY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val parent = view.parent as? View
                    val maxWidth = parent?.width ?: activity.resources.displayMetrics.widthPixels
                    val maxHeight = parent?.height ?: activity.resources.displayMetrics.heightPixels
                    val newX = (event.rawX + dX).coerceIn(8f, (maxWidth - view.width - 8).toFloat().coerceAtLeast(8f))
                    val newY = (event.rawY + dY).coerceIn(8f, (maxHeight - view.height - 8).toFloat().coerceAtLeast(8f))
                    view.x = newX
                    view.y = newY
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val movedX = kotlin.math.abs(event.rawX - downRawX)
                    val movedY = kotlin.math.abs(event.rawY - downRawY)
                    if (movedX < touchSlopPx && movedY < touchSlopPx) {
                        onSnifferBubbleClicked()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onSnifferBubbleClicked() {
        val currentUrl = getCurrentUrl()
        val hasReadyVideo = detectedVideosList.isNotEmpty() || detectedVideoInfo != null || !detectedVideoUrl.isNullOrBlank()
        val host = MonetizedDomainRegistry.extractHost(currentUrl ?: "").ifEmpty { "dashboard" }
        FirebaseAnalyticsManager.logBrowserBubbleClick(
            hasDetectedVideo = hasReadyVideo,
            detectedCount = detectedVideosList.size,
            host = host
        )
        if (!currentUrl.isNullOrBlank() && currentUrl != "about:blank" && dashboardScroll.visibility != View.VISIBLE) {
            webView.evaluateJavascript(MULTI_LAYER_SNIFFER_JS, null)
        }
        if (hasReadyVideo) {
            showDownloadModal()
            return
        }

        if (!currentUrl.isNullOrBlank() && currentUrl != "about:blank" && dashboardScroll.visibility != View.VISIBLE) {
            Toast.makeText(activity, R.string.browser_toast_analyzing_url, Toast.LENGTH_SHORT).show()
            webView.evaluateJavascript(LIGHTWEIGHT_DOM_EXTRACT_JS) { rawHtmlJson ->
                activity.lifecycleScope.launch {
                    val cleanHtml = try {
                        if (!rawHtmlJson.isNullOrBlank() && rawHtmlJson != "null") {
                            JSONTokener(rawHtmlJson).nextValue().toString()
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                    val extracted = snifferEngine.extractVideoFromUrl(currentUrl, cleanHtml)
                    if (extracted != null && extracted.qualities.isNotEmpty()) {
                        notifyVideoInfoDetected(extracted)
                        showDownloadModal()
                    } else if (detectedVideosList.isNotEmpty() || !detectedVideoUrl.isNullOrBlank()) {
                        showDownloadModal()
                    } else {
                        Toast.makeText(activity, R.string.browser_toast_no_video_yet, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(activity, R.string.browser_toast_no_video_yet, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupQuickSites() {
        val siteMap = listOf(
            Triple(R.id.site_tiktok, "tiktok", "https://www.tiktok.com"),
            Triple(R.id.site_facebook, "facebook", "https://m.facebook.com/watch"),
            Triple(R.id.site_instagram, "instagram", "https://www.instagram.com/reels/"),
            Triple(R.id.site_youtube, "youtube", "https://m.youtube.com"),
            Triple(R.id.site_twitter, "twitter_x", "https://x.com/explore"),
            Triple(R.id.site_vimeo, "vimeo", "https://vimeo.com/watch"),
            Triple(R.id.site_dailymotion, "dailymotion", "https://www.dailymotion.com"),
            Triple(R.id.site_google, "google", "https://www.google.com")
        )
        for ((viewId, siteName, url) in siteMap) {
            rootView.findViewById<View>(viewId)?.setOnClickListener {
                FirebaseAnalyticsManager.logBrowserQuickSiteClick(siteName, url)
                loadUrlOrSearch(url, source = "quick_site_$siteName")
            }
        }
    }

    private fun updateDownloadIconAnimation(isDownloading: Boolean) {
        if (isDownloading) {
            if (downloadIconAnimator == null) {
                downloadIconAnimator = android.animation.ObjectAnimator.ofFloat(
                    ivBrowserDownloadsIcon,
                    "translationY",
                    -3.5f * activity.resources.displayMetrics.density,
                    3.5f * activity.resources.displayMetrics.density
                ).apply {
                    duration = 520L
                    repeatMode = android.animation.ValueAnimator.REVERSE
                    repeatCount = android.animation.ValueAnimator.INFINITE
                }
            }
            if (downloadIconAnimator?.isRunning != true) {
                downloadIconAnimator?.start()
            }
        } else {
            downloadIconAnimator?.cancel()
            ivBrowserDownloadsIcon.translationY = 0f
        }
    }

    private fun observeDownloads() {
        activity.lifecycleScope.launch {
            downloadEngine.tasksFlow.collectLatest { tasks ->
                val activeCount = tasks.count {
                    it.status == DownloadStatus.DOWNLOADING ||
                        it.status == DownloadStatus.PENDING
                }
                val completedCount = tasks.count { it.status == DownloadStatus.COMPLETED }
                btnBrowserDownloads.text = "${activeCount + completedCount}"
                updateDownloadIconAnimation(activeCount > 0)
                if (activeCount > 0) {
                    val firstActive = tasks.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
                    if (firstActive != null) {
                        tvBrowserDownloadsSubtitle.text = activity.getString(
                            R.string.browser_status_downloading,
                            firstActive.progressPercent,
                            firstActive.qualityLabel
                        )
                    }
                } else {
                    tvBrowserDownloadsSubtitle.text = activity.getString(R.string.browser_downloads_card_desc)
                }
            }
        }
    }

    fun loadUrlOrSearch(
        rawInput: String,
        autoOpenModalWhenReady: Boolean = false,
        autoStartDownload: Boolean = false,
        source: String = "address_bar"
    ) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return

        val cleanedInput = trimmed.removePrefix("://").removePrefix("//").trim()
        if (cleanedInput.isEmpty()) return

        val targetUrl: String = when {
            cleanedInput.startsWith("fb://fullscreen_video/") -> {
                val vid = cleanedInput.substringAfter("fullscreen_video/").substringBefore('?')
                "https://www.facebook.com/reel/$vid"
            }
            isAppDeepLinkOrRedirectUrl(cleanedInput) -> {
                val decoded = try { java.net.URLDecoder.decode(cleanedInput, "UTF-8") } catch (_: Exception) { cleanedInput }
                val ttId = Regex("""(?:aweme/detail/|/video/|/v/)(\d{15,22})""").find(decoded)?.groupValues?.getOrNull(1)
                val fbId = SnifferEngine.extractFacebookVideoId(decoded)
                when {
                    !ttId.isNullOrBlank() -> "https://www.tiktok.com/@tiktok/video/$ttId"
                    fbId.isNotEmpty() -> "https://www.facebook.com/reel/$fbId"
                    decoded.contains("S.browser_fallback_url=http", ignoreCase = true) -> {
                        decoded.substringAfter("S.browser_fallback_url=").substringBefore(';').substringBefore('#')
                    }
                    else -> "https://www.google.com/search?q=" + URLEncoder.encode(cleanedInput, "UTF-8")
                }
            }
            cleanedInput.startsWith("http://", ignoreCase = true) || cleanedInput.startsWith("https://", ignoreCase = true) -> {
                cleanedInput
            }
            cleanedInput.contains(" ") || !cleanedInput.contains(".") || cleanedInput.endsWith(".") -> {
                "https://www.google.com/search?q=" + URLEncoder.encode(cleanedInput, "UTF-8")
            }
            android.util.Patterns.WEB_URL.matcher("https://$cleanedInput").matches() -> {
                "https://$cleanedInput"
            }
            else -> {
                "https://www.google.com/search?q=" + URLEncoder.encode(cleanedInput, "UTF-8")
            }
        }

        val isFbPage = SnifferEngine.isFacebookPageOrProfileUrl(targetUrl)
        val isChannelBatch = SnifferEngine.isChannelOrPageBatchUrl(targetUrl)
        val resolvedTargetUrl = if (isChannelBatch) {
            SnifferEngine.toChannelBatchTargetUrl(targetUrl)
        } else {
            targetUrl
        }

        val isSearch = resolvedTargetUrl.contains("google.com/search?q=", ignoreCase = true)
        val host = MonetizedDomainRegistry.extractHost(resolvedTargetUrl).ifEmpty { "unknown" }
        FirebaseAnalyticsManager.logBrowserUrlLoad(
            url = resolvedTargetUrl,
            host = host,
            isSearchQuery = isSearch,
            source = source,
            isMonetized = MonetizedDomainRegistry.isMonetizedDomain(resolvedTargetUrl)
        )

        etUrlInput.clearFocus()
        setUrlBarExpanded(false)
        dashboardScroll.visibility = View.GONE
        webView.visibility = View.VISIBLE
        etUrlInput.setText(resolvedTargetUrl)
        btnClearUrl.visibility = View.VISIBLE

        resetDetectionState()
        isPageReelsBatchMode = isChannelBatch
        webView.settings.userAgentString = if (isChannelBatch) {
            SnifferEngine.DESKTOP_USER_AGENT
        } else {
            SnifferEngine.MOBILE_USER_AGENT
        }

        tabManager.updateCurrentTabUrl(resolvedTargetUrl, resolvedTargetUrl)
        webView.loadUrl(resolvedTargetUrl)

        if (isChannelBatch) {
            pendingAutoOpenModal = pendingAutoOpenModal || autoOpenModalWhenReady
            pendingAutoStartDownload = pendingAutoStartDownload || autoStartDownload
            Toast.makeText(
                activity,
                "Đang tự động quét toàn bộ Reels/Shorts của Kênh...",
                Toast.LENGTH_SHORT
            ).show()
            if (isFbPage) {
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    val batchInfos = snifferEngine.extractFacebookPageReelsBatch(resolvedTargetUrl)
                    if (batchInfos.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            for (vInfo in batchInfos) {
                                val bestQ = vInfo.qualities.firstOrNull { !it.isAudioOnly } ?: continue
                                addOrUpdateDetectedVideoItem(
                                    url = bestQ.downloadUrl,
                                    title = vInfo.title,
                                    qualityLabel = bestQ.label,
                                    format = bestQ.format,
                                    sizeBytes = bestQ.approximateSize,
                                    durationSeconds = vInfo.durationSeconds,
                                    referer = vInfo.sourcePageUrl,
                                    userAgent = SnifferEngine.DESKTOP_USER_AGENT,
                                    promoteToTop = false
                                )
                            }
                        }
                    }
                }
            }
        } else if (autoOpenModalWhenReady || autoStartDownload || shouldDirectExtract(resolvedTargetUrl)) {
            extractVideoDirectly(
                url = resolvedTargetUrl,
                showToast = autoOpenModalWhenReady,
                autoOpenModal = autoOpenModalWhenReady,
                autoStartDownload = autoStartDownload
            )
        }
    }

    fun showDashboard() {
        etUrlInput.clearFocus()
        setUrlBarExpanded(false)
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.visibility = View.GONE
        dashboardScroll.visibility = View.VISIBLE
        etUrlInput.setText("")
        btnClearUrl.visibility = View.GONE
        resetDetectionState()
        tabManager.updateCurrentTabUrl("about:blank", "Home")
    }

    fun onBackPressed(): Boolean {
        if (etUrlInput.hasFocus()) {
            etUrlInput.clearFocus()
            setUrlBarExpanded(false)
            hideKeyboard()
            return true
        }
        if (dashboardScroll.visibility != View.VISIBLE) {
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            } else {
                showDashboard()
                return true
            }
        }
        return false
    }

    private fun applyTransparentBottomSheet(dialog: BottomSheetDialog, sheetView: View) {
        dialog.setOnShowListener {
            (sheetView.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    @SuppressLint("InflateParams")
    private fun showDownloadModal() {
        val dialog = BottomSheetDialog(activity)
        val sheetView = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_download, null)
        dialog.setContentView(sheetView)
        applyTransparentBottomSheet(dialog, sheetView)

        val tvModalHeaderTitle = sheetView.findViewById<TextView>(R.id.tv_modal_header_title)
        val tvModalSource = sheetView.findViewById<TextView>(R.id.tv_modal_source)
        val tvTitle = sheetView.findViewById<TextView>(R.id.tv_dialog_video_title)
        val tvSubtitle = sheetView.findViewById<TextView>(R.id.tv_dialog_video_subtitle)
        val containerDetectedVideos = sheetView.findViewById<LinearLayout>(R.id.container_detected_videos)

        val btnPlayPreview = sheetView.findViewById<TextView>(R.id.btn_play_preview)
        val btnConfirmDownload = sheetView.findViewById<TextView>(R.id.btn_confirm_download)
        val btnDownloadAllVideos = sheetView.findViewById<TextView>(R.id.btn_download_all_videos)

        val info = detectedVideoInfo
        val homeTitle = getPageTitle()
        val topDetectedTitle = detectedVideosList.firstOrNull { !it.isLikelyAd && !it.isAudioOnly }?.title
            ?.takeIf { it.isNotBlank() && !it.equals("file.mp4", ignoreCase = true) }
        val currentTitle = topDetectedTitle
            ?: detectedVideoTitle?.takeIf { it.isNotBlank() }
            ?: info?.title?.takeIf { it.isNotBlank() && !it.equals("file.mp4", ignoreCase = true) }
            ?: homeTitle?.takeIf { it.isNotBlank() && !it.startsWith("http") }
            ?: run {
                val fileName = detectedVideoUrl?.substringAfterLast('/')?.substringBefore('?') ?: ""
                if (fileName.length > 5 && !fileName.equals("file.mp4", ignoreCase = true) && !fileName.contains("seg-")) {
                    fileName
                } else {
                    "Video_Stream.mp4"
                }
            }
        tvTitle.text = currentTitle

        val activePageUrl = getCurrentUrl() ?: info?.sourcePageUrl ?: ""
        val host = try {
            Uri.parse(activePageUrl).host?.removePrefix("www.") ?: "MP4 / HLS"
        } catch (_: Exception) {
            "MP4 / HLS"
        }
        tvModalSource.text = host.uppercase()

        if (detectedVideosList.isEmpty() && !detectedVideoUrl.isNullOrBlank()) {
            addOrUpdateDetectedVideoItem(
                url = detectedVideoUrl!!,
                title = currentTitle,
                referer = activePageUrl
            )
        }

        fun cleanLabel(label: String): String {
            return label.replace(Regex("\\[.*?\\]"), "").replace(Regex("\\s+"), " ").trim()
        }

        var selectedVideoId: String? = detectedVideosList.firstOrNull { !it.isLikelyAd && !it.isAudioOnly }?.id
            ?: detectedVideosList.firstOrNull()?.id
        var userManuallySelected = false

        fun formatDetectedSize(item: DetectedVideoItem): String {
            return when {
                item.sizeBytes > 0L -> android.text.format.Formatter.formatFileSize(activity, item.sizeBytes)
                !item.isSizeProbed -> "Checking..."
                item.format.equals("m3u8", ignoreCase = true) -> "HLS Stream"
                else -> "Ready"
            }
        }

        fun renderDetectedVideosList() {
            containerDetectedVideos.removeAllViews()
            val items = detectedVideosList.toList()
            val batchCandidatesCount = items.count { !it.isAudioOnly && !it.isLikelyAd }
            if (btnDownloadAllVideos != null) {
                if (batchCandidatesCount >= 2) {
                    btnDownloadAllVideos.visibility = View.VISIBLE
                    btnDownloadAllVideos.text = "DOWNLOAD ALL REELS / VIDEOS ($batchCandidatesCount)"
                } else {
                    btnDownloadAllVideos.visibility = View.GONE
                }
            }
            tvModalHeaderTitle.text = if (items.size > 1) {
                "${activity.getString(R.string.browser_video_detected)} (${items.size})"
            } else {
                activity.getString(R.string.browser_video_detected)
            }
            val isMonetizedModal = MonetizedDomainRegistry.isMonetizedDomain(activePageUrl) ||
                items.any { MonetizedDomainRegistry.isMonetizedDomain(it.url) || MonetizedDomainRegistry.isMonetizedDomain(it.referer) }
            if (isMonetizedModal) {
                val remaining = MonetizedDomainRegistry.getRemainingFreeMonetizedOpens()
                tvSubtitle.text = if (remaining == Int.MAX_VALUE) {
                    "VIP Active • Select video & check file size below"
                } else {
                    "Free downloads/opens left: $remaining/3 • Select video & check file size below"
                }
            }

            if (!userManuallySelected || selectedVideoId == null || items.none { it.id == selectedVideoId }) {
                selectedVideoId = items.firstOrNull { !it.isLikelyAd && !it.isAudioOnly }?.id
                    ?: items.firstOrNull()?.id
            }

            val selectedObj = items.firstOrNull { it.id == selectedVideoId }
            if (selectedObj != null && selectedObj.title.isNotBlank()) {
                tvTitle.text = selectedObj.title
            }

            for ((index, item) in items.withIndex()) {
                val rowView = LayoutInflater.from(activity)
                    .inflate(R.layout.item_detected_video, containerDetectedVideos, false)
                val card = rowView.findViewById<MaterialCardView>(R.id.card_detected_video)
                val rb = rowView.findViewById<RadioButton>(R.id.rb_select_video)
                val tvRowTitle = rowView.findViewById<TextView>(R.id.tv_detected_video_title)
                val tvRowMeta = rowView.findViewById<TextView>(R.id.tv_detected_video_meta)
                val tvRowSize = rowView.findViewById<TextView>(R.id.tv_detected_video_size)

                val isSelected = item.id == selectedVideoId
                rb.isChecked = isSelected
                card.strokeColor = ContextCompat.getColor(
                    activity,
                    if (isSelected) R.color.primary else R.color.border_card
                )
                card.strokeWidth = if (isSelected) {
                    (2f * activity.resources.displayMetrics.density).toInt()
                } else {
                    (1f * activity.resources.displayMetrics.density).toInt()
                }

                val isChecking = item.sizeBytes <= 0L && !item.isSizeProbed
                val cleanQ = cleanLabel(item.qualityLabel)
                val streamTitle = item.title.takeIf { it.isNotBlank() && !it.startsWith("Video_") } ?: currentTitle
                val prefixTag = if (streamTitle.startsWith("Video #")) {
                    streamTitle.substringBefore("•").trim()
                } else ""
                tvRowTitle.text = if (prefixTag.isNotEmpty()) "$prefixTag • $cleanQ" else cleanQ
                tvRowTitle.setTextColor(
                    ContextCompat.getColor(
                        activity,
                        if (isChecking) R.color.red_danger else R.color.text_white
                    )
                )

                val streamTypeTag = when {
                    item.isLikelyAd -> "Short Clip / Ad"
                    item.isAudioOnly -> "Audio Track (${item.format.uppercase()})"
                    index == 0 -> "Latest / Playing (${item.format.uppercase()})"
                    else -> "Waiting List (${item.format.uppercase()})"
                }
                val durPart = if (item.durationSeconds > 0) {
                    val mins = item.durationSeconds / 60
                    val secs = item.durationSeconds % 60
                    String.format(" • %d:%02d", mins, secs)
                } else ""
                tvRowMeta.text = "$streamTypeTag$durPart • $streamTitle"
                tvRowMeta.setTextColor(
                    ContextCompat.getColor(
                        activity,
                        when {
                            isChecking -> R.color.red_danger
                            item.isLikelyAd -> R.color.yellow_warning
                            else -> R.color.text_gray
                        }
                    )
                )

                tvRowSize.text = formatDetectedSize(item)
                tvRowSize.setTextColor(
                    ContextCompat.getColor(
                        activity,
                        if (isChecking) R.color.red_danger else R.color.primary
                    )
                )

                rowView.setOnClickListener {
                    userManuallySelected = true
                    selectedVideoId = item.id
                    tvTitle.text = streamTitle
                    FirebaseAnalyticsManager.logBrowserDetectedModalAction(
                        action = "select_stream",
                        host = host,
                        qualityLabel = cleanQ,
                        format = item.format,
                        sizeBytes = item.sizeBytes,
                        isLikelyAd = item.isLikelyAd
                    )
                    renderDetectedVideosList()
                }

                containerDetectedVideos.addView(rowView)
            }
        }

        activeModalRefreshCallback = {
            if (dialog.isShowing) {
                renderDetectedVideosList()
            }
        }
        dialog.setOnDismissListener {
            activeModalRefreshCallback = null
        }

        renderDetectedVideosList()

        fun resolveSelectedItem(): DetectedVideoItem? {
            return detectedVideosList.firstOrNull { it.id == selectedVideoId }
                ?: detectedVideosList.firstOrNull { !it.isLikelyAd }
                ?: detectedVideosList.firstOrNull()
        }

        val initialSelected = resolveSelectedItem()
        FirebaseAnalyticsManager.logBrowserDetectedModalAction(
            action = "open_modal",
            host = host,
            qualityLabel = initialSelected?.let { cleanLabel(it.qualityLabel) } ?: "",
            format = initialSelected?.format ?: "",
            sizeBytes = initialSelected?.sizeBytes ?: 0L,
            isLikelyAd = initialSelected?.isLikelyAd ?: false
        )

        btnPlayPreview.setOnClickListener {
            val selectedItem = resolveSelectedItem()
            val playUrl = selectedItem?.url ?: detectedVideoUrl
            val referer = selectedItem?.referer ?: activePageUrl
            FirebaseAnalyticsManager.logBrowserDetectedModalAction(
                action = "preview_stream",
                host = host,
                qualityLabel = selectedItem?.let { cleanLabel(it.qualityLabel) } ?: "",
                format = selectedItem?.format ?: "",
                sizeBytes = selectedItem?.sizeBytes ?: 0L,
                isLikelyAd = selectedItem?.isLikelyAd ?: false
            )
            if (!playUrl.isNullOrEmpty()) {
                val allowed = MonetizedDomainRegistry.canOpenAndConsumeMonetizedVideo(
                    fileNameOrKey = selectedItem?.title ?: currentTitle,
                    refererUrl = referer,
                    downloadUrl = playUrl,
                    activePageUrl = activePageUrl
                )
                if (!allowed) {
                    dialog.dismiss()
                    MonetizedDomainRegistry.launchPaywallForMonetizedLimit(activity)
                    return@setOnClickListener
                }
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(playUrl), if (selectedItem?.isAudioOnly == true) "audio/*" else "video/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(intent)
                } catch (_: Exception) {
                    loadUrlOrSearch(playUrl, source = "preview_fallback")
                }
            } else {
                Toast.makeText(activity, R.string.browser_toast_no_video_yet, Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        btnConfirmDownload.setOnClickListener {
            if (!btnConfirmDownload.isEnabled) return@setOnClickListener
            btnConfirmDownload.isEnabled = false
            val selectedItem = resolveSelectedItem()
            val downloadUrl = selectedItem?.url ?: detectedVideoUrl
            val isAudio = selectedItem?.isAudioOnly == true
            val qualityLabel = cleanLabel(selectedItem?.qualityLabel ?: "1080p Full HD")
            val format = if (isAudio) "mp3" else (selectedItem?.format ?: "mp4")
            val referer = selectedItem?.referer ?: info?.extractedHeaders?.get("Referer") ?: activePageUrl
            val finalTitle = selectedItem?.title?.takeIf { it.isNotBlank() } ?: currentTitle

            FirebaseAnalyticsManager.logBrowserDetectedModalAction(
                action = "confirm_download",
                host = host,
                qualityLabel = qualityLabel,
                format = format,
                sizeBytes = selectedItem?.sizeBytes ?: 0L,
                isLikelyAd = selectedItem?.isLikelyAd ?: false
            )

            if (!downloadUrl.isNullOrEmpty()) {
                val allowed = MonetizedDomainRegistry.canDownloadMonetizedVideo(
                    refererUrl = referer,
                    downloadUrl = downloadUrl,
                    activePageUrl = activePageUrl
                )
                if (!allowed) {
                    dialog.dismiss()
                    MonetizedDomainRegistry.launchPaywallForMonetizedLimit(activity)
                    return@setOnClickListener
                }
                downloadEngine.enqueueDownload(
                    title = finalTitle,
                    url = downloadUrl,
                    qualityLabel = qualityLabel,
                    format = format,
                    referer = referer,
                    activePageUrl = activePageUrl
                )
                Toast.makeText(
                    activity,
                    activity.getString(R.string.browser_toast_download_started, finalTitle, qualityLabel),
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
                showDownloadsManagerBottomSheet()
            } else {
                Toast.makeText(activity, R.string.browser_toast_no_video_yet, Toast.LENGTH_SHORT).show()
            }
        }

        btnDownloadAllVideos?.setOnClickListener {
            dialog.dismiss()
            enqueueAllDetectedVideos()
        }

        dialog.show()
    }

    fun enqueueAllDetectedVideos() {
        val activePageUrl = getCurrentUrl() ?: ""
        val batchItems = detectedVideosList.filter { !it.isAudioOnly && !it.isLikelyAd }
        if (batchItems.isEmpty()) {
            Toast.makeText(activity, R.string.browser_toast_no_video_yet, Toast.LENGTH_SHORT).show()
            return
        }
        var enqueuedCount = 0
        for (item in batchItems) {
            val referer = item.referer ?: activePageUrl
            val allowed = MonetizedDomainRegistry.canDownloadMonetizedVideo(
                refererUrl = referer,
                downloadUrl = item.url,
                activePageUrl = activePageUrl
            )
            if (!allowed) {
                MonetizedDomainRegistry.launchPaywallForMonetizedLimit(activity)
                break
            }
            downloadEngine.enqueueDownload(
                title = item.title.ifBlank { "Reel_${enqueuedCount + 1}" },
                url = item.url,
                qualityLabel = item.qualityLabel,
                format = item.format,
                referer = referer,
                activePageUrl = activePageUrl
            )
            enqueuedCount++
        }
        if (enqueuedCount > 0) {
            Toast.makeText(
                activity,
                "Đã thêm $enqueuedCount video Reels vào hàng đợi tải tự động!",
                Toast.LENGTH_LONG
            ).show()
            showDownloadsManagerBottomSheet()
        }
    }

    @SuppressLint("InflateParams")
    private fun showTabManagerBottomSheet() {
        val dialog = BottomSheetDialog(activity)
        val sheetView = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_tabs, null)
        dialog.setContentView(sheetView)
        applyTransparentBottomSheet(dialog, sheetView)

        FirebaseAnalyticsManager.logBrowserTabAction("open_sheet", tabManager.tabsCount)

        val containerTabsList = sheetView.findViewById<LinearLayout>(R.id.container_tabs_list)
        val btnAddTab = sheetView.findViewById<TextView>(R.id.btn_tabs_new_tab)
        val btnClearTabs = sheetView.findViewById<TextView>(R.id.btn_tabs_close_all)
        val btnDismiss = sheetView.findViewById<TextView>(R.id.btn_tabs_dismiss)

        btnDismiss.setOnClickListener {
            FirebaseAnalyticsManager.logBrowserTabAction("dismiss_sheet", tabManager.tabsCount)
            dialog.dismiss()
        }

        fun refreshTabsList() {
            updateTabBadge()
            containerTabsList.removeAllViews()
            val allTabs = tabManager.getAllTabs()
            val activeId = tabManager.getActiveTabId()
            for (tab in allTabs) {
                val itemView = LayoutInflater.from(activity)
                    .inflate(R.layout.item_tab_card, containerTabsList, false)
                val cardRoot = itemView.findViewById<LinearLayout>(R.id.card_tab_root)
                val tvTabTitle = itemView.findViewById<TextView>(R.id.tv_tab_title)
                val tvTabUrl = itemView.findViewById<TextView>(R.id.tv_tab_url)
                val tvActiveBadge = itemView.findViewById<TextView>(R.id.tv_tab_active_badge)
                val btnClose = itemView.findViewById<TextView>(R.id.btn_close_single_tab)

                val isCurrent = (tab.id == activeId)
                cardRoot.setBackgroundResource(
                    if (isCurrent) R.drawable.bg_tab_card_active else R.drawable.bg_tab_card_normal
                )
                tvActiveBadge.visibility = if (isCurrent) View.VISIBLE else View.GONE
                tvTabTitle.text = tab.title.ifBlank { "New Tab" }
                tvTabUrl.text = if (tab.currentUrl == "about:blank") "Dashboard" else tab.currentUrl

                itemView.setOnClickListener {
                    tabManager.selectTab(tab.id)
                    updateTabBadge()
                    FirebaseAnalyticsManager.logBrowserTabAction("switch_tab", tabManager.tabsCount, tab.currentUrl)
                    if (tab.currentUrl == "about:blank") {
                        showDashboard()
                    } else {
                        loadUrlOrSearch(tab.currentUrl, source = "tab_switch")
                    }
                    dialog.dismiss()
                }

                btnClose.setOnClickListener {
                    val current = tabManager.closeTab(tab.id)
                    FirebaseAnalyticsManager.logBrowserTabAction("close_tab", tabManager.tabsCount, tab.currentUrl)
                    if (current == null || current.currentUrl == "about:blank") {
                        showDashboard()
                    } else {
                        loadUrlOrSearch(current.currentUrl, source = "tab_close_fallback")
                    }
                    refreshTabsList()
                }

                containerTabsList.addView(itemView)
            }
        }

        btnAddTab.setOnClickListener {
            tabManager.createNewTab("New Tab", "about:blank")
            updateTabBadge()
            FirebaseAnalyticsManager.logBrowserTabAction("new_tab", tabManager.tabsCount, "about:blank")
            showDashboard()
            dialog.dismiss()
        }

        btnClearTabs.setOnClickListener {
            tabManager.closeAllTabs()
            updateTabBadge()
            FirebaseAnalyticsManager.logBrowserTabAction("close_all_tabs", tabManager.tabsCount, "about:blank")
            showDashboard()
            dialog.dismiss()
        }

        refreshTabsList()
        dialog.show()
    }

    @SuppressLint("InflateParams")
    fun showDownloadsManagerBottomSheet() {
        downloadEngine.syncExistingDownloads()
        val dialog = BottomSheetDialog(activity)
        val sheetView = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_downloads_manager, null)
        dialog.setContentView(sheetView)
        applyTransparentBottomSheet(dialog, sheetView)

        FirebaseAnalyticsManager.logBrowserDownloadTaskAction("open_downloads_sheet")

        val btnCloseSheet = sheetView.findViewById<View>(R.id.btn_close_downloads_sheet)
        val tabDownloading = sheetView.findViewById<TextView>(R.id.tab_downloading)
        val tabDownloaded = sheetView.findViewById<TextView>(R.id.tab_downloaded)
        val sectionDownloading = sheetView.findViewById<LinearLayout>(R.id.section_downloading)
        val sectionDownloaded = sheetView.findViewById<LinearLayout>(R.id.section_downloaded)
        val containerActive = sheetView.findViewById<LinearLayout>(R.id.container_downloading)
        val containerCompleted = sheetView.findViewById<LinearLayout>(R.id.container_downloaded)
        val tvEmptyActive = sheetView.findViewById<TextView>(R.id.tv_empty_downloading)
        val tvEmptyCompleted = sheetView.findViewById<TextView>(R.id.tv_empty_downloaded)

        btnCloseSheet.setOnClickListener {
            FirebaseAnalyticsManager.logBrowserDownloadTaskAction("close_downloads_sheet")
            dialog.dismiss()
        }

        var showingCompletedTab = false
        fun switchSubTab(showCompleted: Boolean) {
            showingCompletedTab = showCompleted
            if (!showCompleted) {
                sectionDownloading.visibility = View.VISIBLE
                sectionDownloaded.visibility = View.GONE
                tabDownloading.setBackgroundResource(R.drawable.bg_segmented_tab_active)
                tabDownloading.setTextColor(Color.WHITE)
                tabDownloaded.setBackgroundColor(Color.TRANSPARENT)
                tabDownloaded.setTextColor(ContextCompat.getColor(activity, R.color.text_gray))
            } else {
                sectionDownloading.visibility = View.GONE
                sectionDownloaded.visibility = View.VISIBLE
                tabDownloaded.setBackgroundResource(R.drawable.bg_segmented_tab_active)
                tabDownloaded.setTextColor(Color.WHITE)
                tabDownloading.setBackgroundColor(Color.TRANSPARENT)
                tabDownloading.setTextColor(ContextCompat.getColor(activity, R.color.text_gray))
            }
        }

        tabDownloading.setOnClickListener {
            FirebaseAnalyticsManager.logBrowserDownloadTaskAction("switch_tab_downloading")
            switchSubTab(false)
        }
        tabDownloaded.setOnClickListener {
            FirebaseAnalyticsManager.logBrowserDownloadTaskAction("switch_tab_downloaded")
            switchSubTab(true)
        }

        val job = activity.lifecycleScope.launch {
            downloadEngine.tasksFlow.collectLatest { tasks ->
                val activeList = tasks.filter { it.status != DownloadStatus.COMPLETED }
                val completedList = tasks.filter { it.status == DownloadStatus.COMPLETED }

                tabDownloading.text = "Downloading (${activeList.size})"
                tabDownloaded.text = "Downloaded (${completedList.size})"

                if (activeList.isEmpty() && completedList.isNotEmpty() && !showingCompletedTab) {
                    switchSubTab(true)
                }

                containerActive.removeAllViews()
                if (activeList.isEmpty()) {
                    tvEmptyActive.visibility = View.VISIBLE
                } else {
                    tvEmptyActive.visibility = View.GONE
                    for (task in activeList) {
                        val row = LayoutInflater.from(activity)
                            .inflate(R.layout.item_task_downloading, containerActive, false)
                        val tvTitle = row.findViewById<TextView>(R.id.tv_task_title)
                        val tvStats = row.findViewById<TextView>(R.id.tv_task_stats)
                        val tvPercent = row.findViewById<TextView>(R.id.tv_task_percent)
                        val pbTask = row.findViewById<ProgressBar>(R.id.pb_task_progress)
                        val btnPause = row.findViewById<TextView>(R.id.btn_task_pause)
                        val btnMore = row.findViewById<View>(R.id.btn_task_more)

                        val progress = task.progressPercent
                        tvTitle.text = task.title
                        pbTask.progress = progress
                        tvPercent.text = "$progress%"

                        when (task.status) {
                            DownloadStatus.DOWNLOADING -> {
                                tvStats.text = activity.getString(
                                    R.string.browser_status_downloading,
                                    progress,
                                    task.qualityLabel
                                )
                                tvStats.setTextColor(ContextCompat.getColor(activity, R.color.text_gray))
                                btnPause.text = "PAUSE"
                                btnPause.setTextColor(ContextCompat.getColor(activity, R.color.primary_light))
                            }
                            DownloadStatus.PAUSED -> {
                                tvStats.text = activity.getString(R.string.browser_status_paused, progress)
                                tvStats.setTextColor(ContextCompat.getColor(activity, R.color.text_gray))
                                btnPause.text = "RESUME"
                                btnPause.setTextColor(ContextCompat.getColor(activity, R.color.primary_light))
                            }
                            DownloadStatus.FAILED -> {
                                tvStats.text = activity.getString(
                                    R.string.browser_status_failed,
                                    task.errorMessage ?: "Network error"
                                )
                                tvStats.setTextColor(ContextCompat.getColor(activity, R.color.red_danger))
                                btnPause.text = "RETRY"
                                btnPause.setTextColor(ContextCompat.getColor(activity, R.color.red_danger))
                            }
                            DownloadStatus.PENDING -> {
                                tvStats.text = activity.getString(R.string.browser_status_pending)
                                tvStats.setTextColor(ContextCompat.getColor(activity, R.color.text_gray))
                                btnPause.text = "PAUSE"
                                btnPause.setTextColor(ContextCompat.getColor(activity, R.color.primary_light))
                            }
                            else -> {}
                        }

                        btnPause.setOnClickListener {
                            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PENDING) {
                                FirebaseAnalyticsManager.logBrowserDownloadTaskAction("pause", task.title, task.status.name)
                                downloadEngine.pauseTask(task.id)
                            } else if (task.status == DownloadStatus.FAILED) {
                                FirebaseAnalyticsManager.logBrowserDownloadTaskAction("retry", task.title, task.status.name, task.errorMessage ?: "")
                                downloadEngine.resumeTask(task.id)
                            } else if (task.status == DownloadStatus.PAUSED) {
                                FirebaseAnalyticsManager.logBrowserDownloadTaskAction("resume", task.title, task.status.name)
                                downloadEngine.resumeTask(task.id)
                            }
                        }

                        btnMore.setOnClickListener {
                            FirebaseAnalyticsManager.logBrowserDownloadTaskAction("cancel_delete_active", task.title, task.status.name)
                            downloadEngine.deleteTask(task.id)
                        }

                        containerActive.addView(row)
                    }
                }

                containerCompleted.removeAllViews()
                if (completedList.isEmpty()) {
                    tvEmptyCompleted.visibility = View.VISIBLE
                } else {
                    tvEmptyCompleted.visibility = View.GONE
                    for (task in completedList) {
                        val row = LayoutInflater.from(activity)
                            .inflate(R.layout.item_task_downloaded, containerCompleted, false)
                        val tvTitle = row.findViewById<TextView>(R.id.tv_downloaded_title)
                        val tvInfo = row.findViewById<TextView>(R.id.tv_downloaded_info)
                        val iconPlay = row.findViewById<View>(R.id.icon_play_downloaded)
                        val ivThumb = row.findViewById<ImageView>(R.id.iv_downloaded_thumb)
                        val btnPlay = row.findViewById<TextView>(R.id.btn_play_downloaded)
                        val btnMore = row.findViewById<View>(R.id.btn_downloaded_more)

                        tvTitle.text = task.title
                        val file = File(task.targetFilePath)
                        val sizeStr = if (file.exists()) android.text.format.Formatter.formatFileSize(activity, file.length()) else ""
                        tvInfo.text = if (sizeStr.isNotEmpty()) "${task.qualityLabel} • $sizeStr" else task.qualityLabel

                        if (file.exists() && ivThumb != null) {
                            activity.lifecycleScope.launch(Dispatchers.IO) {
                                val bmp = try {
                                    @Suppress("DEPRECATION")
                                    android.media.ThumbnailUtils.createVideoThumbnail(
                                        file.absolutePath,
                                        android.provider.MediaStore.Video.Thumbnails.MINI_KIND
                                    )
                                } catch (_: Exception) {
                                    null
                                }
                                if (bmp != null) {
                                    withContext(Dispatchers.Main) {
                                        ivThumb.setImageBitmap(bmp)
                                    }
                                }
                            }
                        }

                        val playAction = View.OnClickListener {
                            if (file.exists()) {
                                FirebaseAnalyticsManager.logBrowserDownloadTaskAction("play_downloaded", task.title, "COMPLETED", task.qualityLabel)
                                val allowed = MonetizedDomainRegistry.canOpenAndConsumeMonetizedVideo(
                                    fileNameOrKey = file.name,
                                    refererUrl = task.referer,
                                    downloadUrl = task.downloadUrl
                                )
                                if (!allowed) {
                                    dialog.dismiss()
                                    MonetizedDomainRegistry.launchPaywallForMonetizedLimit(activity)
                                    return@OnClickListener
                                }
                                P2PTransferManager.openFile(activity, file)
                            }
                        }
                        row.setOnClickListener(playAction)
                        iconPlay.setOnClickListener(playAction)
                        btnPlay.setOnClickListener(playAction)

                        btnMore.setOnClickListener { anchor ->
                            val popup = PopupMenu(activity, anchor)
                            popup.menu.add(0, 1, 0, "Save to Gallery")
                            popup.menu.add(0, 2, 1, "View in Received Files")
                            popup.menu.add(0, 3, 2, activity.getString(R.string.btn_delete))
                            popup.setOnMenuItemClickListener { item ->
                                when (item.itemId) {
                                    1 -> {
                                        FirebaseAnalyticsManager.logBrowserDownloadTaskAction("save_to_gallery", task.title, "COMPLETED")
                                        val allowed = MonetizedDomainRegistry.canOpenAndConsumeMonetizedVideo(
                                            fileNameOrKey = file.name,
                                            refererUrl = task.referer,
                                            downloadUrl = task.downloadUrl
                                        )
                                        if (!allowed) {
                                            dialog.dismiss()
                                            MonetizedDomainRegistry.launchPaywallForMonetizedLimit(activity)
                                            return@setOnMenuItemClickListener true
                                        }
                                        activity.lifecycleScope.launch {
                                            val uri = withContext(Dispatchers.IO) {
                                                MediaStoreExporter.exportVideoToGallery(
                                                    activity.applicationContext,
                                                    file,
                                                    task.title
                                                )
                                            }
                                            if (uri != null) {
                                                Toast.makeText(
                                                    activity,
                                                    R.string.browser_toast_saved_gallery,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    activity,
                                                    R.string.browser_toast_save_gallery_failed,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        true
                                    }
                                    2 -> {
                                        FirebaseAnalyticsManager.logBrowserDownloadTaskAction("view_in_received_files", task.title, "COMPLETED")
                                        dialog.dismiss()
                                        activity.startActivity(Intent(activity, ReceivedFilesActivity::class.java))
                                        true
                                    }
                                    3 -> {
                                        FirebaseAnalyticsManager.logBrowserDownloadTaskAction("delete_completed", task.title, "COMPLETED")
                                        downloadEngine.deleteTask(task.id)
                                        true
                                    }
                                    else -> false
                                }
                            }
                            popup.show()
                        }

                        containerCompleted.addView(row)
                    }
                }
            }
        }

        dialog.setOnDismissListener {
            job.cancel()
        }

        dialog.show()
    }

    private fun updateTabBadge() {
        tabCounterBox.text = tabManager.tabsCount.toString()
    }

    private fun extractFirstUrl(text: String): String? {
        val regex = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
        return regex.find(text)?.value
    }

    private fun hideKeyboard() {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etUrlInput.windowToken, 0)
    }

    companion object {
        private val AD_BLOCK_DOMAINS_AND_PATTERNS = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google",
            "pagead2.googlesyndication.com", "imasdk.googleapis.com", "googletagservices.com",
            "exoclick.com", "exosrv.com", "tsyndicate.com", "trafficjunky.net", "trafficjunky.com",
            "juicyads.com", "realsrv.com", "magsrv.com", "popads.net", "popcash.net",
            "propellerads.com", "adsterra.com", "adsterratools.com", "clickadu.com",
            "hilltopads.net", "bidgear.com", "galaksion.com", "acint.net", "etahub.com",
            "outbrain.com", "taboola.com", "mgid.com", "revcontent.com", "zergnet.com",
            "adnxs.com", "criteo.com", "criteo.net", "pubmatic.com", "rubiconproject.com",
            "openx.net", "smartadserver.com", "casalemedia.com", "moatads.com",
            "auct.xhamster.com", "collector.xhamster.com", "bcvcdn.com/hlsm/", "aucdn.net",
            "modusygunaciro.com", "stripchat.com", "chaturbate.com", "livejasmin.com",
            "bongacams.com", "flirtify.com", "faphouse.com", "cam4.com", "myfreecams.com",
            "jerkmate.com", "bet365", "1xbet", "kubet", "thabet", "w88", "fun88", "fb88", "m88",
            "/adserver", "/adframe", "/adbanner", "/popunder", "/pop.js", "/vast.xml",
            "adsbygoogle.js", "show_ads.js", "/pagead/"
        )

        private const val LIGHTWEIGHT_DOM_EXTRACT_JS = """
            (function() {
                try {
                    var headHtml = document.head ? document.head.innerHTML : '';
                    var bodyPart = '';
                    var vids = document.querySelectorAll('video, source');
                    for (var i = 0; i < vids.length; i++) {
                        bodyPart += vids[i].outerHTML + '\n';
                    }
                    var scripts = document.getElementsByTagName('script');
                    for (var j = 0; j < scripts.length; j++) {
                        var t = scripts[j].textContent || '';
                        if (t.indexOf('.mp4') !== -1 || t.indexOf('.m3u8') !== -1 || t.indexOf('videoUrl') !== -1 ||
                            t.indexOf('setVideo') !== -1 || t.indexOf('mediaDefinitions') !== -1 || t.indexOf('playAddr') !== -1 ||
                            t.indexOf('playable_url') !== -1 || t.indexOf('video_versions') !== -1) {
                            bodyPart += '<script>' + t.slice(0, 80000) + '</script>\n';
                        }
                    }
                    return ('<html><head>' + headHtml + '</head><body>' + bodyPart + '</body></html>').slice(0, 350000);
                } catch(e) {
                    return '';
                }
            })();
        """

        private const val ANTI_POPUP_JS = """
            (function() {
                try {
                    if (window.Env) {
                        window.Env.hwlstpub = true;
                    }
                    window.open = function(url, target, features) {
                        try {
                            if (!url) return null;
                            var raw = String(url).trim();
                            var low = raw.toLowerCase();
                            if (!raw || low === 'about:blank' || low.indexOf('javascript:') === 0) {
                                return null;
                            }
                            if (low.indexOf('exoclick') !== -1 || low.indexOf('tsyndicate') !== -1 || low.indexOf('trafficjunky') !== -1 ||
                                low.indexOf('stripchat') !== -1 || low.indexOf('chaturbate') !== -1 || low.indexOf('livejasmin') !== -1 ||
                                low.indexOf('popads') !== -1 || low.indexOf('popcash') !== -1 || low.indexOf('adsterra') !== -1 ||
                                low.indexOf('realsrv') !== -1 || low.indexOf('juicyads') !== -1 || low.indexOf('clickadu') !== -1 ||
                                low.indexOf('hilltopads') !== -1 || low.indexOf('galaksion') !== -1 || low.indexOf('bidgear') !== -1 ||
                                low.indexOf('onelink.me') !== -1 || low.indexOf('.app.link') !== -1 || low.indexOf('.page.link') !== -1 ||
                                low.indexOf('play.google.com/store') !== -1 || low.indexOf('market.android.com') !== -1 ||
                                low.indexOf('apps.apple.com') !== -1 || low.indexOf('itunes.apple.com') !== -1 ||
                                low.indexOf('snssdk') !== -1 || low.indexOf('intent:') === 0 || low.indexOf('fb:') === 0 ||
                                low.indexOf('tiktok:') === 0 || low.indexOf('musically:') === 0 || low.indexOf('instagram:') === 0 ||
                                low.indexOf('market:') === 0 || low.indexOf('vnd.youtube:') === 0) {
                                return null;
                            }
                            var isHttpOrRel = (low.indexOf('http://') === 0 || low.indexOf('https://') === 0 || low.indexOf('/') === 0);
                            if (!isHttpOrRel) {
                                return null;
                            }
                            var tgt = (target || '').toLowerCase();
                            var hasUserGesture = (Date.now() - (window.__shareNowLastTouchAt || 0) < 2500) ||
                                (navigator.userActivation && navigator.userActivation.isActive);
                            if (tgt === '_self' || tgt === '_top' || tgt === '_parent' || low.indexOf('/') === 0 || hasUserGesture) {
                                window.location.assign(raw);
                                return window;
                            }
                            var currHost = (window.location.hostname || '').toLowerCase().replace(/^www\./, '').replace(/^m\./, '');
                            var linkHost = '';
                            try {
                                linkHost = (new URL(raw, window.location.href)).hostname.toLowerCase().replace(/^www\./, '').replace(/^m\./, '');
                            } catch(e) {}
                            if (currHost && linkHost && (currHost === linkHost || currHost.endsWith('.' + linkHost) || linkHost.endsWith('.' + currHost))) {
                                window.location.assign(raw);
                                return window;
                            }
                            return null;
                        } catch(e) {
                            return null;
                        }
                    };
                    if (window.top && window.top !== window) {
                        try { window.top.open = window.open; } catch(e) {}
                    }
                    var adStyleId = 'sharenow-adblock-style';
                    if (!document.getElementById(adStyleId)) {
                        var st = document.createElement('style');
                        st.id = adStyleId;
                        st.type = 'text/css';
                        st.innerHTML = 'iframe[src*="ads"], iframe[src*="exoclick"], iframe[src*="exosrv"], iframe[src*="tsyndicate"], iframe[src*="trafficjunky"], iframe[src*="juicyads"], iframe[src*="realsrv"], iframe[src*="magsrv"], iframe[src*="doubleclick"], iframe[src*="googlesyndication"], iframe[src*="stripchat"], iframe[src*="chaturbate"], .ad-container, .ads-container, .ad-banner, .banner-ad, .adsbyexoclick, .ins-ad, .exo-native-widget, .tj-banner, .preroll-container, .video-ad-overlay, [id^="ad_"], [id*="ad-banner"], [class*="ad-banner"], [class*="ads-banner"], [class*="popunder"], [id*="popunder"], a[href*="exoclick"], a[href*="tsyndicate"], a[href*="trafficjunky"], a[href*="stripchat"], a[href*="chaturbate"], a[href*="livejasmin"] { display: none !important; visibility: hidden !important; width: 0 !important; height: 0 !important; opacity: 0 !important; pointer-events: none !important; }';
                        (document.head || document.documentElement).appendChild(st);
                    }
                    if (!window.__shareNowClickHooked) {
                        window.__shareNowClickHooked = true;
                        document.addEventListener('pointerdown', function() {
                            window.__shareNowLastTouchAt = Date.now();
                        }, true);
                        document.addEventListener('touchstart', function() {
                            window.__shareNowLastTouchAt = Date.now();
                        }, { capture: true, passive: true });
                        document.addEventListener('click', function(e) {
                            window.__shareNowLastTouchAt = Date.now();
                            var el = e.target;
                            while (el && el.tagName !== 'A') {
                                el = el.parentElement;
                            }
                            if (el && el.tagName === 'A') {
                                var rawHref = (el.getAttribute('href') || el.href || '').trim();
                                var href = rawHref.toLowerCase();
                                if (href.indexOf('exoclick') !== -1 || href.indexOf('tsyndicate') !== -1 || href.indexOf('trafficjunky') !== -1 ||
                                    href.indexOf('stripchat') !== -1 || href.indexOf('chaturbate') !== -1 || href.indexOf('livejasmin') !== -1 ||
                                    href.indexOf('popads') !== -1 || href.indexOf('adsterra') !== -1 || href.indexOf('realsrv') !== -1 ||
                                    href.indexOf('onelink.me') !== -1 || href.indexOf('.app.link') !== -1 || href.indexOf('.page.link') !== -1 ||
                                    href.indexOf('play.google.com/store') !== -1 || href.indexOf('snssdk') !== -1 ||
                                    href.indexOf('intent:') === 0 || href.indexOf('fb:') === 0 || href.indexOf('tiktok:') === 0 ||
                                    href.indexOf('musically:') === 0 || href.indexOf('instagram:') === 0 || href.indexOf('market:') === 0 ||
                                    href.indexOf('vnd.youtube:') === 0) {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    return false;
                                }
                                if (el.target === '_blank') {
                                    if (href.indexOf('http://') === 0 || href.indexOf('https://') === 0 || href.indexOf('/') === 0) {
                                        el.target = '_self';
                                    } else {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        return false;
                                    }
                                }
                            }
                        }, true);
                    }
                } catch(err) {}
            })();
        """

        private const val MOBILE_RESPONSIVE_JS = """
            (function() {
                try {
                    var oldStyle = document.getElementById('ai-downloader-mobile-responsive');
                    if (oldStyle && oldStyle.parentNode) {
                        oldStyle.parentNode.removeChild(oldStyle);
                    }
                    var vp = document.querySelector('meta[name="viewport"]');
                    if (!vp) {
                        vp = document.createElement('meta');
                        vp.setAttribute('name', 'viewport');
                        vp.setAttribute('content', 'width=device-width, initial-scale=1.0');
                        (document.head || document.documentElement).appendChild(vp);
                    }
                } catch(e) {}
            })();
        """

        private val MULTI_LAYER_SNIFFER_JS = """
            (function() {
                if (window.__shareNowSnifferTimer) {
                    clearInterval(window.__shareNowSnifferTimer);
                }
                window.__fbStreamCache = window.__fbStreamCache || {};
                function unescapeJsonUrl(raw) {
                    if (!raw) return '';
                    return raw.replace(/\\\//g, '/')
                              .replace(/\\u0026/gi, '&')
                              .replace(/\\u0025/gi, '%')
                              .replace(/\\u003d/gi, '=')
                              .replace(/&amp;/g, '&');
                }
                function harvestStreamsFromJsonText(txt) {
                    if (!txt || typeof txt !== 'string' || txt.length < 30) return;
                    try {
                        var idAndUrlRegex = /"(?:video_id|id)"\s*:\s*"?(\d{10,22})"?[\s\S]{1,2500}?"(?:playable_url_quality_hd|browser_native_hd_url|progressive_url|playable_url|browser_native_sd_url|hd_src|sd_src)"\s*:\s*"(https?:[^"]+)"/g;
                        var m;
                        while ((m = idAndUrlRegex.exec(txt)) !== null) {
                            var vid = m[1];
                            var sUrl = unescapeJsonUrl(m[2]);
                            if (vid && sUrl && sUrl.indexOf('http') === 0) {
                                window.__fbStreamCache[vid] = sUrl;
                            }
                        }
                        if (window.AndroidSniffer && window.AndroidSniffer.onAudioStreamHarvested && txt.indexOf('fbcdn.net') !== -1) {
                            var fbMp4Regex = /https?:(?:\\\/\\\/|\/\/)[^\s"'<>\\]+\.fbcdn\.net[^\s"'<>\\]+\.mp4[^\s"'<>\\]*/g;
                            var fm;
                            while ((fm = fbMp4Regex.exec(txt)) !== null) {
                                var rawFbUrl = unescapeJsonUrl(fm[0]);
                                if (rawFbUrl.indexOf('efg=') !== -1) {
                                    window.AndroidSniffer.onAudioStreamHarvested(rawFbUrl);
                                }
                            }
                        }
                    } catch(e) {}
                }
                if (!window.__xhrFetchHooked) {
                    window.__xhrFetchHooked = true;
                    try {
                        var origSend = XMLHttpRequest.prototype.send;
                        XMLHttpRequest.prototype.send = function() {
                            this.addEventListener('load', function() {
                                try {
                                    if (this.responseType === '' || this.responseType === 'text') {
                                        var rt = this.responseText;
                                        if (rt && (rt.indexOf('playable_url') !== -1 || rt.indexOf('progressive_url') !== -1 || rt.indexOf('browser_native_') !== -1 || rt.indexOf('hd_src') !== -1)) {
                                            harvestStreamsFromJsonText(rt);
                                        }
                                    }
                                } catch(e) {}
                            });
                            return origSend.apply(this, arguments);
                        };
                    } catch(e) {}
                }
                function isValidStream(u) {
                    if (!u || typeof u !== 'string' || !u.startsWith('http') || u.indexOf('googlevideo.com') !== -1) return false;
                    var low = u.toLowerCase();
                    if (low.indexOf('thumb-') !== -1 || low.indexOf('flixcdn.com') !== -1 ||
                        low.indexOf('preview') !== -1 || low.indexOf('/thumbs/') !== -1 ||
                        (low.indexOf('cdnsolutions.media') !== -1 && low.indexOf('thumb') !== -1)) {
                        return false;
                    }
                    return (
                        u.indexOf('.mp4') !== -1 ||
                        u.indexOf('.m3u8') !== -1 ||
                        u.indexOf('.webm') !== -1 ||
                        u.indexOf('/video/tos/') !== -1 ||
                        u.indexOf('mime_type=video_mp4') !== -1 ||
                        u.indexOf('/aweme/v1/play') !== -1 ||
                        u.indexOf('tiktokcdn') !== -1 ||
                        u.indexOf('tiktokv.com') !== -1 ||
                        u.indexOf('byteoversea.com') !== -1 ||
                        u.indexOf('ibytedtos.com') !== -1 ||
                        u.indexOf('cdninstagram.com') !== -1 ||
                        u.indexOf('fbcdn.net') !== -1 ||
                        u.indexOf('video.twimg.com') !== -1 ||
                        u.indexOf('v.redd.it') !== -1 ||
                        u.indexOf('vimeocdn.com') !== -1 ||
                        u.indexOf('dmcdn.net') !== -1 ||
                        u.indexOf('xhcdn.com') !== -1 ||
                        u.indexOf('growcdnssedge.com') !== -1 ||
                        u.indexOf('xvideos-cdn.com') !== -1 ||
                        u.indexOf('phncdn.com') !== -1
                    );
                }
                function findVisibleLinkMatching(regex) {
                    var anchors = document.getElementsByTagName('a');
                    var bestMatch = '';
                    var bestDist = 999999;
                    var viewportCenter = window.innerHeight / 2;
                    for (var i = 0; i < anchors.length; i++) {
                        var a = anchors[i];
                        var href = a.href || '';
                        if (!href) continue;
                        var m = href.match(regex);
                        if (m && m[0]) {
                            var rect = a.getBoundingClientRect();
                            if (rect.height > 0 && rect.bottom > 40 && rect.top < window.innerHeight - 40) {
                                var center = (rect.top + rect.bottom) / 2;
                                var dist = Math.abs(center - viewportCenter);
                                if (dist < bestDist) {
                                    bestDist = dist;
                                    bestMatch = m[0];
                                }
                            }
                        }
                    }
                    return bestMatch;
                }
                function findActiveViewportVideo() {
                    var vids = document.querySelectorAll('video');
                    var bestVid = null;
                    var bestScore = -1;
                    var vpCenter = window.innerHeight / 2;
                    for (var i = 0; i < vids.length; i++) {
                        var v = vids[i];
                        var rect = v.getBoundingClientRect();
                        if (rect.height < 40 || rect.bottom <= 0 || rect.top >= window.innerHeight) continue;
                        var isPlaying = (!v.paused && !v.ended && v.readyState >= 2);
                        var center = (rect.top + rect.bottom) / 2;
                        var distScore = Math.max(0, 1000 - Math.abs(center - vpCenter));
                        var score = (isPlaying ? 5000 : 0) + (v.currentTime > 0 ? 1000 : 0) + distScore;
                        if (score > bestScore) {
                            bestScore = score;
                            bestVid = v;
                        }
                    }
                    return bestVid;
                }
                function extractVideoDetailsFromElement(videoEl) {
                    var result = { streamUrl: '', platformUrl: '', videoId: '', title: '' };
                    if (!videoEl) return result;
                    var rawSrc = videoEl.currentSrc || videoEl.src || videoEl.getAttribute('src') || '';
                    if (isValidStream(rawSrc)) {
                        result.streamUrl = rawSrc;
                    }
                    var host = (window.location.hostname || '').toLowerCase();
                    var node = videoEl;
                    for (var depth = 0; depth < 12 && node && node !== document.body; depth++) {
                        try {
                            var ds = node.getAttribute && node.getAttribute('data-store');
                            if (ds && ds.length > 5) {
                                var dsObj = JSON.parse(ds);
                                var dsSrc = dsObj.hd_src || dsObj.src || dsObj.sd_src || dsObj.playable_url || '';
                                if (!result.streamUrl && isValidStream(dsSrc)) {
                                    result.streamUrl = dsSrc;
                                }
                                var dsVid = dsObj.videoID || dsObj.video_id || '';
                                if (!result.videoId && dsVid) {
                                    result.videoId = String(dsVid);
                                }
                            }
                        } catch(e) {}
                        try {
                            var keys = Object.keys(node);
                            for (var k = 0; k < keys.length; k++) {
                                var key = keys[k];
                                if (key.indexOf('__reactFiber$') === 0 || key.indexOf('__reactProps$') === 0) {
                                    var fiber = node[key];
                                    var curr = fiber;
                                    for (var fDepth = 0; fDepth < 5 && curr; fDepth++) {
                                        var props = curr.memoizedProps || curr.pendingProps || curr;
                                        if (props && typeof props === 'object') {
                                            var vData = props.videoData || props.video || props;
                                            var candUrl = vData.playable_url_quality_hd || vData.browser_native_hd_url ||
                                                vData.progressive_url || vData.playable_url || vData.browser_native_sd_url ||
                                                vData.hd_src || vData.sd_src || vData.videoUrl || '';
                                            if (!result.streamUrl && candUrl && typeof candUrl === 'string') {
                                                var cand = unescapeJsonUrl(candUrl);
                                                if (isValidStream(cand)) result.streamUrl = cand;
                                            }
                                            var candVid = vData.video_id || vData.videoID || vData.xpv_asset_id || props.video_id || props.videoID || '';
                                            if (!result.videoId && candVid) {
                                                result.videoId = String(candVid);
                                            }
                                        }
                                        curr = curr.return;
                                    }
                                }
                            }
                        } catch(e) {}
                        try {
                            var nRect = node.getBoundingClientRect ? node.getBoundingClientRect() : null;
                            if (nRect && nRect.height > 50 && nRect.height <= window.innerHeight * 1.35) {
                                if (!result.platformUrl) {
                                    var links = node.getElementsByTagName('a');
                                    for (var li = 0; li < links.length; li++) {
                                        var lh = links[li].href || '';
                                        if (!lh) continue;
                                        if ((host.indexOf('facebook.com') !== -1 || host.indexOf('fb.watch') !== -1) &&
                                            (/\/(?:reel|videos)\/\d+/.test(lh) || lh.indexOf('watch/?v=') !== -1)) {
                                            result.platformUrl = lh.split('&')[0];
                                            break;
                                        }
                                        if (host.indexOf('tiktok.com') !== -1 && /\/@[a-zA-Z0-9._-]+\/video\/\d{15,22}/.test(lh)) {
                                            result.platformUrl = lh.split('?')[0];
                                            break;
                                        }
                                        if (host.indexOf('instagram.com') !== -1 && /\/(?:reel|reels|p)\/[A-Za-z0-9_-]+/.test(lh)) {
                                            result.platformUrl = lh.split('?')[0];
                                            break;
                                        }
                                        if ((host.indexOf('x.com') !== -1 || host.indexOf('twitter.com') !== -1) && /\/status\/\d{15,22}/.test(lh)) {
                                            result.platformUrl = lh.split('?')[0];
                                            break;
                                        }
                                    }
                                }
                                if (!result.title) {
                                    var aria = node.getAttribute('aria-label') || '';
                                    if (aria && aria.length > 3 && aria.length < 120) {
                                        result.title = aria.trim();
                                    }
                                }
                            }
                        } catch(e) {}
                        node = node.parentElement;
                    }
                    if (!result.streamUrl && result.videoId && window.__fbStreamCache[result.videoId]) {
                        result.streamUrl = window.__fbStreamCache[result.videoId];
                    }
                    if (!result.platformUrl && result.videoId && (host.indexOf('facebook.com') !== -1 || host.indexOf('fb.watch') !== -1)) {
                        result.platformUrl = 'https://www.facebook.com/watch/?v=' + result.videoId;
                    }
                    return result;
                }
                function detectPlatformVideoUrl(activeVid) {
                    var host = (window.location.hostname || '').toLowerCase();
                    var href = window.location.href || '';
                    var vDetails = extractVideoDetailsFromElement(activeVid || findActiveViewportVideo());
                    if (vDetails.platformUrl) {
                        return vDetails.platformUrl;
                    }

                    // 1. YouTube (watch?v=..., /shorts/..., or inline player)
                    if (host.indexOf('youtube.com') !== -1 || host.indexOf('youtu.be') !== -1) {
                        if (href.indexOf('watch?v=') !== -1 || href.indexOf('/shorts/') !== -1) {
                            return href;
                        }
                        try {
                            if (window.ytInitialPlayerResponse && window.ytInitialPlayerResponse.videoDetails && window.ytInitialPlayerResponse.videoDetails.videoId) {
                                var vids = document.querySelectorAll('video');
                                for (var v = 0; v < vids.length; v++) {
                                    if (!vids[v].paused && vids[v].currentTime > 0) {
                                        return 'https://www.youtube.com/watch?v=' + window.ytInitialPlayerResponse.videoDetails.videoId;
                                    }
                                }
                            }
                        } catch(e) {}
                        return '';
                    }

                    // 2. TikTok: ưu tiên link video đang hiển thị trên viewport trước href
                    if (host.indexOf('tiktok.com') !== -1) {
                        var tkLink = findVisibleLinkMatching(/https?:\/\/(?:www\.|m\.)?tiktok\.com\/@[a-zA-Z0-9._-]+\/video\/\d{15,22}/);
                        if (tkLink) return tkLink;
                        if (/\/@[a-zA-Z0-9._-]+\/video\/\d{15,22}/.test(href)) {
                            return href.split('?')[0];
                        }
                        var uni = document.getElementById('__UNIVERSAL_DATA_FOR_REHYDRATION__') || document.getElementById('SIGI_STATE');
                        if (uni && uni.textContent) {
                            var mId = uni.textContent.match(/"id"\s*:\s*"(7\d{18})"/);
                            if (mId && mId[1]) {
                                return 'https://www.tiktok.com/@tiktok/video/' + mId[1];
                            }
                        }
                        return '';
                    }

                    // 3. Facebook: ưu tiên link của card video đang hiển thị trong viewport trước href cố định
                    if (host.indexOf('facebook.com') !== -1 || host.indexOf('fb.watch') !== -1) {
                        var fbLink = findVisibleLinkMatching(/https?:\/\/(?:www\.|m\.)?facebook\.com\/(?:reel\/\d+|watch\/\?v=\d+|[^\/]+\/videos\/\d+)/);
                        if (fbLink) return fbLink.split('&')[0];
                        if (/\/(?:reel|videos)\/\d+/.test(href) || href.indexOf('watch/?v=') !== -1 || href.indexOf('/share/v/') !== -1 || href.indexOf('/share/r/') !== -1) {
                            return href;
                        }
                        return '';
                    }

                    // 4. Instagram
                    if (host.indexOf('instagram.com') !== -1) {
                        var igLink = findVisibleLinkMatching(/https?:\/\/(?:www\.)?instagram\.com\/(?:reel|reels|p)\/[A-Za-z0-9_-]+/);
                        if (igLink) return igLink;
                        if (/\/(?:reel|reels|p|tv)\/[A-Za-z0-9_-]+/.test(href)) {
                            return href.split('?')[0];
                        }
                        return '';
                    }

                    // 5. X / Twitter
                    if (host.indexOf('x.com') !== -1 || host.indexOf('twitter.com') !== -1) {
                        var xLink = findVisibleLinkMatching(/https?:\/\/(?:www\.)?(?:x|twitter)\.com\/[A-Za-z0-9_]+\/status\/\d{15,22}/);
                        if (xLink) return xLink;
                        if (/\/status\/\d{15,22}/.test(href)) {
                            return href.split('?')[0];
                        }
                        return '';
                    }

                    // 6. Vimeo
                    if (host.indexOf('vimeo.com') !== -1) {
                        var vmLink = findVisibleLinkMatching(/https?:\/\/(?:www\.)?vimeo\.com\/(?:channels\/[^\/]+\/)?\d{6,12}/);
                        if (vmLink) return vmLink;
                        if (/vimeo\.com\/(?:.*\/)?\d{6,12}/.test(href)) {
                            return href.split('?')[0];
                        }
                        return '';
                    }

                    // 7. Dailymotion
                    if (host.indexOf('dailymotion.com') !== -1 || host.indexOf('dai.ly') !== -1) {
                        var dmLink = findVisibleLinkMatching(/https?:\/\/(?:www\.)?dailymotion\.com\/video\/[a-zA-Z0-9]{5,10}/);
                        if (dmLink) return dmLink;
                        if (/\/video\/[a-zA-Z0-9]{5,10}/.test(href)) {
                            return href.split('?')[0];
                        }
                        return '';
                    }

                    return '';
                }
                window.__reportedStreams = window.__reportedStreams || {};
                function reportStreamCandidate(u, t) {
                    if (!u || !isValidStream(u)) return;
                    if (!window.__reportedStreams[u]) {
                        window.__reportedStreams[u] = true;
                        if (window.AndroidSniffer && window.AndroidSniffer.onVideoFound) {
                            window.AndroidSniffer.onVideoFound(u, t || document.title);
                        }
                    }
                }
                function reportPlayedVideoElement(videoEl, forceNotify) {
                    if (!videoEl) return;
                    var details = extractVideoDetailsFromElement(videoEl);
                    var pUrl = details.platformUrl || detectPlatformVideoUrl(videoEl);
                    var sUrl = details.streamUrl || '';
                    var vTitle = details.title || document.title || '';
                    var sig = (sUrl || '') + '|' + (pUrl || '') + '|' + (details.videoId || '');
                    if (!sig || sig === '||') return;
                    var now = Date.now();
                    if (!forceNotify && window.__lastPlayedVideoSig === sig && (now - (window.__lastPlayedVideoAt || 0) < 2200)) {
                        return;
                    }
                    window.__lastPlayedVideoSig = sig;
                    window.__lastPlayedVideoAt = now;
                    if (window.AndroidSniffer && window.AndroidSniffer.onVideoPlayed) {
                        window.AndroidSniffer.onVideoPlayed(sUrl, vTitle, pUrl);
                    } else {
                        if (sUrl) reportStreamCandidate(sUrl, vTitle);
                        if (pUrl && window.AndroidSniffer && window.AndroidSniffer.onPlatformVideoFound) {
                            window.AndroidSniffer.onPlatformVideoFound(pUrl, vTitle);
                        }
                    }
                }
                window.__shareNowSnifferTimer = setInterval(function() {
                    try {
                        var title = document.title || '';

                        // A. Quét các thẻ <script> (mỗi thẻ chỉ quét 1 lần duy nhất để không làm khựng UI khi chuyển tab)
                        var scripts = document.getElementsByTagName('script');
                        for (var i = 0; i < scripts.length; i++) {
                            if (scripts[i].__shareNowParsed) continue;
                            scripts[i].__shareNowParsed = true;
                            var scText = scripts[i].textContent || scripts[i].innerText || '';
                            if (scText.length <= 40) continue;
                            if (scText.indexOf('playable_url') !== -1 || scText.indexOf('progressive_url') !== -1 || scText.indexOf('browser_native_') !== -1) {
                                harvestStreamsFromJsonText(scText);
                            }
                            if (scText.indexOf('setVideoHLS') !== -1 || scText.indexOf('setVideoUrlHigh') !== -1 || scText.indexOf('video_url_high') !== -1) {
                                var mHls = scText.match(/(?:setVideoHLS|video_hls)\s*[\(=]\s*['"]([^'"]+)['"]/);
                                var mHigh = scText.match(/(?:setVideoUrlHigh|video_url_high)\s*[\(=]\s*['"]([^'"]+)['"]/);
                                var mLow = scText.match(/(?:setVideoUrlLow|video_url_low)\s*[\(=]\s*['"]([^'"]+)['"]/);
                                var mTitle = scText.match(/(?:setVideoTitle|video_title)\s*[\(=]\s*['"]([^'"]+)['"]/);
                                if (mTitle && mTitle[1]) title = mTitle[1];
                                if (mHigh && mHigh[1]) reportStreamCandidate(mHigh[1], title);
                                if (mHls && mHls[1]) reportStreamCandidate(mHls[1], title);
                                if (mLow && mLow[1]) reportStreamCandidate(mLow[1], title);
                            }
                            if (scText.indexOf('mediaDefinitions') !== -1) {
                                var mDef = scText.match(/mediaDefinitions\s*[:=]\s*(\[\s*\{.*?\}\s*\])/);
                                if (mDef && mDef[1]) {
                                    try {
                                        var defs = JSON.parse(mDef[1]);
                                        for (var d = 0; d < defs.length; d++) {
                                            if (defs[d].videoUrl && defs[d].videoUrl.startsWith('http')) {
                                                reportStreamCandidate(defs[d].videoUrl, title);
                                            }
                                        }
                                    } catch(e) {}
                                }
                            }
                        }

                        // B. Quét đối tượng toàn cục xplayer & initials
                        if (window.xplayer && window.xplayer.core && window.xplayer.core.options) {
                            var opts = window.xplayer.core.options;
                            if (opts.videoInfo && opts.videoInfo.title) {
                                title = opts.videoInfo.title;
                            }
                            var s = opts.sources;
                            if (s) {
                                if (s.hls && s.hls.h264 && s.hls.h264.url) reportStreamCandidate(s.hls.h264.url, title);
                                if (s.hls && s.hls.av1 && s.hls.av1.url) reportStreamCandidate(s.hls.av1.url, title);
                                if (s.standard && s.standard.h264 && s.standard.h264.length > 0) {
                                    for (var k = s.standard.h264.length - 1; k >= 0; k--) {
                                        if (s.standard.h264[k] && s.standard.h264[k].url) {
                                            reportStreamCandidate(s.standard.h264[k].url, title);
                                        }
                                    }
                                }
                            }
                        }
                        if (window.initials) {
                            var model = window.initials.videoModel || window.initials.xplayerSettings;
                            if (model && model.sources) {
                                var ms = model.sources;
                                if (ms.hls) {
                                    if (typeof ms.hls === 'string' && ms.hls.startsWith('http')) reportStreamCandidate(ms.hls, title);
                                    else if (ms.hls.url && ms.hls.url.startsWith('http')) reportStreamCandidate(ms.hls.url, title);
                                    else if (ms.hls.h264 && ms.hls.h264.url) reportStreamCandidate(ms.hls.h264.url, title);
                                    else if (ms.hls.av1 && ms.hls.av1.url) reportStreamCandidate(ms.hls.av1.url, title);
                                }
                                if (ms.standard) {
                                    var stdList = ms.standard.h264 || ms.standard.mp4 || ms.standard;
                                    if (Array.isArray(stdList)) {
                                        for (var si = 0; si < stdList.length; si++) {
                                            var uStd = stdList[si] && (stdList[si].url || stdList[si].fallback);
                                            if (uStd && typeof uStd === 'string') reportStreamCandidate(uStd, title);
                                        }
                                    }
                                }
                            }
                        }

                        // C. Quét tất cả thẻ <video> và <source> & bắt ngay video đang play trong viewport
                        var vids = document.querySelectorAll('video');
                        for (var j = 0; j < vids.length; j++) {
                            var v = vids[j];
                            if (!v.__shareNowHooked) {
                                v.__shareNowHooked = true;
                                v.addEventListener('loadedmetadata', function() {
                                    var cs = this.currentSrc || this.src || '';
                                    reportStreamCandidate(cs, title || document.title);
                                });
                                v.addEventListener('play', function() {
                                    reportPlayedVideoElement(this, true);
                                });
                                v.addEventListener('playing', function() {
                                    reportPlayedVideoElement(this, true);
                                });
                            }
                            var vSrc = v.currentSrc || v.src || v.getAttribute('src') || '';
                            if (vSrc) reportStreamCandidate(vSrc, title);
                            var srcEls = v.querySelectorAll('source');
                            for (var sIdx = 0; sIdx < srcEls.length; sIdx++) {
                                var sUrl = srcEls[sIdx].src || srcEls[sIdx].getAttribute('src') || '';
                                if (sUrl) reportStreamCandidate(sUrl, title);
                            }
                        }

                        var activeVid = findActiveViewportVideo();
                        if (activeVid && !activeVid.paused && activeVid.currentTime > 0) {
                            reportPlayedVideoElement(activeVid, false);
                        }

                        // D. Quét thẻ meta og:video
                        var metaVid = document.querySelector('meta[property="og:video"]') ||
                                      document.querySelector('meta[property="og:video:secure_url"]');
                        if (metaVid && metaVid.content && isValidStream(metaVid.content)) {
                            var mv = metaVid.content;
                            if (mv.indexOf('youtube.com/embed/') === -1 && mv.indexOf('/embed/') === -1) {
                                reportStreamCandidate(mv, title);
                            }
                        }

                        // E. Hook prototype play & event listeners để bắt luồng ngay khi video phát
                        if (!window.__videoPlayHooked) {
                            window.__videoPlayHooked = true;
                            var origPlay = HTMLMediaElement.prototype.play;
                            HTMLMediaElement.prototype.play = function() {
                                try {
                                    reportPlayedVideoElement(this, true);
                                } catch(e) {}
                                return origPlay.apply(this, arguments);
                            };
                        }

                        // F. Báo cáo Platform Video URL khi URL trang hoặc phần tử video đang phát thay đổi
                        var currHref = window.location.href || '';
                        if (activeVid !== window.__lastPolledActiveVid || currHref !== window.__lastPolledHref) {
                            window.__lastPolledActiveVid = activeVid;
                            window.__lastPolledHref = currHref;
                            var platformVideoUrl = detectPlatformVideoUrl(activeVid);
                            if (platformVideoUrl && platformVideoUrl !== window.__lastReportedPlatformUrl) {
                                window.__lastReportedPlatformUrl = platformVideoUrl;
                                if (window.AndroidSniffer && window.AndroidSniffer.onPlatformVideoFound) {
                                    window.AndroidSniffer.onPlatformVideoFound(platformVideoUrl, title || document.title);
                                }
                            }
                        }
                    } catch(e) {}
                }, 1500);
            })();
        """.trimIndent()

        private val FACEBOOK_PAGE_REELS_AUTOSCROLL_JS = """
            (function() {
                if (window.__shareNowPageReelsHarvesterActive) return;
                window.__shareNowPageReelsHarvesterActive = true;
                var reportedReelIds = {};
                var totalReported = 0;

                function unescapeFb(raw) {
                    if (!raw) return '';
                    return raw.replace(/\\\//g, '/')
                              .replace(/\\u0026/gi, '&')
                              .replace(/\\u0025/gi, '%')
                              .replace(/\\u003d/gi, '=')
                              .replace(/&amp;/g, '&');
                }

                function harvestPageReelsPayload(txt) {
                    if (!txt || typeof txt !== 'string' || txt.length < 40) return;
                    try {
                        var re = /"(?:video_id|id)"\s*:\s*"(\d{8,25})"[\s\S]{1,4000}?"(?:browser_native_sd_url|playable_url)"\s*:\s*"(https?:[^"]+)"(?:[\s\S]{1,1800}?"(?:browser_native_hd_url|playable_url_quality_hd)"\s*:\s*"(https?:[^"]+)")?/g;
                        var m;
                        while ((m = re.exec(txt)) !== null) {
                            var rid = m[1];
                            var sd = unescapeFb(m[2] || '');
                            var hd = unescapeFb(m[3] || '');
                            var best = hd || sd;
                            if (rid && best && !reportedReelIds[rid]) {
                                reportedReelIds[rid] = true;
                                totalReported++;
                                if (window.AndroidSniffer && window.AndroidSniffer.onBatchReelDiscovered) {
                                    window.AndroidSniffer.onBatchReelDiscovered(rid, best, 'Reel_' + rid);
                                }
                            }
                        }
                        // Instagram video_versions extraction
                        var igRe = /"(?:shortcode|code)"\s*:\s*"([A-Za-z0-9_-]{8,15})"[\s\S]{1,3500}?"video_versions"\s*:\s*\[\s*\{[^}]*?"url"\s*:\s*"(https?:[^"]+)"/g;
                        var igm;
                        while ((igm = igRe.exec(txt)) !== null) {
                            var igCode = igm[1];
                            var igUrl = unescapeFb(igm[2] || '');
                            if (igCode && igUrl && !reportedReelIds[igCode]) {
                                reportedReelIds[igCode] = true;
                                totalReported++;
                                if (window.AndroidSniffer && window.AndroidSniffer.onBatchReelDiscovered) {
                                    window.AndroidSniffer.onBatchReelDiscovered(igCode, igUrl, 'IG_Reel_' + igCode);
                                }
                            }
                        }
                    } catch(e) {}
                }

                function harvestDomChannelLinks() {
                    try {
                        var host = (window.location.hostname || '').toLowerCase();
                        var anchors = document.querySelectorAll('a[href]');
                        for (var i = 0; i < anchors.length; i++) {
                            var href = anchors[i].getAttribute('href') || '';
                            if (host.indexOf('instagram.com') !== -1) {
                                var mIg = href.match(/\/(?:reel|reels|p)\/([A-Za-z0-9_-]{8,15})/);
                                if (mIg && mIg[1] && !reportedReelIds[mIg[1]]) {
                                    reportedReelIds[mIg[1]] = true;
                                    totalReported++;
                                    if (window.AndroidSniffer && window.AndroidSniffer.onPlatformVideoFound) {
                                        window.AndroidSniffer.onPlatformVideoFound('https://www.instagram.com/reel/' + mIg[1] + '/', 'Instagram Reel #' + mIg[1]);
                                    }
                                }
                            } else if (host.indexOf('youtube.com') !== -1) {
                                var mYt = href.match(/\/shorts\/([A-Za-z0-9_-]{11})/) || href.match(/[?&]v=([A-Za-z0-9_-]{11})/);
                                if (mYt && mYt[1] && !reportedReelIds[mYt[1]]) {
                                    reportedReelIds[mYt[1]] = true;
                                    totalReported++;
                                    var ytTitle = (anchors[i].getAttribute('title') || anchors[i].textContent || '').trim() || ('YouTube Short #' + mYt[1]);
                                    if (window.AndroidSniffer && window.AndroidSniffer.onPlatformVideoFound) {
                                        window.AndroidSniffer.onPlatformVideoFound('https://www.youtube.com/shorts/' + mYt[1], ytTitle);
                                    }
                                }
                            }
                        }
                    } catch(e) {}
                }

                // Hook fetch & XHR for GraphQL pagination
                try {
                    var origFetch = window.fetch;
                    if (origFetch) {
                        window.fetch = function() {
                            return origFetch.apply(this, arguments).then(function(resp) {
                                try {
                                    var clone = resp.clone();
                                    clone.text().then(function(t) { harvestPageReelsPayload(t); }).catch(function(){});
                                } catch(e) {}
                                return resp;
                            });
                        };
                    }
                    var origXhrSend = XMLHttpRequest.prototype.send;
                    XMLHttpRequest.prototype.send = function() {
                        this.addEventListener('load', function() {
                            try {
                                if (this.responseType === '' || this.responseType === 'text') {
                                    harvestPageReelsPayload(this.responseText);
                                }
                            } catch(e) {}
                        });
                        return origXhrSend.apply(this, arguments);
                    };
                } catch(e) {}

                // Parse initial SSR scripts & DOM links
                var scripts = document.getElementsByTagName('script');
                for (var i = 0; i < scripts.length; i++) {
                    harvestPageReelsPayload(scripts[i].textContent || '');
                }
                harvestDomChannelLinks();

                // Dismiss public overlay close button once if present
                try {
                    var closeBtn = document.querySelector('[role="dialog"] [aria-label="Close"], [role="dialog"] [aria-label="Đóng"]');
                    if (closeBtn) closeBtn.click();
                } catch(e) {}

                var idleTicks = 0;
                var lastCount = totalReported;
                var scrollTimer = setInterval(function() {
                    try {
                        var closeDialog = document.querySelector('[role="dialog"] [aria-label="Close"], [role="dialog"] [aria-label="Đóng"]');
                        if (closeDialog) closeDialog.click();
                    } catch(e) {}
                    harvestDomChannelLinks();
                    window.scrollBy(0, Math.max(1200, window.innerHeight * 1.6));
                    if (totalReported > lastCount) {
                        lastCount = totalReported;
                        idleTicks = 0;
                    } else {
                        idleTicks++;
                    }
                    if (idleTicks >= 5) {
                        clearInterval(scrollTimer);
                        window.__shareNowPageReelsHarvesterActive = false;
                        if (window.AndroidSniffer && window.AndroidSniffer.onPageReelsHarvestComplete) {
                            window.AndroidSniffer.onPageReelsHarvestComplete(totalReported);
                        }
                    }
                }, 1700);
            })();
        """.trimIndent()
    }
}
