package com.freckles.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freckles.tv.data.remote.SearchItem
import com.freckles.tv.ui.common.LocalRepo
import com.freckles.tv.ui.common.PosterCard

@Composable
fun HomeScreen(username: String, onOpenShow: (Long) -> Unit, onOpenMovie: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val repo = LocalRepo.current
    val shows by repo.allShows.collectAsStateWithLifecycle(emptyList())
    val movies by repo.allMovies.collectAsStateWithLifecycle(emptyList())
    val next by remember { repo.observeNext() }.collectAsStateWithLifecycle(emptyList())
    val trending by produceState<List<SearchItem>>(emptyList()) {
        value = runCatching { repo.trending() }.getOrDefault(emptyList())
    }
    val showById = shows.associateBy { it.id }

    LazyColumn(contentPadding = PaddingValues(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    "Ciao, $username", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Impostazioni") }
            }
        }
        item {
            Section("Continua a guardare") {
                if (next.isEmpty()) {
                    Text(
                        "Segna un episodio come visto e comparirà qui.",
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(next, key = { it.id }) { ep ->
                            val show = showById[ep.showId] ?: return@items
                            PosterCard(
                                title = show.title,
                                posterPath = show.posterPath,
                                subtitle = "S${ep.season}E${ep.number}" + (ep.name?.let { " · $it" } ?: ""),
                                modifier = Modifier.width(140.dp),
                                corner = {
                                    IconButton(onClick = { repo.toggleEpisode(ep) }) {
                                        Icon(Icons.Filled.Check, "Segna come visto", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = { onOpenShow(show.id) },
                            )
                        }
                    }
                }
            }
        }
        item {
            Section("Di tendenza") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(trending, key = { "${it.mediaType}${it.id}" }) { item ->
                        val showId = shows.firstOrNull { it.tmdbId == item.id }?.id.takeIf { item.mediaType == "tv" }
                        val movieId = movies.firstOrNull { it.tmdbId == item.id }?.id.takeIf { item.mediaType == "movie" }
                        val inLibrary = showId != null || movieId != null
                        PosterCard(
                            title = item.displayTitle,
                            posterPath = item.posterPath,
                            subtitle = item.year,
                            modifier = Modifier.width(140.dp),
                            corner = {
                                if (!inLibrary) IconButton(onClick = { repo.add(item) }) {
                                    Icon(Icons.Filled.Add, "Aggiungi", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                if (showId != null) onOpenShow(showId)
                                else if (movieId != null) onOpenMovie(movieId)
                                else repo.add(item)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}
