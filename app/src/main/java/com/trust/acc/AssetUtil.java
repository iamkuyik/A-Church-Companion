package com.trust.acc;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

/**
 * Utility for safe, high-performance asset loading, dynamic Bible translation detection,
 * and SQLite scripture querying.
 */
public final class AssetUtil {

    private static final String BIBLE_DB_ASSET = "bible_bilingual.db";
    private static final String DB_META_PREFS = "bible_db_meta";
    private static final String KEY_EXTRACTED_DB_VERSION = "extracted_db_version";
    private static final String KEY_IMPORTED_BIBLE_PACK = "imported_bible_pack";
    /** Increment whenever bundled bible_bilingual.db content/schema is updated in assets. */
    private static final int CURRENT_BIBLE_DB_VERSION = 3;

    private static final Pattern BRACKET_FOOTNOTE_PATTERN = Pattern.compile("\\s*\\[\\d+\\]|\\s*\\[\\]");
    private static final Pattern BBE_MARKER_PATTERN = Pattern.compile("\\\\\\d+:\\d+\\\\\\s*");
    private static final Pattern SPACE_BEFORE_PUNCT_PATTERN = Pattern.compile("\\s+([,.;:?!\\u201d\\u2019)])");
    private static final Pattern MULTI_SPACE_VERSE_PATTERN = Pattern.compile("  +");

    private static volatile SQLiteDatabase cachedBibleDb = null;
    private static volatile Set<String> cachedAvailableColumns = null;
    private static volatile ArrayList<HashMap<String, Object>> cachedBibleBooks = null;
    private static final Object BOOKS_LOCK = new Object();
    private static final ReentrantReadWriteLock DB_LOCK = new ReentrantReadWriteLock();

    private AssetUtil() {}

    /**
     * Warms the bible_index.json cache asynchronously on startup without blocking the UI thread.
     */
    public static void preloadAsync(Context context) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();
        AccApplication.ioExecutor().execute(() -> {
            try {
                getCachedBibleBooks(appContext);
            } catch (Exception ignored) {}
        });
    }

    /**
     * Looks up the total number of chapters for a book from the cached Bible index.
     */
    public static int getBookChapterCount(Context context, int bookId) {
        try {
            ArrayList<HashMap<String, Object>> books = getCachedBibleBooks(context);
            for (int i = 0; i < books.size(); i++) {
                HashMap<String, Object> b = books.get(i);
                int id = b.get("_id_int") instanceof Integer ? (Integer) b.get("_id_int") : -1;
                if (id == bookId) {
                    int ch = b.get("_chap_count") instanceof Integer ? (Integer) b.get("_chap_count") : 50;
                    return ch > 0 ? ch : 50;
                }
            }
        } catch (Exception ignored) {}
        return 50;
    }

    /**
     * Reads an entire asset into a UTF-8 String safely without relying on InputStream.available().
     */
    public static String loadAssetString(Context context, String assetName) throws IOException {
        try (InputStream is = context.getAssets().open(assetName);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(chunk, 0, chunk.length)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }

    /**
     * Returns the parsed and pre-indexed list of Bible books from bible_index.json in a single pass,
     * pre-computing normalized Efik search keys.
     */
    public static ArrayList<HashMap<String, Object>> getCachedBibleBooks(Context context) throws Exception {
        if (cachedBibleBooks == null) {
            synchronized (BOOKS_LOCK) {
                if (cachedBibleBooks == null) {
                    String jsonStr = loadAssetString(context, "bible_index.json");
                    JSONObject jsonObj = new JSONObject(jsonStr);
                    JSONArray booksArray = jsonObj.getJSONArray("books");
                    ArrayList<HashMap<String, Object>> list = new ArrayList<>(booksArray.length());
                    for (int i = 0; i < booksArray.length(); i++) {
                        JSONObject b = booksArray.getJSONObject(i);
                        HashMap<String, Object> map = new HashMap<>(10);
                        int id = b.optInt("id", 0);
                        int chapters = b.optInt("chapters", 0);
                        String nameEn = b.optString("name_en", "");
                        String nameEfi = b.optString("name_efi", "");

                        map.put("id", String.valueOf(id));
                        map.put("_id_int", id);
                        map.put("chapters", String.valueOf(chapters));
                        map.put("_chap_count", chapters);
                        map.put("name_en", nameEn);
                        map.put("name_efi", nameEfi);

                        String normEn = EfikHymnsActivity.normalizeEfik(nameEn);
                        String normEfi = EfikHymnsActivity.normalizeEfik(nameEfi);
                        map.put("_norm_en", normEn);
                        map.put("_norm_en_n", normEn.replace("ng", "n"));
                        map.put("_norm_efi", normEfi);
                        map.put("_norm_efi_n", normEfi.replace("ng", "n"));

                        list.add(map);
                    }
                    cachedBibleBooks = list;
                }
            }
        }
        return new ArrayList<>(cachedBibleBooks);
    }

    /**
     * Ensures the pre-indexed SQLite Bible database is extracted to internal storage and open.
     * Caller must hold DB_LOCK.writeLock().
     */
    private static SQLiteDatabase ensureBibleDatabaseLocked(Context context) throws IOException {
        if (cachedBibleDb != null && cachedBibleDb.isOpen()) {
            return cachedBibleDb;
        }

        Context appCtx = context.getApplicationContext();
        File dbFile = appCtx.getDatabasePath(BIBLE_DB_ASSET);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        SharedPreferences metaPrefs = appCtx.getSharedPreferences(DB_META_PREFS, Context.MODE_PRIVATE);
        int extractedVersion = metaPrefs.getInt(KEY_EXTRACTED_DB_VERSION, 0);
        boolean hasImportedBiblePack = metaPrefs.getBoolean(KEY_IMPORTED_BIBLE_PACK, false);

        // Do not overwrite a user-imported full 5-translation Bible database unless dbFile is missing
        if (!dbFile.exists() || dbFile.length() == 0
                || (!hasImportedBiblePack && extractedVersion != CURRENT_BIBLE_DB_VERSION)) {
            File tempFile = new File(parentDir, BIBLE_DB_ASSET + ".tmp");
            boolean renamed = false;
            try {
                try (InputStream is = appCtx.getAssets().open(BIBLE_DB_ASSET);
                     FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[16384];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.flush();
                }

                renamed = tempFile.renameTo(dbFile);
                if (!renamed) {
                    if (dbFile.exists() && !dbFile.delete()) {
                        throw new IOException("Failed to replace outdated Bible database.");
                    }
                    renamed = tempFile.renameTo(dbFile);
                }
                if (!renamed) {
                    throw new IOException("Failed to move extracted Bible database into place.");
                }
                metaPrefs.edit()
                        .putInt(KEY_EXTRACTED_DB_VERSION, CURRENT_BIBLE_DB_VERSION)
                        .putBoolean(KEY_IMPORTED_BIBLE_PACK, false)
                        .apply();
            } finally {
                if (!renamed && tempFile.exists()) {
                    tempFile.delete();
                }
            }
        }

        cachedBibleDb = SQLiteDatabase.openDatabase(
                dbFile.getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS
        );
        cachedAvailableColumns = inspectVerseColumns(cachedBibleDb);
        return cachedBibleDb;
    }

    private static Set<String> inspectVerseColumns(SQLiteDatabase db) {
        Set<String> cols = new HashSet<>();
        try (Cursor c = db.rawQuery("PRAGMA table_info(verses)", null)) {
            while (c.moveToNext()) {
                String name = c.getString(1);
                if (name != null) {
                    cols.add(name.toLowerCase());
                }
            }
        } catch (Exception ignored) {}
        return cols;
    }

    /**
     * Returns whether a specific Bible translation column (e.g., "niv", "rsv", "kjv", "bbe", "efi")
     * is present in the currently installed SQLite database.
     */
    public static boolean hasTranslationColumn(Context context, String columnId) {
        if (columnId == null) return false;
        Set<String> cols = cachedAvailableColumns;
        if (cols == null) {
            DB_LOCK.writeLock().lock();
            try {
                ensureBibleDatabaseLocked(context);
                cols = cachedAvailableColumns;
            } catch (Exception ignored) {
            } finally {
                DB_LOCK.writeLock().unlock();
            }
        }
        return cols != null && cols.contains(columnId.toLowerCase());
    }

    /**
     * Validates and installs an imported Bible SQLite database from an .acc pack,
     * unlocking all translations present in the pack (such as NIV and RSV).
     */
    public static boolean installImportedBibleDatabase(Context context, File stagedDbFile) {
        if (stagedDbFile == null || !stagedDbFile.exists() || stagedDbFile.length() < 1024 * 1024) {
            return false;
        }
        // Validate SQLite schema before installing
        try (SQLiteDatabase testDb = SQLiteDatabase.openDatabase(
                stagedDbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            Set<String> cols = inspectVerseColumns(testDb);
            if (!cols.contains("efi") || !cols.contains("kjv")) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        DB_LOCK.writeLock().lock();
        try {
            if (cachedBibleDb != null) {
                try {
                    cachedBibleDb.close();
                } catch (Exception ignored) {}
                cachedBibleDb = null;
                cachedAvailableColumns = null;
            }

            Context appCtx = context.getApplicationContext();
            File dbFile = appCtx.getDatabasePath(BIBLE_DB_ASSET);
            File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            File tempFile = new File(parentDir, BIBLE_DB_ASSET + ".import.tmp");
            try (InputStream in = new FileInputStream(stagedDbFile);
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buf = new byte[16384];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
                out.flush();
            }

            if (dbFile.exists()) {
                dbFile.delete();
            }
            if (!tempFile.renameTo(dbFile)) {
                if (tempFile.exists()) tempFile.delete();
                return false;
            }

            SharedPreferences metaPrefs = appCtx.getSharedPreferences(DB_META_PREFS, Context.MODE_PRIVATE);
            metaPrefs.edit()
                    .putBoolean(KEY_IMPORTED_BIBLE_PACK, true)
                    .putInt(KEY_EXTRACTED_DB_VERSION, CURRENT_BIBLE_DB_VERSION)
                    .apply();

            ensureBibleDatabaseLocked(appCtx);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            DB_LOCK.writeLock().unlock();
        }
    }

    /**
     * Reverts any imported Bible database back to the factory bundled asset database.
     */
    public static void resetImportedBibleDatabase(Context context) {
        DB_LOCK.writeLock().lock();
        try {
            if (cachedBibleDb != null) {
                try {
                    cachedBibleDb.close();
                } catch (Exception ignored) {}
                cachedBibleDb = null;
                cachedAvailableColumns = null;
            }
            Context appCtx = context.getApplicationContext();
            SharedPreferences metaPrefs = appCtx.getSharedPreferences(DB_META_PREFS, Context.MODE_PRIVATE);
            metaPrefs.edit()
                    .putBoolean(KEY_IMPORTED_BIBLE_PACK, false)
                    .putInt(KEY_EXTRACTED_DB_VERSION, 0)
                    .apply();
            File dbFile = appCtx.getDatabasePath(BIBLE_DB_ASSET);
            if (dbFile.exists()) {
                dbFile.delete();
            }
        } finally {
            DB_LOCK.writeLock().unlock();
        }
    }

    /**
     * Strips stray bracketed footnote markers ([1], []), embedded BBE \ch:v\ tags, and RSV
     * grave-accent quotes cleanly at read time across both bundled and imported SQLite databases.
     */
    public static String cleanVerseText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        if (raw.indexOf('[') < 0 && raw.indexOf('\\') < 0 && raw.indexOf('`') < 0) return raw;
        String cleaned = raw.replace('`', '\'');
        if (cleaned.indexOf('\\') >= 0) {
            cleaned = BBE_MARKER_PATTERN.matcher(cleaned).replaceAll("");
        }
        if (cleaned.indexOf('[') >= 0) {
            cleaned = BRACKET_FOOTNOTE_PATTERN.matcher(cleaned).replaceAll("");
        }
        cleaned = SPACE_BEFORE_PUNCT_PATTERN.matcher(cleaned).replaceAll("$1");
        return MULTI_SPACE_VERSE_PATTERN.matcher(cleaned).replaceAll(" ").trim();
    }

    /**
     * Queries a single chapter's verses (<1ms) from the WITHOUT ROWID primary-key-ordered
     * SQLite Bible database, dynamically reading whichever translation columns exist.
     */
    public static ArrayList<HashMap<String, Object>> getChapterVerses(Context context, int targetBook, int targetChapter) throws Exception {
        DB_LOCK.readLock().lock();
        try {
            SQLiteDatabase db = cachedBibleDb;
            if (db == null || !db.isOpen() || cachedAvailableColumns == null) {
                DB_LOCK.readLock().unlock();
                DB_LOCK.writeLock().lock();
                try {
                    db = ensureBibleDatabaseLocked(context);
                    DB_LOCK.readLock().lock();
                } finally {
                    DB_LOCK.writeLock().unlock();
                }
            }

            Set<String> cols = cachedAvailableColumns;
            boolean hasRsv = cols != null && cols.contains("rsv");
            boolean hasNiv = cols != null && cols.contains("niv");

            String sql = (hasRsv && hasNiv)
                    ? "SELECT verse, efi, kjv, bbe, rsv, niv FROM verses WHERE book = ? AND chapter = ? ORDER BY verse ASC"
                    : "SELECT verse, efi, kjv, bbe FROM verses WHERE book = ? AND chapter = ? ORDER BY verse ASC";

            ArrayList<HashMap<String, Object>> verses = new ArrayList<>();
            try (Cursor cursor = db.rawQuery(
                    sql,
                    new String[]{String.valueOf(targetBook), String.valueOf(targetChapter)})) {
                while (cursor.moveToNext()) {
                    HashMap<String, Object> vMap = new HashMap<>(8);
                    int verseNum = cursor.getInt(0);
                    String efi = cleanVerseText(cursor.isNull(1) ? "" : cursor.getString(1));
                    String kjv = cleanVerseText(cursor.isNull(2) ? "" : cursor.getString(2));
                    String bbe = cleanVerseText(cursor.isNull(3) ? "" : cursor.getString(3));

                    vMap.put("verse_num", String.valueOf(verseNum));
                    vMap.put("efi", efi);
                    vMap.put("kjv", kjv);
                    vMap.put("bbe", bbe);
                    if (hasRsv && hasNiv) {
                        String rsv = cleanVerseText(cursor.isNull(4) ? "" : cursor.getString(4));
                        String niv = cleanVerseText(cursor.isNull(5) ? "" : cursor.getString(5));
                        vMap.put("rsv", rsv);
                        vMap.put("niv", niv);
                    }
                    vMap.put("en", kjv);
                    verses.add(vMap);
                }
            }
            return verses;
        } finally {
            DB_LOCK.readLock().unlock();
        }
    }

    /**
     * Closes the cached SQLite connection safely when critical low memory is signaled by Android.
     */
    public static void clearBibleCache() {
        DB_LOCK.writeLock().lock();
        try {
            if (cachedBibleDb != null) {
                try {
                    cachedBibleDb.close();
                } catch (Exception ignored) {}
                cachedBibleDb = null;
                cachedAvailableColumns = null;
            }
        } finally {
            DB_LOCK.writeLock().unlock();
        }
    }
}
