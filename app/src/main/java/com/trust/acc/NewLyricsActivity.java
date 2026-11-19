package com.trust.acc;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.trust.acc.databinding.NewLyricsBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class NewLyricsActivity extends AppCompatActivity {

	private static final String STATE_HYMN_INDEX = "state_hymn_index";
	private static final String STATE_SHOWING_PDF = "state_showing_pdf";

	private NewLyricsBinding binding;
	private double size = 18;
	private String hymnTitle = "";
	private String hymnLyrics = "";
	private String hymnPDF = "";
	private PdfPageAdapter currentPdfAdapter;
	private boolean isShowingPdf = false;
	private boolean pdfAvailableLocally = false;

	private SharedPreferences sp;

	private ArrayList<HashMap<String, Object>> masterHymns = new ArrayList<>();
	private int currentHymnIndex = -1;
	private GestureDetector gestureDetector;
	private final AtomicInteger pdfLoadSeq = new AtomicInteger(0);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		binding = NewLyricsBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		SystemBarUtil.applyEdgeToEdge(this, binding.getRoot());
		initialize();
		initializeLogic(savedInstanceState);
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(STATE_HYMN_INDEX, currentHymnIndex);
		outState.putBoolean(STATE_SHOWING_PDF, isShowingPdf);
	}

	@Override
	protected void onResume() {
		super.onResume();
		SystemBarUtil.updateSystemBarAppearance(this);
		applyTypographyPreferences();
		updateBookmarkIcon();
		updateScoreButtonState();
	}

	private void initialize() {
		sp = getSharedPreferences("settings", Activity.MODE_PRIVATE);

		binding.btnBack.setOnClickListener(_view -> finish());

		binding.btnBookmark.setOnClickListener(v -> toggleBookmark());

		// Long-press bookmark star opens Saved Manager
		binding.btnBookmark.setOnLongClickListener(v -> {
			startActivity(new Intent(this, SavedActivity.class));
			return true;
		});

		binding.btnAa.setOnClickListener(v -> showTypographyPopover());

		binding.btnToggle.setOnClickListener(_view -> {
			if (!pdfAvailableLocally) {
				showScorePackPrompt();
				return;
			}
			if (!isShowingPdf) {
				openPdfScoreView();
			} else {
				showLyricsView();
			}
		});

		// Density-scaled horizontal swipe detector for Previous / Next Hymn
		final float density = getResources().getDisplayMetrics().density;
		final float swipeThresholdPx = 56f * density;
		final float velocityThresholdPx = 150f * density;

		gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
				// Don't intercept swipe if currently viewing PDF (so user can pan/zoom sheet music)
				if (binding.pdfContainer.getVisibility() == View.VISIBLE) return false;
				if (e1 == null || e2 == null) return false;
				float diffX = e2.getX() - e1.getX();
				float diffY = e2.getY() - e1.getY();
				if (Math.abs(diffX) > Math.abs(diffY) * 1.3f
						&& Math.abs(diffX) > swipeThresholdPx
						&& Math.abs(velocityX) > velocityThresholdPx) {
					if (diffX > 0) {
						if (currentHymnIndex > 0) {
							displayHymn(currentHymnIndex - 1);
							return true;
						}
					} else {
						if (currentHymnIndex != -1 && currentHymnIndex < masterHymns.size() - 1) {
							displayHymn(currentHymnIndex + 1);
							return true;
						}
					}
				}
				return false;
			}
		});
	}

	private void showScorePackPrompt() {
		new AlertDialog.Builder(this)
				.setTitle("Musical Score Not Installed")
				.setMessage("To keep the app lightweight, musical score PDFs are imported on demand via .acc packs.\n\nDownload a score pack from GitHub Releases or import one shared by your congregation to view zoomable scores offline.")
				.setPositiveButton("Open Releases", (d, w) -> {
					try {
						startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SCORES_PACK_URL)));
					} catch (Exception e) {
						Toast.makeText(this, "Could not open browser: " + e.getMessage(), Toast.LENGTH_LONG).show();
					}
				})
				.setNegativeButton("Close", null)
				.show();
	}

	private void showLyricsView() {
		isShowingPdf = false;
		binding.pdfContainer.setVisibility(View.GONE);
		binding.scrollLyrics.setVisibility(View.VISIBLE);
		binding.btnToggle.setText("VIEW MUSICAL SCORE");
		binding.btnAa.setVisibility(View.VISIBLE);
	}

	private void openPdfScoreView() {
		isShowingPdf = true;
		binding.scrollLyrics.setVisibility(View.GONE);
		binding.pdfContainer.setVisibility(View.VISIBLE);
		binding.recyclerPdfPages.resetZoom();
		binding.btnToggle.setText("VIEW LYRICS");
		binding.btnAa.setVisibility(View.GONE);

		if (currentPdfAdapter == null) {
			loadPdfAsync(hymnPDF);
		}
	}

	private void showTypographyPopover() {
		TypographyHelper.showTypographyDialog(this, (int) size, new TypographyHelper.OnTypographyChangeListener() {
			@Override
			public void onFontSizeChanged(int newSizeSp) {
				size = newSizeSp;
				binding.txtLyrics.setTextSize((int) size);
				binding.tvBodyTitle.setTextSize((int) size + 4);
			}

			@Override
			public void onFontFamilyChanged(Typeface newTypeface, String familyKey) {
				if (newTypeface != null) {
					binding.txtLyrics.setTypeface(newTypeface, Typeface.NORMAL);
					binding.tvBodyTitle.setTypeface(newTypeface, Typeface.BOLD);
				}
			}
		});
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (gestureDetector != null && gestureDetector.onTouchEvent(ev)) {
			MotionEvent cancel = MotionEvent.obtain(ev);
			cancel.setAction(MotionEvent.ACTION_CANCEL);
			super.dispatchTouchEvent(cancel);
			cancel.recycle();
			return true;
		}
		return super.dispatchTouchEvent(ev);
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (currentPdfAdapter != null) {
			int newWidth = getResources().getDisplayMetrics().widthPixels;
			currentPdfAdapter.updateScreenWidth(newWidth);
			binding.recyclerPdfPages.resetZoom();
		}
	}

	private void applyTypographyPreferences() {
		if (sp.contains("font_size")) {
			try {
				size = Double.parseDouble(sp.getString("font_size", "18"));
			} catch (Exception ignored) {
				size = 18;
			}
		}
		size = Math.max(14, Math.min(34, size));
		binding.txtLyrics.setTextSize((int) size);
		binding.tvBodyTitle.setTextSize((int) size + 4);
		Typeface readingFace = TypographyHelper.getReadingTypeface(this);
		if (readingFace != null) {
			binding.txtLyrics.setTypeface(readingFace, Typeface.NORMAL);
			binding.tvBodyTitle.setTypeface(readingFace, Typeface.BOLD);
		}
	}

	private void initializeLogic(Bundle savedInstanceState) {
		final String passedTitle = getIntent().getStringExtra("Title");
		final String passedLyrics = getIntent().getStringExtra("Lyrics");
		final String passedPdf = getIntent().getStringExtra("pdf");
		final int restoredIndex = savedInstanceState != null ? savedInstanceState.getInt(STATE_HYMN_INDEX, -1) : -1;
		final boolean restorePdf = savedInstanceState != null && savedInstanceState.getBoolean(STATE_SHOWING_PDF, false);

		applyTypographyPreferences();

		if (restoredIndex == -1) {
			hymnTitle = passedTitle != null ? passedTitle : "New Hymn";
			hymnLyrics = passedLyrics != null ? passedLyrics : "";
			hymnPDF = passedPdf != null ? passedPdf : "";
			binding.txtTitle.setText(hymnTitle);
			binding.tvBodyTitle.setText(hymnTitle);
			binding.txtLyrics.setText(LyricsActivity.formatHymnLyrics(this, hymnLyrics));
			updateScoreButtonState();
			updateBookmarkIcon();
			if (restorePdf && pdfAvailableLocally) {
				openPdfScoreView();
			}
		}

		AccApplication.ioExecutor().execute(() -> {
			ArrayList<HashMap<String, Object>> loaded =
					HymnRepository.getInstance(getApplicationContext()).getHymns(HymnRepository.CATEGORY_NEW);
			int resolvedIdx = restoredIndex;
			if (resolvedIdx < 0 || resolvedIdx >= loaded.size()) {
				resolvedIdx = -1;
				if (passedTitle != null) {
					String target = passedTitle.trim();
					for (int idx = 0; idx < loaded.size(); idx++) {
						Object t = loaded.get(idx).get("Title");
						if (t != null && target.equalsIgnoreCase(t.toString().trim())) {
							resolvedIdx = idx;
							break;
						}
					}
				}
			}
			final int finalIdx = resolvedIdx;
			runOnUiThread(() -> {
				if (isFinishing() || isDestroyed()) return;
				masterHymns = loaded;
				if (finalIdx != -1) {
					currentHymnIndex = finalIdx;
					if (restoredIndex != -1) {
						displayHymn(finalIdx);
						if (restorePdf && pdfAvailableLocally) {
							openPdfScoreView();
						}
					} else {
						updateBookmarkIcon();
					}
				}
			});
		});
	}

	private void displayHymn(int index) {
		if (index < 0 || index >= masterHymns.size()) return;
		currentHymnIndex = index;
		HashMap<String, Object> item = masterHymns.get(index);
		hymnTitle = item.get("Title") != null ? item.get("Title").toString() : "";
		hymnLyrics = item.get("Lyrics") != null ? item.get("Lyrics").toString() : "";
		Object pdfObj = item.get("PDF_File");
		hymnPDF = pdfObj != null ? pdfObj.toString() : "";

		binding.txtTitle.setText(hymnTitle);
		binding.tvBodyTitle.setText(hymnTitle);
		binding.txtLyrics.setText(LyricsActivity.formatHymnLyrics(this, hymnLyrics));
		binding.scrollLyrics.scrollTo(0, 0);

		releasePdfAdapter();
		showLyricsView();
		updateScoreButtonState();
		updateBookmarkIcon();
	}

	private void updateScoreButtonState() {
		if (BuildConfig.ENABLE_SHEET_MUSIC && hymnPDF != null && !hymnPDF.trim().isEmpty()) {
			pdfAvailableLocally = HymnRepository.getInstance(this).hasPdfAvailable(hymnPDF);
			binding.barToggleScore.setVisibility(View.VISIBLE);
			binding.btnToggle.setVisibility(View.VISIBLE);
			if (!isShowingPdf) {
				binding.btnToggle.setText(pdfAvailableLocally ? "VIEW MUSICAL SCORE" : "GET MUSICAL SCORE PACK");
			}
		} else {
			pdfAvailableLocally = false;
			binding.barToggleScore.setVisibility(View.GONE);
			binding.btnToggle.setVisibility(View.GONE);
			binding.pdfContainer.setVisibility(View.GONE);
		}
	}

	private void releasePdfAdapter() {
		pdfLoadSeq.incrementAndGet();
		if (currentPdfAdapter != null) {
			currentPdfAdapter.close();
			currentPdfAdapter = null;
		}
		binding.recyclerPdfPages.setAdapter(null);
	}

	/**
	 * Opens PdfRenderer lazily on a background thread only when the user taps "VIEW MUSICAL SCORE".
	 */
	private void loadPdfAsync(final String pdfFile) {
		releasePdfAdapter();
		if (!BuildConfig.ENABLE_SHEET_MUSIC || pdfFile == null || pdfFile.trim().isEmpty()) return;

		final int seq = pdfLoadSeq.incrementAndGet();
		final int screenWidth = getResources().getDisplayMetrics().widthPixels;

		AccApplication.ioExecutor().execute(() -> {
			try {
				String resolvedPath = HymnRepository.getInstance(getApplicationContext()).resolvePdfPath(pdfFile);
				PdfRendererHelper helper = new PdfRendererHelper(getApplicationContext(), resolvedPath);
				runOnUiThread(() -> {
					if (isFinishing() || isDestroyed() || seq != pdfLoadSeq.get()) {
						AccApplication.ioExecutor().execute(helper::close);
						return;
					}
					currentPdfAdapter = new PdfPageAdapter(helper, screenWidth);
					binding.recyclerPdfPages.setLayoutManager(new LinearLayoutManager(this));
					binding.recyclerPdfPages.setAdapter(currentPdfAdapter);
				});
			} catch (Exception e) {
				runOnUiThread(() -> {
					if (isFinishing() || isDestroyed() || seq != pdfLoadSeq.get()) return;
					showLyricsView();
					Toast.makeText(this, "Could not open musical score: " + e.getMessage(), Toast.LENGTH_SHORT).show();
				});
			}
		});
	}

	private String getCurrentHymnId() {
		return SavedManager.getHymnBookmarkId("new", hymnTitle);
	}

	private String getLegacyHymnId() {
		return currentHymnIndex != -1 ? ("new_" + currentHymnIndex) : null;
	}

	private void updateBookmarkIcon() {
		boolean saved = SavedManager.isHymnSaved(this, "new", hymnTitle, currentHymnIndex);
		binding.btnBookmark.setImageResource(saved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark_border);
	}

	private void toggleBookmark() {
		String id = getCurrentHymnId();
		SavedManager.Item item = new SavedManager.Item(id, "new", hymnTitle, "New Hymns & Scores", hymnLyrics, hymnPDF, "", "");
		boolean nowSaved = SavedManager.toggleSave(this, item, getLegacyHymnId());
		updateBookmarkIcon();
		Toast.makeText(this, nowSaved ? "Saved" : "Removed from Saved", Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onDestroy() {
		releasePdfAdapter();
		super.onDestroy();
	}
}
