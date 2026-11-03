package com.freckles.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.freckles.tv.data.remote.PersonDetails
import com.freckles.tv.data.remote.profileUrl
import com.freckles.tv.ui.common.ExpandableText
import com.freckles.tv.ui.common.LocalRepo
import com.freckles.tv.ui.common.PosterCard

// Talk show e notiziari: apparizioni come ospite che riempirebbero la filmografia senza dire molto.
private val skippedGenres = setOf(10767, 10763)

/** Pagina di un attore: foto, biografia e filmografia (serie e film), dal più recente. */
@Composable
fun PersonScreen(
    personId: Int,
    onBack: () -> Unit,
    onOpenShow: (Long) -> Unit,
    onOpenMovie: (Long) -> Unit,
    onOpenPreview: (String, Int) -> Unit,
) {
    val repo = LocalRepo.current
    val person by produceState<Result<PersonDetails>?>(null, personId) {
        value = runCatching { repo.person(personId) }
    }
    val shows by repo.allShows.collectAsStateWithLifecycle(emptyList())
    val movies by repo.allMovies.collectAsStateWithLifecycle(emptyList())

    val result = person
    if (result == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val p = result.getOrNull() ?: run {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Impossibile caricare l'attore", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onBack, Modifier.padding(top = 16.dp)) { Text("Indietro") }
        }
        return
    }

    val credits = remember(p) {
        p.combinedCredits.cast
            .filter { it.mediaType == "tv" || it.mediaType == "movie" }
            .filter { c -> c.genreIds.none { it in skippedGenres } }
            .distinctBy { it.mediaType to it.id }
            .sortedWith(compareBy<com.freckles.tv.data.remote.PersonCredit> { it.date.isNullOrBlank() }.thenByDescending { it.date })
    }
    val showIds = remember(shows) { shows.associate { it.tmdbId to it.id } }
    val movieIds = remember(movies) { movies.associate { it.tmdbId to it.id } }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack, Modifier.padding(top = 4.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        profileUrl(p.profilePath), p.name, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(104.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        val life = listOfNotNull(p.birthday?.take(4), p.deathday?.take(4)).joinToString(" – ")
                        listOfNotNull(life.ifBlank { null }, p.placeOfBirth).forEach {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (!p.biography.isNullOrBlank()) {
                    ExpandableText(p.biography, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "Filmografia (${credits.size})", Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        items(credits, key = { "${it.mediaType}${it.id}" }) { c ->
            val libId = if (c.mediaType == "tv") showIds[c.id] else movieIds[c.id]
            PosterCard(
                title = c.displayTitle,
                posterPath = c.posterPath,
                subtitle = listOfNotNull(
                    c.date?.take(4)?.ifBlank { null },
                    c.character?.ifBlank { null },
                    if (libId != null) "In libreria" else null,
                ).joinToString(" · ").ifBlank { null },
                onClick = {
                    when {
                        libId == null -> onOpenPreview(c.mediaType, c.id)
                        c.mediaType == "tv" -> onOpenShow(libId)
                        else -> onOpenMovie(libId)
                    }
                },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.navigationBarsPadding()) }
    }
}
