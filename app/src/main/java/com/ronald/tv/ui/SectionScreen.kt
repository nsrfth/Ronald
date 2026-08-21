package com.ronald.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ronald.tv.data.MenuGroup
import com.ronald.tv.data.MenuItem
import com.ronald.tv.data.Repo
import com.ronald.tv.data.Title

private val SECTION_SLIDER_HEIGHT = 210.dp

/**
 * A menu section laid out like the home screen: one carousel per sub-menu entry.
 * Rows load lazily as they scroll into view, so opening "فیلم ها" does not fire
 * fourteen requests at once.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SectionScreen(
    repo: Repo,
    group: MenuGroup,
    menu: List<MenuGroup>,
    slider: List<Title> = emptyList(),
    onOpen: (Title) -> Unit,
    onHome: () -> Unit,
    onSelectMenu: (MenuGroup) -> Unit,
) {
    BackHandler { onHome() }

    // Give the slider initial focus so the first D-pad press does something useful.
    val sliderFocus = remember { FocusRequester() }
    LaunchedEffect(slider.size) {
        if (slider.isNotEmpty()) runCatching { sliderFocus.requestFocus() }
    }

    // Static /page/ articles carry no listing, so they are not shown as rows.
    val rows = remember(group) { group.items.filter { it.url.contains("/archive/") } }

    Row(Modifier.fillMaxSize()) {
        NavRail(menu = menu, onHome = onHome, onSelect = onSelectMenu)
        Column(Modifier.fillMaxHeight().weight(1f)) {
            Text(
                group.title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(horizontal = CONTENT_PAD, vertical = 12.dp),
            )
            if (rows.isEmpty()) {
                CenterMessage("این بخش فهرستی برای نمایش ندارد.")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (slider.isNotEmpty()) {
                        item { HeroSlider(slider, SECTION_SLIDER_HEIGHT, sliderFocus, onOpen) }
                    }
                    items(rows) { item -> SectionRow(repo, item, onOpen) }
                    item { Box(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionRow(repo: Repo, item: MenuItem, onOpen: (Title) -> Unit) {
    var items by remember(item.url) { mutableStateOf<List<Title>?>(null) }
    var failed by remember(item.url) { mutableStateOf(false) }

    LaunchedEffect(item.url) {
        runCatching { repo.listingByUrl(item.url, 1) }
            .onSuccess { items = it }
            .onFailure { failed = true }
    }

    // Nothing to show and nothing coming: drop the row rather than leave a gap.
    if (failed || items?.isEmpty() == true) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            item.title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth().padding(horizontal = CONTENT_PAD),
        )
        val list = items
        if (list == null) {
            Box(Modifier.height(120.dp).padding(horizontal = CONTENT_PAD)) {
                Text("در حال بارگذاری…", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = CONTENT_PAD),
            ) {
                items(list) { t -> PosterCard(t) { onOpen(t) } }
            }
        }
    }
}
