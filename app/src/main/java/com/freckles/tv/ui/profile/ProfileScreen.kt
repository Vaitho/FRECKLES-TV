package com.freckles.tv.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.freckles.tv.data.prefs.Profile
import com.freckles.tv.data.prefs.UserPrefs
import com.freckles.tv.data.remote.posterUrl
import com.freckles.tv.data.remote.rawgThumb
import com.freckles.tv.ui.common.Avatar
import com.freckles.tv.ui.common.AvatarColors
import com.freckles.tv.ui.common.AvatarEmojis
import com.freckles.tv.ui.common.LocalRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/** Un titolo della libreria che può essere scelto tra i preferiti. */
private data class Candidate(val key: String, val title: String, val image: String?, val kind: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    profile: Profile,
    prefs: UserPrefs,
    onBack: () -> Unit,
    onOpenShow: (Long) -> Unit,
    onOpenMovie: (Long) -> Unit,
    onOpenGame: (Long) -> Unit,
) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val shows by repo.allShows.collectAsStateWithLifecycle(emptyList())
    val movies by repo.allMovies.collectAsStateWithLifecycle(emptyList())
    val games by repo.allGames.collectAsStateWithLifecycle(emptyList())
    var name by remember(profile.name) { mutableStateOf(profile.name ?: "") }

    val mediaLibrary = remember(shows, movies) {
        shows.map { Candidate("s:${it.id}", it.title, posterUrl(it.posterPath), "Serie") } +
            movies.map { Candidate("m:${it.id}", it.title, posterUrl(it.posterPath), "Film") }
    }
    val gameLibrary = remember(games) { games.map { Candidate("g:${it.id}", it.title, rawgThumb(it.imageUrl), "Gioco") } }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val file = File(ctx.filesDir, "avatar_${System.currentTimeMillis()}.jpg")
            ctx.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            profile.photoPath?.let { File(it).delete() }
            prefs.setPhoto(file.absolutePath)
        }
    }

    Column(
        Modifier.statusBarsPadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
            Text("Il tuo profilo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Box(Modifier.align(Alignment.CenterHorizontally)) {
            Avatar(profile, 128.dp, onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            Box(
                Modifier.align(Alignment.BottomEnd).size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PhotoCamera, "Cambia foto", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp)) }
        }
        if (profile.photoPath != null) {
            TextButton(
                onClick = { scope.launch { File(profile.photoPath).delete(); prefs.setPhoto(null) } },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Rimuovi foto") }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(30) },
            label = { Text("Nome") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (name.isNotBlank() && name.trim() != profile.name) {
                    TextButton(onClick = { scope.launch { prefs.setName(name) } }) { Text("Salva") }
                }
            },
        )

        Text("Colore avatar", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AvatarColors.forEachIndexed { i, c ->
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(c)
                        .border(if (profile.colorIdx == i) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { scope.launch { prefs.setAvatar(profile.emoji, i) } }
                )
            }
        }

        Text("Icona avatar", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AvatarEmojis.forEach { e ->
                val selected = profile.photoPath == null && profile.emoji == e
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            scope.launch {
                                profile.photoPath?.let { File(it).delete() }
                                prefs.setPhoto(null)
                                prefs.setAvatar(e, profile.colorIdx)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(e, style = MaterialTheme.typography.titleLarge) }
            }
        }

        FavoritesSection(
            title = "I miei 5 film e serie preferiti",
            subtitle = "Le serie e i film che ti rappresentano. Compaiono anche nella Home.",
            library = mediaLibrary,
            selected = profile.favorites,
            onChange = { scope.launch { prefs.setFavorites(it) } },
            onOpen = { key ->
                val id = key.drop(2).toLong()
                if (key.startsWith("s:")) onOpenShow(id) else onOpenMovie(id)
            },
        )
        FavoritesSection(
            title = "I miei 5 videogiochi preferiti",
            subtitle = "Scegli tra i giochi della tua libreria. Compaiono anche nella Home.",
            library = gameLibrary,
            selected = profile.gameFavorites,
            onChange = { scope.launch { prefs.setGameFavorites(it) } },
            onOpen = { key -> onOpenGame(key.drop(2).toLong()) },
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** Cinque posti per i preferiti, con selettore dalla libreria. */
@Composable
private fun FavoritesSection(
    title: String,
    subtitle: String,
    library: List<Candidate>,
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    onOpen: (String) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val byKey = library.associateBy { it.key }
    val favorites = selected.mapNotNull { byKey[it] }
    // Chiavi di titoli eliminati: non devono occupare posti.
    val live = favorites.map { it.key }

    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(5) { i ->
            val item = favorites.getOrNull(i)
            Box(
                Modifier.weight(1f).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { if (item == null) picking = true else onOpen(item.key) },
                contentAlignment = Alignment.Center,
            ) {
                if (item == null) {
                    Icon(Icons.Filled.Add, "Aggiungi preferito", tint = MaterialTheme.colorScheme.primary)
                } else {
                    AsyncImage(item.image, item.title, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape)
                            .background(Color(0xAA000000))
                            .clickable { onChange(live - item.key) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, "Rimuovi", tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
        }
    }
    if (favorites.size < 5) {
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, null)
            Text("  Scegli dalla libreria")
        }
    }

    if (picking) {
        var query by remember { mutableStateOf("") }
        val candidates = library.filter { it.key !in live && it.title.contains(query, ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Aggiungi ai preferiti") },
            text = {
                Column {
                    OutlinedTextField(query, { query = it }, placeholder = { Text("Cerca nella libreria") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    if (candidates.isEmpty()) Text("Nessun titolo disponibile", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(Modifier.height(320.dp)) {
                        items(candidates, key = { it.key }) { c ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                    onChange(live + c.key)
                                    if (live.size + 1 >= 5) picking = false
                                }.padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    c.image, null,
                                    Modifier.size(40.dp, 60.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop,
                                )
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(c.kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Fatto") } },
        )
    }
}
