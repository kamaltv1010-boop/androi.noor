package io.github.kamaltv1010boop.noor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/// The iPhone app's colors: night blue to violet, and the shields' orange.
object NoorColors {
    val night = Color(0xFF0B0A2E)
    val indigo = Color(0xFF2A1FB8)
    val violet = Color(0xFF7A2CF2)
    val shields = Color(0xFFFF8A00)
    val light = Color(0xFFFF8A4C)
    val privateTint = Color(0xFF7A2CF2)
    val brand = Brush.linearGradient(listOf(night, indigo, violet))
}

@Composable
fun NoorTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF9D8CFF), secondary = NoorColors.light, tertiary = NoorColors.shields)
    } else {
        lightColorScheme(primary = NoorColors.indigo, secondary = NoorColors.violet, tertiary = NoorColors.shields)
    }
    MaterialTheme(colorScheme = colors, content = content)
}
