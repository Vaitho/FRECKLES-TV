package com.freckles.tv.ui.detail

import com.freckles.tv.ui.common.CastRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import com.freckles.tv.ui.common.SolidProgress
import androidx.compose.foundation.combinedClickable
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.freckles.tv.data.remote.backdropUrl
import com.freckles.tv.ui.common.CheckCircle
import com.freckles.tv.ui.common.ExpandableText
import com.freckles.tv.ui.common.InfoChips
import com.freckles.tv.ui.common.LocalRepo
import com.freckles.tv.ui.common.StarRating
import com.freckles.tv.util.formatDate

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ShowDetailScreen(id: Long, onBack: () -> Unit) {
    val repo = LocalRepo.current
    val show by remember(id) { repo.observeShow(id) }.collectAsStateWithLifecycle(null)
    val episodes by remember(id) { repo.observeEpisodes(id) }.collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(id) { repo.fillEpisodeNames(id) }
    val uri = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current
    var confirmDelete by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) repo.say("Senza il permesso per le notifiche la campanella non potrà avvisarti")
    }

    val s = show ?: run { Box(Modifier.fillMaxSize()); return }
    val bySeason = remember(episodes) { episodes.groupBy { it.season }.toSortedMap() }
    val today = remember { java.time.LocalDate.now().toString() }
    // Contano solo gli episodi già usciti (o già visti): quelli annunciati non abbassano l'avanzamento.
    val counted = episodes.filter { !it.isSpecial && (it.watched || (it.airDate != null && it.airDate <= today)) }
    val watched = counted.count { it.watched }
    // Stagione mostrata: quella scelta, altrimenti la prima con episodi usciti da vedere, altrimenti l'ultima.
    val current = selected.takeIf { it in bySeason }
        ?: bySeason.keys.firstOrNull { k -> k != 0 && bySeason.getValue(k).any { !it.watched && it.airDate != null && it.airDate <= today } }
        ?: bySeason.keys.lastOrNull { it != 0 } ?: bySeason.keys.firstOrNull()

    LazyColumn(Modifier.fillMaxSize()) {
        item { DetailHeader(imageUrl = backdropUrl(s.backdropPath ?: s.posterPath), title = s.title, onBack = onBack) }
        item {
            DetailActions(
                notify = s.notifyEnabled,
                favorite = s.isFavorite,
                onNotify = {
                    if (!s.notifyEnabled && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    repo.setShowNotify(s)
                },
                onFavorite = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); repo.setShowFavorite(s) },
                onImdb = s.imdbId?.let { { uri.openUri("https://www.imdb.com/title/$it") } },
                onDelete = { confirmDelete = true },
            )
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoChips(
                    listOfNotNull(
                        episodes.filter { !it.isSpecial }.mapNotNull { it.airDate }.minOrNull()?.take(4),
                        bySeason.keys.count { it != 0 }.takeIf { it > 0 }?.let { if (it == 1) "1 stagione" else "$it stagioni" },
                        "~${s.defaultRuntime} min",
                    ) + s.genres.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(3),
                    Modifier.padding(bottom = 4.dp),
                )
                Text("$watched / ${counted.size} episodi visti", fontWeight = FontWeight.SemiBold)
                SolidProgress(if (counted.isEmpty()) 0f else watched.toFloat() / counted.size, Modifier.fillMaxWidth().height(8.dp))
                Text("Il tuo voto", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                StarRating(s.rating, { repo.setShowRating(s, it) })
                if (!s.overview.isNullOrBlank()) {
                    ExpandableText(s.overview, Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        item(key = "cast") { CastRow("tv", s.tmdbId, Modifier.padding(top = 8.dp, bottom = 4.dp)) }
        if (current != null) {
            val list = bySeason.getValue(current)
            val seasonWatched = list.count { it.watched }
            // Quelli non ancora usciti non si possono segnare: il pulsante considera solo gli altri.
            val seasonMarkable = list.count { it.watched || (it.airDate != null && it.airDate <= today) }
            item(key = "seasonChips") {
                val chipState = rememberLazyListState()
                LaunchedEffect(current) { chipState.animateScrollToItem(bySeason.keys.indexOf(current).coerceAtLeast(0)) }
                LazyRow(state = chipState, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bySeason.keys.toList(), key = { it }) { season ->
                        FilterChip(
                            selected = season == current,
                            onClick = { selected = season },
                            label = { Text(if (season == 0) "Speciali" else "Stagione $season") },
                        )
                    }
                }
            }
            item(key = "seasonHeader$current") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$seasonWatched / ${list.size} episodi", style = MaterialTheme.typography.titleMedium)
                        SolidProgress(if (list.isEmpty()) 0f else seasonWatched.toFloat() / list.size, Modifier.padding(top = 6.dp).fillMaxWidth(0.6f).height(4.dp))
                    }
                    if (current != 0) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            repo.setSeasonWatched(s.id, current, seasonWatched < seasonMarkable)
                        }) {
                            Icon(
                                Icons.Filled.DoneAll, "Segna stagione",
                                tint = if (seasonMarkable > 0 && seasonWatched >= seasonMarkable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(list, key = { it.id }) { ep ->
                Row(
                    Modifier.fillMaxWidth()
                        .combinedClickable(
                            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); repo.markUpTo(ep) },
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); repo.toggleEpisode(ep) },
                        )
                        .padding(start = 12.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckCircle(ep.watched, { haptic.performHapticFeedback(HapticFeedbackType.LongPress); repo.toggleEpisode(ep) })
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text("${ep.number}. ${ep.name ?: "Episodio ${ep.number}"}")
                        Text(
                            listOfNotNull(formatDate(ep.airDate), "${ep.runtime} min").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(32.dp)) }
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
    LightStatusBarIcons()
    Box(Modifier.fillMaxWidth().height(280.dp)) {
        AsyncImage(
            model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, MaterialTheme.colorScheme.background))
            )
        )
        Box(
            Modifier.statusBarsPadding().padding(12.dp).clip(CircleShape).background(Color(0x66000000)),
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = Color.White) }
        }
        Text(
            title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

/** Finché è visibile, rende chiare le icone della barra di stato (ora, batteria) e poi ripristina. */
@Composable
private fun LightStatusBarIcons() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
}
