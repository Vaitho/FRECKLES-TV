package com.freckles.tv.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import com.freckles.tv.data.remote.posterUrl
import com.freckles.tv.ui.common.AddMenu
import com.freckles.tv.ui.common.LocalRepo

@Composable
fun SearchScreen(onOpenShow: (Long) -> Unit, onOpenMovie: (Long) -> Unit, onOpenPreview: (String, Int) -> Unit) {
    val repo = LocalRepo.current
    val vm: SearchViewModel = viewModel(factory = viewModelFactory { initializer { SearchViewModel(repo) } })
    val query by vm.query.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val pending by repo.pending.collectAsStateWithLifecycle()
    val shows by repo.allShows.collectAsStateWithLifecycle(emptyList())
    val movies by repo.allMovies.collectAsStateWithLifecycle(emptyList())

    Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = vm::onQuery,
            placeholder = { Text("Cerca serie e film") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Box(Modifier.fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    state.error!!, Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                query.length >= 2 && state.results.isEmpty() -> Text(
                    "Nessun risultato", Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.results, key = { "${it.mediaType}${it.id}" }) { item ->
                        val showId = if (item.mediaType == "tv") shows.firstOrNull { it.tmdbId == item.id }?.id else null
                        val movieId = if (item.mediaType == "movie") movies.firstOrNull { it.tmdbId == item.id }?.id else null
                        val inLibrary = showId != null || movieId != null
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    if (showId != null) onOpenShow(showId)
                                    else if (movieId != null) onOpenMovie(movieId)
                                    else onOpenPreview(item.mediaType, item.id)
                                }
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = posterUrl(item.posterPath), contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp, 96.dp).clip(RoundedCornerShape(10.dp)),
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(
                                    item.displayTitle, fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    (if (item.mediaType == "tv") "Serie TV" else "Film") +
                                        (item.year?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
            }
        }
    }
}
