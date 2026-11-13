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
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.DebugTextViewHelper
import android.widget.TextView
import androidx.media3.ui.PlayerView
import com.freckles.tv.data.remote.NightSub
import com.freckles.tv.data.remote.NightTracks
import com.freckles.tv.data.remote.NightVideo
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Il telefono ha un decoder per questo video? I formati che non conosco li lascio provare al player. */
private fun canDecode(v: NightVideo): Boolean {
    val mime = when (v.codec) {
        "hevc" -> MediaFormat.MIMETYPE_VIDEO_HEVC
        "h264" -> MediaFormat.MIMETYPE_VIDEO_AVC
        "vp9" -> MediaFormat.MIMETYPE_VIDEO_VP9
        "av1" -> "video/av01"
        else -> return true
    }
    if (v.w <= 0 || v.h <= 0) return true
    val f = MediaFormat.createVideoFormat(mime, v.w, v.h)
    if (v.bits == 10) when (v.codec) {
        "hevc" -> f.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10)
        "h264" -> f.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10)
    }
    return MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(f) != null
}

/** Riproduce un file del server FrecklesNight; riprende da dove ti eri fermato e salva il progresso sul server. */
@Composable
fun PlayerScreen(data: NightData, fileId: String, fromStart: Boolean, subMode: String, onSubMode: (String) -> Unit, onFinished: () -> Unit, onPlay: (String) -> Unit, onBack: () -> Unit) {
    val api = data.api
    val ctx = LocalContext.current
    val player = remember {
        // AC3/EAC3/DTS non sono decodificati da tutti i telefoni: ripiego sul decoder software FFmpeg.
        // Decoder di ripiego: se il primo decoder video del telefono non si avvia (es. Dolby Vision), prova il successivo (HEVC).
        val renderers = DefaultRenderersFactory(ctx).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON).setEnableDecoderFallback(true)
        ExoPlayer.Builder(ctx, renderers).build()
    }

    // Conversione al volo: il telefono non decodifica il video originale, quindi il server lo manda in H.264.
    // Il flusso convertito non ha durata e non si può scorrere: ogni salto ricarica il video dal nuovo punto.
    var tx by remember { mutableStateOf(false) }
    var durMs by remember { mutableStateOf(0L) }
    var subs by remember { mutableStateOf(emptyList<NightSub>()) }

    fun load(posMs: Long) {
        val auto = defaultSub(subs, subMode) // parte acceso da solo; gli altri si scelgono dal tasto dei sottotitoli
        val item = MediaItem.Builder()
            .setUri(if (tx) api.transcodeUrl(fileId, posMs / 1000) else api.mediaUrl(fileId))
            .setSubtitleConfigurations(subs.map {
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(api.subUrl(fileId, it.key)))
                    .setMimeType("text/vtt").setLanguage(it.lang.ifEmpty { null }).setLabel(it.label).setId(it.key)
                    .setSelectionFlags(if (it == auto) C.SELECTION_FLAG_DEFAULT else 0).build()
            })
            .build()
        player.setMediaItem(item, posMs)
        player.prepare()
    }

    // I tasti precedente/successivo del player cambiano episodio; nei film (nessun vicino) sono nascosti.
    val (prevId, nextId) = remember(fileId, data.series) { episodeNeighbors(data.series, fileId) }
    val controls = remember(player, prevId, nextId) {
        object : ForwardingPlayer(player) {
            private val cmds = listOf(
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
            )
            private fun mine(c: Int): Boolean? = when (c) {
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> nextId != null
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> prevId != null
                // Senza la timeline vera la barra usa la durata e la posizione dichiarate qui sotto.
                Player.COMMAND_GET_TIMELINE -> if (tx) false else null
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD -> if (tx) true else null
                else -> null
            }
            override fun isCommandAvailable(command: Int) = mine(command) ?: super.isCommandAvailable(command)
            override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon().apply {
                for (c in cmds) when (mine(c)) { true -> add(c); false -> remove(c); null -> {} }
            }.build()
            override fun seekToNext() { nextId?.let(onPlay) }
            override fun seekToNextMediaItem() = seekToNext()
            override fun seekToPrevious() { prevId?.let(onPlay) }
            override fun seekToPreviousMediaItem() = seekToPrevious()

            override fun getDuration() = if (tx) durMs else super.getDuration()
            override fun getContentDuration() = duration
            override fun isCurrentMediaItemSeekable() = tx || super.isCurrentMediaItemSeekable()
            override fun seekTo(positionMs: Long) { if (tx) load(positionMs.coerceIn(0, durMs)) else super.seekTo(positionMs) }
            override fun seekTo(mediaItemIndex: Int, positionMs: Long) { if (tx) seekTo(positionMs) else super.seekTo(mediaItemIndex, positionMs) }
            override fun seekBack() { if (tx) seekTo(currentPosition - seekBackIncrement) else super.seekBack() }
            override fun seekForward() { if (tx) seekTo(currentPosition + seekForwardIncrement) else super.seekForward() }
        }
    }

    var failed by remember { mutableStateOf<String?>(null) }
    // Nessuna immagine dopo qualche secondo: mostro i dati tecnici del player (stato, formato, decoder) per capire perché.
    var hasVideo by remember { mutableStateOf(false) }
    var stuck by remember { mutableStateOf(false) }
    LaunchedEffect(fileId) { delay(8_000); stuck = !hasVideo }
    var finished by remember { mutableStateOf(false) }
    var lastSaved by remember { mutableStateOf(-1L) }
    val currentSubMode by rememberUpdatedState(subMode)
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Il decoder del telefono non ce l'ha fatta (errori 4xxx): riprovo una volta con il video convertito dal server.
                if (!tx && durMs > 0 && error.errorCode / 1000 == 4) { tx = true; load(player.currentPosition) } else failed = error.errorCodeName
            }
            override fun onRenderedFirstFrame() { stuck = false; hasVideo = true }
            // La scelta fatta col tasto dei sottotitoli vale anche per i prossimi video.
            override fun onTracksChanged(tracks: Tracks) {
                val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                if (text.isEmpty() && subs.isNotEmpty()) return // sottotitoli non ancora caricati
                val f = text.firstOrNull { it.isSelected }?.let { g -> g.getTrackFormat((0 until g.length).first { g.isTrackSelected(it) }) }
                pickedSubMode(subs, currentSubMode, f != null, f?.id?.substringAfterLast(':'), f?.language)?.let(onSubMode)
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    fun save() {
        val dur = if (tx) durMs else player.duration
        if (dur == C.TIME_UNSET || dur <= 0) return
        val pos = player.currentPosition / 1000
        if (pos == lastSaved) return // in pausa: niente da inviare
        lastSaved = pos
        // Stessa soglia del server (92%): il titolo conta come visto anche nella libreria, una volta sola.
        if (!finished && pos * 100 > dur / 1000 * 92) { finished = true; onFinished() }
        data.saveProgress(fileId, pos, dur / 1000)
    }

    LaunchedEffect(fileId) {
        // La posizione è già in memoria (aggiornata entrando in Guarda o nella scheda): niente richiesta in più.
        val start = if (fromStart) 0L else (data.progress[fileId]?.pos ?: 0) * 1000L
        val tracks = runCatching { api.tracks(fileId) }.getOrDefault(NightTracks())
        durMs = (tracks.dur * 1000).toLong()
        tx = durMs > 0 && tracks.video?.let(::canDecode) == false
        // Il primo scaricamento di un sottotitolo fa estrarre le tracce al server. Se sono già pronte arrivano subito
        // e il video parte con i sottotitoli; altrimenti parte senza e li aggiungo dopo (un solo ricaricamento).
        val warm = async(Dispatchers.IO) { tracks.subs.isNotEmpty() && runCatching { api.warm(api.subUrl(fileId, tracks.subs.first().key)) }.getOrDefault(false) }
        val ready = withTimeoutOrNull(1500) { warm.await() } == true
        if (ready) subs = tracks.subs
        load(start)
        player.playWhenReady = true
        if (!ready && warm.await()) { subs = tracks.subs; load(player.currentPosition) }
    }
    LaunchedEffect(fileId) { while (true) { delay(10_000); save() } }
    DisposableEffect(Unit) { onDispose { save(); player.release() } }

    // Schermo intero: telefono in orizzontale e barre di sistema nascoste. Uscendo dal player torna tutto com'era.
    val view = LocalView.current
    var full by remember { mutableStateOf<Boolean?>(null) } // null = tasto mai toccato: decide la rotazione automatica
    DisposableEffect(full) {
        val activity = view.context as? Activity
        val bars = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        // Uscendo con il tasto si torna in verticale anche se il telefono è ancora girato, come nelle altre app video.
        if (full == false) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (full == true) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            bars?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PlayerView(it).apply { keepScreenOn = true; setShowSubtitleButton(true); setFullscreenButtonClickListener { on -> full = on } } },
            update = {
                it.player = controls
                it.setShowPreviousButton(prevId != null)
                it.setShowNextButton(nextId != null)
            },
            modifier = Modifier.fillMaxSize(),
        )
        failed?.let { code -> Text(
            "Riproduzione non riuscita su questo dispositivo.\n$code",
            Modifier.align(Alignment.TopCenter).padding(start = 32.dp, end = 32.dp, top = 96.dp), color = Color.White, textAlign = TextAlign.Center,
        ) }
        if (stuck && failed == null) AndroidView(
            factory = { c -> TextView(c).apply { setTextColor(android.graphics.Color.WHITE); textSize = 12f; DebugTextViewHelper(player, this).start() } },
            modifier = Modifier.align(Alignment.TopCenter).padding(start = 32.dp, end = 32.dp, top = 160.dp),
        )
        IconButton(onClick = onBack, Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = Color.White)
        }
    }
}
