package io.github.kamaltv1010boop.noor

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/// Settings in a map, for the rules that keep them.
class MapStore : Store {
    val map = HashMap<String, Any>()
    override fun getBoolean(key: String, default: Boolean) = map[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getString(key: String) = map[key] as? String
    override fun putString(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    override fun getLong(key: String) = map[key] as? Long
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

class RulesTest {
    @Test
    fun hostsAndSites() {
        assertEquals("www.example.com", Urls.host("https://user@WWW.Example.com:8443/a?b#c"))
        assertEquals("example.com", Urls.site("https://www.example.com/"))
        assertNull(Urls.host("mailto:someone@example.com"))
        assertTrue(Urls.within("cdn.facebook.net", "facebook.net"))
        assertFalse(Urls.within("notfacebook.net", "facebook.net"))
        assertTrue(Urls.sameSite("https://m.example.com/x", "https://example.com/"))
    }

    @Test
    fun httpsUpgrades() {
        assertEquals("https://example.com/a?b=1", HttpsUpgrade.upgraded("http://example.com/a?b=1"))
        assertNull(HttpsUpgrade.upgraded("https://example.com/"))
        assertNull(HttpsUpgrade.upgraded("http://example.com:8080/"))
        assertNull(HttpsUpgrade.upgraded("http://192.168.1.1/"))
        assertNull(HttpsUpgrade.upgraded("http://printer.local/"))
        assertNull(HttpsUpgrade.upgraded("http://localhost/"))
    }

    @Test
    fun linksLoseTrackingCodes() {
        assertEquals("https://example.com/p?id=4#top", LinkCleaner.clean("https://example.com/p?fbclid=x&id=4&utm_source=fb#top"))
        assertEquals("https://example.com/p", LinkCleaner.clean("https://example.com/p?gclid=1"))
        assertNull(LinkCleaner.clean("https://example.com/p?id=4"))
    }

    @Test
    fun addressesAndSearches() {
        val google = SearchEngine.GOOGLE
        assertEquals("https://hespress.com", AddressResolver.url("hespress.com", google))
        assertEquals("http://localhost:3000", AddressResolver.url("localhost:3000", google))
        assertEquals("http://192.168.1.1", AddressResolver.url("192.168.1.1", google))
        assertEquals("https://example.com/a", AddressResolver.url("https://example.com/a", google))
        assertEquals("https://www.google.com/search?q=hello%20world", AddressResolver.url("hello world", google))
        assertEquals("https://duckduckgo.com/?q=noor", AddressResolver.url("noor", SearchEngine.DUCKDUCKGO))
        assertNull(AddressResolver.url("  ", google))
    }

    @Test
    fun shieldsPerSite() {
        val shields = Shields(MapStore())
        assertTrue(shields.areUp("https://www.example.com/"))
        shields.setUp(false, "example.com")
        assertFalse(shields.areUp("https://www.example.com/x"))
        assertTrue(shields.areUp("https://other.com/"))
        shields.setUp(true, "example.com")
        assertTrue(shields.areUp("https://example.com/"))
        assertTrue(Shields.isSignInSite("https://accounts.google.com/signin"))
        assertTrue(Shields.isSignInSite("https://www.instagram.com/"))
        assertFalse(Shields.isSignInSite("https://www.google.com/"))
    }

    @Test
    fun trackersAreThirdPartyOnly() {
        val rules = TrackerRules(File("src/main/assets/trackers.json").readText())
        assertTrue(rules.count > 40)
        assertTrue(rules.isTracker("connect.facebook.net", "news.example"))
        assertFalse(rules.isTracker("connect.facebook.net", "www.facebook.com"))
        assertFalse(rules.isTracker("static.xx.fbcdn.net", "www.instagram.com"))
        assertTrue(rules.isTracker("www.google-analytics.com", "shop.example"))
        assertFalse(rules.isTracker("cdn.example.com", "news.example"))
    }

    @Test
    fun fingerprintsPerProfile() {
        val fingerprint = Fingerprint(MapStore())
        fingerprint.realScreen = Fingerprint.Screen(393, 873)
        assertTrue(fingerprint.screens().all { it.width >= 393 && it.height >= 873 })
        val first = fingerprint.seed(Fingerprint.Kind.CANVAS, "a")
        assertEquals(first, fingerprint.seed(Fingerprint.Kind.CANVAS, "a"))
        assertNotEquals(first, fingerprint.seed(Fingerprint.Kind.CANVAS, "b"))
        val screen = fingerprint.screen("a")
        fingerprint.newSeed(Fingerprint.Kind.SCREEN, "a")
        if (fingerprint.screens().toSet().size > 1) assertNotEquals(screen, fingerprint.screen("a"))
        fingerprint.setEnabled(Fingerprint.Kind.AUDIO, false)
        val config = fingerprint.config("a", desktop = false, brands = null, downSites = listOf("off.example"))
        assertTrue(config.getJSONObject("seeds").isNull("audio"))
        assertFalse(config.getJSONObject("seeds").isNull("canvas"))
        assertEquals(fingerprint.screen("a").width, config.getJSONObject("screen").getInt("width"))
        assertTrue(fingerprint.config("a", desktop = true, brands = null, downSites = emptyList()).isNull("screen"))
        assertEquals("off.example", config.getJSONArray("downSites").getString(0))
    }

    @Test
    fun chromeBrandsAndUserAgent() {
        val brands = Brands.of("138.0.7204.63", null)
        assertEquals(138, brands.major)
        assertEquals(listOf("Not)A;Brand", "Chromium", "Google Chrome").sorted(), brands.list.map { it.brand }.sorted())
        assertEquals("138.0.7204.63", brands.fullVersionOf(brands.list.first { it.brand == "Google Chrome" }))
        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Mobile Safari/537.36",
            brands.userAgent(desktop = false),
        )
        assertFalse(brands.userAgent(desktop = false).contains("wv"))
        assertEquals(120, Brands.of(null, "Mozilla/5.0 (Linux; Android 14; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.230 Mobile Safari/537.36").major)
    }

    @Test
    fun pageScriptIsAssembled() {
        val script = PageScript(File("src/main/assets/protect.js").readText(), File("src/main/assets/bootstrap.js").readText())
        val config = JSONObject().put("seeds", JSONObject()).put("downSites", org.json.JSONArray()).put("signInSites", org.json.JSONArray())
        val text = script.protection(config)
        assertFalse(text.contains("/*NOOR:"))
        assertTrue(text.contains(script.key) && text.contains("//# sourceURL=${script.name}"))
        assertTrue(text.contains("const protectPage = (window, config) =>"))
        assertTrue(Regex("^[0-9a-f]{12}\\.js$").matches(script.name))
    }
}
