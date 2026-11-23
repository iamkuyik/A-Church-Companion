# Changelog

All notable changes to **A Church Companion (ACC)** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.5.0] - 2026-11-23

### Added
- **Old / New Testament Horizontal Swipe:** Swipe left/right between Old Testament and New Testament in `BibleBooksActivity` (density-scaled, disabled during search, preserves per-testament scroll positions).
- **Bible Reader Bottom Navigation & Chapter Jump:** Added a 52dp bottom chapter navigation bar (`‹ PREV CH.` / `CHAPTER X OF Y ▾` / `NEXT CH. ›`), 5-column Chapter Jump modal (also opened by tapping the header book/chapter title), and exact book chapter count bounds—allowing removal of the redundant top-right `☰` menu button.
- **Unified High-Contrast Verse Highlight:** Bookmarked verses now render with a warm amber/gold highlight card, 5dp left accent spine, rounded verse number pill, and `★ BOOKMARKED` badge.
- **Compact Parallel View Selector:** Upgraded Parallel View in the translation picker dialog to compact 1st & 2nd translation dropdown selectors (`SELECT ▾`) with a one-tap `⇅ SWAP 1ST & 2ND` button.
- **Clear Search, Empty States & Share Hymn:** Added 1-tap `✕` Clear Search buttons and friendly empty-search states across all 4 list screens, plus a header Share / Copy Hymn button in `LyricsActivity` and `NewLyricsActivity`.
- **Worship Ergonomics & Startup Speed:** Added `FLAG_KEEP_SCREEN_ON` across all 3 readers and asynchronous background preloading of hymnals and Bible index in `MainActivity`.
- **Regression Safeguards & CI Release Gates:** Added `AGENTS.md`, `FEATURES.md`, PR checklist, `tools/verify_features.py`, JVM JUnit 4 unit tests, and automated CI checks verifying release tags are merged into `main` and match `versionName`.

### Fixed
- **Typography & Text Cleanup:** Calibrated `crimson_pro_regular.ttf` `head.yMax` metrics with `includeFontPadding="false"` and added live read-time scripture footnote/marker sanitization (`AssetUtil.cleanVerseText`).

## [1.4.1] - 2026-11-19

### Fixed
- **PDF Score Rendering & Split Packs:** Restored `Bitmap.Config.ARGB_8888` required by `PdfRenderer.Page.render()`, added a 220dp loading placeholder in `PdfPageAdapter`, and enabled independent split `.acc` text/Bible and sheet-music packs.

## [1.4.0] - 2026-11-19

### Added
- **Open-Source Public Release Readiness:** Added `LICENSE` (MIT), `THIRD_PARTY_NOTICES.md` (SIL OFL-1.1 fonts & Apache 2.0 dependencies), `SECURITY.md`, and `.github/workflows/android-ci.yml` for automated JDK 17 Gradle builds.
- **On-Demand `.acc` Hymn & Score Packs:** Switched to a single lightweight APK with streaming `.acc` pack import/export and repeatable packager `tools/build_scores_pack.py`.
- **`AccApplication`:** Ensures saved Dark/Light/System theme applies on cold-start deep links and shares a bounded background I/O executor across activities.
- **Ranked Hymn Search & Cross-Testament Bible Search:** Typing a hymn number ranks exact matches first; searching Bible books queries both Old and New Testaments simultaneously.
- **Reader Navigation & Backup Rules:** Added Previous/Next Chapter actions in the Bible reader `☰` menu, Android 8.0+ Adaptive Icon (WebP), and `backup_rules.xml` / `data_extraction_rules.xml` excluding the read-only Bible DB and imported PDFs from cloud backup quota.

### Fixed
- **Bible Database Cleanup & Optimization:** Restrict bundled `bible_bilingual.db` strictly to public-domain translations (Efik, KJV, BBE), re-aligned 1,259 BBE verses and 62 Psalm superscriptions, stripped all raw `\ch:v\` markers, and rebuilt as a 16 KB-page `WITHOUT ROWID` table (`21.68 MB → 12.38 MB`, zero temp B-tree sorts).
- **Hymnal & Reader Bugs:** Styled all `[REFRAIN]` headers alongside `[CHORUS]` with dark-mode-aware echo text; fixed `AssetUtil` SQLite `onTrimMemory` close race via `ReentrantReadWriteLock`; added request sequencing, `onSaveInstanceState()`, dp-scaled swipe thresholds, and long-press-only verse options in `BibleReaderActivity`; refreshed bookmark stars on resume in hymn readers.
- **PDF Viewer & Zoom:** Lazy background PDF loading, `RGB_565` rendering (50% lower RAM) with OOM fallback, async `PdfRenderer` close (no UI-thread ANR), `SecurityException` FD leak fix, and `ZoomableRecyclerView` pinch-zoom/scroll conflict fix.
- **Importer Security & Reliability:** Streaming ZIP extraction to disk (eliminates OOM on large packs), Zip-Bomb & Windows/POSIX Zip-Slip guards, unconditional APK rejection, atomic UTF-8 `custom_hymns.json` writes, preserved `PDF_File` on manual hymn edit, and narrowed `FileProvider` to `cache/exports/`.
- **Theme, Contrast & Accessibility:** Fixed dark-mode `colorOnPrimary`, `PopupOverlay`, and `Confirm Import` button contrast; fixed `0dp` weighted `ScrollView` inside `wrap_content` dialogs; upgraded header icon buttons to 48dp touch targets and added missing `contentDescription` / `importantForAccessibility` attributes.

### Changed / Removed
- Curated bundled `soc_hymns.json` (115 pre-1925 public-domain classic hymns) and `new_hymns.json` (12 pre-1925 public-domain hymns) so the public repository contains strictly public-domain devotional texts while congregations can import custom packs via `.acc`.
- Bumped `minSdkVersion` to **26** (Android 8.0+), configured Java 17 `compileOptions`, and enabled **R8 minification + resource shrinking** with proper Gson/ViewBinding ProGuard rules.
- Removed the obsolete desktop web test prototype (`index.html`, `app.js`, `styles.css`, `server.py`, `manifest.json`, `package.json`, `.bat` launchers), duplicate `data/` JSON copies, legacy `scripts/*.py` audit scripts, 6 unused drawables, duplicate `newsreader.xml` font alias, and the `lite`/`full` product flavor split.

---

## [1.3.1] - 2026-11-04
- Restored flexible parallel view (any two translations paired), View All Bookmarks manager, Add New Hymn modal, long-press verse options, and edge-to-edge system bar handling.

## [1.3.0] - 2026-11-02
- Introduced Liturgical Sanctuary UI (Hymnal Parchment & Midnight Sanctuary themes) and offline bilingual SQLite Bible reader.

## [1.2.0] - 2026-10-14
- Standardized typography controls with floating Aa popover, Efik diacritic corrections, and Saved/Bookmarks manager.

## [1.1.0] - 2026-10-13
- Material 3 theme styling, list recycling optimizations, fast scroll, and full-lyrics hymn search.

## [1.0.0] - 2026-10-13
- Initial foundation release of A Church Companion (ACC).
