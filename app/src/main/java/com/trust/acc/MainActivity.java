package com.trust.acc;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.trust.acc.databinding.MainBinding;

public class MainActivity extends AppCompatActivity {

	private MainBinding binding;
	private SharedPreferences theme_prefs;

	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		theme_prefs = getSharedPreferences("theme_prefs", Activity.MODE_PRIVATE);
		super.onCreate(_savedInstanceState);
		binding = MainBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		SystemBarUtil.applyEdgeToEdge(this, binding.getRoot());
		HymnRepository.getInstance(this).preloadAllAsync();
		AssetUtil.preloadAsync(this);
		initialize();
	}

	private boolean isCurrentlyNightMode() {
		String currentMode = theme_prefs.getString("mode", "system");
		if ("dark".equals(currentMode)) return true;
		if ("light".equals(currentMode)) return false;
		int flags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
		return flags == Configuration.UI_MODE_NIGHT_YES;
	}

	private void initialize() {
		binding.btnSaved.setOnClickListener(_view ->
				startActivity(new Intent(this, SavedActivity.class)));

		updateThemeButtonLabel();

		binding.btnTheme.setOnClickListener(_view -> {
			if (isCurrentlyNightMode()) {
				theme_prefs.edit().putString("mode", "light").apply();
				AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
			} else {
				theme_prefs.edit().putString("mode", "dark").apply();
				AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
			}
			updateThemeButtonLabel();
		});

		binding.btnTheme.setOnLongClickListener(_view -> {
			theme_prefs.edit().putString("mode", "system").apply();
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
			updateThemeButtonLabel();
			Toast.makeText(getApplicationContext(), "Theme: System Default", Toast.LENGTH_SHORT).show();
			return true;
		});

		binding.button1.setOnClickListener(_view ->
				startActivity(new Intent(this, SocHymnsActivity.class)));

		binding.button2.setOnClickListener(_view ->
				startActivity(new Intent(this, EfikHymnsActivity.class)));

		binding.button3.setOnClickListener(_view ->
				startActivity(new Intent(this, NewHymnsActivity.class)));

		binding.button4.setOnClickListener(_view ->
				startActivity(new Intent(this, BibleBooksActivity.class)));
	}

	private void updateThemeButtonLabel() {
		binding.btnTheme.setText(isCurrentlyNightMode() ? "☀ LIGHT" : "☾ DARK");
	}

	@Override
	protected void onResume() {
		super.onResume();
		SystemBarUtil.updateSystemBarAppearance(this);
		updateThemeButtonLabel();
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
}
