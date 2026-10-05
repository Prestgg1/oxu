// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5F8B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E7F6),
    onPrimaryContainer = Color(0xFF082A3F),
    secondary = Color(0xFF4C6879),
    onSecondary = Color.White,
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF11181D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF11181D),
    surfaceVariant = Color(0xFFE4EBF1),
    onSurfaceVariant = Color(0xFF43535E),
    outline = Color(0xFFB9C6D0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CC7E8),
    onPrimary = Color(0xFF06283D),
    primaryContainer = Color(0xFF16445F),
    onPrimaryContainer = Color(0xFFCFE7F8),
    secondary = Color(0xFFB1C9D8),
    onSecondary = Color(0xFF1B323F),
    background = Color(0xFF0F1418),
    onBackground = Color(0xFFE3E9EE),
    surface = Color(0xFF161C21),
    onSurface = Color(0xFFE3E9EE),
    surfaceVariant = Color(0xFF2A333B),
    onSurfaceVariant = Color(0xFFBFCBD4),
    outline = Color(0xFF55636D)
)

@Composable
fun OxuTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
