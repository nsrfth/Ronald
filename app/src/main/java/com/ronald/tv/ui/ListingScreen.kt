package com.ronald.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ronald.tv.data.MenuGroup
import com.ronald.tv.data.Repo
import com.ronald.tv.data.Title

/** A paginated grid for one archive URL — used for a person's filmography. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ListingScreen(
    repo: Repo,
    heading: String,
    url: String,
    menu: List<MenuGroup>,
    onOpen: (Title) -> Unit,
    onHome: () -> Unit,
    onSelectMenu: (MenuGroup) -> Unit,
) {
    BackHandler { onHome() }

    var page by remember(url) { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val items = remember { mutableStateListOf<Title>() }
    val gridState = rememberLazyGridState()

    LaunchedEffect(url, page) {
        loading = true; error = null; items.clear()
        runCatching { repo.listingByUrl(url, page) }
            .onSuccess { items.addAll(it) }
            .onFailure { error = it.message ?: "بارگذاری ناموفق بود" }
        loading = false
    }

    Row(Modifier.fillMaxSize()) {
        NavRail(menu = menu, onHome = onHome, onSelect = onSelectMenu)
        Column(Modifier.fillMaxHeight().weight(1f).padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(heading, style = MaterialTheme.typography.titleLarge)
                Text("صفحه $page", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { if (page > 1) page-- }) { Text("قبلی") }
                Button(onClick = { page++ }) { Text("بعدی") }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    loading -> CenterMessage("در حال بارگذاری…")
                    error != null -> CenterMessage("خطا: $error")
                    items.isEmpty() -> CenterMessage("موردی یافت نشد.")
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(items) { t -> PosterCard(t) { onOpen(t) } }
                    }
                }
            }
        }
    }
}
