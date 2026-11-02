package com.freckles.tv.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val state by repo.importer.state.collectAsStateWithLifecycle()
    val prefs = (ctx.applicationContext as com.freckles.tv.FreckleApp).prefs
    val profile by prefs.profile.collectAsStateWithLifecycle(initialValue = com.freckles.tv.data.prefs.Profile())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            scope.launch {
                prefs.setAutoBackupUri(uri.toString())
                com.freckles.tv.backup.AutoBackupWorker.runNow(ctx)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) repo.startImport { readText(ctx, uri) }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) repo.startExport { json -> ctx.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(json) } }
    }

    Column(
        Modifier.statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
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

        Text("Backup automatico", style = MaterialTheme.typography.titleMedium)
        val folder = profile.autoBackupUri
        Text(
            if (folder == null) "Disattivato. Scegli una cartella (meglio se sincronizzata, es. Google Drive): ogni giorno ci salvo un backup e tengo gli ultimi 7."
            else "Ogni giorno in: ${com.freckles.tv.backup.AutoBackupWorker.folderLabel(folder)}" +
                (profile.lastAutoBackup?.let { "\nUltimo backup: " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it)) } ?: "\nPrimo backup in corso…"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.weight(1f)) {
                Text(if (folder == null) "Scegli cartella" else "Cambia cartella")
            }
            if (folder != null) {
                OutlinedButton(onClick = { com.freckles.tv.backup.AutoBackupWorker.runNow(ctx) }, modifier = Modifier.weight(1f)) { Text("Esegui ora") }
            }
        }
        if (folder != null) {
            androidx.compose.material3.TextButton(onClick = {
                runCatching { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(folder), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                scope.launch { prefs.setAutoBackupUri(null) }
            }) { Text("Disattiva backup automatico") }
        }

        Text("Notifiche", style = MaterialTheme.typography.titleMedium)
        Text(
            "Le serie con la campanella vengono controllate ogni 12 ore circa.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { androidx.work.WorkManager.getInstance(ctx).enqueue(androidx.work.OneTimeWorkRequestBuilder<com.freckles.tv.notify.NewSeasonWorker>().build()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Controlla nuove stagioni adesso") }

        Text(
            "Dati di film e serie: TMDB. Dati dei videogiochi: IGDB (riserva: Steam). Link IMDb solo come collegamento.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
            Text("Serie importate: ${state.imported}, film: ${state.moviesImported}, giochi: ${state.gamesImported} (su ${state.total} titoli)")
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
