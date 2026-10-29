package io.github.kamaltv1010boop.noor

import org.json.JSONObject
import kotlin.random.Random

/// The scripts a tab's documents run before the page's own (see
/// BrowserTab.applySettings): the protection, from assets/protect.js (the
/// iPhone app's) and assets/bootstrap.js, and night mode.
class PageScript(private val protect: String, private val bootstrap: String) {
    /// The frame mark and the name the scripts go by, where a stack could
    /// show it: new for each run of the app, so a page can neither
    /// recognize nor imitate them.
    val key = "_" + hex(12)
    val name = hex(6) + ".js"

    private val source = "(() => {\n\"use strict\";\n$protect\nreturn protectPage;\n})()\n//# sourceURL=$name"

    /// The protection with `config` (see Fingerprint.config).
    fun protection(config: JSONObject): String = bootstrap
        .replace("/*NOOR:KEY*/\"\"", JSONObject.quote(key))
        .replace("/*NOOR:CONFIG*/null", config.toString())
        .replace("/*NOOR:SOURCE*/\"\"", JSONObject.quote(source))
        .replace("/*NOOR:CODE*/", protect)
        .replace("/*NOOR:NAME*/", name)

    companion object {
        private fun hex(bytes: Int): String = Random.nextBytes(bytes).joinToString("") { "%02x".format(it) }

        /// Night mode: pages shown with dark colors by inverting them, and
        /// inverting pictures and videos back so they look as they should.
        /// The style goes in as soon as the page has a root element, so it
        /// never flashes white.
        const val NIGHT_MODE = """(() => {
  if (window.top !== window) return;
  const style = document.createElement("style");
  style.textContent =
    "html{filter:invert(1) hue-rotate(180deg)!important;background:#fff!important}" +
    "img,video,picture,canvas,iframe,embed,object,svg image,[style*='background-image']" +
    "{filter:invert(1) hue-rotate(180deg)!important}";
  const add = () => document.documentElement.appendChild(style);
  if (document.documentElement) {
    add();
  } else {
    new MutationObserver((records, observer) => {
      if (!document.documentElement) return;
      observer.disconnect();
      add();
    }).observe(document, { childList: true });
  }
})();"""

        /// The page's canonical link, when it gives one.
        const val CANONICAL = """(function () { var link = document.querySelector('link[rel="canonical"]'); return link && link.href ? String(link.href) : ''; })()"""
    }
}
