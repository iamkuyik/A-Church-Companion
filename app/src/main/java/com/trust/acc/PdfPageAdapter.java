package com.trust.acc;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ColorMatrixColorFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.trust.acc.databinding.ItemPdfPageBinding;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfPageAdapter extends RecyclerView.Adapter<PdfPageAdapter.PageViewHolder> {

    private final PdfRendererHelper helper;
    private final int pageCount;
    private final LruCache<Integer, Bitmap> memoryCache;
    // Single worker matches PdfRendererHelper's synchronized rendering without wasting a second thread
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int screenWidth = 1080;
    private volatile boolean closed = false;

    // Subtle warm-dimmed filter for Dark Mode ("Midnight Sanctuary") to prevent eye strain
    private static final float[] NIGHT_DIM_MATRIX = new float[] {
            0.88f, 0f,    0f,    0f, 0f,
            0f,    0.86f, 0f,    0f, 0f,
            0f,    0f,    0.82f, 0f, 0f,
            0f,    0f,    0f,    1f, 0f
    };
    private static final ColorMatrixColorFilter NIGHT_DIM_FILTER = new ColorMatrixColorFilter(NIGHT_DIM_MATRIX);

    public PdfPageAdapter(PdfRendererHelper helper, int screenWidth) {
        this.helper = helper;
        this.pageCount = helper != null ? helper.getPageCount() : 0;
        if (screenWidth > 0) {
            this.screenWidth = screenWidth;
        }

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = Math.max(16384, maxMemory / 6);
        this.memoryCache = new LruCache<Integer, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(Integer key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public void updateScreenWidth(int newWidth) {
        if (!closed && newWidth > 0 && Math.abs(newWidth - this.screenWidth) > 40) {
            this.screenWidth = newWidth;
            this.memoryCache.evictAll();
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPdfPageBinding binding = ItemPdfPageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PageViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        holder.currentPosition = position;

        int nightFlags = holder.itemView.getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightFlags == Configuration.UI_MODE_NIGHT_YES) {
            holder.binding.imgPage.setColorFilter(NIGHT_DIM_FILTER);
        } else {
            holder.binding.imgPage.clearColorFilter();
        }

        Bitmap cached = memoryCache.get(position);
        if (cached != null) {
            holder.itemView.setMinimumHeight(0);
            holder.binding.imgPage.setImageBitmap(cached);
            holder.binding.progressLoading.setVisibility(View.GONE);
            holder.binding.imgPage.setVisibility(View.VISIBLE);
            return;
        }

        // Use setImageDrawable(null) (not setImageBitmap(null)) and a 220dp loading placeholder
        // so RecyclerView doesn't bind all off-screen pages at once or draw a blank 1:1 square.
        int placeholderPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 220, holder.itemView.getResources().getDisplayMetrics());
        holder.itemView.setMinimumHeight(placeholderPx);
        holder.binding.imgPage.setImageDrawable(null);
        holder.binding.progressLoading.setVisibility(View.VISIBLE);

        if (closed || helper == null) return;

        final int pos = position;
        final int targetW = this.screenWidth;
        executorService.execute(() -> {
            if (closed) return;
            try {
                final Bitmap bitmap = helper.renderPage(pos, targetW);
                if (closed) return;
                if (bitmap != null) {
                    memoryCache.put(pos, bitmap);
                    mainHandler.post(() -> {
                        if (!closed && holder.currentPosition == pos) {
                            holder.itemView.setMinimumHeight(0);
                            holder.binding.imgPage.setImageBitmap(bitmap);
                            holder.binding.progressLoading.setVisibility(View.GONE);
                            holder.binding.imgPage.setVisibility(View.VISIBLE);
                        }
                    });
                }
            } catch (Throwable t) {
                t.printStackTrace();
                mainHandler.post(() -> {
                    if (!closed && holder.currentPosition == pos) {
                        holder.binding.progressLoading.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    @Override
    public int getItemCount() {
        return pageCount;
    }

    /**
     * Releases caches immediately and closes PdfRenderer asynchronously on the worker thread
     * so Activity teardown never blocks the UI thread.
     */
    public void close() {
        if (closed) return;
        closed = true;
        mainHandler.removeCallbacksAndMessages(null);
        memoryCache.evictAll();
        executorService.execute(() -> {
            if (helper != null) {
                helper.close();
            }
        });
        executorService.shutdown();
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        final ItemPdfPageBinding binding;
        int currentPosition = -1;

        PageViewHolder(@NonNull ItemPdfPageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
