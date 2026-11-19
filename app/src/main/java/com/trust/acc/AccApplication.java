package com.trust.acc;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Application entry point ensuring saved liturgical theme preferences apply on every
 * cold start (including deep-link .acc/.json opens) and providing a shared I/O executor.
 */
public class AccApplication extends Application {

    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(2);

    @Override
    public void onCreate() {
        super.onCreate();
        applySavedTheme();
    }

    public static ExecutorService ioExecutor() {
        return IO_EXECUTOR;
    }

    private void applySavedTheme() {
        SharedPreferences themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE);
        String userTheme = themePrefs.getString("mode", "system");
        if ("dark".equals(userTheme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if ("light".equals(userTheme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
