package com.shoaib.tgforward.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Paper = Color(0xFFF4F7F8)
private val Ink = Color(0xFF1B242C)
private val Teal = Color(0xFF0F766E)
private val Muted = Color(0xFF5C6B73)

private val Colors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F3EC),
    onPrimaryContainer = Color(0xFF063F38),
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7EEEF),
    onSurfaceVariant = Muted,
    outlineVariant = Color(0xFFD5DEE2),
    error = Color(0xFFB42318),
    onError = Color.White,
)

@Composable
fun TgTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
