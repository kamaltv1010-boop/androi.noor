package io.github.kamaltv1010boop.noor

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/// The settings, in SharedPreferences.
class Prefs(context: Context) : Store {
    private val prefs: SharedPreferences = context.getSharedPreferences("noor", Context.MODE_PRIVATE)

    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
    override fun getLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0) else null
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()

    companion object {
        const val SEARCH_ENGINE = "searchEngine"
        const val BLOCK_TRACKERS = "blockTrackers"
        const val HTTPS_UPGRADE = "httpsUpgrade"
        const val CLEAN_LINKS = "cleanLinks"
        const val NIGHT_MODE = "nightMode"
        const val HIDE_IN_SWITCHER = "hideInSwitcher"
        const val CLEAR_ON_EXIT = "clearOnExit"
    }
}

/// A profile: its own cookies and site data (a WebView profile of its own,
/// where the phone's WebView has them), history, tabs and fingerprints, like
/// another browser. Bookmarks, pinned links and settings are shared.
data class Profile(val id: String, val name: String)

class Profiles(private val store: Store) {
    var all: List<Profile> by mutableStateOf(load())
        private set
    var currentId: String by mutableStateOf(store.getString(CURRENT) ?: all.first().id)
        private set

    val current: Profile get() = all.firstOrNull { it.id == currentId } ?: all.first()

    private fun load(): List<Profile> {
        val saved = store.getString(LIST)?.let { text ->
            try {
                val array = JSONArray(text)
                (0 until array.length()).map { array.getJSONObject(it).let { p -> Profile(p.getString("id"), p.getString("name")) } }
            } catch (e: Exception) {
                null
            }
        }
        return saved?.takeIf { it.isNotEmpty() } ?: listOf(Profile(FIRST, "Personal"))
    }

    private fun save() {
        store.putString(LIST, JSONArray(all.map { JSONObject().put("id", it.id).put("name", it.name) }).toString())
        store.putString(CURRENT, currentId)
    }

    fun add(name: String): Profile {
        val profile = Profile(UUID.randomUUID().toString().take(8), name.trim().ifEmpty { "Profile ${all.size + 1}" })
        all = all + profile
        save()
        return profile
    }

    fun rename(id: String, name: String) {
        all = all.map { if (it.id == id && name.isNotBlank()) it.copy(name = name.trim()) else it }
        save()
    }

    fun remove(id: String) {
        if (id == FIRST || all.size < 2) return
        all = all.filter { it.id != id }
        if (currentId == id) currentId = all.first().id
        save()
    }

    fun select(id: String) {
        if (all.any { it.id == id }) {
            currentId = id
            save()
        }
    }

    /// A key of `name` kept for profile `id`.
    fun key(id: String, name: String) = "profile.$id.$name"

    companion object {
        const val FIRST = "first"
        private const val LIST = "profiles"
        private const val CURRENT = "currentProfile"

        /// The WebView profile that keeps a profile's cookies and site data:
        /// the WebView's default one for the first profile.
        fun webViewProfileName(id: String): String = if (id == FIRST) androidx.webkit.Profile.DEFAULT_PROFILE_NAME else "noor-$id"

        /// Private tabs' WebView profile, emptied when the last one closes.
        const val PRIVATE_WEBVIEW_PROFILE = "noor-private"
    }
}

/// A page visited, as the history keeps it.
data class Visit(val url: String, val title: String, val time: Long)

/// One profile's history, in a file of its own. Private tabs aren't in it.
class HistoryStore(private val file: File) {
    var visits: List<Visit> by mutableStateOf(load())
        private set

    private fun load(): List<Visit> = try {
        if (!file.exists()) emptyList() else {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { array.getJSONObject(it).let { v -> Visit(v.getString("url"), v.optString("title"), v.getLong("time")) } }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save() {
        try {
            file.writeText(JSONArray(visits.map { JSONObject().put("url", it.url).put("title", it.title).put("time", it.time) }).toString())
        } catch (e: Exception) {
            // Kept for this run only.
        }
    }

    /// Records a visit, newest first; visiting the same page again moves it up.
    fun record(url: String, title: String) {
        if (!Urls.isWebPage(url)) return
        val now = System.currentTimeMillis()
        val previous = visits.firstOrNull()
        visits = if (previous != null && previous.url == url) {
            listOf(previous.copy(title = title.ifEmpty { previous.title }, time = now)) + visits.drop(1)
        } else {
            (listOf(Visit(url, title, now)) + visits).take(MAX)
        }
        save()
    }

    /// The title a page has now, when it changes after the visit is recorded.
    fun updateTitle(url: String, title: String) {
        if (title.isEmpty()) return
        val first = visits.firstOrNull() ?: return
        if (first.url == url && first.title != title) {
            visits = listOf(first.copy(title = title)) + visits.drop(1)
            save()
        }
    }

    fun remove(visit: Visit) {
        visits = visits.filter { it != visit }
        save()
    }

    fun clear() {
        visits = emptyList()
        file.delete()
    }

    /// Visits whose address or title has `text`, most recent first, each page once.
    fun search(text: String, limit: Int): List<Visit> {
        val query = text.trim().lowercase()
        val seen = HashSet<String>()
        return visits.asSequence()
            .filter { query.isEmpty() || it.url.lowercase().contains(query) || it.title.lowercase().contains(query) }
            .filter { seen.add(it.url) }
            .take(limit)
            .toList()
    }

    companion object {
        const val MAX = 5000
    }
}

/// A page kept: a bookmark or a pinned link.
data class Saved(val url: String, val title: String)

/// The bookmarks and pinned links, shared by the profiles.
class BookmarkStore(private val file: File) {
    var bookmarks: List<Saved> by mutableStateOf(emptyList())
        private set
    var pins: List<Saved> by mutableStateOf(emptyList())
        private set

    init {
        try {
            if (file.exists()) {
                val json = JSONObject(file.readText())
                bookmarks = read(json.optJSONArray("bookmarks"))
                pins = read(json.optJSONArray("pins"))
            }
        } catch (e: Exception) {
            // Start empty.
        }
    }

    private fun read(array: JSONArray?): List<Saved> = if (array == null) emptyList() else
        (0 until array.length()).map { array.getJSONObject(it).let { s -> Saved(s.getString("url"), s.optString("title")) } }

    private fun write(list: List<Saved>) = JSONArray(list.map { JSONObject().put("url", it.url).put("title", it.title) })

    private fun save() {
        try {
            file.writeText(JSONObject().put("bookmarks", write(bookmarks)).put("pins", write(pins)).toString())
        } catch (e: Exception) {
            // Kept for this run only.
        }
    }

    fun isBookmarked(url: String?) = url != null && bookmarks.any { it.url == url }

    fun toggleBookmark(url: String, title: String) {
        bookmarks = if (isBookmarked(url)) bookmarks.filter { it.url != url } else bookmarks + Saved(url, title)
        save()
    }

    fun removeBookmark(saved: Saved) {
        bookmarks = bookmarks.filter { it != saved }
        save()
    }

    fun isPinned(url: String?) = url != null && pins.any { it.url == url }

    fun pin(url: String, title: String) {
        if (!isPinned(url)) {
            pins = pins + Saved(url, title)
            save()
        }
    }

    fun unpin(saved: Saved) {
        pins = pins.filter { it != saved }
        save()
    }
}

/// Totals for the start page, like Brave's.
class PrivacyStats(private val store: Store) {
    var trackersBlocked by mutableIntStateOf(store.getLong(TRACKERS)?.toInt() ?: 0)
        private set
    var httpsUpgrades by mutableIntStateOf(store.getLong(UPGRADES)?.toInt() ?: 0)
        private set

    /// About 50 ms of loading for every tracker that didn't load.
    val secondsSaved: Long get() = trackersBlocked * 50L / 1000L

    fun trackerBlocked() {
        trackersBlocked += 1
        store.putLong(TRACKERS, trackersBlocked.toLong())
    }

    fun connectionUpgraded() {
        httpsUpgrades += 1
        store.putLong(UPGRADES, httpsUpgrades.toLong())
    }

    fun reset() {
        trackersBlocked = 0
        httpsUpgrades = 0
        store.remove(TRACKERS)
        store.remove(UPGRADES)
    }

    companion object {
        private const val TRACKERS = "stats.trackersBlocked"
        private const val UPGRADES = "stats.httpsUpgrades"
    }
}
