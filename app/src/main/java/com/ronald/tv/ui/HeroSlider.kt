package com.ronald.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ronald.tv.data.Title
import kotlinx.coroutines.delay

private const val SLIDE_MS = 7000L
private const val SLIDE_ASPECT = 1504f / 846f

/**
 * The site's `#homeslider` carousel, at the top of the app.
 *
 * Its slides are the only true 16:9 artwork the site publishes, so this is the one place
 * a real backdrop is possible — no blurred-poster substitute needed.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HeroSlider(
    slides: List<Title>,
    height: androidx.compose.ui.unit.Dp,
    focusRequester: FocusRequester? = null,
    onOpen: (Title) -> Unit,
) {
    if (slides.isEmpty()) return
    var index by remember(slides) { mutableIntStateOf(0) }

    LaunchedEffect(slides, index) {
        delay(SLIDE_MS)
        index = (index + 1) % slides.size
    }

    val current = slides[index]

    Box(Modifier.fillMaxWidth().height(height)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // Keep the slide's own 16:9 shape so the whole frame is visible — cropping it
            // to the full content width would cut the top and bottom off.
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(SLIDE_ASPECT)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Crossfade(targetState = current, label = "slide") { slide ->
                    PosterImage(
                        candidates = slide.posterCandidates(),
                        title = "",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    current.display,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { onOpen(current) },
                        modifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
                    ) { Text("اطلاعات بیشتر") }
                    Dots(count = slides.size, active = index)
                }
            }
        }
    }
}

@Composable
private fun Dots(count: Int, active: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == active) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (i == active) Color(0xFF7FB3CC) else Color(0x66FFFFFF))
            )
        }
    }
}
