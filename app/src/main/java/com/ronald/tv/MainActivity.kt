package com.ronald.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.ronald.tv.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RonaldTheme { AppRoot() } }
    }

    /** Give playback first refusal on the remote while the player is on screen. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            com.ronald.tv.ui.PlaybackKeys.dispatch(event.keyCode)
        ) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RonaldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF7FB3CC),
            onPrimary = Color(0xFF06222E),
            background = Color(0xFF0E1418),
            onBackground = Color(0xFFE6EDF2),
            surface = Color(0xFF17222A),
            onSurface = Color(0xFFE6EDF2),
        )
    ) {
        // The login screen's OutlinedTextFields come from Compose Material3, which has its
        // own theme lookup — without this they default to a light palette on our dark UI.
        androidx.compose.material3.MaterialTheme(
            colorScheme = androidx.compose.material3.darkColorScheme(
                primary = Color(0xFF7FB3CC),
                onPrimary = Color(0xFF06222E),
                background = Color(0xFF0E1418),
                onBackground = Color(0xFFE6EDF2),
                surface = Color(0xFF17222A),
                onSurface = Color(0xFFE6EDF2),
            )
        ) {
            // Bare Text() outside a tv-material Surface picks up LocalContentColor, which
            // defaults dark — headings were rendering near-invisible on the dark background.
            // The site and its content are Persian; RTL puts the nav rail on the right
            // and right-aligns row headings, matching how TV apps here are laid out.
            CompositionLocalProvider(
                LocalContentColor provides Color(0xFFE6EDF2),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().background(Color(0xFF0E1418))
                ) { content() }
            }
        }
    }
}
