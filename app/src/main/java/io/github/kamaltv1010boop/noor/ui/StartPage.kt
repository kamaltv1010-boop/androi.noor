package io.github.kamaltv1010boop.noor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kamaltv1010boop.noor.BrowserModel
import io.github.kamaltv1010boop.noor.R
import io.github.kamaltv1010boop.noor.Saved
import io.github.kamaltv1010boop.noor.Urls

/// What an empty tab shows: the search, the privacy totals, the pinned links
/// and the bookmarks; and the profiles, to switch at once.
@Composable
fun StartPage(model: BrowserModel, onProfiles: () -> Unit) {
    val tab = model.selected
    val isPrivate = tab?.isPrivate == true
    Surface(Modifier.fillMaxSize(), color = if (isPrivate) Color(0xFF1B1430) else MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.app_name),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPrivate) Color.White else NoorColors.indigo,
            )
            if (!isPrivate) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    for (profile in model.profiles.all) {
                        FilterChip(
                            selected = profile.id == model.profiles.currentId,
                            onClick = { model.switchProfile(profile.id) },
                            label = { Text(profile.name) },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = onProfiles,
                        label = { Text("…") },
                        leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.private_explained),
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
                    .background(if (isPrivate) Color(0xFF3A2A66) else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { model.editingAddress = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = if (isPrivate) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.search_with, model.searchEngine.title),
                    color = if (isPrivate) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isPrivate) {
                Spacer(Modifier.height(20.dp))
                Stats(model)
            }
            if (model.bookmarks.pins.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionTitle(stringResource(R.string.pinned), isPrivate)
                for (row in model.bookmarks.pins.chunked(4)) {
                    Row(Modifier.fillMaxWidth()) {
                        for (pin in row) {
                            PinTile(model, pin, isPrivate, Modifier.weight(1f))
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (model.bookmarks.bookmarks.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionTitle(stringResource(R.string.bookmarks), isPrivate)
                for (bookmark in model.bookmarks.bookmarks.takeLast(12).reversed()) {
                    Column(
                        Modifier.fillMaxWidth().clickable { model.selected?.load(bookmark.url) }.padding(vertical = 8.dp),
                    ) {
                        Text(bookmark.title.ifEmpty { bookmark.url }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isPrivate) Color.White else MaterialTheme.colorScheme.onSurface)
                        Text(Urls.site(bookmark.url) ?: bookmark.url, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, isPrivate: Boolean) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = if (isPrivate) Color.White else MaterialTheme.colorScheme.onSurface,
    )
}

/// The totals, like Brave's: trackers blocked, connections upgraded to
/// https, and the time that saved.
@Composable
private fun Stats(model: BrowserModel) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(vertical = 16.dp)) {
            Stat(model.stats.trackersBlocked.toString(), stringResource(R.string.trackers_blocked), NoorColors.shields, Modifier.weight(1f))
            Stat(model.stats.httpsUpgrades.toString(), stringResource(R.string.https_upgrades), NoorColors.indigo, Modifier.weight(1f))
            Stat(formatTime(model.stats.secondsSaved), stringResource(R.string.time_saved), NoorColors.violet, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun formatTime(seconds: Long): String = when {
    seconds < 60 -> stringResource(R.string.seconds_short, seconds)
    seconds < 3600 -> stringResource(R.string.minutes_short, seconds / 60)
    else -> stringResource(R.string.hours_short, seconds / 3600)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinTile(model: BrowserModel, pin: Saved, isPrivate: Boolean, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    Column(
        modifier.combinedClickable(onClick = { model.selected?.load(pin.url) }, onLongClick = { menu = true }).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(NoorColors.brand), contentAlignment = Alignment.Center) {
            Text((Urls.site(pin.url) ?: "?").take(1).uppercase(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            pin.title.ifEmpty { Urls.site(pin.url) ?: pin.url },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = if (isPrivate) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text(pin.title.ifEmpty { pin.url }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.open_in_new_tab),
                        Modifier.fillMaxWidth().clickable {
                            menu = false
                            model.newTab(pin.url, isPrivate = isPrivate)
                        }.padding(vertical = 12.dp),
                    )
                    Text(
                        stringResource(R.string.unpin),
                        Modifier.fillMaxWidth().clickable {
                            menu = false
                            model.bookmarks.unpin(pin)
                        }.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { menu = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
