package com.trust.acc;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.trust.acc.databinding.DialogCreateHymnBinding;
import com.trust.acc.databinding.DialogImportPreviewBinding;
import com.trust.acc.databinding.EfikHymnRowBinding;
import com.trust.acc.databinding.ItemImportPreviewHymnBinding;
import com.trust.acc.databinding.NewHymnsBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class NewHymnsActivity extends AppCompatActivity {

	private NewHymnsBinding binding;
	private final ArrayList<HashMap<String, Object>> hymnListMap = new ArrayList<>();
	private ArrayList<HashMap<String, Object>> masterListMap = new ArrayList<>();
	private Listview1Adapter listAdapter;
	private AlertDialog activeProgressDialog;

	private final Handler searchHandler = new Handler(Looper.getMainLooper());
	private Runnable pendingSearch;

	private static final String[] HYMN_MIME_TYPES = new String[]{
			"application/json",
			"text/json",
			"application/octet-stream",
			"application/zip",
			"application/x-zip-compressed"
	};

	private final ActivityResultLauncher<String[]> filePickerLauncher =
			registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
				if (uri != null) {
					handleImportUri(uri);
				}
			});

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		binding = NewHymnsBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		SystemBarUtil.applyEdgeToEdge(this, binding.getRoot());
		initialize();
		loadHymnsAsync();

		// Only handle incoming file intent on fresh launch, not on theme/config recreation
		if (savedInstanceState == null) {
			handleIncomingIntent(getIntent());
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		SystemBarUtil.updateSystemBarAppearance(this);
		loadHymnsAsync();
	}

	@Override
	protected void onDestroy() {
		if (pendingSearch != null) {
			searchHandler.removeCallbacks(pendingSearch);
		}
		dismissProgressDialog();
		super.onDestroy();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		handleIncomingIntent(intent);
	}

	private void dismissProgressDialog() {
		if (activeProgressDialog != null && activeProgressDialog.isShowing()) {
			try {
				activeProgressDialog.dismiss();
			} catch (Exception ignored) {}
		}
		activeProgressDialog = null;
	}

	private void handleIncomingIntent(Intent intent) {
		if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
			String type = intent.getType();
			Uri uri = intent.getData();

			if ("application/vnd.android.package-archive".equalsIgnoreCase(type)
					|| (uri != null && uri.toString().toLowerCase(Locale.ROOT).endsWith(".apk"))) {
				new AlertDialog.Builder(this)
						.setTitle("Cannot Install APK")
						.setMessage("The Church of Christ Hymnal app cannot install or view APK applications. Please use Android's Package Installer.")
						.setPositiveButton("OK", null)
						.show();
				return;
			}

			if (uri != null) {
				handleImportUri(uri);
			}
		}
	}

	private void initialize() {
		binding.btnBack.setOnClickListener(v -> finish());

		binding.btnImport.setOnClickListener(v -> showAddOrImportDialog());

		binding.btnMenu.setOnClickListener(this::showOptionsMenu);

		binding.etSearch.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (pendingSearch != null) {
					searchHandler.removeCallbacks(pendingSearch);
				}
				final String q = s != null ? s.toString() : "";
				pendingSearch = () -> filterHymns(q);
				searchHandler.postDelayed(pendingSearch, 110);
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void afterTextChanged(Editable s) {}
		});

		listAdapter = new Listview1Adapter(hymnListMap);
		binding.listview1.setAdapter(listAdapter);
		binding.listview1.setFastScrollEnabled(true);

		binding.listview1.setOnItemClickListener((parent, view, position, id) -> {
			if (position < 0 || position >= hymnListMap.size()) return;
			HashMap<String, Object> selected = hymnListMap.get(position);

			Intent detailIntent = new Intent(this, NewLyricsActivity.class);
			detailIntent.putExtra("Title", selected.get("Title") != null ? selected.get("Title").toString() : "");
			detailIntent.putExtra("Lyrics", selected.get("Lyrics") != null ? selected.get("Lyrics").toString() : "");
			detailIntent.putExtra("pdf", selected.get("PDF_File") != null ? selected.get("PDF_File").toString() : "");
			startActivity(detailIntent);
		});
	}

	private void loadHymnsAsync() {
		AccApplication.ioExecutor().execute(() -> {
			ArrayList<HashMap<String, Object>> loaded =
					HymnRepository.getInstance(getApplicationContext()).getHymns(HymnRepository.CATEGORY_NEW);
			runOnUiThread(() -> {
				if (isFinishing() || isDestroyed()) return;
				masterListMap = loaded;
				String currentQuery = binding.etSearch.getText() != null ? binding.etSearch.getText().toString() : "";
				filterHymns(currentQuery);
			});
		});
	}

	private void filterHymns(String query) {
		hymnListMap.clear();
		hymnListMap.addAll(HymnRepository.searchHymnsRanked(masterListMap, query));
		if (listAdapter != null) {
			listAdapter.notifyDataSetChanged();
		}
	}

	private void showAddOrImportDialog() {
		String[] options = new String[]{
				"Import Hymn / Score Pack (.acc / .json)",
				"Download Musical Scores Pack (GitHub)",
				"Create New Hymn Manually"
		};
		new AlertDialog.Builder(this)
				.setTitle("Add or Import Hymns")
				.setItems(options, (dialog, which) -> {
					if (which == 0) {
						filePickerLauncher.launch(HYMN_MIME_TYPES);
					} else if (which == 1) {
						showDownloadScoresPackDialog();
					} else {
						showManualHymnCreateDialog();
					}
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void showDownloadScoresPackDialog() {
		int installed = HymnRepository.getInstance(this).getImportedScoresCount();
		String status = installed > 0
				? ("Currently installed on this device: " + installed + " musical score(s).\n\n")
				: "No musical score pack is installed yet.\n\n";
		new AlertDialog.Builder(this)
				.setTitle("Hymn & Musical Score Packs")
				.setMessage(status
						+ "Import any .acc hymn or musical score pack shared by your congregation or downloaded from GitHub Releases to unlock additional hymns and zoomable sheet-music scores offline.")
				.setPositiveButton("Open Releases Page", (d, w) -> {
					try {
						Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SCORES_PACK_URL));
						startActivity(browserIntent);
					} catch (Exception e) {
						Toast.makeText(this, "Could not open browser: " + e.getMessage(), Toast.LENGTH_LONG).show();
					}
				})
				.setNeutralButton("Import Downloaded File", (d, w) -> filePickerLauncher.launch(HYMN_MIME_TYPES))
				.setNegativeButton("Close", null)
				.show();
	}

	private void showOptionsMenu(View anchor) {
		PopupMenu popup = new PopupMenu(this, anchor);
		popup.getMenu().add(0, 1, 0, "Download Musical Scores Pack");
		popup.getMenu().add(0, 2, 1, "Export Hymns Pack (.acc)");
		popup.getMenu().add(0, 3, 2, "Reset Custom Hymns");
		popup.setOnMenuItemClickListener(item -> {
			if (item.getItemId() == 1) {
				showDownloadScoresPackDialog();
				return true;
			} else if (item.getItemId() == 2) {
				showExportCategoryDialog();
				return true;
			} else if (item.getItemId() == 3) {
				showResetCategoryDialog();
				return true;
			}
			return false;
		});
		popup.show();
	}

	private void showExportCategoryDialog() {
		String[] labels = new String[]{"New Hymns & Scores", "Songs of the Church", "Ŋwed Ikwọ (Efik)"};
		String[] keys = new String[]{HymnRepository.CATEGORY_NEW, HymnRepository.CATEGORY_SOC, HymnRepository.CATEGORY_EFIK};
		new AlertDialog.Builder(this)
				.setTitle("Export Which Collection?")
				.setItems(labels, (d, which) -> exportHymnsPackageAsync(keys[which]))
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void showResetCategoryDialog() {
		String[] labels = new String[]{"New Hymns & Imported Scores", "Songs of the Church (Custom)", "Ŋwed Ikwọ (Custom)", "Reset All Custom Hymns & Scores"};
		new AlertDialog.Builder(this)
				.setTitle("Reset to Factory Hymnal")
				.setItems(labels, (d, which) -> {
					String targetLabel = labels[which];
					new AlertDialog.Builder(this)
							.setTitle("Confirm Reset")
							.setMessage("Remove custom/imported items for " + targetLabel + "? Built-in hymns and saved bookmarks will not be affected.")
							.setPositiveButton("Reset", (dialog, w) -> {
								AccApplication.ioExecutor().execute(() -> {
									HymnRepository repo = HymnRepository.getInstance(getApplicationContext());
									if (which == 0) repo.resetCategory(HymnRepository.CATEGORY_NEW);
									else if (which == 1) repo.resetCategory(HymnRepository.CATEGORY_SOC);
									else if (which == 2) repo.resetCategory(HymnRepository.CATEGORY_EFIK);
									else repo.resetAllCategories();

									runOnUiThread(() -> {
										if (isFinishing() || isDestroyed()) return;
										loadHymnsAsync();
										Toast.makeText(this, "Reset complete", Toast.LENGTH_SHORT).show();
									});
								});
							})
							.setNegativeButton("Cancel", null)
							.show();
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void handleImportUri(Uri uri) {
		if (uri == null) return;

		String uriStr = uri.toString().toLowerCase(Locale.ROOT);
		if (uriStr.endsWith(".apk")) {
			new AlertDialog.Builder(this)
					.setTitle("Cannot Install APK")
					.setMessage("The selected file is an Android application package (.apk). The hymnal app only imports hymn packages (.acc, .hymn, or .json).")
					.setPositiveButton("OK", null)
					.show();
			return;
		}

		dismissProgressDialog();
		activeProgressDialog = new AlertDialog.Builder(this)
				.setTitle("Inspecting Hymn Package")
				.setMessage("Reading and validating hymn package...")
				.setCancelable(false)
				.show();

		AccApplication.ioExecutor().execute(() -> {
			HymnRepository.HymnPackagePreview preview =
					HymnRepository.getInstance(getApplicationContext()).inspectPackage(uri);
			runOnUiThread(() -> {
				if (isFinishing() || isDestroyed()) {
					HymnRepository.getInstance(getApplicationContext()).cleanupStaging(preview);
					return;
				}
				dismissProgressDialog();
				if (preview != null && preview.isValid) {
					showImportPreviewDialog(preview);
				} else {
					String msg = (preview != null && preview.errorMessage != null && !preview.errorMessage.isEmpty())
							? preview.errorMessage
							: "The selected file does not contain valid hymn data.";
					new AlertDialog.Builder(this)
							.setTitle("Import Not Supported")
							.setMessage(msg)
							.setPositiveButton("OK", null)
							.show();
				}
			});
		});
	}

	private void showImportPreviewDialog(HymnRepository.HymnPackagePreview preview) {
		DialogImportPreviewBinding previewBinding = DialogImportPreviewBinding.inflate(getLayoutInflater());
		AlertDialog dialog = new AlertDialog.Builder(this)
				.setView(previewBinding.getRoot())
				.setCancelable(true)
				.create();

		if (dialog.getWindow() != null) {
			dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
		}

		final boolean[] confirmed = new boolean[]{false};

		previewBinding.tvImportFileName.setText(preview.fileName);
		String sizeFormatted = preview.fileSize >= 1048576
				? String.format(Locale.getDefault(), "%.1f MB", preview.fileSize / 1048576.0)
				: String.format(Locale.getDefault(), "%d KB", Math.max(1, preview.fileSize / 1024));
		previewBinding.tvImportFileSize.setText(sizeFormatted);

		previewBinding.tvBadgeTotal.setText(preview.totalHymns + " Hymn" + (preview.totalHymns == 1 ? "" : "s"));

		if (preview.newHymnsCount > 0) {
			previewBinding.tvBadgeNew.setVisibility(View.VISIBLE);
			previewBinding.tvBadgeNew.setText(preview.newHymnsCount + " New");
		} else {
			previewBinding.tvBadgeNew.setVisibility(View.GONE);
		}

		if (preview.updatedHymnsCount > 0) {
			previewBinding.tvBadgeUpdated.setVisibility(View.VISIBLE);
			previewBinding.tvBadgeUpdated.setText(preview.updatedHymnsCount + " Update" + (preview.updatedHymnsCount == 1 ? "" : "s"));
		} else {
			previewBinding.tvBadgeUpdated.setVisibility(View.GONE);
		}

		if (preview.scoresCount > 0 || preview.hasBibleDatabase) {
			previewBinding.tvBadgeScores.setVisibility(View.VISIBLE);
			String label = "";
			if (preview.scoresCount > 0) {
				label = preview.scoresCount + " Score" + (preview.scoresCount == 1 ? "" : "s");
			}
			if (preview.hasBibleDatabase) {
				label = label.isEmpty() ? "5 Bibles" : (label + " • 5 Bibles");
			}
			previewBinding.tvBadgeScores.setText(label);
		} else {
			previewBinding.tvBadgeScores.setVisibility(View.GONE);
		}

		PreviewListAdapter adapter = new PreviewListAdapter(preview.items);
		previewBinding.lvImportPreview.setAdapter(adapter);

		previewBinding.btnImportCancel.setOnClickListener(v -> dialog.dismiss());
		previewBinding.btnImportConfirm.setOnClickListener(v -> {
			confirmed[0] = true;
			dialog.dismiss();
			commitImport(preview);
		});

		dialog.setOnDismissListener(d -> {
			if (!confirmed[0]) {
				AccApplication.ioExecutor().execute(() ->
						HymnRepository.getInstance(getApplicationContext()).cleanupStaging(preview));
			}
		});

		dialog.show();
	}

	private void commitImport(HymnRepository.HymnPackagePreview preview) {
		dismissProgressDialog();
		activeProgressDialog = new AlertDialog.Builder(this)
				.setTitle("Applying Hymn Update")
				.setMessage("Saving hymns and sheet music scores...")
				.setCancelable(false)
				.show();

		AccApplication.ioExecutor().execute(() -> {
			HymnRepository.ImportResult result =
					HymnRepository.getInstance(getApplicationContext()).commitImport(preview);
			runOnUiThread(() -> {
				if (isFinishing() || isDestroyed()) return;
				dismissProgressDialog();
				if (result.success) {
					new AlertDialog.Builder(this)
							.setTitle("Import Complete")
							.setMessage(result.message)
							.setPositiveButton("OK", (dialog, which) -> loadHymnsAsync())
							.show();
				} else {
					new AlertDialog.Builder(this)
							.setTitle("Import Failed")
							.setMessage(result.message)
							.setPositiveButton("OK", null)
							.show();
				}
			});
		});
	}

	private class PreviewListAdapter extends BaseAdapter {
		private final List<HymnRepository.HymnPreviewItem> mItems;

		PreviewListAdapter(List<HymnRepository.HymnPreviewItem> items) {
			this.mItems = items != null ? items : Collections.emptyList();
		}

		@Override
		public int getCount() {
			return mItems.size();
		}

		@Override
		public HymnRepository.HymnPreviewItem getItem(int position) {
			return mItems.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ItemImportPreviewHymnBinding itemBinding;
			if (convertView == null) {
				itemBinding = ItemImportPreviewHymnBinding.inflate(getLayoutInflater(), parent, false);
				convertView = itemBinding.getRoot();
				convertView.setTag(itemBinding);
			} else {
				itemBinding = (ItemImportPreviewHymnBinding) convertView.getTag();
			}

			HymnRepository.HymnPreviewItem item = getItem(position);
			itemBinding.tvPreviewTitle.setText(item.title);

			if (item.isUpdate) {
				itemBinding.tvPreviewBadge.setText("UPDATE");
				itemBinding.tvPreviewBadge.setBackgroundResource(R.drawable.bg_badge_gold);
				itemBinding.tvPreviewBadge.setTextColor(ContextCompat.getColor(NewHymnsActivity.this, R.color.colorAccent));
			} else {
				itemBinding.tvPreviewBadge.setText("NEW");
				itemBinding.tvPreviewBadge.setBackgroundResource(R.drawable.bg_badge_emerald);
				itemBinding.tvPreviewBadge.setTextColor(ContextCompat.getColor(NewHymnsActivity.this, R.color.colorSpineEmerald));
			}

			itemBinding.ivPreviewScore.setVisibility(item.hasScore ? View.VISIBLE : View.GONE);

			if (item.lyricsSnippet != null && !item.lyricsSnippet.isEmpty()) {
				itemBinding.tvPreviewSnippet.setVisibility(View.VISIBLE);
				itemBinding.tvPreviewSnippet.setText(item.lyricsSnippet);
			} else {
				itemBinding.tvPreviewSnippet.setVisibility(View.GONE);
			}

			return convertView;
		}
	}

	private void showManualHymnCreateDialog() {
		DialogCreateHymnBinding dialogBinding = DialogCreateHymnBinding.inflate(getLayoutInflater());

		String[] categories = new String[]{"New Hymns", "Songs of the Church", "Ŋwed Ikwọ (Efik)"};
		ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories);
		dialogBinding.spinnerCategory.setAdapter(catAdapter);

		AlertDialog dialog = new AlertDialog.Builder(this)
				.setView(dialogBinding.getRoot())
				.setCancelable(true)
				.create();
		if (dialog.getWindow() != null) {
			dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
		}

		dialogBinding.btnCreateCancel.setOnClickListener(v -> dialog.dismiss());

		dialogBinding.btnCreateSave.setOnClickListener(v -> {
			String title = dialogBinding.etDialogTitle.getText().toString().trim();
			String lyrics = dialogBinding.etDialogLyrics.getText().toString().trim();

			if (title.isEmpty()) {
				dialogBinding.etDialogTitle.setError("Please enter a hymn title");
				dialogBinding.etDialogTitle.requestFocus();
				return;
			}
			if (title.length() < 2) {
				dialogBinding.etDialogTitle.setError("Title is too short");
				dialogBinding.etDialogTitle.requestFocus();
				return;
			}
			if (lyrics.isEmpty()) {
				dialogBinding.etDialogLyrics.setError("Please enter the hymn lyrics");
				dialogBinding.etDialogLyrics.requestFocus();
				return;
			}

			int selectedPos = dialogBinding.spinnerCategory.getSelectedItemPosition();
			final String catKey;
			if (selectedPos == 1) catKey = HymnRepository.CATEGORY_SOC;
			else if (selectedPos == 2) catKey = HymnRepository.CATEGORY_EFIK;
			else catKey = HymnRepository.CATEGORY_NEW;

			final String catLabel = categories[Math.max(0, selectedPos)];
			AccApplication.ioExecutor().execute(() -> {
				boolean exists = hymnTitleExists(catKey, title);
				runOnUiThread(() -> {
					if (isFinishing() || isDestroyed()) return;
					if (exists) {
						new AlertDialog.Builder(this)
								.setTitle("Replace Existing Hymn?")
								.setMessage("A hymn titled \"" + title + "\" already exists in " + catLabel
										+ ". Saving will replace its lyrics on this device while preserving any attached musical score.")
								.setPositiveButton("Replace", (d, w) -> persistCustomHymnAsync(catKey, title, lyrics, dialog))
								.setNegativeButton("Cancel", null)
								.show();
					} else {
						persistCustomHymnAsync(catKey, title, lyrics, dialog);
					}
				});
			});
		});

		dialog.show();
	}

	private boolean hymnTitleExists(String category, String title) {
		String needle = title.trim().toLowerCase(Locale.ROOT);
		for (HashMap<String, Object> item : HymnRepository.getInstance(getApplicationContext()).getHymns(category)) {
			Object t = item.get("Title");
			if (t == null) t = item.get("title");
			if (t != null && t.toString().trim().toLowerCase(Locale.ROOT).equals(needle)) return true;
		}
		return false;
	}

	private void persistCustomHymnAsync(String category, String title, String lyrics, AlertDialog dialog) {
		AccApplication.ioExecutor().execute(() -> {
			boolean saved = HymnRepository.getInstance(getApplicationContext()).addOrUpdateHymn(category, title, lyrics, "");
			runOnUiThread(() -> {
				if (isFinishing() || isDestroyed()) return;
				if (saved) {
					dialog.dismiss();
					Toast.makeText(this, "Hymn saved successfully!", Toast.LENGTH_SHORT).show();
					loadHymnsAsync();
				} else {
					Toast.makeText(this, "Could not save hymn. Please try again.", Toast.LENGTH_SHORT).show();
				}
			});
		});
	}

	private void exportHymnsPackageAsync(String category) {
		dismissProgressDialog();
		activeProgressDialog = new AlertDialog.Builder(this)
				.setTitle("Exporting Hymn Pack")
				.setMessage("Creating shareable .acc package...")
				.setCancelable(false)
				.show();

		AccApplication.ioExecutor().execute(() -> {
			try {
				File exportFile = HymnRepository.getInstance(getApplicationContext()).exportHymnsPackage(category);
				runOnUiThread(() -> {
					if (isFinishing() || isDestroyed()) return;
					dismissProgressDialog();
					try {
						Uri contentUri = FileProvider.getUriForFile(
								this,
								getPackageName() + ".fileprovider",
								exportFile
						);

						Intent shareIntent = new Intent(Intent.ACTION_SEND);
						shareIntent.setType("application/octet-stream");
						shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
						shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Church Hymnal Update Pack");
						shareIntent.putExtra(Intent.EXTRA_TEXT, "Here is the latest Church of Christ hymn update package (.acc). Open with the Church Hymnal app to import.");
						shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
						startActivity(Intent.createChooser(shareIntent, "Share Hymn Pack via WhatsApp or Files"));
					} catch (Exception e) {
						Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
					}
				});
			} catch (Exception e) {
				runOnUiThread(() -> {
					if (isFinishing() || isDestroyed()) return;
					dismissProgressDialog();
					Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
				});
			}
		});
	}

	public class Listview1Adapter extends BaseAdapter {

		private final ArrayList<HashMap<String, Object>> _data;

		public Listview1Adapter(ArrayList<HashMap<String, Object>> arr) {
			_data = arr;
		}

		@Override
		public int getCount() {
			return _data != null ? _data.size() : 0;
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
		public View getView(final int position, View convertView, ViewGroup parent) {
			EfikHymnRowBinding itemBinding;
			if (convertView == null) {
				itemBinding = EfikHymnRowBinding.inflate(getLayoutInflater(), parent, false);
				convertView = itemBinding.getRoot();
				convertView.setTag(itemBinding);
			} else {
				itemBinding = (EfikHymnRowBinding) convertView.getTag();
			}

			HashMap<String, Object> rowItem = _data.get(position);
			EfikHymnsActivity.bindHymnRowTitle(itemBinding, rowItem);

			Object pdfObj = rowItem.get("PDF_File");
			String pdfName = pdfObj != null ? pdfObj.toString().trim() : "";
			if (!pdfName.isEmpty()) {
				boolean installed = HymnRepository.getInstance(NewHymnsActivity.this).hasPdfAvailable(pdfName);
				itemBinding.tvSubtitle.setText(installed ? "Includes Musical Score" : "Score available via Pack");
				itemBinding.tvSubtitle.setVisibility(View.VISIBLE);
			} else {
				itemBinding.tvSubtitle.setVisibility(View.GONE);
			}
			return convertView;
		}
	}
}
