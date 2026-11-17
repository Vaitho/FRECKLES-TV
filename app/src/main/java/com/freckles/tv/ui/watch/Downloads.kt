package com.freckles.tv.ui.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freckles.tv.data.remote.NightDownload
import com.freckles.tv.ui.common.LocalRepo
import kotlinx.coroutines.launch

/** Il server FrecklesNight, per le schermate fuori dalla scheda Guarda (es. il tasto "Scarica" nella Libreria). */
val LocalNight = staticCompositionLocalOf<NightData?> { null }

/**
 * Chiede al server di scaricare un titolo della Libreria, come la scheda "Scarica" della TV.
 * Non compare se il server non è configurato o se ha già il titolo.
 */
@Composable
fun DownloadButton(series: Boolean, tmdbId: Int, modifier: Modifier = Modifier) {
    val night = LocalNight.current ?: return
    if (night.api.base.isEmpty()) return
    val onServer = if (series) night.series.any { it.tmdbId == tmdbId } else night.movies.orEmpty().any { it.tmdbId == tmdbId }
    if (onServer) return
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var state by remember(tmdbId) { mutableStateOf("") } // "" = da premere, "sending", oppure il testo dell'esito
    OutlinedButton(
        onClick = {
            state = "sending"
            scope.launch {
                state = runCatching {
                    // Sonarr identifica le serie con l'id TVDB, Radarr i film con quello TMDB.
                    val key = if (series) repo.tvdbId(tmdbId) ?: error("serie non trovata su TVDB") else tmdbId
                    if (night.api.requestDownload(series, key)) "Già richiesto al server" else "Richiesta inviata al server"
                }.getOrElse { repo.say("Download non riuscito: ${it.message}"); "" }
                night.refreshDownloads()
            }
        },
        enabled = state.isEmpty(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Download, null)
        Text("  " + when (state) { "" -> "Scarica sul server"; "sending" -> "Invio…"; else -> state })
    }
}

/** Uno scaricamento in corso sul server, nella scheda Guarda. */
@Composable
fun DownloadRow(d: NightDownload, onCancel: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Annullare lo scaricamento?") },
        text = { Text("“${d.title}” viene tolto dalla coda del server e la parte già scaricata viene eliminata.") },
        confirmButton = { TextButton({ confirm = false; onCancel() }) { Text("Annulla scaricamento") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Continua") } },
    )
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(d.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull("${d.pct}%", d.eta.takeIf { it.isNotEmpty() }?.let { "mancano $it" }).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (d.id != 0) IconButton({ confirm = true }) { Icon(Icons.Filled.Close, "Annulla lo scaricamento di ${d.title}") }
        }
        LinearProgressIndicator(progress = { d.pct / 100f }, modifier = Modifier.fillMaxWidth())
    }
}
