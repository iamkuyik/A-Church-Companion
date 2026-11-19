package com.trust.acc;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Central repository managing built-in hymnals and dynamic, streaming hymn & musical score packs.
 */
public class HymnRepository {

    private static final String TAG = "HymnRepository";
    public static final String CATEGORY_NEW = "new";
    public static final String CATEGORY_SOC = "soc";
    public static final String CATEGORY_EFIK = "efik";

    private static final Set<String> VALID_CATEGORIES = new HashSet<>(
            Arrays.asList(CATEGORY_NEW, CATEGORY_SOC, CATEGORY_EFIK)
    );

    private static final String CUSTOM_HYMNS_FILE = "custom_hymns.json";
    private static final String SCORES_DIR = "scores_imported";

    // Safety caps against Zip Bombs and unbounded memory allocation
    private static final int MAX_ZIP_ENTRIES = 500;
    private static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 160L * 1024L * 1024L; // 160 MB
    private static final long MAX_JSON_MANIFEST_BYTES = 16L * 1024L * 1024L;     // 16 MB

    private static volatile HymnRepository sInstance;
    private final Context mContext;
    private final Gson mGson = new Gson();

    // Cache merged lists and known asset PDF names in memory
    private final Map<String, ArrayList<HashMap<String, Object>>> mMergedCache = new HashMap<>();
    private Set<String> mAssetPdfCache = null;

    public static HymnRepository getInstance(Context context) {
        if (sInstance == null) {
            synchronized (HymnRepository.class) {
                if (sInstance == null) {
                    sInstance = new HymnRepository(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private HymnRepository(Context context) {
        this.mContext = context;
        ensureDirectories();
    }

    private void ensureDirectories() {
        File scoresDir = new File(mContext.getFilesDir(), SCORES_DIR);
        if (!scoresDir.exists()) {
            scoresDir.mkdirs();
        }
    }

    public static class HymnPreviewItem {
        public String title;
        public String category;
        public boolean isUpdate;
        public boolean hasScore;
        public String lyricsSnippet;

        public HymnPreviewItem(String title, String category, boolean isUpdate, boolean hasScore, String lyricsSnippet) {
            this.title = title;
            this.category = category;
            this.isUpdate = isUpdate;
            this.hasScore = hasScore;
            this.lyricsSnippet = lyricsSnippet;
        }
    }

    public static class HymnPackagePreview {
        public boolean isValid;
        public String errorMessage;
        public String fileName = "";
        public long fileSize = 0;
        public int totalHymns = 0;
        public int newHymnsCount = 0;
        public int updatedHymnsCount = 0;
        public int scoresCount = 0;
        public String targetCategory = CATEGORY_NEW;
        public List<HymnPreviewItem> items = new ArrayList<>();

        // Staged payload to commit upon user confirmation (PDFs staged on disk, not RAM!)
        Map<String, ArrayList<HashMap<String, Object>>> toSave = new HashMap<>();
        File stagingDir = null;
        Map<String, File> stagedScoreFiles = new HashMap<>();

        public static HymnPackagePreview failure(String error) {
            HymnPackagePreview p = new HymnPackagePreview();
            p.isValid = false;
            p.errorMessage = error;
            return p;
        }
    }

    public static class ImportResult {
        public boolean success;
        public int importedCount;
        public int updatedCount;
        public int scoresCount;
        public String message;
        public List<String> hymnTitles = new ArrayList<>();

        public ImportResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    /**
     * Normalizes a zip/file entry name safely against both POSIX ('/') and Windows ('\') path traversal.
     */
    private static String sanitizeFileName(String rawName) {
        if (rawName == null) return "";
        String normalized = rawName.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String base = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        base = base.trim();
        if (base.isEmpty() || ".".equals(base) || "..".equals(base) || base.contains("..")) {
            return "";
        }
        return base;
    }

    private static String sanitizeCategory(String rawCategory, String fallback) {
        if (rawCategory != null) {
            String clean = rawCategory.toLowerCase(Locale.ROOT).trim();
            if (VALID_CATEGORIES.contains(clean)) {
                return clean;
            }
        }
        return VALID_CATEGORIES.contains(fallback) ? fallback : CATEGORY_NEW;
    }

    /**
     * Resolves the actual PDF file path or asset identifier for a given hymn.
     * If the PDF was imported into internal storage, returns its absolute path on disk.
     * Otherwise returns the original asset name.
     */
    public String resolvePdfPath(String pdfName) {
        if (pdfName == null || pdfName.trim().isEmpty()) {
            return "";
        }
        File directFile = new File(pdfName);
        if (directFile.isAbsolute() && directFile.exists()) {
            return directFile.getAbsolutePath();
        }

        String safeName = sanitizeFileName(pdfName);
        if (!safeName.isEmpty()) {
            File importedFile = new File(new File(mContext.getFilesDir(), SCORES_DIR), safeName);
            if (importedFile.exists() && importedFile.length() > 0) {
                return importedFile.getAbsolutePath();
            }
        }

        return pdfName;
    }

    /**
     * Checks whether a hymn's musical score PDF is actually available locally
     * (either imported in internal storage or bundled in APK assets).
     */
    public synchronized boolean hasPdfAvailable(String pdfName) {
        if (pdfName == null || pdfName.trim().isEmpty()) return false;
        File directFile = new File(pdfName);
        if (directFile.isAbsolute() && directFile.exists() && directFile.length() > 0) {
            return true;
        }
        String safeName = sanitizeFileName(pdfName);
        if (safeName.isEmpty()) return false;

        File importedFile = new File(new File(mContext.getFilesDir(), SCORES_DIR), safeName);
        if (importedFile.exists() && importedFile.length() > 0) {
            return true;
        }

        if (mAssetPdfCache == null) {
            mAssetPdfCache = new HashSet<>();
            try {
                String[] assets = mContext.getAssets().list("");
                if (assets != null) {
                    for (String a : assets) {
                        if (a.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                            mAssetPdfCache.add(a);
                        }
                    }
                }
            } catch (IOException ignored) {}
        }
        return mAssetPdfCache.contains(safeName);
    }

    /**
     * Returns the number of imported musical score PDFs installed on this device.
     */
    public int getImportedScoresCount() {
        File scoresDir = new File(mContext.getFilesDir(), SCORES_DIR);
        File[] files = scoresDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".pdf"));
        return files != null ? files.length : 0;
    }

    /**
     * Retrieves the merged hymnal list for a given category (built-in + imported).
     */
    public synchronized ArrayList<HashMap<String, Object>> getHymns(String category) {
        category = sanitizeCategory(category, CATEGORY_NEW);
        if (mMergedCache.containsKey(category)) {
            return new ArrayList<>(mMergedCache.get(category));
        }

        ArrayList<HashMap<String, Object>> baseList = loadBaseHymns(category);
        ArrayList<HashMap<String, Object>> customList = loadCustomHymns(category);

        ArrayList<HashMap<String, Object>> merged = new ArrayList<>(baseList.size() + customList.size());
        Map<String, Integer> titleIndexMap = new HashMap<>(baseList.size() * 2);

        for (HashMap<String, Object> baseItem : baseList) {
            String key = getTitle(baseItem).toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) {
                titleIndexMap.put(key, merged.size());
            }
            merged.add(baseItem);
        }

        for (HashMap<String, Object> customItem : customList) {
            String key = getTitle(customItem).toLowerCase(Locale.ROOT);
            Integer matchIndex = !key.isEmpty() ? titleIndexMap.get(key) : null;
            if (matchIndex != null) {
                // Preserve built-in PDF_File if custom entry did not override it
                HashMap<String, Object> existing = merged.get(matchIndex);
                Object customPdf = customItem.get("PDF_File");
                if ((customPdf == null || customPdf.toString().trim().isEmpty()) && existing.get("PDF_File") != null) {
                    customItem.put("PDF_File", existing.get("PDF_File"));
                }
                merged.set(matchIndex, customItem);
            } else {
                if (!key.isEmpty()) {
                    titleIndexMap.put(key, merged.size());
                }
                merged.add(customItem);
            }
        }

        for (HashMap<String, Object> item : merged) {
            enrichHymnMetadata(item);
        }

        mMergedCache.put(category, merged);
        return new ArrayList<>(merged);
    }

    /**
     * Ranked search helper: exact hymn number match -> number prefix -> title match -> lyrics match.
     */
    public static ArrayList<HashMap<String, Object>> searchHymnsRanked(
            List<HashMap<String, Object>> masterList, String rawQuery) {
        ArrayList<HashMap<String, Object>> results = new ArrayList<>();
        if (masterList == null) return results;

        String trimmed = rawQuery != null ? rawQuery.trim() : "";
        if (trimmed.isEmpty()) {
            results.addAll(masterList);
            return results;
        }

        String q = EfikHymnsActivity.normalizeEfik(trimmed);
        String qN = q.replace("ng", "n");

        ArrayList<HashMap<String, Object>> exactNum = new ArrayList<>();
        ArrayList<HashMap<String, Object>> prefixNum = new ArrayList<>();
        ArrayList<HashMap<String, Object>> titleMatch = new ArrayList<>();
        ArrayList<HashMap<String, Object>> lyricsMatch = new ArrayList<>();

        for (HashMap<String, Object> item : masterList) {
            if (!EfikHymnsActivity.matchesPrecomputedHymn(item, q, qN)) continue;

            String num = item.get("_parsed_num") != null
                    ? item.get("_parsed_num").toString().toLowerCase(Locale.ROOT)
                    : "";
            String normTitle = item.get("_norm_title") != null ? item.get("_norm_title").toString() : "";
            String normTitleN = item.get("_norm_title_n") != null ? item.get("_norm_title_n").toString() : "";

            if (!num.isEmpty() && num.equals(q)) {
                exactNum.add(item);
            } else if (!num.isEmpty() && num.startsWith(q)) {
                prefixNum.add(item);
            } else if (normTitle.contains(q) || normTitleN.contains(qN)) {
                titleMatch.add(item);
            } else {
                lyricsMatch.add(item);
            }
        }

        results.addAll(exactNum);
        results.addAll(prefixNum);
        results.addAll(titleMatch);
        results.addAll(lyricsMatch);
        return results;
    }

    /**
     * Pre-computes normalized search indices and parsed hymn numbers/titles once at load time.
     */
    private void enrichHymnMetadata(HashMap<String, Object> item) {
        if (item == null || item.containsKey("_norm_search")) return;
        String fullTitle = getTitle(item);
        Object lObj = item.get("Lyrics");
        if (lObj == null) lObj = item.get("lyrics");
        String lyrics = lObj != null ? lObj.toString() : "";

        String normTitle = EfikHymnsActivity.normalizeEfik(fullTitle);
        String normAll = EfikHymnsActivity.normalizeEfik(fullTitle + "\n" + lyrics);
        item.put("_norm_title", normTitle);
        item.put("_norm_title_n", normTitle.replace("ng", "n"));
        item.put("_norm_search", normAll);
        item.put("_norm_search_n", normAll.replace("ng", "n"));

        java.util.regex.Matcher m = EfikHymnsActivity.HYMN_NUM_PATTERN.matcher(fullTitle);
        if (m.find()) {
            item.put("_parsed_num", m.group(1));
            item.put("_parsed_clean_title", m.group(2).trim());
        } else {
            item.put("_parsed_num", "");
            item.put("_parsed_clean_title", fullTitle);
        }
    }

    private String getTitle(HashMap<String, Object> item) {
        if (item == null) return "";
        Object t = item.get("Title");
        if (t == null) t = item.get("title");
        return t != null ? t.toString().trim() : "";
    }

    private int findMatchingHymnIndex(List<HashMap<String, Object>> list, String title) {
        if (title == null || title.isEmpty()) return -1;
        String cleanTitle = title.toLowerCase(Locale.ROOT).trim();
        for (int i = 0; i < list.size(); i++) {
            String currentTitle = getTitle(list.get(i)).toLowerCase(Locale.ROOT).trim();
            if (currentTitle.equals(cleanTitle)) {
                return i;
            }
        }
        return -1;
    }

    private ArrayList<HashMap<String, Object>> loadBaseHymns(String category) {
        String assetName;
        if (CATEGORY_SOC.equalsIgnoreCase(category)) {
            assetName = "soc_hymns.json";
        } else if (CATEGORY_EFIK.equalsIgnoreCase(category)) {
            assetName = "efik_hymns.json";
        } else {
            assetName = "new_hymns.json";
        }

        try {
            String json = AssetUtil.loadAssetString(mContext, assetName);
            Type listType = new TypeToken<ArrayList<HashMap<String, Object>>>() {}.getType();
            ArrayList<HashMap<String, Object>> list = mGson.fromJson(json, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Error loading base hymns for " + category, e);
            return new ArrayList<>();
        }
    }

    private HashMap<String, ArrayList<HashMap<String, Object>>> readAllCustomHymns() throws IOException {
        File file = new File(mContext.getFilesDir(), CUSTOM_HYMNS_FILE);
        if (!file.exists()) {
            return new HashMap<>();
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type mapType = new TypeToken<HashMap<String, ArrayList<HashMap<String, Object>>>>() {}.getType();
            HashMap<String, ArrayList<HashMap<String, Object>>> store = mGson.fromJson(reader, mapType);
            return store != null ? store : new HashMap<>();
        } catch (RuntimeException e) {
            throw new IOException("Corrupt custom hymns store", e);
        }
    }

    /**
     * Loads custom/imported hymns stored in internal app storage.
     */
    public synchronized ArrayList<HashMap<String, Object>> loadCustomHymns(String category) {
        category = sanitizeCategory(category, CATEGORY_NEW);
        try {
            HashMap<String, ArrayList<HashMap<String, Object>>> store = readAllCustomHymns();
            ArrayList<HashMap<String, Object>> list = store.get(category);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Error loading custom hymns", e);
            return new ArrayList<>();
        }
    }

    /**
     * Persists custom hymns atomically in UTF-8 via temp file + rename and invalidates caches.
     */
    private synchronized boolean saveCustomHymns(String category, ArrayList<HashMap<String, Object>> items) {
        category = sanitizeCategory(category, CATEGORY_NEW);
        File file = new File(mContext.getFilesDir(), CUSTOM_HYMNS_FILE);
        HashMap<String, ArrayList<HashMap<String, Object>>> store;

        if (file.exists()) {
            try {
                store = readAllCustomHymns();
            } catch (IOException e) {
                Log.e(TAG, "Refusing to silently overwrite unreadable custom_hymns.json; backing up first", e);
                File backup = new File(mContext.getFilesDir(), CUSTOM_HYMNS_FILE + ".corrupt.bak");
                file.renameTo(backup);
                store = new HashMap<>();
            }
        } else {
            store = new HashMap<>();
        }

        // Strip transient computed keys before persisting
        ArrayList<HashMap<String, Object>> cleanItems = new ArrayList<>(items.size());
        for (HashMap<String, Object> item : items) {
            HashMap<String, Object> copy = new HashMap<>();
            for (Map.Entry<String, Object> e : item.entrySet()) {
                if (!e.getKey().startsWith("_")) {
                    copy.put(e.getKey(), e.getValue());
                }
            }
            cleanItems.add(copy);
        }
        store.put(category, cleanItems);

        File tempFile = new File(mContext.getFilesDir(), CUSTOM_HYMNS_FILE + ".tmp");
        boolean replaced = false;
        try {
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                mGson.toJson(store, writer);
                writer.flush();
            }
            replaced = tempFile.renameTo(file);
            if (!replaced) {
                if (file.exists()) file.delete();
                replaced = tempFile.renameTo(file);
            }
            if (replaced) {
                mMergedCache.remove(category);
            }
            return replaced;
        } catch (IOException e) {
            Log.e(TAG, "Error saving custom hymns", e);
            return false;
        } finally {
            if (!replaced && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * Adds or updates a single hymn manually, preserving any existing attached PDF_File if none is provided.
     */
    public synchronized boolean addOrUpdateHymn(String category, String title, String lyrics, String pdfFileName) {
        if (title == null || title.trim().isEmpty()) return false;
        category = sanitizeCategory(category, CATEGORY_NEW);

        String trimmedTitle = title.trim();
        String resolvedPdf = pdfFileName != null ? pdfFileName.trim() : "";

        // Preserve existing attached score (from custom or built-in list) if editing lyrics only
        if (resolvedPdf.isEmpty()) {
            ArrayList<HashMap<String, Object>> merged = getHymns(category);
            int mergedIdx = findMatchingHymnIndex(merged, trimmedTitle);
            if (mergedIdx >= 0) {
                Object existingPdf = merged.get(mergedIdx).get("PDF_File");
                if (existingPdf != null && !existingPdf.toString().trim().isEmpty()) {
                    resolvedPdf = existingPdf.toString().trim();
                }
            }
        }

        ArrayList<HashMap<String, Object>> customList = loadCustomHymns(category);
        HashMap<String, Object> newItem = new HashMap<>();
        newItem.put("Title", trimmedTitle);
        newItem.put("Lyrics", lyrics != null ? lyrics : "");
        newItem.put("PDF_File", resolvedPdf);

        int index = findMatchingHymnIndex(customList, trimmedTitle);
        if (index >= 0) {
            customList.set(index, newItem);
        } else {
            customList.add(newItem);
        }

        return saveCustomHymns(category, customList);
    }

    /**
     * Inspects a hymn/score package (.acc / .zip / .json) using streaming I/O so large score packs
     * (30+ MB) never trigger OutOfMemoryError.
     */
    public HymnPackagePreview inspectPackage(Uri uri) {
        String fileName = "hymn_package";
        long fileSize = 0;

        if (uri == null) {
            return HymnPackagePreview.failure("No file selected.");
        }

        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = mContext.getContentResolver().query(
                    uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIdx != -1) {
                        String n = cursor.getString(nameIdx);
                        if (n != null && !n.trim().isEmpty()) fileName = n;
                    }
                    int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx);
                }
            } catch (Exception ignored) {}
        } else if (uri.getPath() != null) {
            File f = new File(uri.getPath());
            fileName = f.getName();
            fileSize = f.length();
        }

        if (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            return HymnPackagePreview.failure("Cannot import Android application packages (.apk). Please choose a valid hymn package (.acc, .hymn, or .json).");
        }

        try (InputStream raw = mContext.getContentResolver().openInputStream(uri)) {
            if (raw == null) {
                return HymnPackagePreview.failure("Unable to open the selected file.");
            }
            return inspectStream(raw, fileName, fileSize);
        } catch (Exception e) {
            Log.e(TAG, "Failed reading file data", e);
            return HymnPackagePreview.failure("Error reading file: " + e.getMessage());
        }
    }

    /**
     * Streams a ZIP (.acc/.hymn) or plain JSON input stream safely with Zip-Bomb & Zip-Slip guards.
     */
    public HymnPackagePreview inspectStream(InputStream rawStream, String fileName, long fileSize) {
        HymnPackagePreview preview = new HymnPackagePreview();
        preview.fileName = (fileName != null && !fileName.trim().isEmpty()) ? fileName : "hymn_package";
        preview.fileSize = fileSize;

        BufferedInputStream bis = new BufferedInputStream(rawStream, 16384);
        bis.mark(8);
        byte[] header = new byte[4];
        int headerRead;
        try {
            headerRead = bis.read(header);
            bis.reset();
        } catch (IOException e) {
            return HymnPackagePreview.failure("Error reading file header: " + e.getMessage());
        }

        if (headerRead <= 0) {
            return HymnPackagePreview.failure("The selected file is empty.");
        }

        boolean isZip = (headerRead == 4
                && header[0] == 0x50 && header[1] == 0x4B
                && header[2] == 0x03 && header[3] == 0x04);

        List<HashMap<String, Object>> incomingHymns;

        if (isZip) {
            boolean isApk = false;
            String preferredJsonManifest = null;
            String fallbackJsonManifest = null;
            int entryCount = 0;
            long totalExtracted = 0;

            File stagingDir = new File(mContext.getCacheDir(), "import_staging_" + UUID.randomUUID().toString());
            stagingDir.mkdirs();
            preview.stagingDir = stagingDir;

            try (ZipInputStream zis = new ZipInputStream(bis)) {
                String canonicalStaging = stagingDir.getCanonicalPath() + File.separator;
                ZipEntry entry;
                byte[] buf = new byte[16384];

                while ((entry = zis.getNextEntry()) != null) {
                    if (++entryCount > MAX_ZIP_ENTRIES) {
                        cleanupStaging(preview);
                        return HymnPackagePreview.failure("Package exceeds maximum allowed file count (" + MAX_ZIP_ENTRIES + ").");
                    }
                    if (entry.isDirectory()) {
                        zis.closeEntry();
                        continue;
                    }

                    String safeName = sanitizeFileName(entry.getName());
                    String lowerName = safeName.toLowerCase(Locale.ROOT);
                    if (lowerName.equals("androidmanifest.xml") || lowerName.endsWith(".dex") || lowerName.equals("resources.arsc")) {
                        isApk = true;
                        break;
                    }

                    if (lowerName.endsWith(".json")) {
                        ByteArrayOutputStream jsonBuf = new ByteArrayOutputStream();
                        int len;
                        long jsonBytes = 0;
                        while ((len = zis.read(buf)) != -1) {
                            jsonBytes += len;
                            totalExtracted += len;
                            if (jsonBytes > MAX_JSON_MANIFEST_BYTES || totalExtracted > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                cleanupStaging(preview);
                                return HymnPackagePreview.failure("Package manifest exceeds safety size limits.");
                            }
                            jsonBuf.write(buf, 0, len);
                        }
                        String content = jsonBuf.toString(StandardCharsets.UTF_8.name());
                        if ("hymns.json".equals(lowerName)) {
                            preferredJsonManifest = content;
                        } else if (fallbackJsonManifest == null) {
                            fallbackJsonManifest = content;
                        }
                    } else if (lowerName.endsWith(".pdf") && !safeName.isEmpty()) {
                        File outFile = new File(stagingDir, safeName);
                        if (!outFile.getCanonicalPath().startsWith(canonicalStaging)) {
                            cleanupStaging(preview);
                            return HymnPackagePreview.failure("Invalid file path detected in package.");
                        }
                        try (FileOutputStream fos = new FileOutputStream(outFile)) {
                            int len;
                            while ((len = zis.read(buf)) != -1) {
                                totalExtracted += len;
                                if (totalExtracted > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                    cleanupStaging(preview);
                                    return HymnPackagePreview.failure("Package exceeds maximum uncompressed size limit.");
                                }
                                fos.write(buf, 0, len);
                            }
                        }
                        preview.stagedScoreFiles.put(safeName, outFile);
                    }
                    zis.closeEntry();
                }
            } catch (Exception e) {
                cleanupStaging(preview);
                return HymnPackagePreview.failure("Error reading compressed package: " + e.getMessage());
            }

            if (isApk) {
                cleanupStaging(preview);
                return HymnPackagePreview.failure("The selected file is an Android application package (.apk), not a hymn bundle.");
            }

            String jsonManifest = preferredJsonManifest != null ? preferredJsonManifest : fallbackJsonManifest;
            if (jsonManifest == null || jsonManifest.trim().isEmpty()) {
                cleanupStaging(preview);
                return HymnPackagePreview.failure("Package does not contain hymn data (hymns.json missing).");
            }

            if (preview.fileSize <= 0) {
                preview.fileSize = totalExtracted;
            }

            try {
                incomingHymns = parseHymnsJson(jsonManifest);
            } catch (Exception e) {
                cleanupStaging(preview);
                return HymnPackagePreview.failure("Failed parsing package manifest: " + e.getMessage());
            }
        } else {
            // Plain JSON format (bounded to MAX_JSON_MANIFEST_BYTES)
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int len;
                long total = 0;
                while ((len = bis.read(buf)) != -1) {
                    total += len;
                    if (total > MAX_JSON_MANIFEST_BYTES) {
                        return HymnPackagePreview.failure("JSON file exceeds maximum supported size (16 MB).");
                    }
                    baos.write(buf, 0, len);
                }
                if (preview.fileSize <= 0) preview.fileSize = total;
                String jsonStr = baos.toString(StandardCharsets.UTF_8.name()).trim();
                if (!jsonStr.startsWith("{") && !jsonStr.startsWith("[")) {
                    return HymnPackagePreview.failure("Unsupported file format: expected a valid .acc hymn package or JSON hymn file.");
                }
                incomingHymns = parseHymnsJson(jsonStr);
            } catch (Exception e) {
                return HymnPackagePreview.failure("Invalid file format: " + e.getMessage());
            }
        }

        if (incomingHymns == null || incomingHymns.isEmpty()) {
            cleanupStaging(preview);
            return HymnPackagePreview.failure("No valid hymns found in the selected file.");
        }

        // Categorize and build preview items with O(1) title sets (no repeated JSON parsing inside loop!)
        Map<String, ArrayList<HashMap<String, Object>>> toSave = new HashMap<>();
        Map<String, Set<String>> baseTitleSets = new HashMap<>();

        for (HashMap<String, Object> hymn : incomingHymns) {
            Object catObj = hymn.containsKey("category") ? hymn.get("category") : hymn.get("Category");
            String cat = sanitizeCategory(catObj != null ? catObj.toString() : null, CATEGORY_NEW);
            hymn.put("category", cat);

            if (!toSave.containsKey(cat)) {
                toSave.put(cat, loadCustomHymns(cat));
            }
            if (!baseTitleSets.containsKey(cat)) {
                Set<String> titles = new HashSet<>();
                for (HashMap<String, Object> baseItem : loadBaseHymns(cat)) {
                    String bt = getTitle(baseItem).toLowerCase(Locale.ROOT);
                    if (!bt.isEmpty()) titles.add(bt);
                }
                baseTitleSets.put(cat, titles);
            }

            ArrayList<HashMap<String, Object>> targetList = toSave.get(cat);
            String title = getTitle(hymn);
            int matchIndex = findMatchingHymnIndex(targetList, title);
            boolean isUpdate = (matchIndex >= 0) || baseTitleSets.get(cat).contains(title.toLowerCase(Locale.ROOT));

            if (isUpdate) {
                preview.updatedHymnsCount++;
                if (matchIndex >= 0) {
                    targetList.set(matchIndex, hymn);
                } else {
                    targetList.add(hymn);
                }
            } else {
                preview.newHymnsCount++;
                targetList.add(hymn);
            }

            // Extract lyric snippet
            String lyrics = hymn.containsKey("Lyrics") && hymn.get("Lyrics") != null ? hymn.get("Lyrics").toString().trim() : "";
            String snippet = "";
            if (!lyrics.isEmpty()) {
                String[] lines = lyrics.split("\n");
                StringBuilder snipBuf = new StringBuilder();
                int lineCount = 0;
                for (String l : lines) {
                    String clean = l.trim();
                    if (!clean.isEmpty()) {
                        if (lineCount > 0) snipBuf.append(" / ");
                        snipBuf.append(clean);
                        lineCount++;
                        if (lineCount >= 2 || snipBuf.length() > 90) break;
                    }
                }
                snippet = snipBuf.toString();
                if (snippet.length() > 95) {
                    snippet = snippet.substring(0, 92) + "...";
                }
            }

            Object pdfObj = hymn.get("PDF_File");
            String pdfStr = pdfObj != null ? sanitizeFileName(pdfObj.toString()) : "";
            boolean hasScore = !pdfStr.isEmpty()
                    && (preview.stagedScoreFiles.containsKey(pdfStr) || hasPdfAvailable(pdfStr));

            preview.items.add(new HymnPreviewItem(title, cat, isUpdate, hasScore, snippet));
        }

        preview.toSave = toSave;
        preview.totalHymns = incomingHymns.size();
        preview.scoresCount = preview.stagedScoreFiles.size();
        preview.isValid = true;
        return preview;
    }

    /**
     * Deletes any temporary staged files if the user cancels the import preview or after commit.
     */
    public void cleanupStaging(HymnPackagePreview preview) {
        if (preview == null || preview.stagingDir == null) return;
        deleteRecursive(preview.stagingDir);
        preview.stagingDir = null;
        preview.stagedScoreFiles.clear();
    }

    private static void deleteRecursive(File target) {
        if (target == null || !target.exists()) return;
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        target.delete();
    }

    /**
     * Commits the reviewed hymn package from staging to persistent internal storage.
     */
    public synchronized ImportResult commitImport(HymnPackagePreview preview) {
        if (preview == null || !preview.isValid) {
            return new ImportResult(false, preview != null ? preview.errorMessage : "Invalid import preview");
        }

        int savedScores = 0;
        try {
            if (!preview.stagedScoreFiles.isEmpty()) {
                File scoresDir = new File(mContext.getFilesDir(), SCORES_DIR);
                scoresDir.mkdirs();
                String canonicalScores = scoresDir.getCanonicalPath() + File.separator;

                for (Map.Entry<String, File> entry : preview.stagedScoreFiles.entrySet()) {
                    String safeName = sanitizeFileName(entry.getKey());
                    File stagedFile = entry.getValue();
                    if (safeName.isEmpty() || stagedFile == null || !stagedFile.exists()) continue;

                    File outFile = new File(scoresDir, safeName);
                    if (!outFile.getCanonicalPath().startsWith(canonicalScores)) continue;

                    if (moveOrCopyFile(stagedFile, outFile)) {
                        savedScores++;
                    }
                }
            }

            for (Map.Entry<String, ArrayList<HashMap<String, Object>>> entry : preview.toSave.entrySet()) {
                saveCustomHymns(entry.getKey(), entry.getValue());
            }

            ImportResult result = new ImportResult(true, String.format(Locale.getDefault(),
                    "Successfully imported %d hymn(s)%s (%d new, %d updated).",
                    preview.totalHymns,
                    savedScores > 0 ? (" and " + savedScores + " musical score(s)") : "",
                    preview.newHymnsCount,
                    preview.updatedHymnsCount));
            result.importedCount = preview.newHymnsCount;
            result.updatedCount = preview.updatedHymnsCount;
            result.scoresCount = savedScores;
            for (HymnPreviewItem item : preview.items) {
                result.hymnTitles.add(item.title);
            }
            return result;
        } catch (IOException e) {
            Log.e(TAG, "Error committing import", e);
            return new ImportResult(false, "Failed saving package: " + e.getMessage());
        } finally {
            cleanupStaging(preview);
        }
    }

    private static boolean moveOrCopyFile(File src, File dst) {
        if (dst.exists()) {
            dst.delete();
        }
        if (src.renameTo(dst)) {
            return true;
        }
        try (InputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[16384];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed copying score file " + dst.getName(), e);
            return false;
        }
    }

    /**
     * Parses incoming hymn list from a JSON string.
     */
    public List<HashMap<String, Object>> parseHymnsJson(String jsonStr) throws Exception {
        List<HashMap<String, Object>> list = new ArrayList<>();
        if (jsonStr == null || jsonStr.trim().isEmpty()) return list;

        jsonStr = jsonStr.trim();
        if (jsonStr.startsWith("[")) {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                list.add(jsonObjectToMap(obj));
            }
        } else if (jsonStr.startsWith("{")) {
            JSONObject root = new JSONObject(jsonStr);
            if (root.has("hymns")) {
                JSONArray arr = root.getJSONArray("hymns");
                for (int i = 0; i < arr.length(); i++) {
                    list.add(jsonObjectToMap(arr.getJSONObject(i)));
                }
            } else if (root.has("Title") || root.has("title")) {
                list.add(jsonObjectToMap(root));
            } else {
                Iterator<String> keys = root.keys();
                while (keys.hasNext()) {
                    String catKey = keys.next();
                    Object val = root.get(catKey);
                    if (val instanceof JSONArray) {
                        JSONArray arr = (JSONArray) val;
                        for (int i = 0; i < arr.length(); i++) {
                            HashMap<String, Object> map = jsonObjectToMap(arr.getJSONObject(i));
                            map.put("category", sanitizeCategory(catKey, CATEGORY_NEW));
                            list.add(map);
                        }
                    }
                }
            }
        }
        return list;
    }

    private HashMap<String, Object> jsonObjectToMap(JSONObject obj) {
        HashMap<String, Object> map = new HashMap<>();
        String title = obj.optString("Title", obj.optString("title", ""));
        String lyrics = obj.optString("Lyrics", obj.optString("lyrics", ""));
        String pdf = sanitizeFileName(obj.optString("PDF_File", obj.optString("pdf", obj.optString("pdf_file", ""))));
        String category = sanitizeCategory(obj.optString("category", obj.optString("Category", CATEGORY_NEW)), CATEGORY_NEW);

        map.put("Title", title);
        map.put("Lyrics", lyrics);
        map.put("PDF_File", pdf);
        map.put("category", category);
        return map;
    }

    /**
     * Exports custom hymns (and any referenced imported PDF scores) to a shareable .acc bundle.
     */
    public File exportHymnsPackage(String category) throws IOException {
        category = sanitizeCategory(category, CATEGORY_NEW);
        ArrayList<HashMap<String, Object>> customHymns = loadCustomHymns(category);
        if (customHymns.isEmpty()) {
            customHymns = loadBaseHymns(category);
        }

        File cacheDir = new File(mContext.getCacheDir(), "exports");
        cacheDir.mkdirs();
        File exportFile = new File(cacheDir, "church_hymns_" + category + ".acc");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(exportFile))) {
            zos.putNextEntry(new ZipEntry("hymns.json"));
            String json = mGson.toJson(customHymns);
            zos.write(json.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            File scoresDir = new File(mContext.getFilesDir(), SCORES_DIR);
            Set<String> includedPdfs = new HashSet<>();
            for (HashMap<String, Object> item : customHymns) {
                Object pdfObj = item.get("PDF_File");
                if (pdfObj != null && !pdfObj.toString().trim().isEmpty()) {
                    String pdfName = sanitizeFileName(pdfObj.toString());
                    if (!pdfName.isEmpty() && !includedPdfs.contains(pdfName)) {
                        File pdfFile = new File(scoresDir, pdfName);
                        if (pdfFile.exists()) {
                            zos.putNextEntry(new ZipEntry(pdfName));
                            try (FileInputStream fis = new FileInputStream(pdfFile)) {
                                byte[] buf = new byte[16384];
                                int len;
                                while ((len = fis.read(buf)) != -1) {
                                    zos.write(buf, 0, len);
                                }
                            }
                            zos.closeEntry();
                            includedPdfs.add(pdfName);
                        }
                    }
                }
            }
        }
        return exportFile;
    }

    /**
     * Resets custom hymns for a category and removes any orphaned imported score PDFs.
     */
    public synchronized void resetCategory(String category) {
        category = sanitizeCategory(category, CATEGORY_NEW);
        saveCustomHymns(category, new ArrayList<>());
        mMergedCache.remove(category);
        pruneOrphanedScores();
    }

    /**
     * Resets custom hymns across all categories and clears imported score PDFs.
     */
    public synchronized void resetAllCategories() {
        for (String cat : VALID_CATEGORIES) {
            saveCustomHymns(cat, new ArrayList<>());
            mMergedCache.remove(cat);
        }
        File scoresDir = new File(mContext.getFilesDir(), SCORES_DIR);
        File[] files = scoresDir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
    }

    private void pruneOrphanedScores() {
        Set<String> referenced = new HashSet<>();
        for (String cat : VALID_CATEGORIES) {
            for (HashMap<String, Object> item : loadCustomHymns(cat)) {
                Object pdfObj = item.get("PDF_File");
                if (pdfObj != null) {
                    String name = sanitizeFileName(pdfObj.toString());
                    if (!name.isEmpty()) referenced.add(name);
                }
            }
        }
        File scoresDir = new File(mContext.getFilesDir(), SCORES_DIR);
        File[] files = scoresDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!referenced.contains(f.getName())) {
                    f.delete();
                }
            }
        }
    }
}
