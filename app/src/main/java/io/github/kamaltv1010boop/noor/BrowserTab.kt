package io.github.kamaltv1010boop.noor

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import java.io.ByteArrayInputStream

/// What is saved of a tab between launches.
data class SavedTab(val url: String, val title: String)

/// One tab: its WebView, created when first needed, and what the views show
/// of it (address, title, progress…). BrowserModel owns the tabs. A private
/// tab keeps its data in a WebView profile of its own, emptied when the last
/// one closes, and isn't remembered.
class BrowserTab(
    private val model: BrowserModel,
    val isPrivate: Boolean,
    /// The profile whose data and fingerprints a normal tab uses.
    val profileId: String,
    restored: SavedTab? = null,
    /// The tab whose page opened this one, so closing it goes back there.
    var opener: BrowserTab? = null,
) {
    val id = nextId++

    var url by mutableStateOf<String?>(null)
        private set
    var title by mutableStateOf(restored?.title ?: "")
        private set
    var progress by mutableIntStateOf(0)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set
    /// Trackers blocked on the current page.
    var trackersBlocked by mutableIntStateOf(0)
        private set
    /// Set when a page fails to load; the view shows it over the page.
    var loadError by mutableStateOf<String?>(null)
        private set
    /// The address that failed, so "Try Again" retries it.
    var failedUrl by mutableStateOf<String?>(null)
        private set
    /// A restored tab loads this address when it is shown.
    var pendingUrl by mutableStateOf(restored?.url)
        private set
    // These two change through setDesktop(…) and setZoom(…) below, so
    // their own setters take other JVM names.
    var isDesktop by mutableStateOf(false)
        @JvmName("putDesktop") private set
    /// The text size, 100 being the page's own.
    var zoom by mutableIntStateOf(100)
        @JvmName("putZoom") private set
    var shieldsUp by mutableStateOf(true)
        private set
    /// A picture of the page for the tab overview.
    var thumbnail by mutableStateOf<ImageBitmap?>(null)
        private set
    /// The match shown and the matches of a search in the page.
    var findMatches by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    /// The address the page gives itself (its canonical link), when on the
    /// same site: what a bookmark or a pin of it keeps.
    var canonicalUrl: String? = null
        private set

    /// The page's address, for the requests checked on WebView's own threads.
    @Volatile private var pageHost: String? = null
    @Volatile private var pageShieldsUp = true

    /// An https:// load upgraded from http://, and the http:// address, so
    /// it can go back to http:// if the site doesn't support https.
    private var upgrade: Pair<String, String>? = null
    /// The last address cleaned of tracking codes, so a site that redirects
    /// back to the tracking address can't cause an endless loop.
    private var lastCleaned: String? = null
    private val scripts = mutableListOf<ScriptHandler>()
    private var appliedSettings = ""
    private var view by mutableStateOf<WebView?>(null)

    /// The WebView profile that keeps this tab's cookies and site data.
    val webViewProfile: String
        get() = if (isPrivate) Profiles.PRIVATE_WEBVIEW_PROFILE else Profiles.webViewProfileName(profileId)

    val webView: WebView
        get() = view ?: createWebView().also { view = it }

    val hasWebView: Boolean get() = view != null

    /// The WebView the screen shows, once made (see BrowserModel.select).
    val currentView: WebView? get() = view

    /// A tab with nothing loaded shows the start page.
    val isBlank: Boolean get() = url == null && pendingUrl == null && loadError == null

    /// What the address bar shows: the failed address while the error is up.
    val displayUrl: String? get() = if (loadError != null) failedUrl ?: url else url ?: pendingUrl

    val displayTitle: String
        get() = title.ifEmpty { Urls.site(displayUrl) ?: "" }

    /// The address and title to remember, or null for an empty tab.
    val saved: SavedTab?
        get() {
            val address = pendingUrl ?: url ?: return null
            return if (Urls.isWebPage(address)) SavedTab(address, displayTitle) else null
        }

    /// The address a bookmark or a pin keeps: the page's canonical address,
    /// or the address without tracking codes.
    val keptUrl: String?
        get() {
            val address = url ?: return null
            return canonicalUrl ?: LinkCleaner.clean(address) ?: address
        }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val webView = WebView(model.activity)
        if (Features.multiProfile) {
            try {
                WebViewCompat.setProfile(webView, webViewProfile)
            } catch (e: Exception) {
                // The default profile, then.
            }
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        // Chrome sends no X-Requested-With header naming the app.
        if (Features.requestedWithAllowList) {
            try {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(webView.settings, emptySet())
            } catch (e: Exception) {
                // Left as the WebView has it.
            }
        }
        webView.webViewClient = Client()
        webView.webChromeClient = ChromeClient()
        webView.setDownloadListener { address, userAgent, contentDisposition, mimeType, _ ->
            model.download(this, address, userAgent, contentDisposition, mimeType)
        }
        webView.setOnLongClickListener { onLongPress(webView) }
        webView.setFindListener { active, count, done ->
            if (done) findMatches = if (count > 0) Pair(active + 1, count) else Pair(0, 0)
        }
        view = webView
        applySettings(pendingUrl ?: url, force = true)
        return webView
    }

    /// Installs what this tab's pages get: the protection (which leaves
    /// alone a site whose shields are down, and gives a sign-in site the
    /// phone's own screen and hardware), night mode, Chrome's user agent and
    /// brands, and the text size. Pages pick up a change when they next load.
    fun applySettings(address: String? = url ?: pendingUrl, force: Boolean = false) {
        shieldsUp = model.shields.areUp(address)
        val webView = view ?: return
        val settings = "${model.settingsSignature(profileId)}|$isDesktop"
        if (!force && settings == appliedSettings) return
        appliedSettings = settings
        scripts.forEach { it.remove() }
        scripts.clear()
        if (Features.documentStart) {
            model.protectionScript(profileId, isDesktop)?.let {
                scripts.add(WebViewCompat.addDocumentStartJavaScript(webView, it, setOf("*")))
            }
            if (model.nightMode) {
                scripts.add(WebViewCompat.addDocumentStartJavaScript(webView, PageScript.NIGHT_MODE, setOf("*")))
            }
        }
        webView.settings.userAgentString = model.brands.userAgent(isDesktop)
        model.applyUserAgentMetadata(webView.settings, isDesktop)
        webView.settings.textZoom = zoom
    }

    // MARK: - Actions

    /// Loads `address` (without its tracking codes, over https:// if the
    /// site has it).
    fun load(address: String) {
        clearError()
        var target = address
        if (model.cleanLinks) {
            LinkCleaner.clean(target)?.let { target = it }
        }
        if (model.httpsUpgrade && model.shields.areUp(target)) {
            val host = Urls.host(target)
            val secure = HttpsUpgrade.upgraded(target)
            if (host != null && secure != null && host !in model.httpOnlyHosts) {
                upgrade = Pair(secure, target)
                target = secure
            }
        }
        pendingUrl = null
        url = target
        pageHost = Urls.host(target)
        pageShieldsUp = model.shields.areUp(target)
        applySettings(target)
        webView.loadUrl(target)
    }

    /// Loads the restored page, if there is one.
    fun loadIfNeeded() {
        pendingUrl?.let { load(it) }
    }

    fun goBack() {
        when {
            loadError != null -> {
                clearError()
                if (url == null) url = view?.url
            }
            view?.canGoBack() == true -> view?.goBack()
        }
    }

    fun goForward() {
        view?.goForward()
    }

    fun reload() {
        val failed = failedUrl
        when {
            loadError != null && failed != null -> load(failed)
            pendingUrl != null -> loadIfNeeded()
            view?.url != null -> {
                applySettings()
                view?.reload()
            }
        }
    }

    fun stopLoading() {
        view?.stopLoading()
    }

    fun setDesktop(isOn: Boolean) {
        if (isOn == isDesktop) return
        isDesktop = isOn
        applySettings(force = true)
        reload()
    }

    fun setZoom(percent: Int) {
        zoom = percent.coerceIn(50, 300)
        view?.settings?.textZoom = zoom
    }

    fun find(text: String) {
        if (text.isEmpty()) {
            clearFind()
        } else {
            view?.findAllAsync(text)
        }
    }

    fun findNext(forward: Boolean) {
        view?.findNext(forward)
    }

    fun clearFind() {
        view?.clearMatches()
        findMatches = null
    }

    /// Updates `thumbnail` with a small picture of the page.
    fun takeThumbnail() {
        val webView = view ?: return
        if (url == null || webView.width <= 0 || webView.height <= 0) return
        try {
            val scale = 0.4f
            val bitmap = Bitmap.createBitmap((webView.width * scale).toInt().coerceAtLeast(1), (webView.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            canvas.translate(-webView.scrollX.toFloat(), -webView.scrollY.toFloat())
            webView.draw(canvas)
            thumbnail = bitmap.asImageBitmap()
        } catch (e: Throwable) {
            // No picture, then.
        }
    }

    fun onPause() {
        view?.onPause()
    }

    fun onResume() {
        view?.onResume()
    }

    /// Stops everything before the tab goes away.
    fun destroy() {
        val webView = view ?: return
        view = null
        scripts.forEach { it.remove() }
        scripts.clear()
        webView.stopLoading()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.webChromeClient = null
        webView.destroy()
    }

    private fun clearError() {
        loadError = null
        failedUrl = null
    }

    private fun updateNavigation(webView: WebView) {
        canGoBack = webView.canGoBack()
        canGoForward = webView.canGoForward()
    }

    private fun readCanonical(webView: WebView, page: String) {
        webView.evaluateJavascript(PageScript.CANONICAL) { result ->
            val link = result?.trim('"')?.replace("\\/", "/")?.takeIf { it.isNotEmpty() && it != "null" }
            canonicalUrl = if (link != null && Urls.isWebPage(link) && Urls.sameSite(link, page) && webView.url == page) link else null
        }
    }

    /// A long press on a link, or a picture in a link: what can be done with it.
    private fun onLongPress(webView: WebView): Boolean {
        val hit = webView.hitTestResult
        return when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                hit.extra?.let { model.showLinkMenu(this, it, null) }
                hit.extra != null
            }
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val image = hit.extra
                val handler = Handler(Looper.getMainLooper()) { message ->
                    val link = message.data.getString("url")?.takeIf { it.isNotEmpty() } ?: image
                    if (link != null) model.showLinkMenu(this, link, image)
                    true
                }
                webView.requestFocusNodeHref(handler.obtainMessage())
                true
            }
            WebView.HitTestResult.IMAGE_TYPE -> {
                hit.extra?.takeIf { Urls.isWebPage(it) }?.let { model.showLinkMenu(this, it, it) }
                hit.extra != null
            }
            else -> false
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val address = request.url.toString()
            val scheme = Urls.scheme(address)
            if (scheme != null && scheme !in WEB_SCHEMES) {
                if (request.isForMainFrame || request.hasGesture()) model.openExternally(address)
                return true
            }
            if (!request.isForMainFrame) return false
            // Tracking codes: the same page, without them.
            if (model.cleanLinks && address != lastCleaned) {
                LinkCleaner.clean(address)?.let { cleaned ->
                    lastCleaned = cleaned
                    view.loadUrl(cleaned)
                    return true
                }
            }
            // http:// over https://, unless the site doesn't have it.
            if (model.httpsUpgrade && model.shields.areUp(address)) {
                val host = Urls.host(address)
                val secure = HttpsUpgrade.upgraded(address)
                if (host != null && secure != null && host !in model.httpOnlyHosts) {
                    val pending = upgrade
                    if (pending != null && Urls.host(pending.second) == host) {
                        // The site sent the upgraded load back to http://.
                        model.httpOnlyHosts.add(host)
                        upgrade = null
                    } else {
                        upgrade = Pair(secure, address)
                        view.loadUrl(secure)
                        return true
                    }
                }
            }
            pageHost = Urls.host(address)
            pageShieldsUp = model.shields.areUp(address)
            return false
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (request.isForMainFrame || !model.blockTrackers || !pageShieldsUp) return null
            val host = request.url.host?.lowercase() ?: return null
            if (!model.trackerRules.isTracker(host, pageHost)) return null
            view.post {
                trackersBlocked += 1
                model.stats.trackerBlocked()
            }
            return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }

        override fun onPageStarted(view: WebView, address: String, favicon: Bitmap?) {
            isLoading = true
            clearError()
            if (address != url) {
                trackersBlocked = 0
                canonicalUrl = null
            }
            url = address
            pageHost = Urls.host(address)
            pageShieldsUp = model.shields.areUp(address)
            shieldsUp = pageShieldsUp
            updateNavigation(view)
        }

        override fun onPageFinished(view: WebView, address: String) {
            isLoading = false
            progress = 100
            updateNavigation(view)
            val pending = upgrade
            if (pending != null && pending.first == address) {
                upgrade = null
                model.stats.connectionUpgraded()
            }
            if (Urls.isWebPage(address)) {
                if (!isPrivate) model.historyFor(profileId).record(address, view.title ?: "")
                readCanonical(view, address)
            }
            model.tabChanged(this@BrowserTab)
        }

        override fun doUpdateVisitedHistory(view: WebView, address: String, isReload: Boolean) {
            url = address
            updateNavigation(view)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val failing = request.url.toString()
            val pending = upgrade
            if (pending != null && pending.first == failing) {
                fallBackToHttp(view, pending)
                return
            }
            // A load replaced by another (or handed to a download) isn't a failure.
            if (error.description?.toString()?.contains("ERR_ABORTED") == true) return
            isLoading = false
            failedUrl = failing
            loadError = error.description?.toString() ?: model.activity.getString(R.string.page_failed)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            val pending = upgrade
            if (pending != null && Urls.host(error.url) == Urls.host(pending.first)) {
                fallBackToHttp(view, pending)
                return
            }
            if (Urls.host(error.url) == Urls.host(url)) {
                isLoading = false
                failedUrl = error.url
                loadError = model.activity.getString(R.string.not_secure)
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // The page's process ended (out of memory, a crash): the tab gets
            // a new WebView when shown again, and says what happened.
            val address = url
            this@BrowserTab.view = null
            appliedSettings = ""
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            failedUrl = address
            loadError = model.activity.getString(R.string.page_crashed)
            model.tabChanged(this@BrowserTab)
            return true
        }
    }

    private fun fallBackToHttp(view: WebView, pending: Pair<String, String>) {
        upgrade = null
        Urls.host(pending.second)?.let { model.httpOnlyHosts.add(it) }
        view.loadUrl(pending.second)
    }

    private inner class ChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress = newProgress
        }

        override fun onReceivedTitle(view: WebView, newTitle: String?) {
            title = newTitle ?: ""
            val address = view.url
            if (!isPrivate && address != null) model.historyFor(profileId).updateTitle(address, title)
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val tab = model.openWindow(this@BrowserTab)
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            transport.webView = tab.webView
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            model.close(this@BrowserTab)
        }

        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<android.net.Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
            return model.activity.chooseFiles(callback, params)
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            model.activity.askPermission(request)
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            model.activity.askLocation(origin, callback)
        }

        override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
            model.activity.showFullscreen(view, callback)
        }

        override fun onHideCustomView() {
            model.activity.hideFullscreen()
        }
    }

    companion object {
        private var nextId = 1L

        /// Schemes WebView loads itself; anything else (tel:, mailto:,
        /// intent:, market:…) goes to another app.
        private val WEB_SCHEMES = setOf("http", "https", "about", "data", "blob", "javascript", "file")
    }
}
