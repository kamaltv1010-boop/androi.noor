package io.github.kamaltv1010boop.noor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kamaltv1010boop.noor.BrowserModel
import io.github.kamaltv1010boop.noor.Fingerprint
import io.github.kamaltv1010boop.noor.MainActivity
import io.github.kamaltv1010boop.noor.Overlay
import io.github.kamaltv1010boop.noor.Profiles
import io.github.kamaltv1010boop.noor.R
import io.github.kamaltv1010boop.noor.Urls

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp)) {
            content()
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector?, title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun MenuSheet(model: BrowserModel, activity: MainActivity, onDismiss: () -> Unit, onOpen: (Sheet) -> Unit) {
    val tab = model.selected
    val url = if (tab != null && tab.loadError == null) tab.url?.takeIf { Urls.isWebPage(it) } else null
    val act = { action: () -> Unit ->
        onDismiss()
        action()
    }
    Sheet(onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            QuickAction(Icons.Filled.Add, stringResource(R.string.new_tab)) { act { model.newTab() } }
            QuickAction(Icons.Filled.VisibilityOff, stringResource(R.string.new_private_tab)) { act { model.newTab(isPrivate = true) } }
            QuickAction(Icons.Filled.History, stringResource(R.string.library)) { act { model.overlay = Overlay.LIBRARY } }
            QuickAction(Icons.Filled.Settings, stringResource(R.string.settings)) { act { model.overlay = Overlay.SETTINGS } }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (url != null && tab != null) {
            val kept = tab.keptUrl ?: url
            val bookmarked = model.bookmarks.isBookmarked(kept)
            ActionRow(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, stringResource(if (bookmarked) R.string.remove_bookmark else R.string.add_bookmark)) {
                act { model.bookmarks.toggleBookmark(kept, tab.displayTitle) }
            }
            ActionRow(Icons.Filled.PushPin, stringResource(R.string.pin_page)) { act { model.bookmarks.pin(kept, tab.displayTitle) } }
            ActionRow(Icons.Filled.Share, stringResource(R.string.share)) { act { activity.share(kept) } }
            ActionRow(Icons.Filled.FindInPage, stringResource(R.string.find_in_page)) { act { model.finding = true } }
            SwitchRow(Icons.Filled.DesktopWindows, stringResource(R.string.desktop_site), tab.isDesktop) { act { tab.setDesktop(it) } }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.text_size), Modifier.weight(1f))
                TextButton(onClick = { tab.setZoom(tab.zoom - 10) }) { Text("A−") }
                Text("${tab.zoom}%")
                TextButton(onClick = { tab.setZoom(tab.zoom + 10) }) { Text("A+") }
            }
        }
        SwitchRow(Icons.Filled.DarkMode, stringResource(R.string.night_mode), model.nightMode) { model.setNightMode(it) }
        ActionRow(Icons.Filled.Fingerprint, stringResource(R.string.fingerprint)) { onOpen(Sheet.FINGERPRINT) }
        ActionRow(Icons.Filled.Person, stringResource(R.string.profile_named, model.profiles.current.name)) { onOpen(Sheet.PROFILES) }
        ActionRow(null, stringResource(R.string.cookies), emoji = "🍪") { act { model.overlay = Overlay.COOKIES } }
        ActionRow(null, stringResource(R.string.new_session), emoji = "🔥", tint = MaterialTheme.colorScheme.error) { act { model.newSession() } }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(8.dp).width(76.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
    }
}

/// The shields for the site shown: the trackers blocked there, and a switch
/// to turn them off for this site alone if it doesn't work with them.
@Composable
fun ShieldsSheet(model: BrowserModel, onDismiss: () -> Unit) {
    val tab = model.selected ?: return
    val site = Urls.site(tab.url) ?: return
    Sheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(site, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.trackers_on_page, tab.trackersBlocked),
                color = NoorColors.shields,
            )
        }
        Spacer(Modifier.height(8.dp))
        SwitchRow(null, stringResource(R.string.shields_for_site), tab.shieldsUp, stringResource(R.string.shields_explained)) { isUp ->
            model.setShields(isUp, site)
        }
    }
}

/// Each fingerprint's switch, and new values for any or all of them.
@Composable
fun FingerprintSheet(model: BrowserModel, onDismiss: () -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val version = model.protectionVersion
    val profile = model.profiles.currentId
    Sheet(onDismiss) {
        Text(
            stringResource(R.string.fingerprint_explained),
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        for (kind in Fingerprint.Kind.entries) {
            val subtitle = when (kind) {
                Fingerprint.Kind.SCREEN -> model.fingerprint.screen(profile).let { "${it.width} × ${it.height}" }
                Fingerprint.Kind.HARDWARE -> model.fingerprint.hardware(profile).let { stringResource(R.string.hardware_value, it.cores, it.memory) }
                else -> null
            }
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SwitchRow(null, stringResource(kindTitle(kind)), model.fingerprint.isEnabled(kind), subtitle) { model.setProtection(kind, it) }
                }
                TextButton(onClick = {
                    model.newFingerprints(listOf(kind))
                    model.toast(model.activity.getString(R.string.fingerprint_changed, model.activity.getString(kindTitle(kind))))
                }) { Text(stringResource(R.string.new_value)) }
            }
        }
        Button(
            onClick = {
                model.newFingerprints(Fingerprint.Kind.entries)
                model.toast(model.activity.getString(R.string.all_changed))
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        ) { Text(stringResource(R.string.change_all)) }
    }
}

fun kindTitle(kind: Fingerprint.Kind): Int = when (kind) {
    Fingerprint.Kind.CANVAS -> R.string.kind_canvas
    Fingerprint.Kind.WEBGL -> R.string.kind_webgl
    Fingerprint.Kind.AUDIO -> R.string.kind_audio
    Fingerprint.Kind.CLIENT_RECTS -> R.string.kind_client_rects
    Fingerprint.Kind.SCREEN -> R.string.kind_screen
    Fingerprint.Kind.HARDWARE -> R.string.kind_hardware
}

/// The profiles: switch to one, make one, or delete one with its data.
@Composable
fun ProfilesSheet(model: BrowserModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    Sheet(onDismiss) {
        Text(stringResource(R.string.profiles), Modifier.padding(horizontal = 20.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.profiles_explained), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
        if (!io.github.kamaltv1010boop.noor.Features.multiProfile) {
            Text(stringResource(R.string.profiles_shared_data), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        for (profile in model.profiles.all) {
            Row(
                Modifier.fillMaxWidth().clickable {
                    model.switchProfile(profile.id)
                    onDismiss()
                }.padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (profile.id == model.profiles.currentId) Icons.Filled.Check else Icons.Filled.Person, null)
                Spacer(Modifier.width(16.dp))
                Text(profile.name, Modifier.weight(1f))
                if (profile.id != Profiles.FIRST) {
                    IconButton(onClick = { deleting = profile.id }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.new_profile)) })
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                model.addProfile(name)
                name = ""
                onDismiss()
            }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) }
        }
        if (model.profiles.all.size > 1) {
            TextButton(onClick = { deleting = "*" }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.delete_all_profiles), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    deleting?.let { id ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(if (id == "*") R.string.delete_all_profiles else R.string.delete_profile)) },
            text = { Text(stringResource(R.string.delete_profile_explained)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    if (id == "*") model.deleteAllProfiles() else model.deleteProfile(id)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
