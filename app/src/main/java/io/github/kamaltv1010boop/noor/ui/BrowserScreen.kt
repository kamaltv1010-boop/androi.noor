package io.github.kamaltv1010boop.noor.ui

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kamaltv1010boop.noor.AddressResolver
import io.github.kamaltv1010boop.noor.BrowserModel
import io.github.kamaltv1010boop.noor.BrowserTab
import io.github.kamaltv1010boop.noor.MainActivity
import io.github.kamaltv1010boop.noor.Overlay
import io.github.kamaltv1010boop.noor.R
import io.github.kamaltv1010boop.noor.Urls

/// What sheet is up over the page.
enum class Sheet { MENU, SHIELDS, FINGERPRINT, PROFILES }

@Composable
fun BrowserScreen(model: BrowserModel, activity: MainActivity) {
    val tab = model.selected
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                WebContent(tab)
                if (tab != null && tab.isBlank) {
                    StartPage(model, onProfiles = { sheet = Sheet.PROFILES })
                }
                val error = tab?.loadError
                if (tab != null && error != null) {
                    ErrorPage(tab, error)
                }
                if (tab != null && tab.isLoading && tab.progress < 100) {
                    LinearProgressIndicator(
                        progress = { tab.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
                        color = NoorColors.light,
                    )
                }
                if (model.finding && tab != null) {
                    FindBar(model, tab, Modifier.align(Alignment.TopCenter))
                }
            }
            BottomBar(model, onMenu = { sheet = Sheet.MENU }, onShields = { sheet = Sheet.SHIELDS })
        }

        if (model.editingAddress) {
            AddressEditor(model)
        }
        when (model.overlay) {
            Overlay.TABS -> TabsScreen(model)
            Overlay.LIBRARY -> LibraryScreen(model)
            Overlay.SETTINGS -> SettingsScreen(model, onProfiles = { sheet = Sheet.PROFILES })
            Overlay.COOKIES -> CookiesScreen(model)
            null -> {}
        }
        when (sheet) {
            Sheet.MENU -> MenuSheet(model, activity, onDismiss = { sheet = null }, onOpen = { sheet = it })
            Sheet.SHIELDS -> ShieldsSheet(model, onDismiss = { sheet = null })
            Sheet.FINGERPRINT -> FingerprintSheet(model, onDismiss = { sheet = null })
            Sheet.PROFILES -> ProfilesSheet(model, onDismiss = { sheet = null })
            null -> {}
        }
        model.linkMenu?.let { LinkMenuDialog(model, activity, it) }
        model.externalUrl?.let { url ->
            AlertDialog(
                onDismissRequest = { model.externalUrl = null },
                title = { Text(stringResource(R.string.open_other_app)) },
                text = { Text(url, maxLines = 4, overflow = TextOverflow.Ellipsis) },
                confirmButton = {
                    TextButton(onClick = {
                        model.externalUrl = null
                        activity.openExternally(url)
                    }) { Text(stringResource(R.string.open)) }
                },
                dismissButton = { TextButton(onClick = { model.externalUrl = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        activity.permissionAsk?.let { ask ->
            AlertDialog(
                onDismissRequest = { ask.answer(false) },
                title = { Text(stringResource(R.string.permission_title, ask.origin)) },
                text = { Text(ask.what.joinToString(", ") { activity.getString(it) }) },
                confirmButton = { TextButton(onClick = { ask.answer(true) }) { Text(stringResource(R.string.allow)) } },
                dismissButton = { TextButton(onClick = { ask.answer(false) }) { Text(stringResource(R.string.block)) } },
            )
        }
        activity.fullscreenView?.let { FullscreenView(it) }
    }

    BackHandler {
        when {
            activity.fullscreenView != null -> activity.hideFullscreen()
            model.overlay != null -> model.overlay = null
            model.editingAddress -> model.editingAddress = false
            model.finding -> {
                model.finding = false
                tab?.clearFind()
            }
            tab != null && (tab.loadError != null || tab.canGoBack) -> tab.goBack()
            tab?.opener != null -> model.close(tab)
            else -> activity.moveTaskToBack(true)
        }
    }
}

/// The selected tab's WebView.
@Composable
private fun WebContent(tab: BrowserTab?) {
    val view = tab?.currentView
    AndroidView(
        factory = { context -> FrameLayout(context) },
        modifier = Modifier.fillMaxSize(),
        update = { frame ->
            if (view == null) {
                frame.removeAllViews()
            } else if (frame.childCount != 1 || frame.getChildAt(0) !== view) {
                frame.removeAllViews()
                (view.parent as? ViewGroup)?.removeView(view)
                frame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        },
    )
}

@Composable
private fun FullscreenView(view: View) {
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                (view.parent as? ViewGroup)?.removeView(view)
                addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        },
        modifier = Modifier.fillMaxSize().background(Color.Black),
    )
}

@Composable
private fun ErrorPage(tab: BrowserTab, error: String) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("⚠️", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.page_failed), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(Urls.host(tab.failedUrl) ?: "", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { tab.reload() }) { Text(stringResource(R.string.try_again)) }
        }
    }
}

@Composable
private fun BottomBar(model: BrowserModel, onMenu: () -> Unit, onShields: () -> Unit) {
    val tab = model.selected
    val isPrivate = tab?.isPrivate == true
    Surface(tonalElevation = 3.dp, color = if (isPrivate) Color(0xFF241A3F) else MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val contentColor = if (isPrivate) Color.White else MaterialTheme.colorScheme.onSurface
            IconButton(onClick = { tab?.goBack() }, enabled = tab != null && (tab.canGoBack || tab.loadError != null)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = contentColor)
            }
            IconButton(onClick = { tab?.goForward() }, enabled = tab?.canGoForward == true) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.forward), tint = contentColor)
            }
            AddressPill(model, isPrivate, onShields, Modifier.weight(1f))
            TabsButton(model.currentTabs.size, isPrivate, contentColor) { model.overlay = Overlay.TABS }
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.menu), tint = contentColor)
            }
        }
    }
}

@Composable
private fun AddressPill(model: BrowserModel, isPrivate: Boolean, onShields: () -> Unit, modifier: Modifier) {
    val tab = model.selected
    val url = tab?.displayUrl
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (isPrivate) Color(0xFF3A2A66) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { model.editingAddress = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tab != null && url != null && tab.loadError == null) {
            Box(Modifier.clickable(onClick = onShields).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Icon(
                    Icons.Filled.Shield,
                    stringResource(R.string.shields),
                    tint = if (tab.shieldsUp) NoorColors.shields else Color.Gray,
                    modifier = Modifier.size(22.dp),
                )
                if (tab.trackersBlocked > 0) {
                    Text(
                        tab.trackersBlocked.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Color(0xFFE5484D)).padding(horizontal = 3.dp),
                    )
                }
            }
        } else {
            Icon(
                Icons.Filled.Search,
                null,
                tint = if (isPrivate) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp).size(20.dp),
            )
        }
        if (url != null && Urls.scheme(url) == "https") {
            Icon(Icons.Filled.Lock, null, modifier = Modifier.size(14.dp), tint = if (isPrivate) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = Urls.host(url)?.removePrefix("www.") ?: stringResource(if (isPrivate) R.string.private_placeholder else R.string.address_placeholder),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isPrivate) Color.White else MaterialTheme.colorScheme.onSurface,
        )
        if (tab != null && url != null) {
            IconButton(onClick = { if (tab.isLoading) tab.stopLoading() else tab.reload() }, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (tab.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                    stringResource(if (tab.isLoading) R.string.stop else R.string.reload),
                    tint = if (isPrivate) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun TabsButton(count: Int, isPrivate: Boolean, color: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(
            Modifier.size(24.dp).border(2.dp, if (isPrivate) NoorColors.violet else color, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (count > 99) ":D" else count.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

/// Typing an address or a search, with what the bookmarks, pins and history
/// have that matches.
@Composable
private fun AddressEditor(model: BrowserModel) {
    val tab = model.selected
    val initial = tab?.displayUrl ?: ""
    var text by remember { mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    val pasted = clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() && it.length < 2000 }

    fun go(input: String) {
        val url = AddressResolver.url(input, model.searchEngine) ?: return
        model.editingAddress = false
        val target = model.selected ?: model.newTab()
        target.load(url)
    }

    val query = text.text.takeIf { it != initial } ?: ""
    val suggestions = remember(query, model.bookmarks.bookmarks, model.bookmarks.pins) {
        val needle = query.trim().lowercase()
        val saved = (model.bookmarks.pins + model.bookmarks.bookmarks)
            .filter { needle.isEmpty() || it.url.lowercase().contains(needle) || it.title.lowercase().contains(needle) }
            .map { it.title to it.url }
        val visited = if (tab?.isPrivate == true || needle.isEmpty()) emptyList() else model.history.search(needle, 8).map { it.title to it.url }
        (saved + visited).distinctBy { it.second }.take(8)
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            LazyColumn(Modifier.weight(1f), reverseLayout = true) {
                items(suggestions) { (title, url) ->
                    Column(
                        Modifier.fillMaxWidth().clickable { go(url) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(title.ifEmpty { url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
                if (pasted != null && query.isEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable { go(pasted) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.ContentPaste, null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.paste_and_go))
                                Text(pasted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.address_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = { go(text.text) }),
                    shape = RoundedCornerShape(22.dp),
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                )
                TextButton(onClick = { model.editingAddress = false }) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun FindBar(model: BrowserModel, tab: BrowserTab, modifier: Modifier) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Surface(modifier.fillMaxWidth(), tonalElevation = 6.dp, shadowElevation = 4.dp) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text,
                onValueChange = {
                    text = it
                    tab.find(it)
                },
                modifier = Modifier.weight(1f).focusRequester(focus),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.find_in_page)) },
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            )
            tab.findMatches?.let { (current, total) -> Text("$current/$total", modifier = Modifier.padding(horizontal = 6.dp)) }
            IconButton(onClick = { tab.findNext(false) }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
            IconButton(onClick = { tab.findNext(true) }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
            IconButton(onClick = {
                model.finding = false
                tab.clearFind()
            }) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun LinkMenuDialog(model: BrowserModel, activity: MainActivity, menu: io.github.kamaltv1010boop.noor.LinkMenu) {
    val close = { model.linkMenu = null }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = close,
        title = { Text(menu.url, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
        text = {
            Column {
                val options = buildList<Pair<Int, () -> Unit>> {
                    add(R.string.open_in_new_tab to { model.newTab(menu.url, isPrivate = menu.tab.isPrivate, select = false, opener = menu.tab); Unit })
                    if (!menu.tab.isPrivate) add(R.string.open_in_private_tab to { model.newTab(menu.url, isPrivate = true); Unit })
                    add(R.string.copy_link to { clipboard.setText(androidx.compose.ui.text.AnnotatedString(menu.url)) })
                    add(R.string.share_link to { activity.share(menu.url) })
                    add(R.string.pin_link to { model.bookmarks.pin(menu.url, Urls.site(menu.url) ?: menu.url) })
                    if (menu.image != null && menu.image != menu.url) {
                        add(R.string.open_image_in_new_tab to { model.newTab(menu.image, isPrivate = menu.tab.isPrivate, select = false); Unit })
                    }
                }
                for ((label, action) in options) {
                    Text(
                        stringResource(label),
                        modifier = Modifier.fillMaxWidth().clickable {
                            close()
                            action()
                        }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } },
    )
}

/// A row of a sheet or a list: an icon, a title, and an action.
@Composable
fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector?, title: String, emoji: String? = null, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint)
        } else if (emoji != null) {
            Text(emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.width(16.dp))
        Text(title, color = tint)
    }
}

@Composable
fun StarIcon(filled: Boolean) {
    Icon(Icons.Filled.Star, null, tint = if (filled) NoorColors.shields else MaterialTheme.colorScheme.onSurfaceVariant)
}
