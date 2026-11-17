package com.ronald.tv.data

enum class Kind(val path: String) { FILM("film"), SERIES("series"), PERSON("person") }

/** One horizontal carousel on the home screen, mirroring a section of the site's homepage. */
data class HomeRow(val title: String, val items: List<Title>)

/** A leaf entry in the site's top menu. */
data class MenuItem(val title: String, val url: String)

/**
 * A top-level menu entry. Bootstrap dropdowns become groups with children; plain links
 * become groups with a single item pointing at themselves.
 */
data class MenuGroup(val title: String, val url: String?, val items: List<MenuItem>) {
    val isGroup: Boolean get() = items.size > 1 || url == null
}

/** Everything the homepage gives us in one fetch. */
data class HomeData(
    val slider: List<Title>,
    val rows: List<HomeRow>,
    val menu: List<MenuGroup>,
)

/** A card in a listing. */
data class Title(
    val postId: String,
    val slug: String,
    val kind: Kind,
    val name: String,
    val year: String?,
    val posterPath: String?,   // site-relative, e.g. /img/354-532/abc.jpg.webp
) {
    val display: String get() = if (year != null) "$name ($year)" else name

    val url: String get() = when (kind) {
        // A person is a listing of their films, not a post.
        Kind.PERSON -> "${Site.BASE}/archive/person/$postId/$slug/"
        else -> "${Site.BASE}/post/${kind.path}/$postId/$slug/"
    }

    val isPerson: Boolean get() = kind == Kind.PERSON

    /**
     * Posters live at /img/{w}-{h}/{hash}.jpg.webp, but each one is generated at exactly
     * ONE size — and which size varies per image. Asking for the wrong one returns a
     * soft-404 (HTTP 200 with the body "404"), so we can't just pick a size we like.
     *
     * The path the site gave us (driven by the `scr` devicePixelRatio cookie, which we
     * set to 2) is right for most images; 177-266 covers most of the rest. Some posters
     * are missing from the site entirely, so callers must handle "none of these load".
     */
    /**
     * Width/height ratio of this item's image, read straight out of its /img/{w}-{h}/ path.
     * Rows differ: posters are ~0.67, "سریال های بروز شده" uses 16:9 stills, and
     * "مشاهدات اخیر" uses ultra-wide banners. Cards must size themselves accordingly or
     * the wide images get center-cropped into a sliver.
     */
    val aspect: Float
        get() {
            val m = posterPath?.let { Site.IMG_DIMS_RE.find(it) } ?: return PORTRAIT_ASPECT
            val w = m.groupValues[1].toFloatOrNull() ?: return PORTRAIT_ASPECT
            val h = m.groupValues[2].toFloatOrNull() ?: return PORTRAIT_ASPECT
            return if (h > 0f) w / h else PORTRAIT_ASPECT
        }

    fun posterCandidates(): List<String> {
        val path = posterPath ?: return emptyList()
        val primary = Site.BASE + path
        // The alternate sizes are all portrait, so they are only valid fallbacks for a
        // portrait image — offering them for a 16:9 still would swap in the wrong shape.
        if (aspect >= 1f) return listOf(primary)
        val sizes = listOf("354-532", "312-468", "177-266")
        return (listOf(primary) +
            sizes.map { Site.BASE + Site.IMG_SIZE_RE.replace(path, "/img/$it/") })
            .distinct()
    }

    /** For the hero: try a 16:9 still first, then fall back to the poster. */
    fun backdropCandidates(): List<String> {
        val path = posterPath ?: return emptyList()
        val wide = Site.BASE + Site.IMG_SIZE_RE.replace(path, "/img/1504-846/")
        return (listOf(wide) + posterCandidates()).distinct()
    }

    private companion object { const val PORTRAIT_ASPECT = 2f / 3f }
}

/** A direct-download MKV/MP4 — the path the player actually uses. */
data class DownloadLink(
    val url: String,
    val fileName: String,
    val heightPx: Int?,        // 2160 / 1080 / 720, parsed from the filename
    val isDubbed: Boolean,     // Persian dub (KIMO.DUBLE) vs original audio
    val season: Int?,          // from SxxEyy in the filename; null for films
    val episode: Int?,
    /** The site's online stream of the same release — where its subtitles live. */
    val stream: StreamRef? = null,
) {
    val label: String
        get() {
            val q = heightPx?.let { "${it}p" } ?: "?"
            return if (isDubbed) "$q دوبله" else q
        }
}

/** The site's own MSE stream endpoint. Not used for playback, only to find subtitles. */
data class StreamOption(val streamId: String, val postId: String, val lang: String, val height: Int)

/**
 * Identifies one online stream: `/stream/{streamId}/{postId}/?lang={lang}`. Its player page
 * lists the site's subtitle files (Farsi, English, English + Farsi), which are not embedded
 * in the downloadable MKV but share its timing.
 */
data class StreamRef(val streamId: String, val postId: String, val lang: String) {
    fun pagePath(): String = "/stream/$streamId/$postId/?lang=$lang&h=720"
}

/** A side-loaded subtitle file; `label` is the site's own name for it (e.g. "Farsi"). */
data class SubtitleRef(val url: String, val label: String)

/** A cast/crew member on a detail page. */
data class CastMember(val name: String, val url: String, val imagePath: String?) {
    fun image(): String? = imagePath?.let { Site.BASE + it }
}

/** Label/value facts from the detail page (مدت، امتیاز، کشور، زبان …). */
data class Fact(val label: String, val value: String)

/** The signed-in account, from /user/panel/. */
data class Profile(
    val username: String?,
    val status: String?,          // "مشترک فعال"
    val facts: List<Fact>,        // شناسه، عضویت، اعتبار
    val avatarPath: String?,
) {
    fun avatar(): String? = avatarPath?.let { Site.BASE + it }
}

data class Detail(
    val postId: String,
    val name: String,
    val plot: String?,
    val posterPath: String?,
    val downloads: List<DownloadLink>,
    val streams: List<StreamOption>,
    val subtitles: List<SubtitleRef>,
    val facts: List<Fact> = emptyList(),
    val genres: List<MenuItem> = emptyList(),
    val cast: List<CastMember> = emptyList(),
) {
    /** The detail page's own poster, which is a proper 2:3 image. */
    fun posterCandidates(): List<String> =
        posterPath?.let { listOf(Site.BASE + it) }.orEmpty()

    val posterAspect: Float
        get() {
            val m = posterPath?.let { Site.IMG_DIMS_RE.find(it) } ?: return 2f / 3f
            val w = m.groupValues[1].toFloatOrNull() ?: return 2f / 3f
            val h = m.groupValues[2].toFloatOrNull() ?: return 2f / 3f
            return if (h > 0f) w / h else 2f / 3f
        }

    /** Series pages list every episode as its own download link. */
    val isSeries: Boolean get() = downloads.any { it.episode != null }

    val seasons: List<Int> get() =
        downloads.mapNotNull { it.season }.distinct().sorted()

    fun episodesIn(season: Int): List<Int> =
        downloads.filter { it.season == season }.mapNotNull { it.episode }.distinct().sorted()

    fun linksFor(season: Int, episode: Int): List<DownloadLink> =
        downloads.filter { it.season == season && it.episode == episode }
}
