package io.github.kamaltv1010boop.noor

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/// Noor on a phone (the emulator's WebView): a page opened in a tab sees
/// Chrome, not an app's WebView, and the profile's fingerprints, from its
/// first line, with nothing of the protection in its stack.
@RunWith(AndroidJUnit4::class)
class ProtectionTest {
    private lateinit var server: ServerSocket
    private var running = true

    /// What the page reads as it loads (an inline script, before anything
    /// else of the page), and later on request.
    private val page = """<!doctype html><html><head><meta name="viewport" content="width=device-width"><title>Probe</title>
<script>
window.first = (() => {
  const c = document.createElement("canvas"); c.width = 160; c.height = 40;
  const x = c.getContext("2d"); x.font = "16px sans-serif"; x.fillText("Noor ✓ probe", 4, 26);
  const d = x.getImageData(0, 0, 160, 40).data; let h = 0; for (const v of d) h = (h * 31 + v) >>> 0;
  let stack = ""; x.getImageData({ valueOf() { stack = new Error().stack; return 0; } }, 0, 1, 1);
  return { canvas: h, stack, cores: navigator.hardwareConcurrency, screen: screen.width, webdriver: navigator.webdriver,
    agent: navigator.userAgent, marker: Function.prototype.toString.call(function () { /* noor lookalike */ }).includes("native code") };
})();
</script></head><body>probe</body></html>"""

    @Before
    fun serve() {
        server = ServerSocket(0)
        thread {
            while (running) {
                val socket = try { server.accept() } catch (e: Exception) { break }
                thread {
                    socket.use {
                        val reader = it.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val body = page.toByteArray()
                        val head = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                        it.getOutputStream().apply {
                            write(head.toByteArray())
                            write(body)
                            flush()
                        }
                    }
                }
            }
        }
    }

    @After
    fun stop() {
        running = false
        server.close()
    }

    private fun evaluate(scenario: ActivityScenario<MainActivity>, script: String): String {
        val latch = CountDownLatch(1)
        var result = ""
        scenario.onActivity { activity ->
            activity.model.selected!!.currentView!!.evaluateJavascript(script) { value ->
                result = value ?: ""
                latch.countDown()
            }
        }
        assertTrue("no answer from the page", latch.await(20, TimeUnit.SECONDS))
        return result
    }

    /// Loads the probe in the selected tab, and gives what it read as it loaded.
    private fun probe(scenario: ActivityScenario<MainActivity>): JSONObject {
        val url = "http://127.0.0.1:${server.localPort}/probe?${System.nanoTime()}"
        scenario.onActivity { it.model.selected!!.load(url) }
        repeat(60) {
            Thread.sleep(500)
            val ready = evaluate(scenario, "document.readyState === 'complete' && location.href === '$url' && typeof window.first === 'object'")
            if (ready == "true") {
                val text = evaluate(scenario, "JSON.stringify(window.first)")
                return JSONObject(JSONObject("{\"v\":$text}").getString("v"))
            }
        }
        throw AssertionError("the probe page didn't load")
    }

    @Test
    fun pagesSeeChromeAndTheProfilesFingerprints() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: BrowserModel? = null
            scenario.onActivity { model = it.model }
            val browser = model!!
            Log.i("NoorTest", "WebView ${browser.brands.fullVersion}: documentStart=${Features.documentStart} multiProfile=${Features.multiProfile} userAgentMetadata=${Features.userAgentMetadata}")
            assertTrue("this WebView can't run scripts before pages", WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))

            val first = probe(scenario)
            Log.i("NoorTest", "probe: $first")
            assertEquals(false, first.get("webdriver"))
            val agent = first.getString("agent")
            assertTrue(agent, agent.contains("Chrome/") && agent.contains("Mobile Safari/537.36"))
            assertFalse(agent, agent.contains("; wv") || agent.contains("Version/4.0"))
            assertTrue("the protection isn't in the page", first.getBoolean("marker"))
            val profile = browser.profiles.currentId
            assertEquals(browser.fingerprint.hardware(profile).cores, first.getInt("cores"))
            assertEquals(browser.fingerprint.screen(profile).width, first.getInt("screen"))
            val stack = first.getString("stack")
            assertFalse("the protection shows in a stack: $stack", stack.contains(browser.pageScript.name))
            assertTrue("the stack has frames that aren't the page's: $stack", stack.lines().filter { it.trim().startsWith("at ") }.all { it.contains("127.0.0.1") })

            val brands = evaluate(scenario, "navigator.userAgentData ? navigator.userAgentData.brands.map((b) => b.brand).join(',') : 'none'")
            Log.i("NoorTest", "brands: $brands")
            assertTrue(brands, brands.contains("Google Chrome") && !brands.contains("WebView"))

            // A new canvas fingerprint gives the page other pixels.
            scenario.onActivity { it.model.newFingerprints(listOf(Fingerprint.Kind.CANVAS)) }
            val second = probe(scenario)
            assertNotEquals(first.getLong("canvas"), second.getLong("canvas"))
        }
    }
}
