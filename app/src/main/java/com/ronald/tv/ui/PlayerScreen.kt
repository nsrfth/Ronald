package com.ronald.tv.ui

import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ronald.tv.data.Repo
import com.ronald.tv.data.Site
import com.ronald.tv.data.StreamRef
import com.ronald.tv.data.SubtitleRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

private const val SEEK_STEP_MS = 10_000L
private const val CONTROLS_TIMEOUT_MS = 5_000L
private const val TAG = "RonaldPlayer"

private val FG = Color(0xFFE6EDF2)
private val FG_DIM = Color(0xFF9AA7B0)
private val ACCENT = Color(0xFF7FB3CC)
private val ON_FOCUS = Color(0xFF06222E)

/** The on-screen control bar, left to right. */
private enum class Ctrl { REWIND, PLAY_PAUSE, FORWARD, SUBTITLES }

/** One row of the subtitle menu; `group == null` is the "off" row. */
private data class SubOption(
    val label: String,
    val group: TrackGroup?,
    val index: Int,
    val selected: Boolean,
)

/**
 * Playback with a remote-driven control overlay.
 *
 * Every key is routed here through [PlaybackKeys] before Compose or the PlayerView sees it
 * (focus inside an AndroidView proved unreliable), so the overlay keeps its own cursor
 * instead of relying on view focus. The PlayerView only renders video and subtitles.
 *
 * Remote:
 *  - controls hidden: Left/Right seek, OK play/pause, Up/Down show the controls
 *  - controls shown:  Left/Right move between buttons, OK presses the highlighted one
 *  - subtitle menu:   Up/Down choose, OK applies, Back closes
 *  - CC/Menu key opens the subtitle menu from anywhere
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(repo: Repo, url: String, label: String, stream: StreamRef?, onBack: () -> Unit) {
    val context = LocalContext.current
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    var controlsVisible by remember { mutableStateOf(true) }
    var ctrlCursor by remember { mutableStateOf(Ctrl.PLAY_PAUSE) }
    var subsOpen by remember { mutableStateOf(false) }
    var subsCursor by remember { mutableIntStateOf(0) }
    // Bumped on every key so the auto-hide timer restarts.
    var interaction by remember { mutableLongStateOf(0L) }

    val player = remember(url) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Site.UA)
            // /dln/ 302s to a different CDN host — without this the redirect is dropped.
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Cookie" to repo.cookieHeader(),
                    "Referer" to "${Site.BASE}/",
                )
            )

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
            .apply {
                // The site's audience reads Persian; pick a Farsi track whenever one exists.
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredTextLanguage("fa")
                    .build()
            }
    }

    // The downloadable MKV only embeds the release's own (usually English) subtitles. The
    // Persian ones live on the site's online stream of the same release, with the same
    // timing, so fetch that list first and side-load it. Never hold playback up for long.
    val scope = rememberCoroutineScope()
    LaunchedEffect(player) {
        val side: List<SubtitleRef> = if (stream == null) emptyList() else {
            val job = scope.async(Dispatchers.IO) {
                runCatching { repo.streamSubtitles(stream) }
                    .onFailure { Log.w(TAG, "stream subtitles failed", it) }
                    .getOrDefault(emptyList())
            }
            withTimeoutOrNull(SUBTITLE_FETCH_TIMEOUT_MS) { job.await() } ?: emptyList()
        }
        Log.i(TAG, "side-loaded subtitles for $stream: ${side.map { it.label }}")
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(url)
                .setSubtitleConfigurations(side.mapIndexed { i, s -> s.toConfiguration(i) })
                .build()
        )
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(t: Tracks) {
                tracks = t
                subtitleOptions(t).forEach { Log.i(TAG, "text track: ${it.label} selected=${it.selected}") }
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Hide the controls after a few idle seconds, but never while paused or in the menu.
    LaunchedEffect(controlsVisible, interaction, isPlaying, subsOpen) {
        if (controlsVisible && isPlaying && !subsOpen) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    fun togglePlay() { if (player.isPlaying) player.pause() else player.play() }
    fun seekBy(ms: Long) {
        val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + ms).coerceIn(0L, dur))
    }
    fun openSubs() {
        subsCursor = subtitleOptions(tracks).indexOfFirst { it.selected }.coerceAtLeast(0)
        subsOpen = true
    }
    fun showControls(focus: Ctrl = ctrlCursor) {
        ctrlCursor = focus
        controlsVisible = true
    }

    DisposableEffect(player) {
        val handler: (Int) -> Boolean = handler@{ code ->
            interaction++
            Log.d(TAG, "key $code subs=$subsOpen controls=$controlsVisible cursor=$ctrlCursor")

            // Transport keys on the remote work in every state.
            when (code) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlay(); return@handler true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); return@handler true }
                KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> {
                    player.pause(); return@handler true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(SEEK_STEP_MS); return@handler true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-SEEK_STEP_MS); return@handler true }
            }

            if (subsOpen) {
                val options = subtitleOptions(tracks)
                when (code) {
                    KeyEvent.KEYCODE_DPAD_UP ->
                        subsCursor = (subsCursor - 1).coerceAtLeast(0)
                    KeyEvent.KEYCODE_DPAD_DOWN ->
                        subsCursor = (subsCursor + 1).coerceAtMost(options.lastIndex)
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        options.getOrNull(subsCursor)?.let { selectSubtitle(player, it) }
                        subsOpen = false
                    }
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_CAPTIONS,
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT ->
                        subsOpen = false
                }
                return@handler true
            }

            if (code == KeyEvent.KEYCODE_CAPTIONS || code == KeyEvent.KEYCODE_MENU) {
                showControls(Ctrl.SUBTITLES)
                openSubs()
                return@handler true
            }

            if (controlsVisible) {
                val all = Ctrl.entries
                when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT ->
                        ctrlCursor = all[(ctrlCursor.ordinal - 1).coerceAtLeast(0)]
                    KeyEvent.KEYCODE_DPAD_RIGHT ->
                        ctrlCursor = all[(ctrlCursor.ordinal + 1).coerceAtMost(all.lastIndex)]
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> when (ctrlCursor) {
                        Ctrl.REWIND -> seekBy(-SEEK_STEP_MS)
                        Ctrl.PLAY_PAUSE -> togglePlay()
                        Ctrl.FORWARD -> seekBy(SEEK_STEP_MS)
                        Ctrl.SUBTITLES -> openSubs()
                    }
                    // The subtitle button sits at the end of the bar; Down jumps straight to it.
                    KeyEvent.KEYCODE_DPAD_DOWN -> ctrlCursor = Ctrl.SUBTITLES
                    KeyEvent.KEYCODE_DPAD_UP -> ctrlCursor = Ctrl.PLAY_PAUSE
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> controlsVisible = false
                    else -> return@handler false
                }
                return@handler true
            }

            // Controls hidden.
            when (code) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    togglePlay(); showControls(Ctrl.PLAY_PAUSE); true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(SEEK_STEP_MS); showControls(Ctrl.FORWARD); true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-SEEK_STEP_MS); showControls(Ctrl.REWIND); true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    showControls(Ctrl.PLAY_PAUSE); true
                }
                else -> false
            }
        }
        PlaybackKeys.register(handler)
        onDispose { PlaybackKeys.unregister(handler) }
    }

    BackHandler { onBack() }

    // Lift subtitles above the control bar while it is on screen.
    LaunchedEffect(playerView, controlsVisible) {
        playerView?.subtitleView?.setBottomPaddingFraction(
            if (controlsVisible) 0.30f else androidx.media3.ui.SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    // Our own overlay replaces the stock controller, which the remote
                    // could not reach.
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    keepScreenOn = true
                    playerView = this
                }
            },
        )

        if (controlsVisible || subsOpen) {
            ControlsOverlay(
                player = player,
                label = label,
                isPlaying = isPlaying,
                buffering = buffering,
                cursor = ctrlCursor,
                subtitleLabel = subtitleOptions(tracks).firstOrNull { it.selected }?.label?.substringBefore(" · "),
            )
        }

        if (subsOpen) {
            SubtitleMenu(subtitleOptions(tracks), subsCursor)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ControlsOverlay(
    player: Player,
    label: String,
    isPlaying: Boolean,
    buffering: Boolean,
    cursor: Ctrl,
    subtitleLabel: String?,
) {
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration
            delay(500)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Title across the top.
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .padding(horizontal = 48.dp, vertical = 28.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                color = FG,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Media controls read left-to-right even in an RTL app.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .padding(start = 48.dp, end = 48.dp, top = 48.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProgressBar(position, duration)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatTime(position)} / ${formatTime(duration)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = FG,
                        modifier = Modifier.width(280.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    CtrlButton(cursor == Ctrl.REWIND) { focused ->
                        Text("« 10", style = MaterialTheme.typography.titleMedium, color = fgFor(focused))
                    }
                    Spacer(Modifier.width(16.dp))
                    CtrlButton(cursor == Ctrl.PLAY_PAUSE) { focused ->
                        if (isPlaying) PauseGlyph(fgFor(focused))
                        else Icon(Icons.Filled.PlayArrow, contentDescription = "پخش", tint = fgFor(focused))
                    }
                    Spacer(Modifier.width(16.dp))
                    CtrlButton(cursor == Ctrl.FORWARD) { focused ->
                        Text("10 »", style = MaterialTheme.typography.titleMedium, color = fgFor(focused))
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.width(280.dp), contentAlignment = Alignment.CenterEnd) {
                        CtrlButton(cursor == Ctrl.SUBTITLES) { focused ->
                            Text(
                                "زیرنویس" + (subtitleLabel?.let { ": $it" } ?: ""),
                                style = MaterialTheme.typography.titleMedium,
                                color = fgFor(focused),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        if (buffering) {
            Text(
                "در حال بارگذاری…",
                style = MaterialTheme.typography.titleMedium,
                color = FG,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private fun fgFor(focused: Boolean) = if (focused) ON_FOCUS else FG

@Composable
private fun CtrlButton(focused: Boolean, content: @Composable (Boolean) -> Unit) {
    Box(
        Modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (focused) FG else Color(0x33FFFFFF))
            .then(if (focused) Modifier else Modifier.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp)))
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { content(focused) }
}

@Composable
private fun PauseGlyph(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(width = 6.dp, height = 22.dp).background(color))
        Box(Modifier.size(width = 6.dp, height = 22.dp).background(color))
    }
}

@Composable
private fun ProgressBar(position: Long, duration: Long) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0x55FFFFFF))
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(ACCENT))
    }
}

private fun formatTime(ms: Long): String {
    if (ms == C.TIME_UNSET || ms < 0) return "--:--"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SubtitleMenu(options: List<SubOption>, cursor: Int) {
    val listState = rememberLazyListState()
    LaunchedEffect(cursor) { if (cursor >= 0) listState.animateScrollToItem(cursor) }

    // Layout is RTL, so Start is the right-hand edge of the screen.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(340.dp)
                .background(Color(0xF00E1418))
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    Text(
                        "زیرنویس",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                if (options.size <= 1) {
                    item {
                        Text(
                            "این فایل زیرنویس ندارد",
                            style = MaterialTheme.typography.bodyLarge,
                            color = FG_DIM,
                        )
                    }
                }
                itemsIndexed(options) { i, opt ->
                    val focused = i == cursor
                    val fg = fgFor(focused)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (focused) FG else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            if (opt.selected) "✓" else "",
                            style = MaterialTheme.typography.titleMedium,
                            color = fg,
                            modifier = Modifier.width(28.dp),
                        )
                        Text(opt.label, style = MaterialTheme.typography.titleMedium, color = fg)
                    }
                }
            }
        }
    }
}

/** "Off" followed by every playable text track in the file. */
@androidx.annotation.OptIn(UnstableApi::class)
private fun subtitleOptions(tracks: Tracks): List<SubOption> {
    val site = mutableListOf<SubOption>()
    val embedded = mutableListOf<SubOption>()
    var anySelected = false
    var n = 0
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            n++
            val format = group.getTrackFormat(i)
            val selected = group.isTrackSelected(i)
            anySelected = anySelected || selected
            val opt = SubOption(trackLabel(format, n), group.mediaTrackGroup, i, selected)
            if (format.isSiteSubtitle()) site += opt else embedded += opt
        }
    }
    // The site's files (Farsi first) lead; the MKV's own tracks follow, marked as such
    // so a site "انگلیسی" and an embedded "انگلیسی" can be told apart.
    val marked = if (site.isEmpty()) embedded else embedded.map { it.copy(label = it.label + " (داخل فایل)") }
    return listOf(SubOption("خاموش", null, -1, !anySelected)) + site + marked
}

private val PERSIAN = Locale("fa")

private const val SITE_SUB_ID = "site-sub-"
private const val SUBTITLE_FETCH_TIMEOUT_MS = 10_000L

private fun androidx.media3.common.Format.isSiteSubtitle(): Boolean =
    id?.contains(SITE_SUB_ID) == true

/**
 * A site subtitle as a side-loaded track. The site names them in English ("Farsi",
 * "English", "English + Farsi"); show Persian names and default to the Farsi-only file.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun SubtitleRef.toConfiguration(index: Int): MediaItem.SubtitleConfiguration {
    val name = label.lowercase(Locale.ROOT)
    val hasFa = "farsi" in name || "persian" in name
    val hasEn = "english" in name
    val (lang, persianLabel) = when {
        hasFa && hasEn -> null to "انگلیسی + فارسی"
        hasFa -> "fa" to "فارسی"
        hasEn -> "en" to "انگلیسی"
        else -> null to label.ifBlank { "زیرنویس ${index + 1}" }
    }
    return MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(url))
        .setMimeType(MimeTypes.APPLICATION_SUBRIP)
        .setId("$SITE_SUB_ID$index")
        .setLanguage(lang)
        .setLabel(persianLabel)
        .setSelectionFlags(if (lang == "fa") C.SELECTION_FLAG_DEFAULT else 0)
        .build()
}

private fun trackLabel(format: androidx.media3.common.Format, n: Int): String {
    if (format.isSiteSubtitle()) return format.label ?: "زیرنویس $n"
    val lang = format.language?.takeIf { it.isNotBlank() && it != "und" }
        ?.let { Locale.forLanguageTag(it).getDisplayLanguage(PERSIAN).ifBlank { it } }
    val parts = listOfNotNull(lang, format.label?.takeIf { it.isNotBlank() })
        .distinct()
    val forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
    val base = parts.joinToString(" · ").ifEmpty { "زیرنویس $n" }
    return if (forced) "$base (اجباری)" else base
}

private fun selectSubtitle(player: Player, opt: SubOption) {
    val params = player.trackSelectionParameters.buildUpon()
    player.trackSelectionParameters = if (opt.group == null) {
        params.clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    } else {
        params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(opt.group, opt.index))
            .build()
    }
}
