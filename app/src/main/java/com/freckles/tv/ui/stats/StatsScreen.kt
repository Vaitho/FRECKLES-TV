package com.freckles.tv.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    val gamesDone by repo.finishedGames.collectAsStateWithLifecycle(0)
    val minutes by repo.totalMinutes.collectAsStateWithLifecycle(0L)
    val monthly by repo.monthly.collectAsStateWithLifecycle(emptyList())

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Scheda principale con gradiente d'accento
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
            ).padding(24.dp)
        ) {
            Column {
                Text("Tempo totale tra schermo e controller", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                Text(
                    formatDuration(minutes), color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text("${minutes / 60} ore in totale", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("Episodi visti", episodes.toString(), Modifier.weight(1f))
            StatCard("Film visti", movies.toString(), Modifier.weight(1f))
        }
        StatCard("Giochi finiti", gamesDone.toString(), Modifier.fillMaxWidth())
        Text("Ultimi mesi (ore di serie TV)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (monthly.isEmpty()) {
            Text("Nessun dato con data di visione.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val data = monthly.reversed()
            val max = data.maxOf { it.minutes }.coerceAtLeast(1)
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().height(190.dp).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    data.forEach { m ->
                        val h by animateFloatAsState(110f * m.minutes / max, tween(700), label = "bar")
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${m.minutes / 60}", style = MaterialTheme.typography.labelSmall)
                            Box(
                                Modifier.fillMaxWidth().height(h.dp.coerceAtLeast(4.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
                                    )
                            )
                            Text(m.month.takeLast(2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}
