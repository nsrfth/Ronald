package com.ronald.tv.ui

/**
 * Remote handling for playback, registered by the player screen and consulted by the
 * activity before anything else sees the key.
 *
 * Compose focus inside an `AndroidView` proved unreliable: once the player controller
 * auto-hid, neither the embedded `PlayerView` nor the Compose node reliably held key
 * focus, so the remote stopped affecting playback after a couple of minutes. Intercepting
 * at the activity sidesteps focus entirely.
 */
object PlaybackKeys {
    private var handler: ((Int) -> Boolean)? = null

    fun register(h: (Int) -> Boolean) { handler = h }

    fun unregister(h: (Int) -> Boolean) { if (handler === h) handler = null }

    fun dispatch(keyCode: Int): Boolean = handler?.invoke(keyCode) ?: false
}
