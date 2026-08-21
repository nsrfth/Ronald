package com.ronald.tv.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * EVERY site-specific selector and regex lives here.
 *
 * The site is server-rendered PHP with no API, so this file is the only thing that
 * breaks when they redesign a page. If the app suddenly shows empty lists or a title
 * with no play options, fix it here — nothing else should need to change.
 *
 * Verified against the live site on 2026-08-21.
 */
object Scraper {

    // ---- Selectors -------------------------------------------------------

    /** Listing card container on /archive/{film,series}/{page}/ */
    private const val SEL_CARD = "div.postMeta"
    private const val SEL_POST_LINK = "a[href*=/post/]"
    private const val SEL_IMG = "img"

    // ---- Regexes ---------------------------------------------------------

    /** /post/film/5737353/toy-story-5/ */
    private val POST_HREF = Regex("""/post/(film|series)/(\d+)/([^/]+)/""")

    /**
     * Direct-download link, from an inline onclick:
     *   javascript:lnk='https://.../dln/{token}/{postId}/{fileId}/Name.mkv';listLcLNK(lnk, 0);
     * This is what we hand to ExoPlayer — it 302s to a CDN serving a progressive
     * MKV with byte-range support.
     */
    private val DLN_LINK = Regex("""lnk\s*=\s*'(https?://[^']*?/dln/[^']+)'""")

    /**
     * The site's own MSE stream endpoint:
     *   javascript:tmpURL = '/stream/359568/5737353/?lang=original&h=2160';
     * Parsed for completeness; playback uses DLN_LINK instead.
     */
    private val STREAM_OPT =
        Regex("""/stream/(\d+)/(\d+)/\?lang=([^&'"]+)&h=(\d+)""")

    /** Quality from a release filename: Toy.Story.5.2026.1080p.WEBRip.Master.mkv */
    private val QUALITY = Regex("""[.\s](\d{3,4})p[.\s]""", RegexOption.IGNORE_CASE)

    /** Persian-dubbed release, e.g. Toy.Story.5.2026.1080p.WEB-DL.KIMO.DUBLE.mkv */
    private val DUBBED = Regex("""DUBLE|DUBBED|\bDUB\b""", RegexOption.IGNORE_CASE)

    /** Episode marker in a series filename: The.White.Olive.Tree.2025.S01E07.1080p.WEBRip.mkv */
    private val EPISODE = Regex("""[.\s]S(\d{1,2})E(\d{1,3})[.\s]""", RegexOption.IGNORE_CASE)

    /** Playable containers. Titles also expose bare .mka audio tracks, which are not video. */
    private val VIDEO_EXT = Regex("""\.(mkv|mp4|avi|m4v)$""", RegexOption.IGNORE_CASE)

    /** Listing titles read "Bendito Corazon (2026) Persian Subtitle" — trim the boilerplate. */
    private val TITLE_NOISE = Regex("""\s*(Persian\s+Subtitle|Farsi\s+Subtitle|زیرنویس\s+فارسی)\s*$""", RegexOption.IGNORE_CASE)
    private val YEAR = Regex("""\((\d{4})\)""")

    /** /subtitle/560227/5737353/sub.srt?stream=359568 */
    private val SUBTITLE = Regex("""/subtitle/\d+/\d+/sub\.srt\?stream=\d+(?:&second=\d+)?""")

    // ---- Listings --------------------------------------------------------

    fun archiveUrl(kind: Kind, page: Int): String =
        if (page <= 1) "/archive/${kind.path}/" else "/archive/${kind.path}/$page/"

    /**
     * Paginate an arbitrary menu URL. The site puts the page number as the last path
     * segment, before any query string:
     *   /archive/category/stream/?type=film  ->  /archive/category/stream/2/?type=film
     */
    fun paged(url: String, page: Int): String {
        if (page <= 1) return url
        val q = url.indexOf('?')
        val path = if (q >= 0) url.substring(0, q) else url
        val query = if (q >= 0) url.substring(q) else ""
        val withSlash = if (path.endsWith("/")) path else "$path/"
        return "$withSlash$page/$query"
    }

    /** /archive/person/4689/steven-spielberg/ */
    private val PERSON_HREF = Regex("""/archive/person/(\d+)/([^/]+)/""")

    fun parseListing(html: String): List<Title> {
        val doc = Jsoup.parse(html, Site.BASE)
        doc.select(SEL_NAV).remove()
        doc.select(SEL_SIDEBAR).remove()
        val posts = doc.select(SEL_CARD).mapNotNull { card -> parseCard(card) }
        if (posts.isNotEmpty()) return posts
        // Search results and award ceremonies use bare poster anchors with no card wrapper.
        // Try these before people: a ceremony page lists both its nominated films and the
        // nominated actors, and the films are what you actually want to open.
        val loose = parseLooseCards(doc)
        if (loose.isNotEmpty()) return loose
        // Artist playlists have no post links at all — only people.
        return parsePeople(doc)
    }

    /**
     * Person cards, as used by the هنرمندان playlists. They have no `postMeta` wrapper —
     * just an anchor to /archive/person/ around a cast portrait.
     */
    private fun parsePeople(doc: Document): List<Title> {
        return doc.select("a[href*=/archive/person/]")
            .mapNotNull { a ->
                val m = PERSON_HREF.find(a.attr("href")) ?: return@mapNotNull null
                val img = a.selectFirst(SEL_IMG) ?: return@mapNotNull null
                val src = listOf("data-src", "src").map { img.attr(it) }
                    .firstOrNull { it.isNotEmpty() && !it.startsWith("data:") }
                    ?: return@mapNotNull null
                val (id, slug) = m.destructured
                val name = a.text().trim().ifEmpty { prettySlug(slug) }
                Title(id, slug, Kind.PERSON, name, null, src.removePrefix(Site.BASE))
            }
            .distinctBy { it.postId }
    }

    /**
     * Any anchor to a post that wraps a poster image. Used by pages that have no
     * `postMeta` wrapper — search results, award ceremonies.
     */
    private fun parseLooseCards(doc: Document): List<Title> {
        return doc.select("a[href*=/post/]")
            .filter { it.selectFirst(SEL_IMG) != null }
            .mapNotNull { linkToTitle(it) }
            .distinctBy { it.postId }
    }

    /**
     * Login is two-step: username+password comes back with a hidden `recaptchaPass`
     * token and a `mobile` field to complete. The token must be echoed back.
     */
    fun parseLoginToken(html: String): String? =
        Jsoup.parse(html, Site.BASE).selectFirst("input[name=recaptchaPass]")
            ?.attr("value")?.trim()?.ifEmpty { null }

    /** The masked hint the site shows, e.g. "شماره موبایل 3101***0933 بطور کامل وارد شود". */
    fun parseMobileHint(html: String): String? =
        Jsoup.parse(html, Site.BASE).selectFirst("input[name=mobile]")
            ?.attr("title")?.trim()?.ifEmpty { null }

    /** The login page renders failures in a div.alert-danger. */
    fun parseLoginError(html: String): String? =
        Jsoup.parse(html, Site.BASE).selectFirst("div.alert-danger")
            ?.text()?.trim()?.ifEmpty { null }

    // ---- Profile ---------------------------------------------------------

    private val PROFILE_LABELS = listOf("شناسه", "عضویت", "اعتبار")

    /**
     * /user/panel/. The username sits as the paragraph's own text next to a label span:
     *   <p class="mb-2 ltr"><span class="float-right">&nbsp;:نام کاربری</span>USERNAME</p>
     */
    fun parseProfile(html: String): Profile {
        val doc = Jsoup.parse(html, Site.BASE)
        doc.select(SEL_NAV).remove()

        val username = doc.select("p")
            .firstOrNull { it.selectFirst("span")?.text()?.contains("نام کاربری") == true }
            ?.ownText()?.trim()?.ifEmpty { null }

        val facts = mutableListOf<Fact>()
        // Leaf elements only: the panel nests several of these inside one wrapper, and a
        // wrapper match would swallow the following labels into the value.
        for (el in doc.select("div, span, li, p, td")) {
            if (el.children().isNotEmpty()) continue
            val text = el.text().trim()
            if (text.length > 60) continue
            for (label in PROFILE_LABELS) {
                if (facts.any { it.label == label }) continue
                val m = Regex("^" + Regex.escape(label) + """\s*:\s*(.+)$""").find(text) ?: continue
                facts += Fact(label, m.groupValues[1].trim())
            }
        }

        val status = doc.select("div, span, li, p, b, strong")
            .firstOrNull { it.children().isEmpty() && it.text().trim().startsWith("مشترک") }
            ?.text()?.trim()

        val avatar = doc.select("img")
            .asSequence()
            .mapNotNull { i ->
                listOf("data-src", "src").map { i.attr(it) }
                    .firstOrNull { it.contains("/avatar/") && !it.startsWith("data:") }
            }
            .firstOrNull()
            ?.removePrefix(Site.BASE)

        return Profile(username, status, facts, avatar)
    }

    // ---- Awards ----------------------------------------------------------

    /** /awards/show/1/oscars/2025/ */
    private val AWARD_HREF = Regex("""/awards/show/(\d+)/([^/]+)/(\d{4})/""")

    /**
     * The awards index is a flat list of ceremony-and-year links, so group it by ceremony
     * and offer the years newest first.
     */
    fun parseAwards(html: String): List<MenuGroup> {
        val doc = Jsoup.parse(html, Site.BASE)
        doc.select(SEL_NAV).remove()
        val byShow = linkedMapOf<String, MutableList<Pair<Int, String>>>()
        for (a in doc.select("a[href*=/awards/show/]")) {
            val m = AWARD_HREF.find(a.attr("href")) ?: continue
            val (_, name, year) = m.destructured
            val label = a.text().trim().ifEmpty { prettySlug(name) }
            val show = label.replace(Regex("""\s*\d{4}\s*"""), "").trim().ifEmpty { prettySlug(name) }
            byShow.getOrPut(show) { mutableListOf() } += (year.toIntOrNull() ?: 0) to m.value
        }
        return byShow.map { (show, years) ->
            MenuGroup(
                title = show,
                url = null,
                items = years.sortedByDescending { it.first }
                    .distinctBy { it.second }
                    .map { (y, url) -> MenuItem(y.toString(), url) },
            )
        }
    }

    private fun prettySlug(slug: String): String {
        val decoded = runCatching { java.net.URLDecoder.decode(slug, "UTF-8") }.getOrDefault(slug)
        return decoded.split('-').filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }

    private fun parseCard(card: Element): Title? {
        val links = card.select(SEL_POST_LINK)
        // The poster link carries the href; a sibling link usually carries the title text.
        val href = links.firstNotNullOfOrNull { POST_HREF.find(it.attr("href")) } ?: return null
        val (kindStr, postId, slug) = href.destructured
        val kind = if (kindStr == "series") Kind.SERIES else Kind.FILM

        val rawName = links.map { it.text().trim() }.firstOrNull { it.isNotEmpty() }
            ?: card.selectFirst(SEL_IMG)?.attr("alt")?.trim()?.ifEmpty { null }
            ?: slug.replace('-', ' ').replaceFirstChar { it.uppercase() }

        val year = YEAR.find(rawName)?.groupValues?.get(1)
        val name = TITLE_NOISE.replace(rawName, "")
            .let { YEAR.replace(it, "") }
            .trim()
            .ifEmpty { rawName }

        val poster = card.selectFirst(SEL_IMG)?.let { img ->
            // Posters lazy-load, so the real path may sit in data-src rather than src.
            listOf("data-src", "src").map { img.attr(it) }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("data:") }
        }?.removePrefix(Site.BASE)

        return Title(postId, slug, kind, name, year, poster)
    }

    // ---- Top menu --------------------------------------------------------

    private const val SEL_NAV = "#navbar"

    /**
     * Sidebars repeat "سریال های محبوب" on many pages. They must be stripped before any
     * content lookup, or a page whose main list is people (the هنرمندان playlists) gets
     * parsed as that sidebar's series instead.
     */
    private const val SEL_SIDEBAR = "[id*=sidebar]"
    private const val SEL_NAV_ITEM = "li.nav-item"
    private const val SEL_DROPDOWN = "div.dropdown-menu"
    private const val SEL_DROPDOWN_TOGGLE = "a.dropdown-toggle"

    /** Menu entries we deliberately leave out of the TV sidebar. */
    private val MENU_SKIP = setOf(
        "کاربری", "سایر", "لیست تماشا", "پشتیبانی",
        "خانه",                       // the rail has its own home entry
        "قوانین و شرایط استفاده",      // static page, no listing
    )

    /**
     * The site's top navigation is a Bootstrap navbar: `li.nav-item` is a plain link,
     * `li.nav-item.dropdown` holds a toggle plus a `div.dropdown-menu` of children.
     */
    fun parseMenu(html: String): List<MenuGroup> {
        val doc = Jsoup.parse(html, Site.BASE)
        val nav = doc.selectFirst(SEL_NAV) ?: return emptyList()
        val out = mutableListOf<MenuGroup>()

        for (li in nav.select(SEL_NAV_ITEM)) {
            val dropdown = li.selectFirst(SEL_DROPDOWN)
            if (dropdown != null) {
                val title = li.selectFirst(SEL_DROPDOWN_TOGGLE)?.text()?.trim().orEmpty()
                if (title.isEmpty() || title in MENU_SKIP) continue
                val items = dropdown.select("a[href]")
                    .mapNotNull { a ->
                        val href = a.attr("href")
                        val t = a.text().trim()
                        if (t.isEmpty() || href.isEmpty() || href.startsWith("#") || href.startsWith("?")) null
                        else MenuItem(t, href.removePrefix(Site.BASE))
                    }
                    .distinctBy { it.url }
                if (items.isNotEmpty()) out += MenuGroup(title, null, items)
            } else {
                val a = li.selectFirst("a[href]") ?: continue
                val title = a.text().trim()
                val href = a.attr("href")
                if (title.isEmpty() || title in MENU_SKIP) continue
                if (href.isEmpty() || href.startsWith("#") || href.startsWith("?")) continue
                val url = href.removePrefix(Site.BASE)
                out += MenuGroup(title, url, listOf(MenuItem(title, url)))
            }
        }
        return out.distinctBy { it.title }
    }

    // ---- Home slider -----------------------------------------------------

    private const val SEL_SLIDER = "#homeslider"
    private const val SEL_SLIDE = "a.carousel-item"

    /** The wide 16:9 carousel at the top of the homepage. */
    fun parseSlider(html: String): List<Title> {
        val doc = Jsoup.parse(html, Site.BASE)
        val slider = doc.selectFirst(SEL_SLIDER) ?: return emptyList()
        return slider.select(SEL_SLIDE).mapNotNull { linkToTitle(it) }.distinctBy { it.postId }
    }

    // ---- Home page -------------------------------------------------------

    /** A section needs at least this many distinct titles to count as a carousel. */
    private const val MIN_ROW_ITEMS = 4

    /**
     * The homepage is a stack of titled sections (فیلم های برگزیده, مشاهدات اخیر,
     * سریال های بروز شده, سریال های محبوب) plus the main list of newest posts.
     *
     * Rather than hard-code each section, we take every heading and walk up to the
     * nearest ancestor holding several post links. Cards inside the newest-posts list
     * also carry an <h3> title, but those resolve to a single distinct post and are
     * dropped by MIN_ROW_ITEMS.
     */
    fun parseHome(html: String): List<HomeRow> {
        val doc = Jsoup.parse(html, Site.BASE)

        data class Candidate(val label: String, val depth: Int, val isH2: Boolean, val items: List<Title>)
        val candidates = mutableListOf<Candidate>()

        for (h in doc.select("h2, h3")) {
            val label = h.text().trim()
            if (label.isEmpty()) continue

            var node: Element? = h
            var links = Elements()
            var depth = 0
            while (node != null && depth < 7) {
                links = node.select(SEL_POST_LINK)
                if (links.size > 1) break
                node = node.parent()
                depth++
            }

            val items = links.mapNotNull { linkToTitle(it) }.distinctBy { it.postId }
            if (items.size < MIN_ROW_ITEMS) continue
            candidates += Candidate(label, depth, h.tagName() == "h2", items)
        }

        // The sidebar repeats the popular-series list under an unrelated heading
        // ("حساب کاربری"). Same items => same section; keep whichever heading sits
        // closest to the container, which is the one actually labelling it.
        val best = candidates
            .groupBy { c -> c.items.joinToString(",") { it.postId } }
            .mapValues { (_, group) -> group.minByOrNull { it.depth }!! }
        val rows = candidates.filter { best[it.items.joinToString(",") { i -> i.postId }] === it }
            .map { HomeRow(it.label, it.items) }
            .toMutableList()

        // The main column's newest-posts list has no heading of its own.
        val newest = doc.select(SEL_CARD).mapNotNull { parseCard(it) }
        if (newest.isNotEmpty()) {
            val afterCarousels = candidates.count { it.isH2 }.coerceAtMost(rows.size)
            rows.add(afterCarousels, HomeRow("جدیدترین‌ها", newest))
        }
        return rows
    }

    /** Carousel entries are bare poster links, so the name comes from the slug. */
    private fun linkToTitle(a: Element): Title? {
        val m = POST_HREF.find(a.attr("href")) ?: return null
        val (kindStr, postId, slug) = m.destructured
        val kind = if (kindStr == "series") Kind.SERIES else Kind.FILM

        val img = a.selectFirst(SEL_IMG) ?: a.parent()?.selectFirst(SEL_IMG)
        val poster = img?.let {
            listOf("data-src", "src").map { attr -> it.attr(attr) }
                .firstOrNull { v -> v.isNotEmpty() && !v.startsWith("data:") }
        }?.removePrefix(Site.BASE)

        return Title(postId, slug, kind, prettySlug(slug), null, poster)
    }

    // ---- Detail page -----------------------------------------------------

    /** "مدت : ۱۰۲ دقیقه" style facts shown beside the poster. */
    private val FACT_LABELS = listOf(
        "امتیاز منتقدین", "امتیاز", "مدت", "کشور", "زبان", "محصول", "شبکه", "رده سنی",
    )

    fun parseDetail(html: String, postId: String, fallbackName: String): Detail {
        val doc: Document = Jsoup.parse(html, Site.BASE)
        // The navbar carries genre/country links of its own; drop it so it cannot
        // contaminate the genre and cast lists below.
        doc.select(SEL_NAV).remove()

        val downloads = DLN_LINK.findAll(html)
            .map { it.groupValues[1] }
            .distinct()
            .map { url ->
                val file = url.substringAfterLast('/').let { java.net.URLDecoder.decode(it, "UTF-8") }
                val ep = EPISODE.find(file)
                DownloadLink(
                    url = url,
                    fileName = file,
                    heightPx = QUALITY.find(file)?.groupValues?.get(1)?.toIntOrNull(),
                    isDubbed = DUBBED.containsMatchIn(file),
                    season = ep?.groupValues?.get(1)?.toIntOrNull(),
                    episode = ep?.groupValues?.get(2)?.toIntOrNull(),
                )
            }
            .filter { VIDEO_EXT.containsMatchIn(it.fileName) }
            // Episode order first, then original audio, then best quality.
            .sortedWith(
                compareBy(
                    { it.season ?: 0 },
                    { it.episode ?: 0 },
                    { it.isDubbed },
                    { -(it.heightPx ?: 0) },
                )
            )
            .toList()

        val streams = STREAM_OPT.findAll(html).map { m ->
            val (sid, pid, lang, h) = m.destructured
            StreamOption(sid, pid, lang, h.toIntOrNull() ?: 0)
        }.distinctBy { it.streamId to it.height }.toList()

        val subtitles = SUBTITLE.findAll(html)
            .map { it.value }
            .distinct()
            .map { SubtitleRef(Site.BASE + it, "SRT") }
            .toList()

        val name = doc.selectFirst("h1")?.text()?.trim()?.ifEmpty { null }
            ?: doc.title().substringBefore(" - ").trim().ifEmpty { null }
            ?: fallbackName

        val plot = doc.selectFirst("div.col-12:not(:has(*))")?.text()?.trim()?.ifEmpty { null }

        // Take the first *portrait* image: the first /img/ on the page is the trailer's
        // play button, which is square-ish and would show up instead of the poster.
        val poster = doc.select("img")
            .asSequence()
            .mapNotNull { img ->
                listOf("data-src", "src").map { img.attr(it) }
                    .firstOrNull { it.contains("/img/") && !it.startsWith("data:") }
            }
            .firstOrNull { path ->
                val m = Site.IMG_DIMS_RE.find(path) ?: return@firstOrNull false
                val w = m.groupValues[1].toFloatOrNull() ?: return@firstOrNull false
                val h = m.groupValues[2].toFloatOrNull() ?: return@firstOrNull false
                h > 0f && w / h < 0.85f && w >= 150f
            }
            ?.removePrefix(Site.BASE)

        val facts = parseFacts(doc)

        val genres = doc.select("a[href*=/genre/]")
            .mapNotNull { a ->
                val t = a.text().trim()
                val h = a.attr("href").removePrefix(Site.BASE)
                if (t.isEmpty() || h.isEmpty()) null else MenuItem(t, h)
            }
            .distinctBy { it.url }

        val cast = doc.select("a[href*=/archive/person/]")
            .mapNotNull { a ->
                val href = a.attr("href").removePrefix(Site.BASE)
                val img = a.selectFirst(SEL_IMG)
                val src = img?.let { i ->
                    listOf("data-src", "src").map { i.attr(it) }
                        .firstOrNull { it.isNotEmpty() && !it.startsWith("data:") }
                }
                val nm = a.text().trim()
                if (nm.isEmpty() && src == null) null
                else CastMember(nm, href, src?.removePrefix(Site.BASE))
            }
            // The page repeats each person as a named link and as a portrait; merge them.
            .groupBy { it.url }
            .map { (url, group) ->
                CastMember(
                    name = group.firstOrNull { it.name.isNotEmpty() }?.name ?: "",
                    url = url,
                    imagePath = group.firstOrNull { it.imagePath != null }?.imagePath,
                )
            }
            .filter { it.name.isNotEmpty() }

        return Detail(
            postId, name, plot, poster, downloads, streams, subtitles,
            facts = facts, genres = genres, cast = cast,
        )
    }

    /**
     * Pull "label : value" pairs out of the info block.
     *
     * Done per element rather than over the whole document text: Jsoup collapses runs of
     * whitespace, so there is no reliable separator between one fact and the next once the
     * page is flattened to a single string.
     */
    private fun parseFacts(doc: Document): List<Fact> {
        val out = mutableListOf<Fact>()
        for (el in doc.select("div, span, li, p, td")) {
            if (el.children().size > 2) continue
            val text = el.text().trim()
            if (text.length > 80) continue
            for (label in FACT_LABELS) {
                if (out.any { it.label == label }) continue
                val m = Regex("^" + Regex.escape(label) + """\s*:\s*(.+)$""").find(text) ?: continue
                val value = m.groupValues[1].trim()
                if (value.isNotEmpty()) out += Fact(label, value)
            }
        }
        return out
    }
}
