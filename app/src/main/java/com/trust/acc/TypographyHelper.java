package com.trust.acc;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.widget.SeekBar;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.trust.acc.databinding.DialogTypographySettingsBinding;

/**
 * Shared helper for reader typography (Font Family, Font Size) and instant Theme switching
 * inside the "Aa" popover across BibleReaderActivity, LyricsActivity, and NewLyricsActivity.
 */
public final class TypographyHelper {

    public static final String FONT_SYSTEM = "system";
    public static final String FONT_EDITORIAL = "editorial";
    public static final String FONT_SERIF = "serif";
    public static final String FONT_SANS = "sans";

    private static final String SETTINGS_PREFS = "settings";
    private static final String THEME_PREFS = "theme_prefs";
    private static final String KEY_FONT_FAMILY = "font_family";
    private static final String KEY_FONT_SIZE = "font_size";

    private TypographyHelper() {}

    public interface OnTypographyChangeListener {
        void onFontSizeChanged(int newSizeSp);
        void onFontFamilyChanged(Typeface newTypeface, String familyKey);
    }

    public static String getSavedFontFamily(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_FONT_FAMILY, FONT_EDITORIAL);
    }

    public static Typeface getReadingTypeface(Context context) {
        String family = getSavedFontFamily(context);
        if (FONT_SYSTEM.equalsIgnoreCase(family)) {
            return Typeface.DEFAULT;
        } else if (FONT_SANS.equalsIgnoreCase(family)) {
            return Typeface.SANS_SERIF;
        } else if (FONT_SERIF.equalsIgnoreCase(family)) {
            return Typeface.SERIF;
        } else {
            Typeface editorial = ResourcesCompat.getFont(context, R.font.crimson_pro);
            return editorial != null ? editorial : Typeface.SERIF;
        }
    }

    public static void showTypographyDialog(
            final Activity activity,
            final int currentSizeSp,
            final OnTypographyChangeListener listener) {

        final SharedPreferences settingsSp = activity.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        final SharedPreferences themeSp = activity.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE);

        final DialogTypographySettingsBinding dialogBinding =
                DialogTypographySettingsBinding.inflate(activity.getLayoutInflater());

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogBinding.getRoot())
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        final int goldColor = ContextCompat.getColor(activity, R.color.colorAntiqueGold);
        final int secondaryColor = ContextCompat.getColor(activity, R.color.colorTextSecondary);

        // 1. Initialize Theme Tabs
        final Runnable refreshThemeTabs = () -> {
            String mode = themeSp.getString("mode", "system");
            dialogBinding.tabThemeLight.setBackground(null);
            dialogBinding.tabThemeDark.setBackground(null);
            dialogBinding.tabThemeSystem.setBackground(null);
            dialogBinding.tabThemeLight.setTextColor(secondaryColor);
            dialogBinding.tabThemeDark.setTextColor(secondaryColor);
            dialogBinding.tabThemeSystem.setTextColor(secondaryColor);

            if ("light".equalsIgnoreCase(mode)) {
                dialogBinding.tabThemeLight.setBackgroundResource(R.drawable.bg_circle_accent);
                dialogBinding.tabThemeLight.setTextColor(goldColor);
            } else if ("dark".equalsIgnoreCase(mode)) {
                dialogBinding.tabThemeDark.setBackgroundResource(R.drawable.bg_circle_accent);
                dialogBinding.tabThemeDark.setTextColor(goldColor);
            } else {
                dialogBinding.tabThemeSystem.setBackgroundResource(R.drawable.bg_circle_accent);
                dialogBinding.tabThemeSystem.setTextColor(goldColor);
            }
        };
        refreshThemeTabs.run();

        dialogBinding.tabThemeLight.setOnClickListener(v -> {
            themeSp.edit().putString("mode", "light").apply();
            refreshThemeTabs.run();
            dialog.dismiss();
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        });

        dialogBinding.tabThemeDark.setOnClickListener(v -> {
            themeSp.edit().putString("mode", "dark").apply();
            refreshThemeTabs.run();
            dialog.dismiss();
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        });

        dialogBinding.tabThemeSystem.setOnClickListener(v -> {
            themeSp.edit().putString("mode", "system").apply();
            refreshThemeTabs.run();
            dialog.dismiss();
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        });

        // 2. Initialize Font Family Tabs
        final Runnable refreshFontTabs = () -> {
            String family = getSavedFontFamily(activity);
            dialogBinding.tabFontSystem.setBackground(null);
            dialogBinding.tabFontEditorial.setBackground(null);
            dialogBinding.tabFontSerif.setBackground(null);
            dialogBinding.tabFontSans.setBackground(null);
            dialogBinding.tabFontSystem.setTextColor(secondaryColor);
            dialogBinding.tabFontEditorial.setTextColor(secondaryColor);
            dialogBinding.tabFontSerif.setTextColor(secondaryColor);
            dialogBinding.tabFontSans.setTextColor(secondaryColor);

            if (FONT_SYSTEM.equalsIgnoreCase(family)) {
                dialogBinding.tabFontSystem.setBackgroundResource(R.drawable.bg_circle_accent);
                dialogBinding.tabFontSystem.setTextColor(goldColor);
            } else if (FONT_SANS.equalsIgnoreCase(family)) {
                dialogBinding.tabFontSans.setBackgroundResource(R.drawable.bg_circle_accent);
                dialogBinding.tabFontSans.setTextColor(goldColor);
            } else if (FONT_SERIF.equalsIgnoreCase(family)) {
                dialogBinding.tabFontSerif.setBackgroundResource(R.drawable.bg_circle_accent);
                dialogBinding.tabFontSerif.setTextColor(goldColor);
            } else {
                dialogBinding.tabFontEditorial.setBackgroundResource(R.drawable.bg_circle_accent);
                dialogBinding.tabFontEditorial.setTextColor(goldColor);
            }
        };
        refreshFontTabs.run();

        dialogBinding.tabFontSystem.setOnClickListener(v -> {
            settingsSp.edit().putString(KEY_FONT_FAMILY, FONT_SYSTEM).apply();
            refreshFontTabs.run();
            if (listener != null) {
                listener.onFontFamilyChanged(getReadingTypeface(activity), FONT_SYSTEM);
            }
        });

        dialogBinding.tabFontEditorial.setOnClickListener(v -> {
            settingsSp.edit().putString(KEY_FONT_FAMILY, FONT_EDITORIAL).apply();
            refreshFontTabs.run();
            if (listener != null) {
                listener.onFontFamilyChanged(getReadingTypeface(activity), FONT_EDITORIAL);
            }
        });

        dialogBinding.tabFontSerif.setOnClickListener(v -> {
            settingsSp.edit().putString(KEY_FONT_FAMILY, FONT_SERIF).apply();
            refreshFontTabs.run();
            if (listener != null) {
                listener.onFontFamilyChanged(getReadingTypeface(activity), FONT_SERIF);
            }
        });

        dialogBinding.tabFontSans.setOnClickListener(v -> {
            settingsSp.edit().putString(KEY_FONT_FAMILY, FONT_SANS).apply();
            refreshFontTabs.run();
            if (listener != null) {
                listener.onFontFamilyChanged(getReadingTypeface(activity), FONT_SANS);
            }
        });

        // 3. Initialize Type Size Slider
        int clampedSize = Math.max(14, Math.min(34, currentSizeSp));
        final int[] latestSize = new int[]{clampedSize};
        dialogBinding.tvSizeValue.setText(clampedSize + " sp");
        dialogBinding.seekbarFontSize.setMax(20);
        dialogBinding.seekbarFontSize.setProgress(clampedSize - 14);

        dialogBinding.seekbarFontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int newSize = progress + 14;
                latestSize[0] = newSize;
                dialogBinding.tvSizeValue.setText(newSize + " sp");
                if (listener != null) {
                    listener.onFontSizeChanged(newSize);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                settingsSp.edit().putString(KEY_FONT_SIZE, String.valueOf(latestSize[0])).apply();
            }
        });

        dialog.setOnDismissListener(d ->
                settingsSp.edit().putString(KEY_FONT_SIZE, String.valueOf(latestSize[0])).apply()
        );

        dialogBinding.btnTypographyDone.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
