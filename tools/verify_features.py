#!/usr/bin/env python3
"""
Lightweight, zero-dependency regression guard verifying that core user-facing features
documented in FEATURES.md remain wired up across Activities, layouts, and AndroidManifest.xml.

Run before opening a PR (and automatically in GitHub Actions CI):
    python3 tools/verify_features.py
"""

from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA_DIR = ROOT / "app" / "src" / "main" / "java" / "com" / "trust" / "acc"
RES_DIR = ROOT / "app" / "src" / "main" / "res"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
BUILD_GRADLE = ROOT / "app" / "build.gradle"

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def read_text(path: pathlib.Path) -> str:
    if not path.is_file():
        raise FileNotFoundError(f"Missing required file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def check_manifest(errors: list[str]) -> list[str]:
    tree = ET.parse(MANIFEST)
    root = tree.getroot()
    app = root.find("application")
    if app is None:
        errors.append("AndroidManifest.xml: missing <application> element")
        return []

    launchers: list[str] = []
    activities: list[str] = []
    has_acc_view_intent = False

    for act in app.findall("activity"):
        name = act.attrib.get(f"{ANDROID_NS}name", "")
        short = name.rsplit(".", 1)[-1]
        if short:
            activities.append(short)
        for intent_filter in act.findall("intent-filter"):
            categories = [c.attrib.get(f"{ANDROID_NS}name", "") for c in intent_filter.findall("category")]
            actions = [a.attrib.get(f"{ANDROID_NS}name", "") for a in intent_filter.findall("action")]
            if "android.intent.category.LAUNCHER" in categories:
                launchers.append(short)
            if short == "NewHymnsActivity" and "android.intent.action.VIEW" in actions:
                patterns = [d.attrib.get(f"{ANDROID_NS}pathPattern", "") for d in intent_filter.findall("data")]
                if any(".acc" in p for p in patterns):
                    has_acc_view_intent = True

    if launchers != ["MainActivity"]:
        errors.append(
            f"AndroidManifest.xml: expected MainActivity as sole LAUNCHER, found {launchers}"
        )
    if not has_acc_view_intent:
        errors.append(
            "AndroidManifest.xml: NewHymnsActivity must retain ACTION_VIEW intent filter for .acc packs"
        )
    return activities


def check_gradle_version(errors: list[str]) -> None:
    text = read_text(BUILD_GRADLE)
    if not re.search(r'versionCode\s+\d+', text):
        errors.append("app/build.gradle: missing numeric versionCode")
    if not re.search(r'versionName\s+"[0-9]+\.[0-9]+\.[0-9]+"', text):
        errors.append('app/build.gradle: versionName must follow SemVer ("X.Y.Z")')
    if "junit:junit:" not in text:
        errors.append("app/build.gradle: missing JUnit testImplementation dependency")


def check_required_patterns(errors: list[str], activities: list[str]) -> None:
    # 1. Every declared Activity must keep edge-to-edge system bar handling
    for activity in activities:
        java_path = JAVA_DIR / f"{activity}.java"
        if not java_path.is_file():
            errors.append(f"AndroidManifest.xml declares {activity} but {java_path.relative_to(ROOT)} is missing")
            continue
        src = read_text(java_path)
        for token in ("SystemBarUtil.applyEdgeToEdge", "SystemBarUtil.updateSystemBarAppearance"):
            if token not in src:
                errors.append(f"{activity}.java: missing {token} (edge-to-edge system bars)")

    # 2. Core shipped feature invariants (see FEATURES.md)
    invariants: list[tuple[pathlib.Path, list[tuple[str, str]]]] = [
        (
            JAVA_DIR / "MainActivity.java",
            [
                ("btnTheme.setOnClickListener", "instant Light/Dark theme toggle"),
                ("btnTheme.setOnLongClickListener", "long-press theme reset to system default"),
                ("SavedActivity.class", "Saved bookmarks navigation"),
                ("BibleBooksActivity.class", "Holy Bible navigation"),
                ("preloadAllAsync", "background hymnal cache preloading on startup"),
                ("AssetUtil.preloadAsync", "background Bible index preloading on startup"),
            ],
        ),
        (
            JAVA_DIR / "BibleBooksActivity.java",
            [
                ("tabOt.setOnClickListener", "Old Testament tab handler"),
                ("tabNt.setOnClickListener", "New Testament tab handler"),
                ("STATE_TESTAMENT", "saved testament instance state"),
                ("etSearch.addTextChangedListener", "cross-testament live book search"),
                ("btnClearSearch", "clear search button"),
                ("layoutEmpty", "empty search state view"),
                ("GestureDetector", "horizontal OT/NT swipe detector"),
                ("dispatchTouchEvent", "touch dispatch for OT/NT swipe"),
                ("resolveSwipeTestament", "OT/NT swipe direction and search guard"),
            ],
        ),
        (
            JAVA_DIR / "BibleReaderActivity.java",
            [
                ("FLAG_KEEP_SCREEN_ON", "keep screen awake while reading scripture"),
                ("GestureDetector", "horizontal chapter swipe detector"),
                ("dispatchTouchEvent", "touch dispatch for chapter swipe"),
                ("setOnItemLongClickListener", "long-press verse options menu"),
                ("btnBookmark.setOnLongClickListener", "long-press bookmark star -> View All Bookmarks"),
                ("btnVersionSelector.setOnClickListener", "translation & parallel mode picker"),
                ("btnReaderMenu.setOnClickListener", "reader hamburger menu"),
                ("btnAa.setOnClickListener", "typography & theme popover"),
                ("isParallelMode", "flexible parallel view mode"),
            ],
        ),
        (
            JAVA_DIR / "EfikHymnsActivity.java",
            [
                ("btnKeypadMode.setOnClickListener", "123/ABC numeric keypad toggle"),
                ("btnClearSearch", "clear search button"),
                ("layoutEmpty", "empty search state view"),
                ("searchHymnsRanked", "4-tier ranked hymn search"),
                ("normalizeEfik", "Efik diacritic normalization"),
            ],
        ),
        (
            JAVA_DIR / "SocHymnsActivity.java",
            [
                ("btnKeypadMode.setOnClickListener", "123/ABC numeric keypad toggle"),
                ("btnClearSearch", "clear search button"),
                ("layoutEmpty", "empty search state view"),
                ("searchHymnsRanked", "4-tier ranked hymn search"),
            ],
        ),
        (
            JAVA_DIR / "NewHymnsActivity.java",
            [
                ("btnImport.setOnClickListener", "add/import hymn entry point"),
                ("btnClearSearch", "clear search button"),
                ("layoutEmpty", "empty search state view"),
                ("handleImportUri", ".acc/.json pack import handler"),
                ("searchHymnsRanked", "ranked hymn search"),
            ],
        ),
        (
            JAVA_DIR / "LyricsActivity.java",
            [
                ("FLAG_KEEP_SCREEN_ON", "keep screen awake while singing hymns"),
                ("GestureDetector", "horizontal hymn swipe detector"),
                ("dispatchTouchEvent", "touch dispatch for hymn swipe"),
                ("btnShare.setOnClickListener", "share or copy hymn action"),
                ("btnBookmark.setOnLongClickListener", "long-press bookmark star -> SavedActivity"),
                ("btnAa.setOnClickListener", "typography popover"),
            ],
        ),
        (
            JAVA_DIR / "NewLyricsActivity.java",
            [
                ("FLAG_KEEP_SCREEN_ON", "keep screen awake while singing or viewing sheet music"),
                ("GestureDetector", "horizontal hymn swipe detector"),
                ("dispatchTouchEvent", "touch dispatch for hymn swipe"),
                ("btnShare.setOnClickListener", "share or copy hymn action"),
                ("pdfContainer.getVisibility() == View.VISIBLE", "disable swipe while viewing PDF score"),
                ("openPdfScoreView", "PDF musical score view toggle"),
                ("btnBookmark.setOnLongClickListener", "long-press bookmark star -> SavedActivity"),
            ],
        ),
        (
            JAVA_DIR / "PdfRendererHelper.java",
            [
                ("Bitmap.Config.ARGB_8888", "ARGB_8888 bitmap required by PdfRenderer.Page.render()"),
                ("OutOfMemoryError", "OOM fallback for high-res PDF pages"),
            ],
        ),
        (
            JAVA_DIR / "PdfPageAdapter.java",
            [
                ("setImageDrawable(null)", "safe drawable reset without blank 1:1 placeholder"),
                ("setMinimumHeight", "non-zero loading placeholder height"),
            ],
        ),
        (
            JAVA_DIR / "AssetUtil.java",
            [
                ("cleanVerseText", "live scripture footnote/marker sanitization"),
            ],
        ),
        (
            RES_DIR / "layout" / "verse_row.xml",
            [
                ('includeFontPadding="false"', "calibrated first-line Crimson Pro spacing"),
            ],
        ),
        (
            RES_DIR / "layout" / "new_lyrics.xml",
            [
                ("com.trust.acc.ZoomableRecyclerView", "zoomable PDF score recycler view"),
                ('includeFontPadding="false"', "calibrated first-line Crimson Pro spacing"),
            ],
        ),
    ]

    for file_path, checks in invariants:
        try:
            content = read_text(file_path)
        except FileNotFoundError as exc:
            errors.append(str(exc))
            continue
        rel = file_path.relative_to(ROOT)
        for token, description in checks:
            if token not in content:
                errors.append(f"{rel}: missing '{token}' ({description})")


def main() -> int:
    errors: list[str] = []
    for doc in ("AGENTS.md", "FEATURES.md"):
        if not (ROOT / doc).is_file():
            errors.append(f"Missing required project guardrail file: {doc}")

    activities = check_manifest(errors)
    check_gradle_version(errors)
    check_required_patterns(errors, activities)

    if errors:
        print("FEATURE GUARD FAILED — potential regressions detected:", file=sys.stderr)
        for err in errors:
            print(f"  - {err}", file=sys.stderr)
        return 1

    print(f"Feature guard passed ({len(activities)} activities + all core shipped invariants verified).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
