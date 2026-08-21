package com.ronald.tv.data

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object Site {
    /** The only place the site's address appears. */
    const val BASE = "https://ronald.ingmar-bergman.com"

    /** Desktop UA. The site varies its markup for mobile clients. */
    const val UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"

    val IMG_SIZE_RE = Regex("""/img/\d+-\d+/""")
    val IMG_DIMS_RE = Regex("""/img/(\d+)-(\d+)/""")
}

/**
 * Cookies are the whole auth story here: the site issues an `sf` session cookie
 * (plus `cf`) that stays valid for a year, so we persist them and never log in again.
 */
class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ronald_cookies", Context.MODE_PRIVATE)

    private val memory = mutableMapOf<String, Cookie>()

    /**
     * Only the site's own cookies belong in here. /dln/ links redirect to a CDN on a
     * different host; without this scoping a CDN cookie could clobber the `sf` session,
     * and site cookies would be leaked to the CDN.
     */
    private val siteHost: String = Site.BASE.toHttpUrlOrNull()?.host.orEmpty()

    init {
        prefs.getStringSet(KEY, emptySet())?.forEach { raw ->
            val url = Site.BASE.toHttpUrlOrNull() ?: return@forEach
            Cookie.parse(url, raw)?.let { memory[it.name] = it }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (url.host != siteHost) return
        cookies.forEach { memory[it.name] = it }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (url.host == siteHost) memory.values.toList() else emptyList()

    /** `name=value; ...` — for handing the session to ExoPlayer's data source. */
    fun headerValue(): String = memory.values.joinToString("; ") { "${it.name}=${it.value}" }

    fun has(name: String): Boolean = memory.containsKey(name)

    /** Seed a cookie we set ourselves (the site normally sets `scr` from JS). */
    fun seed(name: String, value: String) {
        val url = Site.BASE.toHttpUrlOrNull() ?: return
        Cookie.parse(url, "$name=$value; Path=/; Max-Age=31536000")?.let { memory[it.name] = it }
        persist()
    }

    /**
     * Import a `document.cookie` string copied out of a logged-in desktop browser.
     * This is the escape hatch when form login is blocked.
     */
    fun importFrom(documentCookie: String) {
        documentCookie.split(';').forEach { pair ->
            val t = pair.trim()
            if (t.contains('=')) {
                val n = t.substringBefore('=').trim()
                val v = t.substringAfter('=').trim()
                seed(n, v)
            }
        }
    }

    fun clear() { memory.clear(); prefs.edit().remove(KEY).apply() }

    private fun persist() {
        prefs.edit().putStringSet(KEY, memory.values.map { it.toString() }.toSet()).apply()
    }

    private companion object { const val KEY = "cookies" }
}

private const val NETWORK_ERROR = "خطای شبکه — دوباره تلاش کنید."

/** Connections to this host drop often; a single retry is not enough. */
private const val ATTEMPTS = 4

/** Minimum spacing between requests, to stay well clear of the site's abuse limits. */
private const val MIN_REQUEST_GAP_MS = 350L

class Session(context: Context) {
    val cookies = PersistentCookieJar(context.applicationContext)

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookies)
        .followRedirects(true)
        // Measured ~60% connection failures to this host, each otherwise hanging for the
        // full timeout. Fail fast and retry instead of freezing the UI.
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", Site.UA)
                    .header("Referer", Site.BASE + "/")
                    .header("Accept-Language", "fa,en;q=0.8")
                    .build()
            )
        }
        .build()

    /**
     * This connection drops requests intermittently — the same URL will fail outright and
     * then succeed a second later — so one retry is worth it.
     */
    /**
     * One request at a time, spaced out.
     *
     * The site forbids account sharing and appears to invalidate a session that sees
     * bursts of traffic. Section screens would otherwise fire a request per visible row
     * at once, which looks nothing like a browser.
     */
    private val gate = Any()
    private var lastRequestAt = 0L

    private fun execute(request: Request): String = synchronized(gate) {
        val since = System.currentTimeMillis() - lastRequestAt
        if (since in 0 until MIN_REQUEST_GAP_MS) Thread.sleep(MIN_REQUEST_GAP_MS - since)
        try {
            executeNow(request)
        } finally {
            lastRequestAt = System.currentTimeMillis()
        }
    }

    private fun executeNow(request: Request): String {
        var last: java.io.IOException? = null
        repeat(ATTEMPTS) { attempt ->
            try {
                client.newCall(request).execute().use { r ->
                    return r.body?.string().orEmpty()
                }
            } catch (e: java.io.IOException) {
                last = e
                if (attempt < ATTEMPTS - 1) Thread.sleep(400L * (attempt + 1))
            }
        }
        throw last ?: java.io.IOException("request failed: ${request.url}")
    }

    /**
     * The site answers a request that lacks the `scr` cookie with a small JS stub that
     * sets it and meta-refreshes. It is not an HTTP redirect, so OkHttp will not follow
     * it — we have to. Without this a POST silently returns the stub instead of being
     * processed, which is exactly how login used to fail.
     */
    private fun handshakeTarget(html: String): String? {
        if (html.length > 2000 || !html.contains("setCookie")) return null
        return Regex("""URL='([^']+)'""").find(html)?.groupValues?.get(1)
    }

    fun get(url: String): String {
        val full = if (url.startsWith("http")) url else Site.BASE + url
        val html = execute(Request.Builder().url(full).build())
        val target = handshakeTarget(html) ?: return html
        return execute(Request.Builder().url(Site.BASE + target).build())
    }

    /**
     * The site's landing page sets `scr` from JS (devicePixelRatio) before it will
     * hand out a session, so we set it ourselves and then fetch the homepage.
     */
    fun bootstrap() {
        if (!cookies.has("scr")) cookies.seed("scr", "2")
        runCatching { get("/") }
    }

    private fun loggedIn(html: String) = html.contains("/user/logout/")

    private fun postLogin(fields: Map<String, String>): String? {
        val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        val req = Request.Builder()
            .url("${Site.BASE}/user/login/")
            .header("Referer", "${Site.BASE}/user/login/")
            .post(body)
            .build()
        var html = try { execute(req) } catch (e: java.io.IOException) { return null }
        // A POST made before the session is established comes back as the JS stub.
        handshakeTarget(html)?.let { target ->
            runCatching { execute(Request.Builder().url(Site.BASE + target).build()) }
            html = try { execute(req) } catch (e: java.io.IOException) { return null }
        }
        return html
    }

    /**
     * Sign in. Returns null on success, otherwise a message to show.
     *
     * The site uses a two-step form: username+password is answered with a hidden
     * `recaptchaPass` token plus a `mobile` field, and only the second POST signs you in.
     */
    fun login(username: String, password: String, mobile: String): String? {
        bootstrap()

        var html = postLogin(mapOf("username" to username, "password" to password))
            ?: return NETWORK_ERROR
        if (loggedIn(html)) return null

        val token = Scraper.parseLoginToken(html)
        if (token != null) {
            if (mobile.isBlank()) {
                return Scraper.parseMobileHint(html)
                    ?: "برای تکمیل ورود، شماره موبایل حساب را وارد کنید."
            }
            html = postLogin(
                mapOf(
                    "username" to username,
                    "password" to password,
                    "recaptchaPass" to token,
                    "mobile" to mobile,
                )
            ) ?: return NETWORK_ERROR
            if (loggedIn(html)) return null
        }

        // The site answers a successful second step with a "در حال انتقال" interstitial
        // rather than the panel itself, so confirm against the panel.
        if (html.contains("در حال انتقال")) {
            return if (isAuthenticated()) null else "ورود ناموفق بود."
        }
        Scraper.parseLoginError(html)?.let { return it }
        return if (isAuthenticated()) null else "ورود ناموفق بود."
    }

    /** The panel page only renders a logout link for a real session. */
    fun isAuthenticated(): Boolean =
        runCatching { get("/user/panel/").contains("/user/logout/") }.getOrDefault(false)
}
