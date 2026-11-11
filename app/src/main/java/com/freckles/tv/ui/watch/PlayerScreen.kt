package com.freckles.tv.ui.watch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.freckles.tv.data.remote.NightApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Riproduce un file del server FrecklesNight; riprende da dove ti eri fermato e salva il progresso sul server. */
@Composable
fun PlayerScreen(api: NightApi, fileId: String, fromStart: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember {
        // AC3/EAC3/DTS non sono decodificati da tutti i telefoni: ripiego sul decoder software FFmpeg.
        val renderers = DefaultRenderersFactory(ctx).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        ExoPlayer.Builder(ctx, renderers).build()
    }

    var failed by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val l = object : Player.Listener { override fun onPlayerError(error: PlaybackException) { failed = true } }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    fun save() {
        val dur = player.duration
        if (dur == C.TIME_UNSET || dur <= 0) return
        val pos = player.currentPosition / 1000
        CoroutineScope(Dispatchers.IO).launch { runCatching { api.saveProgress(fileId, pos, dur / 1000) } }
    }

    LaunchedEffect(fileId) {
        val resume = runCatching { api.progress()[fileId] }.getOrNull()
        player.setMediaItem(MediaItem.fromUri(api.mediaUrl(fileId)), if (fromStart) 0L else (resume?.pos ?: 0) * 1000L)
        player.prepare()
        player.playWhenReady = true
        // Sottotitoli: il primo scaricamento fa estrarre le tracce al server, quindi parto subito senza e li aggiungo dopo.
        val subs = runCatching { api.subtitles(fileId) }.getOrDefault(emptyList())
        if (subs.isNotEmpty() && runCatching { api.warm(api.subUrl(fileId, subs.first().key)) }.isSuccess) {
            val item = MediaItem.Builder()
                .setUri(api.mediaUrl(fileId))
                .setSubtitleConfigurations(subs.map {
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(api.subUrl(fileId, it.key)))
                        .setMimeType("text/vtt").setLanguage(it.lang.ifEmpty { null }).setLabel(it.label).build()
                })
                .build()
            val playing = player.playWhenReady
            player.setMediaItem(item, player.currentPosition)
            player.prepare()
            player.playWhenReady = playing
        }
    }
    LaunchedEffect(fileId) { while (true) { delay(10_000); save() } }
    DisposableEffect(Unit) { onDispose { save(); player.release() } }

    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; keepScreenOn = true } },
            modifier = Modifier.fillMaxSize(),
        )
        if (failed) Text(
            "Questo dispositivo non riesce a riprodurre il video (formato non supportato, es. HEVC a 10 bit sull'emulatore).",
            Modifier.align(Alignment.TopCenter).padding(start = 32.dp, end = 32.dp, top = 96.dp), color = Color.White, textAlign = TextAlign.Center,
        )
        IconButton(onClick = onBack, Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = Color.White)
        }
    }
}
