package com.freckles.tv.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.freckles.tv.data.prefs.UserPrefs
import com.freckles.tv.ui.common.PosterCard
import com.freckles.tv.ui.common.SolidProgress
import com.freckles.tv.ui.common.shimmerBrush
import kotlinx.coroutines.launch

/**
 * Scheda "Guarda": film e serie del server FrecklesNight (la scheda Libreria resta per tracciare).
 * Toccando una copertina si apre la scheda del titolo; "Continua a guardare" fa partire subito il video.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    data: NightData,
    prefs: UserPrefs,
    onOpen: (key: String) -> Unit, // "m/<id>" o "s/<id>"
    onPlay: (fileId: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(data.api.base.isEmpty()) }
    var showSeries by rememberSaveable { mutableStateOf(false) }
    // Ogni volta che si torna qui (anche dal player) il progresso si aggiorna.
    LaunchedEffect(data) { data.refresh() }

    val movies = data.movies
    PullToRefreshBox(
        isRefreshing = data.loading && movies != null,
        onRefresh = { scope.launch { data.refresh() } },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            GridCells.Adaptive(108.dp),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            full {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                        SegmentedButton(!showSeries, { showSeries = false }, SegmentedButtonDefaults.itemShape(0, 2)) {
                            Text("Film" + (movies?.size?.let { " · $it" } ?: ""))
                        }
                        SegmentedButton(showSeries, { showSeries = true }, SegmentedButtonDefaults.itemShape(1, 2)) {
                            Text("Serie" + (if (movies != null) " · ${data.series.size}" else ""))
                        }
                    }
                    IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Settings, "Indirizzo del server") }
                }
            }
            when {
                movies == null && data.error != null -> full {
                    ErrorState(data.error!!, onRetry = { scope.launch { data.refresh() } }, onEdit = { editing = true })
                }
                movies == null -> items(9) {
                    Box(Modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(18.dp)).background(shimmerBrush()))
                }
                else -> {
                    val cont = data.continueItems()
                    if (cont.isNotEmpty()) {
                        full { SectionTitle("Continua a guardare") }
                        full {
                            LazyRow(
                                Modifier.bleed(20.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(cont, key = { it.fileId }) { ContinueCard(it) { onPlay(it.fileId) } }
                            }
                        }
                        full { SectionTitle(if (showSeries) "Tutte le serie" else "Tutti i film") }
                    }
                    if (showSeries) {
                        if (data.series.isEmpty()) full { EmptyText("Il server non ha trovato serie.") }
                        items(data.series, key = { it.id }) { s ->
                            val n = s.seasons.count { it.n > 0 }
                            val all = s.seasons.flatMap { it.episodes }
                            val seen = all.count { data.progress[it.id]?.watched == true }
                            PosterCard(
                                title = s.title, posterPath = null, imageUrl = s.image,
                                subtitle = if (n > 0) "$n stagion${if (n == 1) "e" else "i"}" else null,
                                progress = if (seen > 0 && seen < all.size) seen / all.size.toFloat() else null,
                                corner = { if (all.isNotEmpty() && seen == all.size) WatchedBadge() },
                                onClick = { onOpen("s/${s.id}") },
                            )
                        }
                    } else {
                        if (movies.isEmpty()) full { EmptyText("Il server non ha trovato film.") }
                        items(movies, key = { it.id }) { m ->
                            val p = m.versions.mapNotNull { data.progress[it.id] }.maxByOrNull { it.time }
                            PosterCard(
                                title = m.title, posterPath = null, imageUrl = m.image,
                                subtitle = m.year?.toString(),
                                progress = if (p.resumable && !p!!.watched) p.pos / p.dur.toFloat() else null,
                                corner = { if (m.versions.any { data.progress[it.id]?.watched == true }) WatchedBadge() },
                                onClick = { onOpen("m/${m.id}") },
                            )
                        }
                    }
                }
            }
        }
    }

    if (editing) {
        var text by remember { mutableStateOf(data.api.base) }
        AlertDialog(
            onDismissRequest = { if (data.api.base.isNotEmpty()) editing = false },
            title = { Text("Server FrecklesNight") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("L'indirizzo che il server mostra all'avvio, sul PC dove sono i film.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Es. 192.168.1.20:8787") })
                }
            },
            confirmButton = { Button({ scope.launch { prefs.setNightUrl(text); editing = false } }, enabled = text.isNotBlank()) { Text("Salva") } },
            dismissButton = { if (data.api.base.isNotEmpty()) TextButton({ editing = false }) { Text("Annulla") } },
        )
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

/** Lascia uscire una riga orizzontale fino ai bordi dello schermo, oltre il margine della griglia. */
private fun Modifier.bleed(h: Dp) = layout { measurable, c ->
    val px = h.roundToPx()
    val p = measurable.measure(c.copy(minWidth = c.minWidth + 2 * px, maxWidth = c.maxWidth + 2 * px))
    layout(p.width - 2 * px, p.height) { p.place(-px, 0) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun EmptyText(text: String) {
    Text(text, Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Spunta tonda "visto" da mettere sull'angolo di una copertina. */
@Composable
internal fun WatchedBadge(size: Dp = 28.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Check, "Visto", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.62f))
    }
}

/** Scheda larga 16:9 della fila "Continua a guardare". */
@Composable
private fun ContinueCard(item: ContinueItem, onClick: () -> Unit) {
    Column(Modifier.width(260.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            SubcomposeAsyncImage(
                item.image, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                loading = { Box(Modifier.fillMaxSize().background(shimmerBrush())) },
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000)), startY = 160f)))
            Box(
                Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape).background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, "Riproduci", tint = Color.White, modifier = Modifier.size(30.dp)) }
            if (item.progress > 0f) {
                SolidProgress(item.progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp).height(4.dp), trackColor = Color(0x44FFFFFF))
            }
        }
        Text(item.title, Modifier.padding(top = 8.dp, start = 2.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            item.subtitle, Modifier.padding(start = 2.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Filled.CloudOff, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onRetry) { Text("Riprova") }
        TextButton(onEdit) { Text("Cambia indirizzo") }
    }
}
