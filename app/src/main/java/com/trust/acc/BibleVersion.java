package com.trust.acc;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registry and model representing offline Bible translations available in the installed database.
 */
public class BibleVersion {
    public final String id;          // e.g. "kjv", "rsv", "bbe", "niv", "efi"
    public final String name;        // e.g. "King James Version", "Ŋwed Abasi"
    public final String abbrev;      // e.g. "KJV", "RSV", "BBE", "NIV", "EFI"
    public final String language;    // e.g. "English", "Efik"
    public final String bookNameKey; // e.g. "name_en", "name_efi"

    private static final List<BibleVersion> ALL_KNOWN_VERSIONS;
    private static final Map<String, BibleVersion> BY_ID;
    private static final BibleVersion DEFAULT_VERSION;

    static {
        BibleVersion kjv = new BibleVersion("kjv", "King James Version", "KJV", "English", "name_en");
        BibleVersion rsv = new BibleVersion("rsv", "Revised Standard Version", "RSV", "English", "name_en");
        BibleVersion bbe = new BibleVersion("bbe", "Bible in Basic English", "BBE", "English", "name_en");
        BibleVersion niv = new BibleVersion("niv", "New International Version", "NIV", "English", "name_en");
        BibleVersion efi = new BibleVersion("efi", "Ŋwed Abasi", "EFI", "Efik", "name_efi");

        ALL_KNOWN_VERSIONS = Collections.unmodifiableList(Arrays.asList(kjv, rsv, bbe, niv, efi));
        DEFAULT_VERSION = kjv;

        Map<String, BibleVersion> map = new HashMap<>(8);
        for (BibleVersion v : ALL_KNOWN_VERSIONS) {
            map.put(v.id, v);
        }
        map.put("en", kjv);
        BY_ID = Collections.unmodifiableMap(map);
    }

    public BibleVersion(String id, String name, String abbrev, String language, String bookNameKey) {
        this.id = id;
        this.name = name;
        this.abbrev = abbrev;
        this.language = language;
        this.bookNameKey = bookNameKey;
    }

    /**
     * Returns only the Bible translations whose columns exist in the currently installed SQLite DB
     * (3 Public Domain translations out-of-the-box in ACC Open, or all 5 after importing the full .acc pack).
     */
    public static List<BibleVersion> getSupportedVersions(Context context) {
        if (context == null) return ALL_KNOWN_VERSIONS;
        List<BibleVersion> active = new ArrayList<>(ALL_KNOWN_VERSIONS.size());
        for (BibleVersion v : ALL_KNOWN_VERSIONS) {
            if (AssetUtil.hasTranslationColumn(context, v.id)) {
                active.add(v);
            }
        }
        return active.isEmpty() ? ALL_KNOWN_VERSIONS : active;
    }

    public static List<BibleVersion> getSupportedVersions() {
        return ALL_KNOWN_VERSIONS;
    }

    public static BibleVersion getById(Context context, String id) {
        BibleVersion candidate = getById(id);
        if (context != null && !AssetUtil.hasTranslationColumn(context, candidate.id)) {
            return DEFAULT_VERSION;
        }
        return candidate;
    }

    public static BibleVersion getById(String id) {
        if (id == null) return DEFAULT_VERSION;
        BibleVersion found = BY_ID.get(id.toLowerCase(Locale.ROOT));
        return found != null ? found : DEFAULT_VERSION;
    }
}
