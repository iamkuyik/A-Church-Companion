package com.trust.acc;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.trust.acc.databinding.BibleReaderBinding;
import com.trust.acc.databinding.DialogBibleVersionPickerBinding;
import com.trust.acc.databinding.VerseRowBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class BibleReaderActivity extends AppCompatActivity {

	private static final String STATE_CHAPTER = "state_chapter_number";

	private BibleReaderBinding binding;
	private double size = 16;
	private Typeface activeReadingTypeface;

	private final ArrayList<HashMap<String, Object>> listmap = new ArrayList<>();
	private Listview1Adapter listAdapter;

	private SharedPreferences sp;

	private String activeVersionId = "efi";
	// Parallel View: two fully independent panes (any translation may be paired with any other)
	private String parallelPrimaryId = "efi";   // Pane 1 (top / left)
	private String parallelSecondaryId = "kjv"; // Pane 2 (indented below / right)
	private boolean isParallelMode = false;

	private String currentBookId = "10";
	private String currentBookName = "Genesis";
	private int currentChapterNumber = 1;
	private int totalChaptersInBook = 50;
	private String currentNameEfi = "GENESIS";
	private String targetVerse = null;
	private final HashSet<String> bookmarkedVersesInChapter = new HashSet<>();
	private int selectableItemBackgroundResId = 0;
	private GestureDetector gestureDetector;

	private final ExecutorService chapterExecutor = Executors.newSingleThreadExecutor();
	private final AtomicInteger loadSequence = new AtomicInteger(0);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		TypedValue outValue = new TypedValue();
		getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
		selectableItemBackgroundResId = outValue.resourceId;
		binding = BibleReaderBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		SystemBarUtil.applyEdgeToEdge(this, binding.getRoot());
		initialize();
		initializeLogic(savedInstanceState);
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(STATE_CHAPTER, currentChapterNumber);
	}

	@Override
	protected void onDestroy() {
		loadSequence.incrementAndGet();
		chapterExecutor.shutdownNow();
		super.onDestroy();
	}

	private void initialize() {
		sp = getSharedPreferences("settings", Activity.MODE_PRIVATE);
		activeVersionId = BibleVersion.getById(sp.getString("bible_version_id", "efi")).id;
		isParallelMode = sp.getBoolean("bible_parallel_mode", false);
		loadParallelPanePreferences();

		binding.btnBack.setOnClickListener(_view -> finish());

		binding.headerTitle.setOnClickListener(v -> showChapterJumpDialog());

		binding.btnVersionSelector.setOnClickListener(v -> showVersionPickerDialog());

		binding.btnBookmark.setOnClickListener(v -> toggleBookmark());

		// Long-press (press-and-hold) on the chapter bookmark star opens "View All Bookmarks"
		binding.btnBookmark.setOnLongClickListener(v -> {
			v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
			openAllBookmarks();
			return true;
		});

		binding.btnAa.setOnClickListener(v -> showTypographyPopover());

		// Bottom Chapter Navigation Bar (Prev / Chapter Jump / Next)
		binding.btnPrevChapter.setOnClickListener(v -> {
			if (currentChapterNumber > 1) {
				loadChapter(currentChapterNumber - 1);
			}
		});
		binding.btnNextChapter.setOnClickListener(v -> {
			if (currentChapterNumber < totalChaptersInBook) {
				loadChapter(currentChapterNumber + 1);
			}
		});
		binding.btnChapterJump.setOnClickListener(v -> showChapterJumpDialog());

		// Verse actions open strictly on long-press (prevents accidental popups while scrolling)
		binding.listview1.setOnItemLongClickListener((parent, view, position, id) -> {
			if (view != null) {
				view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
			}
			showVerseOptions(position);
			return true;
		});

		// Density-scaled horizontal swipe detector for Previous / Next Chapter
		final float density = getResources().getDisplayMetrics().density;
		final float horizontalThresholdPx = 56f * density;
		final float verticalLockPx = 48f * density;
		final float velocityThresholdPx = 150f * density;

		gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
				if (e1 == null || e2 == null) return false;
				float diffX = e2.getX() - e1.getX();
				float diffY = e2.getY() - e1.getY();
				if (Math.abs(diffX) > horizontalThresholdPx
						&& Math.abs(diffY) < verticalLockPx
						&& Math.abs(diffX) > Math.abs(diffY) * 1.3f
						&& Math.abs(velocityX) > velocityThresholdPx) {
					if (diffX > 0) {
						if (currentChapterNumber > 1) {
							loadChapter(currentChapterNumber - 1);
							return true;
						}
					} else if (currentChapterNumber < totalChaptersInBook) {
						loadChapter(currentChapterNumber + 1);
						return true;
					}
				}
				return false;
			}
		});
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (gestureDetector != null && gestureDetector.onTouchEvent(ev)) {
			// Cancel pressed/ripple state on underlying ListView when fling is consumed
			MotionEvent cancel = MotionEvent.obtain(ev);
			cancel.setAction(MotionEvent.ACTION_CANCEL);
			super.dispatchTouchEvent(cancel);
			cancel.recycle();
			return true;
		}
		return super.dispatchTouchEvent(ev);
	}

	private int parseBookId() {
		try {
			return (int) Double.parseDouble(currentBookId);
		} catch (Exception ignored) {
			return 10;
		}
	}

	private void updateChapterNavUi() {
		binding.btnChapterJump.setText("CHAPTER " + currentChapterNumber + " OF " + totalChaptersInBook + " ▾");
		boolean hasPrev = currentChapterNumber > 1;
		boolean hasNext = currentChapterNumber < totalChaptersInBook;
		binding.btnPrevChapter.setEnabled(hasPrev);
		binding.btnPrevChapter.setAlpha(hasPrev ? 1.0f : 0.4f);
		binding.btnNextChapter.setEnabled(hasNext);
		binding.btnNextChapter.setAlpha(hasNext ? 1.0f : 0.4f);
	}

	private void initializeLogic(Bundle savedInstanceState) {
		listAdapter = new Listview1Adapter(listmap);
		binding.listview1.setAdapter(listAdapter);

		String bId = getIntent().getStringExtra("book_id");
		String bName = getIntent().getStringExtra("book_name");
		String cNum = getIntent().getStringExtra("chapter_number");
		String efiName = getIntent().getStringExtra("name_efi");
		String tVerse = getIntent().getStringExtra("target_verse");
		String chapCountExtra = getIntent().getStringExtra("chapter_count");

		if (bId != null && !bId.isEmpty()) currentBookId = bId;
		if (bName != null && !bName.isEmpty()) currentBookName = bName;
		if (efiName != null && !efiName.isEmpty()) currentNameEfi = efiName;
		if (savedInstanceState != null) {
			currentChapterNumber = savedInstanceState.getInt(STATE_CHAPTER, 1);
		} else {
			if (tVerse != null && !tVerse.isEmpty()) targetVerse = tVerse;
			if (cNum != null && !cNum.isEmpty()) {
				try {
					currentChapterNumber = (int) Double.parseDouble(cNum);
				} catch (Exception ignored) {}
			}
		}

		boolean resolvedCount = false;
		if (chapCountExtra != null && !chapCountExtra.isEmpty()) {
			try {
				int parsed = (int) Double.parseDouble(chapCountExtra);
				if (parsed > 0) {
					totalChaptersInBook = parsed;
					resolvedCount = true;
				}
			} catch (Exception ignored) {}
		}
		if (!resolvedCount) {
			final int bookIdInt = parseBookId();
			AccApplication.ioExecutor().execute(() -> {
				int count = AssetUtil.getBookChapterCount(getApplicationContext(), bookIdInt);
				runOnUiThread(() -> {
					if (isFinishing() || isDestroyed()) return;
					totalChaptersInBook = count;
					updateChapterNavUi();
				});
			});
		}

		updateChapterNavUi();
		reloadTypographyPreferences();
		loadChapter(currentChapterNumber);
	}

	private void reloadTypographyPreferences() {
		if (sp.contains("font_size")) {
			try {
				size = Double.parseDouble(sp.getString("font_size", "16"));
			} catch (Exception ignored) {
				size = 16;
			}
		}
		size = Math.max(14, Math.min(34, size));
		activeReadingTypeface = TypographyHelper.getReadingTypeface(this);
	}

	private void showTypographyPopover() {
		TypographyHelper.showTypographyDialog(this, (int) size, new TypographyHelper.OnTypographyChangeListener() {
			@Override
			public void onFontSizeChanged(int newSizeSp) {
				size = newSizeSp;
				if (listAdapter != null) {
					listAdapter.notifyDataSetChanged();
				}
			}

			@Override
			public void onFontFamilyChanged(Typeface newTypeface, String familyKey) {
				activeReadingTypeface = newTypeface;
				if (listAdapter != null) {
					listAdapter.notifyDataSetChanged();
				}
			}
		});
	}

	/**
	 * Loads the two independent Parallel View panes. Migrates the legacy v1.3.0 Efik-locked
	 * preferences ("bible_parallel_eng_id" + "bible_parallel_efik_first") on first run.
	 */
	private void loadParallelPanePreferences() {
		if (sp.contains("bible_parallel_primary_id") && sp.contains("bible_parallel_secondary_id")) {
			parallelPrimaryId = BibleVersion.getById(this, sp.getString("bible_parallel_primary_id", "efi")).id;
			parallelSecondaryId = BibleVersion.getById(this, sp.getString("bible_parallel_secondary_id", "kjv")).id;
		} else {
			String legacyEng = BibleVersion.getById(this, sp.getString("bible_parallel_eng_id", "kjv")).id;
			if ("efi".equalsIgnoreCase(legacyEng)) legacyEng = "kjv";
			boolean legacyEfikFirst = sp.getBoolean("bible_parallel_efik_first", true);
			parallelPrimaryId = legacyEfikFirst ? "efi" : legacyEng;
			parallelSecondaryId = legacyEfikFirst ? legacyEng : "efi";
		}
		if (parallelPrimaryId.equalsIgnoreCase(parallelSecondaryId)) {
			parallelSecondaryId = pickAlternateVersion(parallelPrimaryId);
		}
	}

	private String pickAlternateVersion(String excludeId) {
		for (BibleVersion v : BibleVersion.getSupportedVersions(this)) {
			if (!v.id.equalsIgnoreCase(excludeId)) return v.id;
		}
		return "kjv";
	}

	private void setParallelPane(boolean isPaneOne, String versionId) {
		if (isPaneOne) {
			if (versionId.equalsIgnoreCase(parallelSecondaryId)) parallelSecondaryId = parallelPrimaryId;
			parallelPrimaryId = versionId;
		} else {
			if (versionId.equalsIgnoreCase(parallelPrimaryId)) parallelPrimaryId = parallelSecondaryId;
			parallelSecondaryId = versionId;
		}
	}

	/** Returns verse text for a translation, or an editorial placeholder when omitted. */
	private String getVerseText(HashMap<String, Object> verseMap, String versionId) {
		Object raw = verseMap.get(versionId);
		String text = raw != null ? raw.toString() : "";
		if (text.trim().isEmpty()) {
			return "[Verse omitted in " + BibleVersion.getById(versionId).abbrev + "]";
		}
		return text;
	}

	private String getCitationLabel(BibleVersion v) {
		if ("efi".equalsIgnoreCase(v.id)) {
			return (currentNameEfi != null && !currentNameEfi.isEmpty() ? currentNameEfi : v.name) + " (EFI):";
		}
		return v.name + " (" + v.abbrev + "):";
	}

	private void updateHeaderSubtitle() {
		if (isParallelMode) {
			BibleVersion p1 = BibleVersion.getById(parallelPrimaryId);
			BibleVersion p2 = BibleVersion.getById(parallelSecondaryId);
			binding.headerSubtitle.setText("Parallel (" + p1.abbrev + " + " + p2.abbrev + ")");
		} else {
			BibleVersion v = BibleVersion.getById(activeVersionId);
			binding.headerSubtitle.setText(v.name + " (" + v.abbrev + ")");
		}
	}

	private void saveReadingPreferences() {
		sp.edit()
				.putString("bible_version_id", activeVersionId)
				.putString("bible_parallel_primary_id", parallelPrimaryId)
				.putString("bible_parallel_secondary_id", parallelSecondaryId)
				.putBoolean("bible_parallel_mode", isParallelMode)
				.apply();
		updateHeaderSubtitle();
		if (listAdapter != null) {
			listAdapter.notifyDataSetChanged();
		}
	}

	private int dp(int value) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
	}

	private void openAllBookmarks() {
		startActivity(new Intent(this, SavedActivity.class));
	}

	/**
	 * Sacred Editorial modal dialog displaying a 5-column chapter grid (1..totalChaptersInBook)
	 * so readers can jump directly to any chapter in the current book.
	 */
	private void showChapterJumpDialog() {
		int bookId = parseBookId();
		int chapters = AssetUtil.getBookChapterCount(this, bookId);
		if (chapters > 0) {
			totalChaptersInBook = chapters;
			updateChapterNavUi();
		}

		final Typeface serifFace = ResourcesCompat.getFont(this, R.font.crimson_pro);
		final Typeface monoFace = ResourcesCompat.getFont(this, R.font.jetbrains_mono);
		final int goldColor = ContextCompat.getColor(this, R.color.colorAntiqueGold);
		final int primaryColor = ContextCompat.getColor(this, R.color.colorTextPrimary);
		final int secondaryColor = ContextCompat.getColor(this, R.color.colorTextSecondary);

		LinearLayout rootCard = new LinearLayout(this);
		rootCard.setOrientation(LinearLayout.VERTICAL);
		rootCard.setBackgroundResource(R.drawable.bg_dialog_rounded);
		rootCard.setPadding(dp(20), dp(22), dp(20), dp(14));

		TextView tvKicker = new TextView(this);
		tvKicker.setText("JUMP TO CHAPTER");
		tvKicker.setTextSize(10.5f);
		tvKicker.setLetterSpacing(0.12f);
		if (monoFace != null) tvKicker.setTypeface(monoFace, Typeface.BOLD);
		tvKicker.setTextColor(goldColor);
		rootCard.addView(tvKicker);

		TextView tvTitle = new TextView(this);
		tvTitle.setText(currentBookName + (currentNameEfi != null && !currentNameEfi.isEmpty() ? " • " + currentNameEfi : ""));
		tvTitle.setTextSize(19f);
		if (serifFace != null) tvTitle.setTypeface(serifFace, Typeface.BOLD);
		tvTitle.setTextColor(primaryColor);
		LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		titleLp.topMargin = dp(4);
		titleLp.bottomMargin = dp(14);
		tvTitle.setLayoutParams(titleLp);
		rootCard.addView(tvTitle);

		AlertDialog dialog = new AlertDialog.Builder(this)
				.setView(rootCard)
				.setCancelable(true)
				.create();

		if (dialog.getWindow() != null) {
			dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
		}

		ScrollView scrollView = new ScrollView(this);
		int maxGridHeight = Math.min(dp(360), (int) (getResources().getDisplayMetrics().heightPixels * 0.55f));
		LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				totalChaptersInBook > 30 ? maxGridHeight : ViewGroup.LayoutParams.WRAP_CONTENT);
		scrollView.setLayoutParams(scrollLp);

		LinearLayout gridContainer = new LinearLayout(this);
		gridContainer.setOrientation(LinearLayout.VERTICAL);

		final int columns = 5;
		LinearLayout currentRow = null;
		for (int ch = 1; ch <= totalChaptersInBook; ch++) {
			if ((ch - 1) % columns == 0) {
				currentRow = new LinearLayout(this);
				currentRow.setOrientation(LinearLayout.HORIZONTAL);
				LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				rowLp.bottomMargin = dp(8);
				currentRow.setLayoutParams(rowLp);
				gridContainer.addView(currentRow);
			}

			final int targetCh = ch;
			boolean isCurrent = (ch == currentChapterNumber);
			TextView cell = new TextView(this);
			cell.setText(String.valueOf(ch));
			cell.setGravity(Gravity.CENTER);
			cell.setTextSize(14f);
			if (monoFace != null) cell.setTypeface(monoFace, Typeface.BOLD);
			cell.setTextColor(isCurrent ? goldColor : primaryColor);
			cell.setBackgroundResource(isCurrent ? R.drawable.bg_badge_gold : R.drawable.bg_chapter_cell);
			cell.setMinHeight(dp(44));
			LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
			if (ch % columns != 0) {
				cellLp.setMarginEnd(dp(8));
			}
			cell.setLayoutParams(cellLp);
			cell.setOnClickListener(v -> {
				dialog.dismiss();
				if (targetCh != currentChapterNumber) {
					loadChapter(targetCh);
				}
			});
			if (currentRow != null) {
				currentRow.addView(cell);
			}
		}

		int remainder = totalChaptersInBook % columns;
		if (remainder != 0 && currentRow != null) {
			int fillers = columns - remainder;
			for (int i = 0; i < fillers; i++) {
				View spacer = new View(this);
				LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
				if (i < fillers - 1) {
					spacerLp.setMarginEnd(dp(8));
				}
				spacer.setLayoutParams(spacerLp);
				currentRow.addView(spacer);
			}
		}

		scrollView.addView(gridContainer);
		rootCard.addView(scrollView);

		TextView btnClose = new TextView(this);
		btnClose.setText("CLOSE");
		btnClose.setGravity(Gravity.CENTER);
		btnClose.setTextSize(11.5f);
		if (monoFace != null) btnClose.setTypeface(monoFace, Typeface.BOLD);
		btnClose.setTextColor(secondaryColor);
		btnClose.setPadding(dp(16), dp(10), dp(16), dp(6));
		btnClose.setOnClickListener(v -> dialog.dismiss());
		LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		closeLp.gravity = Gravity.END;
		closeLp.topMargin = dp(4);
		btnClose.setLayoutParams(closeLp);
		rootCard.addView(btnClose);

		dialog.show();
	}

	private void showVersionPickerDialog() {
		DialogBibleVersionPickerBinding pickerBinding = DialogBibleVersionPickerBinding.inflate(getLayoutInflater());
		AlertDialog dialog = new AlertDialog.Builder(this)
				.setView(pickerBinding.getRoot())
				.setCancelable(true)
				.create();

		if (dialog.getWindow() != null) {
			dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
		}

		final List<BibleVersion> allVersions = BibleVersion.getSupportedVersions(this);
		final Typeface serifFace = ResourcesCompat.getFont(this, R.font.crimson_pro);
		final Typeface monoFace = ResourcesCompat.getFont(this, R.font.jetbrains_mono);

		final Runnable refreshDialogUi = new Runnable() {
			@Override
			public void run() {
				int goldColor = ContextCompat.getColor(BibleReaderActivity.this, R.color.colorAntiqueGold);
				int secondaryColor = ContextCompat.getColor(BibleReaderActivity.this, R.color.colorTextSecondary);
				int primaryColor = ContextCompat.getColor(BibleReaderActivity.this, R.color.colorTextPrimary);

				pickerBinding.tabModeSingle.setBackground(null);
				pickerBinding.tabModeParallel.setBackground(null);
				pickerBinding.tabModeSingle.setTextColor(secondaryColor);
				pickerBinding.tabModeParallel.setTextColor(secondaryColor);

				BibleVersion singleVer = BibleVersion.getById(activeVersionId);
				BibleVersion paneOne = BibleVersion.getById(parallelPrimaryId);
				BibleVersion paneTwo = BibleVersion.getById(parallelSecondaryId);

				pickerBinding.containerVersions.removeAllViews();
				pickerBinding.containerVersionsSecondary.removeAllViews();

				if (!isParallelMode) {
					pickerBinding.tabModeSingle.setBackgroundResource(R.drawable.bg_circle_accent);
					pickerBinding.tabModeSingle.setTextColor(goldColor);
					pickerBinding.tvLayoutSummaryTitle.setText("Single Translation: " + singleVer.name + " (" + singleVer.abbrev + ")");
					pickerBinding.tvLayoutSummaryDesc.setText("Full-width devotional reading in a single translation.");
					pickerBinding.tvVersionSectionLabel.setText("SELECT TRANSLATION");
					pickerBinding.tvSecondarySectionLabel.setVisibility(View.GONE);
					pickerBinding.containerVersionsSecondary.setVisibility(View.GONE);

					for (final BibleVersion v : allVersions) {
						boolean selected = v.id.equalsIgnoreCase(activeVersionId);
						pickerBinding.containerVersions.addView(buildVersionCard(v, selected,
								v.language + " Scripture", serifFace, monoFace, goldColor, secondaryColor, primaryColor,
								view -> {
									activeVersionId = v.id;
									saveReadingPreferences();
									run();
								}));
					}
				} else {
					pickerBinding.tabModeParallel.setBackgroundResource(R.drawable.bg_circle_accent);
					pickerBinding.tabModeParallel.setTextColor(goldColor);
					pickerBinding.tvLayoutSummaryTitle.setText(paneOne.name + " (" + paneOne.abbrev + ")  ⇄  " + paneTwo.name + " (" + paneTwo.abbrev + ")");
					pickerBinding.tvLayoutSummaryDesc.setText("1st translation on top • 2nd indented below. Tap either dropdown to switch.");
					pickerBinding.tvVersionSectionLabel.setText("1ST TRANSLATION (TOP)");
					pickerBinding.tvSecondarySectionLabel.setText("2ND TRANSLATION (BELOW)");
					pickerBinding.tvSecondarySectionLabel.setVisibility(View.VISIBLE);
					pickerBinding.containerVersionsSecondary.setVisibility(View.VISIBLE);

					// 1st Translation Compact Dropdown Selector Card
					View firstDropdown = buildDropdownSelectorCard(
							paneOne,
							paneOne.language + " Scripture • Tap to change 1st translation",
							serifFace, monoFace, goldColor, secondaryColor, primaryColor,
							anchor -> showTranslationDropdownPopup(anchor, true, allVersions, this));
					pickerBinding.containerVersions.addView(firstDropdown);

					// Centered Quick-Swap Pill Button between 1st and 2nd Selectors
					LinearLayout swapRow = new LinearLayout(BibleReaderActivity.this);
					swapRow.setOrientation(LinearLayout.HORIZONTAL);
					swapRow.setGravity(Gravity.CENTER);
					LinearLayout.LayoutParams swapRowLp = new LinearLayout.LayoutParams(
							ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
					swapRowLp.topMargin = dp(4);
					swapRow.setLayoutParams(swapRowLp);

					TextView btnSwap = new TextView(BibleReaderActivity.this);
					btnSwap.setText("⇅  SWAP 1ST & 2ND");
					btnSwap.setTextSize(10.5f);
					if (monoFace != null) btnSwap.setTypeface(monoFace, Typeface.BOLD);
					btnSwap.setTextColor(goldColor);
					btnSwap.setLetterSpacing(0.08f);
					btnSwap.setBackgroundResource(R.drawable.bg_version_pill);
					btnSwap.setPadding(dp(14), dp(6), dp(14), dp(6));
					btnSwap.setClickable(true);
					btnSwap.setFocusable(true);
					btnSwap.setOnClickListener(v -> {
						String tmp = parallelPrimaryId;
						parallelPrimaryId = parallelSecondaryId;
						parallelSecondaryId = tmp;
						saveReadingPreferences();
						run();
					});
					swapRow.addView(btnSwap);
					pickerBinding.containerVersions.addView(swapRow);

					// 2nd Translation Compact Dropdown Selector Card
					View secondDropdown = buildDropdownSelectorCard(
							paneTwo,
							paneTwo.language + " Scripture • Tap to change 2nd translation",
							serifFace, monoFace, goldColor, secondaryColor, primaryColor,
							anchor -> showTranslationDropdownPopup(anchor, false, allVersions, this));
					pickerBinding.containerVersionsSecondary.addView(secondDropdown);
				}
			}
		};

		pickerBinding.tabModeSingle.setOnClickListener(v -> {
			isParallelMode = false;
			saveReadingPreferences();
			refreshDialogUi.run();
		});

		pickerBinding.tabModeParallel.setOnClickListener(v -> {
			isParallelMode = true;
			if (parallelPrimaryId.equalsIgnoreCase(parallelSecondaryId)) {
				parallelSecondaryId = pickAlternateVersion(parallelPrimaryId);
			}
			saveReadingPreferences();
			refreshDialogUi.run();
		});

		pickerBinding.btnPickerDone.setOnClickListener(v -> dialog.dismiss());

		refreshDialogUi.run();
		dialog.show();
	}

	private void showTranslationDropdownPopup(View anchor, boolean isPaneOne,
	                                          List<BibleVersion> allVersions, Runnable onSelected) {
		PopupMenu popup = new PopupMenu(this, anchor);
		String currentId = isPaneOne ? parallelPrimaryId : parallelSecondaryId;
		String otherId = isPaneOne ? parallelSecondaryId : parallelPrimaryId;

		for (int i = 0; i < allVersions.size(); i++) {
			BibleVersion v = allVersions.get(i);
			String label = v.name + " (" + v.abbrev + ")";
			if (v.id.equalsIgnoreCase(currentId)) {
				label = "✓  " + label;
			} else if (v.id.equalsIgnoreCase(otherId)) {
				label = label + "  ⇄ Swap";
			}
			popup.getMenu().add(0, i, i, label);
		}

		popup.setOnMenuItemClickListener(item -> {
			int idx = item.getItemId();
			if (idx >= 0 && idx < allVersions.size()) {
				BibleVersion chosen = allVersions.get(idx);
				setParallelPane(isPaneOne, chosen.id);
				saveReadingPreferences();
				if (onSelected != null) {
					onSelected.run();
				}
				return true;
			}
			return false;
		});
		popup.show();
	}

	private View buildDropdownSelectorCard(BibleVersion v, String subtitle,
	                                       Typeface serifFace, Typeface monoFace,
	                                       int goldColor, int secondaryColor, int primaryColor,
	                                       View.OnClickListener onClick) {
		LinearLayout card = new LinearLayout(this);
		card.setOrientation(LinearLayout.HORIZONTAL);
		card.setGravity(Gravity.CENTER_VERTICAL);
		card.setBackgroundResource(R.drawable.bg_card_selected_subtle);
		card.setPadding(dp(14), dp(12), dp(14), dp(12));
		LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		cardLp.bottomMargin = dp(4);
		card.setLayoutParams(cardLp);
		card.setClickable(true);
		card.setFocusable(true);

		TextView badge = new TextView(this);
		badge.setText(v.abbrev);
		badge.setTextSize(11.5f);
		if (monoFace != null) badge.setTypeface(monoFace, Typeface.BOLD);
		badge.setTextColor(goldColor);
		badge.setBackgroundResource(R.drawable.bg_badge_gold);
		badge.setGravity(Gravity.CENTER);
		badge.setPadding(dp(10), dp(4), dp(10), dp(4));
		LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT);
		badgeLp.setMarginEnd(dp(12));
		badge.setLayoutParams(badgeLp);
		card.addView(badge);

		LinearLayout textCol = new LinearLayout(this);
		textCol.setOrientation(LinearLayout.VERTICAL);
		textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		TextView tvName = new TextView(this);
		tvName.setText(v.name);
		tvName.setTextSize(15.5f);
		if (serifFace != null) tvName.setTypeface(serifFace, Typeface.BOLD);
		tvName.setTextColor(primaryColor);
		textCol.addView(tvName);

		TextView tvSub = new TextView(this);
		tvSub.setText(subtitle);
		tvSub.setTextSize(12f);
		if (serifFace != null) tvSub.setTypeface(serifFace);
		tvSub.setTextColor(secondaryColor);
		textCol.addView(tvSub);

		card.addView(textCol);

		TextView chevron = new TextView(this);
		chevron.setText("SELECT ▾");
		chevron.setTextSize(10.5f);
		if (monoFace != null) chevron.setTypeface(monoFace, Typeface.BOLD);
		chevron.setTextColor(goldColor);
		chevron.setBackgroundResource(R.drawable.bg_version_pill);
		chevron.setPadding(dp(10), dp(5), dp(10), dp(5));
		card.addView(chevron);

		card.setOnClickListener(onClick);
		return card;
	}

	private View buildVersionCard(BibleVersion v, boolean isSelected, String subtitle,
	                              Typeface serifFace, Typeface monoFace,
	                              int goldColor, int secondaryColor, int primaryColor,
	                              View.OnClickListener onClick) {
		LinearLayout card = new LinearLayout(this);
		card.setOrientation(LinearLayout.HORIZONTAL);
		card.setGravity(Gravity.CENTER_VERTICAL);
		card.setBackgroundResource(isSelected ? R.drawable.bg_card_selected_subtle : R.drawable.bg_search_bar);
		card.setPadding(dp(14), dp(12), dp(14), dp(12));
		LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		cardLp.bottomMargin = dp(8);
		card.setLayoutParams(cardLp);
		card.setClickable(true);
		card.setFocusable(true);

		TextView badge = new TextView(this);
		badge.setText(v.abbrev);
		badge.setTextSize(11.5f);
		if (monoFace != null) badge.setTypeface(monoFace, Typeface.BOLD);
		badge.setTextColor(isSelected ? goldColor : secondaryColor);
		badge.setBackgroundResource(isSelected ? R.drawable.bg_badge_gold : R.drawable.bg_version_pill);
		badge.setGravity(Gravity.CENTER);
		badge.setPadding(dp(10), dp(4), dp(10), dp(4));
		LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT);
		badgeLp.setMarginEnd(dp(12));
		badge.setLayoutParams(badgeLp);
		card.addView(badge);

		LinearLayout textCol = new LinearLayout(this);
		textCol.setOrientation(LinearLayout.VERTICAL);
		textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		TextView tvName = new TextView(this);
		tvName.setText(v.name);
		tvName.setTextSize(15.5f);
		if (serifFace != null) tvName.setTypeface(serifFace, Typeface.BOLD);
		tvName.setTextColor(primaryColor);
		textCol.addView(tvName);

		TextView tvSub = new TextView(this);
		tvSub.setText(subtitle);
		tvSub.setTextSize(12f);
		if (serifFace != null) tvSub.setTypeface(serifFace);
		tvSub.setTextColor(secondaryColor);
		textCol.addView(tvSub);

		card.addView(textCol);

		TextView check = new TextView(this);
		check.setText(isSelected ? "●" : "○");
		check.setTextSize(14f);
		check.setTextColor(isSelected ? goldColor : secondaryColor);
		card.addView(check);

		card.setOnClickListener(onClick);
		return card;
	}

	private void loadChapter(final int requestedChapter) {
		if (requestedChapter < 1) return;

		final int seq = loadSequence.incrementAndGet();
		if (listmap.isEmpty()) {
			binding.progressbar1.setVisibility(View.VISIBLE);
			binding.listview1.setVisibility(View.GONE);
		}

		chapterExecutor.execute(() -> {
			try {
				int targetBook = (int) Double.parseDouble(currentBookId);
				final ArrayList<HashMap<String, Object>> tempMap =
						AssetUtil.getChapterVerses(getApplicationContext(), targetBook, requestedChapter);

				runOnUiThread(() -> {
					if (isFinishing() || isDestroyed() || seq != loadSequence.get()) return;
					if (!tempMap.isEmpty()) {
						currentChapterNumber = requestedChapter;
						reloadBookmarkedVerses();
						binding.headerTitle.setText(currentBookName + " " + currentChapterNumber);
						updateHeaderSubtitle();
						updateChapterNavUi();
						setTitle(currentBookName + " " + currentChapterNumber);
						updateBookmarkIcon();

						listmap.clear();
						listmap.addAll(tempMap);
						listAdapter.notifyDataSetChanged();
						if (targetVerse != null && !targetVerse.isEmpty()) {
							int targetPos = -1;
							for (int i = 0; i < listmap.size(); i++) {
								if (targetVerse.equals(listmap.get(i).get("verse_num"))) {
									targetPos = i;
									break;
								}
							}
							if (targetPos != -1) {
								final int finalPos = targetPos;
								binding.listview1.post(() -> {
									if (!isFinishing() && !isDestroyed()) {
										binding.listview1.setSelection(finalPos);
									}
								});
							} else {
								binding.listview1.setSelection(0);
							}
							targetVerse = null;
						} else {
							binding.listview1.setSelection(0);
						}
					} else {
						Toast.makeText(getApplicationContext(), "End of book reached", Toast.LENGTH_SHORT).show();
					}
					binding.progressbar1.setVisibility(View.GONE);
					binding.listview1.setVisibility(View.VISIBLE);
				});

			} catch (final Exception e) {
				runOnUiThread(() -> {
					if (isFinishing() || isDestroyed() || seq != loadSequence.get()) return;
					Toast.makeText(getApplicationContext(), "Reader Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
					binding.progressbar1.setVisibility(View.GONE);
					binding.listview1.setVisibility(View.VISIBLE);
				});
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		SystemBarUtil.updateSystemBarAppearance(this);
		reloadTypographyPreferences();
		reloadBookmarkedVerses();
		updateBookmarkIcon();
		if (listAdapter != null) {
			listAdapter.notifyDataSetChanged();
		}
	}

	@Override
	public void onTrimMemory(int level) {
		super.onTrimMemory(level);
		if (level >= TRIM_MEMORY_COMPLETE || level == TRIM_MEMORY_RUNNING_CRITICAL) {
			AssetUtil.clearBibleCache();
		}
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		AssetUtil.clearBibleCache();
	}

	private void reloadBookmarkedVerses() {
		bookmarkedVersesInChapter.clear();
		ArrayList<SavedManager.Item> allSaved = SavedManager.getSavedList(this);
		String prefix = "bible_verse_" + currentBookId + "_" + currentChapterNumber + "_";
		for (SavedManager.Item item : allSaved) {
			if (item != null && "bible_verse".equals(item.type) && item.id != null && item.id.startsWith(prefix)) {
				if (item.extra5 != null && !item.extra5.isEmpty()) {
					bookmarkedVersesInChapter.add(item.extra5);
				}
			}
		}
	}

	private String getCurrentBookmarkId() {
		return "bible_" + currentBookId + "_" + currentChapterNumber;
	}

	private void updateBookmarkIcon() {
		boolean saved = SavedManager.isSaved(this, getCurrentBookmarkId());
		binding.btnBookmark.setImageResource(saved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark_border);
	}

	private void toggleBookmark() {
		String id = getCurrentBookmarkId();
		String title = currentBookName + " " + currentChapterNumber;
		String subtitle = (currentNameEfi != null && !currentNameEfi.isEmpty() ? currentNameEfi + " " + currentChapterNumber + " • " : "") + "Parallel Scripture";
		SavedManager.Item item = new SavedManager.Item(id, "bible", title, subtitle, currentBookId, currentBookName, String.valueOf(currentChapterNumber), currentNameEfi);
		boolean nowSaved = SavedManager.toggleSave(this, item);
		updateBookmarkIcon();
		Toast.makeText(this, nowSaved ? "Saved" : "Removed from Saved", Toast.LENGTH_SHORT).show();
	}

	private void showVerseComparisonDialog(int position) {
		if (position < 0 || position >= listmap.size()) return;
		HashMap<String, Object> verse = listmap.get(position);
		final String verseNum = verse.get("verse_num") != null ? verse.get("verse_num").toString() : "";
		final String citation = currentBookName + " " + currentChapterNumber + ":" + verseNum;

		ScrollView scrollView = new ScrollView(this);
		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(dp(20), dp(14), dp(20), dp(14));

		Typeface serifFace = TypographyHelper.getReadingTypeface(this);
		Typeface monoFace = ResourcesCompat.getFont(this, R.font.jetbrains_mono);

		List<BibleVersion> versions = BibleVersion.getSupportedVersions(this);
		for (BibleVersion v : versions) {
			TextView label = new TextView(this);
			label.setText(v.name.toUpperCase(Locale.ROOT) + " (" + v.language.toUpperCase(Locale.ROOT) + ")");
			label.setTextSize(11);
			if (monoFace != null) {
				label.setTypeface(monoFace, Typeface.BOLD);
			} else {
				label.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
			}
			label.setTextColor(ContextCompat.getColor(this, R.color.colorAntiqueGold));
			label.setPadding(0, dp(8), 0, dp(4));
			layout.addView(label);

			TextView text = new TextView(this);
			text.setText(getVerseText(verse, v.id));
			text.setTextSize((int) size);
			text.setTypeface(serifFace != null ? serifFace : Typeface.SERIF);
			text.setTextColor(ContextCompat.getColor(this, R.color.colorTextPrimary));
			text.setLineSpacing(0, 1.4f);
			text.setPadding(0, 0, 0, dp(8));
			layout.addView(text);

			View divider = new View(this);
			divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
			divider.setBackgroundColor(ContextCompat.getColor(this, R.color.colorCardBorder));
			layout.addView(divider);
		}

		scrollView.addView(layout);

		new AlertDialog.Builder(this)
				.setTitle(citation + " • Translations")
				.setView(scrollView)
				.setPositiveButton("Close", null)
				.show();
	}

	private void showVerseOptions(final int position) {
		if (position < 0 || position >= listmap.size()) return;
		HashMap<String, Object> verse = listmap.get(position);
		final String verseNum = verse.get("verse_num") != null ? verse.get("verse_num").toString() : "";
		final String citation = currentBookName + " " + currentChapterNumber + ":" + verseNum;

		final String formattedText;
		final String primaryBookmarkExcerpt;
		final String secondaryBookmarkExcerpt;
		if (isParallelMode) {
			BibleVersion p1 = BibleVersion.getById(parallelPrimaryId);
			BibleVersion p2 = BibleVersion.getById(parallelSecondaryId);
			String p1Text = getVerseText(verse, p1.id);
			String p2Text = getVerseText(verse, p2.id);
			formattedText = citation + "\n\n"
					+ getCitationLabel(p1) + "\n" + p1Text + "\n\n"
					+ getCitationLabel(p2) + "\n" + p2Text;
			primaryBookmarkExcerpt = p1Text;
			secondaryBookmarkExcerpt = p2Text;
		} else {
			BibleVersion v = BibleVersion.getById(activeVersionId);
			String singleText = getVerseText(verse, v.id);
			formattedText = citation + " (" + v.abbrev + ")\n" + singleText;
			primaryBookmarkExcerpt = singleText;
			secondaryBookmarkExcerpt = singleText;
		}

		final boolean isBookmarked = bookmarkedVersesInChapter.contains(verseNum);
		String bookmarkAction = isBookmarked ? "Remove Bookmark & Highlight" : "Bookmark & Highlight Verse";

		AlertDialog.Builder builder = new AlertDialog.Builder(BibleReaderActivity.this);
		builder.setTitle(citation);
		String[] options = new String[]{"Compare Translations", bookmarkAction, "Copy Verse", "Share Verse"};
		builder.setItems(options, (dialog, which) -> {
			if (which == 0) {
				showVerseComparisonDialog(position);
			} else if (which == 1) {
				toggleVerseBookmark(verseNum, primaryBookmarkExcerpt, secondaryBookmarkExcerpt);
			} else if (which == 2) {
				ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
				ClipData clip = ClipData.newPlainText("Scripture Verse", formattedText);
				if (cm != null) {
					cm.setPrimaryClip(clip);
					Toast.makeText(getApplicationContext(), "Verse copied to clipboard", Toast.LENGTH_SHORT).show();
				}
			} else {
				Intent shareIntent = new Intent(Intent.ACTION_SEND);
				shareIntent.setType("text/plain");
				shareIntent.putExtra(Intent.EXTRA_SUBJECT, citation);
				shareIntent.putExtra(Intent.EXTRA_TEXT, formattedText);
				startActivity(Intent.createChooser(shareIntent, "Share Scripture"));
			}
		});
		builder.show();
	}

	private void toggleVerseBookmark(String verseNum, String primaryText, String secondaryText) {
		String id = SavedManager.getVerseBookmarkId(currentBookId, currentChapterNumber, verseNum);
		String title = currentBookName + " " + currentChapterNumber + ":" + verseNum;
		String excerpt = primaryText != null && !primaryText.isEmpty() ? primaryText : secondaryText;
		if (excerpt != null && excerpt.length() > 90) {
			excerpt = excerpt.substring(0, 90).trim() + "…";
		}
		String subtitle = (currentNameEfi != null && !currentNameEfi.isEmpty() ? currentNameEfi + " • " : "") + (excerpt != null ? excerpt : "");
		SavedManager.Item item = new SavedManager.Item(
				id,
				"bible_verse",
				title,
				subtitle,
				currentBookId,
				currentBookName,
				String.valueOf(currentChapterNumber),
				currentNameEfi,
				verseNum
		);
		boolean nowSaved = SavedManager.toggleSave(this, item);
		reloadBookmarkedVerses();
		if (listAdapter != null) {
			listAdapter.notifyDataSetChanged();
		}
		Toast.makeText(this, nowSaved ? "Verse Bookmarked & Highlighted" : "Removed Bookmark & Highlight", Toast.LENGTH_SHORT).show();
	}

	public class Listview1Adapter extends BaseAdapter {

		private final ArrayList<HashMap<String, Object>> _data;

		public Listview1Adapter(ArrayList<HashMap<String, Object>> arr) {
			_data = arr;
		}

		@Override
		public int getCount() {
			return _data.size();
		}

		@Override
		public HashMap<String, Object> getItem(int index) {
			return _data.get(index);
		}

		@Override
		public long getItemId(int index) {
			return index;
		}

		@Override
		public View getView(final int position, View convertView, ViewGroup container) {
			VerseRowBinding rowBinding;
			if (convertView == null) {
				rowBinding = VerseRowBinding.inflate(getLayoutInflater(), container, false);
				convertView = rowBinding.getRoot();
				convertView.setTag(rowBinding);
			} else {
				rowBinding = (VerseRowBinding) convertView.getTag();
			}

			HashMap<String, Object> verseMap = _data.get(position);
			String vNum = verseMap.get("verse_num") != null ? verseMap.get("verse_num").toString() : "";
			boolean isBookmarked = bookmarkedVersesInChapter.contains(vNum);
			rowBinding.tvVerseNum.setText(vNum);
			rowBinding.ivVerseBookmark.setVisibility(isBookmarked ? View.VISIBLE : View.GONE);

			int pStart = rowBinding.verseRowRoot.getPaddingStart();
			int pTop = rowBinding.verseRowRoot.getPaddingTop();
			int pEnd = rowBinding.verseRowRoot.getPaddingEnd();
			int pBottom = rowBinding.verseRowRoot.getPaddingBottom();
			if (isBookmarked) {
				rowBinding.verseRowRoot.setBackgroundResource(R.drawable.bg_verse_highlighted);
				rowBinding.tvVerseHighlightBadge.setVisibility(View.VISIBLE);
				rowBinding.tvVerseNum.setBackgroundResource(R.drawable.bg_badge_gold);
			} else {
				if (selectableItemBackgroundResId != 0) {
					rowBinding.verseRowRoot.setBackgroundResource(selectableItemBackgroundResId);
				} else {
					rowBinding.verseRowRoot.setBackground(null);
				}
				rowBinding.tvVerseHighlightBadge.setVisibility(View.GONE);
				rowBinding.tvVerseNum.setBackgroundResource(R.drawable.bg_verse_pill);
			}
			rowBinding.verseRowRoot.setPaddingRelative(pStart, pTop, pEnd, pBottom);

			if (isParallelMode) {
				rowBinding.tvPrimary.setText(getVerseText(verseMap, parallelPrimaryId));
				rowBinding.tvSecondary.setText(getVerseText(verseMap, parallelSecondaryId));
				rowBinding.tvSecondary.setVisibility(View.VISIBLE);
			} else {
				rowBinding.tvPrimary.setText(getVerseText(verseMap, activeVersionId));
				rowBinding.tvSecondary.setVisibility(View.GONE);
			}

			int currentFontPx = (int) size;
			rowBinding.tvPrimary.setTextSize(currentFontPx);
			rowBinding.tvSecondary.setTextSize(Math.max(currentFontPx - 1, 12));
			rowBinding.tvVerseNum.setTextSize(Math.max(currentFontPx - 4, 11));

			if (activeReadingTypeface != null) {
				rowBinding.tvPrimary.setTypeface(activeReadingTypeface, Typeface.NORMAL);
				rowBinding.tvSecondary.setTypeface(activeReadingTypeface, Typeface.ITALIC);
			}

			return convertView;
		}
	}
}
