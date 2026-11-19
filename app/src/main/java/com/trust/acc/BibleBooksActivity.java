package com.trust.acc;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
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

		binding.tabOt.setOnClickListener(v -> {
			currentTestament = "ot";
			updateTabs();
			filterBooks();
		});

		binding.tabNt.setOnClickListener(v -> {
			currentTestament = "nt";
			updateTabs();
			filterBooks();
		});

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
