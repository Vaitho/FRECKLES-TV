package com.freckles.tv.ui.detail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.freckles.tv.data.remote.backdropUrl
import com.freckles.tv.ui.common.LocalRepo

@Composable
fun ShowDetailScreen(id: Long, onBack: () -> Unit) {
    val repo = LocalRepo.current
    val show by remember(id) { repo.observeShow(id) }.collectAsStateWithLifecycle(null)
    val episodes by remember(id) { repo.observeEpisodes(id) }.collectAsStateWithLifecycle(emptyList())
    val uri = LocalUriHandler.current
    var confirmDelete by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(setOf<Int>()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val s = show ?: run { Box(Modifier.fillMaxSize()); return }
    val bySeason = episodes.groupBy { it.season }.toSortedMap()
    val counted = episodes.filter { !it.isSpecial }
    val watched = counted.count { it.watched }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            DetailHeader(
                imageUrl = backdropUrl(s.backdropPath ?: s.posterPath),
                title = s.title,
                onBack = onBack,
            )
        }
        item {
            DetailActions(
                notify = s.notifyEnabled,
                favorite = s.isFavorite,
                onNotify = {
                    if (!s.notifyEnabled && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    repo.setShowNotify(s)
                },
                onFavorite = { repo.setShowFavorite(s) },
                onImdb = s.imdbId?.let { { uri.openUri("https://www.imdb.com/title/$it") } },
                onDelete = { confirmDelete = true },
            )
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$watched / ${counted.size} episodi visti", fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { if (counted.isEmpty()) 0f else watched.toFloat() / counted.size },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                if (!s.overview.isNullOrBlank()) {
                    Text(s.overview, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        bySeason.forEach { (season, list) ->
            val open = season in expanded
            val seasonWatched = list.count { it.watched }
            item(key = "season$season") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = if (open) expanded - season else expanded + season }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (season == 0) "Extra" else "Stagione $season", style = MaterialTheme.typography.titleMedium)
                        Text("$seasonWatched / ${list.size}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (season != 0) {
                        IconButton(onClick = { repo.setSeasonWatched(s.id, season, seasonWatched < list.size) }) {
                            Icon(Icons.Filled.DoneAll, "Segna stagione")
                        }
                    }
                    Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                }
                HorizontalDivider()
            }
            if (open) {
                items(list, key = { it.id }) { ep ->
                    Row(
                        Modifier.fillMaxWidth().clickable { repo.toggleEpisode(ep) }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = ep.watched, onCheckedChange = { repo.toggleEpisode(ep) })
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text("${ep.number}. ${ep.name ?: "Episodio ${ep.number}"}")
                            Text(
                                listOfNotNull(ep.airDate, "${ep.runtime} min").joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item { Box(Modifier.height(32.dp)) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Rimuovere ${s.title}?") },
            text = { Text("Verranno persi anche gli episodi visti.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; repo.deleteShow(s.id); onBack() }) { Text("Rimuovi") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }
}

@Composable
internal fun DetailHeader(imageUrl: String?, title: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(240.dp)) {
        AsyncImage(
            model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))
            )
        )
        IconButton(onClick = onBack, Modifier.padding(top = 36.dp, start = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro")
        }
        Text(
            title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
        )
    }
}
