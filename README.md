# A Church Companion (ACC) ☩

[![Release](https://img.shields.io/badge/Release-v1.4.0-8B2635.svg?style=flat-square)](https://github.com/iamkuyik/A-Church-Companion/releases)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-2D5D4B.svg?style=flat-square)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-D4AF37.svg?style=flat-square)](LICENSE)

> *"Speaking to one another in psalms and hymns and spiritual songs, singing and making melody in your heart to the Lord."*  
> — **Ephesians 5:19**

**A Church Companion (ACC)** is an open-source, offline-first worship companion created for the Church of Christ. Designed for clarity, focus, and speed during worship services, Bible classes, and private devotion, ACC pairs an offline public-domain bilingual Holy Bible with classic public-domain hymnals, extensible `.acc` hymn & sheet-music packs, and a distraction-free *Sacred Editorial* design system.

---

## ✦ Core Features

### 📖 Public-Domain Bilingual Holy Bible (Edisana Ŋwed Abasi & English)
* **Zero-Latency Offline SQLite Engine:** Compact (`12.4 MB`), primary-key-ordered (`WITHOUT ROWID`) offline SQLite database covering all 66 books (31,102 verses) across three public-domain texts:
  * **Ŋwed Abasi (EFI)** — Classic traditional Efik Bible
  * **King James Version (KJV)**
  * **Bible in Basic English (BBE)**
* **Flexible Single & Parallel Reading:** Pair any two translations side-by-side or read any single translation full-width.
* **Verse Actions:** Long-press any verse to compare all translations, bookmark, copy, or share a formatted citation.
* **Smart Navigation:** Cross-testament book search, quick chapter grid, density-calibrated horizontal chapter swiping, and `☰` reader navigation menu.

### 🎶 Public-Domain Hymnals & Extensible `.acc` Packs
1. **Classic Hymns (Songs of the Church Starter — 115 Hymns):**
   * Pre-1925 public-domain Christian hymns with styled `[CHORUS]` and `[REFRAIN]` headers and ranked number/title/lyrics search.
2. **Ŋwed Ikwọ (Traditional Efik Hymnal — 512 Hymns):**
   * Traditional Efik hymnal texts with accented diacritics (`ŋ`, `ɔ`, `ẹ`, `ọ`, `ụ`) and diacritic-tolerant search.
3. **Spiritual Songs & Custom Packs (12 Starter Hymns + Unlimited Imports):**
   * Manual hymn creation, custom hymnal editing, and single-file `.acc` / `.json` import & export so congregations can load their own licensed hymnals and sheet music privately offline.

### 🎼 On-Demand Zoomable Sheet Music Viewer
* **Single Lightweight APK:** No bundled PDF bloat—musical scores are loaded on demand when a user imports an `.acc` score pack.
* **Hardware-Accelerated Score Reader:** Lazy-loaded background rendering via Android `PdfRenderer` (`RGB_565`) with smooth pinch-to-zoom, double-tap zoom, and 2D panning in `ZoomableRecyclerView`.
* **Zero Network Permissions:** ACC never requests `INTERNET` permission; `.acc` packs are imported locally via Android's document picker or shared directly from Files / messaging apps.

### 🕯️ Sacred Editorial Liturgical UI
* **Sanctuary-Tuned Themes:** Hymnal Parchment (Light), Midnight Sanctuary (Dark), and System Auto (applied consistently even on cold-start file opens).
* **Typography Controls:** *Crimson Pro* & *JetBrains Mono* (SIL OFL-1.1) with instant font-family, size, and theme switching via the `Aa` popover.

---

## 🛠️ Tech Stack & Architecture

* **Language:** Java (JDK 17)
* **Build System:** Gradle 8.13 with Android Gradle Plugin (AGP) 8.12.0
* **Minimum / Target SDK:** Android 8.0 (API 26) → Android 14 (API 34)
* **Optimization:** R8 code minification + resource shrinking, WebP Adaptive Icons, `WITHOUT ROWID` SQLite Bible engine, streaming Zip-Safe `.acc` importer, shared background I/O executor

---

## 🚀 Building & Packaging

### Prerequisites
* Android Studio (or command-line Android SDK)
* JDK 17
* Android SDK Platform 34 & Build Tools 34.0.0

### Build Commands

```bash
# Build Release APK (R8 minified & resource-shrunk)
./gradlew assembleRelease

# Build Debug APK
./gradlew assembleDebug

# Optional: Package a congregation's local PDFs + hymns into a single .acc pack
python3 tools/build_scores_pack.py --hymns-json /path/to/hymns.json --pdf-dir /path/to/pdfs --output releases/my_pack.acc

# Clean build artifacts
./gradlew clean
```

---

## 📜 Licensing & Notices
* Application source code is licensed under the **[MIT License](LICENSE)**.
* Font licenses (SIL OFL-1.1), third-party library attributions, and public-domain content details are documented in **[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)**.
* Security practices and vulnerability reporting are documented in **[SECURITY.md](SECURITY.md)**.

---

*“To God be the glory, great things He hath done.”*
