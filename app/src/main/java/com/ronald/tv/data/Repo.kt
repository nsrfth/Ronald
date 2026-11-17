package com.ronald.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Repo(context: Context) {
    val session = Session(context)

    suspend fun ensureSession(): Boolean = withContext(Dispatchers.IO) {
        session.bootstrap()
        session.isAuthenticated()
    }

    /** null on success, otherwise a message to show the user. */
    suspend fun login(user: String, pass: String, mobile: String): String? =
        withContext(Dispatchers.IO) { session.login(user, pass, mobile) }

    /** Slider, rows and the top menu all come from one homepage fetch. */
    suspend fun homeData(): HomeData = withContext(Dispatchers.IO) {
        val html = session.get("/")
        HomeData(
            slider = Scraper.parseSlider(html),
            rows = Scraper.parseHome(html),
            menu = Scraper.parseMenu(html),
        )
    }

    suspend fun profile(): Profile = withContext(Dispatchers.IO) {
        Scraper.parseProfile(session.get("/user/panel/"))
    }

    /** Sign out on the site, then drop the stored session so the app asks again. */
    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching { session.get("/user/logout/") }
        session.cookies.clear()
    }

    /** Award ceremonies, grouped by show with their years. */
    suspend fun awards(): List<MenuGroup> = withContext(Dispatchers.IO) {
        Scraper.parseAwards(session.get("/awards/"))
    }

    /** Site search. The advanced-search page is a form, so we drive /search/ directly. */
    suspend fun search(query: String): List<Title> = withContext(Dispatchers.IO) {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        Scraper.parseListing(session.get("/search/?s=$q"))
    }

    /** Any archive-shaped URL from the menu. */
    suspend fun listingByUrl(url: String, page: Int): List<Title> = withContext(Dispatchers.IO) {
        Scraper.parseListing(session.get(Scraper.paged(url, page)))
    }

    suspend fun listing(kind: Kind, page: Int): List<Title> = withContext(Dispatchers.IO) {
        Scraper.parseListing(session.get(Scraper.archiveUrl(kind, page)))
    }

    suspend fun detail(title: Title): Detail = withContext(Dispatchers.IO) {
        Scraper.parseDetail(session.get(title.url), title.postId, title.name)
    }

    /** The subtitle files (Farsi, English, …) the site's own player offers for a stream. */
    suspend fun streamSubtitles(ref: StreamRef): List<SubtitleRef> = withContext(Dispatchers.IO) {
        Scraper.parseStreamSubtitles(session.get(ref.pagePath()))
    }

    /** Cookie header so ExoPlayer's data source inherits the same session. */
    fun cookieHeader(): String = session.cookies.headerValue()
}
