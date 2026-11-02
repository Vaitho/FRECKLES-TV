package com.freckles.tv.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Violet = Color(0xFF7C4DFF)
val Coral = Color(0xFFFF6B6B)
val Ink = Color(0xFF0E0B16)

private val Dark = darkColorScheme(
    primary = Color(0xFFB69CFF),
    secondary = Coral,
    background = Ink,
    surface = Color(0xFF1A1626),
    surfaceVariant = Color(0xFF262033),
)

private val Light = lightColorScheme(
    primary = Violet,
    secondary = Coral,
)

@Composable
fun FreckleTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        // Palette brand di default; i colori dinamici restano opzionali (vedi Fase 4).
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
