package com.freckles.tv.ui.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Riga di azioni come "pillole" tappabili: campanella (solo serie, `notify` != null),
 * preferito, IMDb, rimuovi. Lo stato attivo cambia colore e fa un piccolo "pop".
 */
@Composable
fun DetailActions(
    notify: Boolean?,
    favorite: Boolean,
    onNotify: () -> Unit,
    onFavorite: () -> Unit,
    onImdb: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(notify) {
        if (notify == true) for (a in listOf(25f, -20f, 14f, -8f, 0f)) shake.animateTo(a, tween(80))
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (notify != null) {
            ActionPill(
                icon = if (notify) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                label = if (notify) "Avvisami" else "Avvisami",
                active = notify,
                onClick = onNotify,
                modifier = Modifier.weight(1f),
                iconModifier = Modifier.rotate(shake.value),
            )
        }
        ActionPill(
            icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            label = "Preferito",
            active = favorite,
            onClick = onFavorite,
            modifier = Modifier.weight(1f),
        )
        if (onImdb != null) {
            ActionPill(Icons.AutoMirrored.Filled.OpenInNew, "IMDb", false, onImdb, Modifier.weight(1f))
        }
        ActionPill(Icons.Filled.Delete, "Rimuovi", false, onDelete, Modifier.weight(1f), danger = true)
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.9f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press",
    )
    // "Pop" dell'icona quando si attiva
    val pop = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            pop.snapTo(0.6f)
            pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
        }
    }
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        when {
            active -> scheme.primaryContainer
            danger -> scheme.errorContainer.copy(alpha = 0.45f)
            else -> scheme.surfaceContainerHigh
        }, tween(200), label = "bg",
    )
    val fg by animateColorAsState(
        when {
            active -> scheme.primary
            danger -> scheme.error
            else -> scheme.onSurfaceVariant
        }, tween(200), label = "fg",
    )
    Column(
        modifier
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = fg, modifier = iconModifier.size(28.dp).scale(pop.value))
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
