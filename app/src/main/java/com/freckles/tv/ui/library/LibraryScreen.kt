package com.freckles.tv.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freckles.tv.ui.common.LocalRepo
import com.freckles.tv.ui.common.PosterCard

private val showFilters = listOf("Tutte", "In corso", "Da iniziare", "Completate", "Preferite")
private val movieFilters = listOf("Tutti", "Da vedere", "Visti", "Preferiti")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenShow: (Long) -> Unit, onOpenMovie: (Long) -> Unit) {
    val repo = LocalRepo.current
    val shows by repo.allShows.collectAsStateWithLifecycle(emptyList())
    val movies by repo.allMovies.collectAsStateWithLifecycle(emptyList())
    val progress by repo.progress.collectAsStateWithLifecycle(emptyList())
    val progressById = progress.associateBy { it.showId }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val filters = if (tab == 0) showFilters else movieFilters

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = tab == 0, onClick = { tab = 0; filter = 0 }, text = { Text("Serie (${shows.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1; filter = 0 }, text = { Text("Film (${movies.size})") })
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filters.size) { i ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(filters[i]) })
            }
        }

        val shownShows = shows.filter { s ->
            val p = progressById[s.id]
            val w = p?.watched ?: 0
            val t = p?.total ?: 0
            when (filter) {
                1 -> w in 1 until t
                2 -> w == 0
                3 -> t > 0 && w >= t
                4 -> s.isFavorite
                else -> true
            }
        }
        val shownMovies = movies.filter { m ->
            when (filter) {
                1 -> !m.watched
                2 -> m.watched
                3 -> m.isFavorite
                else -> true
            }
        }

        if ((tab == 0 && shownShows.isEmpty()) || (tab == 1 && shownMovies.isEmpty())) {
            Text(
                "Niente da mostrare. Usa Cerca per aggiungere titoli.",
                Modifier.fillMaxWidth().padding(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (tab == 0) {
                items(shownShows, key = { it.id }) { s ->
                    val p = progressById[s.id]
                    PosterCard(
                        title = s.title,
                        posterPath = s.posterPath,
                        subtitle = p?.let { "${it.watched}/${it.total}" },
                        progress = p?.takeIf { it.total > 0 }?.let { it.watched.toFloat() / it.total },
                        onClick = { onOpenShow(s.id) },
                    )
                }
            } else {
                items(shownMovies, key = { it.id }) { m ->
                    PosterCard(
                        title = m.title,
                        posterPath = m.posterPath,
                        subtitle = if (m.watched) "Visto" else null,
                        onClick = { onOpenMovie(m.id) },
                    )
                }
            }
        }
    }
}
