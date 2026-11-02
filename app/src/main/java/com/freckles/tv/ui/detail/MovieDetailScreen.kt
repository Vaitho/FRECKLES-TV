package com.freckles.tv.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freckles.tv.data.remote.backdropUrl
import com.freckles.tv.ui.common.LocalRepo
import com.freckles.tv.ui.common.StarRating

@Composable
fun MovieDetailScreen(id: Long, onBack: () -> Unit) {
    val repo = LocalRepo.current
    val movie by remember(id) { repo.observeMovie(id) }.collectAsStateWithLifecycle(null)
    val uri = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current
    var confirmDelete by remember { mutableStateOf(false) }

    val m = movie ?: run { Box(Modifier.fillMaxSize()); return }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DetailHeader(backdropUrl(m.backdropPath ?: m.posterPath), m.title, onBack)
        DetailActions(
            notify = null,
            favorite = m.isFavorite,
            onNotify = {},
            onFavorite = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); repo.setMovieFavorite(m) },
            onImdb = m.imdbId?.let { { uri.openUri("https://www.imdb.com/title/$it") } },
            onDelete = { confirmDelete = true },
        )
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                listOfNotNull(m.releaseDate?.take(4), "${m.runtime} min").joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); repo.toggleMovieWatched(m) }, Modifier.fillMaxWidth()) {
                if (m.watched) Icon(Icons.Filled.Check, null)
                Text(if (m.watched) "Visto – tocca per annullare" else "Segna come visto")
            }
            Text("Il tuo voto", style = MaterialTheme.typography.titleSmall)
            StarRating(m.rating, { repo.setMovieRating(m, it) })
            if (!m.overview.isNullOrBlank()) Text(m.overview)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.navigationBarsPadding().height(32.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Rimuovere ${m.title}?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; repo.deleteMovie(m.id); onBack() }) { Text("Rimuovi") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }
}
