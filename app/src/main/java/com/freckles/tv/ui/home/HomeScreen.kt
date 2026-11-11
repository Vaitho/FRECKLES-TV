package com.freckles.tv.ui.home

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
fun HomeScreen(profile: Profile, onOpenShow: (Long) -> Unit, onOpenMovie: (Long) -> Unit, onOpenPreview: (String, Int) -> Unit, onOpenGame: (Long) -> Unit) {
    val repo = LocalRepo.current
    val shows by repo.allShows.collectAsStateWithLifecycle(emptyList())
    val movies by repo.allMovies.collectAsStateWithLifecycle(emptyList())
    val games by repo.allGames.collectAsStateWithLifecycle(emptyList())
    val next by remember { repo.observeNext() }.collectAsStateWithLifecycle(emptyList())
    val trending by produceState<List<SearchItem>>(emptyList()) {
        value = runCatching { repo.trending() }.getOrDefault(emptyList())
    }
    val upcoming by repo.upcoming.collectAsStateWithLifecycle(emptyList())
    val pending by repo.pending.collectAsStateWithLifecycle()
    val showById = remember(shows) { shows.associateBy { it.id } }
    val thisYear = remember { java.time.LocalDate.now().year }
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 6 -> "Buonanotte"
        hour < 13 -> "Buongiorno"
        hour < 18 -> "Buon pomeriggio"
        else -> "Buonasera"
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("$greeting, ") }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(profile.name ?: "") }
                },
                Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.headlineSmall,
            )
        }
        if (upcoming.isNotEmpty()) item {
            Section("In uscita") {
                Surface(
                    Modifier.padding(horizontal = 20.dp), shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        upcoming.forEach { ep ->
                            val show = showById[ep.showId] ?: return@forEach
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenShow(show.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.width(64.dp)) {
                                    Text(airDay(ep.airDate), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(airMonth(ep.airDate, thisYear), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(show.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                    Text(
                                        "S${ep.season}E${ep.number}" + (ep.name?.let { " · $it" } ?: ""),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
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
                Section("I tuoi film e serie preferiti") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(favs, key = { it.first.first }) { (info, open) ->
                            PosterCard(title = info.second, posterPath = info.third, modifier = Modifier.width(120.dp), onClick = open)
                        }
                    }
                }
            }
        }

        val gameFavs = profile.gameFavorites.mapNotNull { key ->
            val id = key.drop(2).toLongOrNull() ?: return@mapNotNull null
            games.firstOrNull { it.id == id }
        }
        if (gameFavs.isNotEmpty()) {
            item {
                Section("I tuoi videogiochi preferiti") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(gameFavs, key = { it.id }) { g ->
                            PosterCard(
                                title = g.title, posterPath = null, imageUrl = g.imageUrl,
                                modifier = Modifier.width(120.dp), onClick = { onOpenGame(g.id) },
                            )
                        }
                    }
                }
            }
        }

        if (trending.isNotEmpty()) item {
            Section("Di tendenza") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(trending, key = { "${it.mediaType}${it.id}" }) { item ->
                        val showId = shows.firstOrNull { it.tmdbId == item.id }?.id.takeIf { item.mediaType == "tv" }
                        val movieId = movies.firstOrNull { it.tmdbId == item.id }?.id.takeIf { item.mediaType == "movie" }
                        val inLibrary = showId != null || movieId != null
                        var menu by remember { mutableStateOf(false) }
                        val adding = repo.pendingKey(item) in pending
                        PosterCard(
                            title = item.displayTitle,
                            posterPath = item.posterPath,
                            subtitle = item.year,
                            modifier = Modifier.width(130.dp),
                            corner = {
                                if (adding) Box(Modifier.size(36.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                                } else if (!inLibrary) Box {
                                    CornerButton(Icons.Filled.Add, "Aggiungi") { menu = true }
                                    AddMenu(menu, { menu = false }, item.mediaType == "movie") { repo.add(item, it) }
                                }
                            },
                            onClick = {
                                if (showId != null) onOpenShow(showId)
                                else if (movieId != null) onOpenMovie(movieId)
                                else if (!adding) onOpenPreview(item.mediaType, item.id)
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

private val shortMonths = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")

/** "2026-11-14" -> "14", "nov". */
private fun airDay(date: String?) = date?.takeLast(2)?.trimStart('0').orEmpty()
private fun airMonth(date: String?, thisYear: Int): String {
    val month = date?.substring(5, 7)?.toIntOrNull()?.let { shortMonths.getOrNull(it - 1) }.orEmpty()
    val year = date?.take(4)?.toIntOrNull()
    return if (year != null && year != thisYear) "$month $year" else month
}
