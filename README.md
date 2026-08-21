# Ronald

An Android TV client for a subscription movie/TV site, built for a Sony X90L (Google TV).

Personal use only. The site's address lives in `Site.BASE` (`data/Net.kt`) and nowhere
else — point it at your own subscription. Do not distribute the APK.

## Why it works this way

The site has no API. It is server-rendered PHP, so this app scrapes HTML.

Its web player uses Media Source Extensions, feeding sequential `.ts` segments into a
`blob:` URL with no `.m3u8` playlist — awkward to reimplement. But every title page also
exposes **direct download links** (`/dln/...`) that 302 to a CDN serving progressive
MKVs with HTTP range support. Those play natively in ExoPlayer, with seeking, and carry
embedded UTF-8 subtitle tracks. That is the path this app uses.

Verified on the live site (2026-08-21):

| | |
|---|---|
| Video | H.264 (`V_MPEG4/ISO/AVC`) |
| Audio | AAC (`A_AAC`) |
| Subtitles | embedded `S_TEXT/UTF8` |
| DRM | none |
| 1080p | ~2.1 GB (~2.3 Mbps) |
| 2160p | ~5.9 GB (~7.9 Mbps) |

Both hardware-decode on the X90L.

## Auth

Login is a **two-step form**, and getting either step wrong fails silently:

1. `POST /user/login/` with `username` + `password` returns the login page again, now
   carrying a hidden `recaptchaPass` token and a `mobile` field.
2. `POST` again with `username`, `password`, `recaptchaPass` **and** `mobile` signs you in.
   Success is announced by a "در حال انتقال" interstitial, not the panel, so the result is
   confirmed against `/user/panel/`.

Failures come back as `div.alert-danger` ("نام کاربری یا رمز عبور اشتباه می باشد"), which
the app surfaces verbatim.

**The `scr` cookie gates everything.** Any request without it is answered with a small JS
stub that sets `scr` from `devicePixelRatio` and meta-refreshes. It is *not* an HTTP
redirect, so OkHttp will not follow it — `Session` detects the stub and walks it manually.
Before that fix a login POST silently returned the stub instead of being processed.

The resulting `sf` (+`cf`) session cookie is persisted and valid for a year, so signing in
is a one-time step.

## Layout

```
app/src/main/java/com/ronald/tv/
├── MainActivity.kt      theme + entry point
├── data/
│   ├── Models.kt        Title, DownloadLink, Detail
│   ├── Net.kt           OkHttp + persistent cookie jar + login
│   ├── Scraper.kt       ← ALL site selectors and regexes live here
│   └── Repo.kt          suspend wrappers
└── ui/
    ├── AppRoot.kt       screen state machine
    ├── LoginScreen.kt   sign-in / cookie paste
    ├── BrowseScreen.kt  poster grid over /archive/{film,series}/{page}/
    ├── DetailScreen.kt  quality + dub picker
    └── PlayerScreen.kt  ExoPlayer with the session cookie attached
```

**When the site redesigns a page, `Scraper.kt` is the only file that should need
changing.** If listings come up empty or a title shows no play buttons, start there.

## Build

Toolchain lives outside the repo:

```
export JAVA_HOME="$HOME/Library/Java/temurin-21/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Sideload to the X90L

1. On the TV: Settings → System → About → click "Build" 7× to unlock Developer options.
2. Settings → System → Developer options → **USB debugging** on.
3. Note the TV's IP (Settings → Network).

```
adb connect <TV_IP>:5555          # accept the prompt on the TV
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.ronald.tv -c android.intent.category.LEANBACK_LAUNCHER 1
adb logcat -s Ronald ExoPlayerImpl AndroidRuntime
```

## Series

Series pages need no special parsing: every episode is just another `/dln/` link, tagged
`SxxEyy` in its filename. `Scraper.kt` reads the season/episode out of the filename and
`Detail` groups them. Verified against a 38-episode season (114 links = 38 episodes x
1080p/720p/480p), all parsed.

## UI

The layout follows the conventions of Persian TV streaming apps (Filimo's TV app was the
reference). Its geometry was measured from a live `uiautomator` hierarchy dump — the app
blocks screencap, but the view tree gives exact bounds:

| element | Filimo (px @1080p) | here (dp) |
|---|---|---|
| nav rail, screen edge | 144 wide | 72 |
| poster cards | 240 wide, 32 gap | 120, 16 gap |
| row headings | right-aligned | right-aligned |
| hero banner | 1820x778 | full width x 210 |

What that means in practice:

- **RTL throughout** (`LocalLayoutDirection provides LayoutDirection.Rtl`), so the nav
  rail sits on the right, headings right-align, and rows scroll right-to-left.
- **Persistent icon rail** on every screen.
- **Hero banner** at the top of home for the first featured title.
- **Cards scale on focus**; the focused title brightens.

Only the layout and interaction conventions are borrowed. The wordmark, colours and
icons are our own — no third-party branding is copied.

**Artwork is not one shape.** Each row uses different art, and the aspect ratio is
readable straight from the image's own `/img/{w}-{h}/` path:

| row | size | ratio |
|---|---|---|
| featured, newest, archive | 354-532 / 312-468 | 2:3 poster |
| سریال های بروز شده, مشاهدات اخیر | 746-134 (also 373-67) | 5.57:1 banner strip |
| some rows | 1504-846 | 16:9 still |

`Title.aspect` derives this per item, and cards widen by band (120dp poster / 220dp
16:9 / 340dp banner) with height following the true ratio. Forcing everything to 2:3
center-crops the wide art down to an unreadable sliver.

Note the banner images exist *only* at their wide sizes — there is no 16:9 or portrait
version of the same hash — so the shape cannot be normalised, only respected. The hero
tries a 16:9 variant first and falls back to the blurred poster.

## Navigation and slider

The app mirrors the site's own structure rather than inventing one:

- **`#homeslider`** — the site's top carousel is scraped by `Scraper.parseSlider` and shown
  as the app's hero. Its slides are 16:9 (`/img/1504-846/`), the only true landscape art
  the site publishes, so this is a real backdrop rather than a blurred poster. Auto-advances
  every 7s with dot indicators.
- **Top menu → sidebar.** `Scraper.parseMenu` reads the Bootstrap navbar: `li.nav-item` is a
  plain link, `li.nav-item.dropdown` holds a toggle plus `div.dropdown-menu` children. The
  rail is icon-only until focused, then widens to 220dp and shows the Persian labels.
- **Dropdowns → sub-menu strip.** Each group's children become a chip row along the top of
  its page; picking one drives the grid. `Scraper.paged` handles pagination for any menu
  URL, inserting the page number before the query string
  (`/archive/category/stream/?type=film` → `/archive/category/stream/2/?type=film`).

Section pages use the **same row layout as home** (`SectionScreen`): one carousel per
sub-menu entry, each loading lazily as it scrolls into view so opening "فیلم ها" does not
fire fourteen requests at once. Static `/page/` articles carry no listing and are skipped;
user-account and misc entries are dropped via `MENU_SKIP`.

**Artists.** The هنرمندان playlists list *people*, not posts — no `postMeta` wrapper, just
anchors to `/archive/person/{id}/{slug}/` around a `cast/` portrait. `parseListing` falls
back to `parsePeople` when no post cards are found, and a person opens their filmography
(the person page uses the ordinary `postMeta` markup, so it needs no special handling).

**Detail pages** carry the site's own information: facts (مدت، امتیاز، امتیاز منتقدین،
کشور، زبان), genres, and the cast as circular portraits. Comments are deliberately not
shown. The navbar is stripped from the document first — it has genre and country links of
its own that would otherwise contaminate both lists. Facts are matched per element rather
than over the whole page text, because Jsoup collapses whitespace and leaves no reliable
separator between one fact and the next.

## Sections that are not listings

Two menu entries need their own screens:

- **مراسم و جوایز** (`/awards/`) is an index of ~1100 ceremony-and-year links
  (`/awards/show/{id}/{name}/{year}/`). `parseAwards` groups them by ceremony with years
  newest-first, giving one row per award. A ceremony page then lists its nominated films.
- **جستجوی پیشرفته** is a *form*, so opening its URL as a listing showed a page of
  unrelated films. `SearchScreen` drives `/search/?s=` instead.

## Listing fallbacks

`parseListing` tries three shapes in order, and the order matters:

1. `div.postMeta` cards — archives, person filmographies
2. bare `a[href*=/post/]` wrapping a poster — search results, award ceremonies
3. `a[href*=/archive/person/]` portraits — artist playlists

Films are tried before people because a ceremony page lists *both* its nominated films and
its nominated actors, and the films are what you want to open.

## Being a good guest

The site forbids account sharing and appears to invalidate a session that sees bursts of
traffic. `Session` therefore serialises every request and spaces them ~350ms apart —
without that, a section screen fires one request per visible row at once, which looks
nothing like a browser. Expect to sign in again if the session is dropped.

## Flaky network

Requests to this host fail outright and then succeed moments later — measured at roughly
a 60% failure rate at one point, each failure otherwise hanging for the full timeout. So
the client uses an 8s connect timeout and retries up to 4 times with backoff. A single
retry was not enough to make login succeed.

## Home screen

`HomeScreen` mirrors the site's homepage as TV carousels. Rather than hard-coding the
sections, `Scraper.parseHome` takes every `<h2>/<h3>` and walks up to the nearest
ancestor holding several post links, so new sections appear automatically:

- فیلم های برگزیده (featured)
- مشاهدات اخیر پخش آنلاین (recently watched)
- سریال های بروز شده (updated series)
- جدیدترین‌ها (newest posts — the main column, which has no heading of its own)
- سریال های محبوب (popular series)

Two wrinkles it handles: cards in the newest-posts list carry their own `<h3>` title but
resolve to a single distinct post, so `MIN_ROW_ITEMS` drops them; and the sidebar repeats
the popular-series list under the unrelated heading "حساب کاربری", so when two sections
hold identical items we keep the heading nearest the container.

Carousel entries are bare poster links with no title text, so their names are derived
from the URL slug. The detail page shows the real title.

## Verified on hardware (2026-08-21)

Installed and run on the actual Sony X90L (`BRAVIA_4K_VH22`, Android 12 / SDK 31,
primary ABI `armeabi-v7a`):

- Browse grid renders live archive listings with posters, titles and years
- Detail page renders the Persian synopsis right-to-left correctly
- 1080p playback confirmed advancing, decoded in hardware by
  `OMX.MTK.VIDEO.DECODER.AVC` (the TV's MediaTek H.264 block), no rebuffering
- Home carousels, quality picker (originals then `دوبله` variants), and the series
  season/episode picker (Reacher: 4 seasons, 8 episodes in S1) all confirmed on screen

## Poster quirk

Each poster is generated at exactly **one** size, and which size varies per image;
asking for any other size returns a soft-404 (HTTP 200 with the literal body `404`).
The size baked into the page's `data-src` is driven by the `scr` devicePixelRatio
cookie — `scr=2` yields `/img/354-532/`, `scr=1` yields `/img/177-266/`. We send
`scr=2` because most posters exist at that size. `PosterImage` falls back to 177-266
and then to a titled placeholder, because some posters are missing from the site at
every size.

## Known gaps

- **No initial focus** on launch: the first D-pad press selects the top button rather
  than moving within the content.
- Persian section headings render left-aligned; the app does not force RTL layout.
- No search, no watchlist sync, no resume position.
- Pagination is prev/next only — the archive runs to ~9000 pages.
- `.mka` standalone Persian audio tracks are filtered out; playing them muxed against the
  original video is not supported.
