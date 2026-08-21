package com.ronald.tv.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ronald.tv.data.MenuGroup

/**
 * The site's top navigation, moved into a sidebar. Collapsed it is an icon rail; when any
 * entry takes focus it widens to show the Persian labels, as TV apps here do.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NavRail(
    menu: List<MenuGroup>,
    onHome: () -> Unit,
    onSelect: (MenuGroup) -> Unit,
    onProfile: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .width(if (expanded) 220.dp else 72.dp)
            .animateContentSize()
            .fillMaxHeight()
            .background(Color(0xFF121C22))
            .padding(vertical = 20.dp)
            .verticalScroll(rememberScrollState())
            .onFocusChanged { expanded = it.hasFocus },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Text("R", color = Color(0xFF7FB3CC))
        }
        RailItem(Icons.Filled.Home, "خانه", expanded, onHome)
        menu.forEach { group ->
            RailItem(iconFor(group.title), group.title, expanded) { onSelect(group) }
        }
        onProfile?.let { RailItem(Icons.Filled.AccountCircle, "پروفایل", expanded, it) }
    }
}

/** The menu is scraped, so match on its Persian labels with a sensible default. */
private fun iconFor(title: String): ImageVector = when {
    title.contains("فیلم") -> Icons.Filled.PlayArrow
    title.contains("سریال") -> Icons.Filled.List
    title.contains("هنرمند") -> Icons.Filled.Person
    title.contains("جوایز") || title.contains("مراسم") -> Icons.Filled.Star
    title.contains("جستجو") -> Icons.Filled.Search
    else -> Icons.Filled.DateRange
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = if (expanded) Modifier.fillMaxWidth().padding(horizontal = 10.dp) else Modifier.size(48.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = if (expanded) 12.dp else 0.dp,
            vertical = 0.dp,
        ),
        colors = ButtonDefaults.colors(containerColor = Color(0x00000000)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(22.dp))
            if (expanded) {
                Text(
                    label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}
