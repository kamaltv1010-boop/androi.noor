package io.github.kamaltv1010boop.noor

import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.kamaltv1010boop.noor.ui.BrowserScreen
import io.github.kamaltv1010boop.noor.ui.NoorTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/// A site asking for the camera, the microphone or the location: what the
/// prompt says, and what to do with the answer.
data class PermissionAsk(val origin: String, val what: List<Int>, val answer: (Boolean) -> Unit)

class MainActivity : ComponentActivity() {
    lateinit var model: BrowserModel
        private set

    /// A page's video (or other element) shown full screen.
    var fullscreenView by mutableStateOf<View?>(null)
        private set
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    var permissionAsk by mutableStateOf<PermissionAsk?>(null)
        private set

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }
    private var permissionsAnswer: ((Map<String, Boolean>) -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionsAnswer?.invoke(result)
        permissionsAnswer = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = BrowserModel(this)
        val metrics = resources.displayMetrics
        val width = min(metrics.widthPixels, metrics.heightPixels) / metrics.density
        val height = max(metrics.widthPixels, metrics.heightPixels) / metrics.density
        model.fingerprint.realScreen = Fingerprint.Screen(width.roundToInt(), height.roundToInt())
        // Stopped last time before it could clear what it was asked to.
        model.clearIfAsked()
        applySecureFlag()
        model.restoreTabs()
        handleIntent(intent)
        setContent {
            NoorTheme {
                BrowserScreen(model, this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /// A link another app opens here, a search, or text shared to Noor.
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString?.takeIf { Urls.isWebPage(it) }?.let { model.newTab(it) }
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(SearchManager.QUERY)?.let { open(it) }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { open(it) }
        }
    }

    private fun open(text: String) {
        val link = Regex("https?://\\S+").find(text)?.value ?: text
        AddressResolver.url(link, model.searchEngine)?.let { model.newTab(it) }
    }

    override fun onPause() {
        super.onPause()
        model.selected?.onPause()
        model.saveTabs()
        try {
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            // Written later, then.
        }
    }

    override fun onResume() {
        super.onResume()
        model.selected?.onResume()
    }

    override fun onDestroy() {
        if (isFinishing) {
            model.saveTabs()
            (model.tabs + model.privateTabs).forEach { it.destroy() }
            model.clearIfAsked()
        }
        super.onDestroy()
    }

    /// Hides the page in the app switcher (and from screenshots) when asked.
    fun applySecureFlag() {
        if (model.hideInSwitcher) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // MARK: - What pages ask for

    fun chooseFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        return try {
            fileChooser.launch(params.createIntent())
            true
        } catch (e: ActivityNotFoundException) {
            fileCallback = null
            callback.onReceiveValue(null)
            true
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /// Asks Android for `permissions` the app doesn't have yet, then answers
    /// with whether it has all of them.
    private fun withPermissions(permissions: List<String>, then: (Boolean) -> Unit) {
        val missing = permissions.filter { !hasPermission(it) }
        if (missing.isEmpty()) {
            then(true)
            return
        }
        permissionsAnswer = { result -> then(missing.all { result[it] == true || hasPermission(it) }) }
        permissionLauncher.launch(missing.toTypedArray())
    }

    fun askPermission(request: PermissionRequest) {
        val wanted = request.resources.filter {
            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE || it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
        }
        if (wanted.isEmpty()) {
            request.deny()
            return
        }
        // Protected content (DRM) plays as in Chrome.
        if (wanted.all { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }) {
            request.grant(wanted.toTypedArray())
            return
        }
        val what = wanted.mapNotNull {
            when (it) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> R.string.permission_camera
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> R.string.permission_microphone
                else -> null
            }
        }
        permissionAsk = PermissionAsk(request.origin.host ?: request.origin.toString(), what) { allowed ->
            permissionAsk = null
            if (!allowed) {
                request.deny()
                return@PermissionAsk
            }
            val needed = buildList {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in wanted) add(Manifest.permission.CAMERA)
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in wanted) add(Manifest.permission.RECORD_AUDIO)
            }
            withPermissions(needed) { granted ->
                if (granted) request.grant(wanted.toTypedArray()) else request.deny()
            }
        }
    }

    fun askLocation(origin: String, callback: GeolocationPermissions.Callback) {
        permissionAsk = PermissionAsk(Urls.host(origin) ?: origin, listOf(R.string.permission_location)) { allowed ->
            permissionAsk = null
            if (!allowed) {
                callback.invoke(origin, false, false)
                return@PermissionAsk
            }
            withPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { granted ->
                callback.invoke(origin, granted || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION), false)
            }
        }
    }

    /// Before Android 10 a download needs the app to write to the storage.
    fun canWriteDownloads(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) return true
        withPermissions(listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { granted ->
            if (granted) model.toast(getString(R.string.download_again))
        }
        return false
    }

    fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden()
            return
        }
        fullscreenView = view
        fullscreenCallback = callback
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    fun hideFullscreen() {
        if (fullscreenView == null) return
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    fun share(text: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        try {
            startActivity(Intent.createChooser(intent, null))
        } catch (e: ActivityNotFoundException) {
            model.toast(getString(R.string.no_app))
        }
    }

    /// Opens `url` in the app that handles it (tel:, mailto:, intent:…). An
    /// intent: address that no app takes opens its web address here instead.
    fun openExternally(url: String) {
        try {
            if (url.startsWith("intent:", ignoreCase = true)) {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                intent.component = null
                intent.selector = null
                try {
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    val fallback = intent.getStringExtra("browser_fallback_url")
                    if (fallback != null && Urls.isWebPage(fallback)) {
                        model.selected?.load(fallback)
                    } else {
                        model.toast(getString(R.string.no_app))
                    }
                }
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
            }
        } catch (e: Exception) {
            model.toast(getString(R.string.no_app))
        }
    }
}
