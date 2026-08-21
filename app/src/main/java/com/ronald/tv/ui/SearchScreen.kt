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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/**
 * The site's "جستجوی پیشرفته" is a form, so opening its URL as a listing just showed a
 * default page of unrelated films. This drives `/search/?s=` instead.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    repo: Repo,
    menu: List<MenuGroup>,
    onOpen: (Title) -> Unit,
    onHome: () -> Unit,
    onSelectMenu: (MenuGroup) -> Unit,
) {
    BackHandler { onHome() }

    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Title>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run() {
        val q = query.trim()
        if (q.isEmpty()) return
        loading = true; error = null
        scope.launch {
            runCatching { repo.search(q) }
                .onSuccess { results = it }
                .onFailure { error = it.message ?: "جستجو ناموفق بود" }
            loading = false
        }
    }

    Row(Modifier.fillMaxSize()) {
        NavRail(menu = menu, onHome = onHome, onSelect = onSelectMenu)
        Column(Modifier.fillMaxHeight().weight(1f).padding(16.dp)) {
            Text("جستجو", style = MaterialTheme.typography.titleLarge)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { androidx.compose.material3.Text("نام فیلم یا سریال") },
                    modifier = Modifier.width(420.dp),
                )
                Button(onClick = { run() }) { Text("جستجو") }
            }
            Box(Modifier.fillMaxSize()) {
                val list = results
                when {
                    loading -> CenterMessage("در حال جستجو…")
                    error != null -> CenterMessage("خطا: $error")
                    list == null -> CenterMessage("عبارتی وارد کنید.")
                    list.isEmpty() -> CenterMessage("نتیجه‌ای یافت نشد.")
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(list) { t -> PosterCard(t) { onOpen(t) } }
                    }
                }
            }
        }
    }
}
