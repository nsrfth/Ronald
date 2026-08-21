package com.ronald.tv.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.ronald.tv.data.Repo
import com.ronald.tv.data.Site

private const val SEEK_STEP_MS = 10_000L

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(repo: Repo, url: String, label: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var view by remember { mutableStateOf<PlayerView?>(null) }

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
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Transport is driven straight off the remote rather than through the controller's
    // focus, so it keeps working after the controls auto-hide.
    DisposableEffect(player, view) {
        val handler: (Int) -> Boolean = handler@{ code ->
            val v = view
            v?.showController()
            when (code) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                    if (player.isPlaying) player.pause() else player.play()
                    true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); true }
                KeyEvent.KEYCODE_MEDIA_STOP -> { player.pause(); true }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    player.seekTo(player.currentPosition + SEEK_STEP_MS); true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    player.seekTo((player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L)); true
                }
                // Up/Down just reveal the controls.
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true
                else -> false
            }
        }
        PlaybackKeys.register(handler)
        onDispose { PlaybackKeys.unregister(handler) }
    }

    BackHandler {
        val v = view
        if (v != null && v.isControllerFullyVisible) v.hideController() else onBack()
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                controllerAutoShow = true
                controllerShowTimeoutMs = 6000
                setShowSubtitleButton(true)
                keepScreenOn = true
                view = this
            }
        },
    )
}
