package com.trust.acc;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;

import androidx.appcompat.app.AppCompatActivity;

import com.trust.acc.databinding.EfikHymnRowBinding;
import com.trust.acc.databinding.SocHymnsBinding;

import java.util.ArrayList;
import java.util.HashMap;

public class SocHymnsActivity extends AppCompatActivity {

	private SocHymnsBinding binding;

	private final ArrayList<HashMap<String, Object>> listmap = new ArrayList<>();
	private ArrayList<HashMap<String, Object>> search_backup = new ArrayList<>();
	private Listview1Adapter adapter;

	private final Handler searchHandler = new Handler(Looper.getMainLooper());
	private Runnable pendingSearch;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		binding = SocHymnsBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		SystemBarUtil.applyEdgeToEdge(this, binding.getRoot());
		initialize();
		initializeLogic();
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
		super.onDestroy();
	}

	private void initialize() {
		binding.btnBack.setOnClickListener(_v -> finish());

		binding.btnKeypadMode.setOnClickListener(v -> {
			boolean isNumberMode = "ABC".equals(binding.btnKeypadMode.getText().toString());
			if (isNumberMode) {
				binding.btnKeypadMode.setText("123");
				binding.etSearch.setInputType(InputType.TYPE_CLASS_TEXT);
			} else {
				binding.btnKeypadMode.setText("ABC");
				binding.etSearch.setInputType(InputType.TYPE_CLASS_NUMBER);
			}
			binding.etSearch.requestFocus();
			binding.etSearch.setSelection(binding.etSearch.getText().length());
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm != null) {
				imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT);
			}
		});

		binding.etSearch.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (pendingSearch != null) {
					searchHandler.removeCallbacks(pendingSearch);
				}
				final String query = s != null ? s.toString() : "";
				pendingSearch = () -> applyFilter(query);
				searchHandler.postDelayed(pendingSearch, 110);
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void afterTextChanged(Editable s) {}
		});

		binding.listview1.setOnItemClickListener((parent, view, position, id) -> {
			if (position < 0 || position >= listmap.size()) return;
			HashMap<String, Object> selected = listmap.get(position);
			Intent intent = new Intent(this, LyricsActivity.class);
			intent.putExtra("Title", selected.get("Title") != null ? selected.get("Title").toString() : "");
			intent.putExtra("Lyrics", selected.get("Lyrics") != null ? selected.get("Lyrics").toString() : "");
			intent.putExtra("source_type", "soc");
			startActivity(intent);
		});
	}

	private void initializeLogic() {
		adapter = new Listview1Adapter(listmap);
		binding.listview1.setAdapter(adapter);
		binding.listview1.setFastScrollEnabled(true);
		loadHymnsAsync();
	}

	private void loadHymnsAsync() {
		AccApplication.ioExecutor().execute(() -> {
			ArrayList<HashMap<String, Object>> loaded =
					HymnRepository.getInstance(getApplicationContext()).getHymns(HymnRepository.CATEGORY_SOC);
			runOnUiThread(() -> {
				if (isFinishing() || isDestroyed()) return;
				search_backup = loaded;
				String currentQuery = binding.etSearch.getText() != null ? binding.etSearch.getText().toString() : "";
				applyFilter(currentQuery);
			});
		});
	}

	private void applyFilter(String rawQuery) {
		listmap.clear();
		listmap.addAll(HymnRepository.searchHymnsRanked(search_backup, rawQuery));
		if (adapter != null) {
			adapter.notifyDataSetChanged();
		}
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
			EfikHymnsActivity.bindHymnRowTitle(rowBinding, _data.get(position));
			return convertView;
		}
	}
}
