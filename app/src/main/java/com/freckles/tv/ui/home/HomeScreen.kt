package com.freckles.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freckles.tv.data.prefs.Profile
import com.freckles.tv.data.remote.SearchItem
import com.freckles.tv.ui.common.AddMenu
import com.freckles.tv.ui.common.CornerButton
import com.freckles.tv.ui.common.LocalRepo
import com.freckles.tv.ui.common.PosterCard
import java.time.LocalTime

@Composable
fun HomeScreen(profile: Profile, onOpenShow: (Long) -> Unit, onOpenMovie: (Long) -> Unit) {
    val repo = LocalRepo.current
    val shows by repo.allShows.collectAsStateWithLifecycle(emptyList())
    val movies by repo.allMovies.collectAsStateWithLifecycle(emptyList())
    val next by remember { repo.observeNext() }.collectAsStateWithLifecycle(emptyList())
    val trending by produceState<List<SearchItem>>(emptyList()) {
        value = runCatching { repo.trending() }.getOrDefault(emptyList())
    }
    val showById = shows.associateBy { it.id }
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 6 -> "Buonanotte"
        hour < 13 -> "Buongiorno"
        hour < 18 -> "Buon pomeriggio"
        else -> "Buonasera"
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(greeting, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(profile.name ?: "", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
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
                                modifier = Modifier.width(150.dp),
                                corner = { CornerButton(Icons.Filled.Check, "Segna come visto") { repo.toggleEpisode(ep) } },
                                onClick = { onOpenShow(show.id) },
                            )
                        }
                    }
                }
            }
        }

        val favs = profile.favorites.mapNotNull { key ->
            val id = key.drop(2).toLongOrNull() ?: return@mapNotNull null
            when {
                key.startsWith("s:") -> showById[id]?.let { Triple(key, it.title, it.posterPath) to { onOpenShow(id) } }
                key.startsWith("m:") -> movies.firstOrNull { it.id == id }?.let { Triple(key, it.title, it.posterPath) to { onOpenMovie(id) } }
                else -> null
            }
        }
        if (favs.isNotEmpty()) {
            item {
                Section("I tuoi preferiti") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(favs, key = { it.first.first }) { (info, open) ->
                            PosterCard(title = info.second, posterPath = info.third, modifier = Modifier.width(120.dp), onClick = open)
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
                        var menu by remember { mutableStateOf(false) }
                        PosterCard(
                            title = item.displayTitle,
                            posterPath = item.posterPath,
                            subtitle = item.year,
                            modifier = Modifier.width(130.dp),
                            corner = {
                                if (!inLibrary) Box {
                                    CornerButton(Icons.Filled.Add, "Aggiungi") { menu = true }
                                    AddMenu(menu, { menu = false }, item.mediaType == "movie") { repo.add(item, it) }
                                }
                            },
                            onClick = {
                                if (showId != null) onOpenShow(showId)
                                else if (movieId != null) onOpenMovie(movieId)
                                else menu = true
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
    Column {
        Text(
            title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}
