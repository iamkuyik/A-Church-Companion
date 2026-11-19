package com.trust.acc;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Utility for edge-to-edge drawing, status bar tinting, display cutout handling,
 * and IME-aware window inset padding.
 */
public final class SystemBarUtil {

    private SystemBarUtil() {}

    public static void applyEdgeToEdge(Activity activity, View rootView) {
        if (activity == null || activity.getWindow() == null) return;

        Window window = activity.getWindow();

        // 1. Extend content under system bars
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // 2. Transparent system bars
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // 3. Enable notch / display cutout short edges mode on API 28+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(layoutParams);
        }

        // 4. Update status and navigation bar icon colors (light vs dark)
        updateSystemBarAppearance(activity);

        // 5. Preserve original XML padding and add system bars, cutout, and IME insets
        if (rootView != null) {
            final int initLeft = rootView.getPaddingLeft();
            final int initTop = rootView.getPaddingTop();
            final int initRight = rootView.getPaddingRight();
            final int initBottom = rootView.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                Insets bars = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                );
                Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
                int bottomInset = Math.max(bars.bottom, ime.bottom);
                v.setPadding(
                        initLeft + bars.left,
                        initTop + bars.top,
                        initRight + bars.right,
                        initBottom + bottomInset
                );
                return windowInsets;
            });
            ViewCompat.requestApplyInsets(rootView);
        }
    }

    public static void updateSystemBarAppearance(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;

        Window window = activity.getWindow();
        View decorView = window.getDecorView();
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, decorView);

        if (insetsController != null) {
            boolean isNight = isNightMode(activity);
            insetsController.setAppearanceLightStatusBars(!isNight);
            insetsController.setAppearanceLightNavigationBars(!isNight);
        }
    }

    public static boolean isNightMode(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }
}
