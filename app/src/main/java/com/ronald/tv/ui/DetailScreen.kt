package com.ronald.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ronald.tv.data.CastMember
import com.ronald.tv.data.Detail
import com.ronald.tv.data.DownloadLink
import com.ronald.tv.data.Repo
import com.ronald.tv.data.StreamRef
import com.ronald.tv.data.Title

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(
    repo: Repo,
    title: Title,
    onBack: () -> Unit,
    onPlay: (String, String, StreamRef?) -> Unit,
) {
    BackHandler { onBack() }

    var detail by remember { mutableStateOf<Detail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(title.postId) {
        runCatching { repo.detail(title) }
            .onSuccess { detail = it }
            .onFailure { error = it.message ?: "Failed to load" }
    }

    val d = detail
    when {
        error != null -> CenterMessage("Error: $error")
        d == null -> CenterMessage("Loading ${title.name}…")
        else -> Row(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Use the detail page's own poster: the card we arrived from may have carried a
            // wide banner image, which stretches badly in a portrait box.
            val posterCandidates = d.posterCandidates().ifEmpty { title.posterCandidates() }
            val posterAspect = if (d.posterCandidates().isNotEmpty()) d.posterAspect else title.aspect
            PosterImage(
                candidates = posterCandidates,
                title = d.name,
                modifier = Modifier
                    .width(220.dp)
                    .height((220.dp / posterAspect).coerceIn(120.dp, 340.dp)),
            )
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(d.name, style = MaterialTheme.typography.headlineMedium)

                if (d.facts.isNotEmpty()) {
                    Text(
                        d.facts.joinToString("   •   ") { "${it.label}: ${it.value}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFAFC4D0),
                    )
                }

                if (d.genres.isNotEmpty()) {
                    Text(
                        d.genres.joinToString("، ") { it.title },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7FB3CC),
                    )
                }

                d.plot?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

                if (d.cast.isNotEmpty()) {
                    Text("عوامل", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(d.cast.take(20)) { c -> CastChip(c) }
                    }
                }

                when {
                    d.downloads.isEmpty() -> Text(
                        "No playable link found. If the site changed its markup, fix DLN_LINK in Scraper.kt.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    d.isSeries -> SeriesPicker(d, onPlay)
                    else -> QualityRow(d.downloads) { link ->
                        onPlay(link.url, "${d.name} · ${link.label}", link.stream)
                    }
                }

                Button(onClick = onBack) { Text("Back") }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeriesPicker(d: Detail, onPlay: (String, String, StreamRef?) -> Unit) {
    val seasons = d.seasons
    var season by remember(d.postId) { mutableStateOf(seasons.firstOrNull() ?: 1) }
    var episode by remember(d.postId) { mutableStateOf<Int?>(null) }

    if (seasons.size > 1) {
        Text("Season", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            seasons.forEach { s ->
                Button(onClick = { season = s; episode = null }) { Text("S$s") }
            }
        }
    }

    val episodes = d.episodesIn(season)
    Text("Episodes (${episodes.size})", style = MaterialTheme.typography.titleMedium)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(episodes) { e ->
            Button(onClick = { episode = e }) { Text("E" + e.toString().padStart(2, '0')) }
        }
    }

    episode?.let { e ->
        val links = d.linksFor(season, e)
        // Built separately: formatting a string that already contains the title would
        // blow up on any title containing a literal '%'.
        val tag = "S" + season.toString().padStart(2, '0') + "E" + e.toString().padStart(2, '0')
        Text(tag, style = MaterialTheme.typography.titleMedium)
        QualityRow(links) { link ->
            onPlay(link.url, "${d.name} · $tag · ${link.label}", link.stream)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CastChip(c: CastMember) {
    Column(
        Modifier.width(72.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PosterImage(
            candidates = listOfNotNull(c.image()),
            title = c.name,
            modifier = Modifier.size(72.dp).clip(CircleShape),
        )
        Text(
            c.name,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFAFC4D0),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QualityRow(links: List<DownloadLink>, onPick: (DownloadLink) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        links.forEach { link -> Button(onClick = { onPick(link) }) { Text(link.label) } }
    }
}
