package io.github.kamaltv1010boop.noor.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kamaltv1010boop.noor.BrowserModel
import io.github.kamaltv1010boop.noor.Features
import io.github.kamaltv1010boop.noor.Fingerprint
import io.github.kamaltv1010boop.noor.Overlay
import io.github.kamaltv1010boop.noor.R
import io.github.kamaltv1010boop.noor.SearchEngine
import io.github.kamaltv1010boop.noor.Urls
import io.github.kamaltv1010boop.noor.Visit
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { actions() },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

// MARK: - Tabs

/// The tabs, as pictures: the normal ones or the private ones.
@Composable
fun TabsScreen(model: BrowserModel) {
    var showPrivate by remember { mutableStateOf(model.selected?.isPrivate == true) }
    val list = if (showPrivate) model.privateTabs else model.tabs
    Surface(Modifier.fillMaxSize(), color = if (showPrivate) Color(0xFF1B1430) else MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TabRow(selectedTabIndex = if (showPrivate) 1 else 0) {
                Tab(selected = !showPrivate, onClick = { showPrivate = false }, text = { Text(stringResource(R.string.tabs_count, model.tabs.size)) })
                Tab(selected = showPrivate, onClick = { showPrivate = true }, text = { Text(stringResource(R.string.private_count, model.privateTabs.size)) })
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.id }) { tab ->
                    val isSelected = tab == model.selected
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .border(if (isSelected) 3.dp else 1.dp, if (isSelected) NoorColors.violet else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                model.select(tab)
                                model.overlay = null
                            },
                    ) {
                        Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tab.displayTitle.ifEmpty { stringResource(R.string.new_tab) },
                                Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            IconButton(onClick = { model.close(tab) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Close, stringResource(R.string.close), Modifier.size(18.dp))
                            }
                        }
                        Box(Modifier.fillMaxWidth().aspectRatio(0.8f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            val picture = tab.thumbnail
                            if (picture != null) {
                                Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                            } else {
                                Text((Urls.site(tab.displayUrl) ?: "✦").take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = NoorColors.indigo)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { model.closeAll(showPrivate) }, enabled = list.isNotEmpty()) {
                    Text(stringResource(R.string.close_all), color = if (list.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Gray)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    model.newTab(isPrivate = showPrivate)
                    model.overlay = null
                }) { Icon(Icons.Filled.Add, stringResource(R.string.new_tab), tint = if (showPrivate) Color.White else MaterialTheme.colorScheme.onSurface) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    if (showPrivate && model.privateTabs.isEmpty()) model.newTab(isPrivate = true)
                    model.overlay = null
                }) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

// MARK: - Library

/// The bookmarks, the history (searchable, by day) and the pinned links.
@Composable
fun LibraryScreen(model: BrowserModel) {
    var page by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val open = { url: String ->
        model.overlay = null
        (model.selected ?: model.newTab()).load(url)
    }
    Screen(stringResource(R.string.library), onBack = { model.overlay = null }) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = page) {
                Tab(selected = page == 0, onClick = { page = 0 }, text = { Text(stringResource(R.string.bookmarks)) })
                Tab(selected = page == 1, onClick = { page = 1 }, text = { Text(stringResource(R.string.history)) })
                Tab(selected = page == 2, onClick = { page = 2 }, text = { Text(stringResource(R.string.pinned)) })
            }
            when (page) {
                0 -> SavedList(model.bookmarks.bookmarks.reversed(), stringResource(R.string.no_bookmarks), open) { model.bookmarks.removeBookmark(it) }
                2 -> SavedList(model.bookmarks.pins, stringResource(R.string.no_pins), open) { model.bookmarks.unpin(it) }
                else -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text(stringResource(R.string.search_history)) })
                        TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error) }
                    }
                    val visits = model.history.search(query, 500)
                    if (visits.isEmpty()) {
                        Empty(stringResource(R.string.no_history))
                    } else {
                        LazyColumn {
                            var lastDay = ""
                            for (visit in visits) {
                                val day = dayOf(visit)
                                if (day != lastDay) {
                                    lastDay = day
                                    item(key = "day-$day-${visit.time}") {
                                        Text(day, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall, color = NoorColors.violet)
                                    }
                                }
                                item(key = "${visit.time}-${visit.url}") {
                                    PageRow(visit.title, visit.url, onClick = { open(visit.url) }, onDelete = { model.history.remove(visit) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    model.history.clear()
                }) { Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun dayOf(visit: Visit): String {
    val day = Calendar.getInstance().apply { timeInMillis = visit.time }
    return DateFormat.getDateInstance(DateFormat.FULL).format(Date(day.timeInMillis))
}

@Composable
private fun SavedList(items: List<io.github.kamaltv1010boop.noor.Saved>, empty: String, open: (String) -> Unit, remove: (io.github.kamaltv1010boop.noor.Saved) -> Unit) {
    if (items.isEmpty()) {
        Empty(empty)
        return
    }
    LazyColumn {
        items(items, key = { it.url }) { saved ->
            PageRow(saved.title, saved.url, onClick = { open(saved.url) }, onDelete = { remove(saved) })
        }
    }
}

@Composable
private fun PageRow(title: String, url: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title.ifEmpty { url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// MARK: - Settings

@Composable
private fun Section(title: String) {
    Text(
        title,
        Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = NoorColors.violet,
    )
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(model: BrowserModel, onProfiles: () -> Unit) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE")
    val version = model.protectionVersion
    val profile = model.profiles.currentId
    Screen(stringResource(R.string.settings), onBack = { model.overlay = null }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Section(stringResource(R.string.search_engine))
            for (engine in SearchEngine.entries) {
                Row(
                    Modifier.fillMaxWidth().clickable { model.setSearchEngine(engine) }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = model.searchEngine == engine, onClick = { model.setSearchEngine(engine) })
                    Text(engine.title)
                }
            }

            Section(stringResource(R.string.shields))
            Toggle(stringResource(R.string.block_trackers), stringResource(R.string.block_trackers_explained), model.blockTrackers) { model.setBlockTrackers(it) }
            Toggle(stringResource(R.string.https_everywhere), stringResource(R.string.https_explained), model.httpsUpgrade) { model.setHttpsUpgrade(it) }
            Toggle(stringResource(R.string.clean_links), stringResource(R.string.clean_links_explained), model.cleanLinks) { model.setCleanLinks(it) }
            val down = model.shields.downSites
            Text(
                stringResource(R.string.shields_down_sites),
                Modifier.padding(start = 20.dp, top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (down.isEmpty()) {
                Text(stringResource(R.string.none), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (site in down) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(site, Modifier.weight(1f))
                    TextButton(onClick = { model.setShields(true, site) }) { Text(stringResource(R.string.shields_up_again)) }
                }
            }

            Section(stringResource(R.string.fingerprint))
            Text(stringResource(R.string.fingerprint_explained), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
            if (!Features.documentStart) {
                Text(stringResource(R.string.webview_too_old), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error)
            }
            for (kind in Fingerprint.Kind.entries) {
                val value = when (kind) {
                    Fingerprint.Kind.SCREEN -> model.fingerprint.screen(profile).let { "${it.width} × ${it.height}" }
                    Fingerprint.Kind.HARDWARE -> model.fingerprint.hardware(profile).let { stringResource(R.string.hardware_value, it.cores, it.memory) }
                    else -> null
                }
                Toggle(stringResource(kindTitle(kind)), value, model.fingerprint.isEnabled(kind)) { model.setProtection(kind, it) }
            }
            OutlinedButton(
                onClick = {
                    model.newFingerprints(Fingerprint.Kind.entries)
                    model.toast(context.getString(R.string.all_changed))
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            ) { Text(stringResource(R.string.change_all)) }

            Section(stringResource(R.string.privacy))
            Toggle(stringResource(R.string.hide_in_switcher), stringResource(R.string.hide_in_switcher_explained), model.hideInSwitcher) { model.setHideInSwitcher(it) }
            Toggle(stringResource(R.string.clear_on_exit), stringResource(R.string.clear_on_exit_explained), model.clearOnExit) { model.setClearOnExit(it) }
            Toggle(stringResource(R.string.night_mode), null, model.nightMode) { model.setNightMode(it) }
            Row(Modifier.fillMaxWidth().clickable(onClick = onProfiles).padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(stringResource(R.string.profiles), Modifier.weight(1f))
                Text(model.profiles.current.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = {
                model.overlay = null
                model.newSession()
            }, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { Text("🔥 " + stringResource(R.string.new_session)) }
            TextButton(onClick = {
                model.stats.reset()
            }, modifier = Modifier.padding(horizontal = 12.dp)) { Text(stringResource(R.string.reset_stats)) }

            Section(stringResource(R.string.about))
            val webView = model.brands.fullVersion
            Text(
                stringResource(R.string.about_text, versionName(context), webView),
                Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                listOf(
                    "Document-start scripts" to Features.documentStart,
                    "Profiles (own data)" to Features.multiProfile,
                    "Chrome brands in headers" to Features.userAgentMetadata,
                    "No X-Requested-With" to Features.requestedWithAllowList,
                ).joinToString("\n") { (name, ok) -> (if (ok) "✓ " else "✗ ") + name },
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kamaltv1010-boop/androi.noor")))
            }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("github.com/kamaltv1010-boop/androi.noor") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun versionName(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
} catch (e: Exception) {
    ""
}

// MARK: - Cookies

/// The cookies of the site shown, as a header string (name=value; …) to
/// copy, share or save, and the same to bring in, typed, pasted or from a
/// file; the page loads again with them, like Cookie-Editor.
@Composable
fun CookiesScreen(model: BrowserModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val tab = model.selected
    val url = tab?.url?.takeIf { Urls.isWebPage(it) }
    val manager = remember(tab) { tab?.let { model.cookieManager(it.webViewProfile) } }
    var cookies by remember(url) { mutableStateOf(url?.let { manager?.getCookie(it) } ?: "") }
    var input by remember { mutableStateOf("") }

    fun importCookies(text: String) {
        val target = url ?: return
        val pairs = text.split(';', '\n').map { it.trim() }.filter { it.contains('=') && !it.startsWith("#") }
        if (pairs.isEmpty() || manager == null) {
            model.toast(context.getString(R.string.cookies_none_found))
            return
        }
        for (pair in pairs) manager.setCookie(target, "$pair; Path=/")
        manager.flush()
        cookies = manager.getCookie(target) ?: ""
        model.toast(context.getString(R.string.cookies_imported, pairs.size))
        tab?.reload()
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(cookies.toByteArray()) }
                model.toast(context.getString(R.string.saved))
            } catch (e: Exception) {
                model.toast(context.getString(R.string.failed))
            }
        }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream -> importCookies(stream.bufferedReader().readText()) }
            } catch (e: Exception) {
                model.toast(context.getString(R.string.failed))
            }
        }
    }

    Screen(stringResource(R.string.cookies), onBack = { model.overlay = null }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (url == null) {
                Text(stringResource(R.string.cookies_open_site))
            } else {
                Text(Urls.site(url) ?: url, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.cookies_export), style = MaterialTheme.typography.titleSmall, color = NoorColors.violet)
                Surface(Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(cookies.ifEmpty { stringResource(R.string.cookies_empty) }, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(cookies))
                        model.toast(context.getString(R.string.copied))
                    }, enabled = cookies.isNotEmpty()) { Text(stringResource(R.string.copy)) }
                    OutlinedButton(onClick = { model.activity.share(cookies) }, enabled = cookies.isNotEmpty()) { Text(stringResource(R.string.share)) }
                    OutlinedButton(onClick = { save.launch("cookies-${Urls.site(url)}.txt") }, enabled = cookies.isNotEmpty()) { Text(stringResource(R.string.save)) }
                }
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.cookies_import), style = MaterialTheme.typography.titleSmall, color = NoorColors.violet)
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    placeholder = { Text("name=value; name2=value2") },
                    minLines = 3,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { importCookies(input) }, enabled = input.isNotBlank()) { Text(stringResource(R.string.import_cookies)) }
                    OutlinedButton(onClick = { load.launch(arrayOf("text/*", "application/json", "*/*")) }) { Text(stringResource(R.string.from_file)) }
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text(stringResource(R.string.cookies_explained), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
