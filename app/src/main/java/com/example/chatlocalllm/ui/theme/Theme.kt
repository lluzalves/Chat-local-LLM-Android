package com.example.chatlocalllm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Deep = Color(0xFF0B1C26)
    val Surface = Color(0xFF10232E)
    val Surface2 = Color(0xFF15303B)
    val Person = Color(0xFF1F4F60)
    val Line = Color(0xFF22414D)
    val Text = Color(0xFFE6F1F5)
    val Muted = Color(0xFF8FA9B3)
    val Teal = Color(0xFF5BC2DC)
    val Teal2 = Color(0xFF2ABBD6)
}

val ChatColorScheme = darkColorScheme(
    primary = Palette.Teal,
    onPrimary = Palette.Deep,
    primaryContainer = Palette.Person,
    onPrimaryContainer = Palette.Text,
    secondary = Palette.Teal2,
    onSecondary = Palette.Deep,
    tertiary = Palette.Teal2,
    onTertiary = Palette.Deep,
    background = Palette.Deep,
    onBackground = Palette.Text,
    surface = Palette.Deep,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Surface,
    onSurfaceVariant = Palette.Muted,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface2,
    surfaceContainerHighest = Palette.Surface2,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ChatColorScheme, content = content)
}
