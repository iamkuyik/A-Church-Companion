package com.trust.acc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.trust.acc.databinding.LyricsBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

public class LyricsActivity extends AppCompatActivity {

	private static final String STATE_HYMN_INDEX = "state_hymn_index";
	private static final Pattern PAREN_PATTERN = Pattern.compile("\\(([^)]+)\\)");

	private LyricsBinding binding;
	private double size = 18;

	private SharedPreferences sp;
	private ArrayList<HashMap<String, Object>> masterHymns = new ArrayList<>();
	private int currentHymnIndex = -1;
	private String currentSourceType = "soc";
	private String currentRawTitle = "";
	private String currentRawLyrics = "";
	private GestureDetector gestureDetector;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		binding = LyricsBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		SystemBarUtil.applyEdgeToEdge(this, binding.getRoot());
		initialize();
		initializeLogic(savedInstanceState);
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(STATE_HYMN_INDEX, currentHymnIndex);
	}

	@Override
	protected void onResume() {
		super.onResume();
		SystemBarUtil.updateSystemBarAppearance(this);
		applyTypographyPreferences();
		updateBookmarkIcon();
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

		// Density-scaled horizontal swipe detector for Previous / Next Hymn
		final float density = getResources().getDisplayMetrics().density;
		final float swipeThresholdPx = 56f * density;
		final float velocityThresholdPx = 150f * density;

		gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
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

	private void showTypographyPopover() {
		TypographyHelper.showTypographyDialog(this, (int) size, new TypographyHelper.OnTypographyChangeListener() {
			@Override
			public void onFontSizeChanged(int newSizeSp) {
				size = newSizeSp;
				binding.tvLyrics.setTextSize((int) size);
				binding.tvTitle.setTextSize((int) size + 4);
			}

			@Override
			public void onFontFamilyChanged(Typeface newTypeface, String familyKey) {
				if (newTypeface != null) {
					binding.tvLyrics.setTypeface(newTypeface, Typeface.NORMAL);
					binding.tvTitle.setTypeface(newTypeface, Typeface.BOLD);
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

	private void applyTypographyPreferences() {
		if (sp.contains("font_size")) {
			try {
				size = Double.parseDouble(sp.getString("font_size", "18"));
			} catch (Exception ignored) {
				size = 18;
			}
		}
		size = Math.max(14, Math.min(34, size));
		binding.tvLyrics.setTextSize((int) size);
		binding.tvTitle.setTextSize((int) size + 4);
		Typeface readingFace = TypographyHelper.getReadingTypeface(this);
		if (readingFace != null) {
			binding.tvLyrics.setTypeface(readingFace, Typeface.NORMAL);
			binding.tvTitle.setTypeface(readingFace, Typeface.BOLD);
		}
	}

	private void initializeLogic(Bundle savedInstanceState) {
		final String passedTitle = getIntent().getStringExtra("Title");
		final String passedLyrics = getIntent().getStringExtra("Lyrics");
		String passedType = getIntent().getStringExtra("source_type");
		if (passedType != null && !passedType.isEmpty()) {
			currentSourceType = passedType;
		}
		final int restoredIndex = savedInstanceState != null ? savedInstanceState.getInt(STATE_HYMN_INDEX, -1) : -1;

		applyTypographyPreferences();

		// Render passed hymn immediately so screen opens with zero latency
		if (restoredIndex == -1) {
			currentRawTitle = passedTitle != null ? passedTitle : "Hymn";
			currentRawLyrics = passedLyrics != null ? passedLyrics : "";
			binding.tvTitle.setText(currentRawTitle);
			binding.headerTitle.setText(currentRawTitle);
			binding.tvLyrics.setText(formatHymnLyrics(this, currentRawLyrics));
			updateBookmarkIcon();
		}

		// Load full hymnal asynchronously for horizontal swipe navigation & state restoration
		final String category = "efik".equals(currentSourceType) ? HymnRepository.CATEGORY_EFIK : HymnRepository.CATEGORY_SOC;
		AccApplication.ioExecutor().execute(() -> {
			ArrayList<HashMap<String, Object>> loaded =
					HymnRepository.getInstance(getApplicationContext()).getHymns(category);
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
		currentRawTitle = item.get("Title") != null ? item.get("Title").toString() : "";
		currentRawLyrics = item.get("Lyrics") != null ? item.get("Lyrics").toString() : "";

		binding.tvTitle.setText(currentRawTitle);
		binding.headerTitle.setText(currentRawTitle);
		binding.tvLyrics.setText(formatHymnLyrics(this, currentRawLyrics));
		binding.vscroll1.scrollTo(0, 0);

		updateBookmarkIcon();
	}

	/**
	 * Formats hymn lyrics into Sacred Editorial HTML, styling [CHORUS] and [REFRAIN] headers
	 * in theme-aware gold and parenthetical harmony/echo lines in theme-aware muted italics.
	 */
	public static CharSequence formatHymnLyrics(Context context, String raw) {
		if (raw == null || raw.isEmpty()) return "";
		int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
		boolean isNight = currentNightMode == Configuration.UI_MODE_NIGHT_YES;
		String goldHex = isNight ? "#D4AF37" : "#9A722C";
		String echoHex = isNight ? "#94A3B8" : "#787167";

		String[] lines = raw.replace("\r\n", "\n").split("\n");
		StringBuilder sb = new StringBuilder(raw.length() + 128);

		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			if (line.equalsIgnoreCase("[CHORUS]") || line.equalsIgnoreCase("CHORUS:")) {
				sb.append("<br><font color='").append(goldHex).append("'><b>— CHORUS —</b></font><br>");
				continue;
			}
			if (line.equalsIgnoreCase("[REFRAIN]") || line.equalsIgnoreCase("REFRAIN:")) {
				sb.append("<br><font color='").append(goldHex).append("'><b>— REFRAIN —</b></font><br>");
				continue;
			}

			if (line.startsWith("(") && line.endsWith(")")) {
				line = "<i><font color='" + echoHex + "'>" + TextUtils.htmlEncode(line) + "</font></i>";
			} else {
				line = TextUtils.htmlEncode(line);
				line = PAREN_PATTERN.matcher(line).replaceAll("<i><font color='" + echoHex + "'>($1)</font></i>");
			}

			sb.append(line).append("<br>");
		}

		return Html.fromHtml(sb.toString(), Html.FROM_HTML_MODE_COMPACT);
	}

	private String getCurrentHymnId() {
		return SavedManager.getHymnBookmarkId(currentSourceType, currentRawTitle);
	}

	private String getLegacyHymnId() {
		return currentHymnIndex != -1 ? (currentSourceType + "_" + currentHymnIndex) : null;
	}

	private void updateBookmarkIcon() {
		boolean saved = SavedManager.isHymnSaved(this, currentSourceType, currentRawTitle, currentHymnIndex);
		binding.btnBookmark.setImageResource(saved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark_border);
	}

	private void toggleBookmark() {
		String id = getCurrentHymnId();
		String subtitle = "efik".equals(currentSourceType) ? "Ŋwed Ikwọ" : "Songs of the Church";

		SavedManager.Item item = new SavedManager.Item(id, currentSourceType, currentRawTitle, subtitle, currentRawLyrics, "", "", "");
		boolean nowSaved = SavedManager.toggleSave(this, item, getLegacyHymnId());
		updateBookmarkIcon();
		Toast.makeText(this, nowSaved ? "Saved" : "Removed from Saved", Toast.LENGTH_SHORT).show();
	}
}
