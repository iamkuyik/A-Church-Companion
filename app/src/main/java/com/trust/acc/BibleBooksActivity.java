package com.trust.acc;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.trust.acc.databinding.BibleBooksBinding;
import com.trust.acc.databinding.EfikHymnRowBinding;

import java.util.ArrayList;
import java.util.HashMap;

public class BibleBooksActivity extends AppCompatActivity {

	private static final String STATE_TESTAMENT = "state_testament";

	private BibleBooksBinding binding;

	private final ArrayList<HashMap<String, Object>> books_list = new ArrayList<>();
	private ArrayList<HashMap<String, Object>> search_backup = new ArrayList<>();
	private String currentTestament = "ot";
	private Listview1Adapter adapter;
	private GestureDetector gestureDetector;
	private int otScrollIndex = 0;
	private int otScrollTop = 0;
	private int ntScrollIndex = 0;
	private int ntScrollTop = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			currentTestament = savedInstanceState.getString(STATE_TESTAMENT, "ot");
		}
		binding = BibleBooksBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		SystemBarUtil.applyEdgeToEdge(this, binding.getRoot());
		initialize();
		initializeLogic();
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(STATE_TESTAMENT, currentTestament);
	}

	@Override
	protected void onResume() {
		super.onResume();
		SystemBarUtil.updateSystemBarAppearance(this);
	}

	private void initialize() {
		binding.btnBack.setOnClickListener(_v -> finish());

		binding.tabOt.setOnClickListener(v -> switchTestament("ot"));

		binding.tabNt.setOnClickListener(v -> switchTestament("nt"));

		View.OnClickListener clearSearchListener = v -> {
			binding.etSearch.setText("");
			binding.etSearch.clearFocus();
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm != null) {
				imm.hideSoftInputFromWindow(binding.etSearch.getWindowToken(), 0);
			}
		};
		binding.btnClearSearch.setOnClickListener(clearSearchListener);
		binding.btnClearEmpty.setOnClickListener(clearSearchListener);

		binding.etSearch.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				filterBooks();
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void afterTextChanged(Editable s) {}
		});

		binding.listview1.setOnItemClickListener((parent, view, position, id) -> {
			if (position < 0 || position >= books_list.size()) return;
			HashMap<String, Object> book = books_list.get(position);
			Intent intent = new Intent(this, BibleChaptersActivity.class);
			intent.putExtra("book_id", book.get("id") != null ? book.get("id").toString() : "10");
			intent.putExtra("book_name", book.get("name_en") != null ? book.get("name_en").toString() : "");
			if (book.get("name_efi") != null) {
				intent.putExtra("name_efi", book.get("name_efi").toString());
			}
			intent.putExtra("chapter_count", book.get("chapters") != null ? book.get("chapters").toString() : "1");
			startActivity(intent);
		});

		// Density-scaled horizontal swipe detector for Old / New Testament switching
		final float density = getResources().getDisplayMetrics().density;
		final float horizontalThresholdPx = 56f * density;
		final float verticalLockPx = 48f * density;
		final float velocityThresholdPx = 150f * density;

		gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
				if (e1 == null || e2 == null) return false;
				if (isTouchInsideView(binding.etSearch, e1)) return false;
				String target = resolveSwipeTestament(
						currentTestament,
						hasActiveSearchQuery(),
						e2.getX() - e1.getX(),
						e2.getY() - e1.getY(),
						velocityX,
						horizontalThresholdPx,
						verticalLockPx,
						velocityThresholdPx
				);
				if (target != null) {
					switchTestament(target);
					return true;
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

	private boolean hasActiveSearchQuery() {
		return binding.etSearch.getText() != null
				&& !binding.etSearch.getText().toString().trim().isEmpty();
	}

	private boolean isTouchInsideView(View view, MotionEvent ev) {
		if (view == null || view.getVisibility() != View.VISIBLE) return false;
		int[] loc = new int[2];
		view.getLocationOnScreen(loc);
		float x = ev.getRawX();
		float y = ev.getRawY();
		return x >= loc[0] && x <= loc[0] + view.getWidth()
				&& y >= loc[1] && y <= loc[1] + view.getHeight();
	}

	private void switchTestament(String target) {
		if (target == null || target.equals(currentTestament)) return;
		boolean searching = hasActiveSearchQuery();
		if (!searching) {
			saveScrollPosition(currentTestament);
		}
		currentTestament = target;
		updateTabs();
		filterBooks();
		if (!searching) {
			restoreScrollPosition(currentTestament);
		}
	}

	private void saveScrollPosition(String testament) {
		int index = binding.listview1.getFirstVisiblePosition();
		View firstChild = binding.listview1.getChildAt(0);
		int top = firstChild != null ? firstChild.getTop() - binding.listview1.getPaddingTop() : 0;
		if ("ot".equals(testament)) {
			otScrollIndex = index;
			otScrollTop = top;
		} else {
			ntScrollIndex = index;
			ntScrollTop = top;
		}
	}

	private void restoreScrollPosition(String testament) {
		if ("ot".equals(testament)) {
			binding.listview1.setSelectionFromTop(otScrollIndex, otScrollTop);
		} else {
			binding.listview1.setSelectionFromTop(ntScrollIndex, ntScrollTop);
		}
	}

	static String resolveSwipeTestament(
			String currentTestament,
			boolean hasSearchQuery,
			float diffX,
			float diffY,
			float velocityX,
			float horizontalThresholdPx,
			float verticalLockPx,
			float velocityThresholdPx) {
		if (hasSearchQuery) return null;
		if (Math.abs(diffX) > horizontalThresholdPx
				&& Math.abs(diffY) < verticalLockPx
				&& Math.abs(diffX) > Math.abs(diffY) * 1.3f
				&& Math.abs(velocityX) > velocityThresholdPx) {
			if (diffX < 0 && "ot".equals(currentTestament)) {
				return "nt";
			} else if (diffX > 0 && "nt".equals(currentTestament)) {
				return "ot";
			}
		}
		return null;
	}

	private void updateTabs() {
		if ("ot".equals(currentTestament)) {
			binding.tabOt.setBackgroundResource(R.drawable.bg_circle_accent);
			binding.tabOt.setTextColor(ContextCompat.getColor(this, R.color.colorAntiqueGold));
			binding.tabNt.setBackground(null);
			binding.tabNt.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary));
		} else {
			binding.tabNt.setBackgroundResource(R.drawable.bg_circle_accent);
			binding.tabNt.setTextColor(ContextCompat.getColor(this, R.color.colorAntiqueGold));
			binding.tabOt.setBackground(null);
			binding.tabOt.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary));
		}
	}

	private void filterBooks() {
		books_list.clear();
		String rawQuery = binding.etSearch.getText() != null ? binding.etSearch.getText().toString().trim() : "";
		binding.btnClearSearch.setVisibility(rawQuery.isEmpty() ? View.GONE : View.VISIBLE);
		String query = EfikHymnsActivity.normalizeEfik(rawQuery);
		String queryWithN = query.replace("ng", "n");

		for (int idx = 0; idx < search_backup.size(); idx++) {
			HashMap<String, Object> item = search_backup.get(idx);
			if (rawQuery.isEmpty()) {
				int id = item.get("_id_int") instanceof Integer ? (Integer) item.get("_id_int") : 0;
				boolean matchesTestament = "ot".equals(currentTestament) ? (id < 470) : (id >= 470);
				if (matchesTestament) {
					books_list.add(item);
				}
			} else {
				// Search across both Old and New Testaments whenever a search query is typed
				String normEn = item.get("_norm_en") != null ? item.get("_norm_en").toString() : "";
				String normEnN = item.get("_norm_en_n") != null ? item.get("_norm_en_n").toString() : "";
				String normEfi = item.get("_norm_efi") != null ? item.get("_norm_efi").toString() : "";
				String normEfiN = item.get("_norm_efi_n") != null ? item.get("_norm_efi_n").toString() : "";

				if (normEn.contains(query) || normEnN.contains(queryWithN)
						|| normEfi.contains(query) || normEfiN.contains(queryWithN)) {
					books_list.add(item);
				}
			}
		}

		if (adapter != null) {
			adapter.notifyDataSetChanged();
		}

		boolean isEmpty = books_list.isEmpty() && !search_backup.isEmpty();
		binding.layoutEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
		binding.listview1.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
	}

	private void initializeLogic() {
		adapter = new Listview1Adapter(books_list);
		binding.listview1.setAdapter(adapter);
		binding.listview1.setFastScrollEnabled(true);
		updateTabs();

		AccApplication.ioExecutor().execute(() -> {
			try {
				ArrayList<HashMap<String, Object>> loaded = AssetUtil.getCachedBibleBooks(getApplicationContext());
				runOnUiThread(() -> {
					if (isFinishing() || isDestroyed()) return;
					search_backup = loaded;
					filterBooks();
				});
			} catch (Exception e) {
				runOnUiThread(() -> {
					if (isFinishing() || isDestroyed()) return;
					Toast.makeText(getApplicationContext(), "Index Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
			EfikHymnRowBinding rowBinding;
			if (convertView == null) {
				rowBinding = EfikHymnRowBinding.inflate(getLayoutInflater(), container, false);
				convertView = rowBinding.getRoot();
				convertView.setTag(rowBinding);
			} else {
				rowBinding = (EfikHymnRowBinding) convertView.getTag();
			}

			HashMap<String, Object> item = _data.get(position);
			rowBinding.tvNum.setVisibility(View.GONE);
			String nameEn = item.get("name_en") != null ? item.get("name_en").toString() : "";
			String nameEfi = item.get("name_efi") != null ? item.get("name_efi").toString() : "";
			int chapCount = item.get("_chap_count") instanceof Integer ? (Integer) item.get("_chap_count") : 0;

			rowBinding.tvTitle.setText(nameEn);
			rowBinding.tvSubtitle.setText(nameEfi + (chapCount > 0 ? (" • " + chapCount + " Chapters") : ""));
			rowBinding.tvSubtitle.setVisibility(View.VISIBLE);
			return convertView;
		}
	}
}
