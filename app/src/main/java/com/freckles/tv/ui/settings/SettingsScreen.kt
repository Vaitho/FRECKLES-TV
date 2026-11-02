package com.freckles.tv.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freckles.tv.ui.common.LocalRepo

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val state by repo.importer.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) repo.startImport { readText(ctx, uri) }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) repo.startExport { json -> ctx.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(json) } }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
        Text("Impostazioni", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Text("Importa ed esporta", style = MaterialTheme.typography.titleMedium)
        Text(
            "Scegli il file di export (formato TV Time). Le serie vengono cercate su TMDB, " +
                "episodi e date di visione vengono ripristinati. Puoi rilanciare l'import senza creare duplicati.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) },
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.FileUpload, null)
            Text("  Scegli file…")
        }

        OutlinedButton(onClick = { exporter.launch("freckles-tv-backup.json") }, modifier = Modifier.fillMaxWidth()) {
            Text("Esporta backup JSON")
        }

        if (state.running) {
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${state.done} / ${state.total} serie elaborate")
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.finished && state.error == null) {
            Text("Import completato", fontWeight = FontWeight.SemiBold)
            Text("Serie importate: ${state.imported}, film: ${state.moviesImported} (su ${state.total} titoli)")
            Text("Episodi segnati come visti: ${state.episodesMarked}")
            if (state.episodesUnmatched > 0) {
                Text(
                    "Episodi non trovati su TMDB (numerazione diversa): ${state.episodesUnmatched}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.failed.isNotEmpty()) {
                Text("Non importate (${state.failed.size}):", color = MaterialTheme.colorScheme.error)
                state.failed.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (state.finished) OutlinedButton(onClick = { repo.importer.dismiss() }) { Text("Chiudi report") }
    }
}

private fun readText(ctx: Context, uri: Uri): String =
    ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
