package io.github.kamaltv1010boop.noor

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/// Where settings are kept: SharedPreferences in the app (Prefs), a map in
/// a test.
interface Store {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun getLong(key: String): Long?
    fun putLong(key: String, value: Long)
    fun remove(key: String)
}

/// Brave-style shields. Tracker blocking, HTTPS upgrades and fingerprint
/// protection are on everywhere, and can be turned off for a single site
/// that doesn't work with them.
class Shields(private val store: Store) {
    /// The sites shields are down for, sorted.
    val downSites: List<String>
        get() {
            val text = store.getString(DOWN_SITES) ?: return emptyList()
            return try {
                val array = JSONArray(text)
                (0 until array.length()).map { array.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }

    fun areUp(url: String?): Boolean {
        val site = Urls.site(url) ?: return true
        return site !in downSites
    }

    fun setUp(isUp: Boolean, site: String) {
        val sites = downSites.filter { it != site }.toMutableList()
        if (!isUp) sites.add(site)
        store.putString(DOWN_SITES, JSONArray(sites.sorted()).toString())
    }

    companion object {
        const val DOWN_SITES = "shieldsDownSites"

        /// Sites that check the browser closely when you sign in or create an
        /// account, and block or keep asking for codes when it looks unusual:
        /// Meta's, and Google, Apple and Microsoft accounts. They see this
        /// phone's own screen and hardware, but still get the canvas, WebGL,
        /// audio and ClientRects noise, so each profile looks to them like
        /// another phone of the same kind; trackers are still blocked.
        val signInSites = listOf(
            "instagram.com", "facebook.com", "messenger.com", "threads.net", "threads.com", "whatsapp.com",
            "meta.com", "fb.com", "accounts.google.com", "appleid.apple.com", "idmsa.apple.com",
            "login.live.com", "login.microsoftonline.com",
        )

        fun isSignInSite(url: String?): Boolean {
            val host = Urls.host(url) ?: return false
            return signInSites.any { Urls.within(host, it) }
        }
    }
}

/// The tracking and ad services blocked on other sites than their own: Meta
/// Pixel and Facebook or Instagram files on other sites, Google Analytics,
/// TikTok, X, Snapchat and more (assets/trackers.json, the iPhone app's
/// rules). Meta's own sites still load their files.
class TrackerRules(json: String) {
    private class Rule(val domain: String, val allowedOn: List<String>)

    private val rules: List<Rule> = try {
        val array = JSONArray(json)
        (0 until array.length()).map { index ->
            val rule = array.getJSONObject(index)
            val allowed = rule.optJSONArray("allowedOn") ?: JSONArray()
            Rule(rule.getString("domain"), (0 until allowed.length()).map { allowed.getString(it) })
        }
    } catch (e: Exception) {
        emptyList()
    }

    val count: Int get() = rules.size

    /// Whether a file from `requestHost` is a tracker on a page of `pageHost`:
    /// one of the rules' domains, from another site, and not one of the
    /// sites it is allowed on.
    fun isTracker(requestHost: String, pageHost: String?): Boolean {
        val page = pageHost ?: ""
        return rules.any { rule ->
            Urls.within(requestHost, rule.domain) &&
                !Urls.within(page, rule.domain) &&
                rule.allowedOn.none { Urls.within(page, it) }
        }
    }
}

/// The fingerprints, in the spirit of Brave's "farbling". Pages can
/// recognize a phone from what it draws (canvas, WebGL), how it renders
/// audio, the exact sizes it lays out text and elements at (ClientRects), its
/// screen size and its hardware, even without cookies. With a protection on,
/// pages read invisibly changed pixels, samples and sizes, or a common
/// screen size and hardware, so these no longer identify the phone.
///
/// Every kind has its own switch, shared by the profiles, and its own seed
/// in each profile. A site sees stable values until the seed changes, which
/// keeps sites that read twice and compare working; a new seed gives every
/// site new values for that kind.
class Fingerprint(private val store: Store) {
    enum class Kind(val key: String) {
        CANVAS("canvas"), WEBGL("webGL"), AUDIO("audio"), CLIENT_RECTS("clientRects"), SCREEN("screen"), HARDWARE("hardware");

        val isNoise: Boolean get() = this != SCREEN && this != HARDWARE
    }

    data class Screen(val width: Int, val height: Int)

    data class Hardware(val cores: Int, val memory: Int)

    /// This phone's screen in CSS pixels, portrait, set at launch.
    var realScreen = Screen(0, 0)

    fun isEnabled(kind: Kind): Boolean = store.getBoolean("protection.${kind.key}", true)

    fun setEnabled(kind: Kind, isOn: Boolean) = store.putBoolean("protection.${kind.key}", isOn)

    private fun seedKey(kind: Kind, profile: String) = "profile.$profile.seed.${kind.key}"

    fun seed(kind: Kind, profile: String): Long = store.getLong(seedKey(kind, profile)) ?: newSeed(kind, profile)

    /// A new seed for `kind`. A new screen size or hardware must actually
    /// differ, when there is another one to pick.
    fun newSeed(kind: Kind, profile: String): Long {
        val old = store.getLong(seedKey(kind, profile))
        val canChange = choices(kind) > 1
        var seed: Long
        var attempts = 0
        do {
            seed = Random.nextLong(0, 1L shl 32)
            attempts++
        } while (canChange && old != null && visibleValue(kind, old) == visibleValue(kind, seed) && attempts < 64)
        store.putLong(seedKey(kind, profile), seed)
        return seed
    }

    fun newSeeds(kinds: Collection<Kind>, profile: String) = kinds.forEach { newSeed(it, profile) }

    fun forget(profile: String) = Kind.entries.forEach { store.remove(seedKey(it, profile)) }

    /// The screens pages may see: those at least as big as this phone's. A
    /// page wider than its screen happens on no real phone.
    fun screens(): List<Screen> {
        val fitting = SCREENS.filter { it.width >= realScreen.width && it.height >= realScreen.height }
        return fitting.ifEmpty { listOf(SCREENS.maxBy { it.width * it.height }) }
    }

    fun screen(profile: String): Screen = pick(seed(Kind.SCREEN, profile), screens())

    fun hardware(profile: String): Hardware = pick(seed(Kind.HARDWARE, profile), HARDWARE)

    private fun <T> pick(seed: Long, list: List<T>): T = list[(seed % list.size).toInt()]

    /// What a seed shows the user for a kind; a new seed must change it.
    fun visibleValue(kind: Kind, seed: Long): String = when (kind) {
        Kind.SCREEN -> pick(seed, screens()).let { "${it.width} × ${it.height}" }
        Kind.HARDWARE -> pick(seed, HARDWARE).let { "${it.cores} cores · ${it.memory} GB" }
        else -> seed.toString()
    }

    private fun choices(kind: Kind): Int = when (kind) {
        Kind.SCREEN -> screens().toSet().size
        Kind.HARDWARE -> HARDWARE.toSet().size
        else -> Int.MAX_VALUE
    }

    /// Changes whenever a switch or a seed of the profile changes.
    fun signature(profile: String): String = Kind.entries.joinToString(",") { "${isEnabled(it)}:${seed(it, profile)}" }

    /// What the page script is given (see bootstrap.js): the seeds of the
    /// noise that is on, the screen and hardware pages see (left out in
    /// desktop mode, where a phone's would contradict the computer the user
    /// agent claims), the brands to show when the WebView can't be told
    /// them, the sites shields are down for and the sign-in sites.
    fun config(profile: String, desktop: Boolean, brands: Brands?, downSites: List<String>): JSONObject {
        val seeds = JSONObject()
        for (kind in Kind.entries.filter { it.isNoise }) {
            seeds.put(kind.key, if (isEnabled(kind)) seed(kind, profile) else JSONObject.NULL)
        }
        val config = JSONObject()
        config.put("seeds", seeds)
        val screen = if (!desktop && isEnabled(Kind.SCREEN)) screen(profile) else null
        config.put("screen", screen?.let { JSONObject().put("width", it.width).put("height", it.height) } ?: JSONObject.NULL)
        val hardware = if (isEnabled(Kind.HARDWARE)) hardware(profile) else null
        config.put("hardware", hardware?.let { JSONObject().put("cores", it.cores).put("memory", it.memory) } ?: JSONObject.NULL)
        if (brands != null) {
            config.put("brands", brands.json(full = false))
            config.put("fullVersionList", brands.json(full = true))
            config.put("fullVersion", brands.fullVersion)
        } else {
            config.put("brands", JSONObject.NULL)
            config.put("fullVersionList", JSONObject.NULL)
            config.put("fullVersion", JSONObject.NULL)
        }
        config.put("downSites", JSONArray(downSites))
        config.put("signInSites", JSONArray(Shields.signInSites))
        return config
    }

    companion object {
        /// Common phone screens, in CSS pixels, portrait.
        val SCREENS = listOf(
            Screen(360, 780), Screen(360, 800), Screen(384, 832), Screen(393, 851),
            Screen(393, 873), Screen(412, 892), Screen(412, 915), Screen(448, 998),
        )

        /// Core counts and memory of common phones (Chrome reports memory in
        /// steps, 8 GB at most).
        val HARDWARE = listOf(Hardware(8, 4), Hardware(8, 8), Hardware(6, 4), Hardware(4, 4))
    }
}

/// What pages see of the browser: Google Chrome on Android, the version the
/// phone's WebView is, without "wv" or the WebView's own brand, which sites
/// read to tell an app's built-in browser (Google's sign-in refuses one).
class Brands(val major: Int, val fullVersion: String) {
    data class Brand(val brand: String, val version: String)

    /// The brands Chrome lists (Sec-CH-UA and navigator.userAgentData): a
    /// "Not A Brand" entry with the characters and version Chrome picks
    /// from its major version, Chromium, and Google Chrome, in Chrome's
    /// order for that version.
    val list: List<Brand> = run {
        val notABrand = Brand(
            "Not${GREASEY_CHARS[major % GREASEY_CHARS.size]}A${GREASEY_CHARS[(major + 1) % GREASEY_CHARS.size]}Brand",
            GREASEY_VERSIONS[major % GREASEY_VERSIONS.size],
        )
        val order = ORDERS[major % ORDERS.size]
        val slots = arrayOfNulls<Brand>(3)
        slots[order[0]] = notABrand
        slots[order[1]] = Brand("Chromium", major.toString())
        slots[order[2]] = Brand("Google Chrome", major.toString())
        slots.map { it!! }
    }

    /// The full version of each brand, as getHighEntropyValues gives them.
    fun fullVersionOf(brand: Brand): String = if (brand.brand.startsWith("Not")) "${brand.version}.0.0.0" else fullVersion

    fun json(full: Boolean): JSONArray = JSONArray(list.map { brand ->
        JSONObject().put("brand", brand.brand).put("version", if (full) fullVersionOf(brand) else brand.version)
    })

    /// Chrome's reduced user agent: the major version, the rest zeros. On a
    /// phone it names neither the Android version nor the model ("K").
    fun userAgent(desktop: Boolean): String = if (desktop) {
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
    } else {
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Mobile Safari/537.36"
    }

    companion object {
        private val GREASEY_CHARS = listOf(" ", "(", ":", "-", ".", "/", ")", ";", "=", "?", "_")
        private val GREASEY_VERSIONS = listOf("8", "99", "24")
        private val ORDERS = listOf(listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0))

        /// From the WebView's version name ("138.0.7204.63"), or the major
        /// version in its own user agent.
        fun of(webViewVersion: String?, defaultUserAgent: String?): Brands {
            val version = webViewVersion?.takeIf { Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+").containsMatchIn(it) }
                ?.let { Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+").find(it)!!.value }
            val major = version?.substringBefore('.')?.toIntOrNull()
                ?: defaultUserAgent?.let { Regex("Chrome/(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
                ?: 120
            return Brands(major, version ?: "$major.0.0.0")
        }
    }
}
