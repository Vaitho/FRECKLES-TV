package com.freckles.tv.ui.library

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freckles.tv.data.db.GameStatus
import com.freckles.tv.ui.common.LocalRepo
import com.freckles.tv.ui.common.PosterCard
import com.freckles.tv.ui.common.SearchField
import com.freckles.tv.ui.common.WatchedBadge
import com.freckles.tv.ui.common.ratingLabel
import com.freckles.tv.util.formatHours

private val showFilters = listOf("Tutte", "In corso", "Da iniziare", "Completate", "Preferite", "Votate")
private val movieFilters = listOf("Tutti", "Da vedere", "Visti", "Preferiti", "Votati")
private val gameFilters = listOf("Tutti", "Da giocare", "In corso", "Finiti", "Abbandonati", "Preferiti", "Votati")
private val sorts = listOf("Aggiunta", "Titolo", "Uscita", "Voto", "Ultima visione")

/** Titolo A-Z; date e visioni più recenti prima; i valori assenti sempre in fondo. */
private fun <T> List<T>.sortedFor(
    sort: Int, title: (T) -> String, added: (T) -> Long, released: (T) -> String?, rating: (T) -> Int, seen: (T) -> Long?,
): List<T> = when (sort) {
    1 -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, title))
    2 -> sortedWith(compareBy<T> { released(it) == null }.thenByDescending { released(it) })
    3 -> sortedByDescending(rating)
    4 -> sortedWith(compareBy<T> { seen(it) == null }.thenByDescending { seen(it) })
    else -> sortedByDescending(added)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenShow: (Long) -> Unit, onOpenMovie: (Long) -> Unit, onOpenGame: (Long) -> Unit, onSearch: () -> Unit) {
    val repo = LocalRepo.current
    val shows by repo.allShows.collectAsStateWithLifecycle(emptyList())
    val movies by repo.allMovies.collectAsStateWithLifecycle(emptyList())
    val games by repo.allGames.collectAsStateWithLifecycle(emptyList())
    val progress by repo.progress.collectAsStateWithLifecycle(emptyList())
    val progressById = remember(progress) { progress.associateBy { it.showId } }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val filters = when (tab) {
        0 -> showFilters
        1 -> movieFilters
        else -> gameFilters
    }

    // Il pulsante Ordina si nasconde scorrendo in giù e ricompare risalendo.
    var fabVisible by remember { mutableStateOf(true) }
    val hideOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -2f) fabVisible = false else if (available.y > 2f) fabVisible = true
                return Offset.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize().nestedScroll(hideOnScroll)) {
        Column(Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0; filter = 0 }, text = { Text("Serie (${shows.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1; filter = 0 }, text = { Text("Film (${movies.size})") })
                Tab(selected = tab == 2, onClick = { tab = 2; filter = 0 }, text = { Text("Giochi (${games.size})") })
            }
            SearchField(query, { query = it }, "Cerca nella libreria", Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filters.size) { i ->
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(filters[i]) })
                }
            }

            val q = query.trim()
            val shownShows = remember(shows, progressById, filter, sort, q) {
                shows.filter { s ->
                    val p = progressById[s.id]
                    val w = p?.watched ?: 0
                    val t = p?.total ?: 0
                    s.title.contains(q, ignoreCase = true) && when (filter) {
                        1 -> w in 1 until t
                        2 -> w == 0
                        3 -> t > 0 && w >= t
                        4 -> s.isFavorite
                        5 -> s.rating > 0
                        else -> true
                    }
                }.sortedFor(sort, { it.title }, { it.addedAt }, { progressById[it.id]?.firstAir }, { it.rating }, { progressById[it.id]?.lastWatched })
            }
            val shownMovies = remember(movies, filter, sort, q) {
                movies.filter { m ->
                    m.title.contains(q, ignoreCase = true) && when (filter) {
                        1 -> !m.watched
                        2 -> m.watched
                        3 -> m.isFavorite
                        4 -> m.rating > 0
                        else -> true
                    }
                }.sortedFor(sort, { it.title }, { it.addedAt }, { it.releaseDate?.ifBlank { null } }, { it.rating }, { it.watchedAt })
            }
            val shownGames = remember(games, filter, sort, q) {
                games.filter { g ->
                    g.title.contains(q, ignoreCase = true) && when (filter) {
                        1 -> g.status == GameStatus.BACKLOG
                        2 -> g.status == GameStatus.PLAYING
                        3 -> g.status == GameStatus.FINISHED
                        4 -> g.status == GameStatus.DROPPED
                        5 -> g.isFavorite
                        6 -> g.rating > 0
                        else -> true
                    }
                }.sortedFor(sort, { it.title }, { it.addedAt }, { it.released?.ifBlank { null } }, { it.rating }, { it.finishedAt })
            }

            if ((tab == 0 && shownShows.isEmpty()) || (tab == 1 && shownMovies.isEmpty()) || (tab == 2 && shownGames.isEmpty())) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.VideoLibrary, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("Niente da mostrare qui", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (q.isNotEmpty()) "Nessun risultato per \"$q\"." else "Cambia filtro oppure cerca qualcosa da aggiungere.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    FilledTonalButton(onClick = onSearch) { Text("Cerca titoli") }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    0 -> items(shownShows, key = { it.id }) { s ->
                        val p = progressById[s.id]
                        PosterCard(
                            title = s.title,
                            posterPath = s.posterPath,
                            subtitle = listOfNotNull(p?.let { "${it.watched}/${it.total}" }, ratingLabel(s.rating)).joinToString(" · ").ifBlank { null },
                            progress = p?.takeIf { it.total > 0 }?.let { it.watched.toFloat() / it.total },
                            onClick = { onOpenShow(s.id) },
                        )
                    }
                    1 -> items(shownMovies, key = { it.id }) { m ->
                        PosterCard(
                            title = m.title,
                            posterPath = m.posterPath,
                            subtitle = ratingLabel(m.rating),
                            corner = { if (m.watched) WatchedBadge() },
                            onClick = { onOpenMovie(m.id) },
                        )
                    }
                    else -> items(shownGames, key = { it.id }) { g ->
                        PosterCard(
                            title = g.title,
                            posterPath = null,
                            imageUrl = g.imageUrl,
                            subtitle = listOfNotNull(GameStatus.label(g.status), g.minutesPlayed.takeIf { it > 0 }?.let { formatHours(it) }, ratingLabel(g.rating)).joinToString(" · "),
                            onClick = { onOpenGame(g.id) },
                        )
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            AnimatedVisibility(fabVisible || sortMenu, enter = scaleIn(), exit = scaleOut()) {
                FloatingActionButton(onClick = { sortMenu = true }, shape = CircleShape) {
                    Icon(Icons.AutoMirrored.Filled.Sort, "Ordina: ${sorts[sort]}")
                }
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                sorts.forEachIndexed { i, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        trailingIcon = { if (i == sort) Icon(Icons.Filled.Check, null) },
                        onClick = { sort = i; sortMenu = false },
                    )
                }
            }
        }
    }
}
