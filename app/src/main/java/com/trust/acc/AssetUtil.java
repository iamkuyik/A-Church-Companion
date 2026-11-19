package com.trust.acc;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Utility for safe, high-performance asset loading and SQLite scripture querying.
 */
public final class AssetUtil {

    private static final String BIBLE_DB_ASSET = "bible_bilingual.db";
    private static final String DB_META_PREFS = "bible_db_meta";
    private static final String KEY_EXTRACTED_DB_VERSION = "extracted_db_version";
    /** Increment whenever bible_bilingual.db content/schema is updated in assets. */
    private static final int CURRENT_BIBLE_DB_VERSION = 3;

    private static volatile SQLiteDatabase cachedBibleDb = null;
    private static volatile ArrayList<HashMap<String, Object>> cachedBibleBooks = null;
    private static final Object BOOKS_LOCK = new Object();
    private static final ReentrantReadWriteLock DB_LOCK = new ReentrantReadWriteLock();

    private AssetUtil() {}

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

        if (!dbFile.exists() || dbFile.length() == 0 || extractedVersion != CURRENT_BIBLE_DB_VERSION) {
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

                // Atomically replace target DB; on POSIX rename overwrites existing file safely
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
                metaPrefs.edit().putInt(KEY_EXTRACTED_DB_VERSION, CURRENT_BIBLE_DB_VERSION).apply();
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
        return cachedBibleDb;
    }

    /**
     * Queries a single chapter's verses (<1ms) from the WITHOUT ROWID primary-key-ordered
     * SQLite Bible database. Protected by a ReadWriteLock so memory-trim closes never race
     * with in-flight queries.
     */
    public static ArrayList<HashMap<String, Object>> getChapterVerses(Context context, int targetBook, int targetChapter) throws Exception {
        DB_LOCK.readLock().lock();
        try {
            SQLiteDatabase db = cachedBibleDb;
            if (db == null || !db.isOpen()) {
                DB_LOCK.readLock().unlock();
                DB_LOCK.writeLock().lock();
                try {
                    db = ensureBibleDatabaseLocked(context);
                    // Downgrade write lock to read lock before querying
                    DB_LOCK.readLock().lock();
                } finally {
                    DB_LOCK.writeLock().unlock();
                }
            }

            ArrayList<HashMap<String, Object>> verses = new ArrayList<>();
            try (Cursor cursor = db.rawQuery(
                    "SELECT verse, efi, kjv, bbe FROM verses WHERE book = ? AND chapter = ? ORDER BY verse ASC",
                    new String[]{String.valueOf(targetBook), String.valueOf(targetChapter)})) {
                while (cursor.moveToNext()) {
                    HashMap<String, Object> vMap = new HashMap<>(6);
                    int verseNum = cursor.getInt(0);
                    String efi = cursor.isNull(1) ? "" : cursor.getString(1);
                    String kjv = cursor.isNull(2) ? "" : cursor.getString(2);
                    String bbe = cursor.isNull(3) ? "" : cursor.getString(3);

                    vMap.put("verse_num", String.valueOf(verseNum));
                    vMap.put("efi", efi);
                    vMap.put("kjv", kjv);
                    vMap.put("bbe", bbe);
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
            }
        } finally {
            DB_LOCK.writeLock().unlock();
        }
    }
}
