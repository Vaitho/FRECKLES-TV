package com.freckles.tv.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.freckles.tv.data.remote.NightEpisode
import com.freckles.tv.data.remote.NightProgress
import com.freckles.tv.ui.common.ExpandableText
import com.freckles.tv.ui.common.InfoChips
import com.freckles.tv.ui.common.SolidProgress
import com.freckles.tv.ui.common.shimmerBrush
import com.freckles.tv.ui.detail.DetailHeader
import kotlinx.coroutines.launch
import java.util.Locale

private fun ratingChip(r: Double?) = r?.let { "★ %.1f".format(Locale.ROOT, it) }

@Composable
private fun PlayButton(text: String, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().height(52.dp)) {
        Icon(Icons.Filled.PlayArrow, null)
        Text(text, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.titleSmall)
    }
}

/** Scheda di un film del server: dettagli, versioni, Riproduci / Riprendi, Dall'inizio e Visto. */
@Composable
fun WatchMovieScreen(data: NightData, id: String, onBack: () -> Unit, onPlay: (fileId: String, fromStart: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(data) { data.refresh() }
    val movie = data.movies?.firstOrNull { it.id == id } ?: run { Box(Modifier.fillMaxSize()); return }
    var chosen by rememberSaveable { mutableStateOf(movie.versions.first().id) }
    val version = movie.versions.firstOrNull { it.id == chosen } ?: movie.versions.first()
    val p = data.progress[version.id]
    val watched = movie.versions.any { data.progress[it.id]?.watched == true }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DetailHeader(movie.backdrop ?: movie.image, movie.title, onBack)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            InfoChips(listOfNotNull(movie.year?.toString(), movie.runtimeMin?.let(::fmtMin), version.label, ratingChip(movie.rating)) + movie.genres.take(3))
            if (p.resumable && !p!!.watched) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SolidProgress(p.pos / p.dur.toFloat(), Modifier.fillMaxWidth().height(6.dp))
                    Text("Mancano ${fmtMin((p.dur - p.pos) / 60)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val resume = p.resumable && p?.watched != true
            PlayButton(if (resume) "Riprendi da ${fmtTime(p!!.pos)}" else "Riproduci") { onPlay(version.id, false) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (resume) OutlinedButton({ onPlay(version.id, true) }, Modifier.weight(1f)) {
                    Icon(Icons.Filled.Replay, null, Modifier.size(18.dp)); Text("  Dall'inizio")
                }
                OutlinedButton(
                    { scope.launch { data.setWatched(if (watched) movie.versions.map { it.id } else listOf(version.id), !watched) } },
                    Modifier.weight(1f),
                ) {
                    if (watched) Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                    Text(if (watched) "  Visto" else "Segna come visto")
                }
            }
            if (movie.versions.size > 1) {
                Text("Versione", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(movie.versions) { v ->
                        FilterChip(v.id == version.id, { chosen = v.id }, { Text("${v.label} · ${"%.1f".format(Locale.ROOT, v.sizeBytes / 1e9)} GB") })
                    }
                }
            }
            if (!movie.overview.isNullOrBlank()) ExpandableText(movie.overview)
        }
        Spacer(Modifier.navigationBarsPadding().height(32.dp))
    }
}

/** Scheda di una serie del server: dettagli, tasto per continuare, stagioni ed episodi. */
@Composable
fun WatchSeriesScreen(data: NightData, id: String, onBack: () -> Unit, onPlay: (fileId: String) -> Unit) {
    LaunchedEffect(data) { data.refresh() }
    val series = data.series.firstOrNull { it.id == id } ?: run { Box(Modifier.fillMaxSize()); return }
    val target = seriesTarget(series, data.progress)
    var season by rememberSaveable { mutableStateOf(target?.season ?: series.seasons.firstOrNull()?.n ?: 1) }
    val regular = series.seasons.filter { it.n > 0 }.ifEmpty { series.seasons }.flatMap { it.episodes }
    val seen = regular.count { data.progress[it.id]?.watched == true }
    val nSeasons = series.seasons.count { it.n > 0 }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { DetailHeader(series.backdrop ?: series.image, series.title, onBack) }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                InfoChips(
                    listOfNotNull(
                        series.year?.toString(), "$nSeasons stagion${if (nSeasons == 1) "e" else "i"}", "${regular.size} episodi", ratingChip(series.rating),
                    ) + series.genres.take(3),
                )
                if (seen > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$seen / ${regular.size} episodi visti", style = MaterialTheme.typography.titleSmall)
                        SolidProgress(seen / regular.size.toFloat(), Modifier.fillMaxWidth().height(6.dp))
                    }
                }
                target?.let { t -> PlayButton(t.label) { onPlay(t.episode.id) } }
                if (!series.overview.isNullOrBlank()) ExpandableText(series.overview)
            }
        }
        item {
            LazyRow(
                Modifier.padding(top = 18.dp, bottom = 6.dp),
                contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(series.seasons) { FilterChip(season == it.n, { season = it.n }, { Text(if (it.n == 0) "Speciali" else "Stagione ${it.n}") }) }
            }
        }
        items(series.seasons.firstOrNull { it.n == season }?.episodes.orEmpty(), key = { it.id }) { e ->
            EpisodeRow(e, data.progress[e.id], highlighted = e.id == target?.episode?.id) { onPlay(e.id) }
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun EpisodeRow(e: NightEpisode, p: NightProgress?, highlighted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 128.dp, height = 72.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            SubcomposeAsyncImage(
                e.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                loading = { Box(Modifier.fillMaxSize().background(shimmerBrush())) },
            )
            if (p?.watched == true) Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { WatchedBadge(22.dp) }
            if (p.resumable && p?.watched != true) {
                SolidProgress(p!!.pos / p.dur.toFloat(), Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp).height(3.dp), trackColor = androidx.compose.ui.graphics.Color(0x44FFFFFF))
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${e.num}. ${e.title.ifEmpty { "Episodio ${e.num}" }}", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(e.runtimeMin?.let(::fmtMin), if (p?.watched == true) "Visto" else null).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!e.overview.isNullOrBlank()) {
                Text(e.overview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
