package com.freckles.tv.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.freckles.tv.FreckleApp
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

private const val PREFIX = "freckles-tv-auto-"
private const val KEEP = 7

/**
 * Backup giornaliero nella cartella scelta dall'utente (Storage Access Framework):
 * un file al giorno, tengo gli ultimi [KEEP]. Se la cartella non è impostata non fa nulla.
 */
class AutoBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FreckleApp
        val folder = app.prefs.profile.first().autoBackupUri ?: return Result.success()
        return runCatching {
            write(applicationContext, Uri.parse(folder), app.repository.exportJson())
            app.prefs.setLastAutoBackup(System.currentTimeMillis())
        }.fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "auto_backup", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build(),
            )
        }

        fun runNow(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "auto_backup_now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AutoBackupWorker>().build(),
            )
        }

        /** "primary:Documents/Backup" -> "Documents/Backup" */
        fun folderLabel(uri: String): String =
            runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(uri)).substringAfter(':').ifBlank { "Memoria principale" } }
                .getOrDefault(uri)

        private data class Doc(val id: String, val name: String)

        private fun write(ctx: Context, tree: Uri, json: String) {
            val resolver = ctx.contentResolver
            val treeId = DocumentsContract.getTreeDocumentId(tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)

            val docs = mutableListOf<Doc>()
            resolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c -> while (c.moveToNext()) docs += Doc(c.getString(0), c.getString(1) ?: "") }

            val name = "$PREFIX${LocalDate.now()}.json"
            fun delete(d: Doc) = runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, d.id)) }

            // Stesso giorno: sostituisco il file invece di crearne un "(1)".
            docs.filter { it.name == name }.forEach(::delete)
            val target = DocumentsContract.createDocument(resolver, parent, "application/json", name)
                ?: error("impossibile creare il file di backup")
            resolver.openOutputStream(target)!!.bufferedWriter().use { it.write(json) }

            docs.filter { it.name.startsWith(PREFIX) && it.name != name }
                .sortedByDescending { it.name }
                .drop(KEEP - 1)
                .forEach(::delete)
        }
    }
}
