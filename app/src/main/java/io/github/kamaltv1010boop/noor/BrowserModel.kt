package io.github.kamaltv1010boop.noor

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.ProfileStore
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Collections

/// What the phone's WebView can do (it is updated from Google Play apart
/// from Android, so this can change from one launch to the next).
object Features {
    /// Scripts that run before a page's own: without them, no protection.
    val documentStart: Boolean by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }
    /// Data of their own for each profile and for private tabs.
    val multiProfile: Boolean by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }
    /// Chrome's brands in the Sec-CH-UA headers and navigator.userAgentData.
    val userAgentMetadata: Boolean by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA) }
    /// No X-Requested-With header naming the app.
    val requestedWithAllowList: Boolean by lazy { WebViewFeature.isFeatureSupported("REQUESTED_WITH_HEADER_ALLOW_LIST") }
}

/// A link long pressed, and the picture it is, if any.
data class LinkMenu(val tab: BrowserTab, val url: String, val image: String?)

enum class Overlay { TABS, LIBRARY, SETTINGS, COOKIES }

/// The tabs (normal ones of the profile in use, and private ones), the
/// settings they share, and what the screens show of them.
class BrowserModel(val activity: MainActivity) {
    val store: Prefs = Prefs(activity)
    val profiles = Profiles(store)
    val shields = Shields(store)
    val fingerprint = Fingerprint(store)
    val stats = PrivacyStats(store)
    val bookmarks = BookmarkStore(File(activity.filesDir, "bookmarks.json"))
    val trackerRules = TrackerRules(asset("trackers.json"))
    val pageScript = PageScript(asset("protect.js"), asset("bootstrap.js"))
    val brands: Brands = Brands.of(
        try { WebViewCompat.getCurrentWebViewPackage(activity)?.versionName } catch (e: Exception) { null },
        try { WebSettings.getDefaultUserAgent(activity) } catch (e: Exception) { null },
    )
    private val histories = HashMap<String, HistoryStore>()
    /// Hosts that failed over https://, opened over http:// from then on.
    val httpOnlyHosts: MutableSet<String> = Collections.synchronizedSet(HashSet())

    val tabs = mutableStateListOf<BrowserTab>()
    val privateTabs = mutableStateListOf<BrowserTab>()
    var selected by mutableStateOf<BrowserTab?>(null)
        private set

    var overlay by mutableStateOf<Overlay?>(null)
    var editingAddress by mutableStateOf(false)
    var finding by mutableStateOf(false)
    var linkMenu by mutableStateOf<LinkMenu?>(null)
    /// An address another app opens, waiting for the user to agree.
    var externalUrl by mutableStateOf<String?>(null)

    var searchEngine by mutableStateOf(SearchEngine.named(store.getString(Prefs.SEARCH_ENGINE)))
        private set
    var blockTrackers by mutableStateOf(store.getBoolean(Prefs.BLOCK_TRACKERS, true))
        private set
    var httpsUpgrade by mutableStateOf(store.getBoolean(Prefs.HTTPS_UPGRADE, true))
        private set
    var cleanLinks by mutableStateOf(store.getBoolean(Prefs.CLEAN_LINKS, true))
        private set
    var nightMode by mutableStateOf(store.getBoolean(Prefs.NIGHT_MODE, false))
        private set
    var hideInSwitcher by mutableStateOf(store.getBoolean(Prefs.HIDE_IN_SWITCHER, false))
        private set
    var clearOnExit by mutableStateOf(store.getBoolean(Prefs.CLEAR_ON_EXIT, false))
        private set
    /// Changes when a switch of the protection changes, so the screens update.
    var protectionVersion by mutableStateOf(0)
        private set

    private fun asset(name: String): String = activity.assets.open(name).bufferedReader().use { it.readText() }

    fun historyFor(profileId: String): HistoryStore =
        histories.getOrPut(profileId) { HistoryStore(File(activity.filesDir, "history-$profileId.json")) }

    val history: HistoryStore get() = historyFor(profiles.currentId)

    // MARK: - Settings

    fun setSearchEngine(engine: SearchEngine) {
        searchEngine = engine
        store.putString(Prefs.SEARCH_ENGINE, engine.name)
    }

    fun setBlockTrackers(isOn: Boolean) {
        blockTrackers = isOn
        store.putBoolean(Prefs.BLOCK_TRACKERS, isOn)
    }

    fun setHttpsUpgrade(isOn: Boolean) {
        httpsUpgrade = isOn
        store.putBoolean(Prefs.HTTPS_UPGRADE, isOn)
    }

    fun setCleanLinks(isOn: Boolean) {
        cleanLinks = isOn
        store.putBoolean(Prefs.CLEAN_LINKS, isOn)
    }

    fun setNightMode(isOn: Boolean) {
        nightMode = isOn
        store.putBoolean(Prefs.NIGHT_MODE, isOn)
        settingsChanged(reload = true)
    }

    fun setHideInSwitcher(isOn: Boolean) {
        hideInSwitcher = isOn
        store.putBoolean(Prefs.HIDE_IN_SWITCHER, isOn)
        activity.applySecureFlag()
    }

    fun setClearOnExit(isOn: Boolean) {
        clearOnExit = isOn
        store.putBoolean(Prefs.CLEAR_ON_EXIT, isOn)
    }

    fun setProtection(kind: Fingerprint.Kind, isOn: Boolean) {
        fingerprint.setEnabled(kind, isOn)
        settingsChanged()
    }

    fun newFingerprints(kinds: Collection<Fingerprint.Kind>) {
        fingerprint.newSeeds(kinds, profiles.currentId)
        settingsChanged(reload = true)
    }

    fun setShields(isUp: Boolean, site: String) {
        shields.setUp(isUp, site)
        settingsChanged()
        selected?.let { if (Urls.site(it.url) == site) it.reload() }
    }

    /// The tabs get the settings as they are now, for their next pages (the
    /// one shown too, if `reload`).
    fun settingsChanged(reload: Boolean = false) {
        protectionVersion += 1
        for (tab in tabs + privateTabs) {
            tab.applySettings()
        }
        if (reload) selected?.reload()
    }

    /// Changes whenever something a tab's scripts depend on changes.
    fun settingsSignature(profileId: String): String =
        "${fingerprint.signature(profileId)}|${shields.downSites.joinToString(",")}|$nightMode|${fingerprint.realScreen}"

    /// The protection's script for a profile's tabs, or null if no
    /// protection is on (and the WebView shows Chrome's brands itself).
    fun protectionScript(profileId: String, desktop: Boolean): String? {
        val brandsInScript = if (Features.userAgentMetadata) null else brands
        if (brandsInScript == null && Fingerprint.Kind.entries.none { fingerprint.isEnabled(it) }) return null
        return pageScript.protection(fingerprint.config(profileId, desktop, brandsInScript, shields.downSites))
    }

    /// Chrome's brands, in the request headers too, where the WebView can be
    /// told them; the rest of what it says of the phone stays as it is.
    fun applyUserAgentMetadata(settings: WebSettings, desktop: Boolean) {
        if (!Features.userAgentMetadata) return
        try {
            val list = brands.list.map {
                UserAgentMetadata.BrandVersion.Builder()
                    .setBrand(it.brand)
                    .setMajorVersion(it.version)
                    .setFullVersion(brands.fullVersionOf(it))
                    .build()
            }
            val metadata = UserAgentMetadata.Builder(WebSettingsCompat.getUserAgentMetadata(settings))
                .setBrandVersionList(list)
                .setFullVersion(brands.fullVersion)
                .setPlatform(if (desktop) "Linux" else "Android")
                .setMobile(!desktop)
                .build()
            WebSettingsCompat.setUserAgentMetadata(settings, metadata)
        } catch (e: Exception) {
            // The WebView's own, then.
        }
    }

    // MARK: - Tabs

    val currentTabs: List<BrowserTab> get() = if (selected?.isPrivate == true) privateTabs else tabs

    fun newTab(url: String? = null, isPrivate: Boolean = false, select: Boolean = true, opener: BrowserTab? = null): BrowserTab {
        val tab = BrowserTab(this, isPrivate, profiles.currentId, opener = opener)
        val list = if (isPrivate) privateTabs else tabs
        val index = opener?.let { list.indexOf(it) }?.takeIf { it >= 0 }?.plus(1) ?: list.size
        list.add(index, tab)
        if (select || selected == null) select(tab)
        if (url != null) tab.load(url)
        saveTabs()
        return tab
    }

    /// The tab for a window a page opens (window.open, a link to a new tab).
    fun openWindow(opener: BrowserTab): BrowserTab = newTab(isPrivate = opener.isPrivate, opener = opener)

    fun select(tab: BrowserTab) {
        if (tab == selected) {
            tab.loadIfNeeded()
            return
        }
        selected?.let {
            it.takeThumbnail()
            it.onPause()
            it.clearFind()
        }
        finding = false
        selected = tab
        if (tab.pendingUrl != null) {
            tab.loadIfNeeded()
        } else {
            // Made now, so the screen has it to show (not while drawing).
            tab.webView
        }
        tab.onResume()
        saveTabs()
    }

    fun close(tab: BrowserTab) {
        val list = if (tab.isPrivate) privateTabs else tabs
        val index = list.indexOf(tab)
        if (index < 0) return
        list.removeAt(index)
        if (selected == tab) {
            val next = tab.opener?.takeIf { it in list } ?: list.getOrNull(index.coerceAtMost(list.size - 1))
            if (next != null) {
                selected = null
                select(next)
            } else if (tab.isPrivate && tabs.isNotEmpty()) {
                selected = null
                select(tabs.last())
            } else {
                selected = null
                newTab()
            }
        }
        (tabs + privateTabs).forEach { if (it.opener == tab) it.opener = null }
        tab.destroy()
        if (tab.isPrivate && privateTabs.isEmpty()) clearPrivateData()
        saveTabs()
    }

    fun closeAll(isPrivate: Boolean) {
        val list = if (isPrivate) privateTabs else tabs
        val closing = list.toList()
        list.clear()
        closing.forEach { it.destroy() }
        if (isPrivate) clearPrivateData()
        if (selected?.isPrivate == isPrivate || selected == null) {
            selected = null
            if (tabs.isNotEmpty()) select(tabs.last()) else newTab()
        }
        saveTabs()
    }

    fun tabChanged(tab: BrowserTab) {
        if (!tab.isPrivate) saveTabs()
    }

    /// Remembers the profile's open tabs for the next launch, unless
    /// everything is cleared on exit.
    fun saveTabs() {
        val key = profiles.key(profiles.currentId, "tabs")
        if (clearOnExit) {
            store.remove(key)
            return
        }
        val saved = tabs.mapNotNull { it.saved }
        store.putString(key, JSONArray(saved.map { JSONObject().put("url", it.url).put("title", it.title) }).toString())
        val index = tabs.indexOf(selected).takeIf { it >= 0 } ?: (saved.size - 1)
        store.putLong(profiles.key(profiles.currentId, "selected"), index.toLong())
    }

    /// The profile's tabs from the last launch, or a new one.
    fun restoreTabs() {
        val text = store.getString(profiles.key(profiles.currentId, "tabs"))
        val saved = try {
            text?.let { JSONArray(it) }?.let { array ->
                (0 until array.length()).map { array.getJSONObject(it).let { t -> SavedTab(t.getString("url"), t.optString("title")) } }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        for (entry in saved) {
            tabs.add(BrowserTab(this, false, profiles.currentId, restored = entry))
        }
        val index = store.getLong(profiles.key(profiles.currentId, "selected"))?.toInt() ?: (tabs.size - 1)
        val tab = tabs.getOrNull(index) ?: tabs.lastOrNull()
        if (tab != null) select(tab) else newTab()
    }

    // MARK: - Profiles and sessions

    fun switchProfile(id: String) {
        if (id == profiles.currentId) return
        saveTabs()
        (tabs + privateTabs).forEach { it.destroy() }
        val hadPrivate = privateTabs.isNotEmpty()
        tabs.clear()
        privateTabs.clear()
        if (hadPrivate) clearPrivateData()
        selected = null
        profiles.select(id)
        restoreTabs()
        protectionVersion += 1
    }

    fun addProfile(name: String) {
        val profile = profiles.add(name)
        switchProfile(profile.id)
    }

    /// Deletes a profile with all its data: cookies, site data, history,
    /// tabs and fingerprints.
    fun deleteProfile(id: String) {
        if (id == Profiles.FIRST) return
        if (id == profiles.currentId) switchProfile(Profiles.FIRST)
        val name = Profiles.webViewProfileName(id)
        var deleted = false
        if (Features.multiProfile) {
            try {
                deleted = ProfileStore.getInstance().deleteProfile(name)
            } catch (e: Exception) {
                deleted = false
            }
        }
        if (!deleted && Features.multiProfile) clearData(name)
        historyFor(id).clear()
        histories.remove(id)
        fingerprint.forget(id)
        store.remove(profiles.key(id, "tabs"))
        store.remove(profiles.key(id, "selected"))
        profiles.remove(id)
    }

    fun deleteAllProfiles() {
        profiles.all.filter { it.id != Profiles.FIRST }.forEach { deleteProfile(it.id) }
    }

    /// A new session: the profile's cookies, site data and history cleared,
    /// its tabs closed and every fingerprint new. Other profiles keep theirs.
    fun newSession() {
        val id = profiles.currentId
        val closing = tabs.toList()
        tabs.clear()
        selected?.takeIf { !it.isPrivate }?.let { selected = null }
        clearData(Profiles.webViewProfileName(id), closing.firstNotNullOfOrNull { if (it.hasWebView) it.webView else null })
        closing.forEach { it.destroy() }
        historyFor(id).clear()
        fingerprint.newSeeds(Fingerprint.Kind.entries, id)
        httpOnlyHosts.clear()
        protectionVersion += 1
        newTab()
        toast(activity.getString(R.string.new_session_done))
    }

    fun cookieManager(profileName: String): CookieManager =
        if (Features.multiProfile) ProfileStore.getInstance().getOrCreateProfile(profileName).cookieManager else CookieManager.getInstance()

    private fun webStorage(profileName: String): WebStorage =
        if (Features.multiProfile) ProfileStore.getInstance().getOrCreateProfile(profileName).webStorage else WebStorage.getInstance()

    /// Clears a WebView profile's cookies and site data, and its cache
    /// through `view` (one of its WebViews) if given.
    fun clearData(profileName: String, view: WebView? = null) {
        try {
            val cookies = cookieManager(profileName)
            cookies.removeAllCookies(null)
            cookies.flush()
            webStorage(profileName).deleteAllData()
            view?.clearCache(true)
        } catch (e: Exception) {
            // Nothing more can be done here.
        }
    }

    private fun clearPrivateData() {
        if (Features.multiProfile) clearData(Profiles.PRIVATE_WEBVIEW_PROFILE)
    }

    /// With "clear everything on exit", what the profiles kept goes as the
    /// app closes (and, should it have been stopped, as it opens again).
    fun clearIfAsked() {
        if (!clearOnExit) return
        for (profile in profiles.all) {
            if (Features.multiProfile || profile.id == Profiles.FIRST) clearData(Profiles.webViewProfileName(profile.id))
            historyFor(profile.id).clear()
            store.remove(profiles.key(profile.id, "tabs"))
        }
        clearPrivateData()
    }

    // MARK: - What pages hand over

    fun showLinkMenu(tab: BrowserTab, url: String, image: String?) {
        linkMenu = LinkMenu(tab, url, image)
    }

    fun openExternally(url: String) {
        externalUrl = url
    }

    fun download(tab: BrowserTab, url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        if (!Urls.isWebPage(url)) {
            toast(activity.getString(R.string.download_unsupported))
            return
        }
        if (!activity.canWriteDownloads()) return
        try {
            val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mimeType)
                .setTitle(name)
                .addRequestHeader("User-Agent", brands.userAgent(tab.isDesktop))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            cookieManager(tab.webViewProfile).getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
            (activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            toast(activity.getString(R.string.downloading, name))
        } catch (e: Exception) {
            toast(activity.getString(R.string.download_failed))
        }
    }

    fun toast(text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
    }
}
