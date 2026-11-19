package com.trust.acc;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.trust.acc.databinding.EfikHymnRowBinding;
import com.trust.acc.databinding.SavedActivityBinding;

import java.util.ArrayList;
import java.util.regex.Matcher;

public class SavedActivity extends AppCompatActivity {

    private SavedActivityBinding binding;
    private final ArrayList<SavedManager.Item> savedList = new ArrayList<>();
    private SavedAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = SavedActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBarUtil.applyEdgeToEdge(this, binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        adapter = new SavedAdapter();
        binding.listview1.setAdapter(adapter);

        binding.listview1.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= savedList.size()) return;
            SavedManager.Item item = savedList.get(position);
            if (item == null) return;
            if ("bible".equals(item.type) || "bible_verse".equals(item.type)) {
                Intent i = new Intent(this, BibleReaderActivity.class);
                i.putExtra("book_id", item.extra1);
                i.putExtra("book_name", item.extra2);
                i.putExtra("chapter_number", item.extra3);
                i.putExtra("name_efi", item.extra4);
                if ("bible_verse".equals(item.type) && item.extra5 != null && !item.extra5.isEmpty()) {
                    i.putExtra("target_verse", item.extra5);
                }
                startActivity(i);
            } else if ("new".equals(item.type)) {
                Intent i = new Intent(this, NewLyricsActivity.class);
                i.putExtra("Title", item.title);
                i.putExtra("Lyrics", item.extra1);
                i.putExtra("pdf", item.extra2);
                startActivity(i);
            } else {
                Intent i = new Intent(this, LyricsActivity.class);
                i.putExtra("Title", item.title);
                i.putExtra("Lyrics", item.extra1);
                i.putExtra("source_type", item.type);
                startActivity(i);
            }
        });

        binding.listview1.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= savedList.size()) return false;
            final SavedManager.Item item = savedList.get(position);
            if (item == null) return false;
            new AlertDialog.Builder(SavedActivity.this)
                .setTitle(item.title)
                .setMessage("Remove this bookmark?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    SavedManager.remove(SavedActivity.this, item.id);
                    loadSavedItems();
                    Toast.makeText(SavedActivity.this, "Bookmark removed", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        SystemBarUtil.updateSystemBarAppearance(this);
        loadSavedItems();
    }

    private void loadSavedItems() {
        savedList.clear();
        savedList.addAll(SavedManager.getSavedList(this));
        adapter.notifyDataSetChanged();
        binding.headerTitle.setText("All Bookmarks");
        int count = savedList.size();
        binding.headerSubtitle.setText(count == 0
                ? "Hymns & Scripture Passages"
                : count + " saved • Tap to open • Hold to delete");
        if (savedList.isEmpty()) {
            binding.layoutEmpty.setVisibility(View.VISIBLE);
            binding.listview1.setVisibility(View.GONE);
        } else {
            binding.layoutEmpty.setVisibility(View.GONE);
            binding.listview1.setVisibility(View.VISIBLE);
        }
    }

    private class SavedAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return savedList.size();
        }

        @Override
        public SavedManager.Item getItem(int position) {
            return savedList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            EfikHymnRowBinding rowBinding;
            if (convertView == null) {
                rowBinding = EfikHymnRowBinding.inflate(getLayoutInflater(), parent, false);
                convertView = rowBinding.getRoot();
                convertView.setTag(rowBinding);
            } else {
                rowBinding = (EfikHymnRowBinding) convertView.getTag();
            }

            SavedManager.Item item = savedList.get(position);
            if (item == null) return convertView;

            rowBinding.tvTitle.setText(item.title != null ? item.title : "");
            if (item.subtitle != null && !item.subtitle.isEmpty()) {
                rowBinding.tvSubtitle.setText(item.subtitle);
                rowBinding.tvSubtitle.setVisibility(View.VISIBLE);
            } else {
                rowBinding.tvSubtitle.setVisibility(View.GONE);
            }

            if ("bible_verse".equals(item.type)) {
                rowBinding.tvNum.setText("v." + (item.extra5 != null ? item.extra5 : ""));
                rowBinding.tvNum.setVisibility(View.VISIBLE);
            } else if ("bible".equals(item.type)) {
                rowBinding.tvNum.setText("CH " + (item.extra3 != null ? item.extra3 : ""));
                rowBinding.tvNum.setVisibility(View.VISIBLE);
            } else {
                String hymnNum = "";
                if (item.title != null) {
                    Matcher m = EfikHymnsActivity.HYMN_NUM_PATTERN.matcher(item.title.trim());
                    if (m.find()) {
                        hymnNum = m.group(1);
                    }
                }
                rowBinding.tvNum.setText(!hymnNum.isEmpty() ? hymnNum : "♪");
                rowBinding.tvNum.setVisibility(View.VISIBLE);
            }

            return convertView;
        }
    }
}
