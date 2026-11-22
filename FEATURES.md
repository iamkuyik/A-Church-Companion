# Shipped Feature Contract

This file is the source of truth for user-facing behaviors in A Church Companion (ACC).
**Rule:** Do not remove or regress any feature listed under **Shipped on `main`** unless explicitly requested. Update this file whenever a feature is added, restored, or intentionally changed.

---

## Shipped on `main` (v1.4.1)

### App Shell & System Chrome
- **`AccApplication`**: Applies saved light/dark/system theme (`theme_prefs`) on every cold start (including deep-link file opens) and provides shared `ioExecutor()`.
- **`MainActivity`** *(sole `LAUNCHER` in `AndroidManifest.xml`)*:
  - Navigation cards for Songs of the Church (`SocHymnsActivity`), Efik Hymnal (`EfikHymnsActivity`), New Hymns (`NewHymnsActivity`), Holy Bible (`BibleBooksActivity`), and Saved Bookmarks (`SavedActivity`).
  - Instant 1-tap Light/Dark theme toggle (`btnTheme`); long-press resets to System Default.
- **Edge-to-Edge System Bars (`SystemBarUtil`)**: Every Activity calls `SystemBarUtil.applyEdgeToEdge(...)` in `onCreate` and `SystemBarUtil.updateSystemBarAppearance(...)` in `onResume`.

### Holy Bible
- **`BibleBooksActivity`**:
  - Sticky Old Testament (`tabOt`, `id < 470`) and New Testament (`tabNt`, `id >= 470`) pill tabs with saved instance state (`STATE_TESTAMENT`).
  - Live search (`etSearch`) across **both** Old and New Testaments (English + Efik with diacritic and `ng`/`n` normalization).
  - Fast scroll enabled on book list; tapping a book opens `BibleChaptersActivity`.
- **`BibleChaptersActivity`**:
  - Full-width chapter grid for the selected book; tapping a chapter opens `BibleReaderActivity`.
- **`BibleReaderActivity`**:
  - Offline SQLite Bible reader (`AssetUtil` + `BibleVersion`) supporting dynamic detection of installed translations (`efi`, `kjv`, `rsv`, `bbe`, `niv`).
  - Single-translation view and flexible **Parallel View** (any two translations paired independently) via translation switcher dialog (`btnVersionSelector`).
  - Horizontal swipe (`GestureDetector` + `dispatchTouchEvent` with `ACTION_CANCEL`) for Previous / Next Chapter.
  - Chapter bookmark toggle on tap (`btnBookmark`); **long-press** on bookmark star opens `SavedActivity` ("View All Bookmarks").
  - Reader `☰` menu (`btnReaderMenu`): Previous/Next Chapter, Bookmarks, Translations & View Mode, Typography & Theme.
  - Typography & Theme popover (`btnAa` via `TypographyHelper`).
  - Verse actions on **long-press** (`setOnItemLongClickListener`): Compare Translations dialog, Bookmark/Remove Verse, Copy Verse, Share Verse.
  - Preserves chapter across configuration changes (`STATE_CHAPTER`) and scrolls to `target_verse` when opened from a verse bookmark.

### Hymnals & Sheet Music
- **`EfikHymnsActivity` & `SocHymnsActivity`**:
  - Async hymn loading via `HymnRepository` + debounced 4-tier ranked search (`HymnRepository.searchHymnsRanked`: exact number → number prefix → title → lyrics) with Efik diacritic & `ng`/`n` normalization (`EfikHymnsActivity.normalizeEfik`).
  - `123` / `ABC` numeric keypad mode toggle (`btnKeypadMode`) and fast scroll.
- **`NewHymnsActivity`**:
  - Async hymn loading, alphabetical sorting, and ranked search.
  - Create custom hymn modal (`btnImport` / `+`) and export/import of `.acc`, `.hymn`, and `.json` packs (including split text/Bible + PDF score packs with deduplication and preview dialog).
  - Deep-link `ACTION_VIEW` intent filters in `AndroidManifest.xml` for `.acc`, `.hymn`, and `.json` files (rejects `.apk` safely).
- **`LyricsActivity`**:
  - Horizontal swipe (`GestureDetector` + `dispatchTouchEvent`) for Previous / Next Hymn.
  - Typography popover (`btnAa`), hymn bookmark toggle (`btnBookmark`) with long-press to open `SavedActivity`.
- **`NewLyricsActivity` + `PdfRendererHelper` + `PdfPageAdapter` + `ZoomableRecyclerView`**:
  - Lyrics view with horizontal swipe for Previous / Next Hymn (disabled while viewing PDF score so pan/zoom works cleanly).
  - Toggle between Lyrics and zoomable PDF Musical Score (`ZoomableRecyclerView` with pinch-to-zoom and double-tap zoom).
  - `PdfRendererHelper` renders strictly with `Bitmap.Config.ARGB_8888` on warm parchment background (capped at 1800px wide with OOM fallback).
  - `PdfPageAdapter` clears recycled images with `setImageDrawable(null)` and keeps a non-zero loading placeholder height.

### Saved Bookmarks
- **`SavedActivity` & `SavedManager`**:
  - Unified list of saved Bible chapters (`bible`), Bible verses (`bible_verse`), and hymns (`soc`, `efik`, `new`) using stable title/reference IDs.
  - Tap opens the exact hymn, chapter, or target verse; long-press prompts to remove bookmark.

---

## Pending Restoration from Unmerged `v1.3.2` (`ACC` PR #1: `feat/sacred-editorial-optimizations`)

These features were shipped in the `v1.3.2` APK release (`24b1705`) on an unmerged branch and were not on `main` when `v1.4.0` branched from `v1.3.1` (`5eab027`). Restore them one small, focused PR at a time, moving each item into **Shipped on `main`** (with tests + `tools/verify_features.py` checks) once merged:

- [ ] **OT / NT horizontal swipe** in `BibleBooksActivity` (swipe left/right between Old Testament and New Testament when search is empty).
- [ ] **Bible Books clear-search (`×`) button & empty search state** in `BibleBooksActivity` / `bible_books.xml`.
- [ ] **In-app `# 123` Hymn Quick-Dial modal** (`QuickDialHelper` + `dialog_quick_dial.xml`) on Hymn List and Hymn Reader screens.
- [ ] **Keep-Screen-On (`FLAG_KEEP_SCREEN_ON`)** in `BibleReaderActivity`, `LyricsActivity`, and `NewLyricsActivity`.
- [ ] **Bottom `‹ PREV` / `NEXT ›` navigation bars & 5-column Chapter Jump modal** in `BibleReaderActivity` (and bottom prev/next hymn indicator in `LyricsActivity` / `NewLyricsActivity`).
- [ ] **Parallel View dropdown selectors (`SELECT ▾`) & one-tap `⇅ SWAP 1ST & 2ND` button** in the Bible translation picker dialog.
- [ ] **Unified high-contrast verse bookmark + highlight styling** (`bg_verse_highlighted.xml` + inline `★ BOOKMARKED` badge).
- [ ] **Crimson Pro `head.yMax` font metrics fix (`includeFontPadding="false"`) & Bible/Hymn corpus cleanup** (verify against v1.4.1 SQLite DB & JSON assets before porting).
