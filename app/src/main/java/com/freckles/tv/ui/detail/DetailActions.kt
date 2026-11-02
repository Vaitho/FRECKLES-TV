package com.freckles.tv.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Riga di azioni: campanella (solo serie, `notify` != null), preferito, IMDb, elimina. */
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
        if (notify == true) {
            for (a in listOf(25f, -20f, 14f, -8f, 0f)) shake.animateTo(a, tween(80))
        }
    }
    Row(Modifier.padding(horizontal = 12.dp)) {
        if (notify != null) {
            IconButton(onClick = onNotify) {
                Icon(
                    modifier = Modifier.rotate(shake.value),
                    imageVector =
                    if (notify) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                    contentDescription = if (notify) "Disattiva notifiche" else "Attiva notifiche nuove stagioni",
                    tint = if (notify) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        IconButton(onClick = onFavorite) {
            Icon(
                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Preferito",
                tint = if (favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (onImdb != null) {
            IconButton(onClick = onImdb) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Apri su IMDb") }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Rimuovi") }
    }
}
