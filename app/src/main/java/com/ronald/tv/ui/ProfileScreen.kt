package com.ronald.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ronald.tv.data.MenuGroup
import com.ronald.tv.data.Profile
import com.ronald.tv.data.Repo
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ProfileScreen(
    repo: Repo,
    menu: List<MenuGroup>,
    onHome: () -> Unit,
    onSelectMenu: (MenuGroup) -> Unit,
    onLoggedOut: () -> Unit,
) {
    BackHandler { onHome() }

    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf(false) }
    val firstButton = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { repo.profile() }
            .onSuccess { profile = it }
            .onFailure { error = it.message ?: "بارگذاری ناموفق بود" }
        runCatching { firstButton.requestFocus() }
    }

    Row(Modifier.fillMaxSize()) {
        NavRail(menu = menu, onHome = onHome, onSelect = onSelectMenu)
        Column(
            Modifier.fillMaxHeight().weight(1f).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("پروفایل", style = MaterialTheme.typography.titleLarge)

            when {
                error != null -> Text("خطا: $error", color = Color(0xFFE57373))
                profile == null -> Text("در حال بارگذاری…")
                else -> {
                    val p = profile!!
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PosterImage(
                            candidates = listOfNotNull(p.avatar()),
                            title = p.username.orEmpty(),
                            modifier = Modifier.size(96.dp).clip(CircleShape),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                p.username ?: "کاربر",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            p.status?.let {
                                Text(it, color = Color(0xFF7FB3CC))
                            }
                        }
                    }
                    p.facts.forEach { f ->
                        Text("${f.label}: ${f.value}", color = Color(0xFFAFC4D0))
                    }
                }
            }

            if (!confirming) {
                Button(
                    onClick = { confirming = true },
                    modifier = Modifier.focusRequester(firstButton),
                ) { Text("خروج از حساب") }
            } else {
                Text("از حساب خارج می‌شوید؟ برای ورود دوباره به نام کاربری و رمز نیاز دارید.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { scope.launch { repo.logout(); onLoggedOut() } },
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFFB4534F),
                            contentColor = Color(0xFFFFFFFF),
                        ),
                    ) { Text("بله، خارج شو") }
                    Button(onClick = { confirming = false }) { Text("انصراف") }
                }
            }
        }
    }
}
