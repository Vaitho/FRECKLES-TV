package com.freckles.tv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.freckles.tv.data.Repository
import com.freckles.tv.data.remote.posterUrl

val LocalRepo = staticCompositionLocalOf<Repository> { error("Repository non fornito") }

/** Poster con titolo in sovrimpressione e barra di avanzamento opzionale. */
@Composable
fun PosterCard(
    title: String,
    posterPath: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    corner: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = posterUrl(posterPath),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)), startY = 250f))
        )
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(
                title, color = Color.White, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, color = Color(0xCCFFFFFF), style = MaterialTheme.typography.labelSmall)
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                )
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { corner() }
    }
}

/** Es. 3745 min -> "2 giorni 14 ore". Mese = 30 giorni. */
fun formatDuration(totalMinutes: Long): String {
    val months = totalMinutes / (60 * 24 * 30)
    val days = totalMinutes / (60 * 24) % 30
    val hours = totalMinutes / 60 % 24
    val parts = buildList {
        if (months > 0) add("$months ${if (months == 1L) "mese" else "mesi"}")
        if (days > 0) add("$days ${if (days == 1L) "giorno" else "giorni"}")
        add("$hours ${if (hours == 1L) "ora" else "ore"}")
    }
    return parts.joinToString(" ")
}
