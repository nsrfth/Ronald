package com.ronald.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ronald.tv.data.HomeData
import com.ronald.tv.data.HomeRow
import com.ronald.tv.data.MenuGroup
import com.ronald.tv.data.Title

internal val CARD_WIDTH = 120.dp
internal val LANDSCAPE_CARD_WIDTH = 220.dp
internal val BANNER_CARD_WIDTH = 340.dp
internal val CARD_GAP = 16.dp
internal val CONTENT_PAD = 24.dp
private val SLIDER_HEIGHT = 260.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    data: HomeData?,
    error: String?,
    onOpen: (Title) -> Unit,
    onSelectMenu: (MenuGroup) -> Unit,
    onProfile: () -> Unit,
) {
    // Without this the first D-pad press is spent giving focus to something arbitrary.
    val heroFocus = remember { FocusRequester() }
    LaunchedEffect(data?.slider?.size) {
        if (!data?.slider.isNullOrEmpty()) {
            runCatching { heroFocus.requestFocus() }
        }
    }

    Row(Modifier.fillMaxSize()) {
        NavRail(
            menu = data?.menu.orEmpty(),
            onHome = {},
            onSelect = onSelectMenu,
            onProfile = onProfile,
        )
        Box(Modifier.fillMaxHeight().weight(1f)) {
            when {
                error != null -> CenterMessage("خطا: $error")
                data == null -> CenterMessage("در حال بارگذاری…")
                data.rows.isEmpty() && data.slider.isEmpty() ->
                    CenterMessage("چیزی یافت نشد — احتمالاً ساختار سایت تغییر کرده (Scraper.kt)")
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (data.slider.isNotEmpty()) {
                        item { HeroSlider(data.slider, SLIDER_HEIGHT, heroFocus, onOpen) }
                    }
                    items(data.rows) { row -> CarouselRow(row, onOpen) }
                    item { Box(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun CarouselRow(row: HomeRow, onOpen: (Title) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            row.title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth().padding(horizontal = CONTENT_PAD),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = CONTENT_PAD),
        ) {
            items(row.items) { t -> PosterCard(t) { onOpen(t) } }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PosterCard(title: Title, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "cardScale")

    // Rows carry different artwork shapes, so each card follows its own image.
    val aspect = title.aspect
    val width = when {
        aspect < 1f -> CARD_WIDTH
        aspect < 3f -> LANDSCAPE_CARD_WIDTH
        else -> BANNER_CARD_WIDTH
    }
    val height = (width / aspect).coerceAtLeast(40.dp)

    Column(
        Modifier
            .width(width)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .onFocusChanged { focused = it.isFocused },
        ) {
            PosterImage(
                candidates = title.posterCandidates(),
                title = title.name,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            title.display,
            style = MaterialTheme.typography.bodySmall,
            color = if (focused) Color(0xFFFFFFFF) else Color(0xFFAFC4D0),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
