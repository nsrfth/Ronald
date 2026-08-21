package com.ronald.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter

/**
 * Posters exist at exactly one generated size, which differs per image, and some are
 * missing from the site altogether. So we try each candidate URL in turn and fall back
 * to a titled placeholder rather than showing an empty grey box.
 */
@Composable
fun PosterImage(
    candidates: List<String>,
    title: String,
    modifier: Modifier = Modifier,
) {
    var index by remember(candidates) { mutableIntStateOf(0) }
    val url = candidates.getOrNull(index)
    val painter = rememberAsyncImagePainter(model = url)
    val state = painter.state

    // A wrong-size poster comes back as HTTP 200 with the body "404", which Coil fails
    // to decode — that surfaces here as Error, so advance to the next candidate.
    LaunchedEffect(state, index) {
        if (state is AsyncImagePainter.State.Error && index < candidates.lastIndex) index++
    }

    val exhausted = url == null || (state is AsyncImagePainter.State.Error && index >= candidates.lastIndex)

    Box(modifier.background(Color(0xFF1E2B33)), contentAlignment = Alignment.Center) {
        if (exhausted) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF7FB3CC),
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            Image(
                painter = painter,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
