package com.trust.acc;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class SavedManager {
    private static final String PREF_NAME = "saved_prefs";
    private static final String KEY_ITEMS = "saved_items_json";
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<ArrayList<Item>>() {}.getType();

    private static ArrayList<Item> sCachedItems = null;
    private static final HashSet<String> sCachedIds = new HashSet<>();

    public static class Item {
        public String id;
        public String type; // "soc", "efik", "new", "bible", "bible_verse"
        public String title;
        public String subtitle;
        public String extra1;
        public String extra2;
        public String extra3;
        public String extra4;
        public String extra5;

        public Item() {}

        public Item(String id, String type, String title, String subtitle, String extra1, String extra2, String extra3, String extra4) {
            this(id, type, title, subtitle, extra1, extra2, extra3, extra4, "");
        }

        public Item(String id, String type, String title, String subtitle, String extra1, String extra2, String extra3, String extra4, String extra5) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.subtitle = subtitle;
            this.extra1 = extra1;
            this.extra2 = extra2;
            this.extra3 = extra3;
            this.extra4 = extra4;
            this.extra5 = extra5;
        }
    }

    public static String getVerseBookmarkId(String bookId, int chapterNum, String verseNum) {
        return "bible_verse_" + bookId + "_" + chapterNum + "_" + verseNum;
    }

    /**
     * Generates a stable title-based ID for hymns so adding or reordering hymns never shifts bookmarks.
     */
    public static String getHymnBookmarkId(String sourceType, String title) {
        String cleanTitle = title != null ? title.trim().toLowerCase(Locale.ROOT) : "";
        return sourceType + "_" + cleanTitle;
    }

    private static synchronized void ensureLoaded(Context context) {
        if (sCachedItems != null) return;
        SharedPreferences sp = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_ITEMS, "[]");
        ArrayList<Item> cleaned = new ArrayList<>();
        try {
            ArrayList<Item> list = GSON.fromJson(json, LIST_TYPE);
            if (list != null) {
                for (Item item : list) {
                    if (item != null && item.id != null && !item.id.isEmpty()) {
                        cleaned.add(item);
                    }
                }
            }
        } catch (Exception ignored) {
            // Corrupt JSON falls back cleanly to empty list
        }
        sCachedItems = cleaned;
        rebuildIdIndex();
    }

    private static void rebuildIdIndex() {
        sCachedIds.clear();
        if (sCachedItems != null) {
            for (Item item : sCachedItems) {
                if (item != null && item.id != null) {
                    sCachedIds.add(item.id);
                }
            }
        }
    }

    public static synchronized boolean isSaved(Context context, String id) {
        if (id == null) return false;
        ensureLoaded(context);
        return sCachedIds.contains(id);
    }

    public static synchronized boolean isHymnSaved(Context context, String sourceType, String title, int legacyIndex) {
        ensureLoaded(context);
        String stableId = getHymnBookmarkId(sourceType, title);
        if (sCachedIds.contains(stableId)) return true;
        if (legacyIndex >= 0 && sCachedIds.contains(sourceType + "_" + legacyIndex)) return true;
        return false;
    }

    public static synchronized boolean toggleSave(Context context, Item target) {
        return toggleSave(context, target, null);
    }

    public static synchronized boolean toggleSave(Context context, Item target, String legacyId) {
        if (target == null || target.id == null) return false;
        ensureLoaded(context);
        int foundIndex = -1;
        for (int i = 0; i < sCachedItems.size(); i++) {
            Item existing = sCachedItems.get(i);
            if (existing == null || existing.id == null) continue;
            if (target.id.equals(existing.id) || (legacyId != null && legacyId.equals(existing.id))) {
                foundIndex = i;
                break;
            }
        }
        boolean nowSaved;
        if (foundIndex != -1) {
            sCachedItems.remove(foundIndex);
            nowSaved = false;
        } else {
            sCachedItems.add(0, target);
            nowSaved = true;
        }
        rebuildIdIndex();
        saveList(context, sCachedItems);
        return nowSaved;
    }

    public static synchronized void remove(Context context, String id) {
        if (id == null) return;
        ensureLoaded(context);
        for (int i = 0; i < sCachedItems.size(); i++) {
            Item existing = sCachedItems.get(i);
            if (existing != null && id.equals(existing.id)) {
                sCachedItems.remove(i);
                break;
            }
        }
        rebuildIdIndex();
        saveList(context, sCachedItems);
    }

    public static synchronized ArrayList<Item> getSavedList(Context context) {
        ensureLoaded(context);
        return new ArrayList<>(sCachedItems);
    }

    private static void saveList(Context context, List<Item> list) {
        SharedPreferences sp = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_ITEMS, GSON.toJson(list)).apply();
    }
}
