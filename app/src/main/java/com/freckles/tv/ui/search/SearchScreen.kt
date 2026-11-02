package com.freckles.tv.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.freckles.tv.data.db.GameStatus
import com.freckles.tv.data.remote.posterUrl
import com.freckles.tv.ui.common.AddMenu
import com.freckles.tv.ui.common.LocalRepo

@Composable
fun SearchScreen(
    onOpenShow: (Long) -> Unit,
    onOpenMovie: (Long) -> Unit,
    onOpenPreview: (String, Int) -> Unit,
    onOpenGame: (Long) -> Unit,
    onOpenGamePreview: (String) -> Unit,
) {
    val repo = LocalRepo.current
    val vm: SearchViewModel = viewModel(factory = viewModelFactory { initializer { SearchViewModel(repo) } })
    val query by vm.query.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val pending by repo.pending.collectAsStateWithLifecycle()
    val shows by repo.allShows.collectAsStateWithLifecycle(emptyList())
    val movies by repo.allMovies.collectAsStateWithLifecycle(emptyList())
    val games by repo.allGames.collectAsStateWithLifecycle(emptyList())

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = vm::onQuery,
            placeholder = { Text(if (mode == SearchMode.Media) "Cerca serie e film" else "Cerca videogiochi") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(mode == SearchMode.Media, { vm.onMode(SearchMode.Media) }, { Text("Film e serie") })
            FilterChip(mode == SearchMode.Games, { vm.onMode(SearchMode.Games) }, { Text("Videogiochi") })
        }
        Box(Modifier.fillMaxSize()) {
            val empty = if (mode == SearchMode.Media) state.results.isEmpty() else state.games.isEmpty()
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    androidx.compose.material3.OutlinedButton(onClick = vm::retry) { Text("Riprova") }
                }
                query.trim().length >= 2 && empty -> Text(
                    "Nessun risultato", Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                mode == SearchMode.Media -> LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.results, key = { "${it.mediaType}${it.id}" }) { item ->
                        val showId = if (item.mediaType == "tv") shows.firstOrNull { it.tmdbId == item.id }?.id else null
                        val movieId = if (item.mediaType == "movie") movies.firstOrNull { it.tmdbId == item.id }?.id else null
                        val inLibrary = showId != null || movieId != null
                        ResultRow(
                            image = posterUrl(item.posterPath),
                            title = item.displayTitle,
                            subtitle = (if (item.mediaType == "tv") "Serie TV" else "Film") + (item.year?.let { " · $it" } ?: ""),
                            onClick = {
                                if (showId != null) onOpenShow(showId)
                                else if (movieId != null) onOpenMovie(movieId)
                                else onOpenPreview(item.mediaType, item.id)
                            },
                        ) {
                            if (repo.pendingKey(item) in pending) {
                                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 3.dp)
                            } else if (inLibrary) {
                                Icon(Icons.Filled.CheckCircle, "Già in libreria", tint = MaterialTheme.colorScheme.primary)
                            } else {
                                var menu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.Add, "Aggiungi") }
                                    AddMenu(menu, { menu = false }, item.mediaType == "movie") { repo.add(item, it) }
                                }
                            }
                        }
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.gameSource == "steam") item(key = "steam-note") {
                        Text(
                            "Risultati da Steam: la fonte principale non risponde in questo momento (solo giochi PC).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(state.games, key = { it.extId }) { g ->
                        val libId = games.firstOrNull { it.extId == g.extId }?.id
                        ResultRow(
                            image = g.imageUrl,
                            title = g.title,
                            subtitle = listOfNotNull(g.year, g.platforms).joinToString(" · "),
                            onClick = { if (libId != null) onOpenGame(libId) else onOpenGamePreview(g.extId) },
                        ) {
                            var menu by remember { mutableStateOf(false) }
                            if (repo.gamePendingKey(g.extId) in pending) {
                                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 3.dp)
                            } else if (libId != null) {
                                Icon(Icons.Filled.CheckCircle, "Già in libreria", tint = MaterialTheme.colorScheme.primary)
                            } else {
                                Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.Add, "Aggiungi") }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        listOf(GameStatus.BACKLOG, GameStatus.PLAYING, GameStatus.FINISHED).forEach { s ->
                                            DropdownMenuItem(
                                                text = { Text(GameStatus.label(s)) },
                                                onClick = { menu = false; repo.addGame(g.extId, s) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    image: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = image, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp, 96.dp).clip(RoundedCornerShape(10.dp)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}
