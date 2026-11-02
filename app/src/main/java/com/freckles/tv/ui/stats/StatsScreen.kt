package com.freckles.tv.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freckles.tv.ui.common.LocalRepo
import com.freckles.tv.ui.common.formatDuration

@Composable
fun StatsScreen() {
    val repo = LocalRepo.current
    val episodes by repo.watchedEpisodes.collectAsStateWithLifecycle(0)
    val movies by repo.watchedMovies.collectAsStateWithLifecycle(0)
    val minutes by repo.totalMinutes.collectAsStateWithLifecycle(0L)
    val monthly by repo.monthly.collectAsStateWithLifecycle(emptyList())

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Statistiche", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        BigCard("Tempo totale", formatDuration(minutes))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigCard("Episodi visti", episodes.toString(), Modifier.weight(1f))
            BigCard("Film visti", movies.toString(), Modifier.weight(1f))
        }
        Text("Ultimi mesi (ore di serie TV)", style = MaterialTheme.typography.titleMedium)
        if (monthly.isEmpty()) {
            Text("Nessun dato con data di visione.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val data = monthly.reversed()
            val max = data.maxOf { it.minutes }.coerceAtLeast(1)
            Row(
                Modifier.fillMaxWidth().height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                data.forEach { m ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${m.minutes / 60}", style = MaterialTheme.typography.labelSmall)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((110f * m.minutes / max).dp.coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(m.month.takeLast(2), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}
