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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ronald.tv.data.MenuGroup
import com.ronald.tv.data.Repo

/**
 * The awards index is ~1100 ceremony-and-year links, which is unusable as a flat list.
 * Grouped by ceremony with its years newest-first, it becomes one row per award.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AwardsScreen(
    repo: Repo,
    menu: List<MenuGroup>,
    onOpenCeremony: (String, String) -> Unit,
    onHome: () -> Unit,
    onSelectMenu: (MenuGroup) -> Unit,
) {
    BackHandler { onHome() }

    var shows by remember { mutableStateOf<List<MenuGroup>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { repo.awards() }
            .onSuccess { shows = it }
            .onFailure { error = it.message ?: "بارگذاری ناموفق بود" }
    }

    Row(Modifier.fillMaxSize()) {
        NavRail(menu = menu, onHome = onHome, onSelect = onSelectMenu)
        Column(Modifier.fillMaxHeight().weight(1f)) {
            Text(
                "مراسم و جوایز",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(horizontal = CONTENT_PAD, vertical = 12.dp),
            )
            val list = shows
            when {
                error != null -> CenterMessage("خطا: $error")
                list == null -> CenterMessage("در حال بارگذاری…")
                list.isEmpty() -> CenterMessage("موردی یافت نشد.")
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(list) { show ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                show.title,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = CONTENT_PAD),
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = CONTENT_PAD
                                ),
                            ) {
                                items(show.items) { year ->
                                    Button(onClick = {
                                        onOpenCeremony("${show.title} ${year.title}", year.url)
                                    }) { Text(year.title) }
                                }
                            }
                        }
                    }
                    item { Box(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
