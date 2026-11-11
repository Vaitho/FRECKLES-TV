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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.freckles.tv.data.remote.NightSub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Riproduce un file del server FrecklesNight; riprende da dove ti eri fermato e salva il progresso sul server. */
@Composable
fun PlayerScreen(data: NightData, fileId: String, fromStart: Boolean, onFinished: () -> Unit, onBack: () -> Unit) {
    val api = data.api
    val ctx = LocalContext.current
    val player = remember {
        // AC3/EAC3/DTS non sono decodificati da tutti i telefoni: ripiego sul decoder software FFmpeg.
        val renderers = DefaultRenderersFactory(ctx).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        ExoPlayer.Builder(ctx, renderers).build()
    }

    var failed by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var lastSaved by remember { mutableStateOf(-1L) }
    DisposableEffect(player) {
        val l = object : Player.Listener { override fun onPlayerError(error: PlaybackException) { failed = true } }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    fun save() {
        val dur = player.duration
        if (dur == C.TIME_UNSET || dur <= 0) return
        val pos = player.currentPosition / 1000
        if (pos == lastSaved) return // in pausa: niente da inviare
        lastSaved = pos
        // Stessa soglia del server (92%): il titolo conta come visto anche nella libreria, una volta sola.
        if (!finished && pos * 100 > dur / 1000 * 92) { finished = true; onFinished() }
        data.saveProgress(fileId, pos, dur / 1000)
    }

    fun item(subs: List<NightSub>) = MediaItem.Builder()
        .setUri(api.mediaUrl(fileId))
        .setSubtitleConfigurations(subs.map {
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(api.subUrl(fileId, it.key)))
                .setMimeType("text/vtt").setLanguage(it.lang.ifEmpty { null }).setLabel(it.label).build()
        })
        .build()

    LaunchedEffect(fileId) {
        // La posizione è già in memoria (aggiornata entrando in Guarda o nella scheda): niente richiesta in più.
        val start = if (fromStart) 0L else (data.progress[fileId]?.pos ?: 0) * 1000L
        val subs = runCatching { api.subtitles(fileId) }.getOrDefault(emptyList())
        // Il primo scaricamento di un sottotitolo fa estrarre le tracce al server. Se sono già pronte arrivano subito
        // e il video parte con i sottotitoli; altrimenti parte senza e li aggiungo dopo (un solo ricaricamento).
        val warm = async(Dispatchers.IO) { subs.isNotEmpty() && runCatching { api.warm(api.subUrl(fileId, subs.first().key)) }.getOrDefault(false) }
        val ready = withTimeoutOrNull(1500) { warm.await() } == true
        player.setMediaItem(item(if (ready) subs else emptyList()), start)
        player.prepare()
        player.playWhenReady = true
        if (!ready && warm.await()) {
            val playing = player.playWhenReady
            player.setMediaItem(item(subs), player.currentPosition)
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
