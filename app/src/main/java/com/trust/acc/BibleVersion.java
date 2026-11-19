package com.trust.acc;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registry and model representing bundled Public Domain offline Bible translations in the APK.
 */
public class BibleVersion {
    public final String id;          // e.g. "kjv", "bbe", "efi"
    public final String name;        // e.g. "King James Version", "Ŋwed Abasi"
    public final String abbrev;      // e.g. "KJV", "BBE", "EFI"
    public final String language;    // e.g. "English", "Efik"
    public final String bookNameKey; // e.g. "name_en", "name_efi"

    private static final List<BibleVersion> SUPPORTED_VERSIONS;
    private static final Map<String, BibleVersion> BY_ID;
    private static final BibleVersion DEFAULT_VERSION;

    static {
        BibleVersion kjv = new BibleVersion("kjv", "King James Version", "KJV", "English", "name_en");
        BibleVersion bbe = new BibleVersion("bbe", "Bible in Basic English", "BBE", "English", "name_en");
        BibleVersion efi = new BibleVersion("efi", "Ŋwed Abasi", "EFI", "Efik", "name_efi");

        SUPPORTED_VERSIONS = Collections.unmodifiableList(Arrays.asList(kjv, bbe, efi));
        DEFAULT_VERSION = kjv;

        Map<String, BibleVersion> map = new HashMap<>(8);
        for (BibleVersion v : SUPPORTED_VERSIONS) {
            map.put(v.id, v);
        }
        // Backwards compatibility for legacy saved preferences
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

    public static List<BibleVersion> getSupportedVersions() {
        return SUPPORTED_VERSIONS;
    }

    public static BibleVersion getById(String id) {
        if (id == null) return DEFAULT_VERSION;
        BibleVersion found = BY_ID.get(id.toLowerCase(Locale.ROOT));
        return found != null ? found : DEFAULT_VERSION;
    }
}
