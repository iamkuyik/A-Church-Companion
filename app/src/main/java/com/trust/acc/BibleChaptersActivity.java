package com.trust.acc;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.trust.acc.databinding.BibleChaptersBinding;
import com.trust.acc.databinding.ChapterRowBinding;

import java.util.ArrayList;
import java.util.HashMap;

public class BibleChaptersActivity extends AppCompatActivity {

	private BibleChaptersBinding binding;
	private final ArrayList<HashMap<String, Object>> listmap = new ArrayList<>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		binding = BibleChaptersBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		SystemBarUtil.applyEdgeToEdge(this, binding.getRoot());
		initialize();
		initializeLogic();
	}

	@Override
	protected void onResume() {
		super.onResume();
		SystemBarUtil.updateSystemBarAppearance(this);
	}

	private void initialize() {
		binding.btnBack.setOnClickListener(v -> finish());

		binding.gridview1.setOnItemClickListener((parent, view, position, id) -> {
			if (position < 0 || position >= listmap.size()) return;
			Object chObj = listmap.get(position).get("chapter");
			if (chObj == null) return;

			Intent intent = new Intent(this, BibleReaderActivity.class);
			intent.putExtra("book_id", getIntent().getStringExtra("book_id"));
			intent.putExtra("book_name", getIntent().getStringExtra("book_name"));
			intent.putExtra("name_efi", getIntent().getStringExtra("name_efi"));
			intent.putExtra("chapter_number", chObj.toString());
			intent.putExtra("chapter_count", getIntent().getStringExtra("chapter_count"));
			startActivity(intent);
		});
	}

	private void initializeLogic() {
		String bName = getIntent().getStringExtra("book_name");
		String efiName = getIntent().getStringExtra("name_efi");
		binding.headerTitle.setText(bName != null ? bName : "Book");
		if (efiName != null && !efiName.isEmpty()) {
			binding.headerSubtitle.setText(efiName + " • Select Chapter • Ibuot");
		} else {
			binding.headerSubtitle.setText("Select Chapter • Ibuot");
		}
		if (bName != null) {
			setTitle(bName);
		}
		try {
			String countStr = getIntent().getStringExtra("chapter_count");
			int chapters = countStr != null ? (int) Double.parseDouble(countStr) : 1;
			for (int ch = 1; ch <= chapters; ch++) {
				HashMap<String, Object> map = new HashMap<>(2);
				map.put("chapter", String.valueOf(ch));
				listmap.add(map);
			}
		} catch (Exception e) {
			Toast.makeText(getApplicationContext(), "Error generating chapters", Toast.LENGTH_SHORT).show();
		}

		binding.gridview1.setAdapter(new Gridview1Adapter(listmap));
	}

	public class Gridview1Adapter extends BaseAdapter {

		private final ArrayList<HashMap<String, Object>> _data;

		public Gridview1Adapter(ArrayList<HashMap<String, Object>> arr) {
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
			ChapterRowBinding rowBinding;
			if (convertView == null) {
				rowBinding = ChapterRowBinding.inflate(getLayoutInflater(), container, false);
				convertView = rowBinding.getRoot();
				convertView.setTag(rowBinding);
			} else {
				rowBinding = (ChapterRowBinding) convertView.getTag();
			}
			Object ch = _data.get(position).get("chapter");
			rowBinding.tvChapter.setText(ch != null ? ch.toString() : "");
			return convertView;
		}
	}
}
