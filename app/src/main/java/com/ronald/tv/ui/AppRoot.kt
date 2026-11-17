package com.ronald.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ronald.tv.data.HomeData
import com.ronald.tv.data.MenuGroup
import com.ronald.tv.data.Repo
import com.ronald.tv.data.StreamRef
import com.ronald.tv.data.Title
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Loading : Screen
    data class Login(val error: String? = null) : Screen
    data object Home : Screen
    data class Section(val group: MenuGroup) : Screen
    data object Awards : Screen
    data object Search : Screen
    data object Profile : Screen
    data class Listing(val heading: String, val url: String) : Screen
    data class Detail(val title: Title) : Screen
    data class Player(val url: String, val label: String, val stream: StreamRef?) : Screen
}

/**
 * Two menu entries are not listings: /awards/ is an index of ceremonies, and the advanced
 * search page is a form. Both get their own screen.
 */
private fun screenFor(group: MenuGroup): Screen {
    val url = group.url ?: group.items.firstOrNull()?.url.orEmpty()
    return when {
        url.contains("/awards") -> Screen.Awards
        url.contains("advancedSearch") -> Screen.Search
        else -> Screen.Section(group)
    }
}

/** A person opens their filmography; everything else opens its detail page. */
private fun openFor(title: Title): Screen =
    if (title.isPerson) Screen.Listing(title.name, title.url.removePrefix(com.ronald.tv.data.Site.BASE))
    else Screen.Detail(title)

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val repo = remember { Repo(context) }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<Screen>(Screen.Loading) }

    // Slider, rows and the menu all come from one homepage fetch, shared by every screen.
    var home by remember { mutableStateOf<HomeData?>(null) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun loadHome() {
        runCatching { repo.homeData() }
            .onSuccess { home = it; homeError = null }
            .onFailure { homeError = it.message ?: "بارگذاری ناموفق بود" }
    }

    LaunchedEffect(Unit) {
        if (repo.ensureSession()) {
            screen = Screen.Home
            loadHome()
        } else {
            screen = Screen.Login()
        }
    }

    when (val s = screen) {
        is Screen.Loading -> CenterMessage("در حال اتصال…")

        is Screen.Login -> LoginScreen(
            error = s.error,
            busy = busy,
            onSubmit = { u, p, m ->
                scope.launch {
                    busy = true
                    val err = runCatching { repo.login(u, p, m) }
                        .getOrElse { it.message ?: "خطای شبکه" }
                    busy = false
                    if (err == null) {
                        screen = Screen.Home
                        loadHome()
                    } else {
                        screen = Screen.Login(err)
                    }
                }
            },
        )

        is Screen.Home -> HomeScreen(
            data = home,
            error = homeError,
            onOpen = { open -> screen = openFor(open) },
            onSelectMenu = { screen = screenFor(it) },
            onProfile = { screen = Screen.Profile },
        )

        is Screen.Section -> SectionScreen(
            repo = repo,
            group = s.group,
            menu = home?.menu.orEmpty(),
            // The site shows "سریال های محبوب" on its homepage; surface it at the top of
            // the series section instead of burying it on home.
            slider = if (s.group.title.contains("سریال")) {
                home?.rows?.firstOrNull { it.title.contains("محبوب") }?.items.orEmpty()
            } else emptyList(),
            onOpen = { open -> screen = openFor(open) },
            onHome = { screen = Screen.Home },
            onSelectMenu = { screen = screenFor(it) },
        )

        is Screen.Listing -> ListingScreen(
            repo = repo,
            heading = s.heading,
            url = s.url,
            menu = home?.menu.orEmpty(),
            onOpen = { open -> screen = openFor(open) },
            onHome = { screen = Screen.Home },
            onSelectMenu = { screen = screenFor(it) },
        )

        is Screen.Detail -> DetailScreen(
            repo = repo,
            title = s.title,
            onBack = { screen = Screen.Home },
            onPlay = { url, label, stream -> screen = Screen.Player(url, label, stream) },
        )

        is Screen.Awards -> AwardsScreen(
            repo = repo,
            menu = home?.menu.orEmpty(),
            onOpenCeremony = { heading, url -> screen = Screen.Listing(heading, url) },
            onHome = { screen = Screen.Home },
            onSelectMenu = { screen = screenFor(it) },
        )

        is Screen.Search -> SearchScreen(
            repo = repo,
            menu = home?.menu.orEmpty(),
            onOpen = { open -> screen = openFor(open) },
            onHome = { screen = Screen.Home },
            onSelectMenu = { screen = screenFor(it) },
        )

        is Screen.Profile -> ProfileScreen(
            repo = repo,
            menu = home?.menu.orEmpty(),
            onHome = { screen = Screen.Home },
            onSelectMenu = { screen = screenFor(it) },
            onLoggedOut = {
                home = null
                screen = Screen.Login()
            },
        )

        is Screen.Player -> PlayerScreen(
            repo = repo,
            url = s.url,
            label = s.label,
            stream = s.stream,
            onBack = { screen = Screen.Home },
        )
    }
}
