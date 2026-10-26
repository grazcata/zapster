package com.graz.zapster.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FB2FF),
    onPrimary = Color(0xFF0A1A33),
    primaryContainer = Color(0xFF23406B),
    onPrimaryContainer = Color(0xFFD7E5FF),
    secondary = Color(0xFF9BA4B8),
    background = Color(0xFF101014),
    onBackground = Color(0xFFE6E6EC),
    surface = Color(0xFF16161C),
    onSurface = Color(0xFFE6E6EC),
    surfaceVariant = Color(0xFF262630),
    onSurfaceVariant = Color(0xFFC6C6D2),
    error = Color(0xFFE4493C),
    onError = Color(0xFF2A0906),
)

@Composable
fun TvRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
