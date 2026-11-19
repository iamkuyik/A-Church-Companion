package com.trust.acc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PdfRendererHelper {

    private ParcelFileDescriptor fileDescriptor;
    private PdfRenderer renderer;
    private File cachedFile;

    // Sacred Editorial warm parchment background (#FAF6EE) instead of stark #FFFFFF
    private static final int PARCHMENT_PAPER_COLOR = Color.rgb(250, 246, 238);

    public PdfRendererHelper(Context context, String assetPath) throws IOException {
        File file = new File(assetPath);
        if (file.isAbsolute() && file.exists()) {
            cachedFile = file;
        } else {
            File cacheDir = new File(context.getCacheDir(), "pdf_cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            String fileName = new File(assetPath).getName();
            cachedFile = new File(cacheDir, fileName);

            if (!cachedFile.exists() || cachedFile.length() == 0) {
                File tmpFile = new File(cacheDir, fileName + ".tmp");
                boolean moved = false;
                try {
                    try (InputStream in = context.getAssets().open(assetPath);
                         FileOutputStream out = new FileOutputStream(tmpFile)) {
                        byte[] buffer = new byte[16384];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                        out.flush();
                    }
                    moved = tmpFile.renameTo(cachedFile);
                    if (!moved) {
                        if (cachedFile.exists()) cachedFile.delete();
                        moved = tmpFile.renameTo(cachedFile);
                    }
                    if (!moved) {
                        throw new IOException("Failed caching PDF score: " + fileName);
                    }
                } finally {
                    if (!moved && tmpFile.exists()) {
                        tmpFile.delete();
                    }
                }
            }
        }

        fileDescriptor = ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY);
        try {
            renderer = new PdfRenderer(fileDescriptor);
        } catch (IOException | RuntimeException e) {
            // Also catches SecurityException on password-protected PDFs so FD never leaks
            close();
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Unable to open PDF score", e);
        }
    }

    public synchronized int getPageCount() {
        if (renderer == null) return 0;
        return renderer.getPageCount();
    }

    public synchronized Bitmap renderPage(int pageIndex, int targetWidth) throws IOException {
        if (renderer == null) {
            throw new IllegalStateException("PdfRenderer is closed");
        }
        if (pageIndex < 0 || pageIndex >= renderer.getPageCount()) {
            throw new IllegalArgumentException("Invalid page index: " + pageIndex);
        }

        PdfRenderer.Page page = renderer.openPage(pageIndex);
        try {
            int pw = Math.max(1, page.getWidth());
            int ph = Math.max(1, page.getHeight());
            float ratio = (float) ph / (float) pw;

            // Android PdfRenderer.Page.render() strictly requires Bitmap.Config.ARGB_8888.
            // Cap at 1800px wide for crisp pinch-to-zoom while keeping per-page memory bounded.
            int hiResWidth = Math.min(1800, Math.max(1080, (int) (targetWidth * 1.35f)));
            int height = Math.max(1, Math.round(hiResWidth * ratio));

            Bitmap bitmap;
            try {
                bitmap = Bitmap.createBitmap(hiResWidth, height, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError oom) {
                int fallbackWidth = Math.max(720, hiResWidth / 2);
                int fallbackHeight = Math.max(1, Math.round(fallbackWidth * ratio));
                bitmap = Bitmap.createBitmap(fallbackWidth, fallbackHeight, Bitmap.Config.ARGB_8888);
            }
            bitmap.eraseColor(PARCHMENT_PAPER_COLOR);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        } finally {
            page.close();
        }
    }

    public synchronized void close() {
        if (renderer != null) {
            try {
                renderer.close();
            } catch (Exception ignored) {}
            renderer = null;
        }
        if (fileDescriptor != null) {
            try {
                fileDescriptor.close();
            } catch (Exception ignored) {}
            fileDescriptor = null;
        }
    }
}
