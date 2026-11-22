package com.trust.acc;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM regression tests covering core search, Efik normalization, Bible translation
 * registry, and stable bookmark ID contracts. Runs via `./gradlew testDebugUnitTest`.
 */
public class CoreFeatureRegressionTest {

    private static HashMap<String, Object> hymn(String title, String lyrics, String num) {
        HashMap<String, Object> item = new HashMap<>();
        item.put("Title", title);
        item.put("Lyrics", lyrics);
        String normTitle = EfikHymnsActivity.normalizeEfik(title);
        String normAll = EfikHymnsActivity.normalizeEfik(title + "\n" + lyrics);
        item.put("_norm_title", normTitle);
        item.put("_norm_title_n", normTitle.replace("ng", "n"));
        item.put("_norm_search", normAll);
        item.put("_norm_search_n", normAll.replace("ng", "n"));
        item.put("_parsed_num", num);
        return item;
    }

    @Test
    public void normalizeEfik_stripsDiacriticsAndSpecialCharacters() {
        assertEquals("ngwed ikwo", EfikHymnsActivity.normalizeEfik("Ŋwed Ikwọ"));
        assertEquals("obong abasi", EfikHymnsActivity.normalizeEfik("Ọbọŋ Abasi"));
        assertEquals("obon abasi", EfikHymnsActivity.normalizeEfik("Ọbọñ Abasi"));
        assertEquals("edikan 'jesus'", EfikHymnsActivity.normalizeEfik("Ẹdikan ‘Jesus’"));
        assertEquals("", EfikHymnsActivity.normalizeEfik(null));
    }

    @Test
    public void matchesEfikOrEnglish_handlesNgAndNVariants() {
        String query = EfikHymnsActivity.normalizeEfik("nw");
        String queryWithN = query.replace("ng", "n");
        assertTrue(EfikHymnsActivity.matchesEfikOrEnglish("Ŋwed Abasi", query, queryWithN));
        assertFalse(EfikHymnsActivity.matchesEfikOrEnglish("Genesis", query, queryWithN));
    }

    @Test
    public void searchHymnsRanked_ordersExactNumberThenPrefixThenTitleThenLyrics() {
        List<HashMap<String, Object>> master = new ArrayList<>();
        master.add(hymn("99. Praise Him", "Sing number 12 today", "99"));
        master.add(hymn("50. Chapter 12 Song", "Other lyrics", "50"));
        master.add(hymn("120. Standing on the Promises", "Standing firm", "120"));
        master.add(hymn("12. Amazing Grace", "How sweet the sound", "12"));

        ArrayList<HashMap<String, Object>> ranked = HymnRepository.searchHymnsRanked(master, "12");
        assertEquals(4, ranked.size());
        assertEquals("12", ranked.get(0).get("_parsed_num"));  // exact number
        assertEquals("120", ranked.get(1).get("_parsed_num")); // number prefix
        assertEquals("50", ranked.get(2).get("_parsed_num"));  // title match
        assertEquals("99", ranked.get(3).get("_parsed_num"));  // lyrics match
    }

    @Test
    public void searchHymnsRanked_emptyQueryReturnsAllItems() {
        List<HashMap<String, Object>> master = new ArrayList<>();
        master.add(hymn("1. First Hymn", "Lyrics one", "1"));
        master.add(hymn("2. Second Hymn", "Lyrics two", "2"));

        ArrayList<HashMap<String, Object>> all = HymnRepository.searchHymnsRanked(master, "   ");
        assertEquals(2, all.size());
    }

    @Test
    public void bibleVersionRegistry_containsAllFiveVersionsAndResolvesAliases() {
        List<BibleVersion> versions = BibleVersion.getSupportedVersions();
        assertEquals(5, versions.size());
        assertEquals("efi", BibleVersion.getById("EFI").id);
        assertEquals("kjv", BibleVersion.getById("en").id);
        assertEquals("kjv", BibleVersion.getById("unknown").id);
        assertEquals("kjv", BibleVersion.getById(null).id);
    }

    @Test
    public void savedManager_generatesStableBookmarkIds() {
        assertEquals("bible_verse_10_1_1", SavedManager.getVerseBookmarkId("10", 1, "1"));
        assertEquals("soc_12. amazing grace", SavedManager.getHymnBookmarkId("soc", "  12. Amazing Grace "));
    }

    @Test
    public void bibleBooksSwipe_switchesBetweenOldAndNewTestament() {
        // Swipe left on OT -> switches to NT
        assertEquals("nt", BibleBooksActivity.resolveSwipeTestament("ot", false, -200f, 10f, -500f, 100f, 90f, 200f));
        // Swipe right on NT -> switches to OT
        assertEquals("ot", BibleBooksActivity.resolveSwipeTestament("nt", false, 200f, -10f, 500f, 100f, 90f, 200f));
        // Swiping past boundaries is a no-op
        assertNull(BibleBooksActivity.resolveSwipeTestament("ot", false, 200f, 0f, 500f, 100f, 90f, 200f));
        assertNull(BibleBooksActivity.resolveSwipeTestament("nt", false, -200f, 0f, -500f, 100f, 90f, 200f));
    }

    @Test
    public void bibleBooksSwipe_blockedDuringSearchOrVerticalScroll() {
        // Blocked while search query is active
        assertNull(BibleBooksActivity.resolveSwipeTestament("ot", true, -200f, 0f, -500f, 100f, 90f, 200f));
        // Blocked when vertical movement exceeds vertical lock
        assertNull(BibleBooksActivity.resolveSwipeTestament("ot", false, -200f, 100f, -500f, 100f, 90f, 200f));
        // Blocked when velocity is too slow
        assertNull(BibleBooksActivity.resolveSwipeTestament("ot", false, -200f, 0f, -100f, 100f, 90f, 200f));
    }
}
