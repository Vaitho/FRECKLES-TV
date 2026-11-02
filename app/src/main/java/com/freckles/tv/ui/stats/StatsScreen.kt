package com.freckles.tv.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.freckles.tv.data.remote.posterUrl
import com.freckles.tv.ui.common.LocalRepo
import com.freckles.tv.util.formatDuration
import com.freckles.tv.util.genreTotals

@Composable
fun StatsScreen(onOpenShow: (Long) -> Unit) {
    val repo = LocalRepo.current
    val episodes by repo.watchedEpisodes.collectAsStateWithLifecycle(0)
    val movies by repo.watchedMovies.collectAsStateWithLifecycle(0)
    val gamesDone by repo.finishedGames.collectAsStateWithLifecycle(0)
    val minutes by repo.totalMinutes.collectAsStateWithLifecycle(0L)
    val monthly by repo.monthly.collectAsStateWithLifecycle(emptyList())
    val yearly by repo.yearly.collectAsStateWithLifecycle(emptyList())
    val byShow by repo.minutesByShow.collectAsStateWithLifecycle(emptyList())
    val shows by repo.allShows.collectAsStateWithLifecycle(emptyList())
    val movieList by repo.allMovies.collectAsStateWithLifecycle(emptyList())
    val games by repo.allGames.collectAsStateWithLifecycle(emptyList())

    val showById = shows.associateBy { it.id }
    val topShows = byShow.mapNotNull { m -> showById[m.showId]?.let { it to m.minutes } }.take(5)
    val genres = genreTotals(
        byShow.mapNotNull { m -> showById[m.showId]?.let { it.genres to m.minutes } } +
            movieList.filter { it.watchCount > 0 }.map { it.genres to (it.runtime.toLong() * it.watchCount) } +
            games.map { it.genres to it.minutesPlayed.toLong() }
    )

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Scheda principale con gradiente d'accento
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
            ).padding(24.dp)
        ) {
            Column {
                Text("Tempo totale tra schermo e controller", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                Text(
                    formatDuration(minutes), color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text("${minutes / 60} ore in totale", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Episodi", episodes.toString(), Modifier.weight(1f))
            StatCard("Film", movies.toString(), Modifier.weight(1f))
            StatCard("Giochi finiti", gamesDone.toString(), Modifier.weight(1f))
        }

        if (topShows.isNotEmpty()) {
            SectionTitle("Serie più viste")
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    topShows.forEachIndexed { i, (show, min) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenShow(show.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                            AsyncImage(
                                posterUrl(show.posterPath), null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(40.dp, 60.dp).clip(RoundedCornerShape(8.dp)),
                            )
                            Text(
                                show.title, Modifier.weight(1f).padding(horizontal = 12.dp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
                            )
                            Text("${min / 60} h", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (genres.isNotEmpty()) {
            SectionTitle("Generi preferiti")
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                val max = genres.first().second.coerceAtLeast(1)
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    genres.forEach { (name, min) ->
                        val frac by animateFloatAsState(min.toFloat() / max, tween(700), label = "genre")
                        Column {
                            Row {
                                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text("${min / 60} h", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(
                                Modifier.padding(top = 4.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                Box(
                                    Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(4.dp)).background(
                                        Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (yearly.isNotEmpty()) {
            SectionTitle("Per anno (ore di serie e film)")
            BarChart(yearly.map { it.year to it.minutes })
            Text(
                "Le visioni importate senza data reale finiscono nell'anno dell'import.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionTitle("Ultimi mesi (ore di serie TV)")
        if (monthly.isEmpty()) {
            Text("Nessun dato con data di visione.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            BarChart(monthly.reversed().map { it.month.takeLast(2) to it.minutes })
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

/** Barre verticali animate; `data` = (etichetta, minuti). */
@Composable
private fun BarChart(data: List<Pair<String, Long>>) {
    val max = data.maxOf { it.second }.coerceAtLeast(1)
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().height(190.dp).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            data.forEach { (label, value) ->
                val h by animateFloatAsState(110f * value / max, tween(700), label = "bar")
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${value / 60}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Box(
                        Modifier.fillMaxWidth().height(h.dp.coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
                    )
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}
