package com.freckles.tv.ui.games

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freckles.tv.data.db.GameStatus
import com.freckles.tv.ui.common.LocalRepo
import com.freckles.tv.util.formatHours
import com.freckles.tv.ui.detail.DetailActions
import com.freckles.tv.ui.detail.DetailHeader


@Composable
fun GameDetailScreen(id: Long, onBack: () -> Unit) {
    val repo = LocalRepo.current
    val game by remember(id) { repo.observeGame(id) }.collectAsStateWithLifecycle(null)
    val haptic = LocalHapticFeedback.current
    var confirmDelete by remember { mutableStateOf(false) }
    var editHours by remember { mutableStateOf(false) }

    val g = game ?: run { Box(Modifier.fillMaxSize()); return }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DetailHeader(g.imageUrl, g.title, onBack)
        Text(
            listOfNotNull(g.released?.take(4), g.genres).joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        DetailActions(
            notify = null,
            favorite = g.isFavorite,
            onNotify = {},
            onFavorite = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); repo.setGameFavorite(g) },
            onImdb = null,
            onDelete = { confirmDelete = true },
        )
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Stato", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameStatus.all.forEach { s ->
                    FilterChip(
                        selected = g.status == s,
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); repo.setGameStatus(g, s) },
                        label = { Text(GameStatus.label(s)) },
                    )
                }
            }

            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ore giocate", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatHours(g.minutesPlayed),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickableNoRipple { editHours = true },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(-60 to "−1 h", 30 to "+30 min", 60 to "+1 h", 300 to "+5 h").forEach { (delta, label) ->
                            OutlinedButton(
                                onClick = { repo.setGameMinutes(g, g.minutesPlayed + delta) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.weight(1f),
                            ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    TextButton(onClick = { editHours = true }) { Text("Inserisci ore esatte") }
                }
            }

            Text("Il tuo voto", style = MaterialTheme.typography.titleSmall)
            Row {
                (1..5).forEach { star ->
                    IconButton(onClick = { repo.setGameRating(g, if (g.rating == star) 0 else star) }) {
                        Icon(
                            if (star <= g.rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                            "$star stelle",
                            tint = if (star <= g.rating) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            if (!g.platforms.isNullOrBlank()) {
                Text("Piattaforme", style = MaterialTheme.typography.titleSmall)
                Text(g.platforms, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!g.overview.isNullOrBlank()) Text(g.overview)
            Spacer(Modifier.navigationBarsPadding().height(32.dp))
        }
    }

    if (editHours) {
        var text by remember { mutableStateOf((g.minutesPlayed / 60.0).let { if (it == 0.0) "" else "%.1f".format(it).trimEnd('0').trimEnd('.', ',') }) }
        AlertDialog(
            onDismissRequest = { editHours = false },
            title = { Text("Ore giocate") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
                    label = { Text("Ore (es. 12,5)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val hours = text.replace(',', '.').toDoubleOrNull() ?: 0.0
                    repo.setGameMinutes(g, (hours * 60).toInt())
                    editHours = false
                }) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { editHours = false }) { Text("Annulla") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Rimuovere ${g.title}?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; repo.deleteGame(g.id); onBack() }) { Text("Rimuovi") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
