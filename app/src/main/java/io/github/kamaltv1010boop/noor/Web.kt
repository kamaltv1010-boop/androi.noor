package io.github.kamaltv1010boop.noor

import java.net.URLEncoder

/// Addresses as the browser handles them, without android.net.Uri, so the
/// rules here are the same in a test.
object Urls {
    private val withScheme = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(//)?([^/?#]*)")

    /// The scheme of `url`, lowercased, or null.
    fun scheme(url: String?): String? {
        val match = withScheme.find(url ?: return null) ?: return null
        return match.groupValues[1].lowercase()
    }

    /// The host of `url`, lowercased, without a port or user name, or null.
    fun host(url: String?): String? {
        val match = withScheme.find(url ?: return null) ?: return null
        if (match.groupValues[2].isEmpty()) return null
        var authority = match.groupValues[3]
        authority = authority.substringAfterLast('@')
        val host = if (authority.startsWith("[")) {
            authority.substringBefore(']') + "]"
        } else {
            authority.substringBefore(':')
        }
        return host.lowercase().trimEnd('.').takeIf { it.isNotEmpty() }
    }

    /// What shields and history are kept for: the host without "www.".
    fun site(url: String?): String? = host(url)?.removePrefix("www.")

    /// Whether `host` is `domain` or one of its subdomains.
    fun within(host: String, domain: String): Boolean = host == domain || host.endsWith(".$domain")

    /// Whether `a` and `b` are on the same site (the same host, or one of
    /// the other's subdomains, "www." aside).
    fun sameSite(a: String?, b: String?): Boolean {
        val one = site(a) ?: return false
        val two = site(b) ?: return false
        return within(one, two) || within(two, one)
    }

    fun isWebPage(url: String?): Boolean = scheme(url).let { it == "http" || it == "https" }
}

/// Opens http:// pages over https://, and goes back to http:// for the
/// sites that don't support it (see BrowserTab).
object HttpsUpgrade {
    /// The https:// version of `url`, or null if it should be left alone: it
    /// isn't http://, names a port, or is a local address.
    fun upgraded(url: String): String? {
        if (Urls.scheme(url) != "http") return null
        val match = Regex("^[hH][tT][tT][pP]://([^/?#]*)").find(url) ?: return null
        val authority = match.groupValues[1].substringAfterLast('@')
        if (authority.contains(':') || authority.startsWith("[")) return null
        val host = authority.lowercase()
        if (!host.contains('.') || host.endsWith(".local")) return null
        if (host.split('.').all { label -> label.isNotEmpty() && label.all { it.isDigit() } }) return null
        return "https" + url.substring(4)
    }
}

/// Removes tracking codes such as fbclid, igshid, gclid and utm_* from the
/// addresses you open, so sites can't tell where you came from.
object LinkCleaner {
    private val trackingParameters = setOf(
        "fbclid", "igshid", "igsh", "gclid", "dclid", "gbraid", "wbraid", "msclkid",
        "ttclid", "twclid", "li_fat_id", "yclid", "srsltid", "mc_cid", "mc_eid",
        "_hsenc", "_hsmi", "mkt_tok", "oly_anon_id", "oly_enc_id", "vero_id",
    )

    private fun isTracking(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith("utm_") || lower in trackingParameters
    }

    /// `url` without its tracking parameters, or null if it has none.
    fun clean(url: String): String? {
        if (!Urls.isWebPage(url)) return null
        val hash = url.indexOf('#')
        val beforeHash = if (hash >= 0) url.substring(0, hash) else url
        val fragment = if (hash >= 0) url.substring(hash) else ""
        val question = beforeHash.indexOf('?')
        if (question < 0) return null
        val base = beforeHash.substring(0, question)
        val items = beforeHash.substring(question + 1).split('&')
        val kept = items.filter { item ->
            val name = item.substringBefore('=')
            val decoded = try { java.net.URLDecoder.decode(name, "UTF-8") } catch (e: Exception) { name }
            !isTracking(decoded)
        }
        if (kept.size == items.size) return null
        val query = kept.filter { it.isNotEmpty() }.joinToString("&")
        return base + (if (query.isEmpty()) "" else "?$query") + fragment
    }
}

enum class SearchEngine(val title: String, val home: String, private val prefix: String) {
    GOOGLE("Google", "https://www.google.com", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com", "https://duckduckgo.com/?q="),
    BING("Bing", "https://www.bing.com", "https://www.bing.com/search?q=");

    fun searchUrl(query: String): String = prefix + URLEncoder.encode(query, "UTF-8").replace("+", "%20")

    companion object {
        fun named(name: String?): SearchEngine = entries.firstOrNull { it.name == name } ?: GOOGLE
    }
}

/// Turns what the user typed in the address bar into the address to load: a
/// full address, a bare one like "hespress.com", or a web search.
object AddressResolver {
    fun url(input: String, engine: SearchEngine): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val scheme = Urls.scheme(text)
        if ((scheme == "http" || scheme == "https") && Urls.host(text) != null) {
            return text
        }
        if (scheme == "about" || scheme == "data" || scheme == "file" || scheme == "javascript") {
            return if (scheme == "javascript") engine.searchUrl(text) else text
        }
        if (text.none { it.isWhitespace() }) {
            when (hostKind(text)) {
                HostKind.LOCAL -> return "http://$text"
                HostKind.DOMAIN -> return "https://$text"
                null -> {}
            }
        }
        return engine.searchUrl(text)
    }

    private enum class HostKind { DOMAIN, LOCAL }

    /// Looks at the part before the first "/", "?" or "#" and decides whether
    /// it is a domain ("google.com"), an IPv4 address, "localhost" or a
    /// ".local" device name, optionally followed by a port.
    private fun hostKind(text: String): HostKind? {
        var host = text.takeWhile { it !in "/?#" }
        val colon = host.lastIndexOf(':')
        if (colon >= 0) {
            val port = host.substring(colon + 1)
            if (port.isEmpty() || !port.all { it in '0'..'9' }) return null
            host = host.substring(0, colon)
        }
        if (host.lowercase() == "localhost") return HostKind.LOCAL
        val labels = host.split('.')
        if (labels.size < 2 || !labels.all { label -> label.isNotEmpty() && label.all { it.isLetterOrDigit() || it == '-' } }) return null
        if (labels.last().lowercase() == "local") return HostKind.LOCAL
        val isIPv4 = labels.size == 4 && labels.all { label -> label.all { it in '0'..'9' } && (label.toIntOrNull() ?: 256) <= 255 }
        if (isIPv4) return HostKind.LOCAL
        // The last label must look like a real top-level domain: "com", "ma",
        // "المغرب" or a punycode one such as "xn--mgbc0a9azcg".
        val tld = labels.last()
        if (tld.length < 2 || !(tld.all { it.isLetter() } || tld.lowercase().startsWith("xn--"))) return null
        return HostKind.DOMAIN
    }
}
