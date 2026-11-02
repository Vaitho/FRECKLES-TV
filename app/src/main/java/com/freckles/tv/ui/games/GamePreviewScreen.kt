package com.freckles.tv.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freckles.tv.data.db.GameStatus
import com.freckles.tv.data.remote.GameInfo
import com.freckles.tv.ui.common.LocalRepo
import com.freckles.tv.ui.detail.DetailHeader

/** Anteprima di un gioco non ancora in libreria, con i pulsanti per aggiungerlo. */
@Composable
fun GamePreviewScreen(extId: String, onBack: () -> Unit, onAdded: (Long) -> Unit) {
    val repo = LocalRepo.current
    var attempt by remember { mutableIntStateOf(0) }
    val info by produceState<Result<GameInfo>?>(null, extId, attempt) {
        value = null
        value = runCatching { repo.gamePreview(extId) }
    }
    val games by repo.allGames.collectAsStateWithLifecycle(emptyList())
    val pending by repo.pending.collectAsStateWithLifecycle()
    val adding = repo.gamePendingKey(extId) in pending
    var requested by remember { mutableStateOf(false) }

    val libId = games.firstOrNull { it.extId == extId }?.id
    LaunchedEffect(libId, requested) { if (libId != null && requested) onAdded(libId) }

    val result = info
    when {
        result == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        result.isFailure -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Il servizio dei giochi non risponde in questo momento.", color = MaterialTheme.colorScheme.error)
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack) { Text("Indietro") }
                Button(onClick = { attempt++ }) { Text("Riprova") }
            }
        }
        else -> {
            val d = result.getOrThrow()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                DetailHeader(d.backdropUrl ?: d.imageUrl, d.title, onBack)
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val meta = listOfNotNull("Videogioco", d.released?.take(4)?.takeIf { it.isNotBlank() }, d.genres).joinToString(" · ")
                    Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (d.hours != null) {
                        Text("Durata media: circa ${d.hours} ore", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(GameStatus.BACKLOG, GameStatus.PLAYING, GameStatus.FINISHED).forEachIndexed { i, s ->
                            val enabled = !adding && !requested
                            val onClick = { requested = true; repo.addGame(extId, s) }
                            if (i == 0) {
                                Button(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) { Text(GameStatus.label(s), maxLines = 1) }
                            } else {
                                FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) { Text(GameStatus.label(s), maxLines = 1) }
                            }
                        }
                    }
                    if (adding || requested) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("  Aggiunta in corso…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!d.platforms.isNullOrBlank()) {
                        Text("Piattaforme", style = MaterialTheme.typography.titleSmall)
                        Text(d.platforms, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!d.overview.isNullOrBlank()) Text(d.overview)
                    Spacer(Modifier.navigationBarsPadding().height(32.dp))
                }
            }
        }
    }
}
