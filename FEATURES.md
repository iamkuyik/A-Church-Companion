# Shipped Feature Contract

This file is the source of truth for user-facing behaviors in A Church Companion (ACC).
**Rule:** Do not remove or regress any feature listed under **Shipped on `main`** unless explicitly requested. Update this file whenever a feature is added, restored, or intentionally changed.

---

## Shipped on `main` (v1.5.0)

### App Shell & System Chrome
- **`AccApplication`**: Applies saved light/dark/system theme (`theme_prefs`) on every cold start (including deep-link file opens) and provides shared `ioExecutor()`.
- **`MainActivity`** *(sole `LAUNCHER` in `AndroidManifest.xml`)*:
  - Navigation cards for Songs of the Church (`SocHymnsActivity`), Efik Hymnal (`EfikHymnsActivity`), New Hymns (`NewHymnsActivity`), Holy Bible (`BibleBooksActivity`), and Saved Bookmarks (`SavedActivity`).
  - Instant 1-tap Light/Dark theme toggle (`btnTheme`); long-press resets to System Default.
  - Triggers asynchronous background preloading of all 3 hymnals (`HymnRepository.preloadAllAsync()`) and Bible index (`AssetUtil.preloadAsync()`) on launch.
- **Edge-to-Edge System Bars (`SystemBarUtil`)**: Every Activity calls `SystemBarUtil.applyEdgeToEdge(...)` in `onCreate` and `SystemBarUtil.updateSystemBarAppearance(...)` in `onResume`.
- **Keep-Screen-On (`FLAG_KEEP_SCREEN_ON`)**: Enabled in `BibleReaderActivity`, `LyricsActivity`, and `NewLyricsActivity` so the screen stays awake during worship and reading.

### Holy Bible
- **`BibleBooksActivity`**:
  - Sticky Old Testament (`tabOt`, `id < 470`) and New Testament (`tabNt`, `id >= 470`) pill tabs with saved instance state (`STATE_TESTAMENT`) and preserved per-testament scroll positions.
  - Horizontal swipe (`GestureDetector` + `dispatchTouchEvent` with `ACTION_CANCEL`) to switch between Old Testament (swipe left → NT) and New Testament (swipe right → OT) when search is empty (ignores touches starting inside `etSearch`).
  - Live search (`etSearch`) across **both** Old and New Testaments (English + Efik with diacritic and `ng`/`n` normalization), with 1-tap Clear Search (`✕`) and empty-search state (`layoutEmpty` + `btnClearEmpty`).
  - Fast scroll enabled on book list; tapping a book opens `BibleChaptersActivity`.
- **`BibleChaptersActivity`**:
  - Full-width chapter grid for the selected book; tapping a chapter opens `BibleReaderActivity`.
- **`BibleReaderActivity`**:
  - Offline SQLite Bible reader (`AssetUtil` + `BibleVersion`) supporting dynamic detection of installed translations (`efi`, `kjv`, `rsv`, `bbe`, `niv`) with live read-time text sanitization (`AssetUtil.cleanVerseText` stripping `[N]`/`[]` footnotes, BBE `\ch:v\` tags, and RSV backticks).
  - Single-translation view and compact **Parallel View** picker (1st & 2nd translation dropdown popup selectors + one-tap `⇅ SWAP 1ST & 2ND` button) via `btnVersionSelector`.
  - Bottom chapter navigation bar (`‹ PREV CH.` / `CHAPTER X OF Y ▾` / `NEXT CH. ›`), 5-column **Chapter Jump** modal (also accessible by tapping the book/chapter header title), and horizontal swipe (`GestureDetector` + `dispatchTouchEvent` with `ACTION_CANCEL`) bounded by `AssetUtil.getBookChapterCount`. *(Top-right `☰` menu removed intentionally since every action has a dedicated control.)*
  - Chapter bookmark toggle on tap (`btnBookmark`); **long-press** on bookmark star opens `SavedActivity` ("View All Bookmarks").
  - Typography & Theme popover (`btnAa` via `TypographyHelper`), calibrated `crimson_pro_regular.ttf` metrics, and `includeFontPadding="false"` across scripture and hymn text views.
  - Verse actions on **long-press** (`setOnItemLongClickListener`): Compare Translations dialog, unified **Bookmark & Highlight Verse** (high-contrast `bg_verse_highlighted` card + 5dp accent spine + `★ BOOKMARKED` badge), Copy Verse, Share Verse.
  - Preserves chapter across configuration changes (`STATE_CHAPTER`) and scrolls to `target_verse` when opened from a verse bookmark.

### Hymnals & Sheet Music
- **`EfikHymnsActivity` & `SocHymnsActivity`**:
  - Async hymn loading via `HymnRepository` + debounced 4-tier ranked search (`HymnRepository.searchHymnsRanked`: exact number → number prefix → title → lyrics) with Efik diacritic & `ng`/`n` normalization (`EfikHymnsActivity.normalizeEfik`), Clear Search (`✕`), and empty-search state.
  - `123` / `ABC` numeric keypad mode toggle (`btnKeypadMode`) and fast scroll.
- **`NewHymnsActivity`**:
  - Async hymn loading, alphabetical sorting, ranked search with Clear Search (`✕`), and empty-search state.
  - Create custom hymn modal (`btnImport` / `+`) and export/import of `.acc`, `.hymn`, and `.json` packs (including split text/Bible + PDF score packs with deduplication and preview dialog).
  - Deep-link `ACTION_VIEW` intent filters in `AndroidManifest.xml` for `.acc`, `.hymn`, and `.json` files (rejects `.apk` safely).
- **`LyricsActivity`**:
  - Horizontal swipe (`GestureDetector` + `dispatchTouchEvent`) for Previous / Next Hymn.
  - Share / Copy Hymn button (`btnShare`), typography popover (`btnAa`), hymn bookmark toggle (`btnBookmark`) with long-press to open `SavedActivity`.
- **`NewLyricsActivity` + `PdfRendererHelper` + `PdfPageAdapter` + `ZoomableRecyclerView`**:
  - Lyrics view with horizontal swipe for Previous / Next Hymn (disabled while viewing PDF score so pan/zoom works cleanly) and Share / Copy Hymn button (`btnShare`).
  - Toggle between Lyrics and zoomable PDF Musical Score (`ZoomableRecyclerView` with pinch-to-zoom and double-tap zoom).
  - `PdfRendererHelper` renders strictly with `Bitmap.Config.ARGB_8888` on warm parchment background (capped at 1800px wide with OOM fallback).
  - `PdfPageAdapter` clears recycled images with `setImageDrawable(null)` and keeps a non-zero loading placeholder height.

### Saved Bookmarks
- **`SavedActivity` & `SavedManager`**:
  - Unified list of saved Bible chapters (`bible`), Bible verses (`bible_verse`), and hymns (`soc`, `efik`, `new`) using stable title/reference IDs.
  - Tap opens the exact hymn, chapter, or target verse; long-press prompts to remove bookmark.

---

## Pending Restoration from Unmerged `v1.3.2` (`ACC` PR #1: `feat/sacred-editorial-optimizations`)

All selected features from `v1.3.2` (`24b1705`) have been restored via focused PRs (with non-selected items documented below so future contributors know the deliberate UX decisions):

- [x] **OT / NT horizontal swipe** in `BibleBooksActivity` (swipe left/right between Old Testament and New Testament when search is empty).
- [x] **Clear-search (`✕`) button & empty search state** across `BibleBooks`, `EfikHymns`, `SocHymns`, and `NewHymns` (+ **Share / Copy Hymn** button in `LyricsActivity` & `NewLyricsActivity`).
- [ ] **In-app `# 123` Hymn Quick-Dial modal** *(skipped intentionally — existing `123`/`ABC` keyboard toggle covers numeric lookup)*.
- [x] **Keep-Screen-On (`FLAG_KEEP_SCREEN_ON`)** in `BibleReaderActivity`, `LyricsActivity`, and `NewLyricsActivity` (+ **Startup background preloading** in `MainActivity`).
- [x] **Bible Reader bottom `‹ PREV CH.` / `CHAPTER X OF Y ▾` / `NEXT CH. ›` bar & 5-column Chapter Jump modal** (+ top-right `☰` menu removed; hymn reader bottom bar skipped intentionally to preserve reading space).
- [x] **Parallel View compact dropdown selectors (`SELECT ▾`) & one-tap `⇅ SWAP 1ST & 2ND` button** in the Bible translation picker dialog.
- [x] **Unified high-contrast verse bookmark + highlight styling** (`bg_verse_highlighted.xml` + `bg_verse_pill.xml` + inline `★ BOOKMARKED` badge).
- [x] **Crimson Pro `head.yMax` font metrics fix (`includeFontPadding="false"`) & Bible/Hymn text cleanup** (`AssetUtil.cleanVerseText` + 12 SoC OCR lyric fixes).
