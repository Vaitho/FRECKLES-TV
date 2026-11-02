package com.freckles.tv.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.freckles.tv.data.Repository
import com.freckles.tv.data.remote.posterUrl

val LocalRepo = staticCompositionLocalOf<Repository> { error("Repository non fornito") }

/** Pennellata animata per i segnaposto di caricamento. */
@Composable
fun shimmerBrush(): Brush {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -400f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "x",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val hi = MaterialTheme.colorScheme.outlineVariant
    return Brush.linearGradient(listOf(base, hi, base), start = Offset(x, 0f), end = Offset(x + 400f, 400f))
}

/** Poster con titolo in sovrimpressione, barra di avanzamento e azione d'angolo opzionali. */
@Composable
fun PosterCard(
    title: String,
    posterPath: String?,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    subtitle: String? = null,
    progress: Float? = null,
    corner: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
    ) {
        SubcomposeAsyncImage(
            model = imageUrl ?: posterUrl(posterPath),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { Box(Modifier.fillMaxSize().background(shimmerBrush())) },
            error = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)), startY = 220f))
        )
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(
                title, color = Color.White, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, color = Color(0xCCFFFFFF), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    trackColor = Color(0x44FFFFFF),
                )
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { corner() }
    }
}

/** Piccolo bottone tondo semitrasparente da mettere sopra un poster. */
@Composable
fun CornerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0x99000000))
            .border(1.dp, Color(0x55FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

/** Spunta circolare animata (sostituisce la checkbox). */
@Composable
fun CheckCircle(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val fill by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else Color.Transparent, tween(180), label = "fill",
    )
    val border by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, tween(180), label = "border",
    )
    val scale by animateFloatAsState(
        if (checked) 1f else 0.92f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "scale",
    )
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(fill)
                .border(2.dp, border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}



/** Menu "aggiungi": da vedere oppure già visto. */
@Composable
fun AddMenu(expanded: Boolean, onDismiss: () -> Unit, isMovie: Boolean, onAdd: (watched: Boolean) -> Unit) {
    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        androidx.compose.material3.DropdownMenuItem(
            text = { Text("Da vedere") },
            leadingIcon = { Icon(Icons.Filled.Bookmark, null) },
            onClick = { onDismiss(); onAdd(false) },
        )
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(if (isMovie) "Già visto" else "Già vista (episodi usciti)") },
            leadingIcon = { Icon(Icons.Filled.Check, null) },
            onClick = { onDismiss(); onAdd(true) },
        )
    }
}
