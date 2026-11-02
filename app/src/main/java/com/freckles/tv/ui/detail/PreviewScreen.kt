package com.freckles.tv.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freckles.tv.data.PreviewInfo
import com.freckles.tv.data.remote.SearchItem
import com.freckles.tv.data.remote.backdropUrl
import com.freckles.tv.ui.common.LocalRepo

/** Anteprima di un titolo non ancora in libreria, con i pulsanti per aggiungerlo. */
@Composable
fun PreviewScreen(
    type: String,
    tmdbId: Int,
    onBack: () -> Unit,
    onAdded: (isShow: Boolean, id: Long) -> Unit,
) {
    val repo = LocalRepo.current
    val uri = LocalUriHandler.current
    val isShow = type == "tv"
    val info by produceState<Result<PreviewInfo>?>(null, type, tmdbId) {
        value = runCatching { repo.preview(type, tmdbId) }
    }
    val shows by repo.allShows.collectAsStateWithLifecycle(emptyList())
    val movies by repo.allMovies.collectAsStateWithLifecycle(emptyList())
    val pending by repo.pending.collectAsStateWithLifecycle()
    val item = SearchItem(id = tmdbId, mediaType = type)
    val adding = repo.pendingKey(item) in pending
    var requested by remember { mutableStateOf(false) }

    // Appena il titolo compare in libreria, passo alla sua pagina.
    val libId = if (isShow) shows.firstOrNull { it.tmdbId == tmdbId }?.id else movies.firstOrNull { it.tmdbId == tmdbId }?.id
    LaunchedEffect(libId, requested) { if (libId != null && requested) onAdded(isShow, libId) }

    val result = info
    when {
        result == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        result.isFailure -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Impossibile caricare il titolo", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onBack, Modifier.padding(top = 16.dp)) { Text("Indietro") }
        }
        else -> {
            val d = result.getOrThrow()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                DetailHeader(backdropUrl(d.backdropPath ?: d.posterPath), d.title, onBack)
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (d.meta.isNotBlank()) Text(d.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { requested = true; repo.add(item, false) },
                            enabled = !adding && !requested,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Bookmark, null, Modifier.size(18.dp))
                            Text("  Da vedere")
                        }
                        FilledTonalButton(
                            onClick = { requested = true; repo.add(item, true) },
                            enabled = !adding && !requested,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                            Text(if (isShow) "  Già vista" else "  Già visto")
                        }
                    }
                    if (adding || requested) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("  Aggiunta in corso…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (d.imdbId != null) {
                        OutlinedButton(onClick = { uri.openUri("https://www.imdb.com/title/${d.imdbId}") }, Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                            Text("  Apri su IMDb")
                        }
                    }
                    if (!d.overview.isNullOrBlank()) Text(d.overview)
                    Spacer(Modifier.navigationBarsPadding().height(32.dp))
                }
            }
        }
    }
}
