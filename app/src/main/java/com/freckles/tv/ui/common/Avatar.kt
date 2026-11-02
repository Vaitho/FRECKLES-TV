package com.freckles.tv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.freckles.tv.data.prefs.Profile
import java.io.File

val AvatarColors = listOf(
    Color(0xFF7C4DFF), Color(0xFFFF6B6B), Color(0xFF1E88E5), Color(0xFF00BFA5),
    Color(0xFF43A047), Color(0xFFFFB300), Color(0xFFEC407A), Color(0xFF546E7A),
)

val AvatarEmojis = listOf(
    "🍿", "🎬", "📺", "🎞️", "🦊", "🐼", "🐱", "🐸", "🦄", "👽",
    "🤖", "👻", "🧙", "🦸", "🧛", "🐉", "🌙", "🔥", "🎧", "🌈",
)

@Composable
fun Avatar(profile: Profile, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val base = modifier
        .size(size)
        .clip(CircleShape)
        .background(AvatarColors[profile.colorIdx.coerceIn(0, AvatarColors.lastIndex)])
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Box(base, contentAlignment = Alignment.Center) {
        val photo = profile.photoPath?.let(::File)?.takeIf { it.exists() }
        if (photo != null) {
            AsyncImage(
                photo, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(size * 0.06f).clip(CircleShape),
            )
        } else {
            Text(profile.emoji, fontSize = emojiSize(size.value))
        }
    }
}

private fun emojiSize(dp: Float): TextUnit = (dp * 0.5f).sp
