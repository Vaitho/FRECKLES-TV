package com.freckles.tv.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class Accent(val id: String, val label: String, val color: Color)

val Accents = listOf(
    Accent("violet", "Viola", Color(0xFF7C4DFF)),
    Accent("coral", "Corallo", Color(0xFFFF6B6B)),
    Accent("ocean", "Oceano", Color(0xFF1E88E5)),
    Accent("teal", "Smeraldo", Color(0xFF00BFA5)),
    Accent("green", "Foresta", Color(0xFF43A047)),
    Accent("amber", "Ambra", Color(0xFFFFB300)),
    Accent("rose", "Rosa", Color(0xFFEC407A)),
)

const val DYNAMIC = "dynamic"

fun dynamicAvailable() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private fun Color.shiftHue(deg: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()), hsv)
    hsv[0] = (hsv[0] + deg) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)

/** Genera una palette coerente a partire da un solo colore d'accento. */
fun schemeFor(accent: Color, dark: Boolean): ColorScheme {
    val secondary = accent.shiftHue(35f)
    return if (dark) {
        val base = Color(0xFF0F0E14)
        val primary = mix(accent, Color.White, 0.35f)
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF14101F),
            primaryContainer = mix(accent, Color.Black, 0.55f),
            onPrimaryContainer = mix(accent, Color.White, 0.85f),
            secondary = mix(secondary, Color.White, 0.3f),
            onSecondary = Color(0xFF14101F),
            secondaryContainer = mix(base, accent, 0.28f),
            onSecondaryContainer = mix(accent, Color.White, 0.85f),
            background = base,
            onBackground = Color(0xFFE9E6F0),
            surface = mix(base, accent, 0.05f),
            onSurface = Color(0xFFE9E6F0),
            surfaceVariant = mix(base, accent, 0.14f),
            onSurfaceVariant = Color(0xFFB9B4C7),
            surfaceContainerLowest = base,
            surfaceContainerLow = mix(base, accent, 0.04f),
            surfaceContainer = mix(base, accent, 0.08f),
            surfaceContainerHigh = mix(base, accent, 0.12f),
            surfaceContainerHighest = mix(base, accent, 0.16f),
            outline = mix(base, Color.White, 0.4f),
            outlineVariant = mix(base, accent, 0.25f),
            error = Color(0xFFFF8A80),
        )
    } else {
        val primary = mix(accent, Color.Black, 0.12f)
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = mix(accent, Color.White, 0.82f),
            onPrimaryContainer = mix(accent, Color.Black, 0.65f),
            secondary = mix(secondary, Color.Black, 0.1f),
            onSecondary = Color.White,
            secondaryContainer = mix(accent, Color.White, 0.85f),
            onSecondaryContainer = mix(accent, Color.Black, 0.65f),
            background = mix(Color.White, accent, 0.04f),
            onBackground = Color(0xFF1B1A22),
            surface = mix(Color.White, accent, 0.03f),
            onSurface = Color(0xFF1B1A22),
            surfaceVariant = mix(Color.White, accent, 0.12f),
            onSurfaceVariant = Color(0xFF4A4756),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = mix(Color.White, accent, 0.04f),
            surfaceContainer = mix(Color.White, accent, 0.07f),
            surfaceContainerHigh = mix(Color.White, accent, 0.1f),
            surfaceContainerHighest = mix(Color.White, accent, 0.14f),
            outline = Color(0xFF7A7685),
            outlineVariant = mix(Color.White, accent, 0.25f),
        )
    }
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun FreckleTheme(dark: Boolean, accentId: String, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (accentId == DYNAMIC && dynamicAvailable()) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        schemeFor((Accents.firstOrNull { it.id == accentId } ?: Accents.first()).color, dark)
    }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
}

